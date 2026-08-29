package com.apexstudio.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apexstudio.app.data.repository.MediaRepository
import com.apexstudio.app.domain.model.*
import com.apexstudio.app.presentation.state.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EditorViewModel(
    private val repo: MediaRepository = MediaRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val _export = MutableStateFlow(ExportState())
    val export: StateFlow<ExportState> = _export.asStateFlow()

    private val _color = MutableStateFlow(ColorToolState())
    val color: StateFlow<ColorToolState> = _color.asStateFlow()

    private val _audio = MutableStateFlow(AudioStudioState())
    val audio: StateFlow<AudioStudioState> = _audio.asStateFlow()

    private val _luts = MutableStateFlow<List<LutPreset>>(emptyList())
    val luts: StateFlow<List<LutPreset>> = _luts.asStateFlow()

    private val _transitions = MutableStateFlow<List<ToolItem>>(emptyList())
    val transitions: StateFlow<List<ToolItem>> = _transitions.asStateFlow()

    private val _fx = MutableStateFlow<List<ToolItem>>(emptyList())
    val fx: StateFlow<List<ToolItem>> = _fx.asStateFlow()

    private val undoStack = ArrayDeque<List<MediaClip>>()
    private val redoStack = ArrayDeque<List<MediaClip>>()

    init {
        loadProject()
        loadLuts()
        loadAudioState()
    }

    private fun loadProject() {
        viewModelScope.launch {
            val p = repo.loadProjects().first()
            _state.update {
                it.copy(
                    project = p,
                    durationMs = p.durationMs,
                    canUndo = false, canRedo = false
                )
            }
        }
    }

    private fun loadLuts() {
        _luts.value = repo.loadLutPresets()
        _transitions.value = repo.loadTransitionPresets()
        _fx.value = repo.loadFxPresets()
    }

    private fun loadAudioState() {
        _audio.update { it.copy(tracks = repo.loadProjects().first().audioTracks) }
    }

    fun togglePlay() = _state.update { it.copy(isPlaying = !it.isPlaying) }
    fun setPlaying(v: Boolean) = _state.update { it.copy(isPlaying = v) }
    fun seekTo(ms: Long) = _state.update { it.copy(currentTimeMs = ms.coerceIn(0, it.durationMs)) }
    fun stepFrame(forward: Boolean) = _state.update {
        val step = 33L
        val next = if (forward) it.currentTimeMs + step else (it.currentTimeMs - step).coerceAtLeast(0)
        it.copy(currentTimeMs = next)
    }
    fun setZoom(z: Float) = _state.update { it.copy(zoomLevel = z.coerceIn(0.5f, 4f)) }
    fun selectTool(t: EditorTool) = _state.update { it.copy(selectedTool = t) }
    fun selectClip(id: String?) = _state.update { it.copy(selectedClipId = id) }

    fun updateExport(upd: (ExportSettings) -> ExportSettings) {
        _export.update { it.copy(settings = upd(it.settings)) }
    }

    fun startExport() = _export.update { it.copy(isExporting = true, progress = 0f) }
    fun setExportProgress(p: Float) = _export.update {
        it.copy(progress = p, isExporting = p < 1f)
    }

    fun updateColorShadows(v: Float) = _color.update { it.copy(shadows = v) }
    fun updateColorMidtones(v: Float) = _color.update { it.copy(midtones = v) }
    fun updateColorHighlights(v: Float) = _color.update { it.copy(highlights = v) }
    fun setColorChannel(c: ColorToolState.Channel) = _color.update { it.copy(activeChannel = c) }
    fun selectLut(id: String) = _color.update { it.copy(selectedLut = id) }

    fun setAudioBpm(b: Int) {
        _audio.update { it.copy(bpm = b) }
        _state.update { it.copy(bpm = b) }
    }
    fun toggleAiVoice() = _audio.update { it.copy(aiVoiceEnhance = !it.aiVoiceEnhance) }
    fun setClarity(v: Float) = _audio.update { it.copy(clarity = v) }
    fun setReduceNoise(v: Float) = _audio.update { it.copy(reduceNoise = v) }
    fun toggleMute(trackId: String) = _audio.update { s ->
        s.copy(tracks = s.tracks.map { if (it.id == trackId) it.copy(isMuted = !it.isMuted) else it })
    }
    fun setTrackVolume(trackId: String, vol: Float) = _audio.update { s ->
        s.copy(tracks = s.tracks.map { if (it.id == trackId) it.copy(volume = vol) else it })
    }
    fun setTrackPanning(trackId: String, pan: Float) = _audio.update { s ->
        s.copy(tracks = s.tracks.map { if (it.id == trackId) it.copy(isSolo = pan > 0.5f) else it })
    }

    fun undo() = viewModelScope.launch {
        val current = _state.value.project?.clips ?: return@launch
        if (undoStack.isEmpty()) return@launch
        redoStack.addLast(current)
        val prev = undoStack.removeLast()
        _state.update {
            it.copy(project = it.project?.copy(clips = prev),
                canUndo = undoStack.isNotEmpty(), canRedo = true)
        }
    }

    fun redo() = viewModelScope.launch {
        val current = _state.value.project?.clips ?: return@launch
        if (redoStack.isEmpty()) return@launch
        undoStack.addLast(current)
        val next = redoStack.removeLast()
        _state.update {
            it.copy(project = it.project?.copy(clips = next),
                canUndo = true, canRedo = redoStack.isNotEmpty())
        }
    }

    fun trimClip(clipId: String, startMs: Long, endMs: Long) {
        pushUndo()
        _state.update { s ->
            s.copy(project = s.project?.copy(
                clips = s.project.clips.map { c ->
                    if (c.id == clipId) c.copy(
                        trimStartMs = startMs.coerceAtLeast(0),
                        trimEndMs = endMs.coerceAtMost(c.durationMs)
                    ) else c
                }
            ))
        }
    }

    fun splitClip(clipId: String, atMs: Long) {
        pushUndo()
        _state.update { s ->
            val c = s.project?.clips?.firstOrNull { it.id == clipId } ?: return@update s
            val newClip = c.copy(
                id = c.id + "_b", trimStartMs = atMs,
                trimEndMs = c.trimEndMs, durationMs = c.trimEndMs
            )
            s.copy(project = s.project.copy(
                clips = s.project.clips.map { if (it.id == clipId) it.copy(trimEndMs = atMs) else it } + newClip
            ))
        }
    }

    private fun pushUndo() {
        val current = _state.value.project?.clips ?: return
        undoStack.addLast(current)
        if (undoStack.size > 50) undoStack.removeFirst()
        redoStack.clear()
        _state.update { it.copy(canUndo = true, canRedo = false) }
    }
}
