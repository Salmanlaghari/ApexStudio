package com.apexstudio.app.domain.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoDrawingTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun legacyProjectWithoutDrawingFieldStillDecodesEmpty() {
        val project = Json.decodeFromString<Project>("""{"id":"p1","name":"Legacy","durationMs":1200}""")
        assertTrue(project.videoDrawings.isEmpty())
    }

    @Test
    fun projectDrawingLayerRoundTripsWithNormalizedBrushAndEraserStrokes() {
        val layer = VideoDrawingLayer(
            id = "ink-1",
            clipId = "clip-1",
            strokes = listOf(
                VideoDrawingStroke(listOf(VideoDrawingPoint(0.1f, 0.2f), VideoDrawingPoint(0.8f, 0.7f))),
                VideoDrawingStroke(listOf(VideoDrawingPoint(0.4f, 0.4f)), tool = VideoDrawingTool.ERASER, width = 0.02f)
            )
        )
        val project = Project("p1", "Drawings", 1000L, videoDrawings = listOf(layer))
        val restored = Json.decodeFromString<Project>(json.encodeToString(project))
        assertEquals(listOf(layer), restored.videoDrawings)
    }

    @Test
    fun sanitizeClampsInvalidGeometryAndUndoRemovesOneWholeStroke() {
        val layer = VideoDrawingLayer(strokes = listOf(
            VideoDrawingStroke(listOf(VideoDrawingPoint(Float.NaN, 1.4f)), width = Float.NaN),
            VideoDrawingStroke(listOf(VideoDrawingPoint(-0.2f, 0.5f)), tool = VideoDrawingTool.ERASER)
        ))
        val clean = layer.sanitized()
        assertEquals(0f, clean.strokes[0].points.single().x)
        assertEquals(1f, clean.strokes[0].points.single().y)
        assertEquals(VideoDrawingStroke.DEFAULT_WIDTH, clean.strokes[0].width)
        assertEquals(1, clean.undo().strokes.size)
        assertEquals(VideoDrawingTool.BRUSH, clean.undo().strokes.single().tool)
    }

    @Test
    fun emptyUndoIsSafe() {
        val empty = VideoDrawingLayer(id = "empty")
        assertEquals(empty, empty.undo())
    }

    @Test
    fun visibleLayersIncludeGlobalAndOnlyTheCurrentClip() {
        val global = VideoDrawingLayer(id = "global")
        val first = VideoDrawingLayer(id = "first", clipId = "clip-a")
        val second = VideoDrawingLayer(id = "second", clipId = "clip-b")
        assertEquals(listOf(global, first), visibleVideoDrawingLayers(listOf(global, first, second), "clip-a"))
        assertEquals(listOf(global), visibleVideoDrawingLayers(listOf(global, first, second), null))
    }
}
