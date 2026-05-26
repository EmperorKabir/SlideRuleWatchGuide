package com.sliderulewatchguide.sync

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.nio.ByteBuffer

/**
 * Bidirectional bezel-rotation sync over the Wear OS Data Layer.
 *
 * Two transport channels:
 *   • MessageClient (`/bezel/live`) — low-latency, fire-and-forget,
 *     used for live updates while the partner app is foregrounded.
 *     Throttled by the caller (the ViewModel) to ~30 Hz.
 *   • DataClient (`/bezel/state`) — persisted last-known value, used
 *     so a side that resumes from background / fresh launch can catch
 *     up to whatever the partner last set. Debounced to ~4 Hz.
 *
 * Conflict resolution is last-write-wins by the embedded epoch-ms
 * timestamp: an incoming value is only surfaced if its timestamp is
 * newer than the most-recent value WE emitted, so a device never
 * fights its own echo.
 *
 * `partnerAvailable` reflects whether any paired node currently
 * advertises [capability]. When false the publish methods are no-ops.
 *
 * Construction is cheap; call [start] once (e.g. from the ViewModel
 * init) and [stop] when the owning scope is cleared.
 *
 * @param source short tag identifying this device ("phone" / "wear"),
 *   embedded in payloads for diagnostics and self-echo rejection.
 */
class BezelSyncManager(
    context: Context,
    private val scope: CoroutineScope,
    private val capability: String,
    private val source: String,
) {
    private val appContext = context.applicationContext
    private val messageClient: MessageClient = Wearable.getMessageClient(appContext)
    private val dataClient: DataClient = Wearable.getDataClient(appContext)
    private val capabilityClient: CapabilityClient = Wearable.getCapabilityClient(appContext)

    private val _partnerAvailable = MutableStateFlow(false)
    val partnerAvailable: StateFlow<Boolean> = _partnerAvailable.asStateFlow()

    // Remote rotation updates (degrees). Replay-1 so a late collector
    // immediately gets the most recent remote value.
    private val _remoteRotation = MutableSharedFlow<Double>(
        replay = 1,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val remoteRotation: SharedFlow<Double> = _remoteRotation.asSharedFlow()

    @Volatile private var enabled = true
    // Timestamp of the most recent value WE published. Incoming values
    // older than this are our own echo and are ignored.
    @Volatile private var lastLocalPublishMs = 0L
    // Timestamp of the most recent value we APPLIED from the partner.
    @Volatile private var lastAppliedRemoteMs = 0L

    // Reachable nodes advertising [capability], cached so the 30 Hz
    // publishLive path never awaits a getCapability() IPC round-trip per
    // update. Kept current by the capability listener + seeded on
    // start()/refreshCapability(). @Volatile: written on the listener/IO
    // threads, read on the publish path.
    @Volatile private var cachedNodes: Set<Node> = emptySet()

    private fun updateNodes(nodes: Set<Node>) {
        cachedNodes = nodes
        _partnerAvailable.value = nodes.isNotEmpty()
    }

    private val capabilityListener = CapabilityClient.OnCapabilityChangedListener { info ->
        updateNodes(info.nodes)
    }

    private val messageListener = MessageClient.OnMessageReceivedListener { event: MessageEvent ->
        if (event.path != PATH_LIVE) return@OnMessageReceivedListener
        decodeAndEmit(event.data)
    }

    private val dataListener = DataClient.OnDataChangedListener { buffer: DataEventBuffer ->
        for (event in buffer) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            if (event.dataItem.uri.path != PATH_STATE) continue
            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            val degrees = map.getDouble(KEY_DEGREES)
            val ts = map.getLong(KEY_TIMESTAMP)
            emitIfNewer(degrees, ts)
        }
        buffer.release()
    }

    fun start() {
        capabilityClient.addListener(capabilityListener, capability)
        messageClient.addListener(messageListener)
        dataClient.addListener(dataListener)
        // Seed availability + last-known state immediately.
        scope.launch {
            runCatching {
                val info = capabilityClient.getCapability(capability, CapabilityClient.FILTER_REACHABLE).await()
                updateNodes(info.nodes)
            }
            runCatching { readLatestState() }
        }
    }

    fun stop() {
        capabilityClient.removeListener(capabilityListener)
        messageClient.removeListener(messageListener)
        dataClient.removeListener(dataListener)
    }

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    /** Re-query partner capability — call when the app foregrounds so a
     *  watch paired AFTER first launch is picked up without a restart. */
    fun refreshCapability() {
        scope.launch {
            runCatching {
                val info = capabilityClient.getCapability(capability, CapabilityClient.FILTER_REACHABLE).await()
                updateNodes(info.nodes)
            }
        }
    }

    /** Live update (30 Hz path). Sends to every cached reachable capable
     *  node WITHOUT a per-call capability IPC — the node set is kept
     *  current by [capabilityListener] and the start/refresh seed. */
    fun publishLive(degrees: Double) {
        if (!enabled) return
        val nodes = cachedNodes
        if (nodes.isEmpty()) return
        val ts = System.currentTimeMillis()
        lastLocalPublishMs = ts
        val payload = encode(degrees, ts)
        scope.launch {
            runCatching {
                for (node in nodes) {
                    messageClient.sendMessage(node.id, PATH_LIVE, payload)
                }
            }
        }
    }

    /** Persisted state (≤4 Hz path). Survives sleep / app close. */
    fun publishState(degrees: Double) {
        if (!enabled) return
        val ts = System.currentTimeMillis()
        lastLocalPublishMs = ts
        scope.launch {
            runCatching {
                val req = PutDataMapRequest.create(PATH_STATE).apply {
                    dataMap.putDouble(KEY_DEGREES, degrees)
                    dataMap.putLong(KEY_TIMESTAMP, ts)
                    dataMap.putString(KEY_SOURCE, source)
                }.asPutDataRequest().setUrgent()
                dataClient.putDataItem(req).await()
            }
        }
    }

    private suspend fun readLatestState() {
        val items = dataClient.dataItems.await()
        try {
            for (item in items) {
                if (item.uri.path != PATH_STATE) continue
                val map = DataMapItem.fromDataItem(item).dataMap
                emitIfNewer(map.getDouble(KEY_DEGREES), map.getLong(KEY_TIMESTAMP))
            }
        } finally {
            items.release()
        }
    }

    private fun decodeAndEmit(data: ByteArray) {
        if (data.size < 16) return
        val buf = ByteBuffer.wrap(data)
        val degrees = buf.double
        val ts = buf.long
        emitIfNewer(degrees, ts)
    }

    private fun emitIfNewer(degrees: Double, ts: Long) {
        // Reject our own echo and any stale value.
        if (ts <= lastLocalPublishMs) return
        if (ts <= lastAppliedRemoteMs) return
        if (!degrees.isFinite()) return
        lastAppliedRemoteMs = ts
        _remoteRotation.tryEmit(degrees)
    }

    private fun encode(degrees: Double, ts: Long): ByteArray =
        ByteBuffer.allocate(16).putDouble(degrees).putLong(ts).array()

    companion object {
        private const val PATH_LIVE = "/bezel/live"
        private const val PATH_STATE = "/bezel/state"
        private const val KEY_DEGREES = "degrees"
        private const val KEY_TIMESTAMP = "ts"
        private const val KEY_SOURCE = "source"
    }
}
