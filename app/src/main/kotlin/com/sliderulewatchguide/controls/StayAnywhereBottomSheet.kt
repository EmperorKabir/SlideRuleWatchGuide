package com.sliderulewatchguide.controls

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Bottom-anchored sheet with N discrete snap points. Gestures on the
 * drag-handle / title bar:
 *
 *   • Tap (release within touch slop of touch-down) cycles to the next
 *     snap point upward, wrapping back to the lowest from the highest.
 *   • Vertical drag (movement beyond touch slop) snaps to the closest
 *     of the configured points on release.
 *
 * Both gestures are handled in a single [pointerInput] block via
 * [awaitEachGesture], using touch slop to disambiguate — Compose's
 * built-in `clickable + draggable` chain can confuse each other.
 *
 * `snapHeightsDp` are sheet heights in dp (visible portion above the
 * bottom of the screen). Caller must supply at least one value.
 *
 * `topInsetDp` carves out a small strip at the top of the screen so
 * the full-expand snap clears the status bar.
 */
@Composable
fun StayAnywhereBottomSheet(
    title: String,
    snapHeightsDp: List<Dp>,
    modifier: Modifier = Modifier,
    peekHeightDp: Dp = 56.dp,
    topInsetDp: Dp = 28.dp,
    content: @Composable () -> Unit,
) {
    require(snapHeightsDp.isNotEmpty()) { "snapHeightsDp must contain at least one value" }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val parentHeightPx = with(density) { maxHeight.toPx() }
        val peekPx = with(density) { peekHeightDp.toPx() }
        val topInsetPx = with(density) { topInsetDp.toPx() }

        val maxHeightPx = (parentHeightPx - topInsetPx).coerceAtLeast(peekPx)
        val snapPx = snapHeightsDp
            .map { with(density) { it.toPx().coerceIn(peekPx, maxHeightPx) } }
            .distinct()
            .sorted()

        val initialSnapPx = snapPx.first()
        val sheetHeightPx = remember(maxHeightPx, snapPx) { Animatable(initialSnapPx) }
        val scope = rememberCoroutineScope()

        val sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)

        fun nearestSnap(current: Float): Float =
            snapPx.minBy { abs(it - current) }

        fun cycleToNextSnap() {
            val current = sheetHeightPx.value
            val next = snapPx.firstOrNull { it > current + 1f } ?: snapPx.first()
            scope.launch { sheetHeightPx.animateTo(next) }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { sheetHeightPx.value.toDp() })
                .align(Alignment.BottomCenter)
                .clip(sheetShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = sheetShape,
                ),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(peekHeightDp)
                        .pointerInput(snapPx) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                // Wait for either touch slop crossed (drag)
                                // or pointer release (tap).
                                val dragChange = awaitVerticalTouchSlopOrCancellation(down.id) { change, _ ->
                                    change.consume()
                                }
                                if (dragChange == null) {
                                    // No drag — it's a tap. Cycle to next snap.
                                    cycleToNextSnap()
                                } else {
                                    // It's a vertical drag. Track until release.
                                    verticalDrag(dragChange.id) { change ->
                                        val delta = change.positionChange().y
                                        val newH = (sheetHeightPx.value - delta)
                                            .coerceIn(snapPx.first(), snapPx.last())
                                        scope.launch { sheetHeightPx.snapTo(newH) }
                                        change.consume()
                                    }
                                    // Release — snap to nearest.
                                    val target = nearestSnap(sheetHeightPx.value)
                                    if (target != sheetHeightPx.value) {
                                        scope.launch { sheetHeightPx.animateTo(target) }
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .width(36.dp)
                            .height(4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(2.dp),
                            )
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 22.dp),
                    )
                }
                Box(modifier = Modifier.fillMaxSize()) { content() }
            }
        }
    }
}
