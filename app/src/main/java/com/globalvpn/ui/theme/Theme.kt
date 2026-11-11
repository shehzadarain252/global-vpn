package com.globalvpn.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Global VPN brand colours — premium blue / purple / white, matching
// the app icon (magenta -> purple -> blue gradient) and the design
// reference screenshot (dark purple dashboard).
val PurplePrimary = Color(0xFF7C3AED)
val PurpleDark = Color(0xFF5B21B6)
val BlueSecondary = Color(0xFF2563EB)
val MagentaAccent = Color(0xFFD946EF)
val CyanAccent = Color(0xFF0EA5E9)
val DarkBackground = Color(0xFF0F0B1E)
val DarkSurface = Color(0xFF1E1B3A)
val DarkSurfaceVariant = Color(0xFF2A2550)
val LightBackground = Color(0xFFF6F4FF)
val LightSurface = Color(0xFFFFFFFF)
val SuccessGreen = Color(0xFF22C55E)
val WarningAmber = Color(0xFFF59E0B)
val ErrorRed = Color(0xFFEF4444)

/** The signature purple -> blue gradient used on the Connect button. */
val BrandGradient = Brush.linearGradient(
    listOf(MagentaAccent, PurplePrimary, BlueSecondary, CyanAccent)
)

private val DarkColors = darkColorScheme(
    primary = PurplePrimary,
    secondary = BlueSecondary,
    tertiary = CyanAccent,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    error = ErrorRed
)

private val LightColors = lightColorScheme(
    primary = PurplePrimary,
    secondary = BlueSecondary,
    tertiary = CyanAccent,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = Color(0xFFEDE9FE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1E1B3A),
    onSurface = Color(0xFF1E1B3A),
    error = ErrorRed
)

@Composable
fun GlobalVpnTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
