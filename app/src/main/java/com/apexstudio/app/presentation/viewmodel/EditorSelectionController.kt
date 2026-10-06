package com.apexstudio.app.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import com.apexstudio.app.data.picker.MediaMetadata
import com.apexstudio.app.presentation.state.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * CapCut-style contextual toolbar support: single selection across clips
 * and the A1 audio lane, per-clip volume, and the Replace flow.
 *
 * Selection is mutually exclusive — tapping a clip clears the audio-lane
 * selection and vice versa, mirroring CapCut's contextual toolbar.
 */

/** Selects an A1 audio track (clears any clip selection). */
fun EditorViewModel.selectAudioTrack(id: String?) = _state.update { state ->
    state.copy(
        selectedAudioTrackId = id,
        selectedClipId = if (id != null) null else state.selectedClipId
    )
}

/** Clears every selection — the contextual toolbar returns to global tools. */
fun EditorViewModel.clearSelection() = _state.update {
    it.copy(selectedClipId = null, selectedAudioTrackId = null)
}

/**
 * Per-clip audio gain, 0 (silent) .. 2 (boosted). Applied live to the
 * ExoPlayer preview of the clip and multiplied into the export volume,
 * so the Volume tool is real in both preview and export.
 */
fun EditorViewModel.setClipVolume(clipId: String, volume: Float) {
    val safe = volume.coerceIn(0f, 2f)
    _state.update { s ->
        val p = s.project ?: return@update s
        val updated = p.clips.map { if (it.id == clipId) it.copy(volume = safe) else it }
        s.copy(project = p.copy(clips = updated))
    }
    persistProject()
}

fun EditorViewModel.openClipVolumeSheet() = _state.update { it.copy(clipVolumeSheetOpen = true) }

fun EditorViewModel.closeClipVolumeSheet() = _state.update { it.copy(clipVolumeSheetOpen = false) }

fun EditorViewModel.openAudioFadeSheet() = _state.update { it.copy(audioFadeSheetOpen = true) }

fun EditorViewModel.closeAudioFadeSheet() = _state.update { it.copy(audioFadeSheetOpen = false) }

/** Arms the Replace flow: the next media-picker result swaps this clip's media. */
fun EditorViewModel.setPendingReplaceClip(clipId: String?) = _state.update {
    it.copy(pendingReplaceClipId = clipId, pendingReplaceAudioTrackId = null)
}

/** Arms the Replace flow for an A1 audio track. */
fun EditorViewModel.setPendingReplaceAudioTrack(trackId: String?) = _state.update {
    it.copy(pendingReplaceAudioTrackId = trackId, pendingReplaceClipId = null)
}

fun EditorViewModel.clearPendingReplace() = _state.update {
    it.copy(pendingReplaceClipId = null, pendingReplaceAudioTrackId = null)
}

/**
 * Replaces a clip's media with newly picked media while keeping its
 * identity, timeline position, speed, keyframes, overlays and type.
 * Trims are clamped to the new source duration.
 */
fun EditorViewModel.replaceClipMedia(clipId: String, meta: MediaMetadata) {
    _state.update { s ->
        val p = s.project ?: return@update s
        val newDur = meta.durationMs.coerceAtLeast(500L)
        val updated = p.clips.map { clip ->
            if (clip.id != clipId) return@map clip
            val trimEnd = clip.trimEndMs.coerceAtMost(newDur).coerceAtLeast(500L)
            val trimStart = clip.trimStartMs.coerceAtMost(trimEnd - 100L).coerceAtLeast(0L)
            clip.copy(
                name = meta.name,
                uri = meta.uri,
                durationMs = newDur,
                trimStartMs = trimStart,
                trimEndMs = trimEnd,
                thumbnail = null
            )
        }
        val maxDuration = updated.maxOfOrNull {
            it.timelineOffsetMs + (it.trimEndMs - it.trimStartMs).coerceAtLeast(100L)
        } ?: s.durationMs
        s.copy(
            project = p.copy(clips = updated),
            durationMs = maxDuration,
            pendingReplaceClipId = null
        )
    }
    persistProject()
}

/**
 * Replaces an A1 audio track's media, keeping its id, volume, mute/solo
 * and fade settings. Trims reset to the full new source.
 * Updates both the project list and the mixer list (they are kept in sync).
 */
fun EditorViewModel.replaceAudioTrackMedia(trackId: String, meta: MediaMetadata) {
    _state.update { s ->
        val p = s.project ?: return@update s
        val newDur = meta.durationMs.coerceAtLeast(500L)
        val updated = p.audioTracks.map { track ->
            if (track.id != trackId) return@map track
            track.copy(
                name = meta.name,
                uri = meta.uri,
                trimStartMs = 0L,
                trimEndMs = newDur
            )
        }
        s.copy(
            project = p.copy(audioTracks = updated),
            pendingReplaceAudioTrackId = null,
            // Beats were computed for the old audio — drop them.
            beatMarkersMs = emptyList(),
            beatSourceTrackId = null
        )
    }
    _audio.update { s ->
        val newDur = meta.durationMs.coerceAtLeast(500L)
        s.copy(tracks = s.tracks.map { track ->
            if (track.id != trackId) return@map track
            track.copy(name = meta.name, uri = meta.uri, trimStartMs = 0L, trimEndMs = newDur)
        })
    }
    persistProject()
}

/**
 * Audio-track volume that is real everywhere: updates both the project
 * list (timeline preview via AudioPlaybackManager) and the mixer list
 * (audio export), following the toggleAudioTrackMute dual-write pattern.
 */
fun EditorViewModel.setAudioTrackVolumeFull(trackId: String, volume: Float) {
    val safe = volume.coerceIn(0f, 1f)
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
 * Editor layout backup (New contextual vs Classic legacy).
 * Persisted in DataStore so the choice survives app restarts.
 */
fun EditorViewModel.setClassicEditorLayout(classic: Boolean) {
    _state.update { it.copy(useClassicEditorLayout = classic) }
    viewModelScope.launch {
        val ctx = context ?: return@launch
        try {
            com.apexstudio.app.data.settings.EditorLayoutPrefs(ctx).setClassicEditorLayout(classic)
        } catch (e: Exception) {
            android.util.Log.w("EditorViewModel", "Failed to persist editor layout: ${e.message}")
        }
    }
}

fun EditorViewModel.loadEditorLayoutPref() {
    viewModelScope.launch {
        val ctx = context ?: return@launch
        try {
            val classic = first(
                com.apexstudio.app.data.settings.EditorLayoutPrefs(ctx).classicEditorLayout
            )
            _state.update { it.copy(useClassicEditorLayout = classic) }
        } catch (e: Exception) {
            android.util.Log.w("EditorViewModel", "Failed to load editor layout: ${e.message}")
        }
    }
}
