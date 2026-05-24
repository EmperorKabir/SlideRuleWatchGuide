package com.sliderulewatchguide

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sliderulewatchguide.controls.CurvedPresets
import com.sliderulewatchguide.controls.StayAnywhereBottomSheet
import com.sliderulewatchguide.dial.WatchDial
import com.sliderulewatchguide.dial.bezelDragRotation
import com.sliderulewatchguide.equations.BezelInputs
import com.sliderulewatchguide.equations.ConverterInputs
import com.sliderulewatchguide.equations.FloatingEquations
import com.sliderulewatchguide.viewmodel.DialViewModel

// Remote-sync glow colour: subtle cyan, matching the wear side. Drawn as
// an outer-edge ring on the phone dial (the wear side uses the inner
// chapter-ring edge) when a bezel rotation arrives from the partner.
private val SYNC_GLOW_COLOR = androidx.compose.ui.graphics.Color(0x804DD0E1)

/**
 * Outer-edge cyan glow overlay, alpha driven by [alpha] (0..1). Drawn at
 * the visible bezel outer radius (0.88 of the half-dimension) so it reads
 * as a halo around the dial. No-op when [alpha] is ~0.
 */
private fun Modifier.syncGlow(alpha: Float): Modifier = drawWithContent {
    drawContent()
    if (alpha <= 0.001f) return@drawWithContent
    val side = kotlin.math.min(size.width, size.height)
    val rOuter = side * 0.5f * 0.88f
    drawCircle(
        color = SYNC_GLOW_COLOR.copy(alpha = SYNC_GLOW_COLOR.alpha * alpha),
        radius = rOuter,
        center = Offset(size.width / 2f, size.height / 2f),
        style = Stroke(width = side * 0.014f),
    )
}

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
    // Capture the chrono-ms method reference ONCE so Compose can treat
    // the dial subtree as stable across recompositions (a fresh
    // KFunction would otherwise be allocated on every parent change,
    // forcing WatchDial / LiveHandsLayer to recompose unnecessarily).
    val chronoMillis = remember(vm) { vm::currentChronoMs }

    // Remote-sync glow: alpha pulsed on each phone-applied remote
    // rotation — a subtle outer-edge cyan halo on the dial, mirroring
    // the wear-side inner-edge glow (~150 ms fade). The pulse counter is
    // declared BEFORE the sync binder so onRemoteRotation increments it
    // directly.
    val glow = remember { Animatable(0f) }
    var remotePulse by remember { mutableStateOf(0) }

    // Bidirectional bezel sync with a paired Wear OS watch. Self-
    // activates when a partner is detected; the OFF toggle lives in
    // the dial's long-press menu. Incoming remote rotations are applied
    // with a small epsilon guard so a round-tripped value never starts
    // an echo loop (the manager also rejects our own timestamped echo).
    val syncState = com.sliderulewatchguide.sync.rememberBezelSync(
        rotationFlow = vm.rotationDegrees,
        source = "phone",
        onRemoteRotation = { remote ->
            if (kotlin.math.abs(remote - vm.rotationDegrees.value) > 0.05) {
                vm.setRotation(remote)
                remotePulse++
            }
        },
    )

    LaunchedEffect(remotePulse) {
        if (remotePulse > 0) {
            glow.snapTo(1f)
            glow.animateTo(0f, tween(durationMillis = 150))
        }
    }

    // Disclaimer state lifted to the top level so it can render as an
    // overlay z-stacked OVER the main content. Underlying layout stays
    // in place regardless of disclaimer visibility — dismissing the
    // overlay does not shift the watch or the equations panel at all.
    // rememberSaveable so the dismissed state survives config changes
    // (fold / unfold, rotation, font-scale change).
    var disclaimerExpanded by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(true)
    }

    // Sync settings menu (long-press the dial background to open).
    var showSyncMenu by remember { mutableStateOf(false) }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { showSyncMenu = true })
                }
        ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val isWide = maxWidth >= 720.dp
            // Hoist the equations-panel scroll state so it survives any
            // recomposition triggered by font-scale / rotation changes.
            val equationsScroll = rememberScrollState()
            if (isWide) {
                WideLayout(
                    rotation = rotation,
                    chronoState = chronoState,
                    chronoMillisProvider = chronoMillis,
                    outerText = outerText,
                    innerText = innerText,
                    statText = statText,
                    nautText = nautText,
                    kmText = kmText,
                    equationsScroll = equationsScroll,
                    glowAlpha = glow.value,
                    vm = vm
                )
            } else {
                // Compact / portrait: 5-snap-point live-equations sheet.
                // P1 peek, P2 below input boxes, P3 below dial circle,
                // P4 below chip buttons, P5 full extent. All four
                // measured snaps derive from runtime layout values so
                // they remain correct across screen sizes, font scales
                // and foldable transitions.
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    val sheetParentHeightDp = maxHeight
                    var chipsBottomDp by remember { mutableStateOf(0.dp) }
                    var dialCircleBottomDp by remember { mutableStateOf(0.dp) }
                    var inputsBottomDp by remember { mutableStateOf(0.dp) }
                    val density = LocalDensity.current

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        DialColumn(
                            modifier = Modifier.fillMaxWidth(),
                            rotation = rotation,
                            chronoState = chronoState,
                            chronoMillisProvider = chronoMillis,
                            outerText = outerText,
                            innerText = innerText,
                            statText = statText,
                            nautText = nautText,
                            kmText = kmText,
                            glowAlpha = glow.value,
                            vm = vm,
                            onDialBottomYChanged = { chipsPx, dialPx, inputsPx ->
                                val newChips = with(density) { chipsPx.toDp() }
                                val newDial = with(density) { dialPx.toDp() }
                                val newInputs = with(density) { inputsPx.toDp() }
                                if ((newChips - chipsBottomDp).value.let { kotlin.math.abs(it) } > 0.5f) {
                                    chipsBottomDp = newChips
                                }
                                if ((newDial - dialCircleBottomDp).value.let { kotlin.math.abs(it) } > 0.5f) {
                                    dialCircleBottomDp = newDial
                                }
                                if ((newInputs - inputsBottomDp).value.let { kotlin.math.abs(it) } > 0.5f) {
                                    inputsBottomDp = newInputs
                                }
                            },
                        )
                    }
                    // Snap points derived dynamically from layout
                    // measurements — scale across screen sizes,
                    // foldable transitions and font-scale changes.
                    val gapBelowChips = 2.dp
                    val gapBelowDial = 2.dp
                    val gapBelowInputs = 4.dp
                    val chipsSnapDp = (sheetParentHeightDp - chipsBottomDp - gapBelowChips)
                        .coerceAtLeast(56.dp)
                    val midSnapDp = (sheetParentHeightDp - dialCircleBottomDp - gapBelowDial)
                        .coerceAtLeast(56.dp)
                    val inputsSnapDp = (sheetParentHeightDp - inputsBottomDp - gapBelowInputs)
                        .coerceAtLeast(56.dp)
                    val fullSnapDp = sheetParentHeightDp.coerceAtLeast(chipsSnapDp)
                    StayAnywhereBottomSheet(
                        title = "Live equations",
                        // Ordered low to high sheet height:
                        //   P1 peek (56 dp)
                        //   P2 just below input boxes
                        //   P3 just below dial circle
                        //   P4 just below chip buttons (Nudge/Reset row)
                        //   P5 full extent
                        // The sheet de-duplicates and sorts internally.
                        snapHeightsDp = listOf(56.dp, inputsSnapDp, midSnapDp, chipsSnapDp, fullSnapDp),
                        topInsetDp = 0.dp,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 4.dp, vertical = 4.dp),
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

    if (showSyncMenu) {
        SyncSettingsSheet(
            syncEnabled = syncState.syncEnabled,
            partnerAvailable = syncState.partnerAvailable,
            onToggle = { syncState.setSyncEnabled(it) },
            onDismiss = { showSyncMenu = false },
        )
    }
}

/** Long-press settings sheet exposing the bezel-sync On/Off toggle. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SyncSettingsSheet(
    syncEnabled: Boolean,
    partnerAvailable: Boolean,
    onToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            androidx.compose.material3.Text(
                text = "Bezel sync",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    androidx.compose.material3.Text(
                        text = "Sync bezel with watch",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    androidx.compose.material3.Text(
                        text = if (partnerAvailable) "Watch connected" else "No watch detected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.Switch(
                    checked = syncEnabled,
                    onCheckedChange = onToggle,
                )
            }
            androidx.compose.material3.Text(
                text = "When on, turning the bezel here also turns it on your paired watch (and vice versa). Works only while both apps are installed and the watch is paired.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

/**
 * Portrait / compact-width dial column: presets + dial-with-corner-inputs.
 * The dial + inputs use a SubcomposeLayout that guarantees the inputs
 * never overlap the dial by construction.
 */
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
    glowAlpha: Float,
    vm: DialViewModel,
    onDialBottomYChanged: ((Float, Float, Float) -> Unit)? = null,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        CurvedPresets(
            onSetAngle = vm::setRotation,
            onReset = vm::reset,
            onNudge = vm::nudgeToNearestInteger,
            modifier = Modifier.fillMaxWidth()
        )

        DialWithCornerInputs(
            // capture dial-bottom-Y (presets height + dial diameter).
            dialBottomYReporter = onDialBottomYChanged,
            rotation = rotation,
            chronoState = chronoState,
            chronoMillisProvider = chronoMillisProvider,
            glowAlpha = glowAlpha,
            onBezelDrag = vm::rotateBy,
            onChronoStartStop = vm::chronoStartStop,
            onChronoReset = vm::chronoReset,
            bezelInputs = {
                BezelInputs(
                    outer = outerText,
                    inner = innerText,
                    onOuterChange = vm::setOuterText,
                    onInnerChange = vm::setInnerText,
                    onCommit = vm::commitInputs
                )
            },
            converterInputs = {
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
        )
    }
}

/**
 * Tablet / wide-canvas layout (single-pass SubcomposeLayout). Measures
 * the presets, dial, input panels and equations panel up-front, then
 * decides at layout time whether the inputs Row sits BELOW the dial in
 * the left column (preferred when there's enough vertical room — e.g.
 * Fold 7 unfolded at fontScale 1.0) or moves to the TOP of the right
 * (equations) column when the left column would otherwise overflow.
 *
 * Robust under:
 *   - foldable open / close (recomposes on parent maxWidth/maxHeight change)
 *   - system fontScale change (re-measures inputs at new sizes; the
 *     placement decision re-evaluates accordingly)
 *   - configuration changes (subcompose slot IDs are stable strings;
 *     ViewModel + rememberScrollState survive across recomposition)
 */
@Composable
private fun WideLayout(
    rotation: Double,
    chronoState: com.sliderulewatchguide.viewmodel.ChronoState,
    chronoMillisProvider: () -> Long,
    outerText: String,
    innerText: String,
    statText: String,
    nautText: String,
    kmText: String,
    equationsScroll: androidx.compose.foundation.ScrollState,
    glowAlpha: Float,
    vm: DialViewModel
) {
    SubcomposeLayout(modifier = Modifier.fillMaxSize().padding(12.dp)) { constraints ->
        val totalW = constraints.maxWidth
        val totalH = constraints.maxHeight
        val spacerPx = 12.dp.roundToPx()
        val colW = ((totalW - spacerPx) / 2).coerceAtLeast(0)
        val dialBelowGapPx = 8.dp.roundToPx()
        val inputsBelowMarginPx = 8.dp.roundToPx()
        val rightColInputsGapPx = 10.dp.roundToPx()

        val presetsP = subcompose("presets") {
            CurvedPresets(
                onSetAngle = vm::setRotation,
                onReset = vm::reset,
                onNudge = vm::nudgeToNearestInteger,
                modifier = Modifier.fillMaxWidth()
            )
        }.first().measure(Constraints(maxWidth = colW))

        val dialSize = colW
        val dialP = subcompose("dial") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .bezelDragRotation { vm.rotateBy(it) }
            ) {
                DialWithPushers(
                    side = dialSize.toDp(),
                    rotation = rotation,
                    chronoState = chronoState,
                    chronoMillisProvider = chronoMillisProvider,
                    onChronoStartStop = vm::chronoStartStop,
                    onChronoReset = vm::chronoReset,
                    glowAlpha = glowAlpha
                )
            }
        }.first().measure(Constraints.fixed(dialSize, dialSize))

        val inputsP = subcompose("inputs") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                BezelInputs(
                    outer = outerText,
                    inner = innerText,
                    onOuterChange = vm::setOuterText,
                    onInnerChange = vm::setInnerText,
                    onCommit = vm::commitInputs
                )
                Spacer(modifier = Modifier.weight(1f))
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
        }.first().measure(Constraints(maxWidth = colW))

        val leftStackH = presetsP.height + dialP.height + dialBelowGapPx +
            inputsP.height + inputsBelowMarginPx
        val inputsBelowDial = leftStackH <= totalH

        val rightTopUsed = if (inputsBelowDial) 0 else inputsP.height + rightColInputsGapPx
        val eqMaxH = (totalH - rightTopUsed).coerceAtLeast(0)
        val equationsP = subcompose("equations") {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(equationsScroll),
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
        }.first().measure(Constraints(maxWidth = colW, maxHeight = eqMaxH))

        layout(totalW, totalH) {
            presetsP.placeRelative(0, 0)
            dialP.placeRelative(0, presetsP.height)
            if (inputsBelowDial) {
                inputsP.placeRelative(0, presetsP.height + dialP.height + dialBelowGapPx)
            }
            val rightX = colW + spacerPx
            if (inputsBelowDial) {
                equationsP.placeRelative(rightX, 0)
            } else {
                inputsP.placeRelative(rightX, 0)
                equationsP.placeRelative(rightX, inputsP.height + rightColInputsGapPx)
            }
        }
    }
}

/**
 * Dial + the two chronograph-pusher tap targets, sized to the given [side].
 * Pulled out so the tablet branch (dial in its own aspect-ratio Box) and
 * the portrait SubcomposeLayout branch share the same content.
 */
@Composable
private fun DialWithPushers(
    side: androidx.compose.ui.unit.Dp,
    rotation: Double,
    chronoState: com.sliderulewatchguide.viewmodel.ChronoState,
    chronoMillisProvider: () -> Long,
    onChronoStartStop: () -> Unit,
    onChronoReset: () -> Unit,
    glowAlpha: Float = 0f
) {
    val haptics = LocalHapticFeedback.current
    WatchDial(
        bezelRotationDegrees = rotation,
        chronoState = chronoState,
        chronoMillisProvider = chronoMillisProvider,
        modifier = Modifier.fillMaxSize().syncGlow(glowAlpha)
    )
    // Top pusher tap target (start / stop)
    Box(modifier = Modifier
        .offset(x = side * 0.85f, y = side * 0.20f)
        .size(width = side * 0.13f, height = side * 0.13f)
        .clickable {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onChronoStartStop()
        })
    // Bottom pusher tap target (reset)
    Box(modifier = Modifier
        .offset(x = side * 0.85f, y = side * 0.67f)
        .size(width = side * 0.13f, height = side * 0.13f)
        .clickable {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onChronoReset()
        })
}

/**
 * Single-pass layout used in compact / portrait mode. Measures the two
 * input panels first, then sizes the container so the panels' inner
 * corners sit OUTSIDE a circle of radius (rOuter + safetyGap) centred
 * on the dial — guaranteed no-overlap by construction.
 */
@Composable
private fun DialWithCornerInputs(
    rotation: Double,
    chronoState: com.sliderulewatchguide.viewmodel.ChronoState,
    chronoMillisProvider: () -> Long,
    onBezelDrag: (Double) -> Unit,
    onChronoStartStop: () -> Unit,
    onChronoReset: () -> Unit,
    glowAlpha: Float,
    bezelInputs: @Composable () -> Unit,
    converterInputs: @Composable () -> Unit,
    // Reports three Y values used to seed the bottom sheet's snap
    // points (all in the layout's local coordinate space):
    //   1. Chip-column bottom Y (= dial-container top).
    //   2. Dial circle visible bottom Y.
    //   3. Dial container bottom Y (= just below the input rows).
    dialBottomYReporter: ((Float, Float, Float) -> Unit)? = null,
) {
    SubcomposeLayout(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (dialBottomYReporter != null) {
                    Modifier.onGloballyPositioned { coords ->
                        // Dial canvas: square, size = parentWidth.
                        // Visible bezel outer radius = 0.88 × halfDim,
                        // so the circle's bottom edge sits at
                        //   centreY + rOuter
                        //   = (parentWidth / 2) + 0.88 × (parentWidth / 2)
                        //   = 0.94 × parentWidth
                        // measured from the canvas's top.
                        val containerTopY = coords.positionInParent().y
                        // 3-tuple of layout Y values used by the bottom
                        // sheet to seed snap points:
                        //   - chipsBottomY: y of the chip-column's
                        //     bottom (= top of the dial container).
                        //   - circleBottomY: y of the visible dial
                        //     chrome rim bottom (~92 % of canvas).
                        //   - containerBottomY: y of the entire dial-
                        //     with-corner-inputs container's bottom
                        //     (just below the input rows).
                        val chipsBottomY = containerTopY
                        val circleBottomY = containerTopY + coords.size.width * 0.945f
                        val containerBottomY = containerTopY + coords.size.height
                        dialBottomYReporter(chipsBottomY, circleBottomY, containerBottomY)
                    }
                } else Modifier
            )
    ) { constraints ->
        val parentWidth = constraints.maxWidth
        check(parentWidth != Constraints.Infinity) {
            "DialWithCornerInputs requires bounded width"
        }

        val looseInputConstraints = Constraints(maxWidth = parentWidth / 2)
        val bezelP = subcompose("bezel") { bezelInputs() }
            .first().measure(looseInputConstraints)
        val converterP = subcompose("converter") { converterInputs() }
            .first().measure(looseInputConstraints)

        val dialSide = parentWidth
        val rOuter = (dialSide * 0.5f * 0.88f).toInt()
        val safetyPx = 12.dp.toPx().toInt()
        val safety = rOuter + safetyPx
        val centre = dialSide / 2
        val safetySq = safety.toLong() * safety.toLong()

        fun requiredHeight(w: Int, h: Int): Int {
            val dx = maxOf(0, centre - w)
            val dxSq = dx.toLong() * dx.toLong()
            if (dxSq >= safetySq) return dialSide
            val dyMin = kotlin.math.sqrt((safetySq - dxSq).toDouble()).toInt()
            return maxOf(dialSide, h + centre + dyMin)
        }
        val containerHeight = maxOf(
            requiredHeight(bezelP.width, bezelP.height),
            requiredHeight(converterP.width, converterP.height)
        )

        val sideDp = dialSide.toDp()
        val dialP = subcompose("dial") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .bezelDragRotation { onBezelDrag(it) }
            ) {
                DialWithPushers(
                    side = sideDp,
                    rotation = rotation,
                    chronoState = chronoState,
                    chronoMillisProvider = chronoMillisProvider,
                    onChronoStartStop = onChronoStartStop,
                    onChronoReset = onChronoReset,
                    glowAlpha = glowAlpha
                )
            }
        }.first().measure(Constraints.fixed(dialSide, dialSide))

        layout(parentWidth, containerHeight) {
            dialP.placeRelative(0, 0)
            bezelP.placeRelative(0, containerHeight - bezelP.height)
            converterP.placeRelative(
                parentWidth - converterP.width,
                containerHeight - converterP.height
            )
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
        // user must use the explicit "Dismiss" button so the dismissal
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
            // Layout: scrollable text region (takes weight 1f) above a
            // pinned Dismiss row at the bottom. At very high font scales
            // the text grows past one screen — the user can scroll it,
            // but the Dismiss button is always visible at the bottom so
            // the disclaimer is always reachable / dismissable.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
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
                }
                Spacer(Modifier.size(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    DisclaimerToggleChip(label = "Dismiss", onClick = onToggle)
                }
            }
        }
    }
    // Collapsed state intentionally renders nothing. The disclaimer
    // popup appears once at app launch and is dismissed by the user;
    // re-surfacing it would clutter the watch face area, and the
    // disclaimer text is preserved in the app's static documentation.
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
