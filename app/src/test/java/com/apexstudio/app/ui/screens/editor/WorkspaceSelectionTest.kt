package com.apexstudio.app.ui.screens.editor

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.state.EditorState
import org.junit.Assert.*
import org.junit.Test

class WorkspaceSelectionTest {
    private val first = MediaClip(id = "first", name = "First", uri = "file:///first.mp4", durationMs = 5000L, trimEndMs = 5000L)
    private val second = first.copy(id = "second", name = "Second", uri = "file:///second.mp4")
    private val project = Project("p", "Project", 10000L, clips = listOf(first, second))

    @Test fun `null or stale selection never falls back to the first video`() {
        assertNull(selectedWorkspaceVideo(project, null))
        assertNull(selectedWorkspaceVideo(project, "missing"))
        assertEquals(second, selectedWorkspaceVideo(project, "second"))
    }
    @Test fun `unsupported types layers and invalid source ranges cannot launch editing`() {
        listOf(first.copy(type = ClipType.OVERLAY, trackIndex = 1), first.copy(type = ClipType.IMAGE),
            first.copy(trimEndMs = 0L), first.copy(trimEndMs = 6000L), first.copy(uri = "")).forEach { invalid ->
            assertNull(selectedWorkspaceVideo(project.copy(clips = listOf(invalid)), invalid.id))
        }
    }

    @Test fun `explicit restart dismisses before opening only the latest saved selected snapshot`() {
        val fresh = second.copy(uri = "file:///replaced.mp4", trimStartMs = 500L, trimEndMs = 4500L)
        val saved = EditorState(project = project.copy(clips = listOf(first, fresh)), selectedClipId = fresh.id)
        val calls = mutableListOf<String>()
        var opened: MediaClip? = null
        assertTrue(restartFilterWorkspace("p", saved, onDismiss = { calls += "dismiss" }) {
            calls += "open"
            opened = it
        })
        assertEquals(listOf("dismiss", "open"), calls)
        assertSame(fresh, opened)
        assertEquals(listOf(first, fresh), saved.project!!.clips)
    }

    @Test fun `invalid restart closes and never substitutes another available main video`() {
        val overlay = second.copy(type = ClipType.OVERLAY, trackIndex = 1)
        val invalidRange = second.copy(trimEndMs = 0L)
        val cases = listOf(
            EditorState(project = project, selectedClipId = null),
            EditorState(project = project, selectedClipId = "missing"),
            EditorState(project = project.copy(clips = listOf(first, overlay)), selectedClipId = overlay.id),
            EditorState(project = project.copy(clips = listOf(first, invalidRange)), selectedClipId = invalidRange.id),
            EditorState(project = project.copy(id = "different"), selectedClipId = first.id),
            EditorState(project = null, selectedClipId = first.id),
        )
        cases.forEach { saved ->
            var dismissed = 0
            var opened = 0
            assertFalse(restartFilterWorkspace("p", saved, onDismiss = { dismissed++ }) { opened++ })
            assertEquals(1, dismissed)
            assertEquals(0, opened)
        }
    }
}
