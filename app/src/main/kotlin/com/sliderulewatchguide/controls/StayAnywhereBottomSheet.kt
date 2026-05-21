package com.sliderulewatchguide.controls

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
 * — it only snaps to a limit when the user releases within
 * [snapThresholdDp] of the top or bottom limit. Outside that snap zone
 * the sheet stays free-floating, matching the Dribbble settings drawer
 * pattern requested by the tester.
 *
 * Sheet height = the visible portion above the bottom edge. Travel
 * range:
 *   • min = [peekHeightDp] (only the drag-handle / title bar visible)
 *   • max = parent height − [topMarginDp] (leaves room at top for the
 *     status bar / dial chrome)
 *
 * Sheet content goes inside the [content] slot. The drag-handle area
 * is the only part that responds to vertical drag — the inner content
 * is free to scroll independently.
 */
@Composable
fun StayAnywhereBottomSheet(
    title: String,
    modifier: Modifier = Modifier,
    peekHeightDp: Dp = 56.dp,
    topMarginDp: Dp = 80.dp,
    snapThresholdDp: Dp = 64.dp,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val parentHeightPx = with(density) { maxHeight.toPx() }
        val peekPx = with(density) { peekHeightDp.toPx() }
        val topMarginPx = with(density) { topMarginDp.toPx() }
        val snapPx = with(density) { snapThresholdDp.toPx() }

        val minHeightPx = peekPx
        val maxHeightPx = (parentHeightPx - topMarginPx).coerceAtLeast(peekPx)

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
                // Drag-handle bar — captures vertical drag.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(peekHeightDp)
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                // Vertical delta: positive = downward = SHRINK sheet.
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
                // Free content area below the drag handle.
                Box(modifier = Modifier.fillMaxSize()) { content() }
            }
        }
    }
}
