package com.apexstudio.app.ui.screens.editor

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.presentation.state.EditorState

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

/** A text/sticker/audio selection must not edit the video left selected underneath it. */
internal fun selectedKeyframeVideo(state: EditorState): MediaClip? {
    if (state.selectedTextOverlayId != null || state.selectedStickerId != null || state.selectedAudioTrackId != null) return null
    val clips = state.project?.clips.orEmpty()
    val selected = clips.firstOrNull {
        it.id == state.selectedClipId && (it.type == ClipType.VIDEO || it.type == ClipType.OVERLAY)
    } ?: return null
    if (selected.trackIndex in state.hiddenVideoLayers) return null
    return selected.takeIf {
        if (it.type == ClipType.OVERLAY) it.isOverlayActiveAt(state.playerPositionMs)
        else timelinePreviewClip(clips, state.playerPositionMs)?.id == it.id &&
            state.playerPositionMs < clips.filter { clip ->
                clip.trackIndex == 0 && (clip.type == ClipType.VIDEO || clip.type == ClipType.IMAGE)
            }.sumOf { clip -> clip.effectiveDurationMs().coerceAtLeast(0L) }
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
