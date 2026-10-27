package com.apexstudio.app.ui.screens.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditorPreviewStageGeometryTest {
    @Test
    fun portraitStageUsesAvailableHeightAndKeepsTheSourceAspectRatio() {
        val fitted = fittedPreviewStageSize(maxWidth = 375f, maxHeight = 300f, aspectRatio = 9f / 16f)

        assertEquals(168.75f, fitted!!.width, 0.001f)
        assertEquals(300f, fitted.height, 0.001f)
        assertEquals(9f / 16f, fitted.width / fitted.height, 0.0001f)
    }

    @Test
    fun landscapeStageUsesAvailableWidthAndKeepsTheSourceAspectRatio() {
        val fitted = fittedPreviewStageSize(maxWidth = 375f, maxHeight = 300f, aspectRatio = 16f / 9f)

        assertEquals(375f, fitted!!.width, 0.001f)
        assertEquals(210.9375f, fitted.height, 0.001f)
        assertEquals(16f / 9f, fitted.width / fitted.height, 0.0001f)
    }

    @Test
    fun invalidGeometryDoesNotProduceAnInvalidStage() {
        assertNull(fittedPreviewStageSize(0f, 300f, 9f / 16f))
        assertNull(fittedPreviewStageSize(375f, 300f, Float.NaN))
    }
}
