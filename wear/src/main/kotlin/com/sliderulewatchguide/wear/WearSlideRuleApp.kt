package com.sliderulewatchguide.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import com.sliderulewatchguide.wear.dial.WatchDial
import com.sliderulewatchguide.wear.dial.bezelDragRotation
import com.sliderulewatchguide.wear.sync.rememberBezelSync
import com.sliderulewatchguide.wear.viewmodel.DialViewModel
import kotlin.math.abs
import kotlin.math.min

private const val SNAP_60_HALF_WIDTH_DEG = 1.82
private const val PUSHER_RED = 0xFFD7263D

private val TOP_PUSHER_SIN = kotlin.math.sin(Math.toRadians(75.0)).toFloat()
private val TOP_PUSHER_COS = kotlin.math.cos(Math.toRadians(75.0)).toFloat()
private val BOT_PUSHER_SIN = kotlin.math.sin(Math.toRadians(105.0)).toFloat()
private val BOT_PUSHER_COS = kotlin.math.cos(Math.toRadians(105.0)).toFloat()
private val PUSHER_VISUAL_DP = 14.dp
private val PUSHER_TAP_DP = 24.dp

/**
 * Wear OS top-level UI — dial-only, no static chrome. Long-press the
 * dial for the sync settings menu (the only non-dial affordance, and
 * it's hidden until invoked, per spec).
 *
 * Chrono pushers + magnetic snap-to-60 as before. Adds bidirectional
 * bezel sync with the paired phone: incoming rotations apply with an
 * epsilon echo-guard.
 */
@Composable
fun WearSlideRuleApp(vm: DialViewModel = viewModel()) {
    val rotationState = vm.rotationDegrees.collectAsStateWithLifecycle()
    // Defer the bezel-rotation read to the draw/layer phase: changing
    // rotation (rotary crown, drag, or sync) then only re-runs the
    // RotatingBezel graphicsLayer transform instead of recomposing the dial.
    val rotationProvider = remember { { rotationState.value.toFloat() } }
    // Hoist the chrono-ms method reference once so WatchDial's param stays
    // stable across recompositions (mirrors the phone app).
    val chronoMillis = remember(vm) { vm::currentChronoMs }
    val chronoState by vm.chronoState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    var showSyncMenu by remember { mutableStateOf(false) }

    val syncState = rememberBezelSync(
        rotationFlow = vm.rotationDegrees,
        source = "wear",
        onRemoteRotation = { remote ->
            if (abs(remote - vm.rotationDegrees.value) > 0.05) {
                vm.setRotation(remote)
            }
        },
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onRotaryScrollEvent { event ->
                vm.rotateBy(event.verticalScrollPixels.toDouble() / 6.0)
                true
            }
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { showSyncMenu = true })
            }
    ) {
        WatchDial(
            rotationProvider = rotationProvider,
            chronoState = chronoState,
            chronoMillisProvider = chronoMillis,
            modifier = Modifier
                .fillMaxSize()
                .bezelDragRotation(
                    onRotate = { delta -> vm.rotateBy(delta) },
                    onDragEnd = {
                        val r = vm.rotationDegrees.value
                        val wrapped = if (r > 180.0) r - 360.0 else r
                        if (abs(wrapped) < SNAP_60_HALF_WIDTH_DEG) {
                            vm.setRotation(0.0)
                        }
                    }
                )
        )

        ChronoPusherButtons(
            onTopPusher = { vm.chronoStartStop() },
            onBottomPusher = { vm.chronoReset() },
        )

        if (showSyncMenu) {
            SyncMenu(
                syncEnabled = syncState.syncEnabled,
                partnerAvailable = syncState.partnerAvailable,
                onToggle = { syncState.setSyncEnabled(it) },
                onDismiss = { showSyncMenu = false },
            )
        }
    }
}

@Composable
private fun BoxScope.SyncMenu(
    syncEnabled: Boolean,
    partnerAvailable: Boolean,
    onToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ToggleChip(
                checked = syncEnabled,
                onCheckedChange = onToggle,
                label = { Text("Sync bezel with phone") },
                secondaryLabel = {
                    Text(if (partnerAvailable) "Phone connected" else "No phone detected")
                },
                toggleControl = {
                    androidx.wear.compose.material.Icon(
                        imageVector = ToggleChipDefaults.switchIcon(syncEnabled),
                        contentDescription = if (syncEnabled) "On" else "Off",
                    )
                },
                colors = ToggleChipDefaults.toggleChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Tap outside to close",
                color = Color(0xFF8A8A8A),
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun BoxScope.ChronoPusherButtons(
    onTopPusher: () -> Unit,
    onBottomPusher: () -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val sidePx = minOf(widthPx, heightPx)
        val halfPx = sidePx / 2f
        val rOuter = halfPx * 0.88f
        val pusherRadiusPx = rOuter * 0.63f
        val cx = widthPx / 2f
        val cy = heightPx / 2f
        val tapPx = with(density) { PUSHER_TAP_DP.toPx() }

        val topX = cx + pusherRadiusPx * TOP_PUSHER_SIN
        val topY = cy - pusherRadiusPx * TOP_PUSHER_COS
        val botX = cx + pusherRadiusPx * BOT_PUSHER_SIN
        val botY = cy - pusherRadiusPx * BOT_PUSHER_COS

        PusherButton(topX - tapPx / 2, topY - tapPx / 2, HapticFeedbackType.LongPress, onTopPusher, haptics)
        PusherButton(botX - tapPx / 2, botY - tapPx / 2, HapticFeedbackType.TextHandleMove, onBottomPusher, haptics)
    }
}

@Composable
private fun PusherButton(
    offsetXPx: Float,
    offsetYPx: Float,
    haptic: HapticFeedbackType,
    onClick: () -> Unit,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
) {
    Box(
        modifier = Modifier
            .offset { IntOffset(offsetXPx.toInt(), offsetYPx.toInt()) }
            .size(PUSHER_TAP_DP)
            .clickable {
                haptics.performHapticFeedback(haptic)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(PUSHER_VISUAL_DP)
                .clip(CircleShape)
                .background(Color(PUSHER_RED))
        )
    }
}
