package com.sliderulewatchguide.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sliderulewatchguide.wear.dial.WatchDial
import com.sliderulewatchguide.wear.dial.bezelDragRotation
import com.sliderulewatchguide.wear.viewmodel.DialViewModel
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Wear OS top-level UI — dial-only, no overlays.
 *
 * Identical phone-app dial via [WatchDial]. Two on-screen chronograph
 * pusher buttons sit between the hour markers at clock-angles 75° and
 * 105° (i.e. between 2/3 o'clock and between 3/4 o'clock). They are
 * drawn as red circular discs on top of the dial in z-order, so taps
 * always reach them regardless of which hand happens to be passing
 * over them visually.
 *
 *   • Top pusher  → vm.chronoStartStop()
 *   • Bottom pusher → vm.chronoReset()
 *
 * Bezel input:
 *   • Touch drag on the dial → rotates the bezel.
 *   • Galaxy Watch Classic rotary bezel → also rotates the bezel.
 *
 * Magnetic snap: on bezel-drag release, if the rotation is within
 * 1.82° of the canonical outer-60-at-inner-60 alignment (rotation =
 * 0°), the bezel snaps to exact 0°. That window corresponds to outer
 * values in [59.31, 60.70] sitting above the inner-60 anchor — the
 * angularly-symmetric equivalent of the user's [59.3, 60.7] request.
 */
private const val SNAP_60_HALF_WIDTH_DEG = 1.82
private const val PUSHER_RED = 0xFFD7263D
// Visual disc 14 dp (~10 % smaller than the 16 dp seen earlier) sits
// just inside the chapter ring at radial 0.65 × rOuter. The
// surrounding invisible tap-zone (24 dp) provides a standard-sized
// touch target. Tap-zone outer edge stops at ~0.77 × rOuter, well
// short of the rotating bezel at rBezelInner = 0.85 × rOuter, so
// bezel drag gestures do not clash with pusher taps.
private val PUSHER_VISUAL_DP = 14.dp
private val PUSHER_TAP_DP = 24.dp

@Composable
fun WearSlideRuleApp(vm: DialViewModel = viewModel()) {
    val rotation by vm.rotationDegrees.collectAsStateWithLifecycle()
    val chronoState by vm.chronoState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

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
    ) {
        WatchDial(
            bezelRotationDegrees = rotation,
            chronoState = chronoState,
            chronoMillisProvider = vm::currentChronoMs,
            modifier = Modifier
                .fillMaxSize()
                .bezelDragRotation(
                    onRotate = { delta -> vm.rotateBy(delta) },
                    onDragEnd = {
                        // Wrap rotation into (-180, 180] then snap to 0 if
                        // within the magnetic window around outer-60.
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
    }
}

/**
 * The two red chrono-pusher tap targets. Position is computed from the
 * dial's geometry constants so they scale automatically across any
 * round-watch resolution (Wear OS devices range ~390-480 px).
 */
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
        // Mirror DialGeom: rOuter = 0.88 × half-min-dim. Pusher centre
        // sits at 0.63 × rOuter — between the right sub-dial (outer
        // edge 0.455 × rOuter) and the chapter ring's inner edge
        // (rChapterInner = 0.71 × rOuter), with a small visible gap
        // to the bezel.
        val rOuter = halfPx * 0.88f
        val pusherRadiusPx = rOuter * 0.63f
        val cx = widthPx / 2f
        val cy = heightPx / 2f
        val tapPx = with(density) { PUSHER_TAP_DP.toPx() }

        // Clock-angle 75° (between 2 and 3 hour markers) — top pusher.
        // Clock-angle 105° (between 3 and 4 hour markers) — bottom pusher.
        // Screen coords: x = sin(angle), y = -cos(angle) (clock 0° = north).
        val topAngleRad = Math.toRadians(75.0)
        val botAngleRad = Math.toRadians(105.0)
        val topX = cx + pusherRadiusPx * sin(topAngleRad).toFloat()
        val topY = cy - pusherRadiusPx * cos(topAngleRad).toFloat()
        val botX = cx + pusherRadiusPx * sin(botAngleRad).toFloat()
        val botY = cy - pusherRadiusPx * cos(botAngleRad).toFloat()

        PusherButton(
            offsetXPx = topX - tapPx / 2,
            offsetYPx = topY - tapPx / 2,
            haptic = HapticFeedbackType.LongPress,
            onClick = onTopPusher,
            haptics = haptics,
        )
        PusherButton(
            offsetXPx = botX - tapPx / 2,
            offsetYPx = botY - tapPx / 2,
            haptic = HapticFeedbackType.TextHandleMove,
            onClick = onBottomPusher,
            haptics = haptics,
        )
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
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(PUSHER_VISUAL_DP)
                .clip(CircleShape)
                .background(Color(PUSHER_RED))
        )
    }
}
