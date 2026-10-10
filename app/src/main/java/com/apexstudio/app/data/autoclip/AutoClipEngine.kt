package com.apexstudio.app.data.autoclip

import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.SpeedCurve

/**
 * Phase 3: Auto Clip System (TikTok-style 1-tap edits).
 *
 * A style bundles: beat-sync cut pattern + filter + transition +
 * effect + music mood. [AutoClipEngine.plan] turns the current
 * timeline clips into an [AutoClipPlan] the ViewModel executes with
 * existing primitives (no new engine code paths).
 */

data class AutoClipStyle(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    /** Target seconds per cut before a transition fires. */
    val cutEverySec: Float,
    /** Filter pack id (GpuColorProfile id) or LUT id. Empty = keep current. */
    val filterId: String,
    val filterIntensity: Float = 0.8f,
    /** TransitionLibrary id. */
    val transitionId: String,
    val transitionDurationMs: Long = 400L,
    /** Effect id (StylisticEffectType name) or empty. */
    val effectId: String = "",
    val effectIntensity: Float = 0.5f,
    /** Speed applied to every clip (1f = unchanged). */
    val clipSpeed: Float = 1f,
    val speedCurve: SpeedCurve = SpeedCurve.LINEAR,
    /** Suggested music mood tag for the music picker. */
    val musicMood: String = "trending"
)

val AUTO_CLIP_STYLES = listOf(
    AutoClipStyle(
        id = "viral_reel",
        name = "Viral Reel",
        emoji = "🔥",
        description = "Punchy cuts + vibrant pop + trending beat",
        cutEverySec = 1.5f,
        filterId = "pack_vibrant_pop",
        filterIntensity = 0.85f,
        transitionId = "zoom",
        transitionDurationMs = 350L,
        effectId = "",
        clipSpeed = 1.1f,
        musicMood = "trending"
    ),
    AutoClipStyle(
        id = "cinematic",
        name = "Cinematic",
        emoji = "🎬",
        description = "Slow crossfades + film look + epic score",
        cutEverySec = 4f,
        filterId = "pack_cinematic_teal",
        filterIntensity = 0.9f,
        transitionId = "fade",
        transitionDurationMs = 800L,
        effectId = "VIGNETTE",
        effectIntensity = 0.4f,
        clipSpeed = 0.9f,
        musicMood = "emotional"
    ),
    AutoClipStyle(
        id = "energetic",
        name = "Energetic",
        emoji = "⚡",
        description = "Beat-sync cuts + glitch + high energy",
        cutEverySec = 1f,
        filterId = "pack_vibrant_neon",
        filterIntensity = 0.9f,
        transitionId = "glitch",
        transitionDurationMs = 300L,
        effectId = "",
        clipSpeed = 1.25f,
        speedCurve = SpeedCurve.RAMP,
        musicMood = "energetic"
    ),
    AutoClipStyle(
        id = "emotional",
        name = "Emotional",
        emoji = "💧",
        description = "Gentle fades + soft tones + lofi",
        cutEverySec = 3f,
        filterId = "pack_soft_dream",
        filterIntensity = 0.7f,
        transitionId = "fade",
        transitionDurationMs = 1000L,
        effectId = "",
        clipSpeed = 0.95f,
        musicMood = "lofi"
    )
)

/** One concrete edit operation produced by the planner. */
sealed interface AutoClipOp {
    data class SetSpeed(val clipId: String, val speed: Float, val curve: SpeedCurve) : AutoClipOp
    data class SetTransition(val afterClipId: String, val transitionId: String, val durationMs: Long) : AutoClipOp
    data class SetFilter(val filterId: String, val intensity: Float) : AutoClipOp
    data class SetEffect(val effectId: String, val intensity: Float) : AutoClipOp
    data class TrimTo(val clipId: String, val maxDurationMs: Long) : AutoClipOp
}

data class AutoClipPlan(
    val style: AutoClipStyle,
    val ops: List<AutoClipOp>,
    val summary: String
)

object AutoClipEngine {

    /**
     * Builds an edit plan for [clips] using [style].
     * Pure function — the ViewModel executes the ops with existing APIs.
     */
    fun plan(clips: List<MediaClip>, style: AutoClipStyle): AutoClipPlan {
        val ops = mutableListOf<AutoClipOp>()
        if (clips.isEmpty()) {
            return AutoClipPlan(style, emptyList(), "Add clips first, then Auto Clip.")
        }

        // 1. Beat-sync cuts: trim long clips to the style's cut length.
        val cutMs = (style.cutEverySec * 1000).toLong()
        clips.forEach { clip ->
            val dur = clip.durationMs.coerceAtLeast(0L)
            if (dur > cutMs * 1.5f) {
                ops += AutoClipOp.TrimTo(clip.id, cutMs)
            }
            if (style.clipSpeed != 1f) {
                ops += AutoClipOp.SetSpeed(clip.id, style.clipSpeed, style.speedCurve)
            }
        }

        // 2. Transitions between consecutive clips.
        clips.dropLast(1).forEach { clip ->
            ops += AutoClipOp.SetTransition(clip.id, style.transitionId, style.transitionDurationMs)
        }

        // 3. Filter + effect (project-wide look).
        if (style.filterId.isNotBlank()) {
            ops += AutoClipOp.SetFilter(style.filterId, style.filterIntensity)
        }
        if (style.effectId.isNotBlank()) {
            ops += AutoClipOp.SetEffect(style.effectId, style.effectIntensity)
        }

        val summary = buildString {
            append(style.emoji).append(' ').append(style.name).append(": ")
            append(clips.size).append(" clips → ")
            append("${style.cutEverySec}s cuts, ${style.transitionId} transitions")
            if (style.clipSpeed != 1f) append(", ${style.clipSpeed}x speed")
            append(", ${style.musicMood} music")
        }
        return AutoClipPlan(style, ops, summary)
    }
}
