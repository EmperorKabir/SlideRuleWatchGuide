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
private val PUSHER_SIZE_DP = 24.dp

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
        // Mirror DialGeom: rOuter = 0.88 × half-min-dim; place pushers
        // at ~85 % of rOuter, which lands them just inside the inner
        // chapter ring's outer edge — sitting on the dial face between
        // the hour-index batons. Tuned visually against image #4.
        val rOuter = halfPx * 0.88f
        val pusherRadiusPx = rOuter * 0.85f
        val cx = widthPx / 2f
        val cy = heightPx / 2f
        val buttonPx = with(density) { PUSHER_SIZE_DP.toPx() }

        // Clock-angle 75° (between 2 and 3 hour markers) — top pusher.
        // Clock-angle 105° (between 3 and 4 hour markers) — bottom pusher.
        // Screen coords: x = sin(angle), y = -cos(angle) (clock 0° = north).
        val topAngleRad = Math.toRadians(75.0)
        val botAngleRad = Math.toRadians(105.0)
        val topX = cx + pusherRadiusPx * sin(topAngleRad).toFloat()
        val topY = cy - pusherRadiusPx * cos(topAngleRad).toFloat()
        val botX = cx + pusherRadiusPx * sin(botAngleRad).toFloat()
        val botY = cy - pusherRadiusPx * cos(botAngleRad).toFloat()

        Box(
            modifier = Modifier
                .offset { IntOffset((topX - buttonPx / 2).toInt(), (topY - buttonPx / 2).toInt()) }
                .size(PUSHER_SIZE_DP)
                .clip(CircleShape)
                .background(Color(PUSHER_RED))
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onTopPusher()
                }
        )
        Box(
            modifier = Modifier
                .offset { IntOffset((botX - buttonPx / 2).toInt(), (botY - buttonPx / 2).toInt()) }
                .size(PUSHER_SIZE_DP)
                .clip(CircleShape)
                .background(Color(PUSHER_RED))
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onBottomPusher()
                }
        )
    }
}
