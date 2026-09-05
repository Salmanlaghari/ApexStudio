package com.apexstudio.app.data.template

import kotlinx.serialization.Serializable

/**
 * Data models for JSON timeline templates.
 *
 * Captures all video production attributes:
 * - Clips and trimming
 * - Color grading filters and 3D LUT assets
 * - Transitions between clips (Cross dissolve, wipe, zoom blur, slide, glitch)
 * - Visual effects (RGB split, glitch, VHS)
 * - Audio tracks and volume levels
 * - Text titles and captions
 */
@Serializable
data class TimelineTemplate(
    val id: String,
    val name: String,
    val description: String = "",
    val resolution: String = "1080p",
    val fps: Int = 60,
    val aspectRatio: String = "16:9",
    val clips: List<TemplateClip> = emptyList(),
    val filters: List<TemplateFilter> = emptyList(),
    val transitions: List<TemplateTransition> = emptyList(),
    val effects: List<TemplateEffect> = emptyList(),
    val audioTracks: List<TemplateAudioTrack> = emptyList(),
    val textOverlays: List<TemplateTextOverlay> = emptyList()
) {
    val totalDurationMs: Long
        get() = clips.sumOf { (it.trimEndMs - it.trimStartMs).coerceAtLeast(0L) }
}

@Serializable
data class TemplateClip(
    val id: String,
    val name: String = "",
    val uri: String,
    val durationMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = durationMs,
    val speedMultiplier: Float = 1.0f
)

@Serializable
data class TemplateFilter(
    val clipId: String,
    val filterId: String,
    val lutAsset: String? = null,
    val intensity: Float = 1.0f
)

@Serializable
data class TemplateTransition(
    val fromClipId: String,
    val toClipId: String,
    val type: String, // "cross", "wipe", "zoom", "slide", "glitch"
    val durationMs: Long = 500L
)

@Serializable
data class TemplateEffect(
    val clipId: String,
    val effectType: String, // "rgb_split", "glitch", "vhs", "grain", "vignette"
    val startTimeMs: Long = 0L,
    val endTimeMs: Long = Long.MAX_VALUE,
    val intensity: Float = 1.0f
)

@Serializable
data class TemplateAudioTrack(
    val id: String,
    val name: String = "Audio",
    val uri: String,
    val startMs: Long = 0L,
    val volume: Float = 0.8f,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L
)

@Serializable
data class TemplateTextOverlay(
    val id: String,
    val text: String,
    val posX: Float = 0.5f,
    val posY: Float = 0.5f,
    val scale: Float = 1.0f,
    val colorArgb: Long = 0xFFFFFFFFL,
    val bgArgb: Long? = null,
    val startMs: Long = 0L,
    val endMs: Long = Long.MAX_VALUE
)


/**
 * Result of parsing a `transmission_templates.json` payload.
 *
 * Surfaces how many entries were dropped because they referenced a
 * LUT id not in the live manifest or an FX preset id not in the
 * [com.apexstudio.app.data.fx.FxPreset] enum, so the editor can
 * log a one-line summary instead of silently dropping the chips the
 * user expected to see in the transmission panel.
 */
data class TransmissionTemplateLoadResult(
    val templates: List<TransmissionTemplate>,
    val skippedCount: Int,
    val skippedIds: List<String>
)
