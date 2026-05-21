package com.sliderulewatchguide.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sliderulewatchguide.wear.dial.WatchDial
import com.sliderulewatchguide.wear.dial.bezelDragRotation
import com.sliderulewatchguide.wear.viewmodel.DialViewModel

/**
 * Wear OS top-level UI — dial-only, no overlays.
 *
 * The dial is the IDENTICAL composable used by the phone app
 * ([WatchDial]) — same sunburst, sub-dials, indices, hands, baton,
 * MPH arrow, fixed chapter ring, rotating bezel scale, etc. Only the
 * crown + chronograph-pusher visuals are omitted (see
 * `DialCanvas.kt`); the chronograph state machine itself is preserved
 * in the [DialViewModel] for a custom binding the user will add later.
 *
 * Inputs:
 *  • Touch drag on the dial → rotates the bezel.
 *  • Galaxy Watch Classic rotary bezel → also rotates the bezel.
 */
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
                // ~1° rotation per detent-click on Galaxy Watch Classic.
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
                .bezelDragRotation { delta -> vm.rotateBy(delta) }
        )
    }
}
