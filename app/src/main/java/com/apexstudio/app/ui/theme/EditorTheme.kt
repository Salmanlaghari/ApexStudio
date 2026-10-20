package com.apexstudio.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Editor-only light controls. Media renderers and the rest of the app retain their own colors. */
private val EditorColors = lightColorScheme(
    primary = Color(0xFF111827),
    onPrimary = Color.White,
    primaryContainer = EditorPalette.BgElevated,
    onPrimaryContainer = EditorPalette.TextPrimary,
    secondary = Color(0xFF374151),
    onSecondary = Color.White,
    secondaryContainer = EditorPalette.BgElevated,
    onSecondaryContainer = EditorPalette.TextPrimary,
    background = EditorPalette.BgBase,
    onBackground = EditorPalette.TextPrimary,
    surface = EditorPalette.BgSurface,
    onSurface = EditorPalette.TextPrimary,
    surfaceVariant = EditorPalette.BgElevated,
    onSurfaceVariant = EditorPalette.TextSecondary,
    surfaceContainer = EditorPalette.BgSurface,
    surfaceContainerLow = EditorPalette.BgSurface,
    surfaceContainerHigh = EditorPalette.BgElevated,
    error = Color(0xFFB91C1C),
    onError = Color.White,
    outline = Color(0xFF6B7280),
    outlineVariant = EditorPalette.BorderGlass,
)

@Composable
fun EditorLightTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = EditorColors, typography = ApexTypography, shapes = ApexShapes, content = content)
}
