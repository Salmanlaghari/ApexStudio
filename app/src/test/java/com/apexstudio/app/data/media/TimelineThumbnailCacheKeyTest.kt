package com.apexstudio.app.data.media

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.PhotoEditSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TimelineThumbnailCacheKeyTest {
    private fun image() = MediaClip(
        id = "image-1",
        name = "still.jpg",
        uri = "content://media/still.jpg",
        durationMs = 5_000L,
        trimEndMs = 5_000L,
        type = ClipType.IMAGE,
    )

    @Test
    fun `photo edit changes invalidate still thumbnail at same source frame`() {
        val original = image()
        val originalKey = TimelineThumbnailCacheKey.from(original, 0L, 160, 90)
        val editedKey = TimelineThumbnailCacheKey.from(
            original.copy(photoEdit = PhotoEditSettings(rotationSteps = 1)), 0L, 160, 90,
        )
        assertNotEquals(originalKey, editedKey)
    }

    @Test
    fun `photo edit changes invalidate image-backed overlay thumbnail`() {
        val overlay = image().copy(type = ClipType.OVERLAY)
        val originalKey = TimelineThumbnailCacheKey.from(overlay, 0L, 160, 90)
        val editedKey = TimelineThumbnailCacheKey.from(
            overlay.copy(photoEdit = PhotoEditSettings(rotationSteps = 1)), 0L, 160, 90,
        )
        assertNotEquals(originalKey, editedKey)
    }

    @Test
    fun `trim source time dimensions and media kind participate in frame identity`() {
        val original = image()
        val key = TimelineThumbnailCacheKey.from(original, 500L, 160, 90)
        assertEquals(500L, key.sourceTimeMs)
        assertNotEquals(key, TimelineThumbnailCacheKey.from(original.copy(trimStartMs = 100L), 500L, 160, 90))
        assertNotEquals(key, TimelineThumbnailCacheKey.from(original, 500L, 320, 90))
        assertNotEquals(key, TimelineThumbnailCacheKey.from(original.copy(type = ClipType.VIDEO), 500L, 160, 90))
    }

    @Test
    fun `layer movement does not invalidate unchanged source frame`() {
        val original = image()
        assertEquals(
            TimelineThumbnailCacheKey.from(original, 1_000L, 160, 90),
            TimelineThumbnailCacheKey.from(original.copy(trackIndex = 4, timelineOffsetMs = 2_000L), 1_000L, 160, 90),
        )
    }

    @Test
    fun `source timestamp arithmetic saturates rather than wrapping`() {
        val clip = image().copy(trimStartMs = Long.MAX_VALUE - 10)
        assertEquals(Long.MAX_VALUE, TimelineThumbnailCacheKey.from(clip, 20L, 160, 90).sourceTimeMs)
    }
}
