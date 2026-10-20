package com.apexstudio.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** PR #170 dark editing surfaces, lifted only slightly with a neutral-white tint.
 * Geometry, accent colors and media pixels are unchanged; this is not a light-theme redesign.
 */
object EditorPalette {
    val BgDeep = Color(0xFF10131A)
    val BgBase = Color(0xFF141820)
    val BgSurface = Color(0xFF1B212C)
    val BgElevated = Color(0xFF252D3A)
    val BgGlass = Color(0xF2262E3B)
    val BgGlassSoft = Color(0xD9262E3B)

    // Semantic accents intentionally unchanged from ApexPalette.
    val NeonCyan = ApexPalette.NeonCyan
    val NeonCyanGlow = ApexPalette.NeonCyanGlow
    val NeonPurple = ApexPalette.NeonPurple
    val NeonPurpleGlow = ApexPalette.NeonPurpleGlow
    val NeonEmerald = ApexPalette.NeonEmerald
    val NeonPink = ApexPalette.NeonPink
    val NeonAmber = ApexPalette.NeonAmber
    val TrackBlue = ApexPalette.TrackBlue

    val TextPrimary = Color(0xFFF3F5F7)
    val TextSecondary = Color(0xFFC1CAD6)
    val TextTertiary = Color(0xFF9DAABA)
    val TextMuted = Color(0xFF94A3B8)

    val Success = ApexPalette.Success
    val Warning = ApexPalette.Warning
    val Danger = ApexPalette.Danger

    val Divider = Color(0xFF303A49)
    val BorderGlass = Color(0xFF3B4657)

    val TrackVideo = ApexPalette.TrackVideo
    val TrackAudio = ApexPalette.TrackAudio
    val TrackOverlay = ApexPalette.TrackOverlay
    val TrackSfx = ApexPalette.TrackSfx

    val GradientPrimary = Brush.linearGradient(listOf(NeonCyan, NeonPurple))
    val GradientSurface = Brush.verticalGradient(listOf(BgSurface, BgDeep))
    val GradientGlass = Brush.verticalGradient(
        listOf(Color(0xFF293341), Color(0xCC1B212C))
    )
    val GradientAccent = Brush.linearGradient(listOf(NeonPurple, NeonCyan))
}
