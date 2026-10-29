package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.ai.autoreframe.FaceTrackAnalysisResult
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.domain.model.SpeedPoint
import com.apexstudio.app.domain.model.SpeedCurve

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
    val speedMultiplier: Float,
    val speedCurve: SpeedCurve,
    val rampStartSpeed: Float,
    val rampEndSpeed: Float,
) {
    fun matches(activeProjectId: String?, clip: MediaClip?): Boolean =
        clip != null && projectId == activeProjectId && clipId == clip.id && uri == clip.uri &&
            type == clip.type && durationMs == clip.durationMs && trimStartMs == clip.trimStartMs &&
            trimEndMs == clip.trimEndMs && speedPoints == clip.speedPoints &&
            speedMultiplier == clip.speedMultiplier && speedCurve == clip.speedCurve &&
            rampStartSpeed == clip.rampStartSpeed && rampEndSpeed == clip.rampEndSpeed

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
            speedMultiplier = clip.speedMultiplier,
            speedCurve = clip.speedCurve,
            rampStartSpeed = clip.rampStartSpeed,
            rampEndSpeed = clip.rampEndSpeed,
        )
    }
}

/** Pure lookup used at inference boundaries and by tests; absent clips/projects never match. */
fun isFaceTrackSnapshotCurrent(snapshot: FaceTrackSourceSnapshot, project: Project?, fallbackProjectId: String?): Boolean {
    val activeProjectId = project?.id ?: fallbackProjectId
    val currentClip = project?.clips?.firstOrNull { it.id == snapshot.clipId }
    return snapshot.matches(activeProjectId, currentClip)
}

/** Lifecycle for sampled face-framing suggestions and review. */
enum class FaceTrackReviewStatus { IDLE, RUNNING, READY, CANCELLED, ERROR }

/** Review targets only: changing this never applies a crop track or alters the project canvas. */
enum class FaceReframeReviewTarget(val label: String, val aspectRatio: Float) {
    PORTRAIT("9:16 · Shorts/Reels", 9f / 16f),
    SQUARE("1:1 · Square", 1f),
    LANDSCAPE("16:9 · Landscape", 16f / 9f),
}

/**
 * Separate from ordinary signal analysis and protected AI actions. This state contains sampled
 * detections/crop candidates only; it does not imply real-time preview, export, or moderation approval.
 */
data class FaceTrackReviewUiState(
    val status: FaceTrackReviewStatus = FaceTrackReviewStatus.IDLE,
    val progress: Float = 0f,
    val message: String = "Select a video to review face-framing suggestions.",
    val clipId: String? = null,
    val sourceUri: String? = null,
    val sourceStartMs: Long? = null,
    val sourceEndMs: Long? = null,
    val sourceSnapshot: FaceTrackSourceSnapshot? = null,
    val result: FaceTrackAnalysisResult? = null,
    val reviewedSampleTimestamps: Set<Long> = emptySet(),
    val reframeTarget: FaceReframeReviewTarget = FaceReframeReviewTarget.PORTRAIT,
    val error: String? = null,
) {
    val isRunning: Boolean get() = status == FaceTrackReviewStatus.RUNNING
    val isReady: Boolean get() = status == FaceTrackReviewStatus.READY
}
