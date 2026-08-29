package com.apexstudio.app.data.repository

import com.apexstudio.app.domain.model.*

class MediaRepository {

    fun loadProjects(): List<Project> = listOf(
        Project(
            id = "p1", name = "Mountain Adventure", durationMs = 234_000L,
            resolution = "4K", fps = 60,
            clips = listOf(
                MediaClip("c1", "MTN_HIKE_001.mp4", "asset://video1",
                    durationMs = 234_000L, trimEndMs = 234_000L, trackIndex = 0,
                    type = ClipType.VIDEO),
                MediaClip("c2", "Overlay_FX.mp4", "asset://overlay",
                    durationMs = 180_000L, trimStartMs = 12_000L, trimEndMs = 180_000L,
                    trackIndex = 1, type = ClipType.OVERLAY)
            ),
            audioTracks = listOf(
                AudioTrack("a1", "BGM_Track.wav", "asset://bgm", volume = 0.7f),
                AudioTrack("a2", "Voiceover.mp3", "asset://vo", volume = 0.85f),
                AudioTrack("a3", "SFX", "asset://sfx", volume = 0.6f)
            )
        ),
        Project(
            id = "p2", name = "Cinematic Reel 2026", durationMs = 60_000L,
            resolution = "4K", fps = 60
        ),
        Project(
            id = "p3", name = "Travel Vlog – Tokyo", durationMs = 480_000L
        )
    )

    fun loadLutPresets(): List<LutPreset> = listOf(
        LutPreset("cinematic", "CINEMATIC", "asset://lut_cine"),
        LutPreset("nostalgia", "NOSTALGIA", "asset://lut_nos"),
        LutPreset("vintage", "VINTAGE", "asset://lut_vin"),
        LutPreset("teal_orange", "TEAL/ORANGE", "asset://lut_to"),
        LutPreset("mono", "MONO", "asset://lut_mono"),
        LutPreset("lu", "LUT 06", "asset://lut_6")
    )

    fun loadTransitionPresets(): List<ToolItem> = listOf(
        ToolItem("cross", "Cross Dissolve", androidx.compose.material.icons.Icons.Default.Layers, com.apexstudio.app.ui.theme.ApexPalette.NeonCyan),
        ToolItem("wipe", "Wipe", androidx.compose.material.icons.Icons.Default.SwapHoriz, com.apexstudio.app.ui.theme.ApexPalette.NeonPurple),
        ToolItem("zoom", "Zoom", androidx.compose.material.icons.Icons.Default.ZoomIn, com.apexstudio.app.ui.theme.ApexPalette.NeonPink),
        ToolItem("cube", "Cube Spin", androidx.compose.material.icons.Icons.Default.ViewInAr, com.apexstudio.app.ui.theme.ApexPalette.NeonCyan)
    )

    fun loadFxPresets(): List<ToolItem> = listOf(
        ToolItem("chrom", "Chromatic Glitch", androidx.compose.material.icons.Icons.Default.Bolt, com.apexstudio.app.ui.theme.ApexPalette.NeonPurple),
        ToolItem("grain", "Film Grain", androidx.compose.material.icons.Icons.Default.BlurOn, com.apexstudio.app.ui.theme.ApexPalette.TextSecondary),
        ToolItem("vhs", "VHS Retro", androidx.compose.material.icons.Icons.Default.Videocam, com.apexstudio.app.ui.theme.ApexPalette.NeonPink),
        ToolItem("leak", "Light Leak", androidx.compose.material.icons.Icons.Default.WbSunny, com.apexstudio.app.ui.theme.ApexPalette.NeonCyan)
    )
}
