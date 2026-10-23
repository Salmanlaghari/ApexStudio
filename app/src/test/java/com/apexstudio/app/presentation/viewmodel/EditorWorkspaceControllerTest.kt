package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorWorkspaceControllerTest {
    private val clip = MediaClip("clip", "Video", "file:///source.mp4", 10_000L, 2_000L, 8_000L)
    private fun editor() = EditorViewModel().also { vm ->
        vm._state.value = vm.state.value.copy(project = Project("project", "Project", 6_000L, clips = listOf(clip)), selectedClipId = clip.id)
    }
    @Test fun `apply changes only effect list and records one undo snapshot`() {
        val vm = editor()
        val effects = listOf(WorkspaceEffect("warm", 3_000L, 4_000L))
        assertTrue(vm.applyWorkspaceEffects("project", clip, effects))
        assertEquals(clip.copy(workspaceEffects = effects), vm.state.value.project!!.clips.single())
        assertEquals(clip, vm.undoStack.single().project.clips.single())
        assertEquals(1, vm.undoStack.size)
        assertEquals("file:///source.mp4", clip.uri)
    }
    @Test fun `stale selection unknown preset invalid range and nonfinite intensity are rejected without history`() {
        val vm = editor()
        val cases = listOf(
            listOf(WorkspaceEffect("unknown", 2_000L, 3_000L)),
            listOf(WorkspaceEffect("mono", 3_000L, 2_000L)),
            listOf(WorkspaceEffect("mono", 2_000L, 3_000L, Float.NaN))
        )
        cases.forEach { assertFalse(vm.applyWorkspaceEffects("project", clip, it)) }
        assertFalse(vm.applyWorkspaceEffects("other", clip, listOf(WorkspaceEffect("mono", 2_000L, 3_000L))))
        assertFalse(vm.applyWorkspaceEffects("project", clip.copy(name = "stale"), listOf(WorkspaceEffect("mono", 2_000L, 3_000L))))
        assertEquals(clip, vm.state.value.project!!.clips.single())
        assertTrue(vm.undoStack.isEmpty())
    }
    @Test fun `legacy clip roundtrip retains defaults and saved effects retain exact source range`() {
        val legacy = Json.encodeToString(clip)
        assertEquals(emptyList<WorkspaceEffect>(), Json.decodeFromString<MediaClip>(legacy).workspaceEffects)
        val saved = clip.copy(workspaceEffects = listOf(WorkspaceEffect("split3", 2_500L, 7_500L)))
        assertEquals(saved, Json.decodeFromString<MediaClip>(Json.encodeToString(saved)))
    }
}
