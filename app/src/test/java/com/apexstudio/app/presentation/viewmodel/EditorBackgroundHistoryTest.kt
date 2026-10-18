package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorBackgroundHistoryTest {
    private fun editor() = EditorViewModel().also { vm ->
        val clip = MediaClip(id = "clip", name = "Video", uri = "file:///bg_replace/processed.mp4",
            durationMs = 10_000L, trimEndMs = 10_000L,
            backgroundReplace = BackgroundReplaceSettings(sourceUri = "file:///original.mp4"))
        vm._state.update { it.copy(project = Project(id = "project", name = "Project",
            clips = listOf(clip), durationMs = 10_000L)) }
    }
    @Test fun restoreOriginalKeepsProcessedUriInUndoHistory() {
        val vm = editor()
        vm.restoreOriginalBackground("clip")
        assertEquals("file:///original.mp4", vm.state.value.project!!.clips.single().uri)
        assertNull(vm.state.value.project!!.clips.single().backgroundReplace)
        assertEquals("file:///bg_replace/processed.mp4", vm.undoStack.single().single().uri)
        assertNotNull(vm.undoStack.single().single().backgroundReplace)
    }
    @Test fun restoreInvalidatesActiveRequestBeforeChangingClip() {
        val vm = editor(); val job = Job()
        vm.backgroundJob = job
        vm.restoreOriginalBackground("clip")
        assertTrue(job.isCancelled)
        assertNull(vm.backgroundJob)
        assertEquals(BackgroundPhase.IDLE, vm.backgroundUi.value.phase)
    }
    @Test fun repeatedRestoreDoesNotCreateAnotherUndoEntry() {
        val vm = editor()
        vm.restoreOriginalBackground("clip")
        vm.restoreOriginalBackground("clip")
        assertEquals(1, vm.undoStack.size)
    }
}
