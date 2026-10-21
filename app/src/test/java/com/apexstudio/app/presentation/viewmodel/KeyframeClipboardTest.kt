package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.*
import kotlinx.coroutines.flow.update
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyframeClipboardTest {
    private val original = Keyframe("original", 100L, scale = 1.7f, rotationDeg = 42f,
        opacity = .6f, volume = .4f, effectIntensity = .3f, filterIntensity = .2f,
        saturation = .8f, curve = KeyframeCurve.EASE_IN)
    private fun editor() = EditorViewModel().apply {
        _state.update { it.copy(project = Project(id = "clipboard", name = "Clipboard", durationMs = 5_000L,
            clips = listOf(MediaClip(id = "clip", name = "Video", uri = "file:///video.mp4",
                durationMs = 5_000L, trimEndMs = 5_000L, keyframes = KeyframeTrack(listOf(original)))))) }
    }
    @Test fun pasteRetainsEveryValueAndCreatesAnIndependentPin() {
        val vm = editor()
        vm.pasteKeyframe("clip", original, 2_000L)
        val pins = vm.state.value.project!!.clips.single().keyframes.keyframes
        assertEquals(2, pins.size)
        val copy = pins.last()
        assertNotEquals(original.id, copy.id)
        assertEquals(original.copy(id = copy.id, timeMs = 2_000L), copy)
        assertTrue(vm.state.value.canUndo)
        assertEquals(original, pins.first())
    }
    @Test fun pasteReplacesOnlyExactTimeCollisionAndKeepsIdentity() {
        val vm = editor()
        vm.pasteKeyframe("clip", original.copy(scale = 2f), 100L)
        val pin = vm.state.value.project!!.clips.single().keyframes.keyframes.single()
        assertEquals("original", pin.id)
        assertEquals(2f, pin.scale)
    }
    @Test fun missingTargetDoesNotMutateProject() {
        val vm = editor()
        val before = vm.state.value.project
        vm.pasteKeyframe("missing", original, 900L)
        assertEquals(before, vm.state.value.project)
        assertFalse(vm.state.value.canUndo)
    }
    @Test fun addCopiesAudioAndEffectValuesAlongsideTransform() {
        val vm = editor()
        val transform = AnimatedTransform(.1f, .2f, 1.4f, 20f, .8f, .7f, .6f, .5f, .4f)
        vm.addKeyframe("clip", 800L, transform)
        val pin = vm.state.value.project!!.clips.single().keyframes.keyframes.last()
        assertEquals(.7f, pin.volume)
        assertEquals(.6f, pin.effectIntensity)
        assertEquals(.5f, pin.filterIntensity)
        assertEquals(.4f, pin.saturation!!)
    }
}
