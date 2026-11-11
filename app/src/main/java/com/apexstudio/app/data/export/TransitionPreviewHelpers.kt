package com.apexstudio.app.data.export

import com.apexstudio.app.data.gl.PackedTransitionGlEffect
import com.apexstudio.app.domain.model.ClipTransition
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip

/** Pure, bounded sample used by the picker so only the A tail and B head are decoded. */
internal data class TransitionPreviewSample(
    val plan: ProjectTransitionTimeline.Plan,
    val transitionWindowStartMs: Long,
    val transitionWindowEndMs: Long,
) {
    val transitionDurationMs: Long get() = transitionWindowEndMs - transitionWindowStartMs
}

internal fun buildBoundedTransitionPreviewSample(
    fromClip: MediaClip,
    toClip: MediaClip,
    transitionType: String,
    requestedDurationMs: Long,
): TransitionPreviewSample? {
    if (fromClip.type != ClipType.VIDEO || toClip.type != ClipType.VIDEO) return null
    if (!PackedTransitionGlEffect.supportsTransitionType(transitionType)) return null
    val requested = requestedDurationMs.coerceAtLeast(1L)
    val fromAvailable = (fromClip.trimEndMs - fromClip.trimStartMs).coerceAtLeast(0L)
    val toAvailable = (toClip.trimEndMs - toClip.trimStartMs).coerceAtLeast(0L)
    val valid = minOf(requested, fromAvailable / 2L, toAvailable / 2L)
    if (valid <= 0L) return null
    val fromStart = fromClip.trimEndMs - valid * 2L
    val toEnd = toClip.trimStartMs + valid * 2L
    val from = fromClip.copy(trimStartMs = fromStart.coerceAtLeast(fromClip.trimStartMs))
    val to = toClip.copy(trimEndMs = toEnd.coerceAtMost(toClip.trimEndMs))
    val transition = ClipTransition(
        fromClipId = from.id,
        toClipId = to.id,
        type = transitionType,
        durationMs = valid,
    )
    val plan = ProjectTransitionTimeline.build(listOf(from, to), listOf(transition))
    val window = plan.transitions.singleOrNull() ?: return null
    return TransitionPreviewSample(plan, window.startMs, window.endMs)
}

internal enum class TransitionPreviewBlocker {
    NON_VIDEO_MAIN_TRACK,
    NON_REPRESENTABLE_SPEED_OR_FILTER,
    BEFORE_AFTER_COMPARE,
    AR_FILTER,
    COVER_TITLE,
    GLOBAL_OVERLAYS,
    UNSUPPORTED_TRANSITION_TYPE,
}

internal fun transitionPreviewBlocker(
    plan: ProjectTransitionTimeline.Plan,
    effectsRepresentable: Boolean,
    gpuFilterCompareMode: Boolean,
    lutCompareMode: Boolean,
    activeArFilterId: String?,
    coverText: String,
    hasSignature: Boolean,
    hasGlobalStickers: Boolean,
    hasGlobalDrawings: Boolean,
): TransitionPreviewBlocker? {
    if (plan.entries.any { it.clip.type != ClipType.VIDEO }) return TransitionPreviewBlocker.NON_VIDEO_MAIN_TRACK
    if (!effectsRepresentable) return TransitionPreviewBlocker.NON_REPRESENTABLE_SPEED_OR_FILTER
    if (gpuFilterCompareMode || lutCompareMode) return TransitionPreviewBlocker.BEFORE_AFTER_COMPARE
    if (activeArFilterId != null) return TransitionPreviewBlocker.AR_FILTER
    if (coverText.isNotBlank()) return TransitionPreviewBlocker.COVER_TITLE
    if (hasSignature || hasGlobalStickers || hasGlobalDrawings) return TransitionPreviewBlocker.GLOBAL_OVERLAYS
    if (plan.transitions.any { !PackedTransitionGlEffect.supportsTransitionType(it.transition.type) }) {
        return TransitionPreviewBlocker.UNSUPPORTED_TRANSITION_TYPE
    }
    return null
}

internal fun transitionPreviewBlockerMessage(blocker: TransitionPreviewBlocker): String = when (blocker) {
    TransitionPreviewBlocker.NON_VIDEO_MAIN_TRACK -> "Main-track images or non-video content prevent the full transition preview."
    TransitionPreviewBlocker.NON_REPRESENTABLE_SPEED_OR_FILTER -> "A speed curve or filter cannot be represented in the full transition preview."
    TransitionPreviewBlocker.BEFORE_AFTER_COMPARE -> "Before/after compare mode temporarily disables the full transition preview."
    TransitionPreviewBlocker.AR_FILTER -> "The active AR filter is not available in the full transition preview."
    TransitionPreviewBlocker.COVER_TITLE -> "The cover title is not available in the full transition preview."
    TransitionPreviewBlocker.GLOBAL_OVERLAYS -> "A signature, global sticker, or drawing is not available in the full transition preview."
    TransitionPreviewBlocker.UNSUPPORTED_TRANSITION_TYPE -> "One saved transition is not supported by the preview shader."
}
