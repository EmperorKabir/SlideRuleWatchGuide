package com.sliderulewatchguide.dial

import androidx.compose.ui.graphics.Color

/**
 * Burgundy / wine dial palette. Chosen because no major slide-rule pilot's
 * chronograph manufacturer uses a deep wine red on their core line-up, so
 * this colourway sits in a clear visual gap and isn't a knock-off of any
 * known watch's dial.
 *
 * Identifier names retain the historic Green* prefix to keep the rest of
 * the rendering code untouched and minimise drift against the original
 * project's source — the values are burgundy, not green.
 */
object DialPalette {
    // Dial face (sunburst): deep oxblood centre, brighter wine outer band,
    // very dark vignette at the rim.
    val DialGreenInner = Color(0xFF5A1A26)        // oxblood centre
    val DialGreenOuter = Color(0xFF1F0810)        // near-black edge
    val DialGreenSpokeLight = Color(0xFF7B2435)   // sunburst spoke +lum
    val DialGreenSpokeDark = Color(0xFF3A0F18)    // sunburst spoke -lum

    val SubdialBlack = Color(0xFF0A0A0A)
    val SubdialAzureTick = Color(0xFF1F1F1F)

    val BezelInsertBlack = Color(0xFF0B0B0B)
    val BezelEdgeShadow = Color(0xFF000000)

    val BezelWhite = Color(0xFFF0EFEA)
    val BezelEdge = Color(0xFFCFCDC4)

    val Hand = Color(0xFFE8E8E8)
    val HandFrame = Color(0xFFB8BCC0)
    val Lume = Color(0xFFFFFEEA)

    // Central chronograph sweep hand: cream-white instead of the red used
    // on many other slide-rule chronographs. Reads more like a vintage
    // instrument and is visibly distinct.
    val SecondHand = Color(0xFFF5F1E0)
    val Numeral = Color(0xFFFFFFFF)
    // Red still used for triangle markers (10, 36, KM, STAT, NAUT).
    val Red = Color(0xFFD7263D)

    val SteelLight = Color(0xFFE2E5E8)
    val SteelMid = Color(0xFFA8ACB0)
    val SteelDark = Color(0xFF6E7176)
    val SteelGroove = Color(0xFF1A1B1D)

    val CrownSteel = Color(0xFFC8CCCF)
    val DialBorder = Color(0xFF1B1B1B)
    val Tick = Color(0xFFFFFFFF)
    val SubdialTick = Color(0xFFD8D8D8)
}
