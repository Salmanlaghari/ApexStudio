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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


class EditorViewModel(
    internal val repo: MediaRepository = MediaRepository,
    internal val context: android.content.Context? = null,
    internal val projectId: String? = null
) : ViewModel() {

    internal val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    internal val _export = MutableStateFlow(ExportState())
    val export: StateFlow<ExportState> = _export.asStateFlow()

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

    internal val undoStack = ArrayDeque<List<MediaClip>>()
    internal val redoStack = ArrayDeque<List<MediaClip>>()

    internal val mediaPicker = context?.let { MediaPickerHelper(it) }
    internal val mediaAnalyzer = context?.let { MediaAnalyzer() }
    internal val exportEngine = context?.let { ExportEngine(it) }
    internal val audioEngine = context?.let { AudioEngine(it) }
    internal val colorGradingEngine = ColorGradingEngine()
    internal val media3VideoTrimmer = context?.let { com.apexstudio.app.data.trim.Media3VideoTrimmer(it) }
    internal val projectRepository: ProjectRepository? = context?.let { ProjectRepository(it) }
    internal val timelineTemplateManager = context?.let { TimelineTemplateManager(it) }
    internal var mainPlayerRef: androidx.media3.exoplayer.ExoPlayer? = null

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


    override fun onCleared() {
        super.onCleared()
        audioEngine?.stopRecording()
        audioEngine?.release()
        exportEngine?.release()
        colorGradingEngine.release()
    }
}
