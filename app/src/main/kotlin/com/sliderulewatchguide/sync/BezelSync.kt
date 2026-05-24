package com.sliderulewatchguide.sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/** Capability string advertised in res/values/wear.xml on both sides. */
private const val CAPABILITY = "sliderule_bezel_sync"

/** Outgoing live-update interval: ~30 Hz. */
private const val LIVE_SAMPLE_MS = 33L

/** Outgoing persisted-state debounce: ~4 Hz. */
private const val STATE_DEBOUNCE_MS = 250L

/** UI-facing sync state surfaced to the settings menu. */
data class BezelSyncUiState(
    val syncEnabled: Boolean,
    val partnerAvailable: Boolean,
    val setSyncEnabled: (Boolean) -> Unit,
)

/**
 * Wires bidirectional bezel sync into the composition without touching
 * the ViewModel's construction:
 *
 *   • Incoming remote rotations are delivered to [onRemoteRotation]
 *     (the caller routes them into the ViewModel).
 *   • Local rotation changes ([rotationFlow]) are published — live at
 *     ~30 Hz via MessageClient, plus a debounced ~4 Hz persisted-state
 *     snapshot via DataClient for background catch-up.
 *   • The OFF toggle is persisted in DataStore and gates the manager.
 *
 * Returns a [BezelSyncUiState] for the long-press settings menu.
 *
 * @param source "phone" or "wear" — identifies this device in payloads.
 */
@OptIn(FlowPreview::class)
@Composable
fun rememberBezelSync(
    rotationFlow: StateFlow<Double>,
    source: String,
    onRemoteRotation: (Double) -> Unit,
): BezelSyncUiState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val manager = remember { BezelSyncManager(context, scope, CAPABILITY, source) }
    val settings = remember { SyncSettings(context) }

    DisposableEffect(manager) {
        manager.start()
        onDispose { manager.stop() }
    }

    val partnerAvailable by manager.partnerAvailable.collectAsStateWithLifecycle()
    val syncEnabled by settings.syncEnabled.collectAsStateWithLifecycle(initialValue = true)

    LaunchedEffect(syncEnabled) { manager.setEnabled(syncEnabled) }

    LaunchedEffect(manager) {
        manager.remoteRotation.collect { onRemoteRotation(it) }
    }
    LaunchedEffect(manager) {
        rotationFlow.sample(LIVE_SAMPLE_MS).collect { manager.publishLive(it) }
    }
    LaunchedEffect(manager) {
        rotationFlow.debounce(STATE_DEBOUNCE_MS).collect { manager.publishState(it) }
    }

    return BezelSyncUiState(
        syncEnabled = syncEnabled,
        partnerAvailable = partnerAvailable,
        setSyncEnabled = { enabled -> scope.launch { settings.setSyncEnabled(enabled) } },
    )
}
