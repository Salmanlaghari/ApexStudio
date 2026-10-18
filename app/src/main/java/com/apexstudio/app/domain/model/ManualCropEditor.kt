package com.apexstudio.app.domain.model

/** Older photo edits may exist only in photoEdit.crop, which is what preview/export render. */
fun MediaClip.manualCropSettings(): VideoCropSettings {
    val photoCrop = photoEdit.crop
    return if (type == ClipType.IMAGE) {
        if (photoCrop == null) VideoCropSettings() else VideoCropSettings(
            photoCrop.left, photoCrop.top, photoCrop.right, photoCrop.bottom,
            photoEdit.cropAspectPreset ?: "free"
        ).sanitized()
    } else videoCrop.sanitized()
}

/** Free-form source-space crop controls. Values are positions, not percentages to remove. */
enum class ManualCropEdge(val label: String) { LEFT("Left"), TOP("Top"), RIGHT("Right"), BOTTOM("Bottom") }

fun VideoCropSettings.edgePosition(edge: ManualCropEdge): Float = sanitized().let {
    when (edge) {
        ManualCropEdge.LEFT -> it.left
        ManualCropEdge.TOP -> it.top
        ManualCropEdge.RIGHT -> it.right
        ManualCropEdge.BOTTOM -> it.bottom
    }
}

/** Keep the opposite edge anchored, with at least one percent of the source retained. */
fun VideoCropSettings.moveEdge(edge: ManualCropEdge, position: Float): VideoCropSettings {
    val current = sanitized()
    if (!position.isFinite()) return current
    return when (edge) {
        ManualCropEdge.LEFT -> current.copy(left = position.coerceIn(0f, (current.right - .01f).coerceAtLeast(0f)))
        ManualCropEdge.TOP -> current.copy(top = position.coerceIn(0f, (current.bottom - .01f).coerceAtLeast(0f)))
        ManualCropEdge.RIGHT -> current.copy(right = position.coerceIn((current.left + .01f).coerceAtMost(1f), 1f))
        ManualCropEdge.BOTTOM -> current.copy(bottom = position.coerceIn((current.top + .01f).coerceAtMost(1f), 1f))
    }.copy(presetId = "free")
}

/** Pan a crop without changing its dimensions; values are normalized crop-center positions. */
fun VideoCropSettings.moveCenter(x: Float, y: Float): VideoCropSettings {
    val current = sanitized()
    if (!x.isFinite() || !y.isFinite()) return current
    val left = (x - current.width / 2f).coerceIn(0f, (1f - current.width).coerceAtLeast(0f))
    val top = (y - current.height / 2f).coerceIn(0f, (1f - current.height).coerceAtLeast(0f))
    return current.copy(left = left, top = top, right = left + current.width, bottom = top + current.height)
}
