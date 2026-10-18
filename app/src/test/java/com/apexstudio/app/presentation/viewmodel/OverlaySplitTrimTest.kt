package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlaySplitTrimTest {
    @Test
    fun identicalTrimDoesNotCreateAnUndoStep() {
        val overlay = MediaClip(
            id = "unchanged-overlay",
            name = "unchanged-overlay",
            uri = "file:///overlay.mp4",
            durationMs = 15_000L,
            trimStartMs = 2_000L,
            trimEndMs = 12_000L,
            timelineOffsetMs = 6_000L,
            type = ClipType.OVERLAY,
            trackIndex = 2
        )
        val vm = EditorViewModel().also { viewModel ->
            viewModel._state.update {
                it.copy(
                    project = Project(id = "p", name = "p", durationMs = 20_000L, clips = listOf(overlay)),
                    durationMs = 20_000L
                )
            }
        }

        vm.trimClip(overlay.id, overlay.trimStartMs, overlay.trimEndMs)

        assertFalse("a no-op drag must not create a misleading undo action", vm.state.value.canUndo)
        assertEquals(overlay, vm.state.value.project!!.clips.single())
    }

    @Test
    fun overlayGesturesRejectNonFiniteTransformUpdates() {
        val overlay = MediaClip(
            id = "finite-overlay",
            name = "finite-overlay",
            uri = "file:///overlay.mp4",
            durationMs = 15_000L,
            trimEndMs = 15_000L,
            type = ClipType.OVERLAY,
            trackIndex = 2
        )
        val vm = EditorViewModel().also { viewModel ->
            viewModel._state.update {
                it.copy(
                    project = Project(id = "p", name = "p", durationMs = 20_000L, clips = listOf(overlay)),
                    durationMs = 20_000L
                )
            }
        }

        vm.moveOverlayClip(overlay.id, Float.NaN, Float.POSITIVE_INFINITY)
        vm.scaleOverlayClip(overlay.id, Float.NaN)
        vm.rotateOverlayClip(overlay.id, Float.NaN)
        vm.setOverlayClipOpacity(overlay.id, Float.NaN)

        val edited = vm.state.value.project!!.clips.single()
        assertEquals(0.85f, edited.pipX, 0.0001f)
        assertEquals(0.85f, edited.pipY, 0.0001f)
        assertEquals(0.25f, edited.pipScale, 0.0001f)
        assertEquals(0f, edited.pipRotationDeg, 0.0001f)
        assertEquals(1f, edited.pipOpacity, 0.0001f)

        vm._state.update { state ->
            state.copy(project = state.project!!.copy(clips = listOf(edited.copy(pipRotationDeg = Float.MAX_VALUE))))
        }
        vm.rotateOverlayClip(overlay.id, Float.MAX_VALUE)
        val rotation = vm.state.value.project!!.clips.single().pipRotationDeg
        assertTrue("finite gesture values must not overflow into a NaN transform", rotation.isFinite())
        assertTrue(rotation in -360f..360f)

        vm.upsertOverlayKeyframe(overlay.id, 0L, Float.NaN, Float.POSITIVE_INFINITY,
            Float.NaN, Float.NaN, Float.NaN)
        val keyframe = vm.state.value.project!!.clips.single().keyframes.keyframes.single()
        assertEquals(edited.pipX, keyframe.translateX, 0.0001f)
        assertEquals(edited.pipY, keyframe.translateY, 0.0001f)
        assertEquals(edited.pipScale, keyframe.scale, 0.0001f)
        assertTrue(keyframe.rotationDeg.isFinite())
        assertEquals(edited.pipOpacity, keyframe.opacity, 0.0001f)

        vm.upsertOverlayKeyframe(overlay.id, 0L, .3f, .4f, .5f, 25f, .6f)
        val validKeyframe = vm.state.value.project!!.clips.single().keyframes.keyframes.single()
        vm.upsertOverlayKeyframe(overlay.id, 0L, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN)
        assertEquals(validKeyframe, vm.state.value.project!!.clips.single().keyframes.keyframes.single())
        vm.setOverlayClipOpacity(overlay.id, Float.NaN)
        assertEquals(.6f, vm.state.value.project!!.clips.single().keyframes.keyframes.single().opacity, .0001f)
    }

    @Test
    fun trimmingOverlaySourceHeadAdvancesItsTimelineOffset() {
        val overlay = MediaClip(
            id = "left-trim-overlay",
            name = "left-trim-overlay",
            uri = "file:///overlay.mp4",
            durationMs = 15_000L,
            trimStartMs = 2_000L,
            trimEndMs = 12_000L,
            timelineOffsetMs = 6_000L,
            type = ClipType.OVERLAY,
            trackIndex = 2
        )
        val vm = EditorViewModel().also { viewModel ->
            viewModel._state.update {
                it.copy(
                    project = Project(id = "p", name = "p", durationMs = 20_000L, clips = listOf(overlay)),
                    durationMs = 20_000L
                )
            }
        }

        vm.trimClip(overlay.id, 5_000L, overlay.trimEndMs)

        val trimmed = vm.state.value.project!!.clips.single()
        assertEquals(9_000L, trimmed.timelineOffsetMs)
        assertEquals(5_000L, trimmed.trimStartMs)
    }

    @Test
    fun extendingOverlaySourceHeadEarlierMovesItsTimelineOffsetEarlier() {
        val overlay = MediaClip(
            id = "extend-head-overlay",
            name = "extend-head-overlay",
            uri = "file:///overlay.mp4",
            durationMs = 15_000L,
            trimStartMs = 5_000L,
            trimEndMs = 12_000L,
            timelineOffsetMs = 9_000L,
            type = ClipType.OVERLAY,
            trackIndex = 2
        )
        val vm = EditorViewModel().also { viewModel ->
            viewModel._state.update {
                it.copy(
                    project = Project(id = "p", name = "p", durationMs = 20_000L, clips = listOf(overlay)),
                    durationMs = 20_000L
                )
            }
        }

        // The right edge is at 16s. Restoring 3s at the source head must
        // move the left edge from 9s to 6s rather than changing the end.
        vm.trimClip(overlay.id, 2_000L, overlay.trimEndMs)

        val extended = vm.state.value.project!!.clips.single()
        assertEquals(6_000L, extended.timelineOffsetMs)
        assertEquals(2_000L, extended.trimStartMs)
        assertEquals(16_000L, extended.timelineOffsetMs + (extended.trimEndMs - extended.trimStartMs))
    }

    @Test
    fun overlayPropertiesTrimAlsoMovesTimelineHeadAndRecomputesDuration() {
        val overlay = MediaClip(
            id = "properties-trim-overlay",
            name = "properties-trim-overlay",
            uri = "file:///overlay.mp4",
            durationMs = 15_000L,
            trimStartMs = 5_000L,
            trimEndMs = 12_000L,
            timelineOffsetMs = 9_000L,
            type = ClipType.OVERLAY,
            trackIndex = 2
        )
        val vm = EditorViewModel().also { viewModel ->
            viewModel._state.update {
                it.copy(
                    project = Project(id = "p", name = "p", durationMs = 20_000L, clips = listOf(overlay)),
                    durationMs = 20_000L
                )
            }
        }

        vm.trimOverlayClip(overlay.id, 2_000L, overlay.trimEndMs)

        val trimmed = vm.state.value.project!!.clips.single()
        assertEquals(6_000L, trimmed.timelineOffsetMs)
        assertEquals(16_000L, vm.state.value.durationMs)
        assertEquals(vm.state.value.durationMs, vm.state.value.project!!.durationMs)
    }

    @Test
    fun trimmingOverlayHeadOnTimelineKeepsTheRightEdgeWithAChangedSpeedCurveSpan() {
        val overlay = MediaClip(
            id = "ramped-overlay",
            name = "ramped-overlay",
            uri = "file:///overlay.mp4",
            durationMs = 10_000L,
            trimEndMs = 10_000L,
            timelineOffsetMs = 6_000L,
            type = ClipType.OVERLAY,
            trackIndex = 2,
            speedPoints = listOf(
                com.apexstudio.app.domain.model.SpeedPoint(0f, 0.5f),
                com.apexstudio.app.domain.model.SpeedPoint(0.5f, 0.5f),
                com.apexstudio.app.domain.model.SpeedPoint(1f, 2f),
            ),
        )
        val vm = EditorViewModel().also { viewModel ->
            viewModel._state.update {
                it.copy(
                    project = Project(id = "p", name = "p", durationMs = 20_000L, clips = listOf(overlay)),
                    durationMs = 20_000L
                )
            }
        }

        val originalRightEdge = overlay.timelineOffsetMs + overlay.effectiveDurationMs()
        vm.trimClip(overlay.id, 2_500L, overlay.trimEndMs)

        val trimmed = vm.state.value.project!!.clips.single()
        assertEquals(2_500L, trimmed.trimStartMs)
        assertEquals("the right edge must not drift when speed points are renormalized", originalRightEdge,
            trimmed.timelineOffsetMs + trimmed.effectiveDurationMs())
    }

    @Test
    fun trimmingOverlayHeadInPropertiesKeepsTheRightEdgeWithASpeedRamp() {
        val overlay = MediaClip(
            id = "ramped-properties-overlay",
            name = "ramped-properties-overlay",
            uri = "file:///overlay.mp4",
            durationMs = 10_000L,
            trimEndMs = 10_000L,
            timelineOffsetMs = 6_000L,
            type = ClipType.OVERLAY,
            trackIndex = 2,
            speedPoints = listOf(
                com.apexstudio.app.domain.model.SpeedPoint(0f, 0.5f),
                com.apexstudio.app.domain.model.SpeedPoint(0.5f, 0.5f),
                com.apexstudio.app.domain.model.SpeedPoint(1f, 2f),
            ),
        )
        val vm = EditorViewModel().also { viewModel ->
            viewModel._state.update {
                it.copy(
                    project = Project(id = "p", name = "p", durationMs = 20_000L, clips = listOf(overlay)),
                    durationMs = 20_000L
                )
            }
        }
        val originalRightEdge = overlay.timelineOffsetMs + overlay.effectiveDurationMs()

        vm.trimOverlayClip(overlay.id, 2_500L, overlay.trimEndMs)

        val trimmed = vm.state.value.project!!.clips.single()
        assertEquals(2_500L, trimmed.trimStartMs)
        assertEquals("properties trim must preserve the same right-edge invariant", originalRightEdge,
            trimmed.timelineOffsetMs + trimmed.effectiveDurationMs())
    }

    @Test
    fun simultaneousHeadAndTailTrimDoesNotCountRemovedTailAsHeadMovement() {
        val source = MediaClip("both", "both", "file:///both.mp4", 10_000L,
            trimEndMs = 10_000L, timelineOffsetMs = 6_000L, type = ClipType.OVERLAY, trackIndex = 2)
        val vm = EditorViewModel().also { viewModel ->
            viewModel._state.update { it.copy(project = Project("p", "p", 16_000L,
                clips = listOf(source)), durationMs = 16_000L) }
        }
        vm.trimOverlayClip(source.id, 2_000L, 8_000L)
        val trimmed = vm.state.value.project!!.clips.single()
        assertEquals(8_000L, trimmed.timelineOffsetMs)
        assertEquals(14_000L, trimmed.timelineOffsetMs + trimmed.effectiveDurationMs())
    }

    @Test
    fun expandingRampedHeadPreservesRightEdgeWhenThereIsTimelineRoom() {
        val source = MediaClip("expand", "expand", "file:///expand.mp4", 10_000L,
            trimStartMs = 2_500L, trimEndMs = 10_000L, timelineOffsetMs = 20_000L,
            type = ClipType.OVERLAY, trackIndex = 2, speedPoints = listOf(
                com.apexstudio.app.domain.model.SpeedPoint(0f, .5f),
                com.apexstudio.app.domain.model.SpeedPoint(1f, 2f)))
        val shift = overlayTimelineStartShift(source, 0L)
        val expanded = source.copy(trimStartMs = 0L, timelineOffsetMs = source.timelineOffsetMs + shift)
        assertTrue(shift < 0L)
        assertEquals(source.timelineOffsetMs + source.effectiveDurationMs(),
            expanded.timelineOffsetMs + expanded.effectiveDurationMs())
    }

    @Test
    fun splittingOverlayPreservesSourceDurationWhenRightHalfIsTrimmed() {
        val source = MediaClip(
            id = "overlay-source",
            name = "overlay-source",
            uri = "file:///overlay.mp4",
            durationMs = 12_000L,
            trimStartMs = 2_000L,
            trimEndMs = 12_000L,
            timelineOffsetMs = 6_000L,
            type = ClipType.OVERLAY,
            trackIndex = 2,
        )
        val vm = EditorViewModel().also { viewModel ->
            viewModel._state.update {
                it.copy(
                    project = Project(
                        id = "p",
                        name = "p",
                        durationMs = 20_000L,
                        clips = listOf(source),
                    ),
                    durationMs = 20_000L,
                )
            }
        }

        // Timeline 10s maps to source 6s because this overlay starts at 6s
        // with a 2s source trim-in.
        vm.splitClip(source.id, 10_000L)
        val halvesAfterSplit = vm.state.value.project!!.clips
        val rightHalf = halvesAfterSplit.single { it.id != source.id }
        assertEquals(6_000L, halvesAfterSplit.single { it.id == source.id }.trimEndMs)
        assertEquals(6_000L, rightHalf.trimStartMs)
        assertEquals(12_000L, rightHalf.trimEndMs)
        assertEquals(12_000L, rightHalf.durationMs)
        assertEquals(10_000L, rightHalf.timelineOffsetMs)

        vm.trimClip(rightHalf.id, 7_000L, 11_000L)
        val trimmedRightHalf = vm.state.value.project!!.clips.single { it.id == rightHalf.id }
        assertEquals(7_000L, trimmedRightHalf.trimStartMs)
        assertEquals(11_000L, trimmedRightHalf.trimEndMs)
        assertEquals("trimming the split overlay must not shorten its source-duration ceiling", 12_000L, trimmedRightHalf.durationMs)
        assertTrue(trimmedRightHalf.trimEndMs <= trimmedRightHalf.durationMs)
    }
}
