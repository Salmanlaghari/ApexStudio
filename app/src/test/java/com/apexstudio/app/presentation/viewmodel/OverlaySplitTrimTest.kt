package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlaySplitTrimTest {
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
