package com.apexstudio.editor.thumbnail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ThumbnailSequenceGenerator] — the maths behind the
 * Bug 1 fix ("timeline shows thumbnails for only the first 8 seconds").
 *
 * Pure JVM: no Android, no decoding, no coroutines.
 */
class ThumbnailSequenceGeneratorTest {

    // === sampleCount: how many frames does a clip need? ===

    @Test
    fun `three minute clip gets one frame per second`() {
        // Regression: this used to be a hard-coded 8, so a 3:00 video
        // produced eight thumbnails and the video layer blanked after
        // the first few seconds.
        assertEquals(180, ThumbnailSequenceGenerator.sampleCount(180_000L))
    }

    @Test
    fun `one minute clip gets 60 frames`() {
        assertEquals(60, ThumbnailSequenceGenerator.sampleCount(60_000L))
    }

    @Test
    fun `short clip is floored at MIN_FRAMES`() {
        assertEquals(ThumbnailSequenceGenerator.MIN_FRAMES, ThumbnailSequenceGenerator.sampleCount(0L))
        assertEquals(ThumbnailSequenceGenerator.MIN_FRAMES, ThumbnailSequenceGenerator.sampleCount(500L))
        assertEquals(ThumbnailSequenceGenerator.MIN_FRAMES, ThumbnailSequenceGenerator.sampleCount(-1L))
    }

    @Test
    fun `partial seconds round up so the tail is covered`() {
        // 4_500 ms at 1 frame/sec must be 5, never a truncated 4 that
        // would leave the last 500 ms unrepresented.
        assertEquals(5, ThumbnailSequenceGenerator.sampleCount(4_500L))
        assertEquals(59, ThumbnailSequenceGenerator.sampleCount(58_001L))
    }

    @Test
    fun `very long clips are clamped to MAX_FRAMES`() {
        assertEquals(ThumbnailSequenceGenerator.MAX_FRAMES, ThumbnailSequenceGenerator.sampleCount(10_000_000L))
        assertTrue(ThumbnailSequenceGenerator.sampleCount(10_000_000L) <= ThumbnailSequenceGenerator.MAX_FRAMES)
        assertEquals(ThumbnailSequenceGenerator.MAX_FRAMES, ThumbnailSequenceGenerator.sampleCount(Long.MAX_VALUE))
    }

    @Test
    fun `custom interval is honoured`() {
        assertEquals(18, ThumbnailSequenceGenerator.sampleCount(18_000L, 1_000L))
        assertEquals(6, ThumbnailSequenceGenerator.sampleCount(18_000L, 3_000L))
    }

    // === timestamps: does the strip actually span the whole clip? ===

    @Test
    fun `strip starts at zero and ends exactly at the clip duration`() {
        val stamps = ThumbnailSequenceGenerator.timestamps(180_000L, 180)
        assertEquals(0L, stamps.first())
        assertEquals(180_000L, stamps.last())
    }

    @Test
    fun `eight frame strip spans the full eight seconds`() {
        // The reported symptom: only 8 SECONDS of thumbnail visible.
        // With duration-based spacing the 8 frames must reach 8_000 ms.
        val stamps = ThumbnailSequenceGenerator.timestamps(8_000L, 8)
        assertEquals(8, stamps.size)
        assertEquals(0L, stamps.first())
        assertEquals(8_000L, stamps.last())
    }

    @Test
    fun `timestamps are strictly increasing`() {
        val stamps = ThumbnailSequenceGenerator.timestamps(180_000L, 180)
        for (i in 1 until stamps.size) {
            assertTrue(
                "timestamps must strictly increase, but $i was ${stamps[i]} after ${stamps[i - 1]}",
                stamps[i] > stamps[i - 1]
            )
        }
    }

    @Test
    fun `single frame yields the origin`() {
        assertEquals(1, ThumbnailSequenceGenerator.timestamps(9_000L, 1).size)
        assertEquals(0L, ThumbnailSequenceGenerator.timestamps(9_000L, 1).first())
    }

    @Test
    fun `zero duration keeps every timestamp at the origin`() {
        // A 0 ms span still yields a caller-sized strip, but every
        // sample collapses onto the origin instead of inventing times
        // past the end of the clip.
        val stamps = ThumbnailSequenceGenerator.timestamps(0L, 40)
        assertEquals(40, stamps.size)
        assertTrue(stamps.all { it == 0L })
    }

    // === windowKeys: only decode what is on screen ===

    @Test
    fun `window selects only frames inside it`() {
        val durationMs = 60_000L
        val count = ThumbnailSequenceGenerator.sampleCount(durationMs) // 60
        val seen = ThumbnailSequenceGenerator.windowKeys(durationMs, 10_000L, 20_000L, count)
        assertTrue(seen.isNotEmpty())
        assertTrue(seen.all { it in 10_000L..20_000L })
    }

    @Test
    fun `window is strictly smaller than the full strip for a partial view`() {
        val durationMs = 300_000L
        val count = ThumbnailSequenceGenerator.sampleCount(durationMs)
        val all = ThumbnailSequenceGenerator.timestamps(durationMs, count)
        val window = ThumbnailSequenceGenerator.windowKeys(durationMs, 0L, 10_000L, count)
        assertTrue(window.size < all.size)
    }

    @Test
    fun `inverted or out of range window returns empty instead of throwing`() {
        assertEquals(0, ThumbnailSequenceGenerator.windowKeys(10_000L, 5_000L, 1_000L, 10).size)
        assertEquals(0, ThumbnailSequenceGenerator.windowKeys(10_000L, 90_000L, 99_000L, 10).size)
        assertEquals(0, ThumbnailSequenceGenerator.windowKeys(10_000L, -5_000L, -1_000L, 10).size)
    }

    @Test
    fun `full range window returns every frame`() {
        val durationMs = 60_000L
        val count = ThumbnailSequenceGenerator.sampleCount(durationMs)
        assertEquals(
            count,
            ThumbnailSequenceGenerator.windowKeys(durationMs, 0L, durationMs, count).size
        )
    }

    // === frameIndexAt: locating the playhead in a strip ===

    @Test
    fun `frame index is clamped to the strip bounds`() {
        assertEquals(0, ThumbnailSequenceGenerator.frameIndexAt(10_000L, -5_000L, 10))
        assertEquals(9, ThumbnailSequenceGenerator.frameIndexAt(10_000L, 999_999L, 10))
    }

    @Test
    fun `frame index tracks time proportionally`() {
        assertEquals(0, ThumbnailSequenceGenerator.frameIndexAt(10_000L, 0L, 11))
        assertEquals(5, ThumbnailSequenceGenerator.frameIndexAt(10_000L, 5_000L, 11))
        assertEquals(10, ThumbnailSequenceGenerator.frameIndexAt(10_000L, 10_000L, 11))
    }

    @Test
    fun `frame index is safe on a zero duration`() {
        assertEquals(0, ThumbnailSequenceGenerator.frameIndexAt(0L, 0L, 10))
    }

    // === sequence: thumbnail model placeholders ===

    @Test
    fun `sequence carries one placeholder per sampled frame`() {
        val durationMs = 180_000L
        val expected = ThumbnailSequenceGenerator.sampleCount(durationMs)
        val seq = ThumbnailSequenceGenerator.sequence("file:///clip.mp4", durationMs)
        assertEquals("file:///clip.mp4", seq.videoUri)
        assertEquals(durationMs, seq.durationMs)
        assertEquals(expected, seq.thumbnails.size)
        assertEquals(durationMs, seq.thumbnails.last().timestampMs)
        assertEquals(0L, seq.thumbnails.first().timestampMs)
    }

    @Test
    fun `sequence ids are unique and ordered`() {
        val seq = ThumbnailSequenceGenerator.sequence("file:///a.mp4", 30_000L, frameCount = 30)
        val ids = seq.thumbnails.map { it.id }
        assertEquals(30, ids.toSet().size)
        assertEquals(ids.map { it.toInt() }, ids.map { it.toInt() }.sorted())
    }
}
