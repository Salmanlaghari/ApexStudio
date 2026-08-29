package com.apexstudio.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val ApexDarkColors = darkColorScheme(
    primary = ApexPalette.NeonCyan,
    onPrimary = ApexPalette.BgDeep,
    primaryContainer = ApexPalette.NeonCyanGlow,
    onPrimaryContainer = ApexPalette.BgDeep,
    secondary = ApexPalette.NeonPurple,
    onSecondary = ApexPalette.TextPrimary,
    secondaryContainer = ApexPalette.NeonPurpleGlow,
    onSecondaryContainer = ApexPalette.TextPrimary,
    tertiary = ApexPalette.NeonPink,
    onTertiary = ApexPalette.TextPrimary,
    background = ApexPalette.BgBase,
    onBackground = ApexPalette.TextPrimary,
    surface = ApexPalette.BgSurface,
    onSurface = ApexPalette.TextPrimary,
    surfaceVariant = ApexPalette.BgElevated,
    onSurfaceVariant = ApexPalette.TextSecondary,
    surfaceTint = ApexPalette.NeonCyan,
    inverseSurface = ApexPalette.TextPrimary,
    inverseOnSurface = ApexPalette.BgDeep,
    error = ApexPalette.Danger,
    onError = ApexPalette.TextPrimary,
    outline = ApexPalette.BorderGlass,
    outlineVariant = ApexPalette.Divider
)

@Composable
fun ApexTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = ApexPalette.BgBase.toArgb()
            window.navigationBarColor = ApexPalette.BgBase.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(
        colorScheme = ApexDarkColors,
        typography = ApexTypography,
        shapes = ApexShapes,
        content = content
    )
}
