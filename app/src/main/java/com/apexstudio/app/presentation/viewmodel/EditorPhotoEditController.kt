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

/** Deterministic on-device enhancement through the existing photo renderer. */
fun EditorViewModel.applyPhotoAutoEnhance(clipId: String) =
    updatePhotoAdjustments(clipId) {
        it.copy(
            brightness = maxOf(it.brightness, 0.08f),
            contrast = maxOf(it.contrast, 1.08f),
            saturation = maxOf(it.saturation, 1.05f),
            clarity = maxOf(it.clarity, 0.12f),
            sharpness = maxOf(it.sharpness, 0.18f)
        )
    }

/** Enable the existing local denoise shader at a conservative strength. */
fun EditorViewModel.applyPhotoDenoise(clipId: String) =
    updatePhotoAdjustments(clipId) { it.copy(denoise = maxOf(it.denoise, 0.35f)) }

/** Enable the existing local sharpness stage without changing other edits. */
fun EditorViewModel.applyPhotoSharpen(clipId: String) =
    updatePhotoAdjustments(clipId) { it.copy(sharpness = maxOf(it.sharpness, 0.35f)) }

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

/** Enable a small-angle, single-image auto-level correction and matching safe crop. */
fun EditorViewModel.applyPhotoStabilize(clipId: String) =
    updatePhotoEdit(clipId) { it.copy(stabilizePhoto = true) }

fun EditorViewModel.setPhotoStabilize(clipId: String, enabled: Boolean) =
    updatePhotoEdit(clipId) { it.copy(stabilizePhoto = enabled) }

/** Clear every photo edit on the clip back to the untouched photo. */
fun EditorViewModel.resetPhotoEdits(clipId: String) =
    updatePhotoEdit(clipId) { PhotoEditSettings() }

/** Phase 4: toggles the Ken Burns pan/zoom motion on a photo clip. */
fun EditorViewModel.setPhotoKenBurns(clipId: String, enabled: Boolean) =
    updatePhotoEdit(clipId) { it.copy(kenBurns = enabled) }

/** Invalid preset ids are ignored rather than corrupting a saved project. */
fun EditorViewModel.setPhotoCanvas(clipId: String, canvas: com.apexstudio.app.domain.model.PhotoCanvasSettings) {
    if (canvas.aspectPreset !in com.apexstudio.app.domain.model.PhotoCanvasSettings.PRESETS) return
    if (_state.value.project?.clips?.none {
            it.id == clipId && it.type == com.apexstudio.app.domain.model.ClipType.IMAGE
        } != false) return
    updatePhotoEdit(clipId) { it.copy(canvas = canvas.copy(backgroundArgb = canvas.backgroundArgb or 0xFF000000L)) }
}
