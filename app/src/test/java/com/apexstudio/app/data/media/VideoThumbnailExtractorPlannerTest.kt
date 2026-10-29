package com.apexstudio.app.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoThumbnailExtractorPlannerTest {
    @Test
    fun `short clip keeps evenly spaced samples across its duration`() {
        val times = VideoThumbnailExtractor.thumbnailSampleTimes(5_000L, 8)

        assertEquals(8, times.size)
        assertEquals(555L, times.first().toLong())
        assertEquals(4_440L, times.last().toLong())
        assertTrue(times.zipWithNext().all { (a, b) -> b > a })
        assertTrue(times.first() > 0L)
        assertTrue(times.last() < 5_000L)
    }

    @Test
    fun `long clip is bounded while sampling the full duration`() {
        val times = VideoThumbnailExtractor.thumbnailSampleTimes(180_000L, 300)

        assertEquals(VideoThumbnailExtractor.MAX_EAGER_THUMBS_PER_CLIP, times.size)
        assertEquals(7_200L, times.first().toLong())
        assertEquals(172_800L, times.last().toLong())
        assertEquals(7_200L, times[1] - times[0])
        assertTrue(times.last() < 180_000L)
    }

    @Test
    fun `zero duration and zero requested frames remain safe`() {
        assertEquals(emptyList<Long>(), VideoThumbnailExtractor.thumbnailSampleTimes(0L, 24))
        assertEquals(emptyList<Long>(), VideoThumbnailExtractor.thumbnailSampleTimes(10_000L, 0))
    }
}
