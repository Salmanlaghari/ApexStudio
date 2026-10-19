package com.apexstudio.app.data.effect

import com.apexstudio.app.domain.model.TextMaskKeyframe
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.domain.model.VideoMask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TextOverlayFramePlannerTest {
    private fun timed(id: String, start: Long, end: Long, text: String = id) =
        TextOverlay(id = id, text = text, startMs = start, endMs = end)

    @Test
    fun sequentialKaraokeWindowsLeaveGapsWithoutAFrameSprite() {
        val wordOne = timed("one", 100L, 199L, "Hello").copy(
            highlightStartUtf16 = 0,
            highlightEndUtf16 = 5,
            highlightSourceFingerprint = "phrase-fingerprint"
        )
        val wordTwo = timed("two", 250L, 349L, "world").copy(
            highlightStartUtf16 = 6,
            highlightEndUtf16 = 11,
            highlightSourceFingerprint = "phrase-fingerprint"
        )
        val overlays = listOf(wordOne, wordTwo)

        assertEquals(listOf("one"), TextOverlayFramePlanner.plan(overlays, 150L).overlays.map { it.id })
        val gap = TextOverlayFramePlanner.plan(overlays, 220L)
        assertTrue(gap.overlays.isEmpty())
        assertNull(gap.cacheKey)
        assertEquals(listOf("two"), TextOverlayFramePlanner.plan(overlays, 300L).overlays.map { it.id })
        // Keep the per-word UTF-16 highlight metadata intact for TextSpriteRenderer.
        assertEquals(6, TextOverlayFramePlanner.plan(overlays, 300L).overlays.single().highlightStartUtf16)
    }

    @Test
    fun overlappingOrdinaryCaptionsKeepInputDrawOrder() {
        val back = timed("back", 0L, 1_000L)
        val front = timed("front", 100L, 900L)
        val laterInSource = timed("source-last", 250L, 750L)

        val plan = TextOverlayFramePlanner.plan(listOf(back, front, laterInSource), 500L)
        assertEquals(listOf("back", "front", "source-last"), plan.overlays.map { it.id })
        assertEquals(3, plan.cacheKey?.size)
    }

    @Test
    fun animationAndEvaluatedMaskChangesInvalidateTheBatchedSpriteKey() {
        val animated = timed("animated", 0L, 2_000L).copy(
            animationType = "FADE_IN",
            animationDurationMs = 1_000L,
            mask = VideoMask(centerX = 0.25f),
            maskKeyframes = listOf(
                TextMaskKeyframe(id = "mask-start", timeMs = 0L, centerX = 0.25f),
                TextMaskKeyframe(id = "mask-end", timeMs = 1_000L, centerX = 0.75f)
            )
        )

        val early = TextOverlayFramePlanner.plan(listOf(animated), 100L).cacheKey
        val late = TextOverlayFramePlanner.plan(listOf(animated), 800L).cacheKey
        assertNotEquals("intro/keyframe state must invalidate cached pixels", early, late)
        assertNotEquals(
            "evaluated mask center must invalidate cached pixels",
            TextOverlayFramePlanner.plan(listOf(animated), 0L).cacheKey,
            TextOverlayFramePlanner.plan(listOf(animated), 1_000L).cacheKey
        )
    }

    @Test
    fun plannerWorkingSetIsBoundedByActiveWindowsNotProjectOverlayCount() {
        val projectOverlays = (0 until 2_000).map { index ->
            timed("word-$index", index * 20L, index * 20L + 10L)
        }
        val plan = TextOverlayFramePlanner.plan(projectOverlays, 20_001L)

        assertEquals(listOf("word-1000"), plan.overlays.map { it.id })
        assertEquals(1, plan.cacheKey?.size)
        // The planner returns the original active model, not cloned overlays/bitmaps.
        assertSame(projectOverlays[1_000], plan.overlays.single())
    }

    @Test
    fun spriteDimensionsPreservePortraitAndLandscapeAspectWithCappedLongEdge() {
        val landscape = TextOverlaySpriteSize.forAspect(16f / 9f)
        val portrait = TextOverlaySpriteSize.forAspect(9f / 16f)

        assertEquals(1_600 to 900, landscape)
        assertEquals(900 to 1_600, portrait)
        assertTrue(maxOf(landscape.first, landscape.second) <= 1_600)
        assertTrue(maxOf(portrait.first, portrait.second) <= 1_600)
    }
}
