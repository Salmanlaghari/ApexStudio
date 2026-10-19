package com.apexstudio.app.domain.model

/**
 * Display aspect after source crop. Keep [MediaClip.overlayAspect] intrinsic: storing the cropped
 * aspect back into it would compound every subsequent crop and make Reset unable to restore it.
 * Both preview bounds/hit tests and the export shader use this calculation.
 */
fun croppedOverlayAspect(intrinsicAspect: Float, settings: VideoCropSettings): Float {
    val sourceAspect = intrinsicAspect.takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)
    val crop = settings.sanitized()
    val result = sourceAspect.toDouble() * crop.width / crop.height
    return result.toFloat().takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)
}
