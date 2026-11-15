package com.apexstudio.app.ui.screens.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import org.junit.Assert.assertEquals
import org.junit.Test

class AnimatedTextOverlayStrokeTest {
    @Test
    fun `preview outline matches export color and stroke width`() {
        val style = textOverlayOutlineStyle(0xFF112233L, frameHeightPx = 1080f)

        assertEquals(Color(0xFF112233L), style.color)
        assertEquals(8.64f, (style.drawStyle as Stroke).width, 0.001f)
    }

    @Test
    fun `preview outline keeps export minimum width on small frames`() {
        val style = textOverlayOutlineStyle(0xFF000000L, frameHeightPx = 100f)

        assertEquals(2f, (style.drawStyle as Stroke).width, 0f)
    }
}
