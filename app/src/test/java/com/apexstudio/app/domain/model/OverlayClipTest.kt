package com.apexstudio.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bug 3 tests for [OverlayClip] — the overlay's edit surface (layout,
 * trim, own track).
 *
 * The load-bearing property here is losslessness: an overlay carries
 * many more settings than layout (mask, blend, facility-free keyframes,
 * photo edits), so every write through this model must touch only the
 * fields it claims. A silent overwrite in `writeTo` would eat a user's
 * mask without ever mentioning it.
 */
class OverlayClipTest {

    private fun overlay(clipId: String = "ov1") = MediaClip(
        id = clipId,
        name = "pip.mp4",
        uri = "content://media/pip",
        durationMs = 30_000L,
        trimStartMs = 2_000L,
        trimEndMs = 10_000L,
        type = ClipType.OVERLAY,
        trackIndex = 3,
        pipX = 0.4f,
        pipY = 0.6f,
        pipScale = 0.35f,
        pipRotationDeg = 12f,
        pipOpacity = 0.8f,
        mask = VideoMask(shape = MaskShape.HEART, feather = 0.4f),
        overlayBlend = OverlayBlend.SCREEN,
        textOverlays = listOf(
            TextOverlay(id = "t1", text = "", startMs = 0L, endMs = 0L)
        )
    )

    @Test
    fun `from mirrors every editable field`() {
        val view = OverlayClip.from(overlay())
        assertEquals("ov1", view.id)
        assertEquals(30_000L, view.sourceDurationMs)
        assertEquals(2_000L, view.startMs)
        assertEquals(10_000L, view.endMs)
        assertEquals(0.4f, view.x, 0.0001f)
        assertEquals(0.6f, view.y, 0.0001f)
        assertEquals(0.35f, view.scale, 0.0001f)
        assertEquals(12f, view.rotationDeg, 0.0001f)
        assertEquals(0.8f, view.opacity, 0.0001f)
        assertEquals(3, view.layer)
    }

    @Test
    fun `from clamps values from older projects instead of failing to load`() {
        val jagged = overlay().copy(
            trimStartMs = -5_000L,   // below source zero
            trimEndMs = 999_999L,    // past the end of the media
            pipScale = 3.5f,         // pre-normalisation bug
            pipX = -2f,
            pipOpacity = 4f
        )
        val view = OverlayClip.from(jagged)
        assertEquals(0L, view.startMs)
        assertEquals(30_000L, view.endMs)
        assertEquals(OverlayClip.MAX_SCALE, view.scale, 0.0001f)
        assertEquals(0f, view.x, 0.0001f)
        assertEquals(1f, view.opacity, 0.0001f)
        assertTrue("window must never invert", view.endMs > view.startMs)
    }

    @Test
    fun `writeTo touches only the fields OverlayClip owns`() {
        val clip = overlay()
        val edited = OverlayClip.from(clip).copy(x = 0.9f, startMs = 5_000L)
        val written = edited.writeTo(clip)

        // Owned fields moved:
        assertEquals(0.9f, written.pipX, 0.0001f)
        assertEquals(5_000L, written.trimStartMs)

        // Everything else must be byte-identical:
        assertEquals(clip.mask, written.mask)
        assertEquals(clip.overlayBlend, written.overlayBlend)
        assertEquals(clip.keyframes, written.keyframes)
        assertEquals(clip.photoEdit, written.photoEdit)
        assertEquals(clip.textOverlays, written.textOverlays)
        assertEquals(clip.volume, written.volume, 0.0001f)
        assertEquals(clip.id, written.id)
        assertEquals(clip.name, written.name)
        assertEquals(clip.uri, written.uri)
        assertEquals(clip.durationMs, written.durationMs)
        assertEquals(3, written.trackIndex)
    }

    @Test
    fun `writeTo refuses to write onto a different clip`() {
        val other = overlay("other")
        try {
            OverlayClip.from(overlay()).copy(x = 0.1f).writeTo(other)
            throw AssertionError("expected a mismatched target to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("ov1"))
        }
    }

    @Test
    fun `start handle can never empty the overlay`() {
        val view = OverlayClip.from(overlay())
        val tooFar = view.withStart(25_000L)
        assertTrue("start must stay strictly below end", tooFar.startMs < tooFar.endMs)
        val negative = view.withStart(-9_999L)
        assertEquals(0L, negative.startMs)
        assertTrue(negative.startMs < negative.endMs)
    }

    @Test
    fun `end handle stops at the source and one millisecond after start`() {
        val view = OverlayClip.from(overlay())
        val pastEnd = view.withEnd(90_000_000L)
        assertEquals(30_000L, pastEnd.endMs)
        val tooSoon = view.withEnd(0L)
        assertEquals(view.startMs + 1L, tooSoon.endMs)
        assertTrue(tooSoon.endMs > tooSoon.startMs)
    }

    @Test
    fun `trimmed survives a reversed drag and stays inside the source`() {
        val view = OverlayClip.from(overlay())
        val reversed = view.trimmed(15_000L, 4_000L)
        assertTrue(reversed.startMs < reversed.endMs)
        val outside = view.trimmed(-100L, 40_000L)
        assertEquals(0L, outside.startMs)
        assertEquals(30_000L, outside.endMs)
        assertEquals(30_000L, outside.durationMs)
    }

    @Test
    fun `trim handles at source end never create an out of source frame`() {
        val view = OverlayClip.from(overlay())
        val atEnd = view.trimmed(30_000L, 30_000L)
        assertEquals(29_999L, atEnd.startMs)
        assertEquals(30_000L, atEnd.endMs)
        assertEquals(1L, atEnd.durationMs)

        val emptySource = view.copy(sourceDurationMs = 0L).trimmed(0L, 0L)
        assertEquals(0L, emptySource.startMs)
        assertEquals(0L, emptySource.endMs)
        assertEquals(0L, emptySource.durationMs)
        assertEquals(emptySource, emptySource.withEnd(10_000L))
    }

    @Test
    fun `duration tracks the trimmed window`() {
        val view = OverlayClip.from(overlay())
        assertEquals(8_000L, view.durationMs)
        assertEquals(0L, view.copy(startMs = 5_000L, endMs = 5_000L).durationMs)
    }

    @Test
    fun `lane rules keep overlays off the main track`() {
        val own = OverlayClip.from(overlay())
        assertTrue(own.isOnOwnTrack)
        val mainLane = own.copy(layer = 0)
        assertFalse("lane 0 is the video lane, overlays need their own", mainLane.isOnOwnTrack)
        val valid = own.copy(layer = 9)
        assertTrue(valid.isOnOwnTrack)
    }

    @Test
    fun `preferredLayer prefers a free lane`() {
        assertEquals(1, OverlayClip.preferredLayer(emptyList()))
        assertEquals(2, OverlayClip.preferredLayer(listOf(1)))
        // Lane 3 is the first free lane; expecting 4 skipped an available lane.
        assertEquals(3, OverlayClip.preferredLayer(listOf(1, 2, 5)))
        // All taken: fall back to the first legal lane rather than 0.
        val all = (1..9).toList()
        assertEquals(1, OverlayClip.preferredLayer(all))
    }

    @Test
    fun `identical overlay has zero drift`() {
        val a = OverlayClip.from(overlay())
        assertEquals(0f, a.transformDrift(OverlayClip.from(overlay())), 0.0001f)
        assertTrue(a.sameGeometryAs(OverlayClip.from(overlay())))
    }

    @Test
    fun `round trip is stable`() {
        val clip = overlay()
        val once = OverlayClip.from(clip).writeTo(clip)
        val twice = OverlayClip.from(once).writeTo(once)
        assertEquals(once, twice)
        assertEquals(OverlayClip.from(clip), OverlayClip.from(twice))
    }
}
