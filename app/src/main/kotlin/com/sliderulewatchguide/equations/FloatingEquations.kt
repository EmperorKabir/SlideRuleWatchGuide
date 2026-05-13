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
 * Plain-English equation panel — brand-neutral variant of the Navitimer
 * sibling. Marker labels read "Sta" / "Nau" (matching the dial's printed
 * labels) instead of "STAT." / "NAUT.".
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

        // ---------------- Factors of 10
        InfoSection(
            title = "Factors of 10",
            text =
                "All numbers can be multiplied or divided by factors of 10. " +
                "For example, 40 on the bezel could be 0.004, 0.4, 4, 40, 400 " +
                "etc. You need an approximate awareness of which factor of 10 " +
                "you are working with. The slide rule mechanics can't always " +
                "tell you that."
        )

        // ---------------- Division
        Section(
            title = "Division",
            primaryExplanation =
                "Pick a number on the outer ring. Turn the bezel so it lines " +
                "up with a number on the inner ring. The number on the outer " +
                "bezel divided by the number on the inner bezel is shown " +
                "above the inner 10 marker.",
            primaryLive = if (x != null && y != null && y != 0.0)
                "Outer ${fmt(x)} ÷ inner ${fmt(y)} = ${fmt(x / y)}."
            else "Type Outer and Inner above to see the live answer.",
            altExplanation =
                "Alternative: Line up an inner number with an outer number. " +
                "The answer (inner ÷ outer) is the inner value below the " +
                "outer 10 marker.",
            altLive = if (y != null && x != null && x != 0.0)
                "Inner ${fmt(y)} ÷ outer ${fmt(x)} = ${fmt(y / x)}."
            else null
        )

        // ---------------- Multiplication (primary + reversed-lookup alt + div alt)
        MultiSection(
            title = "Multiplication",
            primaryExplanation =
                "Line up the outer 10 marker with any number on the inner " +
                "ring; that number becomes your multiplier. Pick a value on " +
                "the inner bezel. Read the result on the outer bezel directly " +
                "above it.",
            primaryLive = if (y != null)
                "Bezel multiplier is ${fmt(k)}; inner ${fmt(y)} × ${fmt(k)} = ${fmt(y * k)}."
            else "Slide the bezel to set a multiplier; the live value will appear here.",
            alt1Explanation =
                "Alternative: Line up the inner 10 marker with any number " +
                "on the outer bezel; that number becomes your multiplier. " +
                "Pick a value on the outer bezel. Read the result on the " +
                "inner bezel directly below it.",
            alt1Live = if (x != null && invK.isFinite())
                "Bezel multiplier is ${fmt(k)}; outer ${fmt(x)} × ${fmt(k)} = ${fmt(x * k)} on inner."
            else null,
            alt2Explanation =
                "Alternative (division by the same multiplier): the same " +
                "alignment also divides. Pick a number on the outer bezel; " +
                "the inner value directly below it is that outer number " +
                "divided by the multiplier.",
            alt2Live = if (x != null && invK.isFinite())
                "Outer ${fmt(x)} ÷ ${fmt(k)} = ${fmt(x * invK)} on inner."
            else null
        )

        // ---------------- Speed
        Section(
            title = "Speed in miles per hour",
            primaryExplanation =
                "Speed is distance ÷ time, scaled to per hour. Line up your " +
                "distance in miles on the outer bezel with time in minutes on " +
                "the inner bezel. The mph reading appears above the 12 " +
                "o'clock MPH index.",
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
                "First set the outer bezel to your speed: rotate it so the " +
                "mph value lines up with the MPH index at 12 o'clock. The " +
                "inner bezel is now a minutes scale for that speed. Pick any " +
                "distance on the outer bezel. The journey time in minutes " +
                "sits directly below it on the inner bezel.",
            primaryLive = if (x != null && mph.isFinite() && mph > 0)
                "At ${fmt(mph)} mph, ${fmt(x)} ${unit(x, "mile", "miles")} takes " +
                "${fmt(x * 60.0 / mph)} ${unit(x * 60.0 / mph, "minute", "minutes")}."
            else "Set a speed on the dial to see how long a distance takes.",
            altExplanation =
                "Alternative (distance from time): With the same speed " +
                "alignment, pick a time on the inner bezel. The outer value " +
                "above it is how far you travel in that time at the set speed.",
            altLive = if (y != null && mph.isFinite() && mph > 0)
                "At ${fmt(mph)} mph, ${fmt(y)} ${unit(y, "minute", "minutes")} covers " +
                "${fmt(y * mph / 60.0)} ${unit(y * mph / 60.0, "mile", "miles")}."
            else null
        )

        // ---------------- Statute miles ↔ km
        Section(
            title = "Statute miles to kilometres",
            primaryExplanation =
                "Line up your miles value on the outer ring with the Sta " +
                "marker. Read the kilometres above the KM marker.",
            primaryLive =
                "${fmt(statVal)} ${unit(statVal, "statute mile", "statute miles")} = " +
                "${fmt(kmVal)} ${unit(kmVal, "kilometre", "kilometres")}.",
            altExplanation =
                "Reverse: with the same alignment, the outer value above " +
                "the KM marker is the equivalent in statute miles above the " +
                "Sta marker.",
            altLive =
                "${fmt(kmVal)} ${unit(kmVal, "kilometre", "kilometres")} = " +
                "${fmt(statVal)} ${unit(statVal, "statute mile", "statute miles")}."
        )

        // ---------------- Nautical miles ↔ km
        Section(
            title = "Nautical miles to kilometres",
            primaryExplanation =
                "Line up your nautical mile value with the Nau marker. " +
                "Read the kilometres above the KM marker. Same trick as Sta, " +
                "but for sea or air distances.",
            primaryLive =
                "${fmt(nautVal)} ${unit(nautVal, "nautical mile", "nautical miles")} = " +
                "${fmt(kmVal)} ${unit(kmVal, "kilometre", "kilometres")}.",
            altExplanation =
                "Reverse: with the same alignment, the outer value above the " +
                "KM marker is the equivalent in statute miles above the " +
                "Nau marker.",
            altLive =
                "${fmt(kmVal)} ${unit(kmVal, "kilometre", "kilometres")} = " +
                "${fmt(nautVal)} ${unit(nautVal, "nautical mile", "nautical miles")}."
        )

        // ---------------- Nautical miles ↔ Statute miles
        Section(
            title = "Nautical miles to statute miles",
            primaryExplanation =
                "Line up your nautical mile value with the Nau marker. " +
                "Read the statute mile equivalent above the Sta marker " +
                "directly. One nautical mile is about 1.151 statute miles.",
            primaryLive =
                "${fmt(nautVal)} ${unit(nautVal, "nautical mile", "nautical miles")} = " +
                "${fmt(statVal)} ${unit(statVal, "statute mile", "statute miles")}.",
            altExplanation =
                "Reverse: with the same alignment, pick a statute mile value " +
                "and read the equivalent nautical miles above the Nau marker.",
            altLive =
                "${fmt(statVal)} ${unit(statVal, "statute mile", "statute miles")} = " +
                "${fmt(nautVal)} ${unit(nautVal, "nautical mile", "nautical miles")}."
        )

        // ---------------- Hours / Minutes / Seconds
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
                "Alternative (any anchor): the 36 and 60 markers sit on both " +
                "the inner and outer rings, so you can drive the conversion " +
                "from any of the three anchors (10, 60, 36). For example, " +
                "given seconds, line the seconds value (÷ 1000) on the outer " +
                "scale up to inner 36; the equivalent hours read above inner " +
                "10. With 14,400 seconds aligned at inner 36 (outer reads " +
                "14.4), inner 10 sits below outer 4 — that's 4 hours. The " +
                "factor of 10 you need depends on the magnitude of the " +
                "starting value (see Factors of 10 above).",
            altLive =
                "Bezel currently reads: above inner 60 → ${fmt(above60)}; " +
                "above inner 36 → ${fmt(above36)}."
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

@Composable
private fun MultiSection(
    title: String,
    primaryExplanation: String,
    primaryLive: String,
    alt1Explanation: String?,
    alt1Live: String?,
    alt2Explanation: String?,
    alt2Live: String?
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
        if (alt1Explanation != null) {
            Spacer(Modifier.size(8.dp))
            Text(
                alt1Explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (alt1Live != null) {
                Spacer(Modifier.size(4.dp))
                Text(
                    alt1Live, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
        if (alt2Explanation != null) {
            Spacer(Modifier.size(8.dp))
            Text(
                alt2Explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (alt2Live != null) {
                Spacer(Modifier.size(4.dp))
                Text(
                    alt2Live, style = MaterialTheme.typography.bodyMedium,
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
