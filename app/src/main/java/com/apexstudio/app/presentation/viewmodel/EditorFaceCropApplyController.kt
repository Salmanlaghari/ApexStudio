package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.ai.AiModelUnavailableException
import com.apexstudio.app.ai.safety.GlobalModerationEnforcement
import com.apexstudio.app.ai.safety.ProtectedAiAction
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.VideoCropSettings
import kotlinx.coroutines.flow.update

/** Apply only a current, source-bound crop track after every measured sample was explicitly reviewed. */
fun EditorViewModel.applyReviewedFaceCropTrack(): Boolean {
    val review = _faceTrackReview.value
    val snapshot = review.sourceSnapshot ?: return false
    val result = review.result ?: return false
    if (!review.isReady || result.samples.isEmpty()) return false
    if (!isFaceTrackSnapshotCurrent(snapshot, _state.value.project, projectId)) {
        _faceTrackReview.update { it.copy(error = "The source changed. Analyze and review the current video again.") }
        return false
    }
    val clip = _state.value.project?.clips?.firstOrNull { it.id == snapshot.clipId } ?: return false
    if (_state.value.selectedClipId != clip.id) {
        _faceTrackReview.update { it.copy(error = "Select the reviewed video again before applying its face reframe.") }
        return false
    }
    if (clip.type != ClipType.VIDEO || clip.trackIndex != 0) {
        _faceTrackReview.update { it.copy(error = "Face reframe currently requires a main-track video, not a photo or overlay.") }
        return false
    }
    if (result.samples.any { it.timestampMs !in snapshot.trimStartMs until snapshot.trimEndMs } ||
        result.samples.none { it.faces.isNotEmpty() && it.reframe.faceFound } ||
        result.samples.any { it.timestampMs !in review.reviewedSampleTimestamps }) {
        _faceTrackReview.update { it.copy(error = "Review every measured sample first. At least one real face detection is required.") }
        return false
    }
    try {
        // Review marks are checked above against the current source snapshot before entering this
        // narrowly scoped capability. The scope is source-bound and authorizes no other AI action.
        val track = GlobalModerationEnforcement.withReviewedExistingProjectVideoFaceReframe(clip) {
            GlobalModerationEnforcement.requireApproved(ProtectedAiAction.FACE_AWARE_REFRAME, clip.uri)
            result.toPersistedCropTrack(review.reframeTarget.aspectRatio)
        }
        if (clip.faceCropTrack == track) return false
        pushUndo()
        updateClip(clip.id) { it.copy(faceCropTrack = track, videoCrop = VideoCropSettings()) }
    } catch (blocked: AiModelUnavailableException) {
        _faceTrackReview.update { it.copy(error = blocked.message) }
        return false
    } catch (_: IllegalArgumentException) {
        _faceTrackReview.update { it.copy(error = "Measured crop geometry is invalid. Run face analysis again.") }
        return false
    }
    _faceTrackReview.update { it.copy(message = "Reviewed source-time crop track applied. Undo is available.", error = null) }
    return true
}

/** Clearing an existing transform is an ordinary manual edit and must remain available when AI is blocked. */
fun EditorViewModel.clearFaceCropTrack(clipId: String): Boolean {
    val clip = _state.value.project?.clips?.firstOrNull { it.id == clipId } ?: return false
    if (clip.faceCropTrack == null) return false
    pushUndo()
    updateClip(clipId) { it.copy(faceCropTrack = null) }
    return true
}
