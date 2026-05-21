package com.sliderulewatchguide.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.sliderulewatchguide.wear.dial.DialMath
import com.sliderulewatchguide.wear.dial.WearDial
import com.sliderulewatchguide.wear.dial.bezelDragRotation
import com.sliderulewatchguide.wear.viewmodel.WearDialViewModel
import kotlin.math.abs
import kotlin.math.round

/**
 * Top-level Wear OS UI.
 *
 * Layout (round screen, fullscreen Box):
 *   • Background: full-screen WearDial canvas, dragable to rotate the bezel.
 *   • TimeText overlay (system time) at top of screen.
 *   • Two tap zones on the right edge mirroring where the phone-app's
 *     chronograph pushers sit — top pusher resets the bezel, bottom
 *     pusher nudges the outer value at MPH to the nearest integer.
 *   • Reading text at the bottom centre: current outer-at-inner-60
 *     value plus the multiplier (e.g. "60.0 · ×1.00").
 *   • Rotary input (Galaxy Watch Classic): scrolls rotate the bezel.
 */
@Composable
fun WearSlideRuleApp(vm: WearDialViewModel = viewModel()) {
    val rotation by vm.rotationDegrees.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val focusRequester = remember { FocusRequester() }

    // Auto-focus the dial so rotary input arrives without an explicit tap.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onRotaryScrollEvent { event ->
                // event.verticalScrollPixels: positive = scroll down /
                // clockwise on a rotary bezel. Convert pixels to a
                // sensible rotation step — empirically, dividing by 6
                // gives ~ 1 degree per detent click on a Galaxy Watch
                // Classic; touch-wheel deltas are larger so divide more.
                vm.rotateBy(event.verticalScrollPixels.toDouble() / 6.0)
                true
            }
    ) {
        WearDial(
            rotationDegrees = rotation,
            modifier = Modifier
                .fillMaxSize()
                .bezelDragRotation { delta -> vm.rotateBy(delta) }
        )

        // System time chip at the top — Wear OS convention.
        TimeText()

        // Top pusher tap zone (right side, upper third). Resets bezel.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-8).dp, y = 80.dp)
                .size(width = 28.dp, height = 36.dp)
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.reset()
                }
        )

        // Bottom pusher tap zone (right side, lower third). Nudges to
        // nearest integer at the MPH anchor.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-8).dp, y = (-80).dp)
                .size(width = 28.dp, height = 36.dp)
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.nudgeToNearestInteger()
                }
        )

        // Reading at bottom centre.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            val reading = formatReading(
                outerAtMph = DialMath.outerValueAtInner(DialMath.RED_60_MPH, rotation),
                multiplier = DialMath.multiplierFromRotation(rotation)
            )
            Text(
                text = reading,
                color = Color(0xFFF0EFEA),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun formatReading(outerAtMph: Double, multiplier: Double): String {
    val a = formatNum(outerAtMph)
    val m = formatNum(multiplier)
    return "$a  ·  ×$m"
}

private fun formatNum(v: Double): String {
    if (!v.isFinite()) return "—"
    val rounded2 = round(v * 100.0) / 100.0
    if (abs(rounded2 - rounded2.toInt()) < 1e-9 && abs(rounded2) < 1e9) {
        return rounded2.toInt().toString()
    }
    return "%.2f".format(rounded2).trimEnd('0').trimEnd('.')
}
