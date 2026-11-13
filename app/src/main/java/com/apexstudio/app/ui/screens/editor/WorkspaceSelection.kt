package com.apexstudio.app.ui.screens.editor

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.state.EditorState

/** Editing workspaces never silently substitute the first clip when no clip was selected. */
internal fun selectedWorkspaceVideo(project: Project?, selectedClipId: String?): MediaClip? {
    val id = selectedClipId ?: return null
    return project?.clips?.firstOrNull { it.id == id }?.takeIf {
        it.type == ClipType.VIDEO && it.trackIndex == 0 && it.uri.isNotBlank() &&
            it.trimStartMs >= 0L && it.trimEndMs > it.trimStartMs && it.trimEndMs <= it.durationMs
    }
}

/** Explicit recovery discards the old preview before opening a fresh, saved selection. */
internal fun restartFilterWorkspace(
    projectId: String,
    savedState: EditorState,
    onDismiss: () -> Unit,
    onOpen: (MediaClip) -> Unit,
): Boolean {
    onDismiss()
    val project = savedState.project?.takeIf { it.id == projectId } ?: return false
    val clip = selectedWorkspaceVideo(project, savedState.selectedClipId) ?: return false
    onOpen(clip)
    return true
}
