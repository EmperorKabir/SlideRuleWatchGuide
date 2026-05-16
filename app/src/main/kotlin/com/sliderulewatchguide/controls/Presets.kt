package com.sliderulewatchguide.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sliderulewatchguide.dial.DialMath

/**
 * Header row above the watch. Reset sits standalone on the LEFT in its
 * own emphasised style. Five example chips arch on the RIGHT under an
 * "EXAMPLES" caption nestled inside the curve.
 *
 * Uses [TinyChip] (custom) instead of Material 3 AssistChip so the
 * horizontal padding can be tight enough for all five chips to fit on
 * narrow screens at the bumped UI font size.
 */
@Composable
fun CurvedPresets(
    onSetAngle: (Double) -> Unit,
    onReset: () -> Unit,
    onNudge: () -> Unit,
    modifier: Modifier = Modifier
) {
    val examples = listOf(
        "×2.5"   to { onSetAngle(DialMath.alignRotation(25.0, 10.0)) },
        "×3.5"   to { onSetAngle(DialMath.alignRotation(35.0, 10.0)) },
        "Hours"  to { onSetAngle(DialMath.alignRotation(40.0, 10.0)) },
        "mi→km"  to { onSetAngle(DialMath.alignRotation(10.0, DialMath.STAT_MARKER)) },
        "nm→km"  to { onSetAngle(DialMath.alignRotation(10.0, DialMath.NAUT_MARKER)) }
    )

    // Cap the font-scale just for the chip row. Outside this scope the
    // system's fontScale keeps applying. With cap = 1.0, chip text never
    // grows past its design size, so the chips can never spill out of
    // their equal-width slots and touch each other at high accessibility
    // text-size settings. At fontScale <= 1.0 (e.g. the user's phone) the
    // cap is inert and the row renders identically to before.
    val systemDensity = LocalDensity.current
    val cappedDensity = remember(systemDensity) {
        val capped = systemDensity.fontScale.coerceAtMost(1f)
        if (capped == systemDensity.fontScale) systemDensity
        else Density(density = systemDensity.density, fontScale = capped)
    }
    CompositionLocalProvider(LocalDensity provides cappedDensity) {
        Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            // ----- LEFT: Reset stacked above the Nudge chip -----
            // Fixed-dp column width chosen to fit the longest single
            // word of "Nudge to nearest integer" with margin so the
            // label wraps onto multiple lines without any token being
            // clipped. The surrounding CompositionLocalProvider caps
            // fontScale to 1.0, so 112dp is stable across every user
            // accessibility font-size setting. Reset and Nudge both
            // fillMaxWidth so they share the column's width; Nudge has
            // unbounded maxLines + softWrap so the chip's HEIGHT grows
            // with the wrapped line count.
            val chipFontSize = 18.sp
            Column(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .width(112.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TinyChip(
                    label = "Reset",
                    onClick = onReset,
                    fontSize = chipFontSize,
                    fontWeight = FontWeight.SemiBold,
                    emphasised = true,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                TinyChip(
                    label = "Nudge to nearest integer",
                    onClick = onNudge,
                    fontSize = chipFontSize,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = Int.MAX_VALUE,
                    softWrap = true,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Spacer(Modifier.width(4.dp))

            // ----- RIGHT: arched chip row with EXAMPLES caption sitting LOW -----
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Top
                ) {
                    examples.forEachIndexed { i, (label, onClick) ->
                        val n = examples.size
                        val t = i.toDouble() / (n - 1).coerceAtLeast(1) - 0.5
                        val drop = (t * t * 176.0).dp
                        // Chips are content-sized — at fontScale <= 1.0 this
                        // matches the original layout exactly. At higher font
                        // scales the surrounding CompositionLocalProvider caps
                        // the density so the chip text never grows past its
                        // design size, which means content widths stay
                        // constant and SpaceEvenly continues to keep chips
                        // from touching each other no matter the user's
                        // accessibility text-size setting.
                        TinyChip(
                            label = label,
                            onClick = onClick,
                            fontSize = 16.sp,
                            modifier = Modifier.offset(y = drop)
                        )
                    }
                }
                // EXAMPLES caption nestled inside the arc.
                Text(
                    "EXAMPLES",
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = 56.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun Presets(
    onSetAngle: (Double) -> Unit,
    onReset: () -> Unit,
    onNudge: () -> Unit,
    modifier: Modifier = Modifier
) = CurvedPresets(onSetAngle, onReset, onNudge, modifier)

/**
 * Compact chip with tight content-padding (~10 × 6 dp vs Material's
 * ~16 × 8 dp). Lets five chips fit on a 411 dp emulator at 16 sp font
 * size while still leaving room for the Reset chip on the left.
 */
@Composable
private fun TinyChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp,
    fontWeight: FontWeight = FontWeight.Medium,
    emphasised: Boolean = false,
    maxLines: Int = 1,
    softWrap: Boolean = false,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null
) {
    val haptics = LocalHapticFeedback.current
    val bgColor =
        if (emphasised) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.surface
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = maxLines,
            softWrap = softWrap,
            textAlign = textAlign
        )
    }
}
