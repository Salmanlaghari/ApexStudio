package com.apexstudio.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for domain models.
 *
 * These are pure Kotlin data classes — no Android dependencies needed.
 */
class ModelsTest {

    @Test
    fun `MediaClip has sensible defaults`() {
        val clip = MediaClip(
            id = "clip1",
            name = "Test Clip",
            uri = "file:///test.mp4",
            durationMs = 10000L,
            trimEndMs = 10000L
        )

        assertEquals("clip1", clip.id)
        assertEquals(0L, clip.trimStartMs)
        assertEquals(0, clip.trackIndex)
        assertEquals(ClipType.VIDEO, clip.type)
        assertEquals(0L, clip.timelineOffsetMs)
        assertEquals(1f, clip.speedMultiplier, 0.001f)
    }

    @Test
    fun `MediaClip trimmed duration is correct`() {
        val clip = MediaClip(
            id = "clip1",
            name = "Test",
            uri = "file:///test.mp4",
            durationMs = 10000L,
            trimStartMs = 2000L,
            trimEndMs = 8000L
        )

        val trimmedDuration = clip.trimEndMs - clip.trimStartMs
        assertEquals(6000L, trimmedDuration)
    }

    @Test
    fun `ChromaKeySettings has sensible defaults`() {
        val settings = ChromaKeySettings()

        assertFalse(settings.enabled)
        assertEquals(0.40f, settings.similarity, 0.001f)
        assertEquals(0.15f, settings.smoothness, 0.001f)
        assertEquals(0.50f, settings.spillSuppression, 0.001f)
    }

    @Test
    fun `ChromaKeySettings copy works`() {
        val settings = ChromaKeySettings()
        val enabled = settings.copy(enabled = true)

        assertFalse(settings.enabled)
        assertTrue(enabled.enabled)
        // Other fields preserved
        assertEquals(settings.similarity, enabled.similarity, 0.001f)
    }

    @Test
    fun `ClipTransition defaults are valid`() {
        val transition = ClipTransition(
            fromClipId = "clip1",
            toClipId = "clip2"
        )

        assertEquals("clip1", transition.fromClipId)
        assertEquals("clip2", transition.toClipId)
        assertEquals("cross_dissolve", transition.type)
        assertEquals(500L, transition.durationMs)
        assertTrue("ID should be auto-generated", transition.id.isNotBlank())
    }
}
