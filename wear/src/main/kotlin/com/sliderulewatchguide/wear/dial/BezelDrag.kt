package com.sliderulewatchguide.wear.dial

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import kotlin.math.atan2

/**
 * Drag the dial to rotate the bezel. Each drag delta is converted to an
 * angular rotation around the dial's centre and dispatched via
 * [onRotateBy] in degrees. Same convention as the phone app:
 * positive = clockwise.
 */
fun Modifier.bezelDragRotation(onRotateBy: (Double) -> Unit): Modifier = composed {
    pointerInput(Unit) {
        detectDragGestures { change, dragAmount ->
            val centre = Offset(size.width / 2f, size.height / 2f)
            val current = change.position
            val previous = current - dragAmount
            val a1 = atan2(previous.y - centre.y, previous.x - centre.x)
            val a2 = atan2(current.y - centre.y, current.x - centre.x)
            var delta = (a2 - a1) * 180.0 / Math.PI
            // unwrap to (-180, 180]
            if (delta > 180.0) delta -= 360.0
            if (delta < -180.0) delta += 360.0
            onRotateBy(delta)
            change.consume()
        }
    }
}
