package com.apexstudio.app.data.repository

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.ZoomIn
import com.apexstudio.app.domain.model.*
import com.apexstudio.app.ui.theme.ApexPalette

object MediaRepository {
    private val dynamicProjects = mutableListOf<Project>()
    private var projectCounter = 4

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
    ) + dynamicProjects

    fun createProject(name: String, clips: List<MediaClip>): Project {
        val id = "p${projectCounter++}"
        val durationMs = clips.maxOfOrNull { it.durationMs } ?: 0L
        val project = Project(
            id = id,
            name = name,
            durationMs = durationMs,
            resolution = "4K",
            fps = 60,
            clips = clips,
            audioTracks = emptyList()
        )
        dynamicProjects.add(project)
        return project
    }

    fun loadLutPresets(): List<LutPreset> = listOf(
        LutPreset("cinematic", "CINEMATIC", "asset://lut_cine"),
        LutPreset("nostalgia", "NOSTALGIA", "asset://lut_nos"),
        LutPreset("vintage", "VINTAGE", "asset://lut_vin"),
        LutPreset("teal_orange", "TEAL/ORANGE", "asset://lut_to"),
        LutPreset("mono", "MONO", "asset://lut_mono"),
        LutPreset("lu", "LUT 06", "asset://lut_6")
    )

    fun loadTransitionPresets(): List<ToolItem> =
        com.apexstudio.app.data.gl.TransitionEngine.Companion.TransitionType.values().map { trans ->
            val icon = when (trans.category) {
                "Wipe & Split" -> Icons.Default.SwapHoriz
                "Slide & Push" -> Icons.Default.SwapHoriz
                "Zoom & Warp" -> Icons.Default.ZoomIn
                "Glitch & Digital" -> Icons.Default.Bolt
                "Light & Flash" -> Icons.Default.WbSunny
                else -> Icons.Default.Layers
            }
            val tint = when (trans.category) {
                "Wipe & Split" -> ApexPalette.NeonPurple
                "Slide & Push" -> ApexPalette.NeonPink
                "Zoom & Warp" -> ApexPalette.NeonCyan
                "Glitch & Digital" -> ApexPalette.NeonPurple
                "Light & Flash" -> ApexPalette.NeonPink
                else -> ApexPalette.NeonCyan
            }
            ToolItem(trans.id, trans.label, icon, tint)
        }

    fun loadFxPresets(): List<ToolItem> =
        com.apexstudio.app.data.fx.FxPreset.values().map { fx ->
            val icon = when (fx.category) {
                "Glitch & Digital" -> Icons.Default.Bolt
                "Retro & Film" -> Icons.Default.Videocam
                "Blur & Motion" -> Icons.Default.BlurOn
                "Light & Optics" -> Icons.Default.WbSunny
                else -> Icons.Default.Layers
            }
            val tint = when (fx.category) {
                "Glitch & Digital" -> ApexPalette.NeonPurple
                "Retro & Film" -> ApexPalette.NeonPink
                "Blur & Motion" -> ApexPalette.TextSecondary
                "Light & Optics" -> ApexPalette.NeonCyan
                else -> ApexPalette.NeonCyan
            }
            ToolItem(fx.id, fx.label, icon, tint)
        }
}
