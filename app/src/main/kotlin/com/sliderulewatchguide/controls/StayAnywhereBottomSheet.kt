package com.sliderulewatchguide.controls

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Bottom-anchored sheet that the user can drag to ANY vertical position
 * within its travel range. On release the sheet stays where it was left
 * — it only snaps to a limit when the user releases within the snap
 * threshold of the top or bottom limit.
 *
 * Travel range:
 *   • min = [peekHeightDp] (only the drag-handle / title bar visible)
 *   • max = parent height − [topMarginDp]
 *
 * Snap threshold:
 *   • If [snapThresholdDp] is provided, it's used verbatim.
 *   • Otherwise the threshold is computed dynamically as a fraction of
 *     the actual travel distance (default 12 %), with a sensible floor
 *     and ceiling so it never gets too small on tiny phones or too
 *     large on tablets / foldables.
 *
 * The drag handle / title area also responds to **taps**: tapping when
 * the sheet is more than halfway towards closed expands it fully;
 * tapping when more than halfway expanded collapses it. Tap and drag
 * coexist — Compose's gesture system routes large pointer movement to
 * the dragger and small clicks to the clickable.
 */
@Composable
fun StayAnywhereBottomSheet(
    title: String,
    modifier: Modifier = Modifier,
    peekHeightDp: Dp = 56.dp,
    topMarginDp: Dp = 80.dp,
    snapThresholdDp: Dp? = null,
    snapFraction: Float = 0.12f,
    minSnapDp: Dp = 40.dp,
    maxSnapDp: Dp = 120.dp,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val parentHeightPx = with(density) { maxHeight.toPx() }
        val peekPx = with(density) { peekHeightDp.toPx() }
        val topMarginPx = with(density) { topMarginDp.toPx() }

        val minHeightPx = peekPx
        val maxHeightPx = (parentHeightPx - topMarginPx).coerceAtLeast(peekPx)
        val travelPx = maxHeightPx - minHeightPx

        // Snap threshold: explicit override beats the dynamic calculation.
        val snapPx = if (snapThresholdDp != null) {
            with(density) { snapThresholdDp.toPx() }
        } else {
            val floor = with(density) { minSnapDp.toPx() }
            val ceil = with(density) { maxSnapDp.toPx() }
            (travelPx * snapFraction).coerceIn(floor, ceil)
        }

        val sheetHeightPx = remember(maxHeightPx) { Animatable(minHeightPx) }
        val scope = rememberCoroutineScope()

        val sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)

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
                // Drag-handle area. Accepts BOTH vertical drag AND taps.
                // Drag handler consumes pointer events when the user
                // moves significantly; taps fall through to clickable.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(peekHeightDp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            // Toggle: if closer to collapsed, expand;
                            // if closer to expanded, collapse.
                            val midpoint = minHeightPx + travelPx / 2f
                            val target = if (sheetHeightPx.value < midpoint) {
                                maxHeightPx
                            } else {
                                minHeightPx
                            }
                            scope.launch { sheetHeightPx.animateTo(target) }
                        }
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                val newH = (sheetHeightPx.value - delta)
                                    .coerceIn(minHeightPx, maxHeightPx)
                                scope.launch { sheetHeightPx.snapTo(newH) }
                            },
                            onDragStopped = {
                                val current = sheetHeightPx.value
                                val target = when {
                                    current - minHeightPx < snapPx -> minHeightPx
                                    maxHeightPx - current < snapPx -> maxHeightPx
                                    else -> current
                                }
                                if (target != current) {
                                    scope.launch { sheetHeightPx.animateTo(target) }
                                }
                            },
                        ),
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
