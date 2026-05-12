package com.sliderulewatchguide

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sliderulewatchguide.controls.CurvedPresets
import com.sliderulewatchguide.dial.WatchDial
import com.sliderulewatchguide.dial.bezelDragRotation
import com.sliderulewatchguide.equations.BezelInputs
import com.sliderulewatchguide.equations.ConverterInputs
import com.sliderulewatchguide.equations.FloatingEquations
import com.sliderulewatchguide.viewmodel.DialViewModel

@Composable
fun SlideRuleApp() {
    val vm: DialViewModel = viewModel()
    val rotation by vm.rotationDegrees.collectAsStateWithLifecycle()
    val outerText by vm.outerInput.collectAsStateWithLifecycle()
    val innerText by vm.innerInput.collectAsStateWithLifecycle()
    val statText by vm.statInput.collectAsStateWithLifecycle()
    val nautText by vm.nautInput.collectAsStateWithLifecycle()
    val kmText by vm.kmInput.collectAsStateWithLifecycle()
    val chronoState by vm.chronoState.collectAsStateWithLifecycle()

    // Disclaimer state lifted to the top level so it can render as an
    // overlay z-stacked OVER the main content. Underlying layout stays
    // in place regardless of disclaimer visibility — dismissing the
    // overlay does not shift the watch or the equations panel at all.
    var disclaimerExpanded by remember { mutableStateOf(true) }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val isWide = maxWidth >= 720.dp
            if (isWide) {
                Row(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    DialColumn(
                        modifier = Modifier.weight(1f),
                        rotation = rotation,
                        chronoState = chronoState,
                        chronoMillisProvider = vm::currentChronoMs,
                        outerText = outerText,
                        innerText = innerText,
                        statText = statText,
                        nautText = nautText,
                        kmText = kmText,
                        vm = vm
                    )
                    Spacer(Modifier.size(12.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FloatingEquations(
                            rotationDegrees = rotation,
                            outer = outerText,
                            inner = innerText,
                            statRead = statText,
                            nautRead = nautText,
                            kmRead = kmText
                        )
                    }
                }
            } else {
                // Compact / portrait: dial + buttons stay anchored at the
                // top; the live equations panel is its own bounded scroll
                // area at the bottom so the user can scroll the equations
                // independently without losing sight of the watch.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DialColumn(
                        modifier = Modifier.fillMaxWidth(),
                        rotation = rotation,
                        chronoState = chronoState,
                        chronoMillisProvider = vm::currentChronoMs,
                        outerText = outerText,
                        innerText = innerText,
                        statText = statText,
                        nautText = nautText,
                        kmText = kmText,
                        vm = vm
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        FloatingEquations(
                            rotationDegrees = rotation,
                            outer = outerText,
                            inner = innerText,
                            statRead = statText,
                            nautRead = nautText,
                            kmRead = kmText
                        )
                    }
                }
            }
        }
        // Overlay layer (z-stacked above main content). When the
        // disclaimer is expanded, this draws a dark scrim and the
        // full text panel over EVERYTHING. When collapsed, only a
        // small "Disclaimer" chip is visible (pinned top-left under
        // the Reset chip). The underlying layout never shifts.
        DisclaimerOverlay(
            expanded = disclaimerExpanded,
            onToggle = { disclaimerExpanded = !disclaimerExpanded }
        )
        }
    }
}

@Composable
private fun DialColumn(
    modifier: Modifier,
    rotation: Double,
    chronoState: com.sliderulewatchguide.viewmodel.ChronoState,
    chronoMillisProvider: () -> Long,
    outerText: String,
    innerText: String,
    statText: String,
    nautText: String,
    kmText: String,
    vm: DialViewModel
) {
    val haptics = LocalHapticFeedback.current

    // Disclaimer is now rendered as an overlay at the SlideRuleApp top
    // level (sibling of this column). DialColumn no longer participates
    // in disclaimer state — the underlying layout is stable regardless
    // of the overlay's visibility.

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // Reset (left, on its own) + Examples arc (right) above the watch.
        CurvedPresets(
            onSetAngle = vm::setRotation,
            onReset = vm::reset,
            // Tall enough to fit the steeper chip arc (centre chip up
            // top, outer chips ~44 dp lower) plus the EXAMPLES caption.
            modifier = Modifier.fillMaxWidth().height(96.dp)
        )

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val side = maxWidth
            val rOuter = side.value * 0.5f * 0.88f

            // Both floating panels report their actual measured size back
            // via onSizeChanged so the overlap calc adapts to whatever
            // the bumped fonts / number of rows actually produce on this
            // device. No more hard-coded width / height constants.
            var bezelSize by remember { mutableStateOf(IntSize.Zero) }
            var converterSize by remember { mutableStateOf(IntSize.Zero) }
            val density = LocalDensity.current
            val bezelDp = with(density) {
                bezelSize.width.toDp().value to bezelSize.height.toDp().value
            }
            val converterDp = with(density) {
                converterSize.width.toDp().value to converterSize.height.toDp().value
            }
            // Dynamic safety gap: minimum breathing space (in dp) between
            // the dial edge and the top of a floating box. Scales with
            // the system fontScale so users on larger Android text get
            // proportionally more clearance. Only kicks in when a box
            // would otherwise sit flush against (or inside) the dial.
            val safetyGapDp = 12f * density.fontScale.coerceAtLeast(1f)
            fun overlapFor(w: Float, h: Float): Float {
                if (w == 0f || h == 0f) return 0f
                val dx = side.value / 2f - w
                val dy = side.value / 2f - h
                val cornerDist = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                // Treat the dial radius as if it were larger by safetyGap;
                // the box has to clear that inflated circle.
                return (rOuter + safetyGapDp - cornerDist).coerceAtLeast(0f)
            }
            val overlap = maxOf(
                overlapFor(bezelDp.first, bezelDp.second),
                overlapFor(converterDp.first, converterDp.second)
            )
            // On Fold 7 (~720 dp wide) both overlaps resolve to 0 and the
            // layout is identical to a plain square BoxWithConstraints.
            // On narrower screens, the container grows by however many dp
            // it takes to clear whichever box is the tighter fit.
            val containerHeight = side + overlap.dp

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(containerHeight)
            ) {
                // Watch square — anchored to TOP-CENTER. Only the watch
                // itself receives the bezel drag gesture; the empty
                // overflow strip below (if any) ignores drags.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .align(Alignment.TopCenter)
                        .bezelDragRotation { vm.rotateBy(it) }
                ) {
                    WatchDial(
                        bezelRotationDegrees = rotation,
                        chronoState = chronoState,
                        chronoMillisProvider = chronoMillisProvider,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Top pusher tap target (start / stop)
                    Box(modifier = Modifier
                        .offset(x = side * 0.85f, y = side * 0.20f)
                        .size(width = side * 0.13f, height = side * 0.13f)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.chronoStartStop()
                        })
                    // Bottom pusher tap target (reset)
                    Box(modifier = Modifier
                        .offset(x = side * 0.85f, y = side * 0.67f)
                        .size(width = side * 0.13f, height = side * 0.13f)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            vm.chronoReset()
                        })
                }

                // Floating boxes anchored to the container's bottom
                // corners. If overlap == 0 they sit inside the watch
                // square's corners; if overlap > 0 the container has been
                // extended downward, so the boxes sit BELOW the dial.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 2.dp, bottom = 2.dp)
                        .onSizeChanged { bezelSize = it }
                ) {
                    BezelInputs(
                        outer = outerText,
                        inner = innerText,
                        onOuterChange = vm::setOuterText,
                        onInnerChange = vm::setInnerText,
                        onCommit = vm::commitInputs
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 2.dp, bottom = 2.dp)
                        .onSizeChanged { converterSize = it }
                ) {
                    ConverterInputs(
                        stat = statText,
                        naut = nautText,
                        km = kmText,
                        onStatChange = vm::setStatText,
                        onNautChange = vm::setNautText,
                        onKmChange = vm::setKmText,
                        onCommitStat = vm::commitStat,
                        onCommitNaut = vm::commitNaut,
                        onCommitKm = vm::commitKm
                    )
                }
            }
        }
    }
}

/**
 * Independent-status disclaimer panel. Shown expanded on first launch.
 * Tapping "Collapse" hides the text and replaces it with a slim
 * "Disclaimer" chip pinned to the LEFT (vertically under the Reset chip
 * in the presets row). Tapping that chip re-expands the panel.
 */
/**
 * Sibling-Box overlay: z-stacked above the main app content. When
 * [expanded] is true, a full-screen scrim + centred panel covers
 * everything (the underlying watch and equations remain laid out in
 * their normal positions — they're just visually obscured). When
 * collapsed, only a small "Disclaimer" chip is visible, pinned to the
 * top-left under the Reset chip in the presets row. The underlying
 * layout NEVER shifts based on this state.
 */
@Composable
private fun BoxScope.DisclaimerOverlay(expanded: Boolean, onToggle: () -> Unit) {
    if (expanded) {
        // Full-screen scrim. Tapping the scrim does NOT dismiss — the
        // user must use the explicit "Collapse" button so the dismissal
        // is intentional (matches the user's spec). But the scrim MUST
        // absorb the tap so it doesn't fall through to underlying
        // composables (which would open keyboards / focus text fields).
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .matchParentSize()
                .background(androidx.compose.ui.graphics.Color(0xFF0E0E0E))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = { /* swallow taps */ }
                )
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                androidx.compose.material3.Text(
                    text =
                        "This app is an independent educational tool that demonstrates " +
                        "how circular logarithmic slide-rule bezels work. It is not " +
                        "affiliated with, endorsed by, sponsored by, or in any way " +
                        "officially connected to any watch manufacturer or any brand. " +
                        "All trademarks, trade names, and trade dress are the property " +
                        "of their respective owners. No claim is made to any third-party " +
                        "intellectual property. The dial, hands, sub-dials, markers, and " +
                        "all other visual elements are generic representations of common " +
                        "chronograph and slide-rule conventions and contain no copyrighted " +
                        "assets belonging to any manufacturer.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.ui.graphics.Color(0xFFEAEAEA)
                )
                Spacer(Modifier.size(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    DisclaimerToggleChip(label = "Collapse", onClick = onToggle)
                }
            }
        }
    } else {
        // Collapsed: small chip pinned to top-left. Sits just BELOW the
        // Reset chip's vertical position. Doesn't overlap the Examples
        // arc which is to its right.
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 64.dp)
        ) {
            DisclaimerToggleChip(label = "Disclaimer", onClick = onToggle)
        }
    }
}

@Composable
private fun DisclaimerToggleChip(label: String, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = androidx.compose.material3.MaterialTheme.colorScheme.outline
        )
    ) {
        androidx.compose.material3.Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge.copy(
                fontSize = 14.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            ),
            maxLines = 1,
            softWrap = false
        )
    }
}
