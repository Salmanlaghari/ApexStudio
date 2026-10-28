package com.apexstudio.app.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import com.apexstudio.app.ai.AiCancellationToken
import com.apexstudio.app.ai.autoreframe.FaceAwareReframeEngine
import com.apexstudio.app.ai.autoreframe.FaceTrackAnalysisResult
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
        message = "The video changed. Run the review again to see current suggestions.",
        result = null,
        reviewedSampleTimestamps = emptySet(),
        error = null,
    )
}

/** Analyze a selected video locally and retain samples only for explicit review in the editor. */
fun EditorViewModel.startLocalFaceTrackReview(clipId: String? = null) {
    val reframeTarget = _faceTrackReview.value.reframeTarget
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
                clip == null -> "Select a video clip first."
                clip.type != ClipType.VIDEO -> "Choose a video clip."
                else -> "The editor context is unavailable."
            },
            message = "Face-framing review could not start."
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
            error = "This video has no usable duration.",
            message = "Face-framing review could not start."
        )
        return
    }

    val token = AiCancellationToken()
    faceTrackReviewToken = token
    _faceTrackReview.value = FaceTrackReviewUiState(
        status = FaceTrackReviewStatus.RUNNING,
        progress = 0f,
        message = "Looking for faces in sample frames…",
        clipId = clip.id,
        sourceUri = clip.uri,
        sourceStartMs = startMs,
        sourceEndMs = endMs,
        sourceSnapshot = snapshot,
        reframeTarget = reframeTarget,
    )
    faceTrackReviewJob = viewModelScope.launch {
        try {
            val result = com.apexstudio.app.ai.safety.GlobalModerationEnforcement
                .withExistingProjectVideoFaceReview(clip) {
                    LocalFaceTrackAnalyzer(outputAspectRatio = reframeTarget.aspectRatio).analyze(
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
                }
            if (faceTrackReviewToken === token) {
                if (!isFaceTrackSnapshotCurrent(snapshot)) {
                    invalidateStaleFaceTrackReview(snapshot)
                } else if (_faceTrackReview.value.sourceSnapshot == snapshot) {
                    _faceTrackReview.value = _faceTrackReview.value.copy(
                        status = FaceTrackReviewStatus.READY,
                        progress = 1f,
                        message = "Suggestions are ready. Review the sample frames; no edit has been applied.",
                        result = result,
                        reviewedSampleTimestamps = emptySet(),
                        error = null,
                    )
                }
            }
        } catch (_: CancellationException) {
            if (faceTrackReviewToken === token) {
                _faceTrackReview.update {
                    it.copy(status = FaceTrackReviewStatus.CANCELLED, message = "Review cancelled.")
                }
            }
        } catch (failure: Exception) {
            if (faceTrackReviewToken === token) {
                if (!isFaceTrackSnapshotCurrent(snapshot)) invalidateStaleFaceTrackReview(snapshot)
                else _faceTrackReview.update {
                    it.copy(
                        status = FaceTrackReviewStatus.ERROR,
                        message = "Face-framing review failed.",
                        error = "Could not read this video. Try another one.",
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
            message = "Review cancelled. Incomplete suggestions were cleared.",
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

/** Reuse actual detections for geometry review; there is no second inference or media mutation. */
internal fun faceReviewForTarget(
    result: FaceTrackAnalysisResult,
    target: FaceReframeReviewTarget,
): FaceTrackAnalysisResult {
    val crops = FaceAwareReframeEngine.track(result.sourceWidth, result.sourceHeight, target.aspectRatio,
        result.samples.map { sample -> sample.timestampMs to sample.faces.map { it.detection } })
    return result.copy(samples = result.samples.zip(crops) { sample, crop -> sample.copy(reframe = crop) })
}

/** Target changes invalidate review marks because they describe a different crop, not a new edit. */
fun EditorViewModel.setFaceReframeReviewTarget(target: FaceReframeReviewTarget): Boolean {
    val review = _faceTrackReview.value
    if (review.isRunning) return false
    if (review.reframeTarget == target) return true
    val snapshot = review.sourceSnapshot
    if (review.isReady && (snapshot == null || !isFaceTrackSnapshotCurrent(snapshot))) {
        if (snapshot != null) invalidateStaleFaceTrackReview(snapshot)
        return false
    }
    val result = review.result?.let { faceReviewForTarget(it, target) }
    _faceTrackReview.value = review.copy(
        reframeTarget = target,
        result = result,
        reviewedSampleTimestamps = emptySet(),
        message = if (review.isReady) "Reviewing ${target.label} suggestions. No edit has been applied."
            else review.message,
    )
    return true
}
