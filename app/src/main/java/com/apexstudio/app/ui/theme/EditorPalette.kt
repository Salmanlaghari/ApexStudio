package com.apexstudio.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Deep navy editor chrome from the owner's locked 51303 purple/cyan design.
 * Media pixels and semantic meaning are unchanged; populated timeline tracks
 * carry the luminous accents rather than pale gray surfaces or empty lanes.
 */
object EditorPalette {
    val BgDeep = Color(0xFF010914)
    val BgBase = Color(0xFF020D19)
    val BgSurface = Color(0xFF081A2C)
    val BgElevated = Color(0xFF10253C)
    val BgGlass = Color(0xF2081A2C)
    val BgGlassSoft = Color(0xD9081A2C)
    val NeonCyan = ApexPalette.NeonCyan
    val NeonCyanGlow = ApexPalette.NeonCyanGlow
    val NeonPurple = ApexPalette.NeonPurple
    val NeonPurpleGlow = ApexPalette.NeonPurpleGlow
    val NeonEmerald = ApexPalette.NeonEmerald
    val NeonPink = ApexPalette.NeonPink
    val NeonAmber = ApexPalette.NeonAmber
    val TrackBlue = ApexPalette.TrackBlue
    val TextPrimary = Color(0xFFF8FBFF)
    val TextSecondary = Color(0xFFB9C8EE)
    val TextTertiary = Color(0xFF9AADD1)
    val TextMuted = Color(0xFF7488AA)
    val Success = ApexPalette.Success
    val Warning = ApexPalette.Warning
    val Danger = ApexPalette.Danger
    val Divider = Color(0xFF1C3152)
    val BorderGlass = Color(0xFF294273)
    val TrackVideo = ApexPalette.TrackVideo
    val TrackAudio = ApexPalette.TrackAudio
    val TrackOverlay = ApexPalette.TrackOverlay
    val TrackSfx = ApexPalette.TrackSfx
    val GradientPrimary = Brush.horizontalGradient(listOf(Color(0xFF00D9FF), Color(0xFF9700FF)))
    val GradientSurface = Brush.verticalGradient(listOf(BgSurface, BgDeep))
    val GradientGlass = Brush.verticalGradient(listOf(Color(0xFF112641), Color(0xE6061326)))
    val GradientAccent = Brush.horizontalGradient(listOf(NeonPurple, NeonCyan))
}
