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
class EditorTrimPositionTest {
    private fun clip(id: String, duration: Long) = MediaClip(id = id, name = id,
        uri = "file:///$id.mp4", durationMs = duration, trimEndMs = duration, type = ClipType.VIDEO)
    private fun editor(clips: List<MediaClip>) = EditorViewModel().also { vm ->
        vm._state.update { it.copy(project = Project(id = "test", name = "Test", clips = clips, durationMs = 30_000L), durationMs = 30_000L) }
    }

    @Test fun setStartAtPlayheadConvertsSecondClipProjectTime() {
        val first = clip("first", 10_000L)
        val second = clip("second", 20_000L).copy(trimStartMs = 4_000L)
        val vm = editor(listOf(first, second))
        vm._playerPosition.value = 13_000L
        vm.setTrimStartAtPlayhead("second")
        assertEquals(7_000L, vm.state.value.project!!.clips.last().trimStartMs)
        assertEquals(0L, vm.state.value.project!!.clips.first().trimStartMs)
    }

    @Test fun setEndAtPlayheadConvertsOverlayProjectTime() {
        val overlay = clip("overlay", 20_000L).copy(type = ClipType.OVERLAY,
            trackIndex = 1, timelineOffsetMs = 30_000L, trimStartMs = 5_000L)
        val vm = editor(listOf(overlay))
        vm._playerPosition.value = 32_000L
        vm.setTrimEndAtPlayhead("overlay")
        assertEquals(7_000L, vm.state.value.project!!.clips.single().trimEndMs)
    }

    @Test fun setEndOnSub100msClipDoesNotConstructReversedBounds() {
        val vm = editor(listOf(clip("short", 40L)))
        vm._playerPosition.value = 20L
        vm.setTrimEndAtPlayhead("short")
        val result = vm.state.value.project!!.clips.single()
        assertEquals(0L, result.trimStartMs)
        assertEquals(40L, result.trimEndMs)
    }

    @Test fun setEndNearSourceEndKeepsTheMaximumAvailableSpan() {
        val vm = editor(listOf(clip("tail", 1_000L).copy(trimStartMs = 980L)))
        vm._playerPosition.value = 5L
        vm.setTrimEndAtPlayhead("tail")
        val result = vm.state.value.project!!.clips.single()
        assertEquals(980L, result.trimStartMs)
        assertEquals(1_000L, result.trimEndMs)
    }

    @Test fun playheadTrimRejectsEmptySourceWithoutHistoryMutation() {
        val source = clip("empty", 0L)
        val vm = editor(listOf(source))
        vm.setTrimStartAtPlayhead("empty")
        vm.setTrimEndAtPlayhead("empty")
        assertEquals(source, vm.state.value.project!!.clips.single())
        assertTrue(vm.undoStack.isEmpty())
    }

    @Test fun importedMusicKeepsActualSourceDurationAndCapturedPosition() {
        val vm = editor(listOf(clip("main", 10_000L)))
        vm._playerPosition.value = 1_000L
        vm.addAudioTrack("Phone music", "/local/song.mp3", sourceDurationMs = 82_000L, startTimeMs = 12_000L)
        val track = vm.state.value.project!!.audioTracks.single()
        assertEquals(12_000L, track.startTimeMs)
        assertEquals(82_000L, track.trimEndMs)
        assertEquals(82_000L, track.sourceDurationMs)
        assertEquals(track, vm.audio.value.tracks.single())
    }

    @Test fun audioSplitUsesProjectTimeAndKeepsSecondHalfAtCut() {
        val vm = editor(listOf(clip("main", 10_000L)))
        vm.addAudioTrack("Music", "/local/music.mp3", sourceDurationMs = 20_000L, startTimeMs = 30_000L)
        val original = vm.state.value.project!!.audioTracks.single()
        vm.trimAudioTrackPlacement(original.id, 4_000L, 16_000L, 30_000L)
        vm.splitAudioTrack(original.id, 35_000L)
        val tracks = vm.state.value.project!!.audioTracks
        assertEquals(2, tracks.size)
        assertEquals(9_000L, tracks.first().trimEndMs)
        assertEquals(9_000L, tracks.last().trimStartMs)
        assertEquals(35_000L, tracks.last().startTimeMs)
        assertEquals(20_000L, tracks.last().sourceDurationMs)
        assertEquals(tracks, vm.audio.value.tracks)
    }
}
