package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.PhotoCropRect
import com.apexstudio.app.domain.model.PhotoEditSettings
import com.apexstudio.app.domain.model.VideoAdjustments
import com.apexstudio.app.presentation.state.PhotoEditTab
import kotlinx.coroutines.flow.update

// ---------------------------------------------------------------------------
// Photo-editing tools for IMAGE clips (PR F).
//
// Every edit is stored per-clip on [MediaClip.photoEdit] — a single
// serializable source of truth read by both the live preview
// (PhotoClipPreview) and the export (EditorExportController). All
// mutations go through [updateClip] so the project auto-persists.
// ---------------------------------------------------------------------------

fun EditorViewModel.openPhotoEditPanel(tab: PhotoEditTab = PhotoEditTab.CROP) =
    _state.update { it.copy(photoEditPanelOpen = true, photoEditTab = tab) }

fun EditorViewModel.closePhotoEditPanel() =
    _state.update { it.copy(photoEditPanelOpen = false) }

fun EditorViewModel.setPhotoEditTab(tab: PhotoEditTab) =
    _state.update { it.copy(photoEditTab = tab) }

/** Currently selected IMAGE clip, or null. */
fun EditorViewModel.selectedPhotoClip(): MediaClip? {
    val s = _state.value
    val clip = s.project?.clips?.firstOrNull { it.id == s.selectedClipId } ?: return null
    return clip.takeIf { it.type == com.apexstudio.app.domain.model.ClipType.IMAGE }
}

fun EditorViewModel.updatePhotoEdit(
    clipId: String,
    transform: (PhotoEditSettings) -> PhotoEditSettings
) = updateClip(clipId) { clip -> clip.copy(photoEdit = transform(clip.photoEdit)) }

fun EditorViewModel.setPhotoCrop(clipId: String, crop: PhotoCropRect?) =
    updatePhotoEdit(clipId) { it.copy(crop = crop) }

/**
 * Pick an aspect preset: computes the largest centred crop rect for
 * the preset and locks the aspect for handle drags. "free" keeps the
 * current rect (or full frame) and unlocks the aspect.
 */
fun EditorViewModel.setPhotoCropAspectPreset(clipId: String, presetId: String) {
    val aspect = PhotoEditSettings.aspectRatioForPreset(presetId)
    updatePhotoEdit(clipId) { edit ->
        if (aspect == null) {
            edit.copy(cropAspectPreset = "free")
        } else {
            edit.copy(
                crop = PhotoCropRect.centeredForAspect(aspect),
                cropAspectPreset = presetId
            )
        }
    }
}

fun EditorViewModel.updatePhotoAdjustments(
    clipId: String,
    transform: (VideoAdjustments) -> VideoAdjustments
) = updatePhotoEdit(clipId) { it.copy(adjustments = transform(it.adjustments)) }

fun EditorViewModel.resetPhotoAdjustments(clipId: String) =
    updatePhotoEdit(clipId) { it.copy(adjustments = VideoAdjustments()) }

fun EditorViewModel.setPhotoFilter(clipId: String, filterId: String?, intensity: Float = 1f) =
    updatePhotoEdit(clipId) { it.copy(filterId = filterId, filterIntensity = intensity.coerceIn(0f, 1f)) }

fun EditorViewModel.setPhotoFilterIntensity(clipId: String, intensity: Float) =
    updatePhotoEdit(clipId) { it.copy(filterIntensity = intensity.coerceIn(0f, 1f)) }

/** Rotate 90° clockwise (steps accumulate; normalised to 0..3). */
fun EditorViewModel.rotatePhotoClockwise(clipId: String) =
    updatePhotoEdit(clipId) { it.copy(rotationSteps = it.rotationSteps + 1) }

fun EditorViewModel.togglePhotoFlipHorizontal(clipId: String) =
    updatePhotoEdit(clipId) { it.copy(flipHorizontal = !it.flipHorizontal) }

fun EditorViewModel.togglePhotoFlipVertical(clipId: String) =
    updatePhotoEdit(clipId) { it.copy(flipVertical = !it.flipVertical) }

/** Clear every photo edit on the clip back to the untouched photo. */
fun EditorViewModel.resetPhotoEdits(clipId: String) =
    updatePhotoEdit(clipId) { PhotoEditSettings() }
