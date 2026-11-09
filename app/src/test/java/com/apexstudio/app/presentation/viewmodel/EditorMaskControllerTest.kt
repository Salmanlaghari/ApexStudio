package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.MaskKeyframe
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.domain.model.VideoMask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorMaskControllerTest {
    private fun editor(mask: VideoMask): EditorViewModel {
        val clip = MediaClip("video", "Video", "file:///source.mp4", 2_000L, trimEndMs = 2_000L, mask = mask)
        return EditorViewModel().also { vm ->
            vm._playerPosition.value = 500L
            vm._state.value = vm.state.value.copy(
                selectedClipId = clip.id,
                project = Project("project", "Project", 2_000L, clips = listOf(clip))
            )
        }
    }

    @Test
    fun staticMaskDragUpdatesBaseGeometryAndClearsPresetSelection() {
        val vm = editor(VideoMask(centerX = .5f, centerY = .5f, sizeX = .6f, sizeY = .6f, presetId = "oval"))

        vm.setMaskCenter("video", .2f, .3f, persist = false)
        vm.setMaskSize("video", .4f, .45f, persist = true)

        val result = vm.state.value.project!!.clips.single().mask!!
        assertEquals(.2f, result.centerX, .0001f)
        assertEquals(.3f, result.centerY, .0001f)
        assertEquals(.4f, result.sizeX, .0001f)
        assertEquals(.45f, result.sizeY, .0001f)
        assertNull(result.presetId)
    }

    @Test
    fun animatedMaskDragWritesResolvedGeometryAtPlayheadAndPreservesOtherKeys() {
        val start = MaskKeyframe(
            id = "start", timeMs = 0L, centerX = .2f, centerY = .3f,
            sizeX = .4f, sizeY = .5f, feather = .2f, opacity = .6f
        )
        val end = MaskKeyframe(
            id = "end", timeMs = 1_000L, centerX = .8f, centerY = .7f,
            sizeX = .8f, sizeY = .9f, feather = .8f, opacity = 1f, invert = true
        )
        val original = VideoMask(centerX = .45f, centerY = .55f, presetId = "oval", maskKeyframes = listOf(start, end))
        val vm = editor(original)
        vm._playerPosition.value = 750L // The UI mirror remains at 500 ms while playback advances.

        vm.setMaskCenter("video", .25f, .35f, persist = false, atMs = 500L)
        vm.setMaskSize("video", .3f, .4f, persist = true, atMs = 500L)

        val result = vm.state.value.project!!.clips.single().mask!!
        assertEquals("base geometry stays intact for animated masks", .45f, result.centerX, .0001f)
        assertEquals("base geometry stays intact for animated masks", .55f, result.centerY, .0001f)
        assertEquals("preset marker clears after a manual edit", null, result.presetId)
        assertEquals(3, result.maskKeyframes.size)
        assertEquals(start, result.maskKeyframes.first())
        assertEquals(end, result.maskKeyframes.last())
        val edited = result.maskKeyframes.singleOrNull { it.timeMs == 500L }
        assertNotNull("drag adds/updates a key exactly at the playhead", edited)
        assertEquals(.25f, edited!!.centerX, .0001f)
        assertEquals(.35f, edited.centerY, .0001f)
        assertEquals(.3f, edited.sizeX, .0001f)
        assertEquals(.4f, edited.sizeY, .0001f)
        assertEquals("non-geometry values are pinned from the interpolated mask", .5f, edited.feather, .0001f)
        assertEquals(.8f, edited.opacity, .0001f)
        assertEquals("boolean key interpolation crosses at the midpoint", true, edited.invert)
    }
}
