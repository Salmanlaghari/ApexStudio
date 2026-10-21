package com.apexstudio.app.domain.model

import com.apexstudio.app.data.engine.RangeTimeOperation
import com.apexstudio.app.data.engine.RangeTimeRenderResult

/** Half-open selection in milliseconds relative to the source clip's trimmed start. */
data class SourceRangeMs(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = endMs - startMs

    /** The FFmpeg renderer consumes absolute source-media timestamps. */
    fun absoluteStartMs(clip: MediaClip): Long = clip.trimStartMs + startMs
    fun absoluteEndMs(clip: MediaClip): Long = clip.trimStartMs + endMs
}

enum class RangeTimeRejection {
    PROJECT_MISMATCH,
    CLIP_MISMATCH,
    UNSUPPORTED_CLIP,
    COMPLEX_PROJECT,
    UNSUPPORTED_CLIP_FEATURES,
    INVALID_RANGE,
    INVALID_RENDERED_ASSET,
    UNEXPECTED_RENDER_DURATION
}

data class RangeTimeEligibility(
    val isEligible: Boolean,
    val rejection: RangeTimeRejection? = null,
    val reason: String? = null
) {
    companion object {
        val Eligible = RangeTimeEligibility(isEligible = true)
        fun reject(code: RangeTimeRejection, reason: String) =
            RangeTimeEligibility(isEligible = false, rejection = code, reason = reason)
    }
}

data class RangeTimeEditPlan(
    val projectId: String,
    val sourceClipId: String,
    /** The existing clip id is deliberately retained so selection and non-timing edits survive. */
    val processedClipId: String,
    val range: SourceRangeMs,
    val operation: RangeTimeOperation,
    val originalProjectDurationMs: Long,
    val updatedProject: Project
)

sealed interface RangeTimePlanResult {
    data class Planned(val plan: RangeTimeEditPlan) : RangeTimePlanResult
    data class Rejected(val eligibility: RangeTimeEligibility) : RangeTimePlanResult
}

/** Pure safety assessment and non-mutating whole-clip plan for a local rendered range operation. */
object RangeTimeEdit {
    /**
     * Restrict edits to an isolated V1 video until timeline ripple and timestamp remapping for
     * other lanes are implemented. Unsafe project/clip state is refused, never silently discarded.
     */
    fun eligibility(
        project: Project,
        clipId: String,
        range: SourceRangeMs,
        operation: RangeTimeOperation
    ): RangeTimeEligibility {
        val clip = project.clips.firstOrNull { it.id == clipId }
            ?: return RangeTimeEligibility.reject(RangeTimeRejection.CLIP_MISMATCH, "The selected clip is no longer in this project.")
        if (clip.type != ClipType.VIDEO || clip.trackIndex != 0) {
            return RangeTimeEligibility.reject(RangeTimeRejection.UNSUPPORTED_CLIP, "Range time edits require a main-track video clip.")
        }
        if (project.clips.size != 1 || project.audioTracks.isNotEmpty() || project.transitions.isNotEmpty() ||
            project.stickers.isNotEmpty() || project.videoDrawings.isNotEmpty() || project.coverFrameMs != null ||
            project.coverCustomUri != null) {
            return RangeTimeEligibility.reject(
                RangeTimeRejection.COMPLEX_PROJECT,
                "Range time edits currently require a single video-only project without overlays, audio, transitions, stickers, drawings, or a custom cover."
            )
        }
        if (clip.durationMs <= 0L || clip.trimStartMs < 0L || clip.trimEndMs <= clip.trimStartMs || clip.trimEndMs > clip.durationMs) {
            return RangeTimeEligibility.reject(RangeTimeRejection.INVALID_RANGE, "The selected clip has invalid source trim bounds.")
        }
        val trimmedDuration = clip.trimEndMs - clip.trimStartMs
        if (range.startMs < 0L || range.endMs <= range.startMs || range.endMs > trimmedDuration) {
            return RangeTimeEligibility.reject(
                RangeTimeRejection.INVALID_RANGE,
                "Select a non-empty range within the trimmed source (0–${trimmedDuration} ms)."
            )
        }
        if (clip.speedMultiplier != 1f || clip.speedCurve != SpeedCurve.LINEAR || clip.speedPoints.isNotEmpty() ||
            clip.rampStartSpeed != 1f || clip.rampEndSpeed != 1f || clip.keyframes.keyframes.isNotEmpty() ||
            clip.textOverlays.isNotEmpty() || clip.stickers.isNotEmpty() || clip.mask != null ||
            clip.faceCropTrack != null || clip.backgroundReplace != null || clip.denoiseEnabled ||
            clip.workspaceEffects.isNotEmpty()) {
            return RangeTimeEligibility.reject(
                RangeTimeRejection.UNSUPPORTED_CLIP_FEATURES,
                "This clip has keyframes, drawings, time-varying effects, or speed edits that cannot yet be safely retimed. Remove them or use a plain clip."
            )
        }
        // Reverse is not a speed flag: it is accepted only as a locally materialized renderer output.
        @Suppress("UNUSED_VARIABLE") val requiresRenderedReverse = operation == RangeTimeOperation.REVERSE
        return RangeTimeEligibility.Eligible
    }

    /**
     * Replaces the clip's trimmed media with the renderer's complete output. The derived asset
     * contains the original trimmed clip with only the selected absolute source range transformed;
     * the original URI remains untouched on disk. [range] remains trim-relative for UI callers.
     */
    fun plan(
        project: Project,
        clipId: String,
        range: SourceRangeMs,
        operation: RangeTimeOperation,
        renderedAsset: RangeTimeRenderResult
    ): RangeTimePlanResult {
        val eligibility = eligibility(project, clipId, range, operation)
        if (!eligibility.isEligible) return RangeTimePlanResult.Rejected(eligibility)
        val clip = project.clips.single()
        if (!(renderedAsset.uri.startsWith("file:///") || renderedAsset.uri.startsWith("/")) || renderedAsset.uri == clip.uri || renderedAsset.durationMs <= 0L) {
            return rejected(RangeTimeRejection.INVALID_RENDERED_ASSET, "The renderer must return a distinct, non-empty local asset with a positive duration.")
        }
        val trimmedDuration = clip.trimEndMs - clip.trimStartMs
        val expectedDuration = when (operation) {
            RangeTimeOperation.SLOW, RangeTimeOperation.REPEAT -> safeAdd(trimmedDuration, range.durationMs)
            RangeTimeOperation.REVERSE -> trimmedDuration
        } ?: return rejected(RangeTimeRejection.UNEXPECTED_RENDER_DURATION, "The rendered clip is too long to represent safely.")
        val toleranceMs = maxOf(250L, expectedDuration / 50L)
        if (kotlin.math.abs(renderedAsset.durationMs - expectedDuration) > toleranceMs) {
            return rejected(
                RangeTimeRejection.UNEXPECTED_RENDER_DURATION,
                "The rendered clip duration (${renderedAsset.durationMs} ms) does not match the requested operation (about ${expectedDuration} ms)."
            )
        }

        // Keep the same clip id and every static project-side setting. Only its source asset and
        // trim bounds change; the renderer's derived file has the whole original trimmed clip.
        val replacement = clip.copy(
            uri = renderedAsset.uri,
            durationMs = renderedAsset.durationMs,
            trimStartMs = 0L,
            trimEndMs = renderedAsset.durationMs,
            thumbnail = null,
            speedMultiplier = 1f,
            speedCurve = SpeedCurve.LINEAR,
            rampStartSpeed = 1f,
            rampEndSpeed = 1f,
            speedPoints = emptyList()
        )
        val duration = replacement.effectiveDurationMs().coerceAtLeast(1_000L)
        val updated = project.copy(clips = listOf(replacement), durationMs = duration)
        return RangeTimePlanResult.Planned(
            RangeTimeEditPlan(
                projectId = project.id,
                sourceClipId = clip.id,
                processedClipId = clip.id,
                range = range,
                operation = operation,
                originalProjectDurationMs = project.durationMs,
                updatedProject = updated
            )
        )
    }

    private fun rejected(code: RangeTimeRejection, reason: String) =
        RangeTimePlanResult.Rejected(RangeTimeEligibility.reject(code, reason))

    private fun safeAdd(left: Long, right: Long): Long? =
        if (right > 0L && left > Long.MAX_VALUE - right) null else left + right
}
