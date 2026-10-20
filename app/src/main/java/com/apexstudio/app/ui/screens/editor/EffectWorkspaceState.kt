package com.apexstudio.app.ui.screens.editor

import com.apexstudio.app.domain.model.MediaClip

internal data class WorkspaceRange(val startMs: Long, val endMs: Long) {
    companion object {
        fun full(clip: MediaClip) = WorkspaceRange(clip.trimStartMs, clip.trimEndMs)
        fun fromFractions(clip: MediaClip, start: Float, end: Float): WorkspaceRange {
            val span = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(1L)
            val a = start.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
            val b = end.takeIf { it.isFinite() }?.coerceIn(a, 1f) ?: 1f
            val minimum = minOf(100L, span)
            val left = (clip.trimStartMs + (span * a).toLong()).coerceIn(clip.trimStartMs, clip.trimEndMs - minimum)
            val right = (clip.trimStartMs + (span * b).toLong()).coerceIn(left + minimum, clip.trimEndMs)
            return WorkspaceRange(left, right)
        }
    }
}

/** The copied state is a render input only; EditorViewModel keeps the saved project unchanged. */
internal fun workspacePreviewState(
    saved: com.apexstudio.app.presentation.state.EditorState,
    draft: MediaClip?,
): com.apexstudio.app.presentation.state.EditorState {
    val project = saved.project ?: return saved
    if (draft == null || project.clips.none { it.id == draft.id }) return saved
    val clips = project.clips.map { if (it.id == draft.id) draft else it }
    val duration = com.apexstudio.app.presentation.viewmodel.computeTimelineDurationMs(clips, project.audioTracks)
    return saved.copy(project = project.copy(clips = clips, durationMs = duration), durationMs = duration)
}
