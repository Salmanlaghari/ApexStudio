package com.apexstudio.app.domain.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the photo-editing domain model (PR F).
 *
 * Pure Kotlin data classes — no Android dependencies needed.
 */
class PhotoEditModelTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `PhotoEditSettings defaults to untouched photo`() {
        val settings = PhotoEditSettings()
        assertTrue(settings.isDefault)
        assertNull(settings.crop)
        assertNull(settings.filterId)
        assertEquals(0, settings.normalizedRotationSteps)
        assertFalse(settings.flipHorizontal)
        assertFalse(settings.flipVertical)
    }

    @Test
    fun `rotation steps normalise to 0-3`() {
        assertEquals(1, PhotoEditSettings(rotationSteps = 5).normalizedRotationSteps)
        assertEquals(3, PhotoEditSettings(rotationSteps = -1).normalizedRotationSteps)
        assertEquals(0, PhotoEditSettings(rotationSteps = 8).normalizedRotationSteps)
    }

    @Test
    fun `any edit marks settings non-default`() {
        assertFalse(PhotoEditSettings(filterId = "noir").isDefault)
        assertFalse(PhotoEditSettings(rotationSteps = 1).isDefault)
        assertFalse(PhotoEditSettings(flipHorizontal = true).isDefault)
        assertFalse(
            PhotoEditSettings(crop = PhotoCropRect(0.1f, 0.1f, 0.9f, 0.9f)).isDefault
        )
        assertFalse(
            PhotoEditSettings(adjustments = VideoAdjustments(brightness = 0.2f)).isDefault
        )
        // A full-frame crop rect counts as untouched.
        assertTrue(PhotoEditSettings(crop = PhotoCropRect.full()).isDefault)
    }

    @Test
    fun `aspect presets resolve correctly`() {
        assertEquals(1f, PhotoEditSettings.aspectRatioForPreset("1:1")!!, 0.001f)
        assertEquals(16f / 9f, PhotoEditSettings.aspectRatioForPreset("16:9")!!, 0.001f)
        assertEquals(9f / 16f, PhotoEditSettings.aspectRatioForPreset("9:16")!!, 0.001f)
        assertEquals(0.8f, PhotoEditSettings.aspectRatioForPreset("4:5")!!, 0.001f)
        assertEquals(1.5f, PhotoEditSettings.aspectRatioForPreset("3:2")!!, 0.001f)
        assertNull(PhotoEditSettings.aspectRatioForPreset("free"))
        assertNull(PhotoEditSettings.aspectRatioForPreset(null))
        assertNull(PhotoEditSettings.aspectRatioForPreset("bogus"))
    }

    @Test
    fun `centeredForAspect fits inside unit square`() {
        val square = PhotoCropRect.centeredForAspect(1f)
        assertEquals(0f, square.left, 0.001f)
        assertEquals(0f, square.top, 0.001f)
        assertEquals(1f, square.right, 0.001f)
        assertEquals(1f, square.bottom, 0.001f)

        val wide = PhotoCropRect.centeredForAspect(16f / 9f)
        assertEquals(0f, wide.left, 0.001f)
        assertEquals(1f, wide.right, 0.001f)
        assertEquals(16f / 9f, wide.aspect(), 0.01f)
        assertTrue(wide.top > 0f)
        // Vertically centred.
        assertEquals(wide.top, 1f - wide.bottom, 0.001f)

        val tall = PhotoCropRect.centeredForAspect(9f / 16f)
        assertEquals(0f, tall.top, 0.001f)
        assertEquals(1f, tall.bottom, 0.001f)
        assertEquals(9f / 16f, tall.aspect(), 0.01f)
        assertEquals(tall.left, 1f - tall.right, 0.001f)
    }

    @Test
    fun `MediaClip photoEdit survives JSON round-trip`() {
        val clip = MediaClip(
            id = "photo1",
            name = "sunset.jpg",
            uri = "content://photo",
            durationMs = 3000L,
            trimEndMs = 3000L,
            type = ClipType.IMAGE,
            photoEdit = PhotoEditSettings(
                crop = PhotoCropRect(0.1f, 0.2f, 0.9f, 0.8f),
                cropAspectPreset = "4:5",
                adjustments = VideoAdjustments(brightness = 0.2f, saturation = 1.3f, sharpness = 0.5f),
                filterId = "noir",
                filterIntensity = 0.75f,
                rotationSteps = 1,
                flipHorizontal = true
            )
        )
        val encoded = json.encodeToString(clip)
        val decoded: MediaClip = json.decodeFromString(encoded)
        assertEquals(ClipType.IMAGE, decoded.type)
        assertEquals(clip.photoEdit, decoded.photoEdit)
        assertFalse(decoded.photoEdit.isDefault)
    }

    @Test
    fun `MediaClip without photoEdit defaults (backwards compatibility)`() {
        val decoded: MediaClip = json.decodeFromString(
            """{"id":"c1","name":"v.mp4","uri":"file:///v.mp4","durationMs":5000,"trimEndMs":5000}"""
        )
        assertEquals(ClipType.VIDEO, decoded.type)
        assertTrue(decoded.photoEdit.isDefault)
    }
}
