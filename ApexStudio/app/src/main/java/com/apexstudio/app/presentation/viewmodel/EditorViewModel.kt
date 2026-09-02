package com.apexstudio.app.presentation.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.graphics.Bitmap
import com.apexstudio.app.data.crashlog.CrashMarker
import com.apexstudio.app.data.engine.AudioEngine
import com.apexstudio.app.data.engine.ColorGradingEngine
import com.apexstudio.app.data.export.ExportEngine
import com.apexstudio.app.data.media.MediaAnalyzer
import com.apexstudio.app.data.media.VideoThumbnailExtractor
import com.apexstudio.app.data.picker.MediaPickerHelper
import com.apexstudio.app.data.repository.MediaRepository
import com.apexstudio.app.data.repository.ProjectRepository
import com.apexstudio.app.domain.model.*
import com.apexstudio.app.presentation.state.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

    // Thumbnail bitmaps keyed by clip ID → list of (timeMs, bitmap)
    private val _thumbnails = MutableStateFlow<Map<String, List<Pair<Long, android.graphics.Bitmap>>>>(emptyMap())
    val thumbnails: StateFlow<Map<String, List<Pair<Long, android.graphics.Bitmap>>>> = _thumbnails.asStateFlow()

    private val undoStack = ArrayDeque<List<MediaClip>>()
    private val redoStack = ArrayDeque<List<MediaClip>>()

    private val mediaPicker = context?.let { MediaPickerHelper(it) }
    private val mediaAnalyzer = context?.let { MediaAnalyzer() }
    private val exportEngine = context?.let { ExportEngine(it) }
    private val audioEngine = context?.let { AudioEngine(it) }
    private val colorGradingEngine = ColorGradingEngine()
    private val projectRepository: ProjectRepository? = context?.let { ProjectRepository(it) }

    init {
        Log.d("ApexTrace", "EditorViewModel.init start")
        context?.let { CrashMarker.mark(it, "EditorViewModel.init start") }
        loadProject()
        loadLuts()
        loadAudioState()
        Log.d("ApexTrace", "EditorViewModel.init end")
        context?.let { CrashMarker.mark(it, "EditorViewModel.init completed") }
    }

    fun setContext(ctx: android.content.Context) {
        // Context already provided via constructor; this is for runtime access
    }

    private fun loadProject() {
        viewModelScope.launch {
            // Prefer the persistent DataStore copy over the in-memory
            // MediaRepository stub. The two stay in sync because every
            // mutation auto-saves back to DataStore.
            val projects = projectRepository?.loadAll()?.first() ?: repo.loadProjects()
            val p = projectId?.let { id -> projects.firstOrNull { it.id == id } }
                ?: projects.firstOrNull()
            if (p == null) {
                _state.update { it.copy(project = null, durationMs = 0L) }
                return@launch
            }
            // Auto-select the first video clip in the project AND
            // flip isPlaying = true so the play/pause effect drives
            // ExoPlayer into STATE_READY + playback as soon as the
            // media-prep effect has queued the media item. The
            // previous behaviour left both null/false on load, so
            // the preview sat black until the user pressed play.
            val firstClipId = p.clips.firstOrNull { it.type == ClipType.VIDEO }?.id
                ?: p.clips.firstOrNull()?.id
            _state.update {
                it.copy(
                    project = p,
                    durationMs = p.durationMs,
                    canUndo = false, canRedo = false,
                    selectedClipId = it.selectedClipId ?: firstClipId,
                    isPlaying = firstClipId != null
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

    fun onMediaPicked(mediaList: List<com.apexstudio.app.data.picker.MediaMetadata>, replace: Boolean = false) {
        viewModelScope.launch {
            val s = _state.value
            val newClips = mediaList.mapNotNull { meta ->
                mediaPicker?.toMediaClip(meta, s.project?.clips?.size ?: 0)
            }
            val existingClips = if (replace) emptyList() else (s.project?.clips ?: emptyList())

            val firstVideo = newClips.firstOrNull { it.type == ClipType.VIDEO }
            val waveform = if (firstVideo != null && context != null) {
                mediaAnalyzer?.analyzeAudioWaveform(firstVideo.uri, context)?.samples ?: FloatArray(0)
            } else {
                FloatArray(0)
            }

            val updatedClips = existingClips + newClips
            val updatedProject = s.project?.copy(clips = updatedClips)
            val maxDuration = updatedClips.maxOfOrNull { it.durationMs } ?: s.durationMs

            _state.update {
                it.copy(
                    project = updatedProject,
                    durationMs = maxDuration,
                    pickedMedia = mediaList,
                    isMediaPickerOpen = false,
                    audioWaveform = waveform,
                    // Replace: switch the preview to the first new clip.
                    // Append: keep the current selection if there is one,
                    // otherwise jump to the first new clip so the preview
                    // isn't stuck on an empty timeline.
                    selectedClipId = when {
                        replace -> newClips.firstOrNull()?.id
                        it.selectedClipId != null -> it.selectedClipId
                        else -> newClips.firstOrNull()?.id
                    },
                    // Auto-play as soon as the user adds media. The
                    // previous behaviour waited for the user to press
                    // the play button, which felt broken when the
                    // video had clearly loaded onto the timeline.
                    // The isPlaying flag drives the EditorScreen's
                    // player.play() LaunchedEffect, so the preview
                    // starts immediately.
                    isPlaying = true
                )
            }
            // Auto-save so the freshly-added clip survives a process
            // death / app restart. Without this, the editor's state
            // would be lost the moment the user backgrounds the app.
            persistProject()
            // Kick off thumbnail extraction for each new clip
            for (clip in newClips) {
                if (clip.type == ClipType.VIDEO && context != null) {
                    loadClipThumbnails(clip)
                }
            }
        }
    }

    private fun loadClipThumbnails(clip: MediaClip) {
        val ctx = context ?: return
        viewModelScope.launch {
            try {
                val thumbs = VideoThumbnailExtractor.extractStrip(
                    context = ctx,
                    videoUri = clip.uri,
                    durationMs = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(1000L),
                    count = 12
                )
                _thumbnails.update { current ->
                    current + (clip.id to thumbs)
                }
            } catch (e: Exception) {
                Log.e("EditorViewModel", "Thumbnail extraction failed for ${clip.id}", e)
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
    /**
     * Multiplicative zoom update driven by the pinch gesture.
     * [factor] is the per-frame relative zoom (e.g. 1.05 for 5%
     * zoom-in). The new level is the current level × factor, then
     * clamped to the 0.5x..4x range.
     */
    fun multiplyZoom(factor: Float) = _state.update {
        val current = it.zoomLevel
        val next = (current * factor).coerceIn(0.5f, 4f)
        it.copy(zoomLevel = next)
    }
    fun selectTool(t: EditorTool) = _state.update { it.copy(selectedTool = t) }
    fun selectClip(id: String?) = _state.update { it.copy(selectedClipId = id) }
    fun setPlayerPosition(ms: Long) = _state.update { it.copy(playerPositionMs = ms) }
    fun setPlayerDuration(ms: Long) = _state.update { it.copy(playerDurationMs = ms) }
    fun setPlayerReady(ready: Boolean) = _state.update { it.copy(isPlayerReady = ready) }
    fun setVideoSize(width: Int, height: Int) = _state.update { it.copy(videoWidth = width, videoHeight = height) }

    // ---- Crop ----
    fun setCropMode(enabled: Boolean) = _state.update { it.copy(cropMode = enabled) }
    fun setCropRect(rect: CropRect) = _state.update { it.copy(cropRect = rect) }
    /**
     * Apply one of the preset aspect ratios. Anchors the crop to the
     * current centre of the existing rectangle, expanding/contracting
     * evenly so the user doesn't lose the framing they already had.
     */
    fun applyCropAspect(aspect: CropAspect) {
        _state.update { s ->
            val target = aspect.ratio ?: return@update s.copy(cropAspect = aspect)
            val current = s.cropRect
            val currentAspect = current.width / current.height
            val (newW, newH) = if (currentAspect > target) {
                val w = (current.height * target).coerceAtMost(1f)
                w to current.height
            } else {
                val h = (current.width / target).coerceAtMost(1f)
                current.width to h
            }
            val cx = (current.left + current.right) / 2f
            val cy = (current.top + current.bottom) / 2f
            val l = (cx - newW / 2f).coerceIn(0f, 1f - newW)
            val t = (cy - newH / 2f).coerceIn(0f, 1f - newH)
            s.copy(
                cropAspect = aspect,
                cropRect = CropRect(l, t, l + newW, t + newH)
            )
        }
    }
    fun resetCrop() = _state.update { it.copy(cropRect = CropRect.Full, cropAspect = CropAspect.FREE) }

    // ---- Filters ----
    fun openFilterPanel() = _state.update { it.copy(filterPanelOpen = true) }
    fun closeFilterPanel() = _state.update { it.copy(filterPanelOpen = false) }
    fun setFilterCategory(id: String) = _state.update { it.copy(filterCategory = id) }
    /** id == null clears the active filter (back to original video). */
    fun setActiveFilter(id: String?) = _state.update { it.copy(activeFilterId = id) }
    fun setFilterIntensity(v: Float) = _state.update { it.copy(filterIntensity = v.coerceIn(0f, 1f)) }

    fun openAudioMixer() = _state.update { it.copy(audioMixerOpen = true) }
    fun closeAudioMixer() = _state.update { it.copy(audioMixerOpen = false) }
    fun openSpeedPanel() = _state.update { it.copy(speedPanelOpen = true) }
    fun closeSpeedPanel() = _state.update { it.copy(speedPanelOpen = false) }
    fun setPlaybackSpeed(speed: Float) = _state.update { it.copy(playbackSpeed = speed.coerceIn(0.25f, 8f)) }

    fun updateExport(upd: (ExportSettings) -> ExportSettings) {
        _export.update { it.copy(settings = upd(it.settings)) }
    }

    fun startExport() {
        _export.update { it.copy(isExporting = true, progress = 0f) }
        val selected = _state.value.project?.clips?.firstOrNull { it.id == _state.value.selectedClipId }
            ?: _state.value.project?.clips?.firstOrNull()
        val inputUri = selected?.uri ?: return
        // Reuse the selected clip's speedMultiplier so the exported
        // MP4 matches the in-editor preview rate. If no clip is
        // selected, fall back to the global playback speed slider
        // (useful for projects that haven't loaded a clip yet).
        val speed = selected?.speedMultiplier ?: _state.value.playbackSpeed
        exportEngine?.startExport(
            inputUri,
            ExportEngine.ExportConfig(
                resolution = _export.value.settings.resolution,
                fps = _export.value.settings.frameRate,
                quality = _export.value.settings.quality.label.lowercase(),
                clipSpeed = speed,
                keyframes = selected?.keyframes ?: KeyframeTrack()
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

    // ---- Speed ramping ----
    /**
     * Set the playback speed of a single clip. multiplier is clamped
     * to the supported range (0.25x .. 8x). Pass [updatePlayback] = true
     * (default) to also push the value to the live ExoPlayer via
     * `setPlaybackSpeed` — call sites that only persist the value for
     * later export can pass false to avoid touching the player.
     */
    fun setClipSpeed(clipId: String, multiplier: Float) {
        val clamped = multiplier.coerceIn(SpeedPreset.QUARTER.multiplier, SpeedPreset.FAST.multiplier)
        updateClip(clipId) { it.copy(speedMultiplier = clamped) }
        val selectedSpeed = clamped
        _state.update { it.copy(playbackSpeed = selectedSpeed) }
    }

    fun setClipSpeedCurve(clipId: String, curve: SpeedCurve) =
        updateClip(clipId) { it.copy(speedCurve = curve) }

    fun setClipSpeedRamp(clipId: String, start: Float, end: Float) {
        val s = start.coerceIn(0.25f, 8f)
        val e = end.coerceIn(0.25f, 8f)
        updateClip(clipId) { it.copy(rampStartSpeed = s, rampEndSpeed = e, speedCurve = SpeedCurve.RAMP) }
    }

    fun applySpeedPreset(clipId: String, preset: SpeedPreset) {
        setClipSpeed(clipId, preset.multiplier)
    }

    // ---- Audio mixer ----
    /** Add a new audio track (background music, voiceover, SFX). */
    fun addAudioTrack(name: String, uri: String, kind: AudioTrack.Kind = AudioTrack.Kind.MUSIC, sourceDurationMs: Long = 0L) {
        val track = AudioTrack(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            uri = uri,
            volume = if (kind == AudioTrack.Kind.SFX) 1f else 0.75f,
            trimEndMs = sourceDurationMs
        )
        _audio.update { it.copy(tracks = it.tracks + track) }
    }

    fun removeAudioTrack(trackId: String) = _audio.update { s ->
        s.copy(tracks = s.tracks.filter { it.id != trackId })
    }

    fun setAudioTrackVolume(trackId: String, vol: Float) = _audio.update { s ->
        s.copy(tracks = s.tracks.map { if (it.id == trackId) it.copy(volume = vol.coerceIn(0f, 1f)) else it })
    }

    fun toggleAudioTrackMute(trackId: String) = _audio.update { s ->
        s.copy(tracks = s.tracks.map { if (it.id == trackId) it.copy(isMuted = !it.isMuted) else it })
    }

    fun toggleAudioTrackSolo(trackId: String) = _audio.update { s ->
        s.copy(tracks = s.tracks.map { if (it.id == trackId) it.copy(isSolo = !it.isSolo) else it })
    }

    fun setAudioTrackTrim(trackId: String, startMs: Long, endMs: Long) = _audio.update { s ->
        s.copy(tracks = s.tracks.map {
            if (it.id == trackId) it.copy(trimStartMs = startMs.coerceAtLeast(0), trimEndMs = endMs.coerceAtLeast(startMs)) else it
        })
    }

    fun setAudioTrackFadeIn(trackId: String, ms: Long) = _audio.update { s ->
        s.copy(tracks = s.tracks.map { if (it.id == trackId) it.copy(fadeInMs = ms.coerceAtLeast(0)) else it })
    }

    fun setAudioTrackFadeOut(trackId: String, ms: Long) = _audio.update { s ->
        s.copy(tracks = s.tracks.map { if (it.id == trackId) it.copy(fadeOutMs = ms.coerceAtLeast(0)) else it })
    }

    /** Mute / unmute the original video audio on the V1 track. */
    fun setMuteOriginalVideo(muted: Boolean) {
        _audio.update { it.copy(isMuted = muted) }
    }

    private fun updateClip(clipId: String, transform: (MediaClip) -> MediaClip) {
        _state.update { s ->
            val proj = s.project ?: return@update s
            val newClips = proj.clips.map { if (it.id == clipId) transform(it) else it }
            s.copy(project = proj.copy(clips = newClips))
        }
        persistProject()
    }

    // ---- Keyframes ----
    /**
     * Add a new keyframe at [timeMs] on [clipId]. If a keyframe
     * already exists at the same timestamp it's overwritten — that
     * matches the CapCut behaviour where tapping the playhead
     * replaces the existing mark rather than creating a duplicate.
     */
    fun addKeyframe(clipId: String, timeMs: Long, transform: AnimatedTransform = AnimatedTransform.Identity) {
        updateClip(clipId) { clip ->
            val kf = Keyframe(
                id = java.util.UUID.randomUUID().toString(),
                timeMs = timeMs,
                translateX = transform.translateX,
                translateY = transform.translateY,
                scale = transform.scale,
                rotationDeg = transform.rotationDeg,
                opacity = transform.opacity
            )
            val without = clip.keyframes.keyframes.filter { it.timeMs != timeMs }
            clip.copy(keyframes = KeyframeTrack(without + kf).sorted())
        }
    }

    fun updateKeyframe(clipId: String, keyframeId: String, transform: (Keyframe) -> Keyframe) {
        updateClip(clipId) { clip ->
            clip.copy(
                keyframes = KeyframeTrack(
                    clip.keyframes.keyframes.map { if (it.id == keyframeId) transform(it) else it }
                ).sorted()
            )
        }
    }

    fun removeKeyframe(clipId: String, keyframeId: String) {
        updateClip(clipId) { clip ->
            clip.copy(keyframes = KeyframeTrack(clip.keyframes.keyframes.filter { it.id != keyframeId }))
        }
    }

    fun clearKeyframes(clipId: String) {
        updateClip(clipId) { it.copy(keyframes = KeyframeTrack()) }
    }

    fun setKeyframePanelOpen(open: Boolean) =
        _state.update { it.copy(keyframePanelOpen = open) }

    /**
     * Persist the current `EditorState.project` to DataStore so a
     * later `EditorViewModel(projectId = ...)` constructor call (from
     * the Home screen project card) loads the same clip / LUT /
     * audio / keyframe state back into the editor.
     *
     * Called from every mutation that changes the project. We don't
     * block the UI thread — the write is queued on viewModelScope
     * and DataStore serialises concurrent edits for us.
     */
    private fun persistProject() {
        val snapshot = _state.value.project ?: return
        val repo = projectRepository ?: return
        viewModelScope.launch {
            try {
                repo.saveProject(snapshot)
            } catch (e: Exception) {
                Log.w("EditorViewModel", "Auto-save failed", e)
            }
        }
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
