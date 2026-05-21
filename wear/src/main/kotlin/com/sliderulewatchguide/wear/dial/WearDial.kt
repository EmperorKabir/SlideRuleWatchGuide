package com.sliderulewatchguide.wear.dial

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.Paint as NativePaint
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Slide-rule dial rendered for a round Wear OS screen.
 *
 *   • Outer rotating bezel ring (log scale 10..95) — rotates with [rotationDegrees].
 *   • Inner fixed chapter ring (log scale 10..95) — plus red NAU/STA/KM marks.
 *   • Red triangle index pointing inward at 12 o'clock — MPH speed index
 *     anchored on the inner ring.
 *
 * No time hands (system TimeText handles that). No sub-dials, no chrono.
 */
@Composable
fun WearDial(
    rotationDegrees: Double,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val side = min(size.width, size.height)
            // Ring radii as fractions of the half-side so the dial scales.
            val rOuter = side * 0.5f * 0.98f
            val rBezelInner = side * 0.5f * 0.78f
            val rChapterInner = side * 0.5f * 0.58f

            // Black background.
            drawCircle(color = Color(0xFF0A0A0A), radius = rOuter, center = Offset(cx, cy))

            // Rotating outer bezel.
            rotate(degrees = rotationDegrees.toFloat(), pivot = Offset(cx, cy)) {
                drawRingFill(cx, cy, rOuter, rBezelInner, Color(0xFF1A1A1A))
                drawRingEdges(cx, cy, rOuter, rBezelInner, Color(0xFFF0EFEA))
                drawScaleTicks(cx, cy, rOuter, rBezelInner, Color(0xFFE6E5DF))
                drawScaleNumerals(cx, cy, (rOuter + rBezelInner) / 2f, side)
            }

            // Fixed inner chapter ring.
            drawRingFill(cx, cy, rBezelInner, rChapterInner, Color(0xFF0F0F0F))
            drawRingEdges(cx, cy, rBezelInner, rChapterInner, Color(0xFFB8B6B0), 1f)
            drawScaleTicks(cx, cy, rBezelInner, rChapterInner, Color(0xFFB8B6B0))
            drawScaleNumerals(cx, cy, (rBezelInner + rChapterInner) / 2f, side)
            drawMarkers(cx, cy, (rBezelInner + rChapterInner) / 2f, side)

            // Red MPH triangle index at 12 o'clock, pointing inward.
            drawMphIndex(cx, cy, rBezelInner, side)
        }
    }
}

private fun DrawScope.drawRingFill(
    cx: Float,
    cy: Float,
    rRingOuter: Float,
    rRingInner: Float,
    color: Color,
) {
    val mid = (rRingOuter + rRingInner) / 2f
    drawCircle(
        color = color,
        radius = mid,
        center = Offset(cx, cy),
        style = Stroke(width = rRingOuter - rRingInner),
    )
}

private fun DrawScope.drawRingEdges(
    cx: Float,
    cy: Float,
    rRingOuter: Float,
    rRingInner: Float,
    color: Color,
    width: Float = 1.5f,
) {
    drawCircle(color = color, radius = rRingOuter, center = Offset(cx, cy), style = Stroke(width = width))
    drawCircle(color = color, radius = rRingInner, center = Offset(cx, cy), style = Stroke(width = width))
}

private fun DrawScope.drawScaleTicks(
    cx: Float,
    cy: Float,
    rRingOuter: Float,
    rRingInner: Float,
    tickColor: Color,
) {
    val ringWidth = rRingOuter - rRingInner
    for (n in 10..99) {
        val majorTick = n % 5 == 0
        val tickLen = ringWidth * if (majorTick) 0.30f else 0.18f
        val a = DialMath.degToRad(DialMath.drawAngleDeg(n.toDouble()) - 90.0)
        val rStart = rRingOuter
        val rEnd = rRingOuter - tickLen
        val ca = cos(a).toFloat()
        val sa = sin(a).toFloat()
        drawLine(
            color = tickColor,
            start = Offset(cx + rStart * ca, cy + rStart * sa),
            end = Offset(cx + rEnd * ca, cy + rEnd * sa),
            strokeWidth = if (majorTick) 1.4f else 0.8f,
        )
    }
}

private fun DrawScope.drawScaleNumerals(cx: Float, cy: Float, r: Float, side: Float) {
    val paint = NativePaint().apply {
        color = android.graphics.Color.parseColor("#F0EFEA")
        textSize = side * 0.040f
        textAlign = NativePaint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        isAntiAlias = true
    }
    val redPaint = NativePaint(paint).apply {
        color = android.graphics.Color.parseColor("#D7263D")
    }
    for (n in 10..95 step 5) {
        val a = DialMath.degToRad(DialMath.drawAngleDeg(n.toDouble()) - 90.0)
        val x = cx + r * cos(a).toFloat()
        val y = cy + r * sin(a).toFloat() + paint.textSize / 3f
        val p = if (n == 10 || n == 36 || n == 60) redPaint else paint
        drawContext.canvas.nativeCanvas.drawText(n.toString(), x, y, p)
    }
}

private fun DrawScope.drawMarkers(cx: Float, cy: Float, r: Float, side: Float) {
    val redPaint = NativePaint().apply {
        color = android.graphics.Color.parseColor("#D7263D")
        textSize = side * 0.026f
        textAlign = NativePaint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        isAntiAlias = true
    }
    fun label(value: Double, text: String) {
        val a = DialMath.degToRad(DialMath.drawAngleDeg(value) - 90.0)
        // Place markers slightly INSIDE the numerals row so they don't collide.
        val rLabel = r - side * 0.030f
        val x = cx + rLabel * cos(a).toFloat()
        val y = cy + rLabel * sin(a).toFloat() + redPaint.textSize / 3f
        drawContext.canvas.nativeCanvas.drawText(text, x, y, redPaint)
    }
    label(DialMath.NAUT_MARKER, "NAU")
    label(DialMath.STAT_MARKER, "STA")
    label(DialMath.KM_MARKER, "KM")
}

private fun DrawScope.drawMphIndex(cx: Float, cy: Float, rBezelInner: Float, side: Float) {
    val tipY = cy - rBezelInner + side * 0.020f
    val baseY = cy - rBezelInner - side * 0.012f
    val halfWidth = side * 0.015f
    val path = Path().apply {
        moveTo(cx, tipY)
        lineTo(cx - halfWidth, baseY)
        lineTo(cx + halfWidth, baseY)
        close()
    }
    drawPath(path, color = Color(0xFFD7263D))
}
