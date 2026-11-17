package com.apexstudio.app.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportProgressStageTest {
    @Test fun `video render fraction maps once into the reserved range`() {
        assertEquals(0.01f, videoRenderProgress(0f), 0.000001f)
        assertEquals(0.455f, videoRenderProgress(0.5f), 0.000001f)
        assertEquals(0.90f, videoRenderProgress(1f), 0.000001f)
    }

    @Test fun `audio-only render can reach the prior 99 percent ceiling`() {
        assertEquals(0f, audioExportProgress(0f), 0f)
        assertEquals(0.5f, audioExportProgress(0.5f), 0f)
        assertEquals(0.99f, audioExportProgress(1f), 0f)
        assertEquals(0.99f, audioExportProgress(1.5f), 0f)
        assertEquals(0f, audioExportProgress(Float.NaN), 0f)
    }

    @Test fun `export stage sequences remain forward for both video and post-mix paths`() {
        assertForward(
            ExportProgressStage.VIDEO_RENDER,
            ExportProgressStage.FRAME_RATE_VERIFICATION,
            ExportProgressStage.FINALIZATION,
            ExportProgressStage.COMPLETE
        )
        assertForward(
            ExportProgressStage.VIDEO_RENDER,
            ExportProgressStage.VIDEO_OUTPUT_VERIFICATION,
            ExportProgressStage.AUDIO_PREPARATION,
            ExportProgressStage.AUDIO_MIX,
            ExportProgressStage.MUX,
            ExportProgressStage.FRAME_RATE_VERIFICATION,
            ExportProgressStage.FINALIZATION,
            ExportProgressStage.COMPLETE
        )
        assertForward(
            ExportProgressStage.VIDEO_RENDER,
            ExportProgressStage.AUDIO_PREPARATION,
            ExportProgressStage.AUDIO_MIX,
            ExportProgressStage.MUX,
            ExportProgressStage.FRAME_RATE_VERIFICATION,
            ExportProgressStage.FINALIZATION,
            ExportProgressStage.COMPLETE
        )
        assertFalse(canAdvanceExportStage(ExportProgressStage.FINALIZATION, ExportProgressStage.FRAME_RATE_VERIFICATION))
    }

    @Test fun `verification callbacks only advance the active matching phase`() {
        assertEquals(
            0.94f,
            nextVerificationProgress(
                isExporting = true,
                currentStage = ExportProgressStage.FRAME_RATE_VERIFICATION,
                currentProgress = 0.90f,
                reportedProgress = 0.94f
            )!!,
            0f
        )
        assertEquals(
            0.98f,
            nextVerificationProgress(
                isExporting = true,
                currentStage = ExportProgressStage.VIDEO_OUTPUT_VERIFICATION,
                currentProgress = 0.97f,
                reportedProgress = 0.98f,
                expectedStage = ExportProgressStage.VIDEO_OUTPUT_VERIFICATION
            )!!,
            0f
        )
        assertNull(nextVerificationProgress(true, ExportProgressStage.FRAME_RATE_VERIFICATION, 0.94f, 0.93f))
        assertNull(nextVerificationProgress(true, ExportProgressStage.FRAME_RATE_VERIFICATION, 0.94f, 0.94f))
        assertNull(nextVerificationProgress(true, ExportProgressStage.FINALIZATION, 0.97f, 0.98f))
        assertNull(nextVerificationProgress(false, ExportProgressStage.COMPLETE, 1f, 0.98f))
        assertNull(nextVerificationProgress(true, ExportProgressStage.FRAME_RATE_VERIFICATION, 0.94f, Float.NaN))
    }

    private fun assertForward(vararg stages: ExportProgressStage) {
        for (index in 0 until stages.lastIndex) {
            val current = stages[index]
            val next = stages[index + 1]
            assertTrue("$current should advance to $next", canAdvanceExportStage(current, next))
        }
    }
}
