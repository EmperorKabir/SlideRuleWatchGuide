package com.sliderulewatchguide.viewmodel

import androidx.lifecycle.ViewModel
import com.sliderulewatchguide.dial.DialMath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.abs
import kotlin.math.round

enum class ChronoState { IDLE, RUNNING, STOPPED }

class DialViewModel : ViewModel() {

    // ---------------------------------------------------------------- bezel

    private val _rotationDegrees = MutableStateFlow(0.0)
    val rotationDegrees: StateFlow<Double> = _rotationDegrees.asStateFlow()

    // Five inter-connected input fields. Their default reset values are
    // the slide-rule values that sit above each anchor at rotation = 0.
    private val _outerInput = MutableStateFlow("60")
    val outerInput: StateFlow<String> = _outerInput.asStateFlow()

    private val _innerInput = MutableStateFlow("60")
    val innerInput: StateFlow<String> = _innerInput.asStateFlow()

    private val _statInput = MutableStateFlow(formatNum(DialMath.STAT_MARKER))
    val statInput: StateFlow<String> = _statInput.asStateFlow()

    private val _nautInput = MutableStateFlow(formatNum(DialMath.NAUT_MARKER))
    val nautInput: StateFlow<String> = _nautInput.asStateFlow()

    private val _kmInput = MutableStateFlow(formatNum(DialMath.KM_MARKER))
    val kmInput: StateFlow<String> = _kmInput.asStateFlow()

    fun rotateBy(deltaDegrees: Double) {
        _rotationDegrees.value = DialMath.wrap360(_rotationDegrees.value + deltaDegrees)
        refreshAllFromRotation()
    }

    fun setRotation(angle: Double) {
        _rotationDegrees.value = DialMath.wrap360(angle)
        refreshAllFromRotation()
    }

    fun snapAlign(outerX: Double, innerY: Double) {
        if (outerX <= 0 || innerY <= 0) return
        _rotationDegrees.value = DialMath.alignRotation(outerX, innerY)
        refreshAllFromRotation()
    }

    fun reset() {
        _rotationDegrees.value = 0.0
        _outerInput.value = "60"
        _innerInput.value = "60"
        _statInput.value = formatNum(DialMath.STAT_MARKER)
        _nautInput.value = formatNum(DialMath.NAUT_MARKER)
        _kmInput.value = formatNum(DialMath.KM_MARKER)
    }

    fun setMultiplier(k: Double) {
        if (k <= 0 || !k.isFinite()) return
        setRotation(DialMath.alignRotation(outerX = 10.0 * k, innerY = 10.0))
    }

    fun currentMultiplier(): Double = DialMath.multiplierFromRotation(_rotationDegrees.value)

    /**
     * "Nudge to nearest integer" — round the outer-scale value the user is
     * currently reading in the Outer field (which is the outer value at
     * the user's chosen Inner anchor) to the nearest whole integer.
     * Half-up rounding: 50.4999 → 50, 50.50 → 51, 50.0 → no-op. Falls
     * back to the MPH index (inner = 60) if the Inner field is empty /
     * non-numeric.
     */
    fun nudgeToNearestInteger() {
        val innerY = _innerInput.value.toDoubleOrNull()
            ?.takeIf { it > 0.0 && it.isFinite() }
            ?: DialMath.RED_60_MPH
        val current = DialMath.outerValueAtInner(innerY, _rotationDegrees.value)
        if (!current.isFinite() || current <= 0.0) return
        val target = kotlin.math.floor(current + 0.5)
            .coerceAtLeast(DialMath.SCALE_MIN)
        if (abs(target - current) < 1e-6) return
        setRotation(DialMath.alignRotation(outerX = target, innerY = innerY))
    }

    // ------------------------------------------------------------- inputs
    //
    // Five user-typed fields. None is silently overwritten while typing
    // (no live cross-update). On commit (focus-out / IME Done) for any
    // field, the bezel snaps to align the typed value at that field's
    // anchor:
    //   • Outer/Inner → snapAlign(outer, inner)
    //   • STAT        → snapAlign(stat, STAT_MARKER)
    //   • NAUT        → snapAlign(naut, NAUT_MARKER)
    //   • KM          → snapAlign(km,   KM_MARKER)
    // After the snap, every field except Inner refreshes from the new
    // rotation. Inner is the user's chosen anchor and stays put.
    //
    // Nonsense input (empty, NaN, ≤0) is silently ignored — the bezel
    // does nothing and the field keeps the user's typed text.

    fun setOuterText(s: String) { _outerInput.value = s }
    fun setInnerText(s: String) { _innerInput.value = s }
    fun setStatText(s: String)  { _statInput.value = s }
    fun setNautText(s: String)  { _nautInput.value = s }
    fun setKmText(s: String)    { _kmInput.value = s }

    fun commitInputs() {
        val x = _outerInput.value.toDoubleOrNull() ?: return
        val y = _innerInput.value.toDoubleOrNull() ?: return
        if (x <= 0 || y <= 0 || !x.isFinite() || !y.isFinite()) return
        snapAlign(x, y)
    }

    fun commitStat() = commitAtMarker(_statInput.value, DialMath.STAT_MARKER)
    fun commitNaut() = commitAtMarker(_nautInput.value, DialMath.NAUT_MARKER)
    fun commitKm()   = commitAtMarker(_kmInput.value,   DialMath.KM_MARKER)

    private fun commitAtMarker(text: String, marker: Double) {
        val v = text.toDoubleOrNull() ?: return
        if (v <= 0 || !v.isFinite()) return
        _rotationDegrees.value = DialMath.alignRotation(outerX = v, innerY = marker)
        refreshAllFromRotation()
    }

    private fun refreshAllFromRotation() {
        val rot = _rotationDegrees.value
        val innerY = _innerInput.value.toDoubleOrNull()
        if (innerY != null && innerY > 0) {
            _outerInput.value = formatNum(DialMath.outerValueAtInner(innerY, rot))
        }
        _statInput.value = formatNum(DialMath.outerValueAtInner(DialMath.STAT_MARKER, rot))
        _nautInput.value = formatNum(DialMath.outerValueAtInner(DialMath.NAUT_MARKER, rot))
        _kmInput.value   = formatNum(DialMath.outerValueAtInner(DialMath.KM_MARKER,   rot))
    }

    private fun formatNum(v: Double): String {
        if (!v.isFinite()) return ""
        val rounded2 = round(v * 100.0) / 100.0
        if (abs(rounded2 - rounded2.toInt()) < 1e-9 && abs(rounded2) < 1e9) {
            return rounded2.toInt().toString()
        }
        return "%.2f".format(rounded2).trimEnd('0').trimEnd('.')
    }

    // ----------------------------------------------------------- chronograph

    private val _chronoState = MutableStateFlow(ChronoState.IDLE)
    val chronoState: StateFlow<ChronoState> = _chronoState.asStateFlow()

    private val _accumulatedMs = MutableStateFlow(0L)
    private var startInstant: Instant? = null

    fun chronoStartStop() {
        when (_chronoState.value) {
            ChronoState.IDLE, ChronoState.STOPPED -> {
                startInstant = Clock.System.now()
                _chronoState.value = ChronoState.RUNNING
            }
            ChronoState.RUNNING -> {
                val now = Clock.System.now()
                val delta = (now - (startInstant ?: now)).inWholeMilliseconds
                _accumulatedMs.value = _accumulatedMs.value + delta
                startInstant = null
                _chronoState.value = ChronoState.STOPPED
            }
        }
    }

    fun chronoReset() {
        if (_chronoState.value == ChronoState.RUNNING) return
        _accumulatedMs.value = 0L
        startInstant = null
        _chronoState.value = ChronoState.IDLE
    }

    fun currentChronoMs(): Long {
        val acc = _accumulatedMs.value
        return when (_chronoState.value) {
            ChronoState.RUNNING -> {
                val now = Clock.System.now()
                acc + (now - (startInstant ?: now)).inWholeMilliseconds
            }
            else -> acc
        }
    }
}
