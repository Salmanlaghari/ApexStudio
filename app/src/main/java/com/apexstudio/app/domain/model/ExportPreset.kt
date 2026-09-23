package com.apexstudio.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ExportPreset(
    val id: String,
    val name: String,
    val resolution: String,      // "720p", "1080p", "4K", "8K"
    val frameRate: Int,          // 24, 30, 60, 120
    val bitrateMbps: Int,        // e.g. 8, 16, 35, 60, 100
    val codec: String = "H.265", // "H.264" or "H.265"
    val isCustom: Boolean = false,
    val description: String = ""
) {
    companion object {
        val DefaultPresets: List<ExportPreset> = listOf(
            ExportPreset(
                id = "preset_tiktok_reels",
                name = "Reels & Shorts",
                resolution = "1080p",
                frameRate = 60,
                bitrateMbps = 18,
                codec = "H.264",
                isCustom = false,
                description = "1080p 60fps • 18 Mbps (Optimized for Instagram & TikTok)"
            ),
            ExportPreset(
                id = "preset_youtube_4k",
                name = "YouTube 4K Ultra",
                resolution = "4K",
                frameRate = 60,
                bitrateMbps = 55,
                codec = "H.265",
                isCustom = false,
                description = "4K 60fps • 55 Mbps (Pristine YouTube HDR/UHD)"
            ),
            ExportPreset(
                id = "preset_cinematic_24",
                name = "Cinematic 24fps",
                resolution = "4K",
                frameRate = 24,
                bitrateMbps = 40,
                codec = "H.265",
                isCustom = false,
                description = "4K 24fps • 40 Mbps (Classic Hollywood Motion)"
            ),
            ExportPreset(
                id = "preset_fast_share",
                name = "Quick Share 720p",
                resolution = "720p",
                frameRate = 30,
                bitrateMbps = 6,
                codec = "H.264",
                isCustom = false,
                description = "720p 30fps • 6 Mbps (Low file size, instant messaging)"
            ),
            ExportPreset(
                id = "preset_pro_master_8k",
                name = "8K Master Archive",
                resolution = "8K",
                frameRate = 60,
                bitrateMbps = 120,
                codec = "H.265",
                isCustom = false,
                description = "8K 60fps • 120 Mbps (Maximum fidelity studio archive)"
            )
        )
    }
}
