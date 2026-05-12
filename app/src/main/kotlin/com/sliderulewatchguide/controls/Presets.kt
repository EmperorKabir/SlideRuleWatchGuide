package com.sliderulewatchguide.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
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
    modifier: Modifier = Modifier
) {
    val examples = listOf(
        "×2.5"   to { onSetAngle(DialMath.alignRotation(25.0, 10.0)) },
        "×3.5"   to { onSetAngle(DialMath.alignRotation(35.0, 10.0)) },
        "Hours"  to { onSetAngle(DialMath.alignRotation(40.0, 10.0)) },
        "mi→km"  to { onSetAngle(DialMath.alignRotation(10.0, DialMath.STAT_MARKER)) },
        "nm→km"  to { onSetAngle(DialMath.alignRotation(10.0, DialMath.NAUT_MARKER)) }
    )

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        // ----- LEFT: Reset on its own, slightly emphasised -----
        Box(modifier = Modifier.padding(top = 6.dp)) {
            TinyChip(
                label = "Reset",
                onClick = onReset,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                emphasised = true
            )
        }

        Spacer(Modifier.width(4.dp))

        // ----- RIGHT: arched chip row with EXAMPLES caption sitting LOW -----
        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth()) {
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

@Composable
fun Presets(
    onSetAngle: (Double) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) = CurvedPresets(onSetAngle, onReset, modifier)

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
    emphasised: Boolean = false
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
            maxLines = 1,
            softWrap = false
        )
    }
}
