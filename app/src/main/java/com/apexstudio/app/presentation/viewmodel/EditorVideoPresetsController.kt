package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.data.template.ProjectVideoPresets
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import kotlinx.coroutines.flow.update

/**
 * Adds a built-in literal title to exactly the video clip represented by [expectedClip].
 * A caller must capture both the project id and complete clip snapshot before opening its UI;
 * stale callbacks and non-video targets are rejected without touching history or project state.
 */
fun EditorViewModel.applyProjectVideoTextTemplate(
    projectId: String,
    expectedClip: MediaClip,
    templateId: String,
): Boolean = synchronized(this) {
    val template = ProjectVideoPresets.textTemplate(templateId) ?: return@synchronized false
    val target = validatedVideoPresetTarget(projectId, expectedClip) ?: return@synchronized false
    val overlay = template.createTextOverlay()

    // This is always an actual change: templates append a new overlay rather than replacing
    // existing titles, and each call creates a fresh overlay id.
    pushUndo()
    updateClip(target.id) { clip -> clip.copy(textOverlays = clip.textOverlays + overlay) }
    _state.update { it.copy(selectedTextOverlayId = overlay.id, selectedStickerId = null) }
    true
}

/** Adds the same fixed literal title with one of the built-in entrance motions. */
fun EditorViewModel.applyProjectVideoTextMotion(
    projectId: String,
    expectedClip: MediaClip,
    motionId: String,
): Boolean = synchronized(this) {
    val motion = ProjectVideoPresets.textMotion(motionId) ?: return@synchronized false
    val target = validatedVideoPresetTarget(projectId, expectedClip) ?: return@synchronized false
    val title = ProjectVideoPresets.textTemplate("title-bold") ?: return@synchronized false
    val overlay = title.createTextOverlay(motion)

    pushUndo()
    updateClip(target.id) { clip -> clip.copy(textOverlays = clip.textOverlays + overlay) }
    _state.update { it.copy(selectedTextOverlayId = overlay.id, selectedStickerId = null) }
    true
}

/** Applies one fixed look plus its fixed title atomically with a single undo entry. */
fun EditorViewModel.applyProjectVideoTemplate(
    projectId: String,
    expectedClip: MediaClip,
    templateId: String,
    allowUnselectedMainTrackTarget: Boolean = false,
): Boolean = synchronized(this) {
    val recipe = ProjectVideoPresets.videoTemplate(templateId) ?: return@synchronized false
    val effect = ProjectVideoPresets.effect(recipe.effectId) ?: return@synchronized false
    val title = ProjectVideoPresets.textTemplate(recipe.textTemplateId) ?: return@synchronized false
    val target = validatedVideoPresetTarget(
        projectId, expectedClip, requireSelectedClip = !allowUnselectedMainTrackTarget,
    ) ?: return@synchronized false
    val overlay = title.createTextOverlay()

    pushUndo()
    updateClip(target.id) { clip ->
        clip.copy(projectVideoEffectId = effect.id, textOverlays = clip.textOverlays + overlay)
    }
    _state.update { it.copy(selectedTextOverlayId = overlay.id, selectedStickerId = null) }
    true
}

/** Applies a fixed, local GPU adjustment preset to the exact captured video clip. */
fun EditorViewModel.applyProjectVideoEffect(
    projectId: String,
    expectedClip: MediaClip,
    effectId: String,
): Boolean = synchronized(this) {
    val effect = ProjectVideoPresets.effect(effectId) ?: return@synchronized false
    val target = validatedVideoPresetTarget(projectId, expectedClip) ?: return@synchronized false
    if (target.projectVideoEffectId == effect.id) return@synchronized true

    // Snapshot only real changes. The persisted id is resolved by the shared color-matrix
    // effect in preview and export; existing clip filter settings are left untouched.
    pushUndo()
    updateClip(target.id) { clip -> clip.copy(projectVideoEffectId = effect.id) }
    true
}

/** Captured selection boundary shared by both select-only video preset actions. */
private fun EditorViewModel.validatedVideoPresetTarget(
    expectedProjectId: String,
    expectedClip: MediaClip,
    requireSelectedClip: Boolean = true,
): MediaClip? {
    val snapshot = state.value
    val project = snapshot.project ?: return null
    if (project.id != expectedProjectId || (requireSelectedClip && snapshot.selectedClipId != expectedClip.id)) return null
    val current = project.clips.firstOrNull { it.id == expectedClip.id } ?: return null
    if (current != expectedClip || current.type != ClipType.VIDEO || current.trackIndex != 0) return null
    if (current.uri.isBlank() || current.durationMs <= 0L) return null
    if (current.trimStartMs < 0L || current.trimEndMs <= current.trimStartMs ||
        current.trimEndMs > current.durationMs
    ) return null
    return current
}
