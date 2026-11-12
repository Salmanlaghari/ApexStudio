package com.apexstudio.app.ui.theme

import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.apexstudio.app.ui.screens.settings.SettingsPreferences

internal val LocalApexThemePalette = staticCompositionLocalOf {
    ApexThemeOption.default.palette
}

/** Composition-scoped theme tokens for app and editor chrome. */
object ApexThemeTokens {
    val current: ApexThemePalette
        @Composable @ReadOnlyComposable get() = LocalApexThemePalette.current
}

private fun colorSchemeFor(palette: ApexThemePalette): ColorScheme = darkColorScheme(
    primary = palette.primary,
    onPrimary = palette.bgDeep,
    primaryContainer = palette.primary.copy(alpha = 0.24f),
    onPrimaryContainer = palette.textPrimary,
    secondary = palette.secondary,
    onSecondary = palette.onSecondary,
    secondaryContainer = palette.secondary.copy(alpha = 0.24f),
    onSecondaryContainer = palette.textPrimary,
    tertiary = palette.primaryGlow,
    onTertiary = palette.bgDeep,
    background = palette.bgBase,
    onBackground = palette.textPrimary,
    surface = palette.bgSurface,
    onSurface = palette.textPrimary,
    surfaceVariant = palette.bgElevated,
    onSurfaceVariant = palette.textSecondary,
    surfaceTint = palette.primary,
    inverseSurface = palette.textPrimary,
    inverseOnSurface = palette.bgDeep,
    inversePrimary = palette.primaryGlow,
    outline = palette.border,
    outlineVariant = palette.divider,
    scrim = Color.Black
)

@Composable
fun ApexTheme(
    option: ApexThemeOption? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val themeFlow = remember(context) { SettingsPreferences.theme(context) }
    val storedOption by themeFlow.collectAsState(initial = ApexThemeOption.default)
    val activeOption = option ?: storedOption
    val activePalette = activeOption.palette
    val activeColors = remember(activePalette) { colorSchemeFor(activePalette) }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = activePalette.bgBase.toArgb()
            window.navigationBarColor = activePalette.bgBase.toArgb()
            val insets = WindowCompat.getInsetsController(window, view)
            insets.isAppearanceLightStatusBars = false
            insets.isAppearanceLightNavigationBars = false
        }
    }
    CompositionLocalProvider(LocalApexThemePalette provides activePalette) {
        MaterialTheme(
            colorScheme = activeColors,
            typography = ApexTypography,
            shapes = ApexShapes,
            content = content
        )
    }
}
