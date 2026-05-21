package com.sliderulewatchguide.wear.viewmodel

import androidx.lifecycle.ViewModel
import com.sliderulewatchguide.wear.dial.DialMath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.floor

/**
 * Minimal view-model for the watch UI. Holds only the bezel rotation —
 * the watch app doesn't need the five inter-connected input fields or
 * the chronograph state from the phone version.
 */
class WearDialViewModel : ViewModel() {

    private val _rotationDegrees = MutableStateFlow(0.0)
    val rotationDegrees: StateFlow<Double> = _rotationDegrees.asStateFlow()

    fun rotateBy(deltaDegrees: Double) {
        _rotationDegrees.value = DialMath.wrap360(_rotationDegrees.value + deltaDegrees)
    }

    fun setRotation(angle: Double) {
        _rotationDegrees.value = DialMath.wrap360(angle)
    }

    fun reset() {
        _rotationDegrees.value = 0.0
    }

    /**
     * Nudge the outer value sitting above the MPH anchor (inner = 60) to
     * the nearest whole integer. Mirrors the phone-app behaviour but
     * always uses inner = 60 because the wear UI doesn't expose an
     * Inner-anchor selector.
     */
    fun nudgeToNearestInteger() {
        val current = DialMath.outerValueAtInner(DialMath.RED_60_MPH, _rotationDegrees.value)
        if (!current.isFinite() || current <= 0.0) return
        val target = floor(current + 0.5).coerceAtLeast(DialMath.SCALE_MIN)
        if (abs(target - current) < 1e-6) return
        setRotation(DialMath.alignRotation(outerX = target, innerY = DialMath.RED_60_MPH))
    }

    /** Current "reading": outer value sitting above the MPH (inner = 60) anchor. */
    fun currentReading(): Double =
        DialMath.outerValueAtInner(DialMath.RED_60_MPH, _rotationDegrees.value)

    fun currentMultiplier(): Double =
        DialMath.multiplierFromRotation(_rotationDegrees.value)
}
