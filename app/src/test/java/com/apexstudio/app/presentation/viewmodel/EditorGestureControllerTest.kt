package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.ai.mediapipe.GestureCommand
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorGestureControllerTest {
    private val clip = MediaClip("video", "Video", "file:///source.mp4", 20_000L,
        trimStartMs = 4_000L, trimEndMs = 14_000L)

    private fun editor() = EditorViewModel().also { vm ->
        vm._state.value = vm.state.value.copy(selectedClipId = clip.id,
            project = Project("project", "Project", 10_000L, clips = listOf(clip)))
    }

    @Test fun signedSwipeUsesAbsoluteSourceBoundsNotProjectPlayhead() {
        assertEquals(4_000L to 13_000L, GestureEditPlanner.trim(clip, .1f))
        assertEquals(5_000L to 14_000L, GestureEditPlanner.trim(clip, -.1f))
    }

    @Test fun tinyClipRetainsAMinimumSpanWithoutMovingOppositeHandle() {
        val tail = clip.copy(trimStartMs = 4_000L, trimEndMs = 4_105L)
        assertEquals(4_000L to 4_100L, GestureEditPlanner.trim(tail, .1f))
        assertEquals(4_005L to 4_105L, GestureEditPlanner.trim(tail, -.1f))
        assertNull(GestureEditPlanner.trim(tail.copy(trimEndMs = 4_100L), .1f))
    }

    @Test fun malformedOrUnboundedGestureRequestsNeverPlanAnEdit() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, .11f, -.11f, 0f).forEach {
            assertNull(GestureEditPlanner.trim(clip, it))
        }
        assertNull(GestureEditPlanner.trim(clip.copy(durationMs = 0L), .1f))
        assertNull(GestureEditPlanner.timelineZoom(Float.NaN, 1f))
        assertNull(GestureEditPlanner.timelineZoom(1f, Float.NaN))
        assertNull(GestureEditPlanner.timelineZoom(1f, 2f))
    }

    @Test fun zoomIsBoundedTimelineOnlyAndAvoidsNoOpUpdates() {
        assertEquals(10f, GestureEditPlanner.timelineZoom(9f, 1.25f)!!, 0f)
        assertEquals(.5f, GestureEditPlanner.timelineZoom(.6f, .8f)!!, 0f)
        assertNull(GestureEditPlanner.timelineZoom(1f, 1f))
    }

    @Test fun explicitArmCannotEnableGestureAiOutsideTheVideoOnlyScope() {
        val vm = editor()
        val before = vm.state.value.project
        assertNull(vm.beginGestureEditing())
        assertNull(vm.gestureEditing.value.session)
        assertNull(vm.editorGestureSession)
        assertTrue(vm.gestureEditing.value.message.contains("existing-project-video scope"))
        assertEquals(before, vm.state.value.project)
        assertTrue(vm.undoStack.isEmpty())
    }

    @Test fun forgedSessionAndCommandsAfterDisarmCannotMutateTheEditor() {
        val vm = editor()
        val before = vm.state.value.project
        assertFalse(vm.applyGestureCommand("forged", GestureCommand.TrimByFraction(.1f)))
        vm.endGestureEditing()
        assertFalse(vm.applyGestureCommand("forged", GestureCommand.ZoomByFactor(1.25f)))
        assertEquals(before, vm.state.value.project)
        assertTrue(vm.undoStack.isEmpty())
    }

    @Test fun staleEditorSnapshotDisarmsBeforeMutation() {
        val vm = editor()
        val snapshot = vm.state.value.project!!
        vm.editorGestureSession = EditorGestureSession("session", snapshot, clip.id)
        vm._state.value = vm.state.value.copy(project = snapshot.copy(name = "Changed project"))
        assertFalse(vm.applyGestureCommand("session", GestureCommand.TrimByFraction(.1f)))
        assertNull(vm.editorGestureSession)
        assertTrue(vm.gestureEditing.value.message.contains("editor changed"))
        assertTrue(vm.undoStack.isEmpty())
    }
}
