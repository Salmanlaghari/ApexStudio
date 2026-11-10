package com.apexstudio.app.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportDiagnosticTraceTest {
    @Test
    fun `trace records elapsed stage and throttles progress by decile`() {
        var nowMs = 100L
        val events = mutableListOf<String>()
        val trace = ExportDiagnosticTrace(emit = events::add, clockMs = { nowMs })

        trace.stage("video_started", "resolution=1080p")
        trace.progress(10)
        nowMs += 25L
        trace.progress(19)
        trace.progress(97)
        trace.stage("post_mix_started", "musicTracks=1 overlaySources=0")

        assertEquals(4, events.size)
        assertTrue(events[0].contains("elapsedMs=0 stage=video_started"))
        assertTrue(events[1].contains("stage=video_progress percent=10"))
        assertTrue(events[2].contains("elapsedMs=25 stage=video_progress percent=90"))
        assertTrue(events[3].contains("elapsedMs=25 stage=post_mix_started"))
    }

    @Test
    fun `progress is clamped and duplicate buckets are omitted`() {
        val events = mutableListOf<String>()
        val trace = ExportDiagnosticTrace(emit = events::add, clockMs = { 0L })

        trace.progress(-20)
        trace.progress(0)
        trace.progress(100)
        trace.progress(101)
        repeat(100) {
            trace.progress(10)
            trace.progress(97)
        }

        assertEquals(2, events.size)
        assertTrue(events[0].contains("percent=0"))
        assertTrue(events[1].contains("percent=90"))
    }

    @Test
    fun `phase transitions delimit render audio mux and finalization`() {
        val events = mutableListOf<String>()
        val trace = ExportDiagnosticTrace(emit = events::add, clockMs = { 0L })

        trace.phase("video_render")
        trace.phase("audio_preparation")
        trace.phase("audio_mix")
        trace.phase("mux")
        trace.phase("finalization")
        trace.phase(null)

        assertEquals(
            listOf(
                "video_render_started", "video_render_completed", "audio_preparation_started",
                "audio_preparation_completed", "audio_mix_started", "audio_mix_completed",
                "mux_started", "mux_completed", "finalization_started", "finalization_completed"
            ),
            events.map { it.substringAfter("stage=").substringBefore(' ') }
        )
    }

    @Test
    fun `failed phase is recorded as failed rather than completed`() {
        val events = mutableListOf<String>()
        val trace = ExportDiagnosticTrace(emit = events::add, clockMs = { 0L })

        trace.phase("mux")
        trace.failPhase("failure=IOException")

        assertTrue(events[1].contains("stage=mux_failed failure=IOException"))
        assertEquals(2, events.size)
    }
}
