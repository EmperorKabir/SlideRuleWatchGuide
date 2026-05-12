package com.sliderulewatchguide.dial

enum class ScaleSide { OUTER, INNER }

enum class MarkerStyle { TRIANGLE_INWARD, TRIANGLE_OUTWARD, TEXT, RED_NUMERAL }

/**
 * One marker (label / triangle) on either scale. [scaleValue] is the
 * scale-value position (10..100). [text] is what to draw if [style] needs it.
 */
data class Marker(
    val scaleValue: Double,
    val side: ScaleSide,
    val style: MarkerStyle,
    val text: String? = null,
    val isRed: Boolean = false
)

object Markers {
    val all: List<Marker> = listOf(
        // Inner fixed scale -------------------------------------------------
        // MPH (60) — kept in the list but rendered specially as a WHITE
        // ARROW with curving sides (per photo image 22), not a red triangle.
        Marker(DialMath.RED_60_MPH, ScaleSide.INNER, MarkerStyle.TRIANGLE_OUTWARD, "MPH", isRed = true),
        Marker(DialMath.RED_10, ScaleSide.INNER, MarkerStyle.RED_NUMERAL, "10", isRed = true),
        Marker(DialMath.RED_36, ScaleSide.INNER, MarkerStyle.RED_NUMERAL, "36", isRed = true),
        Marker(DialMath.KM_MARKER, ScaleSide.INNER, MarkerStyle.TEXT, "KM", isRed = true),
        // STAT / NAUT triangles only — text labels are placed BEFORE these
        // triangles (see entries below) so the "35" / "40" portion of the
        // marker reads at the integer scale-position, matching photo
        // image 23.
        Marker(DialMath.STAT_MARKER, ScaleSide.INNER, MarkerStyle.TRIANGLE_OUTWARD, null, isRed = true),
        Marker(DialMath.NAUT_MARKER, ScaleSide.INNER, MarkerStyle.TRIANGLE_OUTWARD, null, isRed = true),
        // "NAUT." text is centred at scale 34 (between the NAUT triangle at
        // 33 and the white "35" numeral at 35), so it reads as
        // "NAUT. 35" continuously. Same for STAT. between 38 and 40.
        // Label CENTRE sits AT the triangle (same angular position) so the
        // text reads UNDER its red triangle on the chapter ring.
        Marker(33.0, ScaleSide.INNER, MarkerStyle.TEXT, "Nau", isRed = true),
        Marker(38.0, ScaleSide.INNER, MarkerStyle.TEXT, "Sta", isRed = true),

        // Outer rotating scale ---------------------------------------------
        Marker(DialMath.RED_60_MPH, ScaleSide.OUTER, MarkerStyle.TRIANGLE_INWARD, null, isRed = true),
        Marker(DialMath.RED_10, ScaleSide.OUTER, MarkerStyle.RED_NUMERAL, "10", isRed = true),
        Marker(DialMath.RED_36, ScaleSide.OUTER, MarkerStyle.RED_NUMERAL, "36", isRed = true)
    )
}
