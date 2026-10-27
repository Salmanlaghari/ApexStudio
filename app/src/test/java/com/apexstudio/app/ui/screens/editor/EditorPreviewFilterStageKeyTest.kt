package com.apexstudio.app.ui.screens.editor

import com.apexstudio.app.domain.model.GpuFilterConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EditorPreviewFilterStageKeyTest {
    @Test
    fun `strength-only edits keep Media3 stage key stable`() {
        val initial = GpuFilterConfig(filterPresetId = "cinematic", filterIntensity = 0.2f)
        assertEquals(
            gpuFilterConfigPreviewKey(initial),
            gpuFilterConfigPreviewKey(initial.copy(filterIntensity = 0.9f)),
        )
    }

    @Test
    fun `changing the look keeps a structural stage change`() {
        val initial = GpuFilterConfig(filterPresetId = "cinematic", filterIntensity = 0.2f)
        assertNotEquals(
            gpuFilterConfigPreviewKey(initial),
            gpuFilterConfigPreviewKey(initial.copy(filterPresetId = "mono")),
        )
    }
}
