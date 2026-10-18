package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.AudioTrack
import kotlinx.coroutines.flow.update

/** Pure placement math shared by the timeline controller and regression tests. */
object AudioTrackPlacement {
    fun moved(track: AudioTrack, timelineStartMs: Long): AudioTrack =
        track.copy(startTimeMs = timelineStartMs.coerceAtLeast(0L))

    /**
     * Applies the two independent coordinate systems of an audio clip:
     * source trim bounds and project-time placement. A left-edge trim advances
     * both the source in-point and timeline start, preserving the right edge.
     */
    fun trimmed(
        track: AudioTrack,
        sourceStartMs: Long,
        sourceEndMs: Long,
        timelineStartMs: Long
    ): AudioTrack {
        val sourceEnd = track.sourceEndMs.coerceAtLeast(1L)
        val minSpan = 1L
        val start = sourceStartMs.coerceIn(0L, (sourceEnd - minSpan).coerceAtLeast(0L))
        val end = sourceEndMs.coerceIn(start + minSpan, sourceEnd.coerceAtLeast(start + minSpan))
        return track.copy(
            trimStartMs = start,
            trimEndMs = end,
            startTimeMs = timelineStartMs.coerceAtLeast(0L),
            sourceDurationMs = track.sourceDurationMs.takeIf { it > 0L } ?: sourceEnd
        )
    }
}

private fun AudioTrack.projectEndMs(): Long =
    startTimeMs.coerceAtLeast(0L) + (trimEndMs - trimStartMs).coerceAtLeast(0L)

/**
 * Timeline drag entry point. The project list is authoritative for preview
 * and export; `_audio.tracks` is kept in lock-step for mixer tools.
 */
fun EditorViewModel.moveAudioTrack(trackId: String, startTimeMs: Long) {
    updateAudioPlacement(trackId) { AudioTrackPlacement.moved(it, startTimeMs) }
}

/** Timeline edge-trim entry point: source trim and project placement are saved together. */
fun EditorViewModel.trimAudioTrackPlacement(
    trackId: String,
    sourceStartMs: Long,
    sourceEndMs: Long,
    timelineStartMs: Long
) {
    updateAudioPlacement(trackId) {
        AudioTrackPlacement.trimmed(it, sourceStartMs, sourceEndMs, timelineStartMs)
    }
}

private fun EditorViewModel.updateAudioPlacement(
    trackId: String,
    transform: (AudioTrack) -> AudioTrack
) {
    var updatedTracks: List<AudioTrack>? = null
    _state.update { state ->
        val project = state.project ?: return@update state
        if (project.audioTracks.none { it.id == trackId }) return@update state
        val tracks = project.audioTracks.map { if (it.id == trackId) transform(it) else it }
        updatedTracks = tracks
        val clipDuration = computeTimelineDurationMs(project.clips, emptyList())
        val audioDuration = tracks.maxOfOrNull { it.projectEndMs() } ?: 0L
        val duration = maxOf(clipDuration, audioDuration).coerceAtLeast(1_000L)
        state.copy(
            project = project.copy(audioTracks = tracks, durationMs = duration),
            durationMs = duration,
            playerPositionMs = state.playerPositionMs.coerceAtMost(duration),
            currentTimeMs = state.currentTimeMs.coerceAtMost(duration)
        )
    }
    val tracks = updatedTracks ?: return
    _playerPosition.update { it.coerceAtMost(_state.value.durationMs) }
    _audio.update { it.copy(tracks = tracks) }
    persistProject()
}
