package com.apexstudio.app.ui.screens.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceVideoPreviewVolumeTest {
    @Test
    fun `muted original video has zero preview volume`() {
        assertEquals(0f, sourceVideoPreviewVolume(0.75f, originalVideoMuted = true), 0f)
    }

    @Test
    fun `unmuted original video keeps its clamped clip volume`() {
        assertEquals(0.75f, sourceVideoPreviewVolume(0.75f, originalVideoMuted = false), 0f)
        assertEquals(2f, sourceVideoPreviewVolume(3f, originalVideoMuted = false), 0f)
    }
}
