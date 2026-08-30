package com.apexstudio.app.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apexstudio.app.data.engine.AudioEngine
import com.apexstudio.app.data.engine.ColorGradingEngine
import com.apexstudio.app.data.export.ExportEngine
import com.apexstudio.app.data.media.MediaAnalyzer
import com.apexstudio.app.data.picker.MediaPickerHelper
import com.apexstudio.app.data.repository.MediaRepository
import com.apexstudio.app.domain.model.*
import com.apexstudio.app.presentation.state.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EditorViewModel(
    private val repo: MediaRepository = MediaRepository,
    private val context: android.content.Context? = null,
    private val projectId: String? = null
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

    private val mediaPicker = context?.let { MediaPickerHelper(it) }
    private val mediaAnalyzer = context?.let { MediaAnalyzer() }
    private val exportEngine = context?.let { ExportEngine(it) }
    private val audioEngine = context?.let { AudioEngine(it) }
    private val colorGradingEngine = ColorGradingEngine()

    init {
        loadProject()
        loadLuts()
        loadAudioState()
        audioEngine?.startRecording()
    }

    fun setContext(ctx: android.content.Context) {
        // Context already provided via constructor; this is for runtime access
    }

    private fun loadProject() {
        viewModelScope.launch {
            val projects = repo.loadProjects()
            val p = projectId?.let { id -> projects.firstOrNull { it.id == id } }
                ?: projects.firstOrNull()
            if (p == null) {
                _state.update { it.copy(project = null, durationMs = 0L) }
                return@launch
            }
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

    fun openMediaPicker() = _state.update { it.copy(isMediaPickerOpen = true) }
    fun closeMediaPicker() = _state.update { it.copy(isMediaPickerOpen = false) }

    fun onMediaPicked(mediaList: List<com.apexstudio.app.data.picker.MediaMetadata>) {
        viewModelScope.launch {
            val s = _state.value
            val newClips = mediaList.mapNotNull { meta ->
                mediaPicker?.toMediaClip(meta, s.project?.clips?.size ?: 0)
            }
            val existingClips = s.project?.clips ?: emptyList()

            val firstVideo = newClips.firstOrNull { it.type == ClipType.VIDEO }
            val waveform = if (firstVideo != null && context != null) {
                mediaAnalyzer?.analyzeAudioWaveform(firstVideo.uri, context)?.samples ?: FloatArray(0)
            } else {
                FloatArray(0)
            }

            val updatedProject = s.project?.copy(clips = existingClips + newClips)
            val maxDuration = updatedProject?.clips?.maxOfOrNull { it.durationMs } ?: s.durationMs

            _state.update {
                it.copy(
                    project = updatedProject,
                    durationMs = maxDuration,
                    pickedMedia = mediaList,
                    isMediaPickerOpen = false,
                    audioWaveform = waveform
                )
            }
        }
    }

    fun togglePlay() = _state.update { it.copy(isPlaying = !it.isPlaying) }
    fun setPlaying(v: Boolean) = _state.update { it.copy(isPlaying = v) }
    fun seekTo(ms: Long) = _state.update {
        val clamped = ms.coerceIn(0, it.durationMs)
        it.copy(currentTimeMs = clamped, playerPositionMs = clamped)
    }
    fun stepFrame(forward: Boolean) = _state.update {
        val step = 33L
        val next = if (forward) it.currentTimeMs + step else (it.currentTimeMs - step).coerceAtLeast(0)
        it.copy(currentTimeMs = next, playerPositionMs = next)
    }
    fun setZoom(z: Float) = _state.update { it.copy(zoomLevel = z.coerceIn(0.5f, 4f)) }
    fun selectTool(t: EditorTool) = _state.update { it.copy(selectedTool = t) }
    fun selectClip(id: String?) = _state.update { it.copy(selectedClipId = id) }
    fun setPlayerPosition(ms: Long) = _state.update { it.copy(playerPositionMs = ms) }
    fun setPlayerDuration(ms: Long) = _state.update { it.copy(playerDurationMs = ms) }
    fun setPlayerReady(ready: Boolean) = _state.update { it.copy(isPlayerReady = ready) }

    fun updateExport(upd: (ExportSettings) -> ExportSettings) {
        _export.update { it.copy(settings = upd(it.settings)) }
    }

    fun startExport() {
        _export.update { it.copy(isExporting = true, progress = 0f) }
        val inputUri = _state.value.project?.clips?.firstOrNull()?.uri ?: return
        exportEngine?.startExport(
            inputUri,
            ExportEngine.ExportConfig(
                resolution = _export.value.settings.resolution,
                fps = _export.value.settings.frameRate,
                quality = _export.value.settings.quality.label.lowercase()
            )
        )
    }

    fun setExportProgress(p: Float) = _export.update {
        it.copy(progress = p, isExporting = p < 1f)
    }

    fun setExportOutputUri(uri: String?) = _export.update { it.copy(outputUri = uri) }
    fun setExportError(error: String?) = _export.update { it.copy(error = error) }

    fun updateColorBrightness(v: Float) = _color.update { it.copy(brightness = v) }
    fun updateColorContrast(v: Float) = _color.update { it.copy(contrast = v.coerceIn(0f, 3f)) }
    fun updateColorSaturation(v: Float) = _color.update { it.copy(saturation = v.coerceIn(0f, 3f)) }
    fun updateColorShadows(v: Float) = _color.update { it.copy(shadows = v) }
    fun updateColorMidtones(v: Float) = _color.update { it.copy(midtones = v) }
    fun updateColorHighlights(v: Float) = _color.update { it.copy(highlights = v) }
    fun setColorChannel(c: ColorToolState.Channel) = _color.update { it.copy(activeChannel = c) }
    fun selectLut(id: String) = _color.update { it.copy(selectedLut = id) }

    fun applyBrightness(v: Float) { colorGradingEngine.applyBrightness(v) }
    fun applyContrast(v: Float) { colorGradingEngine.applyContrast(v) }
    fun applySaturation(v: Float) { colorGradingEngine.applySaturation(v) }
    fun applyShadows(v: Float) { colorGradingEngine.applyShadows(v) }
    fun applyMidtones(v: Float) { colorGradingEngine.applyMidtones(v) }
    fun applyHighlights(v: Float) { colorGradingEngine.applyHighlights(v) }
    fun applyLut(lutId: String) { colorGradingEngine.applyLut(lutId) }

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

    fun setLowEQ(gain: Short) {
        audioEngine?.setLowGain(gain)
        _audio.update { it.copy(lowEQ = gain) }
    }
    fun setMidEQ(gain: Short) {
        audioEngine?.setMidGain(gain)
        _audio.update { it.copy(midEQ = gain) }
    }
    fun setHighEQ(gain: Short) {
        audioEngine?.setHighGain(gain)
        _audio.update { it.copy(highEQ = gain) }
    }
    fun setVolume(vol: Float) {
        audioEngine?.setVolume(vol)
        _audio.update { it.copy(volume = vol.coerceIn(0f, 1f)) }
    }
    fun toggleMuteEngine() {
        audioEngine?.toggleMute()
        _audio.update { it.copy(isMuted = !it.isMuted) }
    }
    fun toggleSoloEngine() {
        audioEngine?.toggleSolo()
        _audio.update { it.copy(isSolo = !it.isSolo) }
    }
    fun setNoiseReduction(level: Float) {
        audioEngine?.setNoiseReduction(level)
        _audio.update { it.copy(noiseReduction = level) }
    }
    fun toggleEchoCancellation(enabled: Boolean) {
        audioEngine?.toggleEchoCancellation(enabled)
        _audio.update { it.copy(echoCancellation = enabled) }
    }
    fun toggleNoiseSuppression(enabled: Boolean) {
        audioEngine?.toggleNoiseSuppression(enabled)
        _audio.update { it.copy(noiseSuppression = enabled) }
    }
    fun setWaveformSamples(samples: FloatArray) {
        _audio.update { it.copy(waveformSamples = samples) }
    }
    fun setRecordingState(recording: Boolean) {
        _audio.update { it.copy(isRecording = recording) }
    }

    fun analyzeAudio(uri: String) {
        viewModelScope.launch {
            val data = mediaAnalyzer?.analyzeAudioWaveform(uri, context ?: return@launch)
            data?.samples?.let { setWaveformSamples(it) }
        }
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
            if (atMs <= c.trimStartMs || atMs >= c.trimEndMs) return@update s

            val originalEnd = c.trimEndMs
            val newClip = c.copy(
                id = c.id + "_split",
                trimStartMs = atMs,
                trimEndMs = originalEnd,
                durationMs = originalEnd - atMs
            )
            s.copy(project = s.project.copy(
                clips = s.project.clips.map {
                    if (it.id == clipId) it.copy(trimEndMs = atMs, durationMs = atMs - it.trimStartMs)
                    else it
                } + newClip
            ))
        }
    }

    fun cutClipAtPlayhead() {
        val clipId = _state.value.selectedClipId ?: return
        val atMs = _state.value.currentTimeMs
        pushUndo()
        _state.update { s ->
            s.copy(project = s.project?.copy(
                clips = s.project.clips.map {
                    if (it.id == clipId) {
                        val end = atMs.coerceIn(it.trimStartMs, it.trimEndMs)
                        it.copy(trimEndMs = end, durationMs = end - it.trimStartMs)
                    } else it
                }
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

    override fun onCleared() {
        super.onCleared()
        audioEngine?.stopRecording()
        audioEngine?.release()
        exportEngine?.release()
        colorGradingEngine.release()
    }
}
