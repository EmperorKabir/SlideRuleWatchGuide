package com.sliderulewatchguide.dial

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.sliderulewatchguide.R
import com.sliderulewatchguide.viewmodel.ChronoState
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.sin

/**
 * Three stacked layers:
 *  1. [StaticDial]      — dial face (no rotation, no live time).
 *  2. [RotatingBezel]   — outer slide-rule scale; whole layer rotated via
 *                         graphicsLayer rotationZ.
 *  3. [LiveHandsLayer]  — hour, minute, central red chronograph hand,
 *                         small running seconds, 30-min and 12-hr chrono
 *                         counters. Driven by the system clock and the
 *                         chronograph state.
 */
/** Hard ceiling on the system font-scale used for the dial face. Set to
 *  1.0 so the dial is completely invariant to Android's text-size
 *  accessibility setting: bezel numerals, chapter-ring numerals and
 *  brand stack render at their design size regardless of fontScale.
 *  At fontScale ≤ 1.0 there is no behavioural change; at fontScale > 1.0
 *  the dial is held at its design pixel size while text OUTSIDE the
 *  dial scope (equations panel, input boxes, presets) continues to
 *  respect the user's accessibility preference. */
private const val DIAL_FONT_SCALE_CEILING: Float = 1.0f

@Composable
fun WatchDial(
    bezelRotationDegrees: Double,
    chronoState: ChronoState,
    chronoMillisProvider: () -> Long,
    modifier: Modifier = Modifier
) {
    // Cap the font-scale just for the watch face. Outside this scope
    // (equations panel, preset chips, input fields) the system's actual
    // fontScale keeps applying unchanged.
    val systemDensity = LocalDensity.current
    val cappedDensity = remember(systemDensity) {
        val capped = systemDensity.fontScale.coerceAtMost(DIAL_FONT_SCALE_CEILING)
        if (capped == systemDensity.fontScale) systemDensity
        else Density(density = systemDensity.density, fontScale = capped)
    }
    CompositionLocalProvider(LocalDensity provides cappedDensity) {
        // CRITICAL: rememberTextMeasurer must be called INSIDE this scope
        // so it captures the capped density. Calling it ABOVE the
        // provider (in the parent composition) silently captures the
        // ORIGINAL system density and the cap is ignored. Confirmed via
        // Android documentation on CompositionLocalProvider behaviour.
        val measurer = rememberTextMeasurer()
        Box(
            modifier = modifier
                .aspectRatio(1f)
                .fillMaxSize()
        ) {
            StaticDial(measurer = measurer, modifier = Modifier.fillMaxSize())
            RotatingBezel(
                measurer = measurer,
                rotationDegrees = bezelRotationDegrees,
                modifier = Modifier.fillMaxSize()
            )
            LiveHandsLayer(
                chronoState = chronoState,
                chronoMillisProvider = chronoMillisProvider,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// =============================================================== layers

@Composable
private fun StaticDial(measurer: TextMeasurer, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val g = geom()
        drawCoinEdgeBaseplate(g)
        drawBezelInsertRecess(g)
        drawFixedChapterRing(g, measurer)
        drawDialBackground(g)
        drawSunburstOverlay(g)
        drawDialHighlight(g)
        drawMphLabel(g, measurer)
        // Central area intentionally left blank — no brand stack.
        drawSubDialFaces(g, measurer)
        drawDialHourIndices(g)
        drawCrownAndPushers(g)
        // (Inner-border backstop removed — with fontScale capped at 1.0
        //  and chapter-ring numerals centred at midR there is no longer
        //  any text bleed for it to mask, and as a visible 0.006·rOuter
        //  band straddling the dial/chapter-ring boundary it was adding a
        //  dark seam that didn't exist on the real watch.)
    }
}

@Composable
private fun RotatingBezel(
    measurer: TextMeasurer,
    rotationDegrees: Double,
    modifier: Modifier
) {
    Canvas(modifier = modifier.graphicsLayer { rotationZ = rotationDegrees.toFloat() }) {
        val g = geom()
        drawRotatingBezelScale(g, measurer)
    }
}

@Composable
private fun LiveHandsLayer(
    chronoState: ChronoState,
    chronoMillisProvider: () -> Long,
    modifier: Modifier
) {
    // Poll cadence must oversample the beat rate or the time-seconds hand
    // aliases visually (Nyquist). At 8 beats / second each beat is 125 ms,
    // so polling at 250 ms only catches roughly every second beat. Tying
    // the delay directly to BEATS_PER_SECOND keeps the visible tick rate
    // correct regardless of the constant: chrono running → 4× oversample,
    // chrono idle → 2× oversample plus margin for scheduler jitter.
    val idleDelayMs = (1000L / (BEATS_PER_SECOND.toLong() * 2L))
        .coerceAtLeast(30L)
    val runningDelayMs = (1000L / (BEATS_PER_SECOND.toLong() * 4L))
        .coerceAtLeast(16L)
    val nowState: State<LocalDateTime> = produceState(initialValue = currentLocalDateTime()) {
        while (true) {
            value = currentLocalDateTime()
            delay(if (chronoState == ChronoState.RUNNING) runningDelayMs else idleDelayMs)
        }
    }
    val now = nowState.value
    Canvas(modifier = modifier) {
        val g = geom()
        val chronoMs = chronoMillisProvider()
        drawSubDialSecondsHand(g, now)
        drawChronoMinAndHourHands(g, chronoMs)
        drawTimeHands(g, now)
        drawChronoSecondsHand(g, chronoMs)
        drawHandHub(g)
    }
}

private fun currentLocalDateTime(): LocalDateTime =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

// =============================================================== geometry

private data class DialGeom(
    val w: Float, val h: Float,
    val cx: Float, val cy: Float,
    val center: Offset,
    val rOuter: Float,                 // outer edge of chrome bezel (the case rim)
    val rBezelOuter: Float,            // outer edge of black slide-rule insert
    val rBezelInner: Float,            // inner edge of slide-rule insert
    val rChapterOuter: Float,
    val rChapterInner: Float,
    val rDial: Float
)

/*
 * ----------------------------------------------------------------------
 *  Watch-face proportion reference (all values relative to rOuter, the
 *  outermost chrome bezel edge). Derived from reference photos of a
 *  classic 46 mm pilot's chronograph.
 * ----------------------------------------------------------------------
 *
 *   Concentric rings (radius from centre):
 *     0.00 .. 0.71   green sunburst dial
 *     0.71 .. 0.84   inner FIXED slide-rule scale (the rehaut)
 *     0.84 .. 0.86   thin step / shadow between rings
 *     0.86 .. 0.99   rotating bezel insert (slide-rule numerals)
 *     0.99 .. 1.00   chrome coin-edge teeth
 *
 *   Hour indices (within the green dial):
 *     inner end  ≈ 0.43 r  (just outside brand wordmark + sub-dial cores)
 *     outer end  ≈ 0.69 r  (right at the inner edge of the chapter ring)
 *     width      ≈ 0.018 r
 *     skipped at 3 / 6 / 9 (sub-dial cores)
 *
 *   Sub-dials (inside the dial):
 *     centre offset from dial centre  ≈ 0.30 r
 *     sub-dial radius                ≈ 0.155 r
 *
 *   Date window (inside the 6 o'clock sub-dial):
 *     width   ≈ 0.030 r   (taller than wide — portrait aperture, ~0.6:1)
 *     height  ≈ 0.050 r
 *     centre below sub-dial centre by 0.07 r
 *     bg = black; text = white
 *
 *   Brand stack (removed): the central area between the upper sub-dial
 *   and the hub is intentionally left blank. No wordmarks, no logo,
 *   no curved bottom text.
 *
 *   Crown (3 o'clock):
 *     body rectangle: 0.09 r wide × 0.20 r tall, anchored at rOuter on right
 *     cap rectangle:  0.08 r wide × 0.18 r tall, sits OUTSIDE the body
 *     reeded grip stripes on body + cap face
 *
 *   Pushers (top: 2 o'clock = 60° from N; bottom: 4 o'clock = 120° from N):
 *     pusher axis is RADIAL (perpendicular to the case rim at that angle)
 *     shaft length ≈ 0.06 r, cap depth ≈ 0.05 r, cap face ≈ 0.05 r wide × 0.11 r tall
 *     reeded grip stripes parallel to the cap's long axis
 * ----------------------------------------------------------------------
 */
private fun DrawScope.geom(): DialGeom {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    // Shrink the watch a touch so the crown + angled pushers fit inside the
    // canvas (they protrude about 9% of r past the case at 2/3/4 o'clock).
    val rOuter = (minOf(w, h) / 2f) * 0.88f
    // Bezel-outer step now sits at 0.985 r (was 0.99 r) so the thicker
    // perimeter border (0.028 r) has clean radial room inside rOuter
    // without overpainting the bezel face. Visually it's a sub-pixel
    // change at typical dial sizes.
    val rBezelOuter = rOuter * 0.985f
    // Step gap between rotating bezel and fixed chapter ring tightened from
    // 0.02 r to 0.005 r so the outer and inner ticks visually almost meet
    // across a hairline step (per photo image 16).
    val rBezelInner = rOuter * 0.850f
    val rChapterOuter = rOuter * 0.845f
    val rChapterInner = rOuter * 0.71f
    val rDial = rChapterInner
    return DialGeom(w, h, cx, cy, Offset(cx, cy), rOuter, rBezelOuter, rBezelInner, rChapterOuter, rChapterInner, rDial)
}

// =============================================================== labels

/** Outer rotating bezel: every 5 in the upper half, every 1 in 10..25, every 5 in 30..55. */
private val OUTER_LABEL_SET: Set<Int> =
    (10..25).toSet() +
    setOf(30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95)

/** Inner fixed scale: 10..25 every integer, 30..55 every 5, plus 70/80/90
 *  abbreviated as 7/8/9 in the upper half. 60 is excluded (replaced by the
 *  white MPH arrow). 36 is excluded — the user asked for no red "36" text
 *  label on the inner bezel; the red triangle at 36 stays. */
private val INNER_LABEL_MAP: Map<Int, String> =
    (10..25).associateWith { it.toString() } +
    listOf(30, 35, 40, 45, 50, 55).associateWith { it.toString() } +
    mapOf(70 to "7", 80 to "8", 90 to "9")

/** Integer scale values where a RED TRIANGLE (or the white arrow at inner
 *  60) replaces the regular WHITE tick. NAUT triangle is at scale 33;
 *  STAT triangle is at scale 38 — both replace their white ticks. */
private val OUTER_TICK_REPLACED_BY_TRIANGLE: Set<Int> = setOf(10, 36, 60)
// 61 is added because the inner thin tick at integer 61 is replaced by
// the small red KM triangle (drawn explicitly below).
private val INNER_TICK_REPLACED_BY_TRIANGLE: Set<Int> = setOf(10, 33, 36, 38, 60, 61)
/** Inner numerals drawn in RED — only the unit index (10) now. */
private val INNER_RED_NUMERAL_VALUES: Set<Int> = setOf(10)

// =============================================================== ticks

private enum class TickRank { TALL, MEDIUM, SHORT }

/** Step between ticks at scale-value [v]. Four tiers transcribed from
 *  the user's definitive spec (images 29..33 of the real watch):
 *   - 10..12           → 0.1  (four thin between each pair of thick marks
 *                              at 10 / 10.5 / 11 / 11.5 / 12)
 *   - 12..25           → 0.2  (four thin between thick integers)
 *   - 25..60           → 0.5  (long thin integers + short thin halves)
 *   - 60..100          → 1.0  (integer marks only — no halves on this side)
 */
private fun stepAt(v: Double): Double = when {
    v < 12.0 -> 0.1
    v < 25.0 -> 0.2
    v < 60.0 -> 0.5
    else -> 1.0
}

private fun isInteger(v: Double): Boolean = kotlin.math.abs(v - round(v)) < 1e-6

/** Unlabelled scale values that still render as long+thick (TALL rank) on
 *  BOTH bezels — the half-mark thicks at 10.5 and 11.5 in the dense unit
 *  region. */
private val EXTRA_THICK_VALUES: Set<Double> = setOf(10.5, 11.5)
private fun isExtraThick(v: Double): Boolean =
    EXTRA_THICK_VALUES.any { kotlin.math.abs(v - it) < 1e-6 }

/** Inner-bezel-only thick integers — every-five values that are NOT
 *  labelled on the inner ring (60/65/75/85/95 sit between the labelled
 *  inner numerals 7/8/9 and the MPH index, but still render as thick
 *  marks on the real watch). 60 is excluded because it's already drawn
 *  as the MPH white arrow. */
private val INNER_EXTRA_THICK_INTEGERS: Set<Int> = setOf(65, 75, 85, 95)

private fun tickRank(v: Double, isThick: Boolean): TickRank {
    // TALL = long + thick. The caller decides what counts as thick — for
    // OUTER it's labelled values + the EXTRA_THICK_VALUES half-marks; for
    // INNER it's the same plus 65/75/85/95.
    if (isThick) return TickRank.TALL
    return when {
        // 10..25 sub-marks (the 0.1 / 0.2 fifth- and tenth-divisions) all
        // render SHORT — short + thin.
        v < 25.0 -> TickRank.SHORT
        // 25..60 unlabelled values: integers are LONG + thin (MEDIUM),
        // halves are SHORT + thin.
        v < 60.0 -> if (isInteger(v)) TickRank.MEDIUM else TickRank.SHORT
        // 60..100: stepAt = 1.0, so only integer marks reach this branch.
        // Unlabelled integers (61..64, 66..69, …) render LONG + thin.
        else -> TickRank.MEDIUM
    }
}

/** Ordered list of all tick values across one decade [10, 100). */
private fun allTickValues(): List<Double> {
    val out = mutableListOf<Double>()
    var v = 10.0
    while (v < 100.0 - 1e-9) {
        out += v
        v = round((v + stepAt(v)) * 1000.0) / 1000.0
    }
    return out
}

// =============================================================== chrome teeth

private fun DrawScope.drawCoinEdgeBaseplate(g: DialGeom) {
    val teeth = 90
    val rTip = g.rOuter
    val rBase = g.rOuter * 0.94f
    drawCircle(color = Color(0xFF0C0C0E), radius = g.rOuter, center = g.center)
    for (i in 0 until teeth) {
        val angle = i * (360.0 / teeth)
        val rad = angle * PI / 180.0
        val perpRad = rad + PI / 2
        val toothHalf = g.rOuter * 0.014f
        val tipX = g.center.x + (rTip * cos(rad)).toFloat()
        val tipY = g.center.y + (rTip * sin(rad)).toFloat()
        val baseX = g.center.x + (rBase * cos(rad)).toFloat()
        val baseY = g.center.y + (rBase * sin(rad)).toFloat()
        val px = (toothHalf * cos(perpRad)).toFloat()
        val py = (toothHalf * sin(perpRad)).toFloat()

        drawLine(color = DialPalette.SteelGroove,
            start = Offset(baseX + px * 1.3f, baseY + py * 1.3f),
            end = Offset(tipX + px * 1.3f, tipY + py * 1.3f),
            strokeWidth = toothHalf * 0.65f)
        drawLine(color = DialPalette.SteelLight,
            start = Offset(baseX, baseY),
            end = Offset(tipX, tipY),
            strokeWidth = toothHalf * 0.75f)
        drawLine(color = DialPalette.SteelMid,
            start = Offset(baseX - px * 0.6f, baseY - py * 0.6f),
            end = Offset(tipX - px * 0.6f, tipY - py * 0.6f),
            strokeWidth = toothHalf * 0.55f)
    }
    drawCircle(color = DialPalette.SteelLight, radius = g.rOuter, center = g.center, style = Stroke(width = 1.2f))
    drawCircle(color = DialPalette.BezelEdgeShadow, radius = rBase, center = g.center,
        style = Stroke(width = g.rOuter * 0.008f))
    // Perimeter border, coloured to match the bezel insert background
    // (BezelInsertBlack = 0xFF0B0B0B) so the rim, border and bezel face
    // read as one continuous dark surface rather than a black ring
    // ringed by a still-black ring. Doubled in thickness from the
    // original 0.012 r so it reads as a deliberate frame. Drawn UNDER
    // the crown / pushers, so those tabs visually break the border at
    // 2 and 4 o'clock instead of running straight through.
    drawCircle(color = DialPalette.BezelInsertBlack, radius = g.rOuter, center = g.center,
        style = Stroke(width = g.rOuter * 0.028f))
}

private fun DrawScope.drawBezelInsertRecess(g: DialGeom) {
    drawCircle(color = DialPalette.BezelInsertBlack, radius = g.rBezelOuter, center = g.center)
    drawCircle(color = Color(0xFF1B1B1B), radius = g.rBezelOuter, center = g.center,
        style = Stroke(width = g.rOuter * 0.006f))
    drawCircle(color = DialPalette.BezelEdgeShadow, radius = g.rBezelInner, center = g.center,
        style = Stroke(width = g.rOuter * 0.012f))
}

// =============================================================== rotating bezel scale (outer)

private fun DrawScope.drawRotatingBezelScale(g: DialGeom, measurer: TextMeasurer) {
    val ringMid = (g.rBezelOuter + g.rBezelInner) / 2f
    val ringWidth = g.rBezelOuter - g.rBezelInner

    // OUTER bezel layering. ALL ticks anchor at rBezelInner (the step
    // boundary) and grow OUTWARD by length-per-rank. This way every tick
    // — tall, medium, short — visually starts at the step, so when bezel
    // rotation = 0 the outer tick at scale X "touches" the inner tick at
    // scale X across the hairline step gap.
    val numeralR = ringMid + ringWidth * 0.32f
    val tickAnchor = g.rBezelInner
    // TALL and MEDIUM are the SAME length per the user's spec — both are
    // "long" marks on the real watch. The differentiator is stroke width:
    // TALL is thick (labelled values + 11.5), MEDIUM is thin (unlabelled
    // integers in 25..100). SHORT is for the half / fifth / tenth-marks.
    val tallLen = ringWidth * 0.55f
    val medLen = ringWidth * 0.55f
    val shortLen = ringWidth * 0.30f

    for (v in allTickValues()) {
        val intV = round(v).toInt()
        val isInt = isInteger(v)
        val skipTick = isInt && intV in OUTER_TICK_REPLACED_BY_TRIANGLE
        val isLabelled = isInt && intV in OUTER_LABEL_SET
        val isThick = isLabelled || isExtraThick(v)
        val rank = tickRank(v, isThick)
        val angle = DialMath.drawAngleDeg(v)
        val rad = angle * PI / 180.0

        // Draw the tick only if it isn't being replaced by a triangle.
        if (!skipTick) {
            val len = when (rank) {
                TickRank.TALL -> tallLen
                TickRank.MEDIUM -> medLen
                TickRank.SHORT -> shortLen
            }
            val sw = when (rank) {
                TickRank.TALL -> 2.0f
                TickRank.MEDIUM -> 0.9f
                TickRank.SHORT -> 0.75f
            }
            // tick anchored at rBezelInner (the step), grows OUTWARD by len
            val tickOuterR = tickAnchor + len
            val sx = g.center.x + (tickAnchor * cos(rad)).toFloat()
            val sy = g.center.y + (tickAnchor * sin(rad)).toFloat()
            val ex = g.center.x + (tickOuterR * cos(rad)).toFloat()
            val ey = g.center.y + (tickOuterR * sin(rad)).toFloat()
            drawLine(
                color = DialPalette.Numeral.copy(alpha = when (rank) {
                    TickRank.TALL -> 0.95f
                    TickRank.MEDIUM -> 0.85f
                    TickRank.SHORT -> 0.75f
                }),
                start = Offset(sx, sy), end = Offset(ex, ey),
                strokeWidth = sw
            )
        }
        // Draw the numeral whenever the value is labelled, even if the tick
        // was skipped — outer 10 / 36 / 60 keep their RED numerals next to
        // their red triangles (per images 22 and 24).
        if (isLabelled) {
            val isRed = (intV == 10 || intV == 36 || intV == 60)
            drawScaleNumeralUpright(
                measurer = measurer,
                text = intV.toString(),
                angleDegFromTop = angle.toFloat(),
                radius = numeralR,
                center = g.center,
                color = if (isRed) DialPalette.Red else DialPalette.Numeral,
                sizeSp = (g.rOuter * 0.090f / density).sp,
                bold = true
            )
        }
    }
    // Red triangles on outer at 10 / 36 / 60 — TIP at the step gap (just
    // past the bezel inner edge) so each triangle on the outer scale
    // visually TOUCHES the corresponding triangle on the inner scale at
    // the same value when the bezel is aligned (per image 21).
    listOf(10.0, 36.0, 60.0).forEach { v ->
        val angle = DialMath.drawAngleDeg(v)
        drawTriangleAtAngle(
            center = g.center, angleDeg = angle.toFloat(),
            radius = ringMid - ringWidth * 0.376f,    // base inside bezel ring's lower half
            size = ringWidth * 0.18f,
            color = DialPalette.Red, inward = true     // tip ≈ 0.847 r
        )
    }
}

// =============================================================== fixed chapter ring (inner)

private fun DrawScope.drawFixedChapterRing(g: DialGeom, measurer: TextMeasurer) {
    val midR = (g.rChapterOuter + g.rChapterInner) / 2f
    val width = g.rChapterOuter - g.rChapterInner

    drawCircle(color = Color(0xFF050505), radius = g.rChapterOuter, center = g.center)
    drawCircle(color = Color(0xFF1F1F1F), radius = g.rChapterOuter, center = g.center,
        style = Stroke(width = width * 0.05f))

    // INNER chapter ring. ALL ticks anchor at rChapterOuter (the step
    // boundary) and grow INWARD by length-per-rank — so each inner tick
    // touches the matching outer tick at the same scale value across the
    // hairline step gap, regardless of tick rank.
    // Numerals centred on the band (midR). Photo-faithful: on real
    // chronograph chapter rings the inner-rehaut numerals sit roughly
    // centred in the annulus. Previous value 0.20f put the numeral
    // centre only 0.027·rOuter above rChapterInner, less than the glyph
    // half-height (~0.039·rOuter) — so the digit tail extended past
    // rChapterInner and was overpainted by drawDialBackground.
    val numeralR = g.rChapterInner + width * 0.50f
    val tickOuterR = g.rChapterOuter
    // TALL and MEDIUM are the SAME length on inner too (matches the
    // outer-bezel rule and the photo). Stroke width is the differentiator.
    val tallLen = width * 0.55f
    val medLen = width * 0.55f
    val shortLen = width * 0.30f

    for (v in allTickValues()) {
        val intV = round(v).toInt()
        val isInt = isInteger(v)
        val skipTick = isInt && intV in INNER_TICK_REPLACED_BY_TRIANGLE
        val isLabelled = isInt && intV in INNER_LABEL_MAP
        val isThick = isLabelled || isExtraThick(v) ||
            (isInt && intV in INNER_EXTRA_THICK_INTEGERS)
        val rank = tickRank(v, isThick)
        val angle = DialMath.drawAngleDeg(v)
        val rad = angle * PI / 180.0

        if (!skipTick) {
            val len = when (rank) {
                TickRank.TALL -> tallLen
                TickRank.MEDIUM -> medLen
                TickRank.SHORT -> shortLen
            }
            val sw = when (rank) {
                TickRank.TALL -> 1.8f
                TickRank.MEDIUM -> 0.85f
                TickRank.SHORT -> 0.7f
            }
            val tickInnerR = tickOuterR - len
            val sx = g.center.x + (tickInnerR * cos(rad)).toFloat()
            val sy = g.center.y + (tickInnerR * sin(rad)).toFloat()
            val ex = g.center.x + (tickOuterR * cos(rad)).toFloat()
            val ey = g.center.y + (tickOuterR * sin(rad)).toFloat()
            drawLine(
                color = DialPalette.Numeral.copy(alpha = when (rank) {
                    TickRank.TALL -> 0.9f
                    TickRank.MEDIUM -> 0.8f
                    TickRank.SHORT -> 0.7f
                }),
                start = Offset(sx, sy), end = Offset(ex, ey),
                strokeWidth = sw
            )
        }
        // Always draw the numeral if this value is labelled, even if the
        // tick was skipped (so red 10 / 36 keep their numerals next to the
        // triangle markers, per image 24).
        if (isLabelled) {
            val text = INNER_LABEL_MAP.getValue(intV)
            val isRed = intV in INNER_RED_NUMERAL_VALUES
            drawScaleNumeralUpright(
                measurer = measurer,
                text = text,
                angleDegFromTop = angle.toFloat(),
                radius = numeralR,
                center = g.center,
                color = if (isRed) DialPalette.Red else DialPalette.Numeral,
                sizeSp = (g.rOuter * 0.078f / density).sp,
                bold = true
            )
        }
    }

    // Inner red-triangle markers — TIPS land at the step gap (~0.847 r) so
    // they touch the matching outer triangles when aligned (per image 21).
    // The triangle base is the smaller-radius end; tip points outward
    // toward the bezel.
    val triangleBaseR = midR + width * 0.341f
    val triangleSize = width * 0.18f
    val triangleSizeSmall = width * 0.14f

    // KM / STAT triangle / NAUT triangle / "NAUT." text / "STAT." text.
    // MPH (60) is filtered OUT so we can draw the white-arrow index manually.
    val markerLabelR = numeralR + width * 0.22f
    Markers.all
        .filter { it.side == ScaleSide.INNER && it.style != MarkerStyle.RED_NUMERAL }
        .filter { kotlin.math.abs(it.scaleValue - DialMath.RED_60_MPH) > 1e-6 }
        .forEach { m ->
            val angle = DialMath.drawAngleDeg(m.scaleValue)
            if (m.style == MarkerStyle.TRIANGLE_OUTWARD) {
                drawTriangleAtAngle(
                    center = g.center, angleDeg = angle.toFloat(),
                    radius = triangleBaseR, size = triangleSize,
                    color = DialPalette.Red, inward = false
                )
            }
            m.text?.let { txt ->
                // Sta and Nau render 30 % larger than the other red-text
                // markers (KM); they're the conversion-anchor labels and
                // the user wants them more prominent.
                val sizeFactor = if (txt == "Sta" || txt == "Nau") 1.30f else 1.0f
                drawScaleNumeralUpright(
                    measurer = measurer,
                    text = txt,
                    angleDegFromTop = angle.toFloat(),
                    radius = markerLabelR,
                    center = g.center,
                    color = DialPalette.Red,
                    sizeSp = (g.rOuter * 0.038f * sizeFactor / density).sp,
                    bold = true
                )
            }
        }

    // Single red triangle at inner 10 — the unit index. No bracketing pair,
    // no numeral — just one triangle, matching image 20's "two triangles
    // total" instruction (one outer + one inner).
    val red10Angle = DialMath.drawAngleDeg(10.0)
    drawTriangleAtAngle(
        center = g.center, angleDeg = red10Angle.toFloat(),
        radius = triangleBaseR, size = triangleSize,
        color = DialPalette.Red, inward = false
    )

    // Red triangle at inner 36 — the time-conversion marker.
    val red36Angle = DialMath.drawAngleDeg(36.0)
    drawTriangleAtAngle(
        center = g.center, angleDeg = red36Angle.toFloat(),
        radius = triangleBaseR, size = triangleSizeSmall,
        color = DialPalette.Red, inward = false
    )

    // Small red KM triangle at inner 61 — replaces the thin tick at 61
    // (per user spec). The "KM" red text label is drawn separately by
    // the markers loop above, sitting just above this triangle.
    val kmAngle = DialMath.drawAngleDeg(DialMath.KM_MARKER)
    drawTriangleAtAngle(
        center = g.center, angleDeg = kmAngle.toFloat(),
        radius = triangleBaseR, size = triangleSizeSmall,
        color = DialPalette.Red, inward = false
    )

    // White MPH arrow at inner 60 — replaces the red triangle (per image 22).
    // The "MPH" text label is drawn separately by [drawMphLabel] AFTER
    // [drawDialBackground], otherwise the green dial paints over it.
    val mphAngle = DialMath.drawAngleDeg(60.0)
    drawMphArrow(g, mphAngle)
}

/**
 * "MPH" caption sits just inside the chapter-ring inner edge, on the green
 * dial, directly below the white MPH arrow. Must be drawn AFTER the dial
 * background / sunburst layers so it isn't painted over.
 */
private fun DrawScope.drawMphLabel(g: DialGeom, measurer: TextMeasurer) {
    val mphAngle = DialMath.drawAngleDeg(60.0)
    // Pushed further inward (closer to dial centre) so the new RED
    // MPH up-arrow has clear room above it.
    // MPH caption sits JUST under the red arrow's base. Offset reduced
    // from 0.085 to 0.0135 r·outer — leaves a very thin sliver of dial
    // visible between the bottom of the red arrow and the top of the
    // MPH text. The 12 o'clock hour marker (drawn elsewhere) is
    // extended upward by the same delta so its tip still meets the
    // bottom of the MPH text cleanly.
    val mphTextR = g.rChapterInner - g.rOuter * 0.0135f
    drawScaleNumeralUpright(
        measurer = measurer,
        text = "MPH",
        angleDegFromTop = mphAngle.toFloat(),
        radius = mphTextR,
        center = g.center,
        color = DialPalette.Numeral,
        sizeSp = (g.rOuter * 0.044f / density).sp,
        bold = true
    )
}

/**
 * Curved-sides white arrow at inner 60 (the MPH index). Tip points OUTWARD;
 * sides bow outward via quadratic Beziers, giving the bulbous winged-arrow
 * shape from photo image 22.
 */
private fun DrawScope.drawMphArrow(g: DialGeom, angleDeg: Double) {
    // RED isoceles triangle pointing OUTWARD (= up at 12 o'clock).
    // Matches the visual language of the other red triangle markers on
    // the dial (STAT, NAUT, KM, 10, 36) instead of the white instrument
    // arrow it used to be.
    val width = g.rChapterOuter - g.rChapterInner
    val rad = angleDeg * PI / 180.0
    val cosA = cos(rad).toFloat()
    val sinA = sin(rad).toFloat()
    val perpCosA = -sinA
    val perpSinA = cosA

    val tipR = g.rChapterOuter
    val baseR = g.rChapterInner + width * 0.10f
    val baseHalfW = width * 0.16f

    val tipX = g.center.x + tipR * cosA
    val tipY = g.center.y + tipR * sinA
    val baseLeftX = g.center.x + baseR * cosA + baseHalfW * perpCosA
    val baseLeftY = g.center.y + baseR * sinA + baseHalfW * perpSinA
    val baseRightX = g.center.x + baseR * cosA - baseHalfW * perpCosA
    val baseRightY = g.center.y + baseR * sinA - baseHalfW * perpSinA

    val path = Path().apply {
        moveTo(tipX, tipY)
        lineTo(baseLeftX, baseLeftY)
        lineTo(baseRightX, baseRightY)
        close()
    }
    drawPath(path, color = DialPalette.Red)
}

// =============================================================== dial background

private fun DrawScope.drawDialBackground(g: DialGeom) {
    val brush = Brush.radialGradient(
        colors = listOf(DialPalette.DialGreenInner, DialPalette.DialGreenSpokeDark, DialPalette.DialGreenOuter),
        center = g.center, radius = g.rDial
    )
    drawCircle(brush = brush, radius = g.rDial, center = g.center)
}

private fun DrawScope.drawSunburstOverlay(g: DialGeom) {
    val spokes = 240
    val rIn = g.rDial * 0.04f
    val rOut = g.rDial * 0.98f
    for (i in 0 until spokes) {
        val angle = i * (360.0 / spokes)
        val rad = angle * PI / 180.0
        val sx = g.center.x + (rIn * cos(rad)).toFloat()
        val sy = g.center.y + (rIn * sin(rad)).toFloat()
        val ex = g.center.x + (rOut * cos(rad)).toFloat()
        val ey = g.center.y + (rOut * sin(rad)).toFloat()
        val light = i % 2 == 0
        drawLine(
            color = if (light) DialPalette.DialGreenSpokeLight.copy(alpha = 0.18f)
                    else DialPalette.DialGreenSpokeDark.copy(alpha = 0.20f),
            start = Offset(sx, sy), end = Offset(ex, ey),
            strokeWidth = 1.0f
        )
    }
}

private fun DrawScope.drawDialHighlight(g: DialGeom) {
    val glow = Brush.radialGradient(
        colors = listOf(Color(0x33FFFFFF), Color(0x11FFFFFF), Color(0x00FFFFFF)),
        center = Offset(g.center.x - g.rDial * 0.45f, g.center.y - g.rDial * 0.55f),
        radius = g.rDial * 0.85f
    )
    drawCircle(brush = glow, radius = g.rDial, center = g.center)
}

// =============================================================== brand marks
//
// The Slide Rule Watch Guide dial intentionally leaves the central area
// blank — no wordmarks, no logo, no curved bottom text. This keeps the
// face brand-neutral and avoids any third-party trade-dress concerns.

private fun DrawScope.drawCenteredText(
    measurer: TextMeasurer, text: String, style: TextStyle, centerTopLeft: Offset
) {
    val l = measurer.measure(androidx.compose.ui.text.AnnotatedString(text), style)
    drawText(textLayoutResult = l,
        topLeft = Offset(centerTopLeft.x - l.size.width / 2f, centerTopLeft.y))
}

// =============================================================== sub-dials

private fun DrawScope.drawSubDialFaces(g: DialGeom, measurer: TextMeasurer) {
    val subR = g.rDial * 0.26f
    val offset = g.rDial * 0.42f
    // Sub-dial layout rotated ONE POSITION CLOCKWISE relative to the
    // previous layout: the function that USED to live at 9 o'clock is
    // now at 3 o'clock, what was at 3 is now at 6, what was at 6 is now
    // at 9.
    //
    //   9 o'clock  → 12-hr chronograph counter + date window
    //   3 o'clock  → running seconds (60-face)
    //   6 o'clock  → 30-minute chronograph counter
    val hrCenter = Offset(g.center.x - offset, g.center.y)
    drawSubDialFace(
        center = hrCenter, radius = subR,
        ticks = 12, majorEvery = 3, measurer = measurer,
        // Larger numerals at 12 / 3 / 9, smaller numerals at every other
        // hour position EXCEPT 5 / 6 / 7 (which sit behind the date window).
        ringNumbers = listOf(3 to "3", 9 to "9", 12 to "12"),
        smallNumbers = listOf(1 to "1", 2 to "2", 4 to "4", 8 to "8", 10 to "10", 11 to "11")
    )
    drawSubDialFace(
        center = Offset(g.center.x + offset, g.center.y), radius = subR,
        ticks = 60, majorEvery = 5, measurer = measurer,
        // 60-face running seconds: numerals at every ten.
        ringNumbers = listOf(60 to "60", 10 to "10", 20 to "20", 30 to "30", 40 to "40", 50 to "50")
    )
    drawSubDialFace(
        center = Offset(g.center.x, g.center.y + offset), radius = subR,
        ticks = 30, majorEvery = 5, measurer = measurer,
        // 30-min counter: numerals at every five.
        ringNumbers = listOf(5 to "5", 10 to "10", 15 to "15", 20 to "20", 25 to "25", 30 to "30")
    )
    // Date window inside the 12-hr sub-dial (which now sits at 9 o'clock).
    val now = currentLocalDateTime()
    val dateBoxW = subR * 0.42f
    val dateBoxH = subR * 0.50f
    val dateTopLeft = Offset(hrCenter.x - dateBoxW / 2f, hrCenter.y + subR * 0.30f)
    drawRect(color = DialPalette.HandFrame,
        topLeft = Offset(dateTopLeft.x - 1.4f, dateTopLeft.y - 1.4f),
        size = Size(dateBoxW + 2.8f, dateBoxH + 2.8f))
    drawRect(color = Color(0xFF0A0A0A), topLeft = dateTopLeft, size = Size(dateBoxW, dateBoxH))
    val l = measurer.measure(
        androidx.compose.ui.text.AnnotatedString(now.dayOfMonth.toString()),
        TextStyle(color = Color.White, fontSize = (subR * 0.42f / density).sp,
            fontWeight = FontWeight.SemiBold)
    )
    drawText(textLayoutResult = l,
        topLeft = Offset(
            dateTopLeft.x + (dateBoxW - l.size.width) / 2f,
            dateTopLeft.y + (dateBoxH - l.size.height) / 2f
        ))
}

private fun DrawScope.drawSubDialFace(
    center: Offset, radius: Float, ticks: Int, majorEvery: Int,
    ringNumbers: List<Pair<Int, String>>, measurer: TextMeasurer,
    smallNumbers: List<Pair<Int, String>> = emptyList()
) {
    drawCircle(color = DialPalette.SubdialBlack, radius = radius, center = center)
    drawCircle(color = Color(0xFF1A1A1A), radius = radius, center = center,
        style = Stroke(width = radius * 0.04f))
    // Concentric guilloché rings
    val rings = 14
    for (k in 1..rings) {
        val r = radius * (0.06f + 0.86f * k / rings)
        drawCircle(color = DialPalette.SubdialAzureTick.copy(alpha = 0.55f),
            radius = r, center = center, style = Stroke(width = 0.6f))
    }
    for (i in 0 until ticks) {
        val angle = i * (360.0 / ticks) - 90.0
        val rad = angle * PI / 180.0
        val isMajor = i % majorEvery == 0
        val rIn = radius * (if (isMajor) 0.78f else 0.86f)
        val rOut = radius * 0.94f
        val sx = center.x + (rIn * cos(rad)).toFloat()
        val sy = center.y + (rIn * sin(rad)).toFloat()
        val ex = center.x + (rOut * cos(rad)).toFloat()
        val ey = center.y + (rOut * sin(rad)).toFloat()
        drawLine(color = DialPalette.SubdialTick.copy(alpha = if (isMajor) 0.95f else 0.5f),
            start = Offset(sx, sy), end = Offset(ex, ey),
            strokeWidth = if (isMajor) radius * 0.025f else radius * 0.012f)
    }
    for ((tick, txt) in ringNumbers) {
        val angle = tick * (360.0 / ticks) - 90.0
        val rad = angle * PI / 180.0
        val rL = radius * 0.62f
        val tx = center.x + (rL * cos(rad)).toFloat()
        val ty = center.y + (rL * sin(rad)).toFloat()
        val l = measurer.measure(
            androidx.compose.ui.text.AnnotatedString(txt),
            TextStyle(color = DialPalette.Numeral, fontSize = (radius * 0.30f / density).sp,
                fontWeight = FontWeight.SemiBold)
        )
        drawText(textLayoutResult = l,
            topLeft = Offset(tx - l.size.width / 2f, ty - l.size.height / 2f))
    }
    // Smaller numerals at non-major positions (e.g., 1/2/4/8/10/11 on the
    // 12-hr counter). Drawn at ~55 % the size of the major numerals.
    for ((tick, txt) in smallNumbers) {
        val angle = tick * (360.0 / ticks) - 90.0
        val rad = angle * PI / 180.0
        val rL = radius * 0.66f
        val tx = center.x + (rL * cos(rad)).toFloat()
        val ty = center.y + (rL * sin(rad)).toFloat()
        val l = measurer.measure(
            androidx.compose.ui.text.AnnotatedString(txt),
            TextStyle(color = DialPalette.Numeral.copy(alpha = 0.85f),
                fontSize = (radius * 0.17f / density).sp,
                fontWeight = FontWeight.Medium)
        )
        drawText(textLayoutResult = l,
            topLeft = Offset(tx - l.size.width / 2f, ty - l.size.height / 2f))
    }
    drawCircle(color = DialPalette.Hand, radius = radius * 0.05f, center = center)
}

private fun DrawScope.drawSubDialHand(
    center: Offset, length: Float, angleDeg: Float, color: Color, thickness: Float
) {
    val rad = (angleDeg - 90.0) * PI / 180.0
    val ex = center.x + (length * cos(rad)).toFloat()
    val ey = center.y + (length * sin(rad)).toFloat()
    drawLine(color = color, start = center, end = Offset(ex, ey), strokeWidth = thickness)
}

/** Beats per second of a 28 800 vph mechanical movement (28 800 vph = 4 Hz =
 *  8 beats / second; each beat advances the seconds hand by 1/8 second). */
private const val BEATS_PER_SECOND: Double = 8.0

private fun DrawScope.drawSubDialSecondsHand(g: DialGeom, now: LocalDateTime) {
    val subR = g.rDial * 0.26f
    val offset = g.rDial * 0.42f
    // Running seconds sub-dial is now at 3 o'clock (rotated one position
    // clockwise from its previous 9 o'clock placement).
    val secondsCenter = Offset(g.center.x + offset, g.center.y)
    val raw = now.second + now.nanosecond / 1e9
    val ticked = floor(raw * BEATS_PER_SECOND) / BEATS_PER_SECOND
    val angle = (ticked * 6.0).toFloat()
    drawSubDialHand(secondsCenter, subR * 0.85f, angle, DialPalette.Hand, subR * 0.04f)
}

private fun DrawScope.drawChronoMinAndHourHands(g: DialGeom, chronoMs: Long) {
    val subR = g.rDial * 0.26f
    val offset = g.rDial * 0.42f
    val totalSec = chronoMs / 1000.0
    val minutes = (totalSec / 60.0) % 30.0           // 30-min counter
    val hours = (totalSec / 3600.0) % 12.0           // 12-hr counter
    val minAngle = (minutes / 30.0 * 360.0).toFloat()
    val hrAngle = (hours / 12.0 * 360.0).toFloat()
    // 30-min counter is now at 6 o'clock; 12-hr counter is now at 9 o'clock.
    drawSubDialHand(
        center = Offset(g.center.x, g.center.y + offset),
        length = subR * 0.80f, angleDeg = minAngle,
        color = DialPalette.Hand, thickness = subR * 0.04f
    )
    drawSubDialHand(
        center = Offset(g.center.x - offset, g.center.y),
        length = subR * 0.78f, angleDeg = hrAngle,
        color = DialPalette.Hand, thickness = subR * 0.04f
    )
}

// =============================================================== applied hour indices

private fun DrawScope.drawDialHourIndices(g: DialGeom) {
    // ALL hour markers share the SAME length as the (formerly short) 6
    // o'clock baton, and they all render in a coral-red shade that
    // complements the burgundy dial. 12 o'clock is a thin single line,
    // shortened further so the red MPH up-arrow + MPH text on the
    // chapter ring above remain fully legible.
    val rOut = g.rDial * 0.94f
    val rInShort = g.rDial * 0.74f
    val width = g.rDial * 0.022f
    val markerColor = Color(0xFFE85A45)   // coral red — complements burgundy

    fun drawMarker(angle: Double, rIn: Float, rOut: Float, w: Float) {
        val rad = angle * PI / 180.0
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()
        val perpX = (-sinA) * w
        val perpY = cosA * w
        val tipX = g.center.x + (rOut * cos(rad)).toFloat()
        val tipY = g.center.y + (rOut * sin(rad)).toFloat()
        val baseX = g.center.x + (rIn * cos(rad)).toFloat()
        val baseY = g.center.y + (rIn * sin(rad)).toFloat()
        val path = Path().apply {
            moveTo(tipX + perpX, tipY + perpY)
            lineTo(tipX - perpX, tipY - perpY)
            lineTo(baseX - perpX, baseY - perpY)
            lineTo(baseX + perpX, baseY + perpY)
            close()
        }
        drawPath(path = path, color = markerColor)
    }

    for (h in 0 until 12) {
        val angle = h * 30.0 - 90.0
        if (h == 0) {
            // 12 o'clock: single thin line. Outer end extended from
            // 0.82 → 0.92 r·dial so its tip meets the bottom of the
            // (now-raised) MPH text. Same Δ as the MPH-text upward move
            // in drawMphLabel, so the visual relationship between the
            // marker and the caption stays consistent.
            drawMarker(angle, rInShort, g.rDial * 0.92f, width * 0.28f)
        } else {
            drawMarker(angle, rInShort, rOut, width * 0.85f)
        }
    }
}

// =============================================================== hands (time + chrono)

private fun DrawScope.drawTimeHands(g: DialGeom, now: LocalDateTime) {
    val s = now.second + now.nanosecond / 1e9
    val mFull = now.minute + s / 60.0
    val hFull = (now.hour % 12) + mFull / 60.0
    val hourAngle = (hFull * 30.0).toFloat()
    val minAngle = (mFull * 6.0).toFloat()
    // Hour: sword / dauphine taper (wide at the base, narrows to a point).
    // Minute: long sword for the same family. Both are distinct from the
    // baton hands of typical slide-rule chronographs.
    drawSwordHand(g.center, hourAngle,
        length = g.rDial * 0.58f, baseHalfW = g.rDial * 0.040f)
    drawSwordHand(g.center, minAngle,
        length = g.rDial * 0.92f, baseHalfW = g.rDial * 0.030f)
}

/** Sword / dauphine hand: a centred triangle that narrows from the hub
 *  out to a point at the tip. Inset highlight stripe gives the chamfered
 *  three-dimensional reading found on classic dress-watch hands. */
private fun DrawScope.drawSwordHand(
    center: Offset, angleDeg: Float, length: Float, baseHalfW: Float
) {
    val rad = (angleDeg - 90.0) * PI / 180.0
    val cosA = cos(rad).toFloat()
    val sinA = sin(rad).toFloat()
    val perpX = -sinA
    val perpY = cosA
    val tipX = center.x + length * cosA
    val tipY = center.y + length * sinA
    val outerPath = Path().apply {
        moveTo(center.x + perpX * baseHalfW, center.y + perpY * baseHalfW)
        lineTo(tipX, tipY)
        lineTo(center.x - perpX * baseHalfW, center.y - perpY * baseHalfW)
        close()
    }
    drawPath(outerPath, color = DialPalette.Hand)
    drawPath(outerPath, color = DialPalette.HandFrame, style = Stroke(width = 1.0f))
    // Centre highlight: thinner triangle running the same length.
    val insetHalfW = baseHalfW * 0.45f
    val insetPath = Path().apply {
        moveTo(center.x + perpX * insetHalfW, center.y + perpY * insetHalfW)
        lineTo(tipX, tipY)
        lineTo(center.x - perpX * insetHalfW, center.y - perpY * insetHalfW)
        close()
    }
    drawPath(insetPath, color = DialPalette.HandFrame.copy(alpha = 0.65f))
}

/**
 * Central red chronograph seconds hand. Reference: user's image #6 crop.
 * Composition (from tip to tail):
 *   • Long red NEEDLE from the hub to ~0.66 r tip.
 *   • Short CHROME counterweight stem extending the OPPOSITE direction
 *     from the hub by ~0.14 r (matches the photo — the stem below the
 *     hub is silver, not red).
 *   • Small RED disc at the very end of the chrome stem.
 */
private fun DrawScope.drawChronoSecondsHand(g: DialGeom, chronoMs: Long) {
    // Same 8 Hz beat as the running seconds — the B01 chrono escapement
    // shares the main balance, so the central red hand also ticks 8x/sec.
    val rawSec = chronoMs / 1000.0
    val tickedSec = floor(rawSec * BEATS_PER_SECOND) / BEATS_PER_SECOND
    val secs = tickedSec % 60.0
    val angleDeg = (secs * 6.0)
    val rad = (angleDeg - 90.0) * PI / 180.0
    val cosA = cos(rad).toFloat()
    val sinA = sin(rad).toFloat()

    // Red needle (hub → tip)
    val tipLen = g.rDial * 0.66f
    val tipX = g.center.x + tipLen * cosA
    val tipY = g.center.y + tipLen * sinA
    drawLine(
        color = DialPalette.SecondHand,
        start = g.center,
        end = Offset(tipX, tipY),
        strokeWidth = g.rDial * 0.013f
    )

    // Chrome counterweight stem (hub → tail, OPPOSITE direction).
    // Per image 19: the stem is silver and TAPERS slightly outward toward
    // the tail (no red dot), like a small chrome flare at the end.
    val tailLen = g.rDial * 0.16f
    val tailX = g.center.x - tailLen * cosA
    val tailY = g.center.y - tailLen * sinA
    val perpX = -sinA
    val perpY = cosA
    val widthHubOuter = g.rDial * 0.012f
    val widthTailOuter = g.rDial * 0.020f
    val widthHubInner = g.rDial * 0.008f
    val widthTailInner = g.rDial * 0.014f
    val stemFrame = Path().apply {
        moveTo(g.center.x + perpX * widthHubOuter, g.center.y + perpY * widthHubOuter)
        lineTo(g.center.x - perpX * widthHubOuter, g.center.y - perpY * widthHubOuter)
        lineTo(tailX - perpX * widthTailOuter, tailY - perpY * widthTailOuter)
        lineTo(tailX + perpX * widthTailOuter, tailY + perpY * widthTailOuter)
        close()
    }
    drawPath(stemFrame, color = DialPalette.HandFrame)
    val stemInner = Path().apply {
        moveTo(g.center.x + perpX * widthHubInner, g.center.y + perpY * widthHubInner)
        lineTo(g.center.x - perpX * widthHubInner, g.center.y - perpY * widthHubInner)
        lineTo(tailX - perpX * widthTailInner, tailY - perpY * widthTailInner)
        lineTo(tailX + perpX * widthTailInner, tailY + perpY * widthTailInner)
        close()
    }
    drawPath(stemInner, color = DialPalette.Hand)
}

private fun DrawScope.drawHandHub(g: DialGeom) {
    drawCircle(color = DialPalette.HandFrame, radius = g.rDial * 0.030f, center = g.center)
    drawCircle(color = DialPalette.Hand, radius = g.rDial * 0.025f, center = g.center)
    drawCircle(color = DialPalette.SecondHand, radius = g.rDial * 0.012f, center = g.center)
}

private fun DrawScope.drawBatonHand(
    center: Offset, angleDeg: Float, length: Float, outerW: Float, innerW: Float
) {
    val rad = (angleDeg - 90.0) * PI / 180.0
    val cosA = cos(rad).toFloat()
    val sinA = sin(rad).toFloat()
    val tipX = center.x + length * cosA
    val tipY = center.y + length * sinA
    val backLen = length * 0.18f
    val backX = center.x - backLen * cosA
    val backY = center.y - backLen * sinA
    fun handPath(w: Float): Path {
        val perpX = -sinA * w
        val perpY = cosA * w
        val tipFlatLen = length * 0.08f
        val tipPreX = center.x + (length - tipFlatLen) * cosA
        val tipPreY = center.y + (length - tipFlatLen) * sinA
        return Path().apply {
            moveTo(backX + perpX, backY + perpY)
            lineTo(backX - perpX, backY - perpY)
            lineTo(tipPreX - perpX, tipPreY - perpY)
            lineTo(tipX, tipY)
            lineTo(tipPreX + perpX, tipPreY + perpY)
            close()
        }
    }
    drawPath(handPath(outerW), color = DialPalette.HandFrame)
    drawPath(handPath(innerW), color = DialPalette.Lume)
    drawPath(handPath(outerW), color = Color(0xFF1A1A1A), style = Stroke(width = 0.8f))
}

// =============================================================== crown + pushers (decorative)

private fun DrawScope.drawCrownAndPushers(g: DialGeom) {
    // Each control's INNER edge (where the shaft meets the case rim)
    // sits exactly at the OUTER edge of the perimeter border, so the
    // whole control is OUTSIDE the watch face and does not overlap
    // the bezel scale. Border = 0.024 r stroke centred on rOuter, so
    // its outer edge is at rOuter × 1.012.
    // Border outer edge moved to rOuter × 1.014 to match the thickened
    // perimeter border (now 0.028 r). Crown/pushers anchor here and
    // grow OUTWARD from the watch face, not inward — keeping them
    // entirely outside the bezel scale.
    val anchorR = g.rOuter * 1.014f
    drawAngledChronoControl(g, angleFromNorthDeg = 60.0,                  // 2 o'clock — top pusher
        shaftLen = g.rOuter * 0.020f, shaftHalfW = g.rOuter * 0.030f,
        capDepth = g.rOuter * 0.060f, capHalfW = g.rOuter * 0.065f,
        reeded = true, anchorR = anchorR)
    drawAngledChronoControl(g, angleFromNorthDeg = 120.0,                 // 4 o'clock — bottom pusher
        shaftLen = g.rOuter * 0.020f, shaftHalfW = g.rOuter * 0.030f,
        capDepth = g.rOuter * 0.060f, capHalfW = g.rOuter * 0.065f,
        reeded = true, anchorR = anchorR)
    drawAngledChronoControl(g, angleFromNorthDeg = 90.0,                  // 3 o'clock — crown
        shaftLen = g.rOuter * 0.015f, shaftHalfW = g.rOuter * 0.045f,
        capDepth = g.rOuter * 0.080f, capHalfW = g.rOuter * 0.090f,
        reeded = true, anchorR = anchorR)
}

/**
 * One chronograph control (crown or pusher) drawn at an arbitrary clock
 * position. Its axis is RADIAL — perpendicular to the case rim at that
 * angle — so top/bottom pushers tilt up-right and down-right just like
 * the real watch.
 *
 *  - The shaft is a parallelogram from the case rim outward along the
 *    radial direction.
 *  - The cap is a rectangle perpendicular to the radial direction —
 *    appears as a vertical-ish rectangle from face-on. The cap is wider
 *    than its depth (matching the photo's mushroom-cap pushers).
 *  - Reeded grip lines run along the cap's long axis (perpendicular to
 *    the radial), giving the brushed-steel look.
 */
private fun DrawScope.drawAngledChronoControl(
    g: DialGeom,
    angleFromNorthDeg: Double,
    shaftLen: Float,
    shaftHalfW: Float,
    capDepth: Float,
    capHalfW: Float,
    reeded: Boolean,
    anchorR: Float                    // radial position of the INNER edge of the shaft
) {
    // Convert "degrees clockwise from 12 o'clock" → screen angle (0 = +x).
    val screenAngleDeg = angleFromNorthDeg - 90.0
    val rad = screenAngleDeg * PI / 180.0
    val nx = cos(rad).toFloat()   // radial-outward x
    val ny = sin(rad).toFloat()   // radial-outward y
    val px = -ny                  // perpendicular-to-radial x
    val py = nx                   // perpendicular-to-radial y

    // Anchor (where the shaft starts) = [anchorR], so the shaft and cap
    // grow OUTWARD from there. With anchorR set to the border's outer
    // edge, the entire control sits beyond the bezel face.
    val rRim = anchorR
    val ax = g.center.x + rRim * nx
    val ay = g.center.y + rRim * ny

    // Far end of the shaft (where the cap base sits).
    val sx = ax + nx * shaftLen
    val sy = ay + ny * shaftLen

    // Far face of the cap.
    val fx = sx + nx * capDepth
    val fy = sy + ny * capDepth

    // Shaft as a parallelogram.
    val shaftPath = Path().apply {
        moveTo(ax + px * shaftHalfW, ay + py * shaftHalfW)
        lineTo(ax - px * shaftHalfW, ay - py * shaftHalfW)
        lineTo(sx - px * shaftHalfW, sy - py * shaftHalfW)
        lineTo(sx + px * shaftHalfW, sy + py * shaftHalfW)
        close()
    }
    drawPath(shaftPath, color = DialPalette.SteelMid)
    drawPath(shaftPath, color = DialPalette.SteelGroove, style = Stroke(width = 0.8f))

    // Cap as a rectangle perpendicular to the axis (taller than wide on
    // the screen because capHalfW > capDepth/2 and the long side is along
    // the perpendicular axis).
    val capPath = Path().apply {
        moveTo(sx + px * capHalfW, sy + py * capHalfW)
        lineTo(sx - px * capHalfW, sy - py * capHalfW)
        lineTo(fx - px * capHalfW, fy - py * capHalfW)
        lineTo(fx + px * capHalfW, fy + py * capHalfW)
        close()
    }
    drawPath(capPath, color = DialPalette.SteelLight)
    drawPath(capPath, color = DialPalette.SteelMid, style = Stroke(width = 1.0f))

    // Reeded grip stripes on the cap face — lines parallel to the radial
    // axis, evenly spaced across the perpendicular (cap-long) direction.
    if (reeded) {
        val numLines = 7
        for (i in 1..numLines) {
            val frac = (i.toDouble() / (numLines + 1)) * 2.0 - 1.0   // -1..+1
            val offX = (px * capHalfW * frac).toFloat()
            val offY = (py * capHalfW * frac).toFloat()
            drawLine(
                color = DialPalette.SteelGroove,
                start = Offset(sx + offX + nx * capDepth * 0.10f,
                               sy + offY + ny * capDepth * 0.10f),
                end = Offset(fx + offX - nx * capDepth * 0.10f,
                             fy + offY - ny * capDepth * 0.10f),
                strokeWidth = 1.0f
            )
        }
    }
}

// =============================================================== text + triangle helpers

// Big Shoulders Display — Google Fonts variable-weight family with strong
// slab terminals, geometric proportions, and a tall condensed feel. The
// closest publicly-available approximation to the proprietary reference
// reference watch numerals. The TTF is the variable-axis 'wght' file, so weight
// must be selected via FontVariation; Bold (700) is heaviest readable.
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private val BezelFont = FontFamily(
    Font(
        R.font.big_shoulders_display_bold,
        FontWeight.Bold,
        variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(
            androidx.compose.ui.text.font.FontVariation.weight(800)
        )
    )
)

private fun DrawScope.drawScaleNumeralUpright(
    measurer: TextMeasurer,
    text: String,
    angleDegFromTop: Float,
    radius: Float,
    center: Offset,
    color: Color,
    sizeSp: androidx.compose.ui.unit.TextUnit,
    bold: Boolean = false
) {
    val rad = angleDegFromTop * PI / 180.0
    val x = center.x + (radius * cos(rad)).toFloat()
    val y = center.y + (radius * sin(rad)).toFloat()
    val l = measurer.measure(
        androidx.compose.ui.text.AnnotatedString(text),
        TextStyle(color = color, fontSize = sizeSp,
            fontFamily = BezelFont,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
    )
    val rot = angleDegFromTop + 90f
    // Tiny non-uniform scale to push the glyph slightly taller / narrower
    // than Big Shoulders Display's natural metrics — the proprietary
    // reference watch face is a touch more compressed than this font.
    val scaleX = 0.94f
    val scaleY = 1.06f
    rotate(rot, pivot = Offset(x, y)) {
        scale(scaleX = scaleX, scaleY = scaleY, pivot = Offset(x, y)) {
            drawText(
                textLayoutResult = l,
                topLeft = Offset(x - l.size.width / 2f, y - l.size.height / 2f)
            )
        }
    }
}

private fun DrawScope.drawTriangleAtAngle(
    center: Offset, angleDeg: Float, radius: Float, size: Float,
    color: Color, inward: Boolean
) {
    val rad = angleDeg * PI / 180.0
    val tipR = if (inward) radius - size else radius + size
    val baseR = radius
    val perpRad = rad + PI / 2
    val tipX = center.x + (tipR * cos(rad)).toFloat()
    val tipY = center.y + (tipR * sin(rad)).toFloat()
    val baseCx = center.x + (baseR * cos(rad)).toFloat()
    val baseCy = center.y + (baseR * sin(rad)).toFloat()
    val px = (size * 0.6f * cos(perpRad)).toFloat()
    val py = (size * 0.6f * sin(perpRad)).toFloat()
    val path = Path().apply {
        moveTo(tipX, tipY)
        lineTo(baseCx + px, baseCy + py)
        lineTo(baseCx - px, baseCy - py)
        close()
    }
    drawPath(path, color = color)
}
