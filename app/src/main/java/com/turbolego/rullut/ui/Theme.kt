package com.turbolego.rullut.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// WCAG-consistent dark theme matching the Expo app's ink/amber palette.
// Contrast ratios verified >= 5.65:1 for text on background.

// Palette — declared BEFORE use (Kotlin requires declaration order)
private val DeepBg = Color(0xFF0D1117)
private val Amber = Color(0xFFE8A020)
private val LightAmber = Color(0xFFF0B840)
private val SteelBlue = Color(0xFF3A5068)
private val TextPrimary = Color(0xFFE6EDF3)
private val TextSecondary = Color(0xFF8B949E)

private val DarkColorScheme = darkColorScheme(
    primary = Amber,
    onPrimary = DeepBg,
    secondary = SteelBlue,
    onSecondary = TextPrimary,
    background = DeepBg,
    onBackground = TextPrimary,
    surface = Color(0xFF161B22),
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF21262D),
    onSurfaceVariant = TextSecondary,
    error = Color(0xFFFF7B72),
    onError = DeepBg,
)

@Composable
fun RullUtTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}