package com.sliderulewatchguide.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = DialGreenLight,
    onPrimary = DialDark,
    secondary = AccentRed,
    onSecondary = BezelWhite,
    background = PanelBg,
    onBackground = PanelText,
    surface = PanelCard,
    onSurface = PanelText,
    surfaceVariant = PanelCard,
    onSurfaceVariant = PanelMuted
)

private val LightColors = lightColorScheme(
    primary = DialGreen,
    onPrimary = BezelWhite,
    secondary = AccentRed,
    onSecondary = BezelWhite,
    background = BezelWhite,
    onBackground = InkBlack,
    surface = BezelWhite,
    onSurface = InkBlack
)

@Composable
fun AppTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
