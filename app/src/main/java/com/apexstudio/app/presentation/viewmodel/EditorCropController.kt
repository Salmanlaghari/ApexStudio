package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.data.effect.VideoCropPreset
import com.apexstudio.app.domain.model.VideoCropSettings
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.PhotoCropRect

/** Save a crop preset on one clip (and therefore with the project) rather than in transient editor state. */
fun EditorViewModel.setVideoCropPreset(
    clipId: String,
    preset: VideoCropPreset,
    sourceAspect: Float
) {
    updateVideoCrop(clipId, preset.centeredWindow(sourceAspect))
}

fun EditorViewModel.updateVideoCrop(clipId: String, crop: VideoCropSettings) {
    updateClip(clipId) { clip ->
        val normalized = crop.sanitized()
        if (clip.type == ClipType.IMAGE) {
            // Photo preview and the still-video export both consume photoEdit.crop.
            clip.copy(
                videoCrop = normalized,
                photoEdit = clip.photoEdit.copy(
                    crop = PhotoCropRect(normalized.left, normalized.top, normalized.right, normalized.bottom),
                    cropAspectPreset = normalized.presetId
                )
            )
        } else clip.copy(videoCrop = normalized)
    }
}

fun EditorViewModel.resetVideoCrop(clipId: String) {
    updateVideoCrop(clipId, VideoCropSettings())
}

fun EditorViewModel.selectedVideoCrop(): VideoCropSettings? {
    val s = _state.value
    val clip = s.project?.clips?.firstOrNull { it.id == s.selectedClipId } ?: return null
    return clip.videoCrop
}
