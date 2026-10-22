package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.Keyframe
import com.apexstudio.app.domain.model.KeyframeParam
import com.apexstudio.app.domain.model.KeyframeTrack
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FramePreciseKeyframeControllerTest {
    private fun viewModel(track: KeyframeTrack = KeyframeTrack()): EditorViewModel {
        val vm = EditorViewModel()
        val clip = MediaClip(
            id = "clip",
            name = "Clip",
            uri = "file:///clip.mp4",
            durationMs = 5_000L,
            trimEndMs = 5_000L,
            keyframes = track
        )
        vm._state.value = vm.state.value.copy(
            project = Project("project", "Project", 5_000L, fps = 60, clips = listOf(clip)),
            durationMs = 5_000L
        )
        return vm
    }

    private fun EditorViewModel.keyframes() =
        state.value.project!!.clips.single().keyframes.keyframes

    @Test
    fun `frame upsert keeps adjacent 60fps frames separate and samples other transform values`() {
        val first = Keyframe(
            id = "first",
            timeMs = 0L,
            translateX = 10f,
            translateY = 2f,
            scale = 2f,
            rotationDeg = 5f,
            opacity = 0.8f,
            volume = 0.7f,
            effectIntensity = 0.6f,
            filterIntensity = 0.5f,
            saturation = 0.4f
        )
        val last = first.copy(id = "last", timeMs = 2_000L, translateX = 30f, scale = 4f)
        val vm = viewModel(KeyframeTrack(listOf(first, last)))

        assertTrue(vm.upsertFrameKeyframeParam("clip", 1_000L, KeyframeParam.SCALE, 5f))
        assertTrue(vm.upsertFrameKeyframeParam("clip", 1_017L, KeyframeParam.SCALE, 6f))

        val adjacent = vm.keyframes().filter { it.timeMs == 1_000L || it.timeMs == 1_017L }.sortedBy { it.timeMs }
        assertEquals(2, adjacent.size)
        assertEquals(5f, adjacent[0].scale, 0f)
        assertEquals(6f, adjacent[1].scale, 0f)
        assertEquals(20f, adjacent[0].translateX, 0f)
        assertEquals(2f, adjacent[0].translateY, 0f)
        assertEquals(5f, adjacent[0].rotationDeg, 0f)
        assertEquals(0.8f, adjacent[0].opacity, 0f)
        assertEquals(0.7f, adjacent[0].volume, 0f)
        assertEquals(0.6f, adjacent[0].effectIntensity, 0f)
        assertEquals(0.5f, adjacent[0].filterIntensity, 0f)
        assertEquals(0.4f, adjacent[0].saturation!!, 0f)
    }

    @Test
    fun `frame toggle adds and removes only a keyframe in the exact project frame`() {
        val vm = viewModel(KeyframeTrack(listOf(Keyframe.identity(1_000L, "frame-60"))))
        vm._playerPosition.value = 1_017L // frame 61, only 17ms from frame 60

        assertTrue(vm.toggleFrameKeyframeAtPlayheadFor("clip"))
        assertEquals(listOf(1_000L, 1_017L), vm.keyframes().map { it.timeMs }.sorted())

        assertTrue(vm.toggleFrameKeyframeAtPlayheadFor("clip"))
        assertEquals(listOf(1_000L), vm.keyframes().map { it.timeMs })
        assertFalse(vm.toggleFrameKeyframeAtPlayheadFor("missing"))
    }

    @Test
    fun `frame upsert updates only the matching frame preserving other fields and is undoable`() {
        val original = Keyframe(
            id = "existing",
            timeMs = 1_000L,
            translateX = 8f,
            translateY = -3f,
            scale = 1.5f,
            rotationDeg = 22f,
            opacity = 0.75f,
            volume = 0.25f,
            effectIntensity = 0.6f,
            filterIntensity = 0.7f,
            saturation = 0.35f
        )
        val adjacent = Keyframe.identity(1_017L, "adjacent")
        val vm = viewModel(KeyframeTrack(listOf(original, adjacent)))

        assertTrue(vm.upsertFrameKeyframeParam("clip", 1_000L, KeyframeParam.ROTATION, 90f))
        val changed = vm.keyframes().first { it.id == "existing" }
        assertEquals(90f, changed.rotationDeg, 0f)
        assertEquals(8f, changed.translateX, 0f)
        assertEquals(-3f, changed.translateY, 0f)
        assertEquals(1.5f, changed.scale, 0f)
        assertEquals(0.75f, changed.opacity, 0f)
        assertEquals(0.25f, changed.volume, 0f)
        assertEquals(0.6f, changed.effectIntensity, 0f)
        assertEquals(0.7f, changed.filterIntensity, 0f)
        assertEquals(0.35f, changed.saturation!!, 0f)
        assertEquals(adjacent, vm.keyframes().first { it.id == "adjacent" })

        assertTrue(vm.state.value.canUndo)
        vm.undo()
        assertEquals(original, vm.keyframes().first { it.id == "existing" })
        assertEquals(adjacent, vm.keyframes().first { it.id == "adjacent" })
    }

    @Test
    fun `frame step pauses playback before moving the playhead`() {
        val vm = viewModel().apply {
            _state.value = state.value.copy(isPlaying = true)
            _playerPosition.value = 42L
        }

        vm.stepFrame(forward = true)

        assertFalse(vm.state.value.isPlaying)
        assertEquals(50L, vm.playerPositionFlow.value)
        assertEquals(50L, vm.state.value.playerPositionMs)
    }
}
