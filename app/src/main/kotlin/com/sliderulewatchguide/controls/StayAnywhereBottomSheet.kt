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
import androidx.compose.ui.input.pointer.util.VelocityTracker
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
 *   • SLOW drag (release velocity below [flingVelocityThresholdDp] dp/s)
 *     snaps to the CLOSEST configured point on release.
 *   • DIRECTIONAL swipe (release velocity at/above the threshold) snaps to
 *     the next configured point in the DRAG DIRECTION, measured from the
 *     position the finger was released at: a swipe up lands on the first
 *     snap above the release height, a swipe down on the first snap below
 *     it. Speed only decides direction, never distance — a gentle flick
 *     and a hard fling both advance exactly one detent past the release
 *     point. The threshold is kept low so a light, deliberate swipe is
 *     enough to commit to the move.
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
 *
 * `flingVelocityThresholdDp` is the release speed (in dp/s) at/above which
 * the directional one-step snap is taken instead of snap-to-nearest.
 * Expressed in dp so it's invariant to display density. Kept low so a
 * light deliberate swipe commits to a step.
 */
@Composable
fun StayAnywhereBottomSheet(
    title: String,
    snapHeightsDp: List<Dp>,
    modifier: Modifier = Modifier,
    peekHeightDp: Dp = 56.dp,
    topInsetDp: Dp = 28.dp,
    flingVelocityThresholdDp: Dp = 5.dp,
    content: @Composable () -> Unit,
) {
    require(snapHeightsDp.isNotEmpty()) { "snapHeightsDp must contain at least one value" }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val parentHeightPx = with(density) { maxHeight.toPx() }
        val peekPx = with(density) { peekHeightDp.toPx() }
        val topInsetPx = with(density) { topInsetDp.toPx() }
        val flingThresholdPx = with(density) { flingVelocityThresholdDp.toPx() }

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

        // Smallest finger travel (touch-down -> release) that counts as a
        // directional gesture. Kept BELOW the touch-slop distance so that
        // ANY recognised drag — including a brief flick that only just
        // crosses slop — has a clear direction and steps (never bounces).
        val directionDeadzonePx = with(density) { 2.dp.toPx() }

        fun nearestIndex(h: Float): Int =
            snapPx.indices.minByOrNull { abs(snapPx[it] - h) } ?: 0

        // Choose the snap target on release. Step DIRECTION comes from the
        // finger's NET vertical travel from touch-down to release — robust to
        // brief flicks (crossing touch slop already implies a net move) and
        // to high-refresh screens (which read a decelerating release as
        // near-stationary, defeating velocity-only detection). Release
        // velocity is only a fallback for a degenerate ~zero-travel gesture.
        // Screen-Y decreases upward and the sheet grows upward, so an UP
        // gesture has negative netY / negative velocity.
        //
        // The target is at LEAST one detent in the gesture direction (from the
        // detent the drag STARTED at), but if the finger travelled further it
        // honours the detent NEAREST the release. Picking the nearest detent
        // (rather than the first one strictly past the release) widens each
        // detent's catchment to the midpoint with its neighbour, so a small
        // overshoot past a closely-spaced detent still lands on it instead of
        // skipping to the next; a deliberate longer drag still multi-steps.
        fun directionalSnap(
            releasedHeightPx: Float, dragStartHeightPx: Float,
            pointerNetY: Float, releaseVelocityY: Float,
        ): Float {
            val up: Boolean = when {
                abs(pointerNetY) >= directionDeadzonePx -> pointerNetY < 0f
                abs(releaseVelocityY) >= flingThresholdPx -> releaseVelocityY < 0f
                else -> return nearestSnap(releasedHeightPx)
            }
            val originIdx = nearestIndex(dragStartHeightPx)
            val releaseIdx = nearestIndex(releasedHeightPx)
            val targetIdx = if (up) maxOf(originIdx + 1, releaseIdx).coerceAtMost(snapPx.lastIndex)
                            else minOf(originIdx - 1, releaseIdx).coerceAtLeast(0)
            return snapPx[targetIdx]
        }

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
                                    // It's a vertical drag. Track the finger's
                                    // net travel from the original touch-down
                                    // (robust step direction — includes the
                                    // slop-eaten portion) plus velocity (flick
                                    // fallback). downY is the initial contact.
                                    val downY = down.position.y
                                    var lastY = dragChange.position.y
                                    val dragStartHeight = sheetHeightPx.value
                                    val tracker = VelocityTracker()
                                    tracker.addPosition(dragChange.uptimeMillis, dragChange.position)
                                    verticalDrag(dragChange.id) { change ->
                                        tracker.addPosition(change.uptimeMillis, change.position)
                                        lastY = change.position.y
                                        val delta = change.positionChange().y
                                        val newH = (sheetHeightPx.value - delta)
                                            .coerceIn(snapPx.first(), snapPx.last())
                                        scope.launch { sheetHeightPx.snapTo(newH) }
                                        change.consume()
                                    }
                                    // Release — step one detent in the gesture
                                    // direction (finger net travel, or flick
                                    // velocity), else nearest.
                                    val releaseVelocityY = tracker.calculateVelocity().y
                                    val target = directionalSnap(sheetHeightPx.value, dragStartHeight, lastY - downY, releaseVelocityY)
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
