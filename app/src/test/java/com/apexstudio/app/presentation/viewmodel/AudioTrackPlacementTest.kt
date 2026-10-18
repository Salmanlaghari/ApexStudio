package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.domain.model.Project
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioTrackPlacementTest {
    private fun track() = AudioTrack(
        id = "music-1",
        name = "Music",
        uri = "file:///music.wav",
        trimStartMs = 2_000L,
        trimEndMs = 8_000L,
        sourceDurationMs = 10_000L,
        startTimeMs = 5_000L
    )

    @Test
    fun moveChangesTimelinePlacementWithoutChangingSourceRange() {
        val moved = AudioTrackPlacement.moved(track(), 12_500L)
        assertEquals(12_500L, moved.startTimeMs)
        assertEquals(2_000L, moved.trimStartMs)
        assertEquals(8_000L, moved.trimEndMs)
    }

    @Test
    fun leftEdgeTrimMovesTimelineStartAndSourceInPointTogether() {
        val trimmed = AudioTrackPlacement.trimmed(track(), 3_500L, 8_000L, 6_500L)
        assertEquals(3_500L, trimmed.trimStartMs)
        assertEquals(8_000L, trimmed.trimEndMs)
        assertEquals(6_500L, trimmed.startTimeMs)
        assertEquals(10_000L, trimmed.sourceDurationMs)
    }

    @Test
    fun rightEdgeTrimOnlyChangesSourceOutPoint() {
        val trimmed = AudioTrackPlacement.trimmed(track(), 2_000L, 6_250L, 5_000L)
        assertEquals(2_000L, trimmed.trimStartMs)
        assertEquals(6_250L, trimmed.trimEndMs)
        assertEquals(5_000L, trimmed.startTimeMs)
    }

    @Test
    fun legacyTrackUsesTrimEndAsSourceCeiling() {
        val legacy = track().copy(sourceDurationMs = 0L)
        val trimmed = AudioTrackPlacement.trimmed(legacy, 2_000L, 7_500L, 5_000L)
        assertEquals(8_000L, trimmed.sourceDurationMs)
        assertEquals(7_500L, trimmed.trimEndMs)
    }

    @Test
    fun timelineMoveDualWritesProjectAndMixerAndExtendsDuration() {
        val original = track()
        val vm = EditorViewModel().also { viewModel ->
            viewModel._state.update {
                it.copy(
                    project = Project(id = "p", name = "p", durationMs = 10_000L, audioTracks = listOf(original)),
                    durationMs = 10_000L
                )
            }
            viewModel._audio.update { it.copy(tracks = listOf(original)) }
        }

        vm.moveAudioTrack(original.id, 12_500L)

        assertEquals(12_500L, vm.state.value.project!!.audioTracks.single().startTimeMs)
        assertEquals(12_500L, vm.audio.value.tracks.single().startTimeMs)
        assertEquals(18_500L, vm.state.value.durationMs)
        assertEquals(18_500L, vm.state.value.project!!.durationMs)
    }

    @Test
    fun timelineDurationUsesPlacedAudioSpanNotSourceOutPoint() {
        assertEquals(11_000L, computeTimelineDurationMs(emptyList(), listOf(track())))
    }
}
