package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.ChromaKeySettings
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorChromaKeyTargetTest {
    private val video = MediaClip("video", "Video", "file:///video.mp4", 5_000L, trimEndMs = 5_000L)
    private val image = MediaClip("image", "Image", "file:///image.jpg", 5_000L, trimEndMs = 5_000L, type = ClipType.IMAGE)

    @Test fun `chroma key does not fall back to first clip when nothing is selected`() {
        val vm = EditorViewModel()
        val project = Project("p", "Project", 5_000L, clips = listOf(video, image))
        vm._state.value = vm.state.value.copy(project = project, selectedClipId = null)
        val requested = ChromaKeySettings(enabled = true)

        assertFalse(vm.updateChromaKeySettings(requested))
        assertEquals(project, vm.state.value.project)
        assertEquals(ChromaKeySettings(), vm.state.value.chromaKeySettings)
        assertFalse(vm.state.value.activeFxId == "3d_chromakey")
    }

    @Test fun `chroma key only updates the explicitly selected visual clip`() {
        val vm = EditorViewModel()
        val project = Project("p", "Project", 5_000L, clips = listOf(video, image))
        vm._state.value = vm.state.value.copy(project = project, selectedClipId = image.id)
        val requested = ChromaKeySettings(enabled = true, similarity = 0.63f)

        assertTrue(vm.updateChromaKeySettings(requested))
        val clips = vm.state.value.project!!.clips
        assertEquals(video, clips[0])
        assertEquals(requested, clips[1].chromaKeySettings)
        assertEquals(requested, vm.state.value.chromaKeySettings)
        assertNull(clips[0].chromaKeySettings)
    }
}
