package com.sliderulewatchguide.wear.dial

import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.pow

/**
 * Pure log-scale math for the slide-rule bezel. Copied verbatim from the
 * phone app — kept duplicated rather than extracted to a shared module
 * so the wear module's build is fully independent of the phone module.
 */
object DialMath {
    const val MILE_TO_KM: Double = 1.609344
    const val NAUT_TO_KM: Double = 1.852

    const val SCALE_MIN: Double = 10.0
    const val SCALE_MAX: Double = 100.0

    const val RED_10: Double = 10.0
    const val RED_36: Double = 36.0
    const val RED_60_MPH: Double = 60.0
    const val NAUT_MARKER: Double = 33.0
    const val STAT_MARKER: Double = 38.0
    const val KM_MARKER: Double = 61.15

    fun wrap360(angle: Double): Double {
        var a = angle % 360.0
        if (a < 0) a += 360.0
        return a
    }

    val SCALE_TOP_OFFSET: Double = wrap360(360.0 * log10(60.0 / 10.0) + 90.0)

    fun drawAngleDeg(value: Double): Double = wrap360(valueToAngle(value) - SCALE_TOP_OFFSET)

    fun valueToAngle(value: Double): Double {
        require(value > 0) { "value must be positive" }
        var v = value
        while (v < SCALE_MIN) v *= 10.0
        while (v >= SCALE_MAX) v /= 10.0
        return wrap360(360.0 * log10(v / SCALE_MIN))
    }

    fun angleToValue(angleDegrees: Double): Double {
        val a = wrap360(angleDegrees)
        return SCALE_MIN * 10.0.pow(a / 360.0)
    }

    fun outerValueAtInner(innerValue: Double, rotationDegrees: Double): Double =
        angleToValue(valueToAngle(innerValue) - rotationDegrees)

    fun innerValueAtOuter(outerValue: Double, rotationDegrees: Double): Double =
        angleToValue(valueToAngle(outerValue) + rotationDegrees)

    fun alignRotation(outerX: Double, innerY: Double): Double =
        wrap360(valueToAngle(innerY) - valueToAngle(outerX))

    fun multiplierFromRotation(rotationDegrees: Double): Double =
        outerValueAtInner(SCALE_MIN, rotationDegrees) / SCALE_MIN

    fun degToRad(d: Double): Double = d * PI / 180.0
    fun radToDeg(r: Double): Double = r * 180.0 / PI
}
