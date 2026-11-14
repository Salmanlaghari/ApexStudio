package com.apexstudio.app.ui.screens.colortools

import com.apexstudio.app.data.export.ExportProgressStage
import com.apexstudio.app.data.export.projectVideoRenderProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColorExportProgressTest {
    @Test fun selectedVideoUsesEncodedMediaTimeAndNeverReports100BeforeCommit() {
        assertNull(colorVideoExportPercent(Double.NaN, 30_000.0))
        assertNull(colorVideoExportPercent(10.0, 0.0))
        assertNull(colorVideoExportPercent(299.0, 30_000.0))
        assertEquals(1, colorVideoExportPercent(300.0, 30_000.0))
        assertEquals(54, colorVideoExportPercent(16_499.0, 30_000.0))
        assertEquals(99, colorVideoExportPercent(30_000.0, 30_000.0))
        assertEquals(99, colorVideoExportPercent(31_000.0, 30_000.0))
    }

    @Test fun timelineShowsOnlyMeasuredVideoRenderProgressAndCommittedCompletion() {
        assertNull(colorTimelineExportPercent(0.5f, null, false))
        assertNull(colorTimelineExportPercent(0f, ExportProgressStage.PREPARING, false))
        assertNull(colorTimelineExportPercent(0.5f, ExportProgressStage.AUDIO_MIX, false))
        assertNull(colorTimelineExportPercent(0.01f, ExportProgressStage.VIDEO_RENDER, false))
        assertEquals(
            1,
            colorTimelineExportPercent(
                projectVideoRenderProgress(0.01f),
                ExportProgressStage.VIDEO_RENDER,
                false
            )
        )
        assertEquals(
            50,
            colorTimelineExportPercent(
                projectVideoRenderProgress(0.5f),
                ExportProgressStage.VIDEO_RENDER,
                false
            )
        )
        assertEquals(
            99,
            colorTimelineExportPercent(
                projectVideoRenderProgress(1f),
                ExportProgressStage.VIDEO_RENDER,
                false
            )
        )
        assertNull(colorTimelineExportPercent(0.99f, ExportProgressStage.COMPLETE, false))
        assertEquals(100, colorTimelineExportPercent(1f, ExportProgressStage.COMPLETE, true))
    }
}
