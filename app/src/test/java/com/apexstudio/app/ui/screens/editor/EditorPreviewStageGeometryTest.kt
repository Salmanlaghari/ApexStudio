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

    @Test
    fun liveSourceRatioWinsAndPersistedProjectRatioPreventsLandscapeFallbackFlash() {
        assertEquals(9f / 16f, editorPreviewStageAspectRatio(null, 9f / 16f, 16f / 9f), 0.0001f)
        assertEquals(16f / 9f, editorPreviewStageAspectRatio(16f / 9f, 9f / 16f, 1f), 0.0001f)
        assertEquals(16f / 9f, editorPreviewStageAspectRatio(null, null, 16f / 9f), 0.0001f)
    }

    @Test
    fun contentSizedPreviewReservesPlaybackAndTimelineChromeWithoutNegativeHeight() {
        assertEquals(260f, previewStageHeightBudget(640f, 380f), 0.001f)
        assertEquals(0f, previewStageHeightBudget(300f, 380f), 0.001f)
        assertEquals(0f, previewStageHeightBudget(Float.NaN, 10f), 0.001f)
    }

    @Test
    fun horizontalMobileShellIsChosenFromCompositionAspectNotDeviceOrientation() {
        assertEquals(true, usesHorizontalProjectLayout(16f / 9f))
        assertEquals(false, usesHorizontalProjectLayout(9f / 16f))
        assertEquals(false, usesHorizontalProjectLayout(1f))
        assertEquals(false, usesHorizontalProjectLayout(Float.NaN))
        assertEquals(false, usesHorizontalProjectLayout(null))
    }
}
