package com.sliderulewatchguide.equations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sliderulewatchguide.dial.DialMath
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Plain-English equation panel.
 *
 * Sections that have an alternative direction (Division, Multiplication,
 * Hours/Min/Sec) render two live answers: the primary in the theme's
 * primary colour, and the alternative in the tertiary colour.
 *
 * The km and nautical-km sections read values straight from the bezel.
 * The outer scale value above each marker is the answer; no textbook
 * conversion factor is re-applied. That's the point of the slide rule.
 */
@Composable
fun FloatingEquations(
    rotationDegrees: Double,
    outer: String,
    inner: String,
    statRead: String,
    nautRead: String,
    kmRead: String,
    modifier: Modifier = Modifier
) {
    val k = DialMath.multiplierFromRotation(rotationDegrees)
    val invK = if (k > 0 && k.isFinite()) 1.0 / k else Double.NaN
    val x = outer.toDoubleOrNull()
    val y = inner.toDoubleOrNull()
    val mph = DialMath.outerValueAtInner(60.0, rotationDegrees)
    val above60 = DialMath.outerValueAtInner(60.0, rotationDegrees)
    val above36 = DialMath.outerValueAtInner(36.0, rotationDegrees)
    val statVal = statRead.toDoubleOrNull() ?: DialMath.STAT_MARKER
    val nautVal = nautRead.toDoubleOrNull() ?: DialMath.NAUT_MARKER
    val kmVal = kmRead.toDoubleOrNull() ?: DialMath.KM_MARKER

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Live equations", style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        // ---------------- Factors of 10 (static note, applies to every section)
        InfoSection(
            title = "Factors of 10",
            text =
                "All numbers can be multiplied or divided by factors of 10. " +
                "For example, 40 on the bezel could be 0.004, 0.4, 4, 40, 400 " +
                "etc. You need an approximate awareness of which factor of 10 " +
                "you are working with. The slide rule mechanics can't always " +
                "tell you that."
        )

        // ---------------- Division (with alternative direction)
        Section(
            title = "Division",
            primaryExplanation =
                "Pick a number on the outer ring. Turn the bezel so it lines " +
                "up with a number on the inner ring. The bezel has just done " +
                "a division for you. Read the answer above inner 10.",
            primaryLive = if (x != null && y != null && y != 0.0)
                "Outer ${fmt(x)} ÷ inner ${fmt(y)} = ${fmt(x / y)}."
            else "Type Outer and Inner above to see the live answer.",
            altExplanation =
                "Alternative: Line up an inner number with an outer number. " +
                "The answer (inner ÷ outer) is the inner value below outer 10.",
            altLive = if (y != null && x != null && x != 0.0)
                "Inner ${fmt(y)} ÷ outer ${fmt(x)} = ${fmt(y / x)}."
            else null
        )

        // ---------------- Multiplication (with alternative direction)
        Section(
            title = "Multiplication",
            primaryExplanation =
                "Line up outer 10 with any number on the inner ring; that " +
                "number becomes your multiplier. Pick a value on the inner " +
                "side. Read the result on the outer side directly above it.",
            primaryLive = if (y != null)
                "Bezel multiplier is ${fmt(k)}; inner ${fmt(y)} × ${fmt(k)} = ${fmt(y * k)}."
            else "Slide the bezel to set a multiplier; the live value will appear here.",
            altExplanation =
                "Alternative: Line up inner 10 with the outer multiplier. Any " +
                "inner number reads on the outer scale × that multiplier.",
            altLive = if (x != null && invK.isFinite())
                "Outer ${fmt(x)} × ${fmt(invK)} = ${fmt(x * invK)} on inner."
            else null
        )

        // ---------------- Speed
        Section(
            title = "Speed in miles per hour",
            primaryExplanation =
                "Speed is distance ÷ time, scaled to per hour. Line up your " +
                "distance in miles on the outer ring with how long it took in " +
                "minutes on the inner ring. The mph reading appears above the " +
                "12 o'clock MPH index.",
            primaryLive = if (x != null && y != null && y != 0.0)
                "${fmt(x)} ${unit(x, "mile", "miles")} in ${fmt(y)} " +
                "${unit(y, "minute", "minutes")} = ${fmt(x / y * 60.0)} mph."
            else "MPH index reads ${fmt(mph)} mph at the current rotation.",
            altExplanation = null,
            altLive = null
        )

        // ---------------- Time
        Section(
            title = "Time for a journey",
            primaryExplanation =
                "First set the bezel to your speed: rotate it so the mph " +
                "value lines up with the MPH index at 12 o'clock. The inner " +
                "ring is now a minutes scale for that speed. Pick any " +
                "distance on the outer ring. The journey time in minutes " +
                "sits directly below it on the inner ring.",
            primaryLive = if (x != null && mph.isFinite() && mph > 0)
                "At ${fmt(mph)} mph, ${fmt(x)} ${unit(x, "mile", "miles")} takes " +
                "${fmt(x * 60.0 / mph)} ${unit(x * 60.0 / mph, "minute", "minutes")}."
            else "Set a speed on the dial to see how long a distance takes.",
            altExplanation = null,
            altLive = null
        )

        // ---------------- Statute miles ↔ km
        Section(
            title = "Miles to kilometres",
            primaryExplanation =
                "Line up your miles value on the outer ring with the small red " +
                "STAT triangle. Read the kilometres above the KM marker.",
            primaryLive =
                "${fmt(statVal)} ${unit(statVal, "statute mile", "statute miles")} = " +
                "${fmt(kmVal)} ${unit(kmVal, "kilometre", "kilometres")}.",
            altExplanation = null,
            altLive = null
        )

        // ---------------- Nautical miles ↔ km
        Section(
            title = "Nautical miles to kilometres",
            primaryExplanation =
                "Line up your nautical mile value with the NAUT triangle. " +
                "Read the kilometres above the KM marker. Same trick as STAT, " +
                "but for sea or air distances.",
            primaryLive =
                "${fmt(nautVal)} ${unit(nautVal, "nautical mile", "nautical miles")} = " +
                "${fmt(kmVal)} ${unit(kmVal, "kilometre", "kilometres")}.",
            altExplanation = null,
            altLive = null
        )

        // ---------------- Hours / Minutes / Seconds (with alternative)
        Section(
            title = "Hours, minutes and seconds",
            primaryExplanation =
                "There are 60 seconds in a minute and 60 minutes in an hour, " +
                "so 60 × 60 = 3600 seconds in an hour. That's why the red 36 " +
                "marker matters; it stands for 3600. Line up your hours times " +
                "ten on the outer ring against inner 10, the unit index. The " +
                "minutes sit above inner 60. The seconds sit above inner 36. " +
                "All in one move. For example, align outer 40 (= 4 hours) " +
                "with inner 10. Above inner 60 reads 24 (= 240 minutes). " +
                "Above inner 36 reads 14.4 (= 14,400 seconds).",
            primaryLive = "${fmt(k)} ${unit(k, "hour", "hours")} = " +
                "${fmt(k * 60)} ${unit(k * 60, "minute", "minutes")} = " +
                "${fmt(k * 3600)} ${unit(k * 3600, "second", "seconds")}.",
            altExplanation =
                "Alternative: The 36 and 60 markers sit on both the inner " +
                "and outer rings, so you can invert the calculation just like " +
                "division and multiplication. Drive it from whichever anchor " +
                "is more convenient. The bezel keeps the others in sync.",
            altLive =
                "Bezel reads above inner 60: ${fmt(above60)}; above inner 36: ${fmt(above36)}."
        )
    }
}

@Composable
private fun InfoSection(title: String, text: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.size(2.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Section(
    title: String,
    primaryExplanation: String,
    primaryLive: String,
    altExplanation: String?,
    altLive: String?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.size(2.dp))
        Text(
            primaryExplanation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(4.dp))
        Text(
            primaryLive, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
        if (altExplanation != null) {
            Spacer(Modifier.size(8.dp))
            Text(
                altExplanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (altLive != null) {
                Spacer(Modifier.size(4.dp))
                Text(
                    altLive, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

private fun fmt(value: Double): String {
    if (!value.isFinite()) return "—"
    val rounded = value.roundToInt()
    if (abs(value - rounded) < 1e-6 && abs(value) < 1e9) {
        return rounded.toString()
    }
    val s = "%.4f".format(value).trimEnd('0').trimEnd('.')
    return s.ifEmpty { "0" }
}

private fun unit(value: Double, singular: String, plural: String): String {
    val isOne = abs(value - 1.0) < 1e-6
    return if (isOne) singular else plural
}
