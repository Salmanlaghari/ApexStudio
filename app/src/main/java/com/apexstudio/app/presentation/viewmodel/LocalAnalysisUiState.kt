package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.ai.autoreframe.AutoCropSuggestion
import com.apexstudio.app.ai.tflite.SceneCutSuggestion
import com.apexstudio.app.ai.tflite.SmartTrimSuggestion

/** Lifecycle/status for deterministic, on-device signal analysis (not learned AI inference). */
enum class LocalAnalysisStatus { IDLE, RUNNING, READY, CANCELLED, ERROR }

/**
 * Public editor-facing analysis state. sourceUri and trim bounds capture the analyzed source window;
 * apply APIs reject stale suggestions if that clip or range has changed. Nothing is applied until
 * the corresponding suggestion is explicitly reviewed and the user invokes an apply method.
 */
data class LocalAnalysisUiState(
    val status: LocalAnalysisStatus = LocalAnalysisStatus.IDLE,
    val progress: Float = 0f,
    val message: String = "Offline heuristic analysis (not learned-model inference)",
    val clipId: String? = null,
    val sourceUri: String? = null,
    val sourceStartMs: Long? = null,
    val sourceEndMs: Long? = null,
    val sceneCuts: List<SceneCutSuggestion> = emptyList(),
    val smartTrims: List<SmartTrimSuggestion> = emptyList(),
    val autoCrop: AutoCropSuggestion? = null,
    val sampledFrameCount: Int = 0,
    val reviewedSuggestionIds: Set<String> = emptySet(),
    val error: String? = null
) {
    val isRunning: Boolean get() = status == LocalAnalysisStatus.RUNNING
    val isReady: Boolean get() = status == LocalAnalysisStatus.READY
}
