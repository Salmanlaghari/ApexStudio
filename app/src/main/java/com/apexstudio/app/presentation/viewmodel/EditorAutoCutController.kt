package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.ClipTimeMapping
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.SpeedCurveMath
import com.apexstudio.app.domain.model.SpeedPoint
import kotlinx.coroutines.flow.update
import java.util.UUID

/** Appends a beat-aligned sequence made from the selected V1 clips without replacing the existing edit. */
fun EditorViewModel.autoCutSelectedClips(): Boolean {
    val snapshot = _state.value
    val project = snapshot.project ?: return false
    val selectedIds = snapshot.selectedClipIds
    if (selectedIds.size < 2) return false

    // Project order is the visual V1 sequence order; do not depend on Set iteration order.
    val selectedClips = project.clips.filter { it.id in selectedIds }
    if (selectedClips.size != selectedIds.size || selectedClips.any {
            it.type != ClipType.VIDEO || it.trackIndex != 0 || it.effectiveDurationMs() <= 0L
        }) return false

    val beatTrackId = snapshot.selectedAudioTrackId ?: return false
    val beatTrack = project.audioTracks.firstOrNull { it.id == beatTrackId } ?: return false
    if (beatTrack.kind != AudioTrack.Kind.MUSIC) return false
    val sourceBeats = snapshot.beatMarkersByTrackUri[beatTrack.uri].orEmpty()
    if (sourceBeats.size < 2) return false
    val projectBeats = projectBeatMarkers(listOf(beatTrack), mapOf(beatTrack.uri to sourceBeats))

    val sequenceStartMs = project.clips
        .filter { it.trackIndex == 0 && it.type in setOf(ClipType.VIDEO, ClipType.IMAGE) }
        .sumOf { it.effectiveDurationMs().coerceAtLeast(0L) }
    val plan = AutoCutPlanner.plan(
        beatTimestampsMs = projectBeats,
        sequenceStartMs = sequenceStartMs,
        clips = selectedClips.map { AutoCutClipCapacity(it.id, it.effectiveDurationMs()) }
    )
    if (plan.size != selectedClips.size) return false

    val sourceById = selectedClips.associateBy(MediaClip::id)
    val generatedClips = plan.map { segment ->
        val source = sourceById[segment.clipId] ?: return false
        val trimEndMs = sourceEndForOutputDuration(source, segment.durationMs)
        source.copy(
            id = "clip_${UUID.randomUUID()}_autocut",
            trimEndMs = trimEndMs,
            speedPoints = speedPointsForTrim(source, trimEndMs),
            trackIndex = 0,
            type = ClipType.VIDEO,
            timelineOffsetMs = 0L,
            groupId = null,
            textOverlays = source.textOverlays.map { it.copy(id = "text_${UUID.randomUUID()}") },
            stickers = source.stickers.map { it.copy(id = "sticker_${UUID.randomUUID()}") }
        )
    }

    // A single snapshot covers the whole append, so one Undo restores the exact project.
    pushUndo()
    val appendedDurationMs = generatedClips.sumOf { it.effectiveDurationMs() }
    val clipsWithRippledOverlays = rippleOverlays(project.clips, sequenceStartMs, appendedDurationMs)
    val updatedClips = clipsWithRippledOverlays + generatedClips
    val duration = computeTimelineDurationMs(updatedClips, project.audioTracks)
    _state.update { current ->
        val currentProject = current.project ?: return@update current
        current.copy(
            project = currentProject.copy(clips = updatedClips, durationMs = duration),
            durationMs = duration,
            selectedClipId = generatedClips.lastOrNull()?.id,
            selectedClipIds = generatedClips.mapTo(linkedSetOf(), MediaClip::id),
            multiSelectMode = true,
            selectedAudioTrackId = null
        )
    }
    persistProject(importLegacyAnimation = true, refreshLegacyClipFields = true, refreshLegacyTextStickerFields = true)
    return true
}

/** Converts an output-time segment into a source trim without exceeding the selected clip's current trim. */
private fun sourceEndForOutputDuration(source: MediaClip, outputDurationMs: Long): Long {
    val sourceSpan = (source.trimEndMs - source.trimStartMs).coerceAtLeast(0L)
    val sourceOffset = ClipTimeMapping.mainSourceOffsetAtOutputMs(
        source,
        outputDurationMs.coerceIn(0L, source.effectiveDurationMs())
    )
    return (source.trimStartMs + sourceOffset).coerceIn(source.trimStartMs, source.trimStartMs + sourceSpan)
}

/** Re-bases a trimmed variable-speed curve to the new source range instead of discarding the user's speed ramp. */
private fun speedPointsForTrim(source: MediaClip, trimEndMs: Long): List<SpeedPoint> {
    val points = SpeedCurveMath.sanitize(source.speedPoints)
    val sourceSpan = (source.trimEndMs - source.trimStartMs).coerceAtLeast(0L)
    val newSpan = (trimEndMs - source.trimStartMs).coerceAtLeast(0L)
    if (points.size < 2 || newSpan <= 0L || newSpan >= sourceSpan) return source.speedPoints

    val endFraction = (newSpan.toDouble() / sourceSpan.toDouble()).toFloat().coerceIn(0f, 1f)
    if (endFraction <= 0f) return emptyList()
    val epsilon = 0.000001f
    val kept = points.filter { it.timeFraction > epsilon && it.timeFraction < endFraction - epsilon }
        .map { it.copy(timeFraction = it.timeFraction / endFraction) }
    val exactEnd = points.firstOrNull { kotlin.math.abs(it.timeFraction - endFraction) <= epsilon }
    val endSharp = exactEnd?.sharp ?: points.firstOrNull { it.timeFraction > endFraction }?.sharp ?: false
    return SpeedCurveMath.sanitize(
        listOf(points.first().copy(timeFraction = 0f)) + kept +
            SpeedPoint(1f, SpeedCurveMath.speedAt(points, endFraction), endSharp)
    )
}
