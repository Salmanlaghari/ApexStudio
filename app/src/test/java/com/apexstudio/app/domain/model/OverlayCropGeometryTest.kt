package com.apexstudio.app.domain.model

import com.apexstudio.app.data.effect.VideoCropPreset
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class OverlayCropGeometryTest {
    @Test fun `all crop presets preserve actual overlay output aspect`() {
        for (sourceAspect in listOf(16f / 9f, 9f / 16f, 4f / 3f, 1f)) {
            for (preset in VideoCropPreset.all) {
                val crop = preset.centeredWindow(sourceAspect)
                assertEquals(preset.aspectRatio ?: sourceAspect,
                    croppedOverlayAspect(sourceAspect, crop), .0001f)
            }
        }
    }

    @Test fun `crop geometry does not mutate intrinsic aspect and reset restores it`() {
        val original = MediaClip("overlay", "Photo", "file:///photo.jpg", 5000L,
            trimEndMs = 5000L, type = ClipType.OVERLAY, overlayAspect = 16f / 9f)
        val cropped = original.copy(videoCrop = VideoCropPreset.SQUARE.centeredWindow(original.overlayAspect))
        assertEquals(1f, croppedOverlayAspect(cropped.overlayAspect, cropped.videoCrop), .0001f)
        assertEquals(original.overlayAspect, cropped.overlayAspect, 0f)
        val restored = Json.decodeFromString<MediaClip>(Json.encodeToString(cropped))
        assertEquals(1f, croppedOverlayAspect(restored.overlayAspect, restored.videoCrop), .0001f)
        val reset = restored.copy(videoCrop = VideoCropSettings())
        assertEquals(original.overlayAspect, croppedOverlayAspect(reset.overlayAspect, reset.videoCrop), .0001f)
    }

    @Test fun `custom off center crop changes aspect using width and height not position`() {
        assertEquals(2f, croppedOverlayAspect(2f, VideoCropSettings(.25f, .1f, .75f, .6f)), .0001f)
        assertEquals(1f, croppedOverlayAspect(2f, VideoCropSettings(.25f, 0f, .75f, 1f)), .0001f)
    }

    @Test fun `invalid source aspect falls back safely`() {
        for (aspect in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0f, -1f)) {
            assertEquals(16f / 9f, croppedOverlayAspect(aspect, VideoCropSettings()), .0001f)
        }
    }

    @Test fun `non finite crop coordinates restore full frame instead of corrupting rendering`() {
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            val crops = listOf(VideoCropSettings(left = invalid), VideoCropSettings(top = invalid),
                VideoCropSettings(right = invalid), VideoCropSettings(bottom = invalid))
            crops.forEach {
                assertEquals(VideoCropSettings(), it.sanitized())
                assertEquals(2f, croppedOverlayAspect(2f, it), .0001f)
            }
        }
    }
}
