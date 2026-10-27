package com.apexstudio.app.presentation.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import com.apexstudio.app.data.crashlog.CrashMarker
import com.apexstudio.app.data.engine.AudioEngine
import com.apexstudio.app.data.engine.ColorGradingEngine
import com.apexstudio.app.data.export.ExportEngine
import com.apexstudio.app.data.media.MediaAnalyzer
import com.apexstudio.app.data.picker.MediaPickerHelper
import com.apexstudio.app.data.repository.MediaRepository
import com.apexstudio.app.data.repository.ProjectRepository
import com.apexstudio.app.data.template.TransmissionTemplate
import com.apexstudio.app.data.template.TimelineTemplateManager
import com.apexstudio.app.ai.AiCancellationToken
import com.apexstudio.app.data.aireport.AiContentReportReason
import com.apexstudio.app.data.aireport.AiContentReportTarget
import com.apexstudio.app.data.aireport.FileAiContentReportStore
import com.apexstudio.app.domain.model.*
import com.apexstudio.app.presentation.state.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** In-memory undo unit: preserve every project lane plus editor-only layer flags. */
internal data class EditorUndoSnapshot(
    val project: Project,
    val hiddenVideoLayers: Set<Int>,
    val lockedVideoLayers: Set<Int>,
    val extraVideoLayers: Set<Int>,
) : List<MediaClip> by project.clips


class EditorViewModel(
    internal val repo: MediaRepository = MediaRepository,
    internal val context: android.content.Context? = null,
    internal val projectId: String? = null,
    internal val savedStateHandle: SavedStateHandle? = null
) : ViewModel() {

    internal val _state = MutableStateFlow(
        EditorState(
            selectedClipIds = savedStateHandle?.get<ArrayList<String>>(SELECTED_CLIP_IDS_KEY)?.toSet()
                ?: emptySet(),
            multiSelectMode = savedStateHandle?.get<Boolean>(MULTI_SELECT_MODE_KEY) ?: false,
        )
    )
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private companion object {
        const val SELECTED_CLIP_IDS_KEY = "editor.selectedClipIds"
        const val MULTI_SELECT_MODE_KEY = "editor.multiSelectMode"
    }

    private val aiContentReportController = AiContentReportController(
        store = context?.let { FileAiContentReportStore(java.io.File(it.filesDir, "ai-content-reports")) },
        scope = viewModelScope
    )
    val aiContentReportState: StateFlow<AiContentReportUiState> = aiContentReportController.state

    fun submitAiContentReport(target: AiContentReportTarget?, reason: AiContentReportReason) {
        aiContentReportController.submit(target, reason)
    }

    fun clearAiContentReportFeedback() = aiContentReportController.clearFeedback()

    /**
     * Auto-caption UI state (Whisper). Instance-scoped so it can't
     * bleed between editors (multi-window / tests).
     */
    internal val _captionUi = MutableStateFlow(CaptionUiState())
    val captionUi: StateFlow<CaptionUiState> = _captionUi.asStateFlow()

    /** Review-before-apply filler candidates, scoped to this editor instance. */
    internal val _captionFillerReviewState = MutableStateFlow(CaptionFillerReviewState())
    val captionFillerReview: StateFlow<CaptionFillerReviewState> = _captionFillerReviewState.asStateFlow()

    /**
     * In-flight auto-caption transcription job (Whisper). Cancelled when
     * the user taps Cancel, switches language, or leaves the editor.
     */
    internal var captionJob: Job? = null

    /** Offline heuristic scene/trim/crop analysis; never a learned-model result. */
    internal val _localAnalysis = MutableStateFlow(LocalAnalysisUiState())
    val localAnalysis: StateFlow<LocalAnalysisUiState> = _localAnalysis.asStateFlow()
    internal var localAnalysisJob: Job? = null
    internal var localAnalysisToken: AiCancellationToken? = null

    /** Sparse offline face-track suggestions are review-only and separate from protected AI actions. */
    internal val _faceTrackReview = MutableStateFlow(FaceTrackReviewUiState())
    val faceTrackReview: StateFlow<FaceTrackReviewUiState> = _faceTrackReview.asStateFlow()
    internal var faceTrackReviewJob: Job? = null
    internal var faceTrackReviewToken: AiCancellationToken? = null

    internal val _gestureEditing = MutableStateFlow(GestureEditingUiState())
    val gestureEditing: StateFlow<GestureEditingUiState> = _gestureEditing.asStateFlow()
    internal var editorGestureSession: EditorGestureSession? = null

    /**
     * Background-replace UI state (MediaPipe person segmentation,
     * PR-G). Instance-scoped so it can't bleed between editors.
     */
    internal val _backgroundUi = MutableStateFlow(BackgroundUiState())
    val backgroundUi: StateFlow<BackgroundUiState> = _backgroundUi.asStateFlow()

    /**
     * In-flight background-replace processing job. Cancelled when
     * the user taps Cancel or leaves the editor.
     */
    internal var backgroundJob: Job? = null

    /** Offline ESPCN render request; scoped to this editor and cancelled with its lifecycle. */
    internal val _videoUpscaleUi = MutableStateFlow(VideoUpscaleUiState())
    val videoUpscaleUi: StateFlow<VideoUpscaleUiState> = _videoUpscaleUi.asStateFlow()
    internal var videoUpscaleJob: Job? = null

    /** Offline still-photo ESPCN and MediaPipe face-crop actions, isolated per editor instance. */
    internal val _photoAiEnhanceUi = MutableStateFlow(PhotoAiEnhanceUiState())
    val photoAiEnhanceUi: StateFlow<PhotoAiEnhanceUiState> = _photoAiEnhanceUi.asStateFlow()
    internal var photoAiEnhanceJob: Job? = null

    /** Captured on opening the recorder, independent of recording-time playback. */
    internal var voiceOverStartMs: Long = 0L

    /**
     * Live playback position, updated on EVERY position tick (~30/s) by
     * [setPlayerPosition]. This is the 60fps-safe playhead stream: the
     * timeline playhead overlay collects it in a tiny composable, so
     * playback ticks do NOT recompose the whole timeline.
     *
     * Deliberately separate from [_state]: pushing every tick through
     * [EditorState] recomposed the ENTIRE EditorScreen (all panels,
     * preview, transport, toolbar) 30x/s. The state's
     * [EditorState.playerPositionMs] is now a ~10Hz mirror, synced by
     * [setPlayerPosition] (throttled) and forced on every seek / pause /
     * user action. UI that needs the live value reads this flow (or
     * [livePlayheadMs]); UI that reads the state copy gets the 10Hz
     * mirror, which is plenty for timecode text and panel playheads.
     */
    internal val _playerPosition = MutableStateFlow(0L)
    val playerPositionFlow: StateFlow<Long> = _playerPosition.asStateFlow()

    /** Current live playhead (ms) — the authoritative "now" for user actions (split / trim / keyframe). */
    internal fun livePlayheadMs(): Long = _playerPosition.value

    /** Wall-clock (uptimeMillis) of the last position mirror sync into [_state]. */
    internal var lastPositionStateSyncAtMs: Long = 0L

    internal val _export = MutableStateFlow(ExportState())
    val export: StateFlow<ExportState> = _export.asStateFlow()

    @Volatile internal var photoExportRun: Any? = null
    internal var photoExportJob: Job? = null

    internal val _color = MutableStateFlow(ColorToolState())
    val color: StateFlow<ColorToolState> = _color.asStateFlow()

    internal val _audio = MutableStateFlow(AudioStudioState())
    val audio: StateFlow<AudioStudioState> = _audio.asStateFlow()

    internal val _luts = MutableStateFlow<List<LutPreset>>(emptyList())
    val luts: StateFlow<List<LutPreset>> = _luts.asStateFlow()

    internal val _transitions = MutableStateFlow<List<ToolItem>>(emptyList())
    val transitions: StateFlow<List<ToolItem>> = _transitions.asStateFlow()

    internal val _fx = MutableStateFlow<List<ToolItem>>(emptyList())
    val fx: StateFlow<List<ToolItem>> = _fx.asStateFlow()

    /**
     * Transmission templates (LUT + FX + transition combos) loaded
     * from `assets/transmission_templates.json`. Surfaced to the UI
     * via the [TransmissionTemplatesPanel] chip strip. Live-validated
     * at load time by [TimelineTemplateManager.loadTransmissionTemplates]
     * so a renamed LUT or FX surfaces as a warning, not a crash.
     */
    internal val _transmissionTemplates = MutableStateFlow<List<TransmissionTemplate>>(emptyList())
    val transmissionTemplates: StateFlow<List<TransmissionTemplate>> = _transmissionTemplates.asStateFlow()

    // Thumbnail bitmaps keyed by clip ID → list of (timeMs, bitmap)
    internal val _thumbnails = MutableStateFlow<Map<String, List<Pair<Long, android.graphics.Bitmap>>>>(emptyMap())
    val thumbnails: StateFlow<Map<String, List<Pair<Long, android.graphics.Bitmap>>>> = _thumbnails.asStateFlow()

    internal val undoStack = ArrayDeque<EditorUndoSnapshot>()
    internal val redoStack = ArrayDeque<EditorUndoSnapshot>()

    // Overlay editing: one undo entry per gesture / slider drag.
    // Armed by the first live update of an interaction, disarmed when
    // the interaction ends (see EditorPipOverlayController).
    internal var overlayEditUndoArmed: Boolean = false

    internal val mediaPicker = context?.let { MediaPickerHelper(it) }
    internal val mediaAnalyzer = context?.let { MediaAnalyzer() }
    private val exportEngineInitialization = context?.let { suppliedContext ->
        runCatching { ExportEngine(suppliedContext.applicationContext) }
    }
    internal val exportEngine = exportEngineInitialization?.getOrNull()
    private val exportEngineInitializationError = exportEngineInitialization
        ?.exceptionOrNull()
        ?.let { "Export engine initialization failed: ${it.message ?: it.javaClass.simpleName}" }
    internal val audioEngine = context?.let { AudioEngine(it) }
    internal val colorGradingEngine = ColorGradingEngine()
    internal val media3VideoTrimmer = context?.let { com.apexstudio.app.data.trim.Media3VideoTrimmer(it) }
    internal val projectRepository: ProjectRepository? = context?.let { ProjectRepository(it) }
    internal val timelineTemplateManager = context?.let { TimelineTemplateManager(it) }
    internal var mainPlayerRef: androidx.media3.exoplayer.ExoPlayer? = null

    init {
        _export.update {
            it.copy(
                isExportEngineReady = exportEngine != null,
                error = exportEngineInitializationError ?: it.error
            )
        }
        savedStateHandle?.let { handle ->
            viewModelScope.launch {
                state.drop(1).collect { current ->
                    handle[SELECTED_CLIP_IDS_KEY] = ArrayList(current.selectedClipIds)
                    handle[MULTI_SELECT_MODE_KEY] = current.multiSelectMode
                }
            }
        }
        Log.d("ApexTrace", "EditorViewModel.init start")
        context?.let { CrashMarker.mark(it, "EditorViewModel.init start") }
        if (context != null || projectId != null) loadProject()
        loadLuts()
        loadTransmissionTemplates()
        // NOTE: no loadAudioState() — the audio list is synced from the
        // loaded project inside loadProject() (the old function read
        // the FIRST project from the stub repo, i.e. the wrong project
        // whenever several exist, diverging the mixer list).
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
                            error = st.error,
                            warning = st.warning
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


    override fun onCleared() {
        super.onCleared()
        localAnalysisToken?.cancel()
        localAnalysisJob?.cancel()
        faceTrackReviewToken?.cancel()
        faceTrackReviewJob?.cancel()
        videoUpscaleJob?.cancel()
        photoAiEnhanceJob?.cancel()
        endGestureEditing()
        audioEngine?.stopRecording()
        audioEngine?.release()
        exportEngine?.release()
        colorGradingEngine.release()
    }
}
