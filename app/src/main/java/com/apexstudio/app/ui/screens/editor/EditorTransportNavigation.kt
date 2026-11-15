package com.apexstudio.app.ui.screens.editor

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.presentation.state.EditorState
import com.apexstudio.app.presentation.viewmodel.TimelineFrameMath

/** Selection is an editing target, never the clock/source of timeline playback. */
internal fun timelinePreviewClip(clips: List<MediaClip>, projectTimeMs: Long): MediaClip? {
    val main = clips.filter { it.trackIndex == 0 && (it.type == ClipType.VIDEO || it.type == ClipType.IMAGE) }
    var end = 0L
    val at = projectTimeMs.coerceAtLeast(0L)
    for (clip in main) {
        end += clip.effectiveDurationMs().coerceAtLeast(0L)
        if (at < end) return clip
    }
    return main.lastOrNull()
}

/** A visible selected clip may show its keyframe affordance before the playhead reaches it. */
internal data class SelectedVideoKeyframeTarget(
    val clip: MediaClip,
    val canEdit: Boolean,
)

/** A text/sticker/audio selection must not edit the video/photo clip left selected underneath it. */
internal fun selectedVisibleVideoClip(state: EditorState): MediaClip? {
    if (state.selectedTextOverlayId != null || state.selectedStickerId != null || state.selectedAudioTrackId != null) return null
    return state.project?.clips.orEmpty().firstOrNull {
        it.id == state.selectedClipId &&
            (it.type == ClipType.VIDEO || it.type == ClipType.IMAGE || it.type == ClipType.OVERLAY)
    }?.takeIf { it.trackIndex !in state.hiddenVideoLayers }
}

/** Keep the selected action visible, but permit mutations only while the clip is active. */
internal fun selectedVideoKeyframeTarget(
    state: EditorState,
    playheadMs: Long = state.playerPositionMs,
): SelectedVideoKeyframeTarget? {
    val clip = selectedVisibleVideoClip(state) ?: return null
    val canEdit = selectedKeyframeVideo(state.copy(playerPositionMs = playheadMs))?.id == clip.id
    return SelectedVideoKeyframeTarget(clip, canEdit)
}

/** Display Add/Remove using the same project-frame identity as the real toggle mutation. */
internal fun hasKeyframeAtProjectFrame(clip: MediaClip, playheadMs: Long, fps: Int): Boolean {
    val safeFps = fps.takeIf { it > 0 } ?: 60
    val frame = TimelineFrameMath.frameIndex(playheadMs, safeFps)
    return clip.keyframes.keyframes.any { TimelineFrameMath.frameIndex(it.timeMs, safeFps) == frame }
}

/** Resolve only the visible selected clip that is active at the project playhead. */
internal fun selectedKeyframeVideo(state: EditorState): MediaClip? {
    val clips = state.project?.clips.orEmpty()
    val selected = selectedVisibleVideoClip(state) ?: return null
    return selected.takeIf {
        when {
            it.type == ClipType.OVERLAY -> it.isOverlayActiveAt(state.playerPositionMs)
            it.trackIndex > 0 -> {
                val startMs = it.timelineOffsetMs
                val durationMs = it.effectiveDurationMs().coerceAtLeast(0L)
                val endMs = if (startMs > Long.MAX_VALUE - durationMs) Long.MAX_VALUE else startMs + durationMs
                state.playerPositionMs in startMs until endMs
            }
            else -> timelinePreviewClip(clips, state.playerPositionMs)?.id == it.id &&
                state.playerPositionMs < clips.filter { clip ->
                    clip.trackIndex == 0 && (clip.type == ClipType.VIDEO || clip.type == ClipType.IMAGE)
                }.sumOf { clip -> clip.effectiveDurationMs().coerceAtLeast(0L) }
        }
    }
}

/** Navigate real main-track edit boundaries in output time, respecting trims/speed. */
internal fun editorClipBoundary(clips: List<MediaClip>, positionMs: Long, durationMs: Long, forward: Boolean): Long {
    var end = 0L
    val boundaries = mutableListOf(0L)
    clips.filter { it.trackIndex == 0 && (it.type == ClipType.VIDEO || it.type == ClipType.IMAGE) }
        .forEach { end += it.effectiveDurationMs().coerceAtLeast(0L); boundaries += end }
    val limit = durationMs.coerceAtLeast(0L)
    return if (forward) (boundaries.firstOrNull { it > positionMs + 1L } ?: end).coerceIn(0L, limit)
    else (boundaries.lastOrNull { it < positionMs - 1L } ?: 0L).coerceIn(0L, limit)
}
