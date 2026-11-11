package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.data.export.ProjectTransitionTimeline
import com.apexstudio.app.data.template.ProjectVideoPresets
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.timeline.syncSharedTimeline
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

/**
 * Applies a fixed look and title to a deterministic montage of the captured V1 source.
 * The source trim is divided into equal source-time shots; no transition is generated here.
 */
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
    val blockReason = projectVideoTemplateBlockReason(target)
    if (blockReason != null) return@synchronized false

    val shotCount = projectVideoTemplateShotCount(recipe.id)
    val span = target.trimEndMs - target.trimStartMs
    val boundaries = (0..shotCount).map { index ->
        target.trimStartMs + (span * index) / shotCount
    }
    if (boundaries.zipWithNext().any { (start, end) -> end <= start }) return@synchronized false

    val project = _state.value.project ?: return@synchronized false
    val generated = (0 until shotCount).map { index ->
        val shotStart = boundaries[index]
        val shotEnd = boundaries[index + 1]
        target.copy(
            id = if (index == 0) target.id else "${target.id}_template_${recipe.id}_$index",
            name = "${target.name} · Shot ${index + 1}",
            trimStartMs = shotStart,
            trimEndMs = shotEnd,
            // A generated thumbnail is requested by the normal clip-id cache path.
            thumbnail = null,
            projectVideoEffectId = effect.id,
            textOverlays = listOf(title.createTextOverlay()),
        )
    }
    val targetIndex = project.clips.indexOfFirst { it.id == target.id }
    if (targetIndex < 0) return@synchronized false
    val updatedClips = project.clips.toMutableList().apply {
        subList(targetIndex, targetIndex + 1).clear()
        addAll(targetIndex, generated)
    }
    val remappedTransitions = project.transitions.map { transition ->
        when {
            transition.toClipId == target.id -> transition.copy(toClipId = generated.first().id)
            transition.fromClipId == target.id -> transition.copy(fromClipId = generated.last().id)
            else -> transition
        }
    }
    val transitions = ProjectTransitionTimeline.normalizeTransitions(updatedClips, remappedTransitions)
    val duration = computeTimelineDurationMs(updatedClips, project.audioTracks, transitions)
    val updatedProject = project.copy(
        clips = updatedClips,
        transitions = transitions,
        durationMs = duration,
    ).syncSharedTimeline(refreshLegacyClipFields = true, refreshLegacyTextStickerFields = true)

    // One snapshot and one persistence call make the whole montage one atomic edit.
    pushUndo()
    _state.update { state ->
        val replacesSelectedClip = state.selectedClipId == target.id
        state.copy(
            project = updatedProject,
            durationMs = duration,
            canUndo = true,
            canRedo = false,
            // Keep a PiP (or other) selection exactly as captured by the panel.
            selectedTextOverlayId = if (replacesSelectedClip) {
                generated.first().textOverlays.first().id
            } else state.selectedTextOverlayId,
            selectedStickerId = if (replacesSelectedClip) null else state.selectedStickerId,
        )
    }
    persistProject(
        importLegacyAnimation = true,
        refreshLegacyClipFields = true,
        refreshLegacyTextStickerFields = true,
    )
    true
}

/** A user-facing reason for the few clip states that cannot be split safely. */
fun projectVideoTemplateBlockReason(clip: MediaClip): String? = when {
    clip.speedPoints.size >= 2 -> "Templates need a constant-speed source; flatten its speed curve first."
    clip.keyframes.keyframes.isNotEmpty() -> "Templates cannot safely split a clip with animated keyframes."
    clip.textOverlays.isNotEmpty() -> "Templates cannot safely split a clip that already has clip titles."
    clip.stickers.isNotEmpty() -> "Templates cannot safely split a clip with clip stickers."
    clip.faceCropTrack != null -> "Templates cannot safely split a clip with a source-time face track."
    clip.workspaceEffects.isNotEmpty() -> "Templates cannot safely split a clip with source-time effects."
    else -> null
}

private fun projectVideoTemplateShotCount(templateId: String): Int = when (templateId) {
    "warm-story" -> 3
    "cool-moment" -> 4
    else -> 0
}

/** Fixed, input-free effects are valid only on the exact captured V1 video clip. */
fun EditorViewModel.applyProjectVideoEffect(
    projectId: String,
    expectedClip: MediaClip,
    effectId: String,
): Boolean = synchronized(this) {
    val effect = ProjectVideoPresets.effect(effectId) ?: return@synchronized false
    val target = validatedVideoPresetTarget(projectId, expectedClip) ?: return@synchronized false
    if (target.projectVideoEffectId == effect.id) return@synchronized true

    pushUndo()
    updateClip(target.id) { clip -> clip.copy(projectVideoEffectId = effect.id) }
    true
}

/** Captured selection boundary shared by all select-only video preset actions. */
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
