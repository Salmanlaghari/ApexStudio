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
    val bpm: Int = 128,
    val pickedMedia: List<com.apexstudio.app.data.picker.MediaMetadata> = emptyList(),
    val isMediaPickerOpen: Boolean = false,
    val playerPositionMs: Long = 0L,
    val playerDurationMs: Long = 0L,
    val isPlayerReady: Boolean = false,
    // Set to true while ExoPlayer is in STATE_BUFFERING. Drives the
    // "Loading…" spinner overlay in VideoPreviewSection so the user
    // sees feedback during the 1-3s startup / seek-while-paused gap
    // instead of a black screen. Cleared the moment STATE_READY fires.
    val isBuffering: Boolean = false,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val audioWaveform: FloatArray = FloatArray(0),
    // Crop: normalized (0..1) rectangle inside the source video frame.
    // Defaults to the full frame. When cropMode is true, the overlay is
    // drawn and the user can drag the handles / pick an aspect preset.
    val cropMode: Boolean = false,
    val cropAspect: CropAspect = CropAspect.FREE,
    val cropRect: CropRect = CropRect.Full,
    // Filter panel state. activeFilterId == null means "no filter"
    // (i.e. original video). intensity is 0..1 and is exposed as a
    // slider in the panel so the user can dial the look from
    // subtle to full.
    val filterPanelOpen: Boolean = false,
    val activeFilterId: String? = null,
    val filterIntensity: Float = 1.0f,
    val filterCategory: String = "cinematic",
    // Generated 1:1 filter preview thumbnails (filter ID → ImageBitmap).
    // Populated asynchronously when a clip is loaded; the FilterPanel
    // shows these instead of gradient color blocks.
    val filterThumbnails: Map<String?, androidx.compose.ui.graphics.ImageBitmap> = emptyMap(),
    val filterThumbnailsLoading: Boolean = false,
    // Global playback speed applied to the ExoPlayer preview. Mirrors
    // the speed of the currently selected clip; the speed panel also
    // lets the user set it independently for quick time-lapse previews.
    val playbackSpeed: Float = 1f,
    // Set to true while the Audio Mixer bottom sheet is open.
    val audioMixerOpen: Boolean = false,
    // Set to true while the Speed Ramping bottom sheet is open.
    val speedPanelOpen: Boolean = false,
    // Set to true while the Keyframe Animation bottom sheet is open.
    val keyframePanelOpen: Boolean = false,
    // Real-time FX (VHS, Glitch, Grain, …). activeFxId == null means
    // no FX; intensity is the 0..1 slider exposed in the FX panel.
    val fxPanelOpen: Boolean = false,
    val activeFxId: String? = null,
    val fxIntensity: Float = 1f,
    // Text overlay editing. textPanelOpen is true while the bottom
    // sheet is up; selectedTextOverlayId points at the caption being
    // edited / dragged on the preview.
    val textPanelOpen: Boolean = false,
    val selectedTextOverlayId: String? = null,
    // Set to true while the Trim & Set Points bottom sheet is open.
    val trimPanelOpen: Boolean = false,
    // Set to true while the Transmission Templates bottom sheet is open.
    // Selecting a template from the chip strip drives the LUT + FX +
    // intensity state, which the preview GL pipeline re-reads on the
    // next recomposition.
    val transmissionPanelOpen: Boolean = false
) {
    companion object {
        // Equality on data classes with FloatArray doesn't compare the
        // array contents; EditorViewModel never updates audioWaveform
        // after init, so this is safe to leave to the default.
    }
}

data class CropRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0.01f)
    val height: Float get() = (bottom - top).coerceAtLeast(0.01f)
    val aspect: Float get() = width / height

    fun isFullFrame(): Boolean =
        left <= 0f && top <= 0f && right >= 1f && bottom >= 1f

    companion object {
        val Full = CropRect(0f, 0f, 1f, 1f)
    }
}

enum class CropAspect(val label: String, val ratio: Float?) {
    FREE("Free", null),
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_9_16("9:16", 9f / 16f),
    RATIO_1_1("1:1", 1f),
    RATIO_4_3("4:3", 4f / 3f),
    RATIO_3_4("3:4", 3f / 4f),
    RATIO_21_9("21:9", 21f / 9f)
}

enum class EditorTool(val label: String) {
    SPLIT("Split"), TRIM("Trim"), KEYFRAME("Keyframe"),
    TRANSITION("Transition"), EFFECTS("Effects"), AUDIO("Audio"),
    TEXT("Text"), COLOR("Color")
}

data class ExportState(
    val isExporting: Boolean = false,
    val progress: Float = 0f,
    val settings: ExportSettings = ExportSettings(),
    val outputUri: String? = null,
    val error: String? = null,
    val isExportEngineReady: Boolean = false
)

data class ColorToolState(
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val shadows: Float = 0.17f,
    val midtones: Float = -0.05f,
    val highlights: Float = 0.23f,
    val activeChannel: Channel = Channel.R,
    val curvePoints: List<Pair<Float, Float>> = defaultCurve(),
    val selectedLut: String = "none"
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
    val reduceNoise: Float = 0.4f,
    val lowEQ: Short = 0,
    val midEQ: Short = 0,
    val highEQ: Short = 0,
    val volume: Float = 0.75f,
    val isMuted: Boolean = false,
    val isSolo: Boolean = false,
    val noiseReduction: Float = 0f,
    val echoCancellation: Boolean = false,
    val noiseSuppression: Boolean = false,
    val waveformSamples: FloatArray = FloatArray(0),
    val isRecording: Boolean = false
)