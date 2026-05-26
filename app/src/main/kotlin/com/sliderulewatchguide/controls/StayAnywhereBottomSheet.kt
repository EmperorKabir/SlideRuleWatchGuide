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

        // Choose the snap target on drag release. Below the velocity
        // threshold the gesture is treated as a slow settle → closest snap
        // to the release position. At/above it, the gesture is a directional
        // swipe → step to the next configured snap in the drag direction,
        // measured from the RELEASE height: a swipe up (sheet height grows,
        // so screen-Y velocity is negative) lands on the first snap above
        // the release height; a swipe down lands on the first snap below it.
        // Speed decides direction only, never distance.
        fun directionalSnap(releasedHeightPx: Float, releaseVelocityY: Float): Float {
            if (abs(releaseVelocityY) < flingThresholdPx) return nearestSnap(releasedHeightPx)
            return if (releaseVelocityY < 0f) {
                // Swipe up → first snap strictly above the release height.
                snapPx.firstOrNull { it > releasedHeightPx + 0.5f } ?: snapPx.last()
            } else {
                // Swipe down → first snap strictly below the release height.
                snapPx.lastOrNull { it < releasedHeightPx - 0.5f } ?: snapPx.first()
            }
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
                                    // It's a vertical drag. Track velocity
                                    // across the drag so the release can
                                    // decide snap-to-nearest vs fling-decay.
                                    val tracker = VelocityTracker()
                                    tracker.addPosition(dragChange.uptimeMillis, dragChange.position)
                                    verticalDrag(dragChange.id) { change ->
                                        tracker.addPosition(change.uptimeMillis, change.position)
                                        val delta = change.positionChange().y
                                        val newH = (sheetHeightPx.value - delta)
                                            .coerceIn(snapPx.first(), snapPx.last())
                                        scope.launch { sheetHeightPx.snapTo(newH) }
                                        change.consume()
                                    }
                                    // Release — snap to nearest (slow) or
                                    // one detent in the swipe direction (fast).
                                    val releaseVelocityY = tracker.calculateVelocity().y
                                    val target = directionalSnap(sheetHeightPx.value, releaseVelocityY)
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
