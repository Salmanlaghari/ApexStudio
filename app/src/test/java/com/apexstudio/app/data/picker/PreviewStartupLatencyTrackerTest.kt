package com.apexstudio.app.data.picker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewStartupLatencyTrackerTest {
    @Test
    fun `measures picker result to first frame of selected clip once`() {
        val tracker = PreviewStartupLatencyTracker()
        tracker.begin("project-1", "clip-1", selectedAtElapsedRealtimeMs = 120L)

        assertNull(tracker.recordFirstFrame("project-1", "other-clip", renderedAtElapsedRealtimeMs = 175L))
        assertNull(tracker.recordFirstFrame("other-project", "clip-1", renderedAtElapsedRealtimeMs = 175L))
        assertEquals(55L, tracker.recordFirstFrame("project-1", "clip-1", renderedAtElapsedRealtimeMs = 175L))
        assertNull(tracker.recordFirstFrame("project-1", "clip-1", renderedAtElapsedRealtimeMs = 220L))
    }

    @Test
    fun `clearing an abandoned project discards its pending sample`() {
        val tracker = PreviewStartupLatencyTracker()
        tracker.begin("project-1", "clip-1", selectedAtElapsedRealtimeMs = 120L)

        tracker.clear("project-1")

        assertNull(tracker.recordFirstFrame("project-1", "clip-1", renderedAtElapsedRealtimeMs = 175L))
    }

    @Test
    fun `rejects invalid or backward monotonic timestamps`() {
        val tracker = PreviewStartupLatencyTracker()
        tracker.begin("", "clip-1", selectedAtElapsedRealtimeMs = 120L)
        tracker.begin("project-1", "", selectedAtElapsedRealtimeMs = 120L)
        tracker.begin("project-1", "clip-1", selectedAtElapsedRealtimeMs = 120L)

        assertNull(tracker.recordFirstFrame("project-1", "clip-1", renderedAtElapsedRealtimeMs = 119L))
        assertNull(tracker.recordFirstFrame("project-1", "clip-1", renderedAtElapsedRealtimeMs = 130L))
    }
}
