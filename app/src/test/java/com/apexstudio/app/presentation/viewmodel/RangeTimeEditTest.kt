package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.data.engine.RangeTimeOperation
import com.apexstudio.app.data.engine.RangeTimeRenderResult
import com.apexstudio.app.domain.model.*
import kotlinx.coroutines.flow.update
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RangeTimeEditTest {
    private fun source() = MediaClip(
        id = "source", name = "Source", uri = "file:///original.mp4",
        durationMs = 10_000L, trimStartMs = 1_000L, trimEndMs = 9_000L
    )

    private fun project(clip: MediaClip = source()) = Project(
        id = "project", name = "Project", durationMs = clip.effectiveDurationMs(), clips = listOf(clip)
    )

    @Test
    fun `selection is trim-relative and renderer receives absolute source bounds`() {
        val clip = source()
        val range = SourceRangeMs(2_000L, 4_000L)
        assertEquals(3_000L, range.absoluteStartMs(clip))
        assertEquals(5_000L, range.absoluteEndMs(clip))
    }

    @Test
    fun `slow motion plan replaces whole clip with 2x-duration derived media`() {
        val original = project()
        val result = RangeTimeEdit.plan(
            original, "source", SourceRangeMs(2_000L, 4_000L), RangeTimeOperation.SLOW,
            RangeTimeRenderResult("file:///derived/slow.mp4", 10_000L)
        ) as RangeTimePlanResult.Planned

        val updated = result.plan.updatedProject
        assertEquals(1, updated.clips.size)
        assertEquals("source", updated.clips.single().id)
        assertEquals("file:///derived/slow.mp4", updated.clips.single().uri)
        assertEquals(10_000L, updated.clips.single().durationMs)
        assertEquals(0L, updated.clips.single().trimStartMs)
        assertEquals(10_000L, updated.clips.single().trimEndMs)
        assertEquals(10_000L, updated.durationMs)
        assertEquals("file:///original.mp4", original.clips.single().uri)
        assertEquals(8_000L, original.durationMs)
    }

    @Test
    fun `repeat once adds range duration while reverse preserves whole trimmed duration`() {
        val original = project()
        val repeated = RangeTimeEdit.plan(
            original, "source", SourceRangeMs(2_000L, 4_000L), RangeTimeOperation.REPEAT,
            RangeTimeRenderResult("file:///derived/repeated.mp4", 10_000L)
        ) as RangeTimePlanResult.Planned
        assertEquals(10_000L, repeated.plan.updatedProject.durationMs)

        val reversed = RangeTimeEdit.plan(
            original, "source", SourceRangeMs(2_000L, 4_000L), RangeTimeOperation.REVERSE,
            RangeTimeRenderResult("file:///derived/reversed.mp4", 8_000L)
        ) as RangeTimePlanResult.Planned
        assertEquals(8_000L, reversed.plan.updatedProject.durationMs)
        assertEquals(1f, reversed.plan.updatedProject.clips.single().speedMultiplier)
    }

    @Test
    fun `planner rejects out of trim range and unexpected output duration`() {
        val invalidRange = RangeTimeEdit.eligibility(
            project(), "source", SourceRangeMs(7_500L, 8_001L), RangeTimeOperation.REVERSE
        )
        assertFalse(invalidRange.isEligible)
        assertEquals(RangeTimeRejection.INVALID_RANGE, invalidRange.rejection)

        val mismatch = RangeTimeEdit.plan(
            project(), "source", SourceRangeMs(2_000L, 4_000L), RangeTimeOperation.SLOW,
            RangeTimeRenderResult("file:///derived/wrong.mp4", 8_000L)
        ) as RangeTimePlanResult.Rejected
        assertEquals(RangeTimeRejection.UNEXPECTED_RENDER_DURATION, mismatch.eligibility.rejection)
    }

    @Test
    fun `complex projects and time-dependent clip effects are refused`() {
        val secondVideo = source().copy(id = "second", trimStartMs = 0L, trimEndMs = 1_000L)
        val complex = RangeTimeEdit.eligibility(
            project().copy(clips = listOf(source(), secondVideo)),
            "source", SourceRangeMs(0L, 1_000L), RangeTimeOperation.REPEAT
        )
        assertEquals(RangeTimeRejection.COMPLEX_PROJECT, complex.rejection)

        val keyed = source().copy(keyframes = KeyframeTrack(listOf(Keyframe(id = "kf", timeMs = 500L))))
        val keyedResult = RangeTimeEdit.eligibility(
            project(keyed), "source", SourceRangeMs(0L, 1_000L), RangeTimeOperation.REPEAT
        )
        assertEquals(RangeTimeRejection.UNSUPPORTED_CLIP_FEATURES, keyedResult.rejection)

        val workspaceEffect = source().copy(workspaceEffects = listOf(WorkspaceEffect("fx", 0L, 1_000L)))
        val effectResult = RangeTimeEdit.eligibility(
            project(workspaceEffect), "source", SourceRangeMs(0L, 1_000L), RangeTimeOperation.REPEAT
        )
        assertEquals(RangeTimeRejection.UNSUPPORTED_CLIP_FEATURES, effectResult.rejection)
    }

    @Test
    fun `controller applies only after explicit apply and change is one undoable transaction`() {
        val vm = EditorViewModel()
        val originalProject = project()
        vm._state.update { it.copy(project = originalProject, durationMs = originalProject.durationMs, selectedClipId = "source") }
        val expectedClip = originalProject.clips.single()
        val range = SourceRangeMs(2_000L, 4_000L)
        val asset = RangeTimeRenderResult("file:///derived/repeat.mp4", 10_000L)

        assertTrue(vm.rangeTimeEligibility("project", expectedClip, range, RangeTimeOperation.REPEAT).isEligible)
        // A pending preview is represented by a pure plan; neither editor state nor history changes.
        val preview = RangeTimeEdit.plan(originalProject, expectedClip.id, range, RangeTimeOperation.REPEAT, asset)
            as RangeTimePlanResult.Planned
        assertEquals(originalProject, vm.state.value.project)
        assertTrue(vm.undoStack.isEmpty())
        assertEquals("file:///derived/repeat.mp4", preview.plan.updatedProject.clips.single().uri)

        val applied = vm.applyRangeTimeRender("project", expectedClip, range, RangeTimeOperation.REPEAT, asset)
        assertTrue(applied is RangeTimeApplyResult.Applied)
        assertEquals(1, vm.undoStack.size)
        assertEquals("file:///original.mp4", vm.undoStack.single().project.clips.single().uri)
        assertEquals("file:///derived/repeat.mp4", vm.state.value.project!!.clips.single().uri)
        assertEquals(10_000L, vm.state.value.durationMs)

        vm.undo()
        assertEquals(originalProject, vm.state.value.project)
        assertTrue(vm.redoStack.isNotEmpty())
        vm.redo()
        assertEquals("file:///derived/repeat.mp4", vm.state.value.project!!.clips.single().uri)
        assertEquals(1, vm.undoStack.size)
    }

    @Test
    fun `stale source snapshot or wrong project fails before history mutation`() {
        val vm = EditorViewModel()
        val original = project()
        vm._state.update { it.copy(project = original, selectedClipId = "source") }
        val stale = vm.applyRangeTimeRender(
            "not-project", original.clips.single(), SourceRangeMs(0L, 1_000L), RangeTimeOperation.REVERSE,
            RangeTimeRenderResult("file:///derived/reverse.mp4", 8_000L)
        )
        assertTrue(stale is RangeTimeApplyResult.Rejected)
        assertEquals(RangeTimeRejection.PROJECT_MISMATCH, (stale as RangeTimeApplyResult.Rejected).eligibility.rejection)
        assertTrue(vm.undoStack.isEmpty())

        vm._state.update { it.copy(project = project(source().copy(name = "Changed"))) }
        val rejected = vm.applyRangeTimeRender(
            "project", source(), SourceRangeMs(0L, 1_000L), RangeTimeOperation.REVERSE,
            RangeTimeRenderResult("file:///derived/reverse2.mp4", 8_000L)
        )
        assertTrue(rejected is RangeTimeApplyResult.Rejected)
        assertEquals(RangeTimeRejection.CLIP_MISMATCH, (rejected as RangeTimeApplyResult.Rejected).eligibility.rejection)
        assertTrue(vm.undoStack.isEmpty())
    }
}
