package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.presentation.state.EditorTool
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression tests for the EditorViewModel controller split.
 *
 * The god-class `EditorViewModel` was divided into cohesive controller
 * files holding extension functions (`EditorProjectController`,
 * `EditorTimelineController`, `EditorFilterController`, …). These tests
 * drive the public API of the extracted controllers through
 * [EditorViewModel] to prove the pure code-move preserved behavior.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorViewModelTest {

    private fun viewModel() = EditorViewModel()

    @Test
    fun `togglePlay flips isPlaying`() {
        val vm = viewModel()
        assertFalse(vm.state.value.isPlaying)
        vm.togglePlay()
        assertTrue(vm.state.value.isPlaying)
        vm.togglePlay()
        assertFalse(vm.state.value.isPlaying)
    }

    @Test
    fun `setZoom coerces into allowed range`() {
        val vm = viewModel()
        vm.setZoom(10f)
        assertEquals(4f, vm.state.value.zoomLevel, 0.001f)
        vm.setZoom(0.01f)
        assertEquals(0.2f, vm.state.value.zoomLevel, 0.001f)
        vm.setZoom(2.5f)
        assertEquals(2.5f, vm.state.value.zoomLevel, 0.001f)
    }

    @Test
    fun `selectTool updates selectedTool`() {
        val vm = viewModel()
        vm.selectTool(EditorTool.AUDIO)
        assertEquals(EditorTool.AUDIO, vm.state.value.selectedTool)
    }

    @Test
    fun `openFilterPanel and closeFilterPanel toggle the GPU filter panel`() {
        val vm = viewModel()
        assertFalse(vm.state.value.gpuFilterPanelOpen)
        vm.openFilterPanel()
        assertTrue(vm.state.value.gpuFilterPanelOpen)
        vm.closeFilterPanel()
        assertFalse(vm.state.value.gpuFilterPanelOpen)
    }

    @Test
    fun `setPlaybackSpeed coerces into allowed range`() {
        val vm = viewModel()
        vm.setPlaybackSpeed(100f)
        assertEquals(10f, vm.state.value.playbackSpeed, 0.001f)
        vm.setPlaybackSpeed(0.001f)
        assertEquals(0.1f, vm.state.value.playbackSpeed, 0.001f)
        vm.setPlaybackSpeed(1.5f)
        assertEquals(1.5f, vm.state.value.playbackSpeed, 0.001f)
    }

    @Test
    fun `audio track add undo and redo restore the full project and mixer state`() {
        val vm = viewModel()
        val clip = MediaClip("clip", "Clip", "file:///clip.mp4", 10_000L, trimEndMs = 10_000L)
        vm._state.value = vm.state.value.copy(
            project = Project("project", "Project", 10_000L, clips = listOf(clip)),
            durationMs = 10_000L
        )

        vm.addAudioTrack("Music", "file:///music.mp3", sourceDurationMs = 5_000L)
        val addedTrack = vm.state.value.project!!.audioTracks.single()
        assertEquals(listOf(addedTrack), vm.audio.value.tracks)

        vm.undo()
        assertTrue(vm.state.value.project!!.audioTracks.isEmpty())
        assertTrue(vm.audio.value.tracks.isEmpty())
        vm.redo()
        assertEquals(listOf(addedTrack), vm.state.value.project!!.audioTracks)
        assertEquals(listOf(addedTrack), vm.audio.value.tracks)
    }

    @Test
    fun `layer visibility undo and redo restore editor-only layer state`() {
        val vm = viewModel()
        val clip = MediaClip("clip", "Clip", "file:///clip.mp4", 10_000L, trimEndMs = 10_000L)
        vm._state.value = vm.state.value.copy(
            project = Project("project", "Project", 10_000L, clips = listOf(clip)),
            durationMs = 10_000L
        )

        vm.toggleVideoLayerVisibility(2)
        assertEquals(setOf(2), vm.state.value.hiddenVideoLayers)
        vm.undo()
        assertTrue(vm.state.value.hiddenVideoLayers.isEmpty())
        vm.redo()
        assertEquals(setOf(2), vm.state.value.hiddenVideoLayers)
    }

    @Test
    fun `text overlay add undo and redo restore the selected clip content`() {
        val vm = viewModel()
        val clip = MediaClip("clip", "Clip", "file:///clip.mp4", 10_000L, trimEndMs = 10_000L)
        vm._state.value = vm.state.value.copy(
            project = Project("project", "Project", 10_000L, clips = listOf(clip)),
            durationMs = 10_000L
        )

        vm.addTextOverlay("clip", text = "Title")
        assertEquals("Title", vm.state.value.project!!.clips.single().textOverlays.single().text)
        vm.undo()
        assertTrue(vm.state.value.project!!.clips.single().textOverlays.isEmpty())
        vm.redo()
        assertEquals("Title", vm.state.value.project!!.clips.single().textOverlays.single().text)
    }

    @Test
    fun `updateColorBrightness writes through to color state`() {
        val vm = viewModel()
        vm.updateColorBrightness(0.5f)
        assertEquals(0.5f, vm.color.value.brightness, 0.001f)
    }

    @Test
    fun `selectLut updates the selected LUT`() {
        val vm = viewModel()
        vm.selectLut("hollywood")
        assertEquals("hollywood", vm.color.value.selectedLut)
    }

    @Test
    fun `setFilterCategory and setActiveFilter update filter state`() {
        val vm = viewModel()
        vm.setFilterCategory("Cinematic")
        assertEquals("Cinematic", vm.state.value.filterCategory)
        vm.setActiveFilter("hollywood")
        assertEquals("hollywood", vm.state.value.activeFilterId)
    }

    @Test
    fun `findNearestBeat returns timeMs when snapToBeat is disabled`() {
        val vm = viewModel()
        assertFalse(vm.state.value.snapToBeat)
        assertEquals(12345L, vm.findNearestBeat(12345L))
    }

    @Test
    fun `setLutTemperature coerces into kelvin range`() {
        val vm = viewModel()
        vm.setLutTemperature(50000f)
        assertEquals(9000f, vm.state.value.lutTemperature, 0.001f)
        vm.setLutTemperature(100f)
        assertEquals(2000f, vm.state.value.lutTemperature, 0.001f)
    }
}
