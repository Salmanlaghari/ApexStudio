package com.apexstudio.app.ui.screens.editor

import com.apexstudio.app.domain.model.MediaClip
import org.junit.Assert.*
import org.junit.Test

class EffectWorkspaceStateTest {
    private val source = MediaClip("clip", "Video", "file:///source.mp4", 10_000L, 2_000L, 8_000L)

    @Test fun `derived preview replaces only render input and never mutates saved project`() {
        val project = com.apexstudio.app.domain.model.Project("project", "Project", 6_000L, clips = listOf(source))
        val saved = com.apexstudio.app.presentation.state.EditorState(project = project, durationMs = 6_000L)
        val derived = source.copy(uri = "file:///derived.mp4", durationMs = 12_000L, trimStartMs = 0L, trimEndMs = 12_000L)
        val preview = workspacePreviewState(saved, derived)
        assertEquals(project, saved.project)
        assertEquals("file:///source.mp4", saved.project!!.clips.single().uri)
        assertEquals(derived, preview.project!!.clips.single())
        assertEquals(12_000L, preview.durationMs)
        assertEquals(saved, workspacePreviewState(saved, null))
        assertEquals(saved, workspacePreviewState(saved, derived.copy(id = "not-in-project")))
    }

    @Test fun `range is absolute source time and keeps a nonempty minimum`() {
        assertEquals(WorkspaceRange(3_500L, 6_500L), WorkspaceRange.fromFractions(source, .25f, .75f))
        assertEquals(WorkspaceRange(7_900L, 8_000L), WorkspaceRange.fromFractions(source, 1f, 1f))
        assertEquals(WorkspaceRange(2_000L, 8_000L), WorkspaceRange.fromFractions(source, Float.NaN, Float.NaN))
        assertEquals(WorkspaceRange(2_000L, 8_000L), WorkspaceRange.fromFractions(source, -1f, 2f))
    }

    @Test fun `playback marks preserve source time order and enforce nonempty in-trim range`() {
        assertEquals(WorkspaceRange(3_000L, 5_500L), WorkspaceRange.fromPlaybackMarks(source, 5_500L, 3_000L))
        assertEquals(WorkspaceRange(7_900L, 8_000L), WorkspaceRange.fromPlaybackMarks(source, 8_000L, 8_000L))
        assertEquals(WorkspaceRange(2_000L, 2_100L), WorkspaceRange.fromPlaybackMarks(source, -50L, 2_020L))
    }
}
