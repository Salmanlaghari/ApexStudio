package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.WorkspaceEffect
import com.apexstudio.app.domain.model.WorkspaceEffectPresets
import com.apexstudio.app.domain.model.hasValidParameters

/** Drafts are render-only. This is the only workspace visual-effect commit boundary. */
fun EditorViewModel.applyWorkspaceEffects(
    projectId: String,
    expectedClip: MediaClip,
    effects: List<WorkspaceEffect>,
): Boolean = synchronized(this) {
    val snapshot = state.value
    val project = snapshot.project ?: return@synchronized false
    val current = project.clips.firstOrNull { it.id == expectedClip.id } ?: return@synchronized false
    if (project.id != projectId || snapshot.selectedClipId != expectedClip.id || current != expectedClip ||
        current.type != ClipType.VIDEO || current.trackIndex != 0 || current.uri.isBlank()) return@synchronized false
    if (effects.size > 16 || effects.any { !it.hasValidParameters(current.durationMs) }) return@synchronized false
    if (current.workspaceEffects == effects) return@synchronized true
    pushUndo()
    updateClip(current.id) { it.copy(workspaceEffects = effects.toList()) }
    true
}
