package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.ai.autoreframe.FaceTrackAnalysisResult
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.domain.model.SpeedPoint

/** Identity of every source property that affects which frames are analyzed and their timeline mapping. */
data class FaceTrackSourceSnapshot(
    val projectId: String?,
    val clipId: String,
    val uri: String,
    val type: ClipType,
    val durationMs: Long,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val speedPoints: List<SpeedPoint>,
) {
    fun matches(activeProjectId: String?, clip: MediaClip?): Boolean =
        clip != null && projectId == activeProjectId && clipId == clip.id && uri == clip.uri &&
            type == clip.type && durationMs == clip.durationMs && trimStartMs == clip.trimStartMs &&
            trimEndMs == clip.trimEndMs && speedPoints == clip.speedPoints

    companion object {
        fun from(projectId: String?, clip: MediaClip) = FaceTrackSourceSnapshot(
            projectId = projectId,
            clipId = clip.id,
            uri = clip.uri,
            type = clip.type,
            durationMs = clip.durationMs,
            trimStartMs = clip.trimStartMs,
            trimEndMs = clip.trimEndMs,
            speedPoints = clip.speedPoints.toList(),
        )
    }
}

/** Pure lookup used at inference boundaries and by tests; absent clips/projects never match. */
fun isFaceTrackSnapshotCurrent(snapshot: FaceTrackSourceSnapshot, project: Project?, fallbackProjectId: String?): Boolean {
    val activeProjectId = project?.id ?: fallbackProjectId
    val currentClip = project?.clips?.firstOrNull { it.id == snapshot.clipId }
    return snapshot.matches(activeProjectId, currentClip)
}

/** Lifecycle for local, sparse face-track analysis and review-only crop suggestions. */
enum class FaceTrackReviewStatus { IDLE, RUNNING, READY, CANCELLED, ERROR }

/**
 * Separate from ordinary signal analysis and protected AI actions. This state contains sampled
 * detections/crop candidates only; it does not imply real-time preview, export, or moderation approval.
 */
data class FaceTrackReviewUiState(
    val status: FaceTrackReviewStatus = FaceTrackReviewStatus.IDLE,
    val progress: Float = 0f,
    val message: String = "Offline face tracking review is available for a selected video.",
    val clipId: String? = null,
    val sourceUri: String? = null,
    val sourceStartMs: Long? = null,
    val sourceEndMs: Long? = null,
    val sourceSnapshot: FaceTrackSourceSnapshot? = null,
    val result: FaceTrackAnalysisResult? = null,
    val reviewedSampleTimestamps: Set<Long> = emptySet(),
    val error: String? = null,
) {
    val isRunning: Boolean get() = status == FaceTrackReviewStatus.RUNNING
    val isReady: Boolean get() = status == FaceTrackReviewStatus.READY
}
