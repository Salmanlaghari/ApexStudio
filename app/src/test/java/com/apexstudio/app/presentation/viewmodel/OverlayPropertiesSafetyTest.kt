package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.Keyframe
import com.apexstudio.app.domain.model.KeyframeTrack
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.OverlayBlend
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
class OverlayPropertiesSafetyTest {
    private fun overlay() = MediaClip(
        id = "overlay", name = "overlay", uri = "file:///overlay.mp4", durationMs = 10_000L,
        trimEndMs = 10_000L, type = ClipType.OVERLAY, trackIndex = 2,
        overlayBorderWidth = .02f, overlayShadow = .4f, overlayVolume = .8f,
        keyframes = KeyframeTrack(listOf(Keyframe(id = "kf", timeMs = 1_000L)))
    )

    private fun editor(clip: MediaClip) = EditorViewModel().also { vm ->
        vm._state.update { it.copy(project = Project("p", "p", 10_000L, clips = listOf(clip)),
            durationMs = 10_000L) }
    }

    @Test
    fun propertyAndKeyframeRemovalCallbacksCannotMutateMainVideoOrArmUndo() {
        val clip = overlay().copy(type = ClipType.VIDEO, trackIndex = 0)
        val vm = editor(clip)
        for (id in listOf(clip.id, "missing")) {
            vm.setOverlayBlend(id, OverlayBlend.SCREEN)
            vm.setOverlayBorder(id, .04f, 0xff00ff00.toInt())
            vm.setOverlayShadow(id, .9f)
            vm.setOverlayFlip(id, true, true)
            vm.setOverlayVolume(id, 1.5f)
            vm.removeOverlayKeyframe(id, "kf")
        }
        assertEquals(clip, vm.state.value.project!!.clips.single())
        assertFalse(vm.state.value.canUndo)
        assertFalse(vm.overlayEditUndoArmed)
    }

    @Test
    fun invalidPropertyNumbersRetainFiniteValuesWithoutCreatingUndo() {
        val clip = overlay()
        val vm = editor(clip)
        for (value in listOf(Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY)) {
            vm.setOverlayBorder(clip.id, value, clip.overlayBorderColor)
            vm.setOverlayShadow(clip.id, value)
            vm.setOverlayVolume(clip.id, value)
        }
        assertEquals(clip, vm.state.value.project!!.clips.single())
        assertFalse(vm.state.value.canUndo)
        assertFalse(vm.overlayEditUndoArmed)
    }

    @Test
    fun malformedPersistedNumbersRepairToFiniteDefaultsAndValidValuesClamp() {
        val clip = overlay().copy(overlayBorderWidth = Float.NaN,
            overlayShadow = Float.POSITIVE_INFINITY, overlayVolume = Float.NEGATIVE_INFINITY)
        val vm = editor(clip)
        vm.setOverlayBorder(clip.id, Float.NaN, clip.overlayBorderColor)
        vm.setOverlayShadow(clip.id, Float.NaN)
        vm.setOverlayVolume(clip.id, Float.NaN)
        val repaired = vm.state.value.project!!.clips.single()
        assertEquals(0f, repaired.overlayBorderWidth, 0f)
        assertEquals(0f, repaired.overlayShadow, 0f)
        assertEquals(0f, repaired.overlayVolume, 0f)
        vm.setOverlayBorder(clip.id, 100f, clip.overlayBorderColor)
        vm.setOverlayShadow(clip.id, -100f)
        vm.setOverlayVolume(clip.id, 100f)
        val clamped = vm.state.value.project!!.clips.single()
        assertEquals(.05f, clamped.overlayBorderWidth, 0f)
        assertEquals(0f, clamped.overlayShadow, 0f)
        assertEquals(2f, clamped.overlayVolume, 0f)
    }

    @Test
    fun identicalPropertiesAndMissingKeyframeRemovalDoNotCreateUndo() {
        val clip = overlay()
        val vm = editor(clip)
        vm.setOverlayBlend(clip.id, clip.overlayBlend)
        vm.setOverlayBorder(clip.id, clip.overlayBorderWidth, clip.overlayBorderColor)
        vm.setOverlayShadow(clip.id, clip.overlayShadow)
        vm.setOverlayFlip(clip.id, clip.overlayFlipH, clip.overlayFlipV)
        vm.setOverlayVolume(clip.id, clip.overlayVolume)
        vm.removeOverlayKeyframe(clip.id, "missing")
        assertEquals(clip, vm.state.value.project!!.clips.single())
        assertFalse(vm.state.value.canUndo)
        assertFalse(vm.overlayEditUndoArmed)
    }

    @Test
    fun propertyDragStillHasOneUndoStepAndValidKeyframeDeletionIsUndoable() {
        val clip = overlay()
        val vm = editor(clip)
        vm.setOverlayVolume(clip.id, .9f)
        vm.setOverlayVolume(clip.id, 1.1f)
        vm.setOverlayVolume(clip.id, 1.3f)
        vm.disarmOverlayUndo()
        assertTrue(vm.state.value.canUndo)
        vm.undo()
        assertEquals(clip, vm.state.value.project!!.clips.single())
        assertFalse(vm.state.value.canUndo)
        vm.removeOverlayKeyframe(clip.id, "kf")
        assertTrue(vm.state.value.project!!.clips.single().keyframes.keyframes.isEmpty())
        vm.undo()
        assertEquals(clip, vm.state.value.project!!.clips.single())
    }
}
