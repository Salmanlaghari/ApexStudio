package com.apexstudio.app.data.effect

import com.apexstudio.app.domain.model.VideoCropSettings

/** Ten original crop choices; ratio values are displayed output width / height. */
enum class VideoCropPreset(val id: String, val label: String, val aspectRatio: Float?) {
    FREE("free", "Free", null),
    SQUARE("1:1", "1:1", 1f),
    PORTRAIT_4_5("4:5", "4:5", 4f / 5f),
    PORTRAIT_3_4("3:4", "3:4", 3f / 4f),
    PORTRAIT_2_3("2:3", "2:3", 2f / 3f),
    PORTRAIT_9_16("9:16", "9:16", 9f / 16f),
    LANDSCAPE_5_4("5:4", "5:4", 5f / 4f),
    LANDSCAPE_4_3("4:3", "4:3", 4f / 3f),
    LANDSCAPE_3_2("3:2", "3:2", 3f / 2f),
    LANDSCAPE_16_9("16:9", "16:9", 16f / 9f);

    /** Largest centered normalized window with this output aspect. */
    fun centeredWindow(sourceAspect: Float): VideoCropSettings {
        val ratio = aspectRatio ?: return VideoCropSettings(presetId = id)
        val source = sourceAspect.takeIf { it.isFinite() && it > 0f } ?: ratio
        val (w, h) = if (ratio >= source) {
            1f to (source / ratio).coerceIn(0.01f, 1f)
        } else {
            (ratio / source).coerceIn(0.01f, 1f) to 1f
        }
        val left = (1f - w) / 2f
        val top = (1f - h) / 2f
        return VideoCropSettings(
            left = left,
            top = top,
            right = left + w,
            bottom = top + h,
            presetId = id
        )
    }

    companion object {
        val all: List<VideoCropPreset> = entries
        fun byId(id: String?): VideoCropPreset? = entries.firstOrNull { it.id == id }
    }
}
