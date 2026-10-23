package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.data.effect.VideoCropPreset
import com.apexstudio.app.domain.model.VideoCropSettings
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.PhotoCropRect

/** Save a crop preset on one clip rather than in transient editor state. */
fun EditorViewModel.setVideoCropPreset(clipId: String, preset: VideoCropPreset, sourceAspect: Float) {
    // Free/reset needs no source metadata. Other presets must not assume a square source.
    if (preset.aspectRatio != null && (!sourceAspect.isFinite() || sourceAspect <= 0f)) return
    updateVideoCrop(clipId, preset.centeredWindow(sourceAspect))
}

/** Preset callbacks, including Reset, must obey the same snapshot boundary as manual drafts. */
fun EditorViewModel.applyVideoCropPreset(
    expectedProjectId: String,
    expectedClip: MediaClip,
    preset: VideoCropPreset,
    sourceAspect: Float
): Boolean {
    if (preset.aspectRatio != null && (!sourceAspect.isFinite() || sourceAspect <= 0f)) return false
    return applyVideoCropDraft(expectedProjectId, expectedClip, preset.centeredWindow(sourceAspect))
}

private fun MediaClip.withManualCrop(crop: VideoCropSettings): MediaClip {
    val normalized = crop.sanitized()
    return if (type == ClipType.IMAGE) copy(
        videoCrop = normalized,
        faceCropTrack = null,
        photoEdit = photoEdit.copy(
            crop = PhotoCropRect(normalized.left, normalized.top, normalized.right, normalized.bottom),
            cropAspectPreset = normalized.presetId
        )
    ) else copy(videoCrop = normalized, faceCropTrack = null)
}

/** Exactly one history entry per committed crop, never one per slider/drag update. */
fun EditorViewModel.updateVideoCrop(clipId: String, crop: VideoCropSettings) {
    val clip = _state.value.project?.clips?.firstOrNull { it.id == clipId } ?: return
    if (clip.type !in setOf(ClipType.VIDEO, ClipType.IMAGE, ClipType.OVERLAY)) return
    val updated = clip.withManualCrop(crop)
    if (updated == clip) return
    pushUndo()
    updateClip(clipId) { updated }
}

/** A draft cannot accidentally commit to a newly selected or replaced clip/project. */
fun EditorViewModel.applyVideoCropDraft(
    expectedProjectId: String,
    expectedClip: MediaClip,
    crop: VideoCropSettings
): Boolean {
    val state = _state.value
    val project = state.project ?: return false
    if (project.id != expectedProjectId || state.selectedClipId != expectedClip.id) return false
    val current = project.clips.firstOrNull { it.id == expectedClip.id } ?: return false
    if (current != expectedClip || current.type !in setOf(ClipType.VIDEO, ClipType.IMAGE, ClipType.OVERLAY)) return false
    updateVideoCrop(current.id, crop)
    return true
}

fun EditorViewModel.resetVideoCrop(clipId: String) {
    updateVideoCrop(clipId, VideoCropSettings())
}

fun EditorViewModel.selectedVideoCrop(): VideoCropSettings? {
    val state = _state.value
    return state.project?.clips?.firstOrNull { it.id == state.selectedClipId }?.videoCrop
}
