package com.apexstudio.app.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverlayMediaProbeTest {
    @Test
    fun `picker display name resolves known image and video types`() {
        assertEquals(false, OverlayMediaProbe.resolveVideoAnswer(null, null, "82915.jpg"))
        assertEquals(false, OverlayMediaProbe.resolveVideoAnswer(null, null, "Cover.HEIC"))
        assertEquals(true, OverlayMediaProbe.resolveVideoAnswer(null, null, "portrait.MP4"))
        assertEquals(true, OverlayMediaProbe.resolveVideoAnswer(null, null, "clip.webm"))
    }

    @Test
    fun `retriever and MIME evidence outrank a contradictory display name`() {
        assertEquals(true, OverlayMediaProbe.resolveVideoAnswer(true, "image/jpeg", "still.jpg"))
        assertEquals(false, OverlayMediaProbe.resolveVideoAnswer(false, "video/mp4", "movie.mp4"))
        assertEquals(true, OverlayMediaProbe.resolveVideoAnswer(null, "video/mp4", "still.jpg"))
        assertEquals(false, OverlayMediaProbe.resolveVideoAnswer(null, "image/jpeg", "movie.mp4"))
    }

    @Test
    fun `opaque provider names remain unresolved without media evidence`() {
        assertNull(OverlayMediaProbe.resolveVideoAnswer(null, null, "document-4f92"))
        assertNull(OverlayMediaProbe.resolveVideoAnswer(null, null, null))
    }
}
