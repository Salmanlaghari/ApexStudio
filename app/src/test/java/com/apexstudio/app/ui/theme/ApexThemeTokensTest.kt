package com.apexstudio.app.ui.theme

import android.app.Activity
import android.view.Window
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.view.WindowCompat
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h720dp-mdpi")
class ApexThemeTokensTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun completePalettesAreDistinctAndRetainStableStoredValues() {
        assertEquals(listOf("neon", "violet", "ember"), ApexThemeOption.entries.map { it.storedValue })
        assertEquals(ApexThemeOption.default, ApexThemeOption.fromStoredValue(null))
        assertEquals(ApexThemeOption.default, ApexThemeOption.fromStoredValue("future-value"))

        val palettes = ApexThemeOption.entries.map { it.palette }
        assertEquals(palettes.size, palettes.map { it.bgBase }.distinct().size)
        assertEquals(palettes.size, palettes.map { it.bgSurface }.distinct().size)
        assertEquals(palettes.size, palettes.map { it.primary }.distinct().size)
        palettes.forEach { palette ->
            assertTrue(contrastRatio(palette.textPrimary, palette.bgBase) >= 7.0)
            assertTrue(contrastRatio(palette.textSecondary, palette.bgBase) >= 4.5)
            assertTrue(contrastRatio(palette.bgDeep, palette.primary) >= 4.5)
            assertTrue(contrastRatio(palette.onSecondary, palette.secondary) >= 4.5)
        }
    }

    @Test
    fun compositionLocalMaterialSchemeAndSystemBarsFollowThemeSelection() {
        val selected = mutableStateOf(ApexThemeOption.NEON)
        val observed = AtomicReference<ThemeObservation?>(null)
        val observedWindow = AtomicReference<Window?>(null)
        compose.setContent {
            ApexTheme(option = selected.value) {
                val tokens = ApexThemeTokens.current
                val scheme = MaterialTheme.colorScheme
                val view = LocalView.current
                val editorPalette = EditorPaletteObservation(
                    bgDeep = EditorPalette.BgDeep,
                    bgBase = EditorPalette.BgBase,
                    bgSurface = EditorPalette.BgSurface,
                    bgElevated = EditorPalette.BgElevated,
                    bgGlass = EditorPalette.BgGlass,
                    bgGlassSoft = EditorPalette.BgGlassSoft,
                    primary = EditorPalette.NeonCyan,
                    primaryGlow = EditorPalette.NeonCyanGlow,
                    secondary = EditorPalette.NeonPurple,
                    secondaryGlow = EditorPalette.NeonPurpleGlow,
                    textPrimary = EditorPalette.TextPrimary,
                    textSecondary = EditorPalette.TextSecondary,
                    textTertiary = EditorPalette.TextTertiary,
                    textMuted = EditorPalette.TextMuted,
                    divider = EditorPalette.Divider,
                    border = EditorPalette.BorderGlass
                )
                SideEffect {
                    observedWindow.set((view.context as Activity).window)
                    observed.set(
                        ThemeObservation(
                            background = tokens.bgBase,
                            surface = tokens.bgSurface,
                            primary = scheme.primary,
                            tertiary = scheme.tertiary,
                            materialBackground = scheme.background,
                            trackVideo = ApexPalette.TrackVideo,
                            editorPalette = editorPalette
                        )
                    )
                }
            }
        }

        compose.waitForIdle()
        val neon = requireNotNull(observed.get())
        assertEquals(ApexThemeOption.NEON.palette.bgBase, neon.background)
        val neonWindow = requireNotNull(observedWindow.get())
        assertEquals(ApexThemeOption.NEON.palette.bgSurface, neon.surface)
        assertEquals(ApexThemeOption.NEON.palette.primary, neon.primary)
        assertEquals(ApexThemeOption.NEON.palette.primaryGlow, neon.tertiary)
        assertEquals(neon.background, neon.materialBackground)
        assertEditorPaletteMatches(ApexThemeOption.NEON, neon.editorPalette)
        assertEquals(neon.background.toArgb(), neonWindow.statusBarColor)
        assertEquals(neon.background.toArgb(), neonWindow.navigationBarColor)
        assertTrue(!WindowCompat.getInsetsController(
            neonWindow,
            neonWindow.decorView
        ).isAppearanceLightStatusBars)

        compose.runOnIdle { selected.value = ApexThemeOption.VIOLET }
        compose.waitForIdle()
        val violet = requireNotNull(observed.get())
        assertEquals(ApexThemeOption.VIOLET.palette.bgBase, violet.background)
        assertEquals(ApexThemeOption.VIOLET.palette.primary, violet.primary)
        assertEquals(ApexThemeOption.VIOLET.palette.primaryGlow, violet.tertiary)
        assertEditorPaletteMatches(ApexThemeOption.VIOLET, violet.editorPalette)
        assertEquals(violet.background.toArgb(), requireNotNull(observedWindow.get()).statusBarColor)

        compose.runOnIdle { selected.value = ApexThemeOption.EMBER }
        compose.waitForIdle()
        val ember = requireNotNull(observed.get())
        assertEquals(ApexThemeOption.EMBER.palette.bgBase, ember.background)
        assertEquals(ApexThemeOption.EMBER.palette.primary, ember.primary)
        assertEquals(ApexThemeOption.EMBER.palette.primaryGlow, ember.tertiary)
        assertEditorPaletteMatches(ApexThemeOption.EMBER, ember.editorPalette)
        assertNotEquals(neon.background, violet.background)
        assertNotEquals(violet.background, ember.background)
        assertEquals(neon.trackVideo, violet.trackVideo)
        assertEquals(violet.trackVideo, ember.trackVideo)
    }

    private fun assertEditorPaletteMatches(
        option: ApexThemeOption,
        actual: EditorPaletteObservation
    ) {
        val expected = option.palette
        assertEquals(expected.bgDeep, actual.bgDeep)
        assertEquals(expected.bgBase, actual.bgBase)
        assertEquals(expected.bgSurface, actual.bgSurface)
        assertEquals(expected.bgElevated, actual.bgElevated)
        assertEquals(expected.bgSurface.copy(alpha = 0xCC / 255f), actual.bgGlass)
        assertEquals(expected.bgSurface.copy(alpha = 0x80 / 255f), actual.bgGlassSoft)
        assertEquals(expected.primary, actual.primary)
        assertEquals(expected.primaryGlow, actual.primaryGlow)
        assertEquals(expected.secondary, actual.secondary)
        assertEquals(expected.secondaryGlow, actual.secondaryGlow)
        assertEquals(expected.textPrimary, actual.textPrimary)
        assertEquals(expected.textSecondary, actual.textSecondary)
        assertEquals(expected.textTertiary, actual.textTertiary)
        assertEquals(expected.textMuted, actual.textMuted)
        assertEquals(expected.divider, actual.divider)
        assertEquals(expected.border, actual.border)
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val foregroundLuminance = foreground.luminance().toDouble()
        val backgroundLuminance = background.luminance().toDouble()
        return (maxOf(foregroundLuminance, backgroundLuminance) + 0.05) /
            (minOf(foregroundLuminance, backgroundLuminance) + 0.05)
    }

    private data class ThemeObservation(
        val background: Color,
        val surface: Color,
        val primary: Color,
        val tertiary: Color,
        val materialBackground: Color,
        val trackVideo: Color,
        val editorPalette: EditorPaletteObservation
    )

    private data class EditorPaletteObservation(
        val bgDeep: Color,
        val bgBase: Color,
        val bgSurface: Color,
        val bgElevated: Color,
        val bgGlass: Color,
        val bgGlassSoft: Color,
        val primary: Color,
        val primaryGlow: Color,
        val secondary: Color,
        val secondaryGlow: Color,
        val textPrimary: Color,
        val textSecondary: Color,
        val textTertiary: Color,
        val textMuted: Color,
        val divider: Color,
        val border: Color
    )
}
