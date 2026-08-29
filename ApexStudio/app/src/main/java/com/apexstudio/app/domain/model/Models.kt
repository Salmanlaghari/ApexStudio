package com.apexstudio.app.domain.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class MediaClip(
    val id: String,
    val name: String,
    val uri: String,
    val durationMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long,
    val thumbnail: String? = null,
    val trackIndex: Int = 0,
    val type: ClipType = ClipType.VIDEO
)

enum class ClipType { VIDEO, OVERLAY, AUDIO, SFX }

data class Project(
    val id: String,
    val name: String,
    val durationMs: Long,
    val resolution: String = "4K",
    val fps: Int = 60,
    val clips: List<MediaClip> = emptyList(),
    val audioTracks: List<AudioTrack> = emptyList()
)

data class AudioTrack(
    val id: String,
    val name: String,
    val uri: String,
    val volume: Float = 0.75f,
    val isMuted: Boolean = false,
    val isSolo: Boolean = false
)

data class ToolItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val color: Color
)

data class LutPreset(
    val id: String,
    val name: String,
    val thumbnail: String
)

data class ExportSettings(
    val resolution: String = "8K Ultra HD",
    val frameRate: Int = 60,
    val quality: ExportQuality = ExportQuality.HIGH,
    val estimatedSizeGb: Float = 1.8f
)

enum class ExportQuality(val label: String) {
    HIGH("High"), MEDIUM("Med"), LOW("Low")
}
