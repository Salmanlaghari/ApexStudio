package com.apexstudio.app.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import com.apexstudio.app.ai.AiCancellationToken
import com.apexstudio.app.ai.autoreframe.LocalFaceTrackAnalyzer
import com.apexstudio.app.domain.model.ClipType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private fun EditorViewModel.isFaceTrackSnapshotCurrent(snapshot: FaceTrackSourceSnapshot): Boolean =
    isFaceTrackSnapshotCurrent(snapshot, _state.value.project, projectId)

/** Clear any review data if the source identity captured for it no longer matches the editor. */
private fun EditorViewModel.invalidateStaleFaceTrackReview(snapshot: FaceTrackSourceSnapshot) {
    val review = _faceTrackReview.value
    if (review.sourceSnapshot != snapshot || isFaceTrackSnapshotCurrent(snapshot)) return
    if (faceTrackReviewToken != null) faceTrackReviewToken?.cancel()
    faceTrackReviewToken = null
    faceTrackReviewJob?.cancel()
    faceTrackReviewJob = null
    _faceTrackReview.value = review.copy(
        status = FaceTrackReviewStatus.CANCELLED,
        progress = 0f,
        message = "The video source or project changed. Run offline face tracking again to review the current source.",
        result = null,
        reviewedSampleTimestamps = emptySet(),
        error = null,
    )
}

/** Analyze a selected video locally and retain samples only for explicit review in the editor. */
fun EditorViewModel.startLocalFaceTrackReview(clipId: String? = null) {
    faceTrackReviewToken?.cancel()
    faceTrackReviewJob?.cancel()
    faceTrackReviewToken = null
    faceTrackReviewJob = null

    val targetId = clipId ?: _state.value.selectedClipId
        ?: _state.value.project?.clips?.firstOrNull { it.type == ClipType.VIDEO }?.id
    val project = _state.value.project
    val clip = targetId?.let { id -> project?.clips?.firstOrNull { it.id == id } }
    val appContext = context?.applicationContext
    if (clip == null || appContext == null || clip.type != ClipType.VIDEO) {
        _faceTrackReview.value = FaceTrackReviewUiState(
            status = FaceTrackReviewStatus.ERROR,
            error = when {
                clip == null -> "Select a video clip before running offline face tracking."
                clip.type != ClipType.VIDEO -> "Offline face tracking requires a video clip."
                else -> "The editor context is unavailable."
            },
            message = "Offline face tracking review could not start."
        )
        return
    }
    val snapshot = FaceTrackSourceSnapshot.from(project?.id ?: projectId, clip)
    val startMs = clip.trimStartMs.coerceAtLeast(0L)
    val endMs = minOf(clip.trimEndMs, clip.durationMs)
    if (endMs <= startMs) {
        _faceTrackReview.value = FaceTrackReviewUiState(
            status = FaceTrackReviewStatus.ERROR,
            clipId = clip.id,
            sourceUri = clip.uri,
            sourceStartMs = startMs,
            sourceEndMs = endMs,
            sourceSnapshot = snapshot,
            error = "The selected clip has no analyzable source duration.",
            message = "Offline face tracking review could not start."
        )
        return
    }

    val token = AiCancellationToken()
    faceTrackReviewToken = token
    _faceTrackReview.value = FaceTrackReviewUiState(
        status = FaceTrackReviewStatus.RUNNING,
        progress = 0f,
        message = "Sampling local video frames and tracking detected faces…",
        clipId = clip.id,
        sourceUri = clip.uri,
        sourceStartMs = startMs,
        sourceEndMs = endMs,
        sourceSnapshot = snapshot,
    )
    faceTrackReviewJob = viewModelScope.launch {
        try {
            val result = LocalFaceTrackAnalyzer().analyze(
                context = appContext,
                uri = snapshot.uri,
                sourceStartMs = startMs,
                sourceEndMs = endMs,
                cancellation = token,
            ) { progress ->
                // Decoder/model callbacks run on a worker. Serialize identity checks and lifecycle
                // mutations on Main so an old worker cannot cancel or overwrite a replacement run.
                viewModelScope.launch {
                    if (faceTrackReviewToken === token) {
                        if (!isFaceTrackSnapshotCurrent(snapshot)) {
                            invalidateStaleFaceTrackReview(snapshot)
                        } else {
                            _faceTrackReview.update { state ->
                                if (state.status == FaceTrackReviewStatus.RUNNING && state.sourceSnapshot == snapshot) {
                                    state.copy(progress = progress.coerceIn(0f, 1f))
                                } else state
                            }
                        }
                    }
                }
            }
            if (faceTrackReviewToken === token) {
                if (!isFaceTrackSnapshotCurrent(snapshot)) {
                    invalidateStaleFaceTrackReview(snapshot)
                } else if (_faceTrackReview.value.sourceSnapshot == snapshot) {
                    _faceTrackReview.value = _faceTrackReview.value.copy(
                        status = FaceTrackReviewStatus.READY,
                        progress = 1f,
                        message = "Offline analysis complete. Review sampled detections and crop suggestions; no edit was applied.",
                        result = result,
                        reviewedSampleTimestamps = emptySet(),
                        error = null,
                    )
                }
            }
        } catch (_: CancellationException) {
            if (faceTrackReviewToken === token) {
                _faceTrackReview.update {
                    it.copy(status = FaceTrackReviewStatus.CANCELLED, message = "Offline face tracking analysis cancelled.")
                }
            }
        } catch (failure: Exception) {
            if (faceTrackReviewToken === token) {
                if (!isFaceTrackSnapshotCurrent(snapshot)) invalidateStaleFaceTrackReview(snapshot)
                else _faceTrackReview.update {
                    it.copy(
                        status = FaceTrackReviewStatus.ERROR,
                        message = "Offline face tracking analysis failed.",
                        error = failure.message ?: failure::class.java.simpleName,
                        result = null,
                        reviewedSampleTimestamps = emptySet(),
                    )
                }
            }
        } finally {
            if (faceTrackReviewToken === token) {
                faceTrackReviewToken = null
                faceTrackReviewJob = null
            }
        }
    }
}

/** Cancel current decoder/inference work; incomplete output is never shown as a completed review. */
fun EditorViewModel.cancelLocalFaceTrackReview() {
    faceTrackReviewToken?.cancel()
    faceTrackReviewJob?.cancel()
    faceTrackReviewToken = null
    faceTrackReviewJob = null
    _faceTrackReview.update {
        if (it.status == FaceTrackReviewStatus.RUNNING) it.copy(
            status = FaceTrackReviewStatus.CANCELLED,
            progress = 0f,
            message = "Offline face tracking analysis cancelled; partial samples were discarded.",
            result = null,
            reviewedSampleTimestamps = emptySet(),
            error = null,
        ) else it
    }
}

/** Only a current, real completed sample can be previewed; returns false before any source seek otherwise. */
fun EditorViewModel.canPreviewFaceTrackSample(timestampMs: Long): Boolean {
    val review = _faceTrackReview.value
    val snapshot = review.sourceSnapshot ?: return false
    if (!review.isReady || review.result?.samples?.any { it.timestampMs == timestampMs } != true) return false
    if (!isFaceTrackSnapshotCurrent(snapshot)) {
        invalidateStaleFaceTrackReview(snapshot)
        return false
    }
    return true
}

/** Mark only a real, completed sampled frame as reviewed; this does not authorize or apply an edit. */
fun EditorViewModel.setFaceTrackSampleReviewed(timestampMs: Long, reviewed: Boolean) {
    val state = _faceTrackReview.value
    val snapshot = state.sourceSnapshot ?: return
    if (!state.isReady || state.result?.samples?.any { it.timestampMs == timestampMs } != true) return
    if (!isFaceTrackSnapshotCurrent(snapshot)) {
        invalidateStaleFaceTrackReview(snapshot)
        return
    }
    _faceTrackReview.update {
        if (it.sourceSnapshot != snapshot || !it.isReady) it
        else it.copy(reviewedSampleTimestamps = if (reviewed) it.reviewedSampleTimestamps + timestampMs
            else it.reviewedSampleTimestamps - timestampMs)
    }
}
