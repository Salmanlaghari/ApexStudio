package com.apexstudio.app.presentation.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.apexstudio.app.data.crashlog.CrashMarker
import com.apexstudio.app.data.engine.AudioEngine
import com.apexstudio.app.data.engine.ColorGradingEngine
import com.apexstudio.app.data.export.ExportEngine
import com.apexstudio.app.data.media.MediaAnalyzer
import com.apexstudio.app.data.media.VideoThumbnailExtractor
import com.apexstudio.app.data.picker.MediaPickerHelper
import com.apexstudio.app.data.repository.MediaRepository
import com.apexstudio.app.data.repository.ProjectRepository
import com.apexstudio.app.data.template.TransmissionTemplate
import com.apexstudio.app.data.template.TimelineTemplateManager
import com.apexstudio.app.domain.model.*
import com.apexstudio.app.presentation.state.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

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

    /**
     * Transmission templates (LUT + FX + transition combos) loaded
     * from `assets/transmission_templates.json`. Surfaced to the UI
     * via the [TransmissionTemplatesPanel] chip strip. Live-validated
     * at load time by [TimelineTemplateManager.loadTransmissionTemplates]
     * so a renamed LUT or FX surfaces as a warning, not a crash.
     */
    private val _transmissionTemplates = MutableStateFlow<List<TransmissionTemplate>>(emptyList())
    val transmissionTemplates: StateFlow<List<TransmissionTemplate>> = _transmissionTemplates.asStateFlow()

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
    private val timelineTemplateManager = context?.let { TimelineTemplateManager(it) }

    init {
        Log.d("ApexTrace", "EditorViewModel.init start")
        context?.let { CrashMarker.mark(it, "EditorViewModel.init start") }
        loadProject()
        loadLuts()
        loadTransmissionTemplates()
        loadAudioState()
        // Mirror the engine's real export progress into the VM state
        // the Export screen renders (progress %, output uri, error).
        exportEngine?.exportState?.let { engineState ->
            viewModelScope.launch {
                engineState.collect { st ->
                    _export.update {
                        it.copy(
                            isExporting = st.isExporting,
                            progress = st.progress,
                            outputUri = st.outputUri,
                            error = st.error
                        )
                    }
                }
            }
        }
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

            val sampleUri = context?.let { ctx ->
                try {
                    com.apexstudio.app.data.media.SampleVideoGenerator.getOrCreateSampleVideo(ctx)
                } catch (_: Exception) { null }
            }
            val ctx = context
            val validClips = p.clips.map { clip ->
                if (ctx != null) {
                    val resolvedUri = com.apexstudio.app.data.media.MediaUriResolver.resolvePlayableUri(ctx, clip.uri).toString()
                    clip.copy(uri = resolvedUri)
                } else if (clip.uri.startsWith("asset://") || clip.uri.isBlank() || !clip.uri.contains("/")) {
                    if (sampleUri != null) clip.copy(uri = sampleUri) else clip
                } else clip
            }
            val effectiveClips = if (validClips.isEmpty() && sampleUri != null) {
                listOf(
                    MediaClip(
                        id = java.util.UUID.randomUUID().toString(),
                        name = "Sample_V1.mp4",
                        uri = sampleUri,
                        durationMs = 10_000L,
                        trimStartMs = 0L,
                        trimEndMs = 10_000L,
                        type = ClipType.VIDEO,
                        trackIndex = 0
                    )
                )
            } else validClips
            val updatedP = p.copy(
                clips = effectiveClips,
                durationMs = effectiveClips.maxOfOrNull { it.trimEndMs } ?: p.durationMs
            )

            val firstClipId = updatedP.clips.firstOrNull { it.type == ClipType.VIDEO }?.id
                ?: updatedP.clips.firstOrNull()?.id
            _state.update {
                it.copy(
                    project = updatedP,
                    durationMs = updatedP.durationMs,
                    canUndo = false, canRedo = false,
                    selectedClipId = it.selectedClipId ?: firstClipId,
                    isPlaying = firstClipId != null
                )
            }

            // Auto-apply the project's last transmission template so
            // the user opens to the look they had last time. Runs
            // after [loadTransmissionTemplates] because we need the
            // catalog to be in the StateFlow before we can resolve
            // the template by id. Falls back silently if the saved
            // id no longer matches a live template (e.g. the
            // template was removed from the catalog).
            val savedTemplateId = updatedP.lastTransmissionTemplateId
            if (savedTemplateId != null) {
                val template = _transmissionTemplates.value.firstOrNull { it.id == savedTemplateId }
                if (template != null) {
                    applyTransmissionTemplateInternal(template, persistProjectId = null)
                } else {
                    Log.w("EditorViewModel", "Saved transmission template '$savedTemplateId' not found in catalog — skipping auto-apply")
                }
            }
        }
    }

    private fun loadLuts() {
        _luts.value = repo.loadLutPresets()
        _transitions.value = repo.loadTransitionPresets()
        _fx.value = repo.loadFxPresets()
    }

    private fun loadTransmissionTemplates() {
        val mgr = timelineTemplateManager ?: run {
            Log.w("EditorViewModel", "TimelineTemplateManager unavailable — transmission catalog empty")
            return
        }
        try {
            val raw = mgr.loadTransmissionTemplatesRaw()
            val stats = mgr.parseTransmissionTemplatesWithStats(raw)
            _transmissionTemplates.value = stats.templates
            if (stats.skippedCount > 0) {
                Log.w(
                    "EditorViewModel",
                    "transmission_templates.json: ${stats.skippedCount} entries skipped " +
                        "(unknown LUT or FX id) — ids: ${stats.skippedIds.joinToString()}"
                )
            }
        } catch (e: Exception) {
            Log.w("EditorViewModel", "loadTransmissionTemplates failed", e)
            _transmissionTemplates.value = emptyList()
        }
    }

    private fun loadAudioState() {
        _audio.update { it.copy(tracks = repo.loadProjects().first().audioTracks) }
    }

    // ---- Transmission templates ----

    /**
     * Apply a transmission template to the current editor state.
     *
     * Sets:
     * - [EditorState.activeFilterId] from `template.filterId`
     * - [EditorState.filterIntensity] from `template.filterIntensity`
     * - [EditorState.activeFxId] from `template.fxPresetId`
     * - [EditorState.fxIntensity] from `template.fxIntensity`
     *
     * The preview GL pipeline re-reads these fields on its next
     * recomposition, so the change is live. Persists the chosen
     * template id into [Project.lastTransmissionTemplateId] so the
     * next time the user opens this project, [loadProject] auto-
     * applies it.
     *
     * `id == null` clears the active template (LUT + FX intensities
     * are reset to 0 so the preview shows the original colour). The
     * persisted id is cleared too.
     */
    fun applyTransmissionTemplate(id: String?) {
        if (id == null) {
            _state.update {
                it.copy(
                    activeFilterId = null,
                    filterIntensity = 0f,
                    activeFxId = null,
                    fxIntensity = 0f,
                    lastTransitionType = null,
                    lastTransitionDurationMs = 500L,
                    playbackSpeed = 1.0f
                )
            }
            persistTransmissionTemplate(
                id = null,
                transitionType = null,
                transitionDurationMs = 500L
            )
            return
        }
        val template = _transmissionTemplates.value.firstOrNull { it.id == id }
        if (template == null) {
            Log.w("EditorViewModel", "applyTransmissionTemplate: unknown id '$id' — ignoring")
            return
        }
        applyTransmissionTemplateInternal(template, persistProjectId = projectId)
    }

    /**
     * Apply a resolved [TransmissionTemplate] without re-looking it
     * up by id. Used by [loadProject] on project open so we can
     * skip the "unknown id" warning path for the auto-apply case.
     *
     * The template's `transitionType` and `transitionDurationMs` are
     * also applied as a project-level hint (see
     * `Project.lastTransitionType`).
     */
    private fun applyTransmissionTemplateInternal(template: TransmissionTemplate, persistProjectId: String?) {
        val targetSpeed = if (template.isSlowMotion && template.playbackSpeed > 0f) template.playbackSpeed else 1.0f
        _state.update {
            it.copy(
                activeFilterId = template.filterId,
                filterIntensity = template.filterIntensity.coerceIn(0f, 1f),
                activeFxId = template.fxPresetId,
                fxIntensity = template.fxIntensity.coerceIn(0f, 1f),
                lastTransitionType = template.transitionType,
                lastTransitionDurationMs = template.transitionDurationMs,
                playbackSpeed = targetSpeed
            )
        }
        // If slow motion is included, also update the active clip's playback speed
        val clipId = _state.value.selectedClipId ?: _state.value.project?.clips?.firstOrNull()?.id
        if (clipId != null && template.isSlowMotion) {
            setClipSpeed(clipId, targetSpeed)
        }
        persistTransmissionTemplate(id = template.id, transitionType = template.transitionType,
            transitionDurationMs = template.transitionDurationMs)
    }

    /**
     * Save the chosen transmission template id + transition hint on
     * the current project and write the project back to DataStore so
     * the choice survives an app restart. Called from both apply
     * paths (manual + auto).
     */
    private fun persistTransmissionTemplate(id: String?, transitionType: String?, transitionDurationMs: Long) {
        val snapshot = _state.value.project ?: return
        if (snapshot.lastTransmissionTemplateId == id &&
            snapshot.lastTransitionType == transitionType &&
            snapshot.lastTransitionDurationMs == transitionDurationMs
        ) return
        val updated = snapshot.copy(
            lastTransmissionTemplateId = id,
            lastTransitionType = transitionType,
            lastTransitionDurationMs = transitionDurationMs
        )
        _state.update { it.copy(project = updated) }
        val repo = projectRepository ?: return
        viewModelScope.launch {
            try {
                repo.saveProject(updated)
            } catch (e: Exception) {
                Log.w("EditorViewModel", "Failed to persist transmission template", e)
            }
        }
    }

    /**
     * Open or close the transmission-templates bottom sheet. Mirrors
     * the open/close pattern used by [openFilterPanel] / [openFxPanel].
     */
    fun openTransmissionTemplatesPanel() = _state.update { it.copy(transmissionPanelOpen = true) }

    // Phase D: PiP overlay preview. setOverlayTransform persists the
    // (x, y, scale, opacity) tuple so a pinch-zoom doesn't get clobbered
    // by unrelated state recompositions. setOverlayClip registers /
    // clears which clip owns the transform. clearOverlay resets both.
    fun setOverlayTransform(transform: com.apexstudio.app.presentation.state.OverlayTransform) =
        _state.update { it.copy(overlayTransform = transform) }
    fun setOverlayClip(clipId: String?) =
        _state.update { it.copy(overlayClipId = clipId) }
    fun clearOverlay() = _state.update {
        it.copy(overlayClipId = null, overlayTransform = com.apexstudio.app.presentation.state.OverlayTransform.Identity)
    }
    fun setPendingAddAsOverlay(v: Boolean) = _state.update {
        it.copy(pendingAddAsOverlay = v)
    }
    fun setPendingAddAsAudio(v: Boolean) = _state.update {
        it.copy(pendingAddAsAudio = v)
    }
    fun closeTransmissionTemplatesPanel() = _state.update { it.copy(transmissionPanelOpen = false) }

    // Phase C: open / close the per-clip action menu (Cut, Trim, Add,
    // Remove, Move, Split, Delete). Mirrors the setTrimPanelOpen
    // pattern from earlier phases. We deliberately do NOT clear the
    // selected clip here — selection persists so the clip stays
    // highlighted after the menu closes, matching the behaviour of
    // the existing trim / filter panels.
    fun openClipActionMenu(clipId: String, atMs: Long) = _state.update {
        it.copy(clipActionMenuClipId = clipId, clipActionMenuPlayheadMs = atMs)
    }
    fun closeClipActionMenu() = _state.update {
        it.copy(clipActionMenuClipId = null)
    }

    fun openMediaPicker() = _state.update { it.copy(isMediaPickerOpen = true) }
    fun closeMediaPicker() = _state.update { it.copy(isMediaPickerOpen = false) }

    fun onMediaPicked(mediaList: List<com.apexstudio.app.data.picker.MediaMetadata>, replace: Boolean = false) {
        viewModelScope.launch {
            val s = _state.value
            // Phase D: read the pendingAddAsOverlay flag from state so
            // the picker's callback can route the result through either
            // the regular video path or the overlay path. Phase E: audio
            // picks land in the A1 lane; the timeline's existing filter
            // `it.type == ClipType.AUDIO` routes them correctly without
            // any extra re-tagging.
            val asOverlay = s.pendingAddAsOverlay
            val newClips = mediaList.mapNotNull { meta ->
                val resolvedMeta = if (context != null) {
                    meta.copy(uri = com.apexstudio.app.data.media.MediaUriResolver.resolvePlayableUri(context!!, meta.uri).toString())
                } else meta
                val created = mediaPicker?.toMediaClip(resolvedMeta, s.project?.clips?.size ?: 0)
                // Phase D: when the user picked from the "Overlay clip"
                // entry point, re-tag the clip as OVERLAY + trackIndex 1
                // and register it as the active overlay. A new overlay
                // replaces any prior one (single-overlay v1 limit).
                if (asOverlay && created != null) {
                    created.copy(
                        type = ClipType.OVERLAY,
                        trackIndex = 1
                    )
                } else created
            }
            val existingClips = if (replace) emptyList() else (s.project?.clips ?: emptyList())

            val updatedClips = existingClips + newClips
            val updatedProject = s.project?.copy(clips = updatedClips)
            val maxDuration = updatedClips.maxOfOrNull { it.durationMs } ?: s.durationMs

            // Immediate state update so video preview loads in 0ms without delay
            _state.update {
                it.copy(
                    project = updatedProject,
                    durationMs = maxDuration,
                    pickedMedia = mediaList,
                    isMediaPickerOpen = false,
                    selectedClipId = when {
                        replace -> newClips.firstOrNull()?.id
                        it.selectedClipId != null -> it.selectedClipId
                        else -> newClips.firstOrNull()?.id
                    },
                    isPlaying = true,
                    // Phase D: register the freshly added overlay so the
                    // preview layer starts rendering it immediately.
                    overlayClipId = if (asOverlay) {
                        newClips.firstOrNull()?.id ?: it.overlayClipId
                    } else it.overlayClipId,
                    overlayTransform = if (asOverlay) {
                        com.apexstudio.app.presentation.state.OverlayTransform.Identity
                    } else it.overlayTransform,
                    pendingAddAsOverlay = false,
                    pendingAddAsAudio = false
                )
            }
            persistProject()

            val firstVideo = updatedClips.firstOrNull { it.type == ClipType.VIDEO } ?: updatedClips.firstOrNull()
            // Asynchronously analyze audio waveform in background without blocking video preview
            if (firstVideo != null && context != null) {
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val wf = mediaAnalyzer?.analyzeAudioWaveform(
                            firstVideo.uri, context,
                            sampleCount = 200,
                            trimStartMs = firstVideo.trimStartMs,
                            trimEndMs = firstVideo.trimEndMs
                        )?.samples ?: FloatArray(0)
                        if (wf.isNotEmpty()) {
                            _state.update { it.copy(audioWaveform = wf) }
                        }
                    } catch (e: Exception) {
                        Log.w("EditorViewModel", "Waveform analysis failed", e)
                    }
                }
            }

            for (clip in newClips) {
                if (clip.type == ClipType.VIDEO && context != null) {
                    loadClipThumbnails(clip)
                }
            }
        }
    }

    private fun loadClipThumbnails(clip: MediaClip) {
        val ctx = context ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val playableUri = com.apexstudio.app.data.media.MediaUriResolver
                    .resolvePlayableUri(ctx, clip.uri).toString()
                val thumbs = VideoThumbnailExtractor.extractStrip(
                    context = ctx,
                    videoUri = playableUri,
                    durationMs = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(1000L),
                    count = 8
                )
                _thumbnails.update { current ->
                    current + (clip.id to thumbs)
                }
                // Phase Live Filter fix: with the source frame on
                // hand, also generate per-filter thumbnails so the
                // Filter panel shows the actual video frame with each
                // LUT applied (not the generic hard-coded gradient).
                try {
                    val firstFrame = VideoThumbnailExtractor.extractFrame(
                        context = ctx,
                        videoUri = playableUri,
                        timeMs = clip.trimStartMs
                    )
                    if (firstFrame != null) {
                        val manifest = com.apexstudio.app.data.filter.LutFilterEngine(ctx).manifest
                        val perFilter = com.apexstudio.app.data.filter.FilterThumbnailGenerator
                            .generateDynamicThumbnails(ctx, firstFrame, manifest)
                        _state.update { it.copy(filterThumbnails = perFilter, filterThumbnailsLoading = false) }
                    }
                } catch (e: Exception) {
                    Log.w("EditorViewModel", "Per-filter thumbnail generation failed", e)
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
    fun setZoom(z: Float) = _state.update { it.copy(zoomLevel = z.coerceIn(0.2f, 4f)) }

    // Resolution / preview mode + modal triggers consumed by EditorScreen.kt
    // panels. These were removed by the 'add new color LUT filters'
    // commit but EditorScreen.kt still references them; restoring here so
    // the existing UI integration continues to compile.
    fun setSelectedResolution(res: String) = _state.update { it.copy(selectedResolution = res) }
    fun toggleFullscreenPreview() = _state.update { it.copy(isFullscreenPreview = !it.isFullscreenPreview) }
    fun openAdjustmentsPanel() = _state.update { it.copy(adjustmentsPanelOpen = true) }
    fun closeAdjustmentsPanel() = _state.update { it.copy(adjustmentsPanelOpen = false) }
    fun openStickerPanel() = _state.update { it.copy(stickerPanelOpen = true) }
    fun closeStickerPanel() = _state.update { it.copy(stickerPanelOpen = false) }
    fun selectSticker(id: String?) = _state.update { it.copy(selectedStickerId = id) }
    fun openVoiceRecorder() = _state.update { it.copy(voiceRecorderOpen = true) }
    fun closeVoiceRecorder() = _state.update { it.copy(voiceRecorderOpen = false) }
    fun openCameraCapture() = _state.update { it.copy(cameraCaptureOpen = true) }
    fun closeCameraCapture() = _state.update { it.copy(cameraCaptureOpen = false) }
    fun openHelpDialog() = _state.update { it.copy(helpDialogOpen = true) }
    fun closeHelpDialog() = _state.update { it.copy(helpDialogOpen = false) }
    fun openCoverPanel() = _state.update { it.copy(coverPanelOpen = true) }
    fun closeCoverPanel() = _state.update { it.copy(coverPanelOpen = false) }
    fun openMediaLibrary() = _state.update { it.copy(mediaLibraryOpen = true) }
    fun closeMediaLibrary() = _state.update { it.copy(mediaLibraryOpen = false) }

    fun updateAdjustments(transform: (VideoAdjustments) -> VideoAdjustments) {
        pushUndo()
        _state.update { s ->
            val newAdj = transform(s.adjustments)
            s.copy(
                adjustments = newAdj,
                project = s.project?.copy(adjustments = newAdj)
            )
        }
        val selectedId = _state.value.selectedClipId
        if (selectedId != null) {
            updateClip(selectedId) { c -> c.copy(adjustments = transform(c.adjustments)) }
        }
        persistProject()
    }

    fun resetAdjustments() {
        pushUndo()
        val defaultAdj = VideoAdjustments()
        _state.update { s ->
            s.copy(adjustments = defaultAdj, project = s.project?.copy(adjustments = defaultAdj))
        }
        val selectedId = _state.value.selectedClipId
        if (selectedId != null) updateClip(selectedId) { it.copy(adjustments = defaultAdj) }
        persistProject()
    }
    fun resetAllAdjustments() = resetAdjustments()

    fun addStickerOverlay(symbolOrUri: String, category: String = "Emoji", name: String = "Sticker") {
        pushUndo()
        val sticker = StickerOverlay(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            category = category,
            symbolOrUri = symbolOrUri,
            x = 0.5f,
            y = 0.5f,
            startMs = _state.value.playerPositionMs,
            endMs = (_state.value.playerPositionMs + 5000L).coerceAtMost(_state.value.durationMs)
        )
        val selectedClipId = _state.value.selectedClipId
        if (selectedClipId != null) updateClip(selectedClipId) { c -> c.copy(stickers = c.stickers + sticker) }
        _state.update { s ->
            val currentList = s.project?.stickers ?: emptyList()
            s.copy(selectedStickerId = sticker.id, project = s.project?.copy(stickers = currentList + sticker))
        }
        persistProject()
    }

    fun updateStickerOverlay(stickerId: String, transform: (StickerOverlay) -> StickerOverlay) {
        _state.update { s ->
            val p = s.project ?: return@update s
            val updatedProjectStickers = p.stickers.map { if (it.id == stickerId) transform(it) else it }
            val updatedClips = p.clips.map { clip ->
                clip.copy(stickers = clip.stickers.map { if (it.id == stickerId) transform(it) else it })
            }
            s.copy(project = p.copy(stickers = updatedProjectStickers, clips = updatedClips))
        }
        persistProject()
    }

    fun removeStickerOverlay(stickerId: String) {
        pushUndo()
        _state.update { s ->
            val p = s.project ?: return@update s
            val updatedProjectStickers = p.stickers.filterNot { it.id == stickerId }
            val updatedClips = p.clips.map { clip -> clip.copy(stickers = clip.stickers.filterNot { it.id == stickerId }) }
            s.copy(
                selectedStickerId = if (s.selectedStickerId == stickerId) null else s.selectedStickerId,
                project = p.copy(stickers = updatedProjectStickers, clips = updatedClips)
            )
        }
        persistProject()
    }

    fun setCoverFrame(ms: Long) {
        _state.update { s -> s.copy(project = s.project?.copy(coverFrameMs = ms, coverCustomUri = null)) }
        persistProject()
    }
    fun setCoverCustomUri(uri: String) {
        _state.update { s -> s.copy(project = s.project?.copy(coverCustomUri = uri)) }
        persistProject()
    }

    fun addVoiceOverTrack(uri: String, durationMs: Long, name: String = "Voiceover") {
        pushUndo()
        val track = AudioTrack(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            uri = uri,
            volume = 1.0f,
            trimStartMs = 0L,
            trimEndMs = durationMs
        )
        val clip = MediaClip(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            uri = uri,
            durationMs = durationMs,
            trimStartMs = 0L,
            trimEndMs = durationMs,
            type = ClipType.SFX,
            trackIndex = 1
        )
        _audio.update { it.copy(tracks = it.tracks + track) }
        _state.update { s ->
            val p = s.project ?: return@update s
            s.copy(project = p.copy(clips = p.clips + clip, audioTracks = p.audioTracks + track))
        }
        persistProject()
    }
    fun multiplyZoom(factor: Float) = _state.update {
        val current = it.zoomLevel
        val next = (current * factor).coerceIn(0.2f, 4f)
        it.copy(zoomLevel = next)
    }
    fun fitTimelineToScreen(viewportPx: Float = 1000f) = _state.update {
        val totalMs = it.durationMs.coerceAtLeast(5000L)
        val target = (viewportPx / (totalMs * 0.12f)).coerceIn(0.2f, 4f)
        it.copy(zoomLevel = target)
    }
    fun selectTool(t: EditorTool) = _state.update { it.copy(selectedTool = t) }
    fun selectClip(id: String?) = _state.update { it.copy(selectedClipId = id) }
    // Phase Live Filter fix: selectClip + regenerate thumbnails so the
    // Filter panel reflects the newly selected clip's frame.
    fun selectClipAndRefresh(id: String?) {
        _state.update { it.copy(selectedClipId = id) }
        regenerateFilterThumbnails()
    }
    fun setPlayerPosition(ms: Long) = _state.update { it.copy(playerPositionMs = ms) }
    fun setPlayerDuration(ms: Long) = _state.update { it.copy(playerDurationMs = ms) }
    fun setPlayerReady(ready: Boolean) = _state.update {
        if (ready) it.copy(isPlayerReady = true, playerError = null, isBuffering = false)
        else it.copy(isPlayerReady = false)
    }
    // Phase A: separate "buffering" signal from "ready". The Player.Listener
    // calls this with (playbackState == Player.STATE_BUFFERING). We keep
    // isPlayerReady semantically unchanged — STATE_READY is the source of
    // truth for "first frame painted" so the rest of the UI (filters,
    // transforms, overlays) keeps gating on isPlayerReady as before.
    fun setBuffering(buffering: Boolean) = _state.update { it.copy(isBuffering = buffering) }
    fun setPlayerError(error: String?) = _state.update {
        it.copy(playerError = error, isBuffering = false, isPlayerReady = error == null && it.isPlayerReady)
    }
    fun setVideoSize(width: Int, height: Int) = _state.update { it.copy(videoWidth = width, videoHeight = height) }

    fun setCropMode(enabled: Boolean) = _state.update { it.copy(cropMode = enabled) }
    fun setCropRect(rect: CropRect) = _state.update { it.copy(cropRect = rect) }
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

    fun openFilterPanel() {
        _state.update { it.copy(filterPanelOpen = true) }
        ensureFilterThumbnails()
    }
    fun closeFilterPanel() = _state.update { it.copy(filterPanelOpen = false) }
    fun setFilterCategory(id: String) = _state.update { it.copy(filterCategory = id) }
    fun setActiveFilter(id: String?) = _state.update { it.copy(activeFilterId = id) }

    // Phase Live Filter fix: regenerate the per-filter thumbnails
    // against the current clip's frame. Cheap (uses Android's hardware
    // ColorMatrix path) and runs on Dispatchers.IO. Called when the
    // user picks a clip OR when the source video URI changes.
    fun regenerateFilterThumbnails() {
        val ctx = context ?: return
        val clip = _state.value.project?.clips?.firstOrNull { it.id == _state.value.selectedClipId }
            ?: _state.value.project?.clips?.firstOrNull() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val playableUri = com.apexstudio.app.data.media.MediaUriResolver
                    .resolvePlayableUri(ctx, clip.uri).toString()
                val firstFrame = VideoThumbnailExtractor.extractFrame(
                    context = ctx, videoUri = playableUri, timeMs = clip.trimStartMs
                )
                if (firstFrame != null) {
                    val manifest = com.apexstudio.app.data.filter.LutFilterEngine(ctx).manifest
                    val perFilter = com.apexstudio.app.data.filter.FilterThumbnailGenerator
                        .generateDynamicThumbnails(ctx, firstFrame, manifest)
                    _state.update { it.copy(filterThumbnails = perFilter, filterThumbnailsLoading = false) }
                }
            } catch (e: Exception) {
                Log.w("EditorViewModel", "regenerateFilterThumbnails failed", e)
            }
        }
    }
    fun setFilterIntensity(v: Float) = _state.update { it.copy(filterIntensity = v.coerceIn(0f, 1f)) }

    fun ensureFilterThumbnails() {
        val ctx = context ?: return
        if (_state.value.filterThumbnails.isNotEmpty() || _state.value.filterThumbnailsLoading) return
        val manifest = try {
            com.apexstudio.app.data.filter.LutFilterEngine(ctx).manifest
        } catch (e: Exception) {
            Log.w("EditorViewModel", "Failed to load filter manifest", e)
            return
        }
        _state.update { it.copy(filterThumbnailsLoading = true) }
        viewModelScope.launch {
            try {
                val activeClipId = _state.value.selectedClipId ?: _state.value.project?.clips?.firstOrNull()?.id
                val cachedThumb = activeClipId?.let { _thumbnails.value[it]?.firstOrNull()?.second }
                val composeMap = if (cachedThumb != null) {
                    com.apexstudio.app.data.filter.FilterThumbnailGenerator
                        .generateDynamicThumbnails(ctx, cachedThumb, manifest)
                } else {
                    com.apexstudio.app.data.filter.FilterThumbnailGenerator
                        .generateWithGenericImage(ctx, manifest)
                }
                _state.update {
                    it.copy(
                        filterThumbnails = composeMap,
                        filterThumbnailsLoading = false
                    )
                }
                Log.d("EditorViewModel", "Generated ${composeMap.size} filter thumbnails")
            } catch (e: Exception) {
                Log.e("EditorViewModel", "Generic filter thumbnail generation failed", e)
                _state.update { it.copy(filterThumbnailsLoading = false) }
            }
        }
    }

    fun generateFilterThumbnails(sourceFrame: android.graphics.Bitmap) {
        val ctx = context ?: return
        val manifest = try {
            com.apexstudio.app.data.filter.LutFilterEngine(ctx).manifest
        } catch (e: Exception) {
            Log.w("EditorViewModel", "Failed to load filter manifest", e)
            return
        }
        _state.update { it.copy(filterThumbnailsLoading = true) }
        viewModelScope.launch {
            try {
                val composeMap = com.apexstudio.app.data.filter.FilterThumbnailGenerator
                    .generateDynamicThumbnails(ctx, sourceFrame, manifest)
                _state.update {
                    it.copy(
                        filterThumbnails = composeMap,
                        filterThumbnailsLoading = false
                    )
                }
                Log.d("EditorViewModel", "Generated ${composeMap.size} dynamic filter thumbnails")
            } catch (e: Exception) {
                Log.e("EditorViewModel", "Filter thumbnail generation failed", e)
                _state.update { it.copy(filterThumbnailsLoading = false) }
            }
        }
    }

    fun openAudioMixer() = _state.update { it.copy(audioMixerOpen = true) }
    fun closeAudioMixer() = _state.update { it.copy(audioMixerOpen = false) }
    fun openSpeedPanel() = _state.update { it.copy(speedPanelOpen = true) }
    fun closeSpeedPanel() = _state.update { it.copy(speedPanelOpen = false) }
    fun setPlaybackSpeed(speed: Float) = _state.update { it.copy(playbackSpeed = speed.coerceIn(0.25f, 8f)) }

    fun openFxPanel() = _state.update { it.copy(fxPanelOpen = true) }
    fun closeFxPanel() = _state.update { it.copy(fxPanelOpen = false) }
    fun setActiveFx(id: String?) = _state.update { it.copy(activeFxId = id) }
    fun setFxIntensity(v: Float) = _state.update { it.copy(fxIntensity = v.coerceIn(0f, 1f)) }

    fun openTextPanel() = _state.update { it.copy(textPanelOpen = true) }
    fun closeTextPanel() = _state.update { it.copy(textPanelOpen = false) }
    fun selectTextOverlay(id: String?) = _state.update { it.copy(selectedTextOverlayId = id) }

    fun addTextOverlay(clipId: String, text: String = "Text", x: Float = 0.5f, y: Float = 0.5f) {
        val overlay = com.apexstudio.app.domain.model.TextOverlay.of(
            text = text, x = x, y = y
        )
        updateClip(clipId) { it.copy(textOverlays = it.textOverlays + overlay) }
        _state.update { it.copy(selectedTextOverlayId = overlay.id) }
    }

    fun updateTextOverlay(clipId: String, overlayId: String, transform: (com.apexstudio.app.domain.model.TextOverlay) -> com.apexstudio.app.domain.model.TextOverlay) {
        updateClip(clipId) { clip ->
            clip.copy(
                textOverlays = clip.textOverlays.map {
                    if (it.id == overlayId) transform(it) else it
                }
            )
        }
    }

    fun moveTextOverlay(
        clipId: String,
        overlayId: String,
        dx: Float,
        dy: Float,
        persist: Boolean = true
    ) {
        updateClip(clipId, persist = persist) { clip ->
            clip.copy(
                textOverlays = clip.textOverlays.map {
                    if (it.id == overlayId) {
                        it.copy(
                            x = (it.x + dx).coerceIn(0.04f, 0.96f),
                            y = (it.y + dy).coerceIn(0.06f, 0.94f)
                        )
                    } else it
                }
            )
        }
    }

    fun flushProject() = persistProject()

    fun setTextOverlayText(clipId: String, overlayId: String, text: String) =
        updateTextOverlay(clipId, overlayId) { it.copy(text = text) }

    fun setTextOverlayColor(clipId: String, overlayId: String, colorArgb: Long) =
        updateTextOverlay(clipId, overlayId) { it.copy(colorArgb = colorArgb) }

    fun setTextOverlayBg(clipId: String, overlayId: String, bgArgb: Long?) =
        updateTextOverlay(clipId, overlayId) { it.copy(bgArgb = bgArgb) }

    fun setTextOverlaySize(clipId: String, overlayId: String, sizeScale: Float) =
        updateTextOverlay(clipId, overlayId) { it.copy(sizeScale = sizeScale.coerceIn(0.4f, 3f)) }

    fun applyTextPreset(clipId: String, overlayId: String, preset: com.apexstudio.app.data.text.TextPreset) =
        updateTextOverlay(clipId, overlayId) { preset.applyTo(it) }

    fun removeTextOverlay(clipId: String, overlayId: String) {
        updateClip(clipId) { clip ->
            clip.copy(textOverlays = clip.textOverlays.filterNot { it.id == overlayId })
        }
        if (_state.value.selectedTextOverlayId == overlayId) {
            _state.update { it.copy(selectedTextOverlayId = null) }
        }
    }

    fun updateExport(upd: (ExportSettings) -> ExportSettings) {
        _export.update { it.copy(settings = upd(it.settings)) }
    }

    fun startExport(
        resolution: String = _export.value.settings.resolution,
        fps: Int = _export.value.settings.frameRate,
        quality: String = _export.value.settings.quality.label.lowercase()
    ) {
        val s = _state.value
        val selected = s.project?.clips?.firstOrNull { it.id == s.selectedClipId }
            ?: s.project?.clips?.firstOrNull()
        val inputUri = selected?.uri ?: return
        val engine = exportEngine ?: return
        _export.update { it.copy(isExporting = true, progress = 0f) }

        var filterPreset: com.apexstudio.app.data.filter.FilterPreset? = null
        if (s.activeFilterId != null && context != null) {
            try {
                filterPreset = com.apexstudio.app.data.filter.LutFilterEngine(context!!).manifest.filters
                    .firstOrNull { it.id == s.activeFilterId }
            } catch (e: Exception) {
                Log.w("EditorViewModel", "Failed to load filter manifest for export", e)
            }
        }
        val fxPreset = com.apexstudio.app.data.fx.FxPreset.byId(s.activeFxId)
        val speed = selected?.speedMultiplier ?: s.playbackSpeed

        // Resolve transition: the Transmission panel writes
        // lastTransitionType / lastTransitionDurationMs to project
        // state. Until now they were never propagated into
        // ExportConfig, so the transition was applied in the live
        // preview's state but silently dropped on export.
        val transitionType: com.apexstudio.app.data.gl.TransitionEngine.Companion.TransitionType? =
            s.project?.lastTransitionType?.let {
                com.apexstudio.app.data.gl.TransitionEngine.Companion.TransitionType.byId(it)
            }
        val transitionDurationMs: Long =
            s.project?.lastTransitionDurationMs?.takeIf { it > 0L } ?: 500L

        // Adjustments (Brightness/Contrast/Saturation/etc.). Same
        // story: the AdjustPanel writes them to state, but
        // ExportConfig.adjustments was declared and never read, so
        // the export baked in the raw video. Pull the currently
        // selected clip's adjustments — fall back to project-level
        // defaults for clips that have none.
        val adjustments: com.apexstudio.app.domain.model.VideoAdjustments =
            selected?.adjustments?.takeIf { !it.isDefault }
                ?: s.project?.adjustments
                ?: com.apexstudio.app.domain.model.VideoAdjustments()

        engine.startExport(
            inputUri,
            ExportEngine.ExportConfig(
                resolution = resolution,
                fps = fps,
                quality = quality,
                filterPreset = filterPreset,
                filterIntensity = s.filterIntensity,
                adjustments = adjustments,
                fxPreset = fxPreset,
                fxIntensity = s.fxIntensity,
                transitionType = transitionType,
                transitionDurationMs = transitionDurationMs,
                clipSpeed = speed,
                keyframes = selected?.keyframes ?: KeyframeTrack(),
                cropRect = s.cropRect.takeIf { !it.isFullFrame() },
                textOverlays = selected?.textOverlays ?: emptyList(),
                trimStartMs = selected?.trimStartMs ?: 0L,
                trimEndMs = selected?.trimEndMs ?: 0L
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

    // Phase E: voice-changer + audio effects. setPitch translates
    // semitones to PlaybackParameters.pitch via 2^(semitones/12) and
    // writes it to the main ExoPlayer so the preview reflects the
    // change. enableReverb / enableEcho / enableBassBoost gate the
    // matching android.media.audiofx classes inside AudioEngine and
    // the export pipeline picks them up via the same state fields.
    fun setPitch(semitones: Float) {
        val clamped = semitones.coerceIn(-12f, 12f)
        _audio.update { it.copy(pitchSemitones = clamped) }
        audioEngine?.setPitchSemitones(clamped)
        applyAudioPitchToMainPlayer(clamped)
    }

    fun enableReverb(enabled: Boolean, preset: Short = 0) {
        _audio.update { it.copy(reverbEnabled = enabled, reverbPreset = preset) }
        audioEngine?.enableReverb(enabled, preset)
    }

    fun enableEcho(enabled: Boolean) {
        _audio.update { it.copy(echoEnabled = enabled) }
        audioEngine?.enableEcho(enabled)
    }

    fun enableBassBoost(enabled: Boolean, strength: Short = 0) {
        _audio.update { it.copy(bassBoostEnabled = enabled, bassBoostStrength = strength) }
        audioEngine?.enableBassBoost(enabled, strength)
    }

    // Phase E: push pitch to the preview ExoPlayer. ExoPlayer exposes
    // pitch via PlaybackParameters(speed, pitch). speed stays at 1f
    // so we only change the pitch component.
    private var mainPlayerRef: androidx.media3.exoplayer.ExoPlayer? = null
    fun registerMainPlayerForAudioEffects(p: androidx.media3.exoplayer.ExoPlayer?) {
        mainPlayerRef = p
        // Apply any previously-stored pitch immediately so re-attaching
        // the player preserves the user's setting.
        applyAudioPitchToMainPlayer(_audio.value.pitchSemitones)
    }
    private fun applyAudioPitchToMainPlayer(semitones: Float) {
        val p = mainPlayerRef ?: return
        val pitch = Math.pow(2.0, (semitones / 12.0).toDouble()).toFloat()
        p.playbackParameters = androidx.media3.common.PlaybackParameters(1f, pitch)
    }
    fun toggleMute(trackId: String) = _audio.update { s ->
        s.copy(tracks = s.tracks.map { if (it.id == trackId) it.copy(isMuted = !it.isMuted) else it })
    }
    fun setTrackVolume(trackId: String, vol: Float) = _audio.update { s ->
        s.copy(tracks = s.tracks.map { if (it.id == trackId) it.copy(volume = vol.coerceIn(0f, 1f)) else it })
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

    fun setTimelineWaveform(samples: FloatArray) = _state.update {
        it.copy(audioWaveform = samples)
    }

    fun refreshTimelineWaveform(clipId: String) {
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
    fun setRecordingState(recording: Boolean) {
        _audio.update { it.copy(isRecording = recording) }
    }

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

    fun setMuteOriginalVideo(muted: Boolean) {
        _audio.update { it.copy(isMuted = muted) }
    }

    private fun updateClip(
        clipId: String,
        persist: Boolean = true,
        transform: (MediaClip) -> MediaClip
    ) {
        _state.update { s ->
            val proj = s.project ?: return@update s
            val newClips = proj.clips.map { if (it.id == clipId) transform(it) else it }
            s.copy(project = proj.copy(clips = newClips))
        }
        if (persist) persistProject()
    }

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
            val proj = s.project ?: return@update s
            val updatedClips = proj.clips.map { c ->
                if (c.id == clipId) {
                    val safeStart = startMs.coerceIn(0L, (c.durationMs - 100L).coerceAtLeast(0L))
                    val safeEnd = endMs.coerceIn(safeStart + 100L, c.durationMs)
                    c.copy(
                        trimStartMs = safeStart,
                        trimEndMs = safeEnd
                    )
                } else c
            }
            val newDuration = updatedClips.sumOf { (it.trimEndMs - it.trimStartMs).coerceAtLeast(1000L) }
            val newPlayhead = s.playerPositionMs.coerceIn(0L, newDuration)
            s.copy(
                project = proj.copy(clips = updatedClips, durationMs = newDuration),
                durationMs = newDuration,
                playerPositionMs = newPlayhead,
                currentTimeMs = newPlayhead,
                canUndo = true,
                canRedo = redoStack.isNotEmpty()
            )
        }
        persistProject()
    }

    fun openTrimPanel() = _state.update { it.copy(trimPanelOpen = true) }
    fun closeTrimPanel() = _state.update { it.copy(trimPanelOpen = false) }

    fun setTrimStartAtPlayhead(clipId: String) {
        val playhead = _state.value.playerPositionMs
        val clip = _state.value.project?.clips?.firstOrNull { it.id == clipId } ?: return
        val newStart = playhead.coerceIn(0L, (clip.trimEndMs - 100L).coerceAtLeast(0L))
        trimClip(clipId, newStart, clip.trimEndMs)
    }

    fun setTrimEndAtPlayhead(clipId: String) {
        val playhead = _state.value.playerPositionMs
        val clip = _state.value.project?.clips?.firstOrNull { it.id == clipId } ?: return
        val newEnd = playhead.coerceIn(clip.trimStartMs + 100L, clip.durationMs)
        trimClip(clipId, clip.trimStartMs, newEnd)
    }

    fun resetTrim(clipId: String) {
        val clip = _state.value.project?.clips?.firstOrNull { it.id == clipId } ?: return
        trimClip(clipId, 0L, clip.durationMs)
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

    fun deleteClip(clipId: String) {
        pushUndo()
        _state.update { s ->
            val p = s.project ?: return@update s
            val updated = p.clips.filterNot { it.id == clipId }
            val newSelected = if (s.selectedClipId == clipId) updated.firstOrNull()?.id else s.selectedClipId
            val newDur = updated.maxOfOrNull { it.trimEndMs - it.trimStartMs } ?: 0L
            s.copy(
                project = p.copy(clips = updated, durationMs = newDur),
                durationMs = newDur,
                selectedClipId = newSelected,
                // Phase D: deleting the active overlay clip clears the
                // transform too, otherwise the preview layer would keep
                // trying to render a non-existent overlay's PlayerView.
                overlayClipId = if (s.overlayClipId == clipId) null else s.overlayClipId,
                overlayTransform = if (s.overlayClipId == clipId) com.apexstudio.app.presentation.state.OverlayTransform.Identity else s.overlayTransform
            )
        }
        persistProject()
    }

    fun moveClipToTrack(clipId: String, newType: ClipType, newTrackIndex: Int) {
        pushUndo()
        _state.update { s ->
            val p = s.project ?: return@update s
            val updated = p.clips.map {
                if (it.id == clipId) it.copy(type = newType, trackIndex = newTrackIndex)
                else it
            }
            s.copy(project = p.copy(clips = updated))
        }
        persistProject()
    }

    fun addClipToTrack(type: ClipType, trackIndex: Int) {
        val ctx = context ?: return
        viewModelScope.launch {
            val sampleUri = try {
                com.apexstudio.app.data.media.SampleVideoGenerator.getOrCreateSampleVideo(ctx)
            } catch (e: Exception) { "" }
            val newClip = MediaClip(
                id = java.util.UUID.randomUUID().toString(),
                name = when(type) {
                    ClipType.VIDEO -> "Clip_V1.mp4"
                    ClipType.OVERLAY -> "Overlay_V2.mp4"
                    ClipType.AUDIO -> "Track_A1.mp3"
                    ClipType.SFX -> "Sfx_FX.wav"
                },
                uri = sampleUri,
                durationMs = 10_000L,
                trimStartMs = 0L,
                trimEndMs = 10_000L,
                trackIndex = trackIndex,
                type = type
            )
            pushUndo()
            _state.update { s ->
                val p = s.project ?: return@update s
                val updated = p.clips + newClip
                val newDur = maxOf(s.durationMs, 10_000L)
                s.copy(
                    project = p.copy(clips = updated, durationMs = newDur),
                    durationMs = newDur,
                    selectedClipId = newClip.id
                )
            }
            persistProject()
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