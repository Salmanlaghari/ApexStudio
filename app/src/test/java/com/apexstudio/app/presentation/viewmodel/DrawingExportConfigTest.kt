package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.*
import com.apexstudio.app.presentation.state.AudioStudioState
import com.apexstudio.app.presentation.state.EditorState
import org.junit.Assert.*
import org.junit.Test

class DrawingExportConfigTest {
    private val brush = VideoDrawingStroke(points = listOf(VideoDrawingPoint(0.5f, 0.5f)))
    private val drawings = listOf(
        VideoDrawingLayer(id = "global", strokes = listOf(brush)),
        VideoDrawingLayer(id = "a-art", clipId = "a", strokes = listOf(brush)),
        VideoDrawingLayer(id = "b-art", clipId = "b", strokes = listOf(brush))
    )
    private val state = EditorState(project = Project(id = "p", name = "P", durationMs = 1000,
        videoDrawings = drawings))

    @Test fun selectedClipReceivesOnlyItsOwnAndGlobalArt() {
        assertEquals(listOf("global", "a-art"), selectedExportDrawings(state, "a").map { it.id })
        assertEquals(listOf("global", "b-art"), selectedExportDrawings(state, "b").map { it.id })
        assertEquals(listOf("global"), selectedExportDrawings(state, null).map { it.id })
        assertTrue(selectedExportDrawings(EditorState(), "a").isEmpty())
    }

    @Test fun projectPreflightReceivesAllDrawingLayersRatherThanDroppingThem() {
        val config = buildProjectExportConfig(state, AudioStudioState(), ExportSettings())
        assertEquals(drawings, config.videoDrawings)
    }
}
