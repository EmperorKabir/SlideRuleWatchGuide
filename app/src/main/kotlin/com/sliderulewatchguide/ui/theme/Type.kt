package com.sliderulewatchguide.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sliderulewatchguide.R

/**
 * Saira Condensed — a free condensed sans-serif used as a stand-in for the
 * brand font (which isn't open-source). Applied to ALL UI text
 * outside the watch face: equation panel, preset chips, input labels,
 * input numbers, every label.
 */
private val Saira = FontFamily(
    Font(R.font.saira_condensed_regular, FontWeight.Normal),
    Font(R.font.saira_condensed_medium, FontWeight.Medium),
    Font(R.font.saira_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.saira_condensed_bold, FontWeight.Bold)
)

private val Default = TextStyle(fontFamily = Saira)

// UI-text defaults are ~26 % above Material's stock sizes (two compounded
// bumps of 15 % and 10 %). Stays in .sp so Android's accessibility
// text-size setting still applies on top. The watch face uses its own
// TextMeasurer formula based on rOuter (capped by DIAL_FONT_SCALE_CEILING)
// and is unaffected.
val AppTypography = Typography(
    displayLarge = Default.copy(fontWeight = FontWeight.Bold, fontSize = 46.sp),
    titleLarge = Default.copy(fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
    titleMedium = Default.copy(fontWeight = FontWeight.Medium, fontSize = 20.sp),
    titleSmall = Default.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    bodyLarge = Default.copy(fontSize = 20.sp),
    bodyMedium = Default.copy(fontSize = 18.sp),
    bodySmall = Default.copy(fontSize = 16.sp),
    labelLarge = Default.copy(fontWeight = FontWeight.Medium, fontSize = 18.sp),
    labelMedium = Default.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp),
    labelSmall = Default.copy(fontSize = 14.sp, letterSpacing = 0.5.sp)
)
