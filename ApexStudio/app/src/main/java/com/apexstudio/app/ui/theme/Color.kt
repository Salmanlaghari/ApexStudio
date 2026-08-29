package com.apexstudio.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object ApexPalette {
    val BgDeep = Color(0xFF05080F)
    val BgBase = Color(0xFF0A0E1A)
    val BgSurface = Color(0xFF111827)
    val BgElevated = Color(0xFF1A2236)
    val BgGlass = Color(0xCC0F1626)
    val BgGlassSoft = Color(0x801A2236)

    val NeonCyan = Color(0xFF00E5FF)
    val NeonCyanGlow = Color(0xFF00B8D4)
    val NeonPurple = Color(0xFF7C4DFF)
    val NeonPurpleGlow = Color(0xFF5E35B1)
    val NeonPink = Color(0xFFFF4081)

    val TextPrimary = Color(0xFFF1F5F9)
    val TextSecondary = Color(0xFF94A3B8)
    val TextTertiary = Color(0xFF64748B)
    val TextMuted = Color(0xFF475569)

    val Success = Color(0xFF22C55E)
    val Warning = Color(0xFFF59E0B)
    val Danger = Color(0xFFEF4444)

    val Divider = Color(0xFF1F2937)
    val BorderGlass = Color(0x33FFFFFF)

    val TrackVideo = Color(0xFF7C4DFF)
    val TrackAudio = Color(0xFF00E5FF)
    val TrackOverlay = Color(0xFFFF4081)
    val TrackSfx = Color(0xFF22C55E)

    val GradientPrimary = Brush.linearGradient(
        listOf(NeonCyan, NeonPurple)
    )
    val GradientSurface = Brush.verticalGradient(
        listOf(Color(0xFF0F1626), Color(0xFF0A0E1A))
    )
    val GradientGlass = Brush.verticalGradient(
        listOf(Color(0x55FFFFFF), Color(0x11FFFFFF))
    )
    val GradientAccent = Brush.linearGradient(
        listOf(NeonPurple, NeonCyan)
    )
}
