package com.sliderulewatchguide.wear.dial

import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.pow

/**
 * Pure log-scale math for the reference watch slide-rule bezel.
 *
 * Both scales (outer rotating, inner fixed) span one logarithmic decade
 * 10..100 across a full 360 degree revolution. The position of a value V on
 * a scale, measured in degrees clockwise from the scale's "10" anchor, is:
 *
 *   angle = 360 * log10(V / 10)        (V in [10, 100))
 *
 * On the watch the inner scale's "10" anchor sits near 12 o'clock; the outer
 * bezel can be rotated by an arbitrary angle relative to the inner scale.
 *
 * Marker scale-positions are expressed as scale-values (not angles), which
 * keeps the math independent of how the dial is drawn:
 *
 *   - RED_10           = 10.0   (multiplication / division unit index)
 *   - RED_36           = 36.0   (60 * 60 = 3600 time conversion)
 *   - RED_60_MPH       = 60.0   (speed index, 12 o'clock on inner)
 *   - KM_MARKER        = 61.0   (km reference on inner)
 *   - STAT_MARKER      = KM_MARKER / 1.609344  (~37.91)
 *   - NAUT_MARKER      = KM_MARKER / 1.852     (~32.94)
 *
 * The STAT/NAUT positions are mathematically self-consistent with the
 * conversion factors AND visually faithful to the photo — the printed labels
 * on the chapter ring sit between scale numerals 35..40, with the actual
 * triangle markers at the precise scale values above.
 */
object DialMath {
    const val MILE_TO_KM: Double = 1.609344
    const val NAUT_TO_KM: Double = 1.852

    const val SCALE_MIN: Double = 10.0
    const val SCALE_MAX: Double = 100.0

    const val RED_10: Double = 10.0
    const val RED_36: Double = 36.0
    const val RED_60_MPH: Double = 60.0
    // Red NAUT / STAT triangles sit at scale values 33 and 38 respectively
    // (per photo image 26). KM sits at 61.15 so that the slide-rule
    // ratios are mathematically exact:
    //   KM / STAT = 61.15 / 38 = 1.609344  (matches MILE_TO_KM ✓)
    //   KM / NAUT = 61.15 / 33 = 1.853     (≈ NAUT_TO_KM 1.852 to 0.05 %)
    // The thin tick at integer 61 on the inner ring is still suppressed
    // (the small red KM triangle drawn at scale 61.15 sits just past it).
    const val NAUT_MARKER: Double = 33.0
    const val STAT_MARKER: Double = 38.0
    const val KM_MARKER: Double = 61.15

    /** Wrap an angle to [0, 360). */
    fun wrap360(angle: Double): Double {
        var a = angle % 360.0
        if (a < 0) a += 360.0
        return a
    }

    /**
     * Rotational offset that places the scale value 60 at 12 o'clock on the
     * dial — matching the real reference watch's MPH index position.
     *
     * Drawing code uses `drawAngleDeg(v) = valueToAngle(v) - SCALE_TOP_OFFSET`
     * to translate a scale-value to screen-degrees (0° = +x, 90° = +y in
     * Compose Canvas; we further subtract 90° elsewhere to put screen-0 at
     * 12 o'clock — see dial drawing).
     */
    val SCALE_TOP_OFFSET: Double = wrap360(360.0 * kotlin.math.log10(60.0 / 10.0) + 90.0)

    /** Screen-space angle (degrees clockwise from 3 o'clock) for a scale value. */
    fun drawAngleDeg(value: Double): Double = wrap360(valueToAngle(value) - SCALE_TOP_OFFSET)

    /** Position (degrees clockwise from "10") of a scale value V in [10, 100). */
    fun valueToAngle(value: Double): Double {
        require(value > 0) { "value must be positive" }
        // Normalise to one decade [10, 100)
        var v = value
        while (v < SCALE_MIN) v *= 10.0
        while (v >= SCALE_MAX) v /= 10.0
        return wrap360(360.0 * log10(v / SCALE_MIN))
    }

    /** Inverse of [valueToAngle]; returns a value in [10, 100). */
    fun angleToValue(angleDegrees: Double): Double {
        val a = wrap360(angleDegrees)
        return SCALE_MIN * 10.0.pow(a / 360.0)
    }

    /**
     * Given the bezel's rotation θ (degrees clockwise; 0 = outer-10 aligned
     * with inner-10), each outer value V sits at angular position
     * valueToAngle(V) + θ.
     *
     * For outer-X aligned with inner-Y:  valueToAngle(X) + θ = valueToAngle(Y),
     * so θ = valueToAngle(Y) − valueToAngle(X).
     *
     * The OUTER value sitting above inner-Y is therefore:
     *   outerValueAtInner(Y, θ) = angleToValue( valueToAngle(Y) − θ )
     *
     * The INNER value sitting under outer-X is:
     *   innerValueAtOuter(X, θ) = angleToValue( valueToAngle(X) + θ )
     */
    fun outerValueAtInner(innerValue: Double, rotationDegrees: Double): Double =
        angleToValue(valueToAngle(innerValue) - rotationDegrees)

    fun innerValueAtOuter(outerValue: Double, rotationDegrees: Double): Double =
        angleToValue(valueToAngle(outerValue) + rotationDegrees)

    /** Required bezel rotation to align outer-X with inner-Y. */
    fun alignRotation(outerX: Double, innerY: Double): Double =
        wrap360(valueToAngle(innerY) - valueToAngle(outerX))

    /**
     * Return the multiplier set by the current bezel rotation, defined as
     * outerValueAtInner(10) / 10. So if the bezel is rotated such that
     * outer-25 sits on inner-10, the multiplier is 2.5.
     */
    fun multiplierFromRotation(rotationDegrees: Double): Double =
        outerValueAtInner(SCALE_MIN, rotationDegrees) / SCALE_MIN

    fun degToRad(d: Double): Double = d * PI / 180.0
    fun radToDeg(r: Double): Double = r * 180.0 / PI
}
