package com.apexstudio.app.ui.screens.editor

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project

/** Editing workspaces never silently substitute the first clip when no clip was selected. */
internal fun selectedWorkspaceVideo(project: Project?, selectedClipId: String?): MediaClip? {
    val id = selectedClipId ?: return null
    return project?.clips?.firstOrNull { it.id == id }?.takeIf {
        it.type == ClipType.VIDEO && it.trackIndex == 0 && it.uri.isNotBlank() &&
            it.trimStartMs >= 0L && it.trimEndMs > it.trimStartMs && it.trimEndMs <= it.durationMs
    }
}
