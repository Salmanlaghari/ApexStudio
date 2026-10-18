package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Locks the already-merged PR #161 behavior; Phase 0 must not redesign ripple. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimelineRipplePreservationTest {
    private fun video(id: String) = MediaClip(
        id = id, name = id, uri = "file:///$id.mp4", durationMs = 10_000L,
        trimEndMs = 10_000L, type = ClipType.VIDEO
    )

    private fun overlay(id: String, offset: Long) = video(id).copy(
        type = ClipType.OVERLAY, trackIndex = 2, timelineOffsetMs = offset
    )

    private fun vm(vararg clips: MediaClip) = EditorViewModel().also { vm ->
        vm._state.update {
            it.copy(project = Project(id = "p", name = "p", durationMs = 40_000L,
                clips = clips.toList()), durationMs = 40_000L)
        }
    }

    private fun EditorViewModel.offset(id: String) =
        state.value.project!!.clips.first { it.id == id }.timelineOffsetMs

    @Test
    fun `V1 trim shifts overlays at and after boundary but not before it`() {
        val vm = vm(video("v1"), overlay("before", 4_000L),
            overlay("at", 5_000L), overlay("after", 12_000L))
        vm.trimClip("v1", 0L, 5_000L)
        assertEquals(4_000L, vm.offset("before"))
        assertEquals(0L, vm.offset("at"))
        assertEquals(7_000L, vm.offset("after"))
    }

    @Test
    fun `V1 delete ripples from cumulative clip start and clamps offsets to zero`() {
        val vm = vm(video("first"), video("second"), overlay("before", 5_000L),
            overlay("at", 10_000L), overlay("after", 25_000L))
        vm.deleteClip("second")
        assertEquals(5_000L, vm.offset("before"))
        assertEquals(0L, vm.offset("at"))
        assertEquals(15_000L, vm.offset("after"))
    }

    @Test
    fun `V1 duplicate preserves existing append-boundary ripple behavior`() {
        val vm = vm(video("first"), video("second"), overlay("before", 19_000L),
            overlay("at", 20_000L), overlay("after", 25_000L))
        vm.duplicateClip("first")
        assertEquals(19_000L, vm.offset("before"))
        assertEquals(30_000L, vm.offset("at"))
        assertEquals(35_000L, vm.offset("after"))
        assertEquals("first", vm.state.value.project!!.clips.last().name)
    }

    @Test
    fun `V1 split does not ripple overlays`() {
        val vm = vm(video("v1"), overlay("overlay", 12_000L))
        vm.splitClip("v1", 5_000L)
        assertEquals(12_000L, vm.offset("overlay"))
    }

    @Test
    fun `overlay trim and delete never ripple another overlay`() {
        val vm = vm(video("v1"), overlay("edit", 3_000L), overlay("other", 12_000L))
        vm.trimClip("edit", 1_000L, 4_000L)
        assertEquals(12_000L, vm.offset("other"))
        vm.deleteClip("edit")
        assertEquals(12_000L, vm.offset("other"))
    }
}
