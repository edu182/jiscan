package com.jiscan.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Ink green palette — the color the user remembered.
val InkGreen = Color(0xFF2E6B5E)
val InkGreenDark = Color(0xFF1E4A40)
val InkGreenLight = Color(0xFF4A8B7C)
val InkGreenSurface = Color(0xFFE8F0ED)
val Paper = Color(0xFFF7F5EF)

private val LightColors = lightColorScheme(
    primary = InkGreen,
    onPrimary = Color.White,
    primaryContainer = InkGreenSurface,
    onPrimaryContainer = InkGreenDark,
    secondary = InkGreenLight,
    onSecondary = Color.White,
    surface = Color.White,
    onSurface = Color(0xFF1A2420),
    surfaceVariant = InkGreenSurface,
    background = Paper,
    onBackground = Color(0xFF1A2420)
)

private val DarkColors = darkColorScheme(
    primary = InkGreenLight,
    onPrimary = InkGreenDark,
    primaryContainer = InkGreenDark,
    onPrimaryContainer = Color.White,
    secondary = InkGreen,
    onSecondary = Color.White,
    surface = Color(0xFF121A17),
    onSurface = Color(0xFFE6EDEA),
    background = Color(0xFF0C1311),
    onBackground = Color(0xFFE6EDEA)
)

@Composable
fun JiScanTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
