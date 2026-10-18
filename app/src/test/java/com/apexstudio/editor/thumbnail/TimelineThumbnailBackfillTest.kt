package com.apexstudio.editor.thumbnail

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineThumbnailBackfillTest {
    private fun source() = MediaClip(
        id = "overlay", name = "overlay", uri = "file:///video.mp4",
        durationMs = 31_000L, trimEndMs = 31_000L, type = ClipType.OVERLAY, trackIndex = 1
    )

    @Test
    fun `canvas transforms and layer movement do not cancel source extraction`() {
        val original = source()
        val moved = original.copy(pipX = 0.2f, pipScale = 0.4f,
            pipOpacity = 0.5f, timelineOffsetMs = 5_000L, trackIndex = 2)
        assertEquals(TimelineThumbnailSourceKey.from(original), TimelineThumbnailSourceKey.from(moved))
    }

    @Test
    fun `replace trim and speed edits invalidate source frames`() {
        val original = source()
        val key = TimelineThumbnailSourceKey.from(original)
        assertNotEquals(key, TimelineThumbnailSourceKey.from(original.copy(uri = "file:///new.mp4")))
        assertNotEquals(key, TimelineThumbnailSourceKey.from(original.copy(trimStartMs = 1_000L)))
        assertNotEquals(key, TimelineThumbnailSourceKey.from(original.copy(speedMultiplier = 2f)))
    }

    @Test
    fun `multi-pass batches cover the full 31-second clip at close zoom`() {
        assertFullClipCoverage(31_000L)
    }

    @Test
    fun `multi-pass batches reach the tail of a three-minute clip`() {
        assertFullClipCoverage(180_000L)
    }

    private fun assertFullClipCoverage(durationMs: Long) {
        val intervalMs = 296L // approximately 4x zoom: one cell per 64dp
        val maxKeysPerPass = 24
        val extractedTimes = mutableListOf<Long>()
        var cursorMs = 0L

        while (cursorMs <= durationMs) {
            val batch = TimelineThumbnailBackfill.batch(
                cursorMs = cursorMs,
                durationMs = durationMs,
                intervalMs = intervalMs,
                maxKeys = maxKeysPerPass
            )
            assertTrue("non-terminal pass must make progress", batch.isNotEmpty())
            assertTrue("batch must respect the extraction-job cap", batch.size <= maxKeysPerPass)
            extractedTimes += batch.toList()
            cursorMs = batch.last() + intervalMs
        }

        val expectedCount = (durationMs / intervalMs + 1L).toInt()
        assertEquals(expectedCount, extractedTimes.size)
        assertEquals(0L, extractedTimes.first())
        assertTrue("tail frames after the initial ~8-second budget must be included", extractedTimes.any { it >= 8_000L })
        assertTrue("last planned key must reach the clip tail", extractedTimes.last() >= durationMs - intervalMs)
        assertEquals(extractedTimes.size, extractedTimes.toSet().size)
    }

    @Test
    fun `no extraction capacity leaves cursor work available for next pass`() {
        val cursorMs = 296L * 27L
        assertTrue(
            TimelineThumbnailBackfill.batch(
                cursorMs = cursorMs,
                durationMs = 31_000L,
                intervalMs = 296L,
                maxKeys = 0
            ).isEmpty()
        )
        assertEquals(
            cursorMs,
            TimelineThumbnailBackfill.batch(
                cursorMs = cursorMs,
                durationMs = 31_000L,
                intervalMs = 296L,
                maxKeys = 1
            ).first()
        )
    }

    @Test
    fun `batch is bounded at the final partial interval`() {
        assertEquals(
            listOf(2_000L, 2_500L, 3_000L),
            TimelineThumbnailBackfill.batch(
                cursorMs = 2_000L,
                durationMs = 3_100L,
                intervalMs = 500L,
                maxKeys = 24
            ).toList()
        )
    }
}
