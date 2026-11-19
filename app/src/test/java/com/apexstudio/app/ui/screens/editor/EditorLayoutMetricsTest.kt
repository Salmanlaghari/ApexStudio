package com.apexstudio.app.ui.screens.editor

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorLayoutMetricsTest {
    @Test
    fun brandedChromeUsesComfortableHeaderAndTransportDimensions() {
        assertEquals(52.dp, EditorLayoutMetrics.TopBarHeight)
        assertEquals(10.dp, EditorLayoutMetrics.TopBarHorizontalPadding)
        assertEquals(68.dp, EditorLayoutMetrics.PlaybackBarHeight)
        assertEquals(48.dp, EditorLayoutMetrics.TimelineActionHeight)
        assertEquals(80.dp, EditorLayoutMetrics.TimelineToolbarHeight)
    }

    @Test
    fun expandedPreviewKeepsASubtleBoundedFrame() {
        assertEquals(14.dp, EditorLayoutMetrics.PreviewCornerRadius)
    }
}
