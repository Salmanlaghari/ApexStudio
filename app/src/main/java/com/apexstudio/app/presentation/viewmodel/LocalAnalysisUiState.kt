package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.ai.autoreframe.AutoCropSuggestion
import com.apexstudio.app.ai.tflite.SceneCutSuggestion
import com.apexstudio.app.ai.tflite.SmartTrimSuggestion

/** Lifecycle/status for generated video-edit suggestions. */
enum class LocalAnalysisStatus { IDLE, RUNNING, READY, CANCELLED, ERROR }

/**
 * Public editor-facing analysis state. The snapshot pins the project, source and full speed mapping;
 * apply/preview APIs reject stale suggestions if those change. Nothing is applied until
 * the corresponding suggestion is explicitly reviewed and the user invokes an apply method.
 */
data class LocalAnalysisUiState(
    val status: LocalAnalysisStatus = LocalAnalysisStatus.IDLE,
    val progress: Float = 0f,
    val message: String = "Select a video to find editing suggestions.",
    val clipId: String? = null,
    val sourceUri: String? = null,
    val sourceStartMs: Long? = null,
    val sourceEndMs: Long? = null,
    val sourceSnapshot: FaceTrackSourceSnapshot? = null,
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
