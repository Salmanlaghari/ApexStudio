package com.apexstudio.app.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.apexstudio.app.domain.model.*
import com.apexstudio.app.presentation.state.*
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


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
fun EditorViewModel.findMagneticSnapPoint(timeMs: Long, snapThresholdMs: Long = 150L): Long {
    if (!_state.value.magneticSnapping) {
        return if (_state.value.snapToBeat) findNearestBeat(timeMs, snapThresholdMs) else timeMs
    }
    val candidates = mutableListOf<Long>()
    val project = _state.value.project
    if (project != null) {
        var accMs = 0L
        candidates.add(0L)
        for (clip in project.clips) {
            val clipDur = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(100L)
            accMs += clipDur
            candidates.add(accMs)
            // Add keyframes
            for (kf in clip.keyframes.keyframes) {
                candidates.add(accMs - clipDur + kf.timeMs)
            }
        }
    }
    if (_state.value.snapToBeat) {
        candidates.addAll(_state.value.beatMarkersMs)
    }
    if (candidates.isEmpty()) return timeMs
    val closest = candidates.minByOrNull { kotlin.math.abs(it - timeMs) } ?: return timeMs
    return if (kotlin.math.abs(closest - timeMs) <= snapThresholdMs) closest else timeMs
}


fun EditorViewModel.findNearestBeat(timeMs: Long, snapThresholdMs: Long = 180L): Long {
    if (!_state.value.snapToBeat) return timeMs
    val beats = _state.value.beatMarkersMs
    if (beats.isEmpty()) return timeMs
    val closest = beats.minByOrNull { kotlin.math.abs(it - timeMs) } ?: return timeMs
    return if (kotlin.math.abs(closest - timeMs) <= snapThresholdMs) closest else timeMs
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

internal fun EditorViewModel.applyAudioPitchToMainPlayer(semitones: Float) {
    val p = mainPlayerRef ?: return
    val pitch = Math.pow(2.0, (semitones / 12.0).toDouble()).toFloat()
    p.playbackParameters = androidx.media3.common.PlaybackParameters(1f, pitch)
}

fun EditorViewModel.toggleMute(trackId: String) = _audio.update { s ->
    s.copy(tracks = s.tracks.map { if (it.id == trackId) it.copy(isMuted = !it.isMuted) else it })
}

fun EditorViewModel.setTrackVolume(trackId: String, vol: Float) = _audio.update { s ->
    s.copy(tracks = s.tracks.map { if (it.id == trackId) it.copy(volume = vol.coerceIn(0f, 1f)) else it })
}

fun EditorViewModel.setTrackPanning(trackId: String, pan: Float) = _audio.update { s ->
    s.copy(tracks = s.tracks.map { if (it.id == trackId) it.copy(isSolo = pan > 0.5f) else it })
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


fun EditorViewModel.addAudioTrack(name: String, uri: String, kind: AudioTrack.Kind = AudioTrack.Kind.MUSIC, sourceDurationMs: Long = 0L) {
    val finalDuration = if (sourceDurationMs > 0L) sourceDurationMs else 30000L
    val track = AudioTrack(
        id = java.util.UUID.randomUUID().toString(),
        name = name,
        uri = uri,
        volume = if (kind == AudioTrack.Kind.SFX) 1f else 0.85f,
        trimStartMs = 0L,
        trimEndMs = finalDuration
    )
    _audio.update { it.copy(tracks = it.tracks + track) }
    _state.update { s ->
        val p = s.project ?: return@update s
        val updatedTracks = p.audioTracks + track
        val newDuration = maxOf(s.durationMs, finalDuration)
        s.copy(
            project = p.copy(audioTracks = updatedTracks),
            durationMs = newDuration
        )
    }
    persistProject()
}


fun EditorViewModel.removeAudioTrack(trackId: String) {
    _audio.update { s ->
        s.copy(tracks = s.tracks.filter { it.id != trackId })
    }
    _state.update { s ->
        val p = s.project ?: return@update s
        s.copy(project = p.copy(audioTracks = p.audioTracks.filter { it.id != trackId }))
    }
    persistProject()
}


fun EditorViewModel.setAudioTrackVolume(trackId: String, vol: Float) = _audio.update { s ->
    s.copy(tracks = s.tracks.map { if (it.id == trackId) it.copy(volume = vol.coerceIn(0f, 1f)) else it })
}


fun EditorViewModel.toggleAudioTrackMute(trackId: String) {
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


fun EditorViewModel.toggleAudioTrackSolo(trackId: String) = _audio.update { s ->
    s.copy(tracks = s.tracks.map { if (it.id == trackId) it.copy(isSolo = !it.isSolo) else it })
}


fun EditorViewModel.setAudioTrackTrim(trackId: String, startMs: Long, endMs: Long) = _audio.update { s ->
    s.copy(tracks = s.tracks.map {
        if (it.id == trackId) it.copy(trimStartMs = startMs.coerceAtLeast(0), trimEndMs = endMs.coerceAtLeast(startMs)) else it
    })
}


fun EditorViewModel.setAudioTrackFadeIn(trackId: String, ms: Long) = _audio.update { s ->
    s.copy(tracks = s.tracks.map { if (it.id == trackId) it.copy(fadeInMs = ms.coerceAtLeast(0)) else it })
}


fun EditorViewModel.setAudioTrackFadeOut(trackId: String, ms: Long) = _audio.update { s ->
    s.copy(tracks = s.tracks.map { if (it.id == trackId) it.copy(fadeOutMs = ms.coerceAtLeast(0)) else it })
}


fun EditorViewModel.setMuteOriginalVideo(muted: Boolean) {
    _audio.update { it.copy(isMuted = muted) }
}


fun EditorViewModel.splitAudioTrack(trackId: String, atMs: Long) {
    pushUndo()
    _state.update { s ->
        val p = s.project ?: return@update s
        val track = p.audioTracks.firstOrNull { it.id == trackId } ?: return@update s
        val splitPoint = atMs.coerceIn(track.trimStartMs + 100L, track.trimEndMs - 100L)
        val part1 = track.copy(trimEndMs = splitPoint)
        val part2 = track.copy(
            id = "audio_${System.currentTimeMillis()}",
            trimStartMs = splitPoint
        )
        val updated = mutableListOf<com.apexstudio.app.domain.model.AudioTrack>()
        for (t in p.audioTracks) {
            if (t.id == trackId) {
                updated.add(part1)
                updated.add(part2)
            } else {
                updated.add(t)
            }
        }
        s.copy(project = p.copy(audioTracks = updated))
    }
    persistProject()
}


fun EditorViewModel.updateAudioTrackVolume(trackId: String, volume: Float) {
    _state.update { s ->
        val p = s.project ?: return@update s
        val updated = p.audioTracks.map {
            if (it.id == trackId) it.copy(volume = volume.coerceIn(0f, 1f)) else it
        }
        s.copy(project = p.copy(audioTracks = updated))
    }
    persistProject()
}
