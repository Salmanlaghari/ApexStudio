package com.apexstudio.app.ui.screens.editor

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project

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

        /** Normalize two source-clock marks into a non-empty, in-trim playback selection. */
        fun fromPlaybackMarks(clip: MediaClip, firstMs: Long, secondMs: Long): WorkspaceRange {
            val span = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(1L)
            val minimum = minOf(100L, span)
            val low = minOf(firstMs, secondMs).coerceIn(clip.trimStartMs, clip.trimEndMs)
            val high = maxOf(firstMs, secondMs).coerceIn(clip.trimStartMs, clip.trimEndMs)
            if (high - low >= minimum) return WorkspaceRange(low, high)
            val start = (low - (minimum - (high - low)) / 2L)
                .coerceIn(clip.trimStartMs, clip.trimEndMs - minimum)
            return WorkspaceRange(start, start + minimum)
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
    val duration = com.apexstudio.app.presentation.viewmodel.computeTimelineDurationMs(
        clips, project.audioTracks, project.transitions
    )
    return saved.copy(project = project.copy(clips = clips, durationMs = duration), durationMs = duration)
}

internal const val WORKSPACE_STALE_TARGET_REASON =
    "This clip changed or is no longer selected. Close and reopen the workspace."

/** Mirrors the commit boundary so the UI explains an invalid target before Apply is tapped. */
internal fun workspaceApplyTargetReason(
    projectId: String,
    project: Project?,
    selectedClipId: String?,
    originalClip: MediaClip,
): String? {
    val persistedClip = project?.clips?.firstOrNull { it.id == originalClip.id }
    val targetCurrent = project?.id == projectId && selectedClipId == originalClip.id &&
        persistedClip == originalClip
    val isMainVideo = originalClip.type == ClipType.VIDEO && originalClip.trackIndex == 0 &&
        originalClip.uri.isNotBlank()
    return when {
        !isMainVideo -> "Only a main-track video with a valid source can receive these effects."
        !targetCurrent -> WORKSPACE_STALE_TARGET_REASON
        else -> null
    }
}
