package com.apexstudio.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Composition-scoped editor palette aligned with the user's selected app theme. */
object EditorPalette {
    private val selected: ApexThemePalette
        @Composable @ReadOnlyComposable get() = ApexThemeTokens.current

    val BgDeep: Color
        @Composable @ReadOnlyComposable get() = selected.bgDeep
    val BgBase: Color
        @Composable @ReadOnlyComposable get() = selected.bgBase
    val BgSurface: Color
        @Composable @ReadOnlyComposable get() = selected.bgSurface
    val BgElevated: Color
        @Composable @ReadOnlyComposable get() = selected.bgElevated
    val BgGlass: Color
        @Composable @ReadOnlyComposable get() = selected.bgSurface.copy(alpha = 0xCC / 255f)
    val BgGlassSoft: Color
        @Composable @ReadOnlyComposable get() = selected.bgSurface.copy(alpha = 0x80 / 255f)

    val NeonCyan: Color
        @Composable @ReadOnlyComposable get() = selected.primary
    val NeonCyanGlow: Color
        @Composable @ReadOnlyComposable get() = selected.primaryGlow
    val NeonPurple: Color
        @Composable @ReadOnlyComposable get() = selected.secondary
    val NeonPurpleGlow: Color
        @Composable @ReadOnlyComposable get() = selected.secondaryGlow

    // Semantic/status and timeline colors intentionally remain consistent across themes.
    val NeonEmerald = ApexPalette.NeonEmerald
    val NeonPink = ApexPalette.NeonPink
    val NeonAmber = ApexPalette.NeonAmber
    val TrackBlue = ApexPalette.TrackBlue

    val TextPrimary: Color
        @Composable @ReadOnlyComposable get() = selected.textPrimary
    val TextSecondary: Color
        @Composable @ReadOnlyComposable get() = selected.textSecondary
    val TextTertiary: Color
        @Composable @ReadOnlyComposable get() = selected.textTertiary
    val TextMuted: Color
        @Composable @ReadOnlyComposable get() = selected.textMuted

    val Success = ApexPalette.Success
    val Warning = ApexPalette.Warning
    val Danger = ApexPalette.Danger
    val Divider: Color
        @Composable @ReadOnlyComposable get() = selected.divider
    val BorderGlass: Color
        @Composable @ReadOnlyComposable get() = selected.border

    val TrackVideo = ApexPalette.TrackVideo
    val TrackAudio = ApexPalette.TrackAudio
    val TrackOverlay = ApexPalette.TrackOverlay
    val TrackSfx = ApexPalette.TrackSfx

    internal val GradientPrimaryStops: List<Color>
        @Composable @ReadOnlyComposable get() = listOf(selected.primary, selected.secondary)
    internal val GradientSurfaceStops: List<Color>
        @Composable @ReadOnlyComposable get() = listOf(selected.bgSurface, selected.bgDeep)
    internal val GradientAccentStops: List<Color>
        @Composable @ReadOnlyComposable get() = listOf(selected.secondary, selected.primary)
    val GradientPrimary: Brush
        @Composable @ReadOnlyComposable get() = Brush.linearGradient(GradientPrimaryStops)
    val GradientSurface: Brush
        @Composable @ReadOnlyComposable get() = Brush.verticalGradient(GradientSurfaceStops)
    // Neutral specular sheen intentionally stays theme-independent; its surface is theme-aware.
    val GradientGlass = ApexPalette.GradientGlass
    val GradientAccent: Brush
        @Composable @ReadOnlyComposable get() = Brush.linearGradient(GradientAccentStops)
}
