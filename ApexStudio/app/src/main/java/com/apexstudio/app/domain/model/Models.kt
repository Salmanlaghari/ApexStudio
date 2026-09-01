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
    val type: ClipType = ClipType.VIDEO,
    // Speed ramping: speedMultiplier is the per-clip playback rate
    // (0.25 slow-mo → 8 fast-forward). The user picks one of the
    // SpeedPreset values OR a custom value via the speed panel.
    // speedCurve models acceleration across the clip — LINEAR keeps
    // a constant rate, EASE_IN/OUT ramps the rate, RAMP allows
    // start/end keyframe anchors via [rampStartSpeed, rampEndSpeed].
    val speedMultiplier: Float = 1f,
    val speedCurve: SpeedCurve = SpeedCurve.LINEAR,
    val rampStartSpeed: Float = 1f,
    val rampEndSpeed: Float = 1f
)

enum class ClipType { VIDEO, OVERLAY, AUDIO, SFX }

enum class SpeedCurve {
    LINEAR,        // constant speed across the clip
    EASE_IN,       // accelerate from start speed → end speed
    EASE_OUT,      // decelerate from start speed → end speed
    EASE_IN_OUT,   // smooth accel + decel
    RAMP           // keyframe-driven (start + end)
}

enum class SpeedPreset(val label: String, val multiplier: Float) {
    QUARTER("0.25x", 0.25f),
    HALF("0.5x", 0.5f),
    NORMAL("1x", 1f),
    DOUBLE("2x", 2f),
    QUAD("4x", 4f),
    FAST("8x", 8f);

    companion object {
        fun nearest(value: Float): SpeedPreset =
            values().minBy { kotlin.math.abs(it.multiplier - value) }
    }
}

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
    val isSolo: Boolean = false,
    // Per-track trim + fade (all in ms, relative to the source media).
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L
) {
    /**
     * The kind of audio track, used by the mixer UI to label faders
     * ("Video", "Music", "Voiceover / SFX") and to pick a default
     * colour strip. SFX tracks default to a louder volume than music.
     */
    enum class Kind { ORIGINAL_VIDEO, MUSIC, SFX }

    /** Approximate source duration if known, else 0. */
    val sourceDurationMs: Long
        get() = trimEndMs

    /** Per-track volume multiplier in 0..1, with mute applied. */
    fun effectiveVolume(): Float = if (isMuted) 0f else volume.coerceIn(0f, 1f)
}

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
