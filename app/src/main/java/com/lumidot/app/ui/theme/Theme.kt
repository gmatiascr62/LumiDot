package com.lumidot.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val LumiGreen = Color(0xFF00E676)
val LumiBackground = Color(0xFF05070B)
val LumiSurface = Color(0xFF0E131B)
val LumiSurfaceHigh = Color(0xFF161D28)
val LumiWarning = Color(0xFFFFB74D)
val LumiError = Color(0xFFFF6E6E)

private val colors = darkColorScheme(
    primary = LumiGreen,
    onPrimary = Color(0xFF00210E),
    primaryContainer = Color(0xFF0B3D24),
    onPrimaryContainer = Color(0xFFB9F6CA),
    secondary = Color(0xFF80D8FF),
    onSecondary = Color(0xFF00222E),
    secondaryContainer = Color(0xFF15303D),
    onSecondaryContainer = Color(0xFFCDEFFF),
    background = LumiBackground,
    onBackground = Color(0xFFE6EAF0),
    surface = LumiBackground,
    onSurface = Color(0xFFE6EAF0),
    surfaceVariant = LumiSurfaceHigh,
    onSurfaceVariant = Color(0xFFA9B3C1),
    surfaceContainerLowest = Color(0xFF020305),
    surfaceContainerLow = Color(0xFF0A0E14),
    surfaceContainer = LumiSurface,
    surfaceContainerHigh = LumiSurfaceHigh,
    surfaceContainerHighest = Color(0xFF1E2633),
    outline = Color(0xFF3A4554),
    outlineVariant = Color(0xFF242D3A),
    error = LumiError,
)

@Composable
fun LumiDotTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}
