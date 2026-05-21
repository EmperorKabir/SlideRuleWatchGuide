package com.sliderulewatchguide.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.sliderulewatchguide.wear.dial.DialMath
import com.sliderulewatchguide.wear.dial.WatchDial
import com.sliderulewatchguide.wear.dial.bezelDragRotation
import com.sliderulewatchguide.wear.viewmodel.DialViewModel
import kotlin.math.abs
import kotlin.math.round

/**
 * Wear OS top-level UI.
 *
 * The dial is the IDENTICAL composable used by the phone app
 * ([WatchDial]) — same sunburst, sub-dials, indices, hands, baton,
 * MPH arrow, fixed chapter ring, rotating bezel scale, etc. Only the
 * crown + chronograph-pusher visuals are omitted (see
 * `DialCanvas.kt`); the chronograph state machine itself is preserved
 * in the [DialViewModel] for a custom binding the user will add later.
 *
 * Inputs:
 *  • Touch drag on the dial → rotates the bezel (atan2-based gesture
 *    shared with the phone version).
 *  • Galaxy Watch Classic rotary bezel → also rotates the bezel via
 *    [Modifier.onRotaryScrollEvent].
 */
@Composable
fun WearSlideRuleApp(vm: DialViewModel = viewModel()) {
    val rotation by vm.rotationDegrees.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onRotaryScrollEvent { event ->
                // ~1° rotation per detent-click on Galaxy Watch Classic.
                vm.rotateBy(event.verticalScrollPixels.toDouble() / 6.0)
                true
            }
    ) {
        WatchDial(
            bezelRotationDegrees = rotation,
            chronoState = vm.chronoState.collectAsStateWithLifecycle().value,
            chronoMillisProvider = vm::currentChronoMs,
            modifier = Modifier
                .fillMaxSize()
                .bezelDragRotation { delta -> vm.rotateBy(delta) }
        )

        TimeText()

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            val outerAtMph = DialMath.outerValueAtInner(DialMath.RED_60_MPH, rotation)
            val multiplier = DialMath.multiplierFromRotation(rotation)
            Text(
                text = "${formatNum(outerAtMph)}  ·  ×${formatNum(multiplier)}",
                color = Color(0xFFF0EFEA),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun formatNum(v: Double): String {
    if (!v.isFinite()) return "—"
    val rounded2 = round(v * 100.0) / 100.0
    if (abs(rounded2 - rounded2.toInt()) < 1e-9 && abs(rounded2) < 1e9) {
        return rounded2.toInt().toString()
    }
    return "%.2f".format(rounded2).trimEnd('0').trimEnd('.')
}
