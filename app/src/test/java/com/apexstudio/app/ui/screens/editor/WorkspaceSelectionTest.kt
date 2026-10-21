package com.apexstudio.app.ui.screens.editor

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
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
}
