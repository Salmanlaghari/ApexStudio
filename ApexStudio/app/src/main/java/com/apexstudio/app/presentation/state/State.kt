package com.apexstudio.app.presentation.state

import com.apexstudio.app.domain.model.*

data class EditorState(
    val project: Project? = null,
    val isPlaying: Boolean = false,
    val currentTimeMs: Long = 0L,
    val durationMs: Long = 0L,
    val zoomLevel: Float = 1f,
    val selectedTool: EditorTool = EditorTool.TRIM,
    val selectedClipId: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val bpm: Int = 128
)

enum class EditorTool(val label: String) {
    SPLIT("Split"), TRIM("Trim"), KEYFRAME("Keyframe"),
    TRANSITION("Transition"), EFFECTS("Effects"), AUDIO("Audio"),
    TEXT("Text"), COLOR("Color")
}

data class ExportState(
    val isExporting: Boolean = false,
    val progress: Float = 0f,
    val settings: ExportSettings = ExportSettings()
)

data class ColorToolState(
    val shadows: Float = 0.17f,
    val midtones: Float = -0.05f,
    val highlights: Float = 0.23f,
    val activeChannel: Channel = Channel.R,
    val curvePoints: List<Pair<Float, Float>> = defaultCurve(),
    val selectedLut: String = "cinematic"
) {
    enum class Channel { R, G, B, Luma }
    companion object {
        fun defaultCurve(): List<Pair<Float, Float>> = listOf(
            0f to 0f, 0.25f to 0.18f, 0.5f to 0.55f, 0.75f to 0.82f, 1f to 1f
        )
    }
}

data class AudioStudioState(
    val bpm: Int = 128,
    val beatSyncActive: Boolean = true,
    val tracks: List<AudioTrack> = emptyList(),
    val aiVoiceEnhance: Boolean = true,
    val clarity: Float = 0.6f,
    val reduceNoise: Float = 0.4f
)
