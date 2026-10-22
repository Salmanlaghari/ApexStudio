package com.apexstudio.app.ui.screens.editor

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorLayoutMetricsTest {
    @Test
    fun brandedChromeUsesComfortableHeaderAndTransportDimensions() {
        assertEquals(40.dp, EditorLayoutMetrics.TopBarHeight)
        assertEquals(8.dp, EditorLayoutMetrics.TopBarHorizontalPadding)
        assertEquals(44.dp, EditorLayoutMetrics.PlaybackBarHeight)
        assertEquals(48.dp, EditorLayoutMetrics.TimelineActionHeight)
    }
}
