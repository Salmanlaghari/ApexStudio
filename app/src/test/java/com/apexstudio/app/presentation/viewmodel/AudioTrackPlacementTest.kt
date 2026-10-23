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
    fun overlongHeadTrimDragPreservesRightEdgeAtClampedSourcePoint() {
        val original = track()
        val clampedSourceStart = 7_750L // 250ms before the fixed source tail
        val timelineStart = AudioTrackPlacement.timelineStartAfterHeadTrim(original, clampedSourceStart)

        val trimmed = AudioTrackPlacement.trimmed(
            original, clampedSourceStart, original.trimEndMs, timelineStart
        )

        assertEquals(10_750L, trimmed.startTimeMs)
        assertEquals(11_000L, trimmed.startTimeMs + trimmed.trimEndMs - trimmed.trimStartMs)
    }

    @Test
    fun headTrimPlacementSaturatesRatherThanWrappingToTimelineZero() {
        val original = track().copy(startTimeMs = Long.MAX_VALUE - 100L)
        assertEquals(Long.MAX_VALUE, AudioTrackPlacement.timelineStartAfterHeadTrim(original, 3_000L))
        assertEquals(Long.MAX_VALUE - 1_100L, AudioTrackPlacement.timelineStartAfterHeadTrim(original, 1_000L))
    }

    @Test
    fun headTrimBeforeSourceZeroUsesEffectiveSourceDelta() {
        val original = track()
        val placement = AudioTrackPlacement.timelineStartAfterHeadTrim(original, -5_000L)
        val trimmed = AudioTrackPlacement.trimmed(original, -5_000L, original.trimEndMs, placement)
        assertEquals(3_000L, placement)
        assertEquals(11_000L, trimmed.startTimeMs + trimmed.trimEndMs - trimmed.trimStartMs)
    }

    @Test
    fun extendingAudioHeadAtTimelineZeroDoesNotMoveTheRightEdge() {
        val original = track().copy(startTimeMs = 1_000L, trimStartMs = 3_000L)
        val originalRightEdge = original.startTimeMs + original.trimEndMs - original.trimStartMs

        // Asking for a 2s head expansion would place this clip at -1s.
        // Placement clamps to zero, so only 1s of source can be restored.
        val trimmed = AudioTrackPlacement.trimmed(original, 1_000L, 8_000L, 0L)

        assertEquals(2_000L, trimmed.trimStartMs)
        assertEquals(0L, trimmed.startTimeMs)
        assertEquals(originalRightEdge, trimmed.startTimeMs + trimmed.trimEndMs - trimmed.trimStartMs)
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
    fun removingLongestAudioRecomputesDurationAndClampsPlayhead() {
        val longest = track().copy(startTimeMs = 12_500L)
        val remaining = track().copy(id = "music-2", startTimeMs = 1_000L, trimStartMs = 0L, trimEndMs = 2_000L)
        val vm = EditorViewModel().also { viewModel ->
            viewModel._state.update {
                it.copy(
                    project = Project(id = "p", name = "p", durationMs = 18_500L, audioTracks = listOf(longest, remaining)),
                    durationMs = 18_500L,
                    playerPositionMs = 16_000L,
                    currentTimeMs = 16_000L
                )
            }
            viewModel._audio.update { it.copy(tracks = listOf(longest, remaining)) }
            viewModel._playerPosition.update { 16_000L }
        }

        vm.removeAudioTrack(longest.id)

        assertEquals(3_000L, vm.state.value.durationMs)
        assertEquals(3_000L, vm.state.value.project!!.durationMs)
        assertEquals(3_000L, vm.state.value.playerPositionMs)
        assertEquals(3_000L, vm.state.value.currentTimeMs)
        assertEquals(3_000L, vm.playerPositionFlow.value)
        assertEquals(listOf(remaining), vm.state.value.project!!.audioTracks)
        assertEquals(listOf(remaining), vm.audio.value.tracks)
    }

    @Test
    fun timelineDurationUsesPlacedAudioSpanNotSourceOutPoint() {
        assertEquals(11_000L, computeTimelineDurationMs(emptyList(), listOf(track())))
    }

    @Test
    fun addingVoiceoverDoesNotCountTrimmedOffSourceAsTimelineDuration() {
        val existing = track()
        val vm = EditorViewModel().also { viewModel ->
            viewModel._state.update {
                it.copy(
                    project = Project(id = "p", name = "p", durationMs = 13_000L, audioTracks = listOf(existing)),
                    durationMs = 13_000L
                )
            }
            viewModel._audio.update { it.copy(tracks = listOf(existing)) }
            viewModel.voiceOverStartMs = 0L
        }

        vm.addVoiceOverTrack(uri = "/voice/take.m4a", durationMs = 1_000L, name = "Take")

        assertEquals(11_000L, vm.state.value.durationMs)
        assertEquals(11_000L, vm.state.value.project!!.durationMs)
        assertEquals(vm.state.value.project!!.audioTracks, vm.audio.value.tracks)
    }

    @Test
    fun addingVoiceoverPastCurrentEndPersistsExtendedProjectDuration() {
        val vm = EditorViewModel().also { viewModel ->
            viewModel._state.update {
                it.copy(
                    project = Project(id = "p", name = "p", durationMs = 1_000L),
                    durationMs = 1_000L
                )
            }
            viewModel.voiceOverStartMs = 12_000L
        }

        vm.addVoiceOverTrack(uri = "/voice/take.m4a", durationMs = 4_000L, name = "Take")

        assertEquals(16_000L, vm.state.value.durationMs)
        assertEquals(16_000L, vm.state.value.project!!.durationMs)
        assertEquals(12_000L, vm.state.value.project!!.audioTracks.single().startTimeMs)
    }
}
