package com.apexstudio.app.domain.model

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ManualCropEditorTest {
    @Test fun imageDraftStartsFromTheActualPhotoRendererCropNotStaleVideoFields() {
        val image = MediaClip("photo", "Photo", "file:///photo.jpg", 5_000L,
            trimEndMs = 5_000L, type = ClipType.IMAGE,
            photoEdit = PhotoEditSettings(crop = PhotoCropRect(.2f, .1f, .9f, .8f), cropAspectPreset = "4:5"))
        assertEquals(VideoCropSettings(.2f, .1f, .9f, .8f, "4:5"), image.manualCropSettings())
        assertEquals(VideoCropSettings(), image.copy(photoEdit = PhotoEditSettings(), videoCrop = VideoCropSettings(.1f, .1f, .9f, .9f)).manualCropSettings())
    }

    @Test fun edgeEditsAnchorTheOppositeSideAndUnlockThePreset() {
        val crop = VideoCropSettings(.2f, .3f, .8f, .9f, "9:16")
        for (edge in ManualCropEdge.entries) {
            val changed = crop.moveEdge(edge, .5f)
            assertEquals(.5f, changed.edgePosition(edge), .00001f)
            assertEquals("free", changed.presetId)
            when (edge) {
                ManualCropEdge.LEFT -> assertEquals(crop.right, changed.right, 0f)
                ManualCropEdge.TOP -> assertEquals(crop.bottom, changed.bottom, 0f)
                ManualCropEdge.RIGHT -> assertEquals(crop.left, changed.left, 0f)
                ManualCropEdge.BOTTOM -> assertEquals(crop.top, changed.top, 0f)
            }
        }
    }
    @Test fun edgesCannotCrossAndAlwaysRetainMinimumWindow() {
        val original = VideoCropSettings(.2f, .3f, .8f, .9f)
        for (edge in ManualCropEdge.entries) for (position in listOf(-100f, 100f)) {
            val crop = original.moveEdge(edge, position)
            assertTrue(crop.left >= 0f && crop.top >= 0f && crop.right <= 1f && crop.bottom <= 1f)
            assertTrue(crop.right - crop.left >= .00999f)
            assertTrue(crop.bottom - crop.top >= .00999f)
        }
    }
    @Test fun nonFiniteInputNeverPoisonsDraft() {
        val original = VideoCropSettings(.2f, .3f, .8f, .9f)
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            for (edge in ManualCropEdge.entries) assertEquals(original, original.moveEdge(edge, bad))
            assertEquals(original, original.moveCenter(bad, .5f))
            assertEquals(original, original.moveCenter(.5f, bad))
        }
        assertEquals(VideoCropSettings(), VideoCropSettings(left = Float.NaN).moveCenter(Float.NaN, .5f))
    }
    @Test fun panningPreservesDimensionsAndAspectPresetAtEveryBoundary() {
        val crop = VideoCropSettings(.2f, .3f, .8f, .9f, "1:1")
        for (x in listOf(-10f, 0f, .5f, 1f, 10f)) for (y in listOf(-10f, 0f, .5f, 1f, 10f)) {
            val moved = crop.moveCenter(x, y)
            assertEquals(crop.width, moved.width, .00001f)
            assertEquals(crop.height, moved.height, .00001f)
            assertEquals(crop.presetId, moved.presetId)
            assertTrue(moved.left >= 0 && moved.top >= 0 && moved.right <= 1 && moved.bottom <= 1)
        }
    }
    @Test fun randomizedGesturesStayFiniteOrderedAndBounded() {
        val random = Random(512)
        var crop = VideoCropSettings()
        repeat(5_000) {
            crop = if (random.nextBoolean()) crop.moveEdge(ManualCropEdge.entries.random(random), random.nextFloat() * 4f - 2f)
                else crop.moveCenter(random.nextFloat() * 4f - 2f, random.nextFloat() * 4f - 2f)
            assertTrue(crop.left.isFinite() && crop.top.isFinite() && crop.right.isFinite() && crop.bottom.isFinite())
            assertTrue(crop.left >= 0f && crop.top >= 0f && crop.right <= 1.000001f && crop.bottom <= 1.000001f)
            assertTrue(crop.right - crop.left >= .00999f && crop.bottom - crop.top >= .00999f)
        }
    }
}
