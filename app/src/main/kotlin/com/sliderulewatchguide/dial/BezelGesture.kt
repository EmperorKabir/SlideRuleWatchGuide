package com.sliderulewatchguide.dial

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Drag the outer bezel: only drags that begin in the outermost ~14% of the
 * radius are considered "on the coin edge" and rotate the bezel; drags
 * inside that band are ignored, matching real-watch behaviour.
 *
 * [onRotate] receives the delta angle in DEGREES clockwise.
 */
fun Modifier.bezelDragRotation(onRotate: (Double) -> Unit): Modifier = composed {
    val state = remember { GestureState() }
    pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { offset ->
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = minOf(size.width, size.height) / 2f
                val r = hypot(offset.x - center.x, offset.y - center.y)
                // Larger touch target than the literal coin-edge so the bezel is
                // easy to grab on small screens; drags inside the dial centre
                // (sub-dial / wordmark area) are still ignored.
                state.active = r >= radius * 0.62f && r <= radius * 1.10f
                state.lastAngle = atan2((offset.y - center.y).toDouble(), (offset.x - center.x).toDouble())
                state.center = center
            },
            onDrag = { change, _ ->
                if (!state.active) return@detectDragGestures
                val pos = change.position
                val newAngle = atan2((pos.y - state.center.y).toDouble(), (pos.x - state.center.x).toDouble())
                var delta = newAngle - state.lastAngle
                // Wrap to (-PI, PI]
                if (delta > PI) delta -= 2 * PI
                if (delta < -PI) delta += 2 * PI
                state.lastAngle = newAngle
                onRotate(delta * 180.0 / PI)
                change.consume()
            },
            onDragEnd = { state.active = false },
            onDragCancel = { state.active = false }
        )
    }
}

private class GestureState {
    var active: Boolean = false
    var lastAngle: Double = 0.0
    var center: Offset = Offset.Zero
}
