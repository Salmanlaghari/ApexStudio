package com.apexstudio.app.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.apexstudio.app.domain.model.*
import com.apexstudio.app.domain.model.timeline.collectAllTimecodes
import com.apexstudio.app.presentation.state.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.apexstudio.app.ai.audio.AudioIntelligence
import com.apexstudio.app.data.audio.PcmAudioExtractor


fun EditorViewModel.openAudioMixer() = _state.update { it.copy(audioMixerOpen = true) }

fun EditorViewModel.closeAudioMixer() = _state.update { it.copy(audioMixerOpen = false) }


fun EditorViewModel.setAudioBpm(b: Int) {
    val safeBpm = b.coerceIn(60, 240)
    _audio.update { it.copy(bpm = safeBpm) }
    val beatIntervalMs = (60_000.0 / safeBpm).toLong()
    val totalMs = _state.value.durationMs.coerceAtLeast(10_000L)
    val beats = mutableListOf<Long>()
    var curMs = 0L
    while (curMs <= totalMs) {
        beats.add(curMs)
        curMs += beatIntervalMs
    }
    _state.update { it.copy(bpm = safeBpm, beatMarkersMs = beats) }
}


fun EditorViewModel.toggleSnapToBeat() {
    val next = !_state.value.snapToBeat
    _state.update { it.copy(snapToBeat = next) }
}


fun EditorViewModel.toggleMagneticSnapping() {
    val next = !_state.value.magneticSnapping
    _state.update { it.copy(magneticSnapping = next) }
}


fun EditorViewModel.toggleRippleEdit() {
    val next = !_state.value.rippleEditEnabled
    _state.update { it.copy(rippleEditEnabled = next) }
}


/**
 * Magnetically snaps [timeMs] to adjacent cut points, keyframes, or beat drop markers
 * if within [snapThresholdMs].
 */
fun EditorViewModel.findMagneticSnapPoint(
    timeMs: Long,
    snapThresholdMs: Long = 150L,
    excludeClipId: String? = null
): Long {
    val state = _state.value
    if (!state.magneticSnapping) {
        return if (state.snapToBeat) findNearestBeat(timeMs, snapThresholdMs) else timeMs
    }
    val candidates = mutableListOf<Long>()
    val project = state.project
    if (project != null) {
        // The typed timeline is canonical for every lane/property. Keep the legacy
        // candidates below as a compatibility safety net while old editors migrate.
        candidates.addAll(project.timeline.collectAllTimecodes())
        var accMs = 0L
        candidates.add(0L)
        for (clip in project.clips) {
            val end = if (clip.trimEndMs == Long.MAX_VALUE) clip.durationMs else clip.trimEndMs
            if (clip.trimStartMs >= 0L && end >= clip.trimStartMs) {
                val duration = SpeedCurveMath.effectiveDurationMs(
                    end - clip.trimStartMs, SpeedCurveMath.sanitize(clip.speedPoints)
                )
                val isMovingClip = clip.id == excludeClipId
                when {
                    clip.type == ClipType.OVERLAY -> {
                        // Overlays are placed independently; they never advance V1.
                        val start = clip.timelineOffsetMs.coerceAtLeast(0L)
                        if (!isMovingClip) {
                            candidates.add(start)
                            candidates.add(saturatedSnapEnd(start, duration))
                        }
                    }
                    clip.trackIndex == 0 && (clip.type == ClipType.VIDEO || clip.type == ClipType.IMAGE) -> {
                        accMs = saturatedSnapEnd(accMs, duration)
                        if (!isMovingClip) candidates.add(accMs)
                    }
                }
            }
            // Persisted clip keyframes and timeline diamonds use absolute project time.
            if (clip.id != excludeClipId) candidates.addAll(clip.keyframes.keyframes.map { it.timeMs })
        }
    }
    if (state.snapToBeat) {
        candidates.addAll(state.beatMarkersMs)
    }
    return nearestTimelineSnap(timeMs, snapThresholdMs, candidates)
}

private fun saturatedSnapEnd(startMs: Long, durationMs: Long): Long =
    if (durationMs > Long.MAX_VALUE - startMs) Long.MAX_VALUE else startMs + durationMs

/** Non-negative timeline differences cannot overflow; abs(a - b) for arbitrary Longs can. */
private fun nearestTimelineSnap(timeMs: Long, thresholdMs: Long, candidates: List<Long>): Long {
    if (timeMs < 0L || thresholdMs < 0L) return timeMs
    var closest = timeMs
    var closestDistance = Long.MAX_VALUE
    var found = false
    for (candidate in candidates) {
        if (candidate < 0L) continue
        val distance = if (candidate >= timeMs) candidate - timeMs else timeMs - candidate
        if (distance <= thresholdMs && (!found || distance < closestDistance ||
                (distance == closestDistance && candidate < closest))) {
            closest = candidate
            closestDistance = distance
            found = true
        }
    }
    return closest
}

fun EditorViewModel.findNearestBeat(timeMs: Long, snapThresholdMs: Long = 180L): Long {
    if (!_state.value.snapToBeat) return timeMs
    return nearestTimelineSnap(timeMs, snapThresholdMs, _state.value.beatMarkersMs)
}

fun EditorViewModel.toggleAiVoice() = _audio.update { it.copy(aiVoiceEnhance = !it.aiVoiceEnhance) }

fun EditorViewModel.setClarity(v: Float) = _audio.update { it.copy(clarity = v) }

fun EditorViewModel.setReduceNoise(v: Float) = _audio.update { it.copy(reduceNoise = v) }


// Phase E: voice-changer + audio effects. setPitch translates
// semitones to PlaybackParameters.pitch via 2^(semitones/12) and
// writes it to the main ExoPlayer so the preview reflects the
// change. enableReverb / enableEcho / enableBassBoost gate the
// matching android.media.audiofx classes inside AudioEngine and
// the export pipeline picks them up via the same state fields.
fun EditorViewModel.setPitch(semitones: Float) {
    val clamped = semitones.coerceIn(-12f, 12f)
    _audio.update { it.copy(pitchSemitones = clamped) }
    audioEngine?.setPitchSemitones(clamped)
    applyAudioPitchToMainPlayer(clamped)
}


fun EditorViewModel.enableReverb(enabled: Boolean, preset: Short = 0) {
    _audio.update { it.copy(reverbEnabled = enabled, reverbPreset = preset) }
    audioEngine?.enableReverb(enabled, preset)
}


fun EditorViewModel.enableEcho(enabled: Boolean) {
    _audio.update { it.copy(echoEnabled = enabled) }
    audioEngine?.enableEcho(enabled)
}


fun EditorViewModel.enableBassBoost(enabled: Boolean, strength: Short = 0) {
    _audio.update { it.copy(bassBoostEnabled = enabled, bassBoostStrength = strength) }
    audioEngine?.enableBassBoost(enabled, strength)
}

// Phase E: push pitch to the preview ExoPlayer. ExoPlayer exposes
// pitch via PlaybackParameters(speed, pitch). speed stays at 1f
// so we only change the pitch component.

fun EditorViewModel.registerMainPlayerForAudioEffects(p: androidx.media3.exoplayer.ExoPlayer?) {
    mainPlayerRef = p
    // Apply any previously-stored pitch immediately so re-attaching
    // the player preserves the user's setting.
    applyAudioPitchToMainPlayer(_audio.value.pitchSemitones)
}

/** Detach a disposed preview player without clearing a newer registration. */
fun EditorViewModel.unregisterMainPlayerForAudioEffects(p: androidx.media3.exoplayer.ExoPlayer) {
    if (mainPlayerRef === p) registerMainPlayerForAudioEffects(null)
}

internal fun EditorViewModel.applyAudioPitchToMainPlayer(semitones: Float) {
    val p = mainPlayerRef ?: return
    val pitch = Math.pow(2.0, (semitones / 12.0).toDouble()).toFloat()
    p.playbackParameters = androidx.media3.common.PlaybackParameters(1f, pitch)
}

fun EditorViewModel.toggleMute(trackId: String) = toggleAudioTrackMute(trackId)

fun EditorViewModel.setTrackVolume(trackId: String, vol: Float) = setAudioTrackVolume(trackId, vol)

fun EditorViewModel.setTrackPanning(trackId: String, pan: Float) = setAudioTrackPan(trackId, pan)

fun EditorViewModel.setAudioTrackPan(trackId: String, pan: Float) {
    val safePan = pan.takeIf(Float::isFinite)?.coerceIn(-1f, 1f) ?: 0f
    val original = _state.value.project ?: return
    logAudioTrackMismatch(trackId, "setAudioTrackPan")
    val updatedAudio = original.audioTracks.map { track ->
        if (track.id == trackId) track.copy(pan = safePan) else track
    }
    val normalized = original.copy(audioTracks = updatedAudio).syncSharedTimeline(importLegacyAnimation = true)
    val sharedId = "shared-audio-$trackId"
    val updatedTimeline = normalized.timeline.tracks.map { track ->
        if (track is com.apexstudio.app.domain.model.timeline.AudioTrack && track.id == sharedId) {
            track.copy(
                pan = safePan,
                clips = track.clips.map { if (it.id == trackId) it.copy(pan = safePan) else it }
            )
        } else track
    }
    val updatedProject = normalized.copy(audioTracks = updatedAudio, timeline = com.apexstudio.app.domain.model.timeline.Timeline(updatedTimeline))
    _state.update { state -> if (state.project?.id == original.id) state.copy(project = updatedProject) else state }
    _audio.update { state -> state.copy(tracks = state.tracks.map { if (it.id == trackId) it.copy(pan = safePan) else it }) }
    persistProject()
}

/**
 * Dual-write guard (Kilo): the project list and the mixer list must
 * carry the same track ids. If a setter targets a track missing from
 * either list (diverged / old project), the map would silently do
 * nothing — log a warning so the divergence is visible instead of
 * silently dropping the edit. The lists are re-synced on project
 * load (see loadProject), so a warning here means a genuinely
 * diverged project, not normal operation.
 */
private fun EditorViewModel.logAudioTrackMismatch(trackId: String, where: String) {
    val inProject = _state.value.project?.audioTracks?.any { it.id == trackId } == true
    val inMixer = _audio.value.tracks.any { it.id == trackId }
    if (!inProject || !inMixer) {
        Log.w(
            "EditorViewModel",
            "AudioTrack $trackId missing from ${if (!inProject) "project " else ""}${if (!inMixer) "mixer" else ""} in $where — edit may not apply everywhere"
        )
    }
}


fun EditorViewModel.setLowEQ(gain: Short) {
    audioEngine?.setLowGain(gain)
    _audio.update { it.copy(lowEQ = gain) }
}

fun EditorViewModel.setMidEQ(gain: Short) {
    audioEngine?.setMidGain(gain)
    _audio.update { it.copy(midEQ = gain) }
}

fun EditorViewModel.setHighEQ(gain: Short) {
    audioEngine?.setHighGain(gain)
    _audio.update { it.copy(highEQ = gain) }
}

fun EditorViewModel.setVolume(vol: Float) {
    audioEngine?.setVolume(vol)
    _audio.update { it.copy(volume = vol.coerceIn(0f, 1f)) }
}

fun EditorViewModel.toggleMuteEngine() {
    audioEngine?.toggleMute()
    _audio.update { it.copy(isMuted = !it.isMuted) }
}

fun EditorViewModel.toggleSoloEngine() {
    audioEngine?.toggleSolo()
    _audio.update { it.copy(isSolo = !it.isSolo) }
}

fun EditorViewModel.setNoiseReduction(level: Float) {
    audioEngine?.setNoiseReduction(level)
    _audio.update { it.copy(noiseReduction = level) }
}

fun EditorViewModel.toggleEchoCancellation(enabled: Boolean) {
    audioEngine?.toggleEchoCancellation(enabled)
    _audio.update { it.copy(echoCancellation = enabled) }
}

fun EditorViewModel.toggleNoiseSuppression(enabled: Boolean) {
    audioEngine?.toggleNoiseSuppression(enabled)
    _audio.update { it.copy(noiseSuppression = enabled) }
}

fun EditorViewModel.setWaveformSamples(samples: FloatArray) {
    _audio.update { it.copy(waveformSamples = samples) }
}


fun EditorViewModel.setTimelineWaveform(samples: FloatArray) = _state.update {
    it.copy(audioWaveform = samples)
}


fun EditorViewModel.refreshTimelineWaveform(clipId: String) {
    val ctx = context ?: return
    val clip = _state.value.project?.clips?.firstOrNull { it.id == clipId } ?: return
    if (clip.type != com.apexstudio.app.domain.model.ClipType.VIDEO) {
        setTimelineWaveform(FloatArray(0))
        return
    }
    val analyzer = mediaAnalyzer ?: return
    viewModelScope.launch {
        try {
            val data = analyzer.analyzeAudioWaveform(
                clip.uri, ctx,
                sampleCount = 200,
                trimStartMs = clip.trimStartMs,
                trimEndMs = clip.trimEndMs
            )
            setTimelineWaveform(data.samples)
        } catch (e: Exception) {
            Log.w("EditorViewModel", "Waveform decode failed for ${clip.id}", e)
            setTimelineWaveform(FloatArray(0))
        }
    }
}

fun EditorViewModel.setRecordingState(recording: Boolean) {
    _audio.update { it.copy(isRecording = recording) }
}


fun EditorViewModel.addAudioTrack(
    name: String,
    uri: String,
    kind: AudioTrack.Kind = AudioTrack.Kind.MUSIC,
    sourceDurationMs: Long = 0L,
    startTimeMs: Long = livePlayheadMs()
) {
    if (_state.value.project == null) return
    pushUndo()
    val finalDuration = if (sourceDurationMs > 0L) sourceDurationMs else 30000L
    val track = AudioTrack(
        id = java.util.UUID.randomUUID().toString(),
        name = name,
        uri = uri,
        volume = if (kind == AudioTrack.Kind.SFX) 1f else 0.85f,
        kind = kind,
        startTimeMs = startTimeMs.coerceAtLeast(0L),
        sourceDurationMs = finalDuration,
        trimStartMs = 0L,
        trimEndMs = finalDuration
    )
    _state.update { s ->
        val p = s.project ?: return@update s
        val updatedTracks = p.audioTracks + track
        val newDuration = computeTimelineDurationMs(p.clips, updatedTracks)
        s.copy(
            project = p.copy(audioTracks = updatedTracks, durationMs = newDuration),
            durationMs = newDuration
        )
    }
    _audio.update { it.copy(tracks = _state.value.project?.audioTracks.orEmpty()) }
    selectAudioTrack(track.id)
    persistProject()
}


fun EditorViewModel.removeAudioTrack(trackId: String) {
    if (_state.value.project?.audioTracks?.none { it.id == trackId } != false) return
    pushUndo()
    var remainingTracks: List<AudioTrack>? = null
    var remainingDuration: Long? = null
    _state.update { s ->
        val p = s.project ?: return@update s
        val tracks = p.audioTracks.filterNot { it.id == trackId }
        val duration = computeTimelineDurationMs(p.clips, tracks)
        remainingTracks = tracks
        remainingDuration = duration
        s.copy(
            project = p.copy(audioTracks = tracks, durationMs = duration),
            durationMs = duration,
            playerPositionMs = s.playerPositionMs.coerceAtMost(duration),
            currentTimeMs = s.currentTimeMs.coerceAtMost(duration)
        )
    }
    val tracks = remainingTracks ?: return
    val duration = remainingDuration ?: return
    _audio.update { it.copy(tracks = tracks) }
    _playerPosition.update { it.coerceAtMost(duration) }
    persistProject()
}


fun EditorViewModel.setAudioTrackVolume(trackId: String, vol: Float) {
    // Dual-write: preview + video export read project.audioTracks,
    // the mixer UI reads _audio.tracks, and persistence saves the
    // project. Writing only _audio (the old behaviour) made mixer
    // volume edits silently vanish — audible nowhere, lost on
    // reopen, absent from export. See setAudioTrackVolumeFull.
    val safe = vol.coerceIn(0f, 1f)
    logAudioTrackMismatch(trackId, "setAudioTrackVolume")
    _state.update { s ->
        val p = s.project ?: return@update s
        s.copy(project = p.copy(audioTracks = p.audioTracks.map {
            if (it.id == trackId) it.copy(volume = safe) else it
        }))
    }
    _audio.update { s ->
        s.copy(tracks = s.tracks.map {
            if (it.id == trackId) it.copy(volume = safe) else it
        })
    }
    persistProject()
}


/**
 * CapCut-style volume animation for music/SFX tracks: per-point
 * volume keyframes over timeline time (distinct from fade in/out,
 * which are envelope ramps at the track edges).
 *
 * - [addAudioVolumeKeyframe]: MANUAL first keyframe at [timeMs],
 *   pinning the current effective volume. Discrete, undoable.
 * - [upsertAudioVolumeKeyframe]: auto-keyframe — a keyframe within
 *   150ms of [timeMs] is updated in place; otherwise a new keyframe
 *   is inserted (one undo entry) when the track already has
 *   keyframes. Returns false (no-op) when the track has no volume
 *   keyframes yet — the first keyframe stays manual.
 * - [setAudioTrackVolumeAuto]: mixer-slider entry point — routes to
 *   the keyframe upsert when the track has volume keyframes, else to
 *   the existing scalar [setAudioTrackVolume] (behaviour unchanged).
 * - [removeAudioVolumeKeyframe]: delete one pin (undoable).
 *
 * All four dual-write (project.audioTracks + _audio.tracks) like
 * every other audio setter, so preview, export and persistence can
 * never disagree about a track's volume keyframes.
 */
fun EditorViewModel.addAudioVolumeKeyframe(trackId: String, timeMs: Long) {
    pushUndo()
    logAudioTrackMismatch(trackId, "addAudioVolumeKeyframe")
    updateAudioTrackBoth(trackId) { track ->
        val without = track.volumeKeyframes.filter { it.timeMs != timeMs }
        track.copy(
            volumeKeyframes = (without + VolumeKeyframe(
                id = java.util.UUID.randomUUID().toString(),
                timeMs = timeMs,
                volume = track.effectiveVolumeAt(timeMs)
            )).sortedBy { it.timeMs }
        )
    }
}

fun EditorViewModel.upsertAudioVolumeKeyframe(
    trackId: String,
    timeMs: Long,
    volume: Float
): Boolean {
    val track = _state.value.project?.audioTracks?.firstOrNull { it.id == trackId } ?: return false
    if (track.volumeKeyframes.isEmpty()) return false
    val safe = volume.coerceIn(0f, 1f)
    logAudioTrackMismatch(trackId, "upsertAudioVolumeKeyframe")
    val near = track.volumeKeyframes.minByOrNull { kotlin.math.abs(it.timeMs - timeMs) }
    if (near != null && kotlin.math.abs(near.timeMs - timeMs) <= 150L) {
        updateAudioTrackBoth(trackId) { t ->
            t.copy(
                volumeKeyframes = t.volumeKeyframes.map {
                    if (it.id == near.id) it.copy(volume = safe) else it
                }
            )
        }
        return true
    }
    // New keyframe at the playhead: one undo entry for the insert;
    // subsequent drag ticks update it via the near-keyframe path.
    pushUndo()
    updateAudioTrackBoth(trackId) { t ->
        t.copy(
            volumeKeyframes = (t.volumeKeyframes + VolumeKeyframe(
                id = java.util.UUID.randomUUID().toString(),
                timeMs = timeMs,
                volume = safe
            )).sortedBy { it.timeMs }
        )
    }
    return true
}

fun EditorViewModel.setAudioTrackVolumeAuto(trackId: String, vol: Float, timeMs: Long) {
    val track = _state.value.project?.audioTracks?.firstOrNull { it.id == trackId }
    if (track != null && track.volumeKeyframes.isNotEmpty()) {
        upsertAudioVolumeKeyframe(trackId, timeMs, vol)
    } else {
        setAudioTrackVolume(trackId, vol)
    }
}

fun EditorViewModel.removeAudioVolumeKeyframe(trackId: String, keyframeId: String) {
    pushUndo()
    logAudioTrackMismatch(trackId, "removeAudioVolumeKeyframe")
    updateAudioTrackBoth(trackId) { track ->
        track.copy(volumeKeyframes = track.volumeKeyframes.filter { it.id != keyframeId })
    }
}

/** Dual-write one [AudioTrack] transform to project + mixer lists, then persist. */
private fun EditorViewModel.updateAudioTrackBoth(
    trackId: String,
    transform: (AudioTrack) -> AudioTrack
) {
    _state.update { s ->
        val p = s.project ?: return@update s
        s.copy(project = p.copy(audioTracks = p.audioTracks.map {
            if (it.id == trackId) transform(it) else it
        }))
    }
    _audio.update { s ->
        s.copy(tracks = s.tracks.map {
            if (it.id == trackId) transform(it) else it
        })
    }
    persistProject()
}

fun EditorViewModel.toggleAudioTrackMute(trackId: String) {
    if (_state.value.project?.audioTracks?.none { it.id == trackId } != false) return
    pushUndo()
    logAudioTrackMismatch(trackId, "toggleAudioTrackMute")
    _audio.update { s ->
        s.copy(tracks = s.tracks.map { if (it.id == trackId) it.copy(isMuted = !it.isMuted) else it })
    }
    _state.update { s ->
        val p = s.project ?: return@update s
        val updated = p.audioTracks.map {
            if (it.id == trackId) it.copy(isMuted = !it.isMuted) else it
        }
        s.copy(project = p.copy(audioTracks = updated))
    }
    persistProject()
}


fun EditorViewModel.toggleAudioTrackSolo(trackId: String) {
    if (_state.value.project?.audioTracks?.none { it.id == trackId } != false) return
    pushUndo()
    // Dual-write + persist (same split-brain bug as volume: the old
    // _audio-only write never reached preview/export/persistence).
    logAudioTrackMismatch(trackId, "toggleAudioTrackSolo")
    _state.update { s ->
        val p = s.project ?: return@update s
        s.copy(project = p.copy(audioTracks = p.audioTracks.map {
            if (it.id == trackId) it.copy(isSolo = !it.isSolo) else it
        }))
    }
    _audio.update { s ->
        s.copy(tracks = s.tracks.map { if (it.id == trackId) it.copy(isSolo = !it.isSolo) else it })
    }
    persistProject()
}


fun EditorViewModel.setAudioTrackTrim(trackId: String, startMs: Long, endMs: Long) {
    // Dual-write + persist (same split-brain bug as volume: the old
    // _audio-only write never reached preview/export/persistence).
    logAudioTrackMismatch(trackId, "setAudioTrackTrim")
    _state.update { s ->
        val p = s.project ?: return@update s
        s.copy(project = p.copy(audioTracks = p.audioTracks.map {
            if (it.id == trackId) it.copy(
                trimStartMs = startMs.coerceAtLeast(0),
                trimEndMs = endMs.coerceAtLeast(startMs)
            ) else it
        }))
    }
    _audio.update { s ->
        s.copy(tracks = s.tracks.map {
            if (it.id == trackId) it.copy(trimStartMs = startMs.coerceAtLeast(0), trimEndMs = endMs.coerceAtLeast(startMs)) else it
        })
    }
    persistProject()
}


fun EditorViewModel.setAudioTrackFadeIn(trackId: String, ms: Long) {
    // Dual-write + persist (same split-brain bug as volume).
    logAudioTrackMismatch(trackId, "setAudioTrackFadeIn")
    _state.update { s ->
        val p = s.project ?: return@update s
        s.copy(project = p.copy(audioTracks = p.audioTracks.map {
            if (it.id == trackId) it.copy(fadeInMs = ms.coerceAtLeast(0)) else it
        }))
    }
    _audio.update { s ->
        s.copy(tracks = s.tracks.map { if (it.id == trackId) it.copy(fadeInMs = ms.coerceAtLeast(0)) else it })
    }
    persistProject()
}


fun EditorViewModel.setAudioTrackFadeOut(trackId: String, ms: Long) {
    // Dual-write + persist (same split-brain bug as volume).
    logAudioTrackMismatch(trackId, "setAudioTrackFadeOut")
    _state.update { s ->
        val p = s.project ?: return@update s
        s.copy(project = p.copy(audioTracks = p.audioTracks.map {
            if (it.id == trackId) it.copy(fadeOutMs = ms.coerceAtLeast(0)) else it
        }))
    }
    _audio.update { s ->
        s.copy(tracks = s.tracks.map { if (it.id == trackId) it.copy(fadeOutMs = ms.coerceAtLeast(0)) else it })
    }
    persistProject()
}


fun EditorViewModel.setMuteOriginalVideo(muted: Boolean) {
    _audio.update { it.copy(isMuted = muted) }
}


fun EditorViewModel.splitAudioTrack(trackId: String, atMs: Long) {
    val original = _state.value.project?.audioTracks?.firstOrNull { it.id == trackId } ?: return
    val elapsed = atMs - original.startTimeMs
    val span = original.trimEndMs - original.trimStartMs
    if (elapsed < 100L || elapsed > span - 100L) return
    val part2Id = java.util.UUID.randomUUID().toString()
    _state.update { s ->
        val p = s.project ?: return@update s
        val track = p.audioTracks.firstOrNull { it.id == trackId } ?: return@update s
        val splitPoint = track.trimStartMs + elapsed
        val part1 = track.copy(trimEndMs = splitPoint)
        val part2 = track.copy(
            id = part2Id,
            trimStartMs = splitPoint,
            startTimeMs = atMs
        )
        val updated = p.audioTracks.flatMap {
            if (it.id == trackId) listOf(part1, part2) else listOf(it)
        }
        s.copy(project = p.copy(audioTracks = updated))
    }
    _audio.update { it.copy(tracks = _state.value.project?.audioTracks.orEmpty()) }
    selectAudioTrack(part2Id)
    persistProject()
}


fun EditorViewModel.updateAudioTrackVolume(trackId: String, volume: Float) {
    // Same dual-write as setAudioTrackVolume (single entry point for
    // project + mixer + persistence so the two lists can never diverge).
    setAudioTrackVolume(trackId, volume)
}


/**
 * CapCut-style Beats: energy/onset-based beat detection for an audio track.
 *
 * Tapping "Beats" in the contextual toolbar decodes the track's amplitude
 * envelope (via [MediaAnalyzer]), runs onset detection + tempo estimation,
 * and stores beat markers that render on the audio lane and drive
 * snap-to-beat. Tapping again clears the markers.
 */
fun EditorViewModel.toggleBeatsForTrack(trackId: String) {
    val s = _state.value
    if (s.beatSourceTrackId == trackId && s.beatMarkersMs.isNotEmpty()) {
        // Toggle off: clear markers.
        _state.update { it.copy(beatMarkersMs = emptyList(), beatSourceTrackId = null) }
        return
    }
    val track = s.project?.audioTracks?.firstOrNull { it.id == trackId }
        ?: _audio.value.tracks.firstOrNull { it.id == trackId }
        ?: return
    val ctx = context ?: return
    _state.update { it.copy(beatsAnalyzing = true) }
    viewModelScope.launch {
        try {
            val data = mediaAnalyzer?.analyzeAudioWaveform(
                uri = track.uri,
                context = ctx,
                sampleCount = 1500,
                trimStartMs = track.trimStartMs,
                trimEndMs = track.trimEndMs
            )
            val beats = if (data != null && data.samples.isNotEmpty()) {
                computeBeatGrid(data.samples, data.durationMs)
            } else {
                // Fallback: BPM grid from the current BPM setting.
                val interval = (60_000.0 / _audio.value.bpm.coerceIn(60, 240)).toLong()
                val total = track.trimEndMs.coerceAtLeast(10_000L)
                generateSequence(0L) { it + interval }.takeWhile { it <= total }.toList()
            }
            _state.update {
                it.copy(
                    beatMarkersMs = beats,
                    beatSourceTrackId = trackId,
                    snapToBeat = true,
                    beatsAnalyzing = false
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("EditorViewModel", "Beat detection failed: ${e.message}")
            _state.update { it.copy(beatsAnalyzing = false) }
        }
    }
}

/**
 * Pure beat-grid computation, unit-testable without Android.
 *
 * [envelope] is a normalised 0..1 amplitude envelope over [durationMs].
 * Steps: onset novelty (positive differences) → tempo via autocorrelation
 * of the novelty in the 60..200 BPM band → beat grid anchored on the
 * strongest early onset, snapped to nearby onsets.
 */
fun computeBeatGrid(envelope: FloatArray, durationMs: Long): List<Long> {
    if (envelope.size < 32 || durationMs <= 0L) return emptyList()
    val n = envelope.size
    // 1. Onset novelty: positive first difference of the envelope.
    val novelty = FloatArray(n)
    for (i in 1 until n) {
        val d = envelope[i] - envelope[i - 1]
        novelty[i] = if (d > 0f) d else 0f
    }
    val mean = novelty.average().toFloat()
    val std = kotlin.math.sqrt(novelty.map { (it - mean) * (it - mean) }.average()).toFloat()
    val onsetThreshold = mean + 0.6f * std
    val onsetIdx = (0 until n).filter { novelty[it] > onsetThreshold }
    if (onsetIdx.size < 4) return emptyList()

    // 2. Tempo estimation: autocorrelate novelty for lags = 60..200 BPM.
    val msPerBucket = durationMs.toDouble() / n
    var bestLag = -1
    var bestScore = 0.0
    val minLagBuckets = (60_000.0 / 200 / msPerBucket).toInt().coerceAtLeast(2)
    val maxLagBuckets = (60_000.0 / 60 / msPerBucket).toInt().coerceAtMost(n / 2)
    for (lag in minLagBuckets..maxLagBuckets) {
        var score = 0.0
        var i = 0
        while (i + lag < n) {
            score += novelty[i] * novelty[i + lag]
            i++
        }
        if (score > bestScore) {
            bestScore = score
            bestLag = lag
        }
    }
    if (bestLag <= 0) return emptyList()
    val beatIntervalMs = (bestLag * msPerBucket).toLong().coerceAtLeast(200L)

    // 3. Anchor: strongest onset in the first two beat intervals.
    val anchorSearchEnd = (2 * bestLag).coerceAtMost(n - 1)
    val anchorIdx = (0..anchorSearchEnd).maxByOrNull { novelty[it] } ?: 0
    val anchorMs = (anchorIdx * msPerBucket).toLong()

    // 4. Grid + snap each beat to the nearest strong onset (±1 interval/4).
    val snapWindow = (bestLag / 4).coerceAtLeast(1)
    val beats = mutableListOf<Long>()
    var t = anchorMs
    while (t <= durationMs) {
        val bucket = (t / msPerBucket).toInt().coerceIn(0, n - 1)
        val lo = (bucket - snapWindow).coerceAtLeast(0)
        val hi = (bucket + snapWindow).coerceAtMost(n - 1)
        val snapped = (lo..hi).maxByOrNull { novelty[it] } ?: bucket
        if (novelty[snapped] > onsetThreshold * 0.5f) {
            beats.add((snapped * msPerBucket).toLong())
        } else {
            beats.add(t)
        }
        t += beatIntervalMs
    }
    return beats.distinct().sorted()
}


// ---------------------------------------------------------------------------
// Audio intelligence — on-device deterministic DSP (ai/audio/AudioIntelligence).
//
// These fill the gap left by the energy-envelope beat grid above: beat
// detection finds *where* the hits are, but nothing in the project measured
// loudness, ducked music under speech, or trimmed a music bed to fit the
// video. All three run on PCM the app already decodes, so they add no model
// bytes, no network call and no licensing surface.
// ---------------------------------------------------------------------------

private const val AUDIO_INTELLIGENCE_TAG = "EditorAudioIntelligence"

/** `PcmAudioExtractor` always resamples to 16 kHz mono; every analysis below is mono. */
private const val AUDIO_INTELLIGENCE_RATE_HZ = PcmAudioExtractor.TARGET_RATE

/** Resolves a track from the project; mixer-only tracks are not analysable (no project copy). */
private fun EditorState.findAudioTrack(trackId: String): AudioTrack? =
    project?.audioTracks?.firstOrNull { it.id == trackId }

private suspend fun decodeTrackPcm(context: android.content.Context, uriString: String): FloatArray? {
    if (uriString.isBlank()) return null
    val uri = android.net.Uri.parse(uriString)
    return withContext(Dispatchers.IO) {
        runCatching { PcmAudioExtractor.extractMono16k(context, uri) }.getOrNull()
    }
}

/**
 * Measures the track's gated loudness and sets its fader so it lands near
 * -16 LUFS. [AudioIntelligence.measureLoudness] returns a gain that is already
 * clamped, and the fader is clamped again to 0..1 so a very quiet source can
 * never push the slider past unity.
 */
fun EditorViewModel.normalizeTrackLoudness(trackId: String) {
    val ctx = context ?: return
    val track = _state.value.findAudioTrack(trackId) ?: return
    viewModelScope.launch {
        val pcm = decodeTrackPcm(ctx, track.uri) ?: return@launch
        val result = withContext(Dispatchers.Default) {
            AudioIntelligence.measureLoudness(pcm, AUDIO_INTELLIGENCE_RATE_HZ)
        }
        val target = (track.volume * result.suggestedGain).coerceIn(0f, 1f)
        setAudioTrackVolume(trackId, target)
        Log.i(
            AUDIO_INTELLIGENCE_TAG,
            "normalize $trackId: ${result.gatedLufs} LUFS, gain ${result.suggestedGain}, fader ${track.volume} -> $target"
        )
    }
}

/**
 * Writes a volume-keyframe envelope on the music track that dips under speech.
 *
 * The envelope is [AudioIntelligence.autoDuckPlan] scaled by the track's own
 * fader, so a track sat at 0.5 ducks to a quarter of *its* level rather than
 * jumping to an absolute value. Existing keyframes are replaced — this is a
 * generated envelope, not a hand-edited one, and it is one undo step.
 */
fun EditorViewModel.autoDuckMusicUnderVoice(musicTrackId: String, voiceTrackId: String) {
    if (musicTrackId == voiceTrackId) return
    val ctx = context ?: return
    val music = _state.value.findAudioTrack(musicTrackId) ?: return
    val voice = _state.value.findAudioTrack(voiceTrackId) ?: return
    viewModelScope.launch {
        val musicPcm = decodeTrackPcm(ctx, music.uri) ?: return@launch
        val voicePcm = decodeTrackPcm(ctx, voice.uri) ?: return@launch
        val points = withContext(Dispatchers.Default) {
            AudioIntelligence.autoDuckPlan(musicPcm, voicePcm, AUDIO_INTELLIGENCE_RATE_HZ)
        }
        if (points.isEmpty()) return@launch
        pushUndo()
        updateAudioTrackBoth(musicTrackId) { track ->
            track.copy(
                volumeKeyframes = points.map { point ->
                    VolumeKeyframe(
                        id = java.util.UUID.randomUUID().toString(),
                        timeMs = point.timeMs,
                        volume = (point.gain * track.volume).coerceIn(0f, 1f)
                    )
                }.sortedBy { it.timeMs }
            )
        }
        Log.i(AUDIO_INTELLIGENCE_TAG, "auto-duck $musicTrackId under $voiceTrackId: ${points.size} points")
    }
}

/**
 * Trims a music bed so its length matches the project, choosing an offset that
 * starts on a detected onset instead of hard-cutting mid-bar.
 */
fun EditorViewModel.fitMusicToProjectLength(trackId: String) {
    val ctx = context ?: return
    val track = _state.value.findAudioTrack(trackId) ?: return
    val targetMs = _state.value.durationMs
    if (targetMs <= 0L) return
    viewModelScope.launch {
        val pcm = decodeTrackPcm(ctx, track.uri) ?: return@launch
        val window: AudioIntelligence.FitWindow = withContext(Dispatchers.Default) {
            val beats = AudioIntelligence.detectBeats(pcm, AUDIO_INTELLIGENCE_RATE_HZ)
            val targetSamples = (targetMs.toFloat() / 1000f * AUDIO_INTELLIGENCE_RATE_HZ).toInt()
            AudioIntelligence.fitToDuration(pcm, AUDIO_INTELLIGENCE_RATE_HZ, targetSamples, beats.beats)
        }
        if (window.endSampleExclusive <= window.startSample) return@launch
        val startMs = (window.startSample.toFloat() / AUDIO_INTELLIGENCE_RATE_HZ * 1000f).toLong()
        val endMs = (window.endSampleExclusive.toFloat() / AUDIO_INTELLIGENCE_RATE_HZ * 1000f).toLong()
        pushUndo()
        updateAudioTrackBoth(trackId) { t -> t.copy(trimStartMs = startMs, trimEndMs = endMs) }
        Log.i(AUDIO_INTELLIGENCE_TAG, "fit $trackId to ${targetMs}ms: trim ${startMs}..${endMs}ms")
    }
}
