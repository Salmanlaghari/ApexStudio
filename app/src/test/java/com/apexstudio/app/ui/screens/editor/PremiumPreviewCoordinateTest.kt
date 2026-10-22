package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PremiumPreviewCoordinateTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun fitBoundsCenterPortraitAndSquareSourcesInsideLandscapePreview() {
        val portrait = previewContentBounds(
            viewportWidth = 320f,
            viewportHeight = 180f,
            mediaAspectRatio = 9f / 16f,
            isCoverMode = false
        )
        assertEquals(109.375f, portrait!!.left, 0.001f)
        assertEquals(0f, portrait.top, 0.001f)
        assertEquals(210.625f, portrait.right, 0.001f)
        assertEquals(180f, portrait.bottom, 0.001f)

        val square = previewContentBounds(
            viewportWidth = 320f,
            viewportHeight = 180f,
            mediaAspectRatio = 1f,
            isCoverMode = false
        )
        assertEquals(70f, square!!.left, 0.001f)
        assertEquals(0f, square.top, 0.001f)
        assertEquals(250f, square.right, 0.001f)
        assertEquals(180f, square.bottom, 0.001f)
    }

    @Test
    fun compositionCanvasUsesTheSelectedExportAspect() {
        assertEquals(16f / 9f, previewCompositionAspect("16:9"), 0.0001f)
        assertEquals(9f / 16f, previewCompositionAspect("9:16"), 0.0001f)
        assertEquals(1f, previewCompositionAspect("1:1"), 0.0001f)
    }

    @Test
    fun productionInteractiveCanvasUsesCenteredContentBounds() {
        val viewportSize = mutableStateOf(IntSize.Zero)
        val canvasSize = mutableStateOf(IntSize.Zero)
        val canvasPosition = mutableStateOf(IntOffset.Zero)

        compose.setContent {
            Box(
                Modifier.size(320.dp, 180.dp).onGloballyPositioned {
                    viewportSize.value = it.size
                }
            ) {
                PreviewContentCanvas(
                    mediaAspectRatio = 9f / 16f,
                    isCoverMode = false,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(Modifier.fillMaxSize().onGloballyPositioned {
                        canvasSize.value = it.size
                        val pos = it.positionInRoot()
                        canvasPosition.value = IntOffset(pos.x.toInt(), pos.y.toInt())
                    })
                }
            }
        }

        compose.waitForIdle()
        val viewport = viewportSize.value
        val canvas = canvasSize.value
        assertTrue("viewport should be laid out", viewport.width > 0 && viewport.height > 0)
        assertTrue("interactive canvas should be laid out", canvas.width > 0 && canvas.height > 0)
        assertEquals(viewport.height, canvas.height)
        assertEquals((viewport.width - canvas.width) / 2, canvasPosition.value.x)
        assertEquals(0, canvasPosition.value.y)
        assertTrue(abs(canvas.width.toFloat() / canvas.height - 9f / 16f) < 0.01f)
    }

    @Test
    fun coverKeepsTheInteractiveCanvasOnTheEntireViewport() {
        val bounds = previewContentBounds(
            viewportWidth = 320f,
            viewportHeight = 180f,
            mediaAspectRatio = 9f / 16f,
            isCoverMode = true
        )
        assertEquals(0f, bounds!!.left, 0.001f)
        assertEquals(0f, bounds.top, 0.001f)
        assertEquals(320f, bounds.right, 0.001f)
        assertEquals(180f, bounds.bottom, 0.001f)
    }
}
