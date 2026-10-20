package com.apexstudio.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Light neutrals for editor tool panels and sheets only. Keep accent, status,
 * and track colors aligned with [ApexPalette] so media semantics and selection
 * cues do not change when the surrounding controls switch to light surfaces.
 */
object EditorPalette {
    val BgDeep = Color(0xFFF3F5F8)
    val BgBase = Color(0xFFFFFFFF)
    val BgSurface = Color(0xFFFFFFFF)
    val BgElevated = Color(0xFFF1F4F7)
    val BgGlass = Color(0xF2FFFFFF)
    val BgGlassSoft = Color(0xD9FFFFFF)

    // Semantic accents intentionally unchanged from ApexPalette.
    val NeonCyan = ApexPalette.NeonCyan
    val NeonCyanGlow = ApexPalette.NeonCyanGlow
    val NeonPurple = ApexPalette.NeonPurple
    val NeonPurpleGlow = ApexPalette.NeonPurpleGlow
    val NeonEmerald = ApexPalette.NeonEmerald
    val NeonPink = ApexPalette.NeonPink
    val NeonAmber = ApexPalette.NeonAmber
    val TrackBlue = ApexPalette.TrackBlue

    val TextPrimary = Color(0xFF111827)
    val TextSecondary = Color(0xFF475569)
    val TextTertiary = Color(0xFF64748B)
    val TextMuted = Color(0xFF94A3B8)

    val Success = ApexPalette.Success
    val Warning = ApexPalette.Warning
    val Danger = ApexPalette.Danger

    val Divider = Color(0xFFE2E8F0)
    val BorderGlass = Color(0xFFCBD5E1)

    val TrackVideo = ApexPalette.TrackVideo
    val TrackAudio = ApexPalette.TrackAudio
    val TrackOverlay = ApexPalette.TrackOverlay
    val TrackSfx = ApexPalette.TrackSfx

    val GradientPrimary = Brush.linearGradient(listOf(NeonCyan, NeonPurple))
    val GradientSurface = Brush.verticalGradient(listOf(BgSurface, BgDeep))
    val GradientGlass = Brush.verticalGradient(
        listOf(Color(0xFFFFFFFF), Color(0xCCFFFFFF))
    )
    val GradientAccent = Brush.linearGradient(listOf(NeonPurple, NeonCyan))
}
