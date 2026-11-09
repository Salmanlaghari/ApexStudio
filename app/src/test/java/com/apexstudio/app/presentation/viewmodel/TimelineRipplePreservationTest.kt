package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.domain.model.StickerOverlay
import com.apexstudio.app.data.filter.GpuColorProfiles
import com.apexstudio.app.data.filter.GpuFilterConfig
import com.apexstudio.app.data.filter.StylisticEffectType
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun `constant-speed V1 trim ripples by output duration at 2x and half speed`() {
        val fast = vm(video("fast").copy(speedMultiplier = 2f), overlay("fast-after", 6_000L))
        fast.trimClip("fast", 0L, 6_000L)
        assertEquals(3_000L, fast.state.value.project!!.clips.first { it.id == "fast" }.effectiveDurationMs())
        assertEquals(4_000L, fast.offset("fast-after"))

        val slow = vm(video("slow").copy(speedMultiplier = 0.5f), overlay("slow-after", 22_000L))
        slow.trimClip("slow", 0L, 6_000L)
        assertEquals(12_000L, slow.state.value.project!!.clips.first { it.id == "slow" }.effectiveDurationMs())
        assertEquals(14_000L, slow.offset("slow-after"))
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
    fun `constant-speed V1 split converts project time to source at 2x and half speed`() {
        val fast = vm(video("fast").copy(speedMultiplier = 2f))
        fast.splitClip("fast", 2_500L)
        val fastHalves = fast.state.value.project!!.clips
        assertEquals(5_000L, fastHalves.first { it.id == "fast" }.trimEndMs)
        assertEquals(5_000L, fastHalves.last().trimStartMs)
        assertEquals(2_500L, fastHalves[0].effectiveDurationMs())
        assertEquals(2_500L, fastHalves[1].effectiveDurationMs())
        assertEquals(10_000L, fastHalves[1].durationMs)

        val slow = vm(video("slow").copy(speedMultiplier = 0.5f))
        slow.splitClip("slow", 5_000L)
        val slowHalves = slow.state.value.project!!.clips
        assertEquals(2_500L, slowHalves.first { it.id == "slow" }.trimEndMs)
        assertEquals(2_500L, slowHalves.last().trimStartMs)
        assertEquals(5_000L, slowHalves[0].effectiveDurationMs())
        assertEquals(15_000L, slowHalves[1].effectiveDurationMs())
        assertEquals(10_000L, slowHalves[1].durationMs)
    }

    @Test
    fun `overlay trim and delete never ripple another overlay`() {
        val vm = vm(video("v1"), overlay("edit", 3_000L), overlay("other", 12_000L))
        vm.trimClip("edit", 1_000L, 4_000L)
        assertEquals(12_000L, vm.offset("other"))
        vm.deleteClip("edit")
        assertEquals(12_000L, vm.offset("other"))
    }


    @Test
    fun `bulk delete ripples overlays and one undo restores selection targets`() {
        val vm = vm(video("first"), video("second"), overlay("at", 20_000L))
        vm._state.update { it.copy(selectedClipIds = setOf("second"), multiSelectMode = true) }
        vm.deleteSelectedClips()
        assertEquals(10_000L, vm.offset("at"))
        assertEquals(listOf("first", "at"), vm.state.value.project!!.clips.map { it.id })
        vm.undo()
        assertEquals(listOf("first", "second", "at"), vm.state.value.project!!.clips.map { it.id })
    }

    @Test
    fun `bulk duplicate appends V1 copies, ripples once, and regenerates child ids`() {
        val source = video("source").copy(
            speedMultiplier = 2f,
            textOverlays = listOf(TextOverlay(id = "title")),
            stickers = listOf(StickerOverlay(id = "sticker"))
        )
        val vm = vm(source, video("tail"), overlay("downstream", 20_000L))
        vm._state.update { it.copy(selectedClipIds = setOf("source"), multiSelectMode = true) }
        vm.duplicateSelectedClips()
        val clips = vm.state.value.project!!.clips
        val copy = clips.last { it.id != "source" && it.id != "tail" && it.id != "downstream" }
        assertEquals(25_000L, clips.first { it.id == "downstream" }.timelineOffsetMs)
        assertEquals(source.effectiveDurationMs(), copy.effectiveDurationMs())
        assertEquals(setOf("title"), source.textOverlays.map { it.id }.toSet())
        assertEquals(setOf("sticker"), source.stickers.map { it.id }.toSet())
        assertTrue(copy.textOverlays.map { it.id }.none { it == "title" })
        assertTrue(copy.stickers.map { it.id }.none { it == "sticker" })
        vm.undo()
        assertEquals(3, vm.state.value.project!!.clips.size)
    }

    @Test
    fun `bulk speed filter and LUT persist as one selection action each`() {
        val vm = vm(video("one"), video("two"))
        vm._state.update { it.copy(selectedClipIds = setOf("one", "two"), multiSelectMode = true) }
        vm.applySpeedToSelectedClips(1.5f)
        assertTrue(vm.state.value.project!!.clips.all { it.speedMultiplier == 1.5f && it.speedPoints.isEmpty() })
        val filter = GpuFilterConfig(stylisticEffect = StylisticEffectType.SEPIA)
        vm.applyFilterToSelectedClips(filter)
        assertTrue(vm.state.value.project!!.clips.all { it.gpuFilterConfig == filter })
        vm.applyLutToSelectedClips(GpuColorProfiles.CINEMATIC.id, 0.7f)
        assertTrue(vm.state.value.project!!.clips.all { it.gpuFilterConfig.activeProfileId == GpuColorProfiles.CINEMATIC.id })
    }
    @Test
    fun `grouped selection keeps clips together during reorder`() {
        val vm = vm(video("a"), video("b"), video("tail"))
        vm._state.update { it.copy(selectedClipIds = setOf("a", "b"), multiSelectMode = true) }
        assertTrue(vm.groupSelectedClips())
        val groupIds = vm.state.value.project!!.clips.filter { it.id == "a" || it.id == "b" }.map { it.groupId }.toSet()
        assertEquals(1, groupIds.size)
        vm.reorderClips(0, 2)
        assertEquals(listOf("tail", "a", "b"), vm.state.value.project!!.clips.map { it.id })
    }

}
