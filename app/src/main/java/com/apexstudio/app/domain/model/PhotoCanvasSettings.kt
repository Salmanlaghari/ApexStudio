package com.apexstudio.app.domain.model

import kotlinx.serialization.Serializable

/** Non-destructive photo canvas. Original leaves existing projects unchanged. */
@Serializable
data class PhotoCanvasSettings(
    val aspectPreset: String = "original",
    val backgroundArgb: Long = 0xFF000000L,
    val fill: Boolean = false
) {
    val aspectRatio: Float?
        get() = PhotoEditSettings.aspectRatioForPreset(aspectPreset)
    val isOriginal: Boolean get() = aspectRatio == null

    companion object {
        val PRESETS = listOf("original", "1:1", "4:5", "16:9", "9:16", "3:2")
        val BACKGROUNDS = listOf(
            "Black" to 0xFF000000L,
            "White" to 0xFFFFFFFFL,
            "Slate" to 0xFF1E293BL,
            "Cream" to 0xFFFFF1D6L,
            "Blue" to 0xFF172554L
        )
    }
}
