package com.apexstudio.app.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import com.apexstudio.app.ai.AiCancellationToken
import com.apexstudio.app.ai.tflite.LocalVideoSceneAnalyzer
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.VideoCropSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update

private const val LOCAL_CROP_REVIEW_ID = "local-auto-crop"

/**
 * Analyze the selected video clip (or the supplied clip) by decoding a bounded set of actual frames
 * on-device. The output is deterministic heuristic analysis, not TFLite/learned inference.
 */
fun EditorViewModel.startLocalAnalysis(clipId: String? = null) {
    localAnalysisToken?.cancel()
    localAnalysisJob?.cancel()
    localAnalysisToken = null
    localAnalysisJob = null
    val targetId = clipId ?: _state.value.selectedClipId
        ?: _state.value.project?.clips?.firstOrNull { it.type == ClipType.VIDEO }?.id
    val clip = targetId?.let { id -> _state.value.project?.clips?.firstOrNull { it.id == id } }
    val appContext = context?.applicationContext
    if (clip == null || appContext == null || clip.type != ClipType.VIDEO) {
        _localAnalysis.value = LocalAnalysisUiState(
            status = LocalAnalysisStatus.ERROR,
            error = when {
                clip == null -> "Select a video clip before running local analysis."
                clip.type != ClipType.VIDEO -> "Offline scene analysis requires a video clip."
                else -> "The editor context is unavailable."
            }
        )
        return
    }
    val snapshot = FaceTrackSourceSnapshot.from(_state.value.project?.id ?: projectId, clip)
    val startMs = clip.trimStartMs.coerceAtLeast(0L)
    val endMs = minOf(clip.trimEndMs, clip.durationMs)
    if (endMs <= startMs) {
        _localAnalysis.value = LocalAnalysisUiState(
            status = LocalAnalysisStatus.ERROR,
            clipId = clip.id,
            sourceUri = clip.uri,
            sourceStartMs = startMs,
            sourceEndMs = endMs,
            error = "The selected clip has no analyzable source duration."
        )
        return
    }

    val token = AiCancellationToken()
    localAnalysisToken = token
    _localAnalysis.value = LocalAnalysisUiState(
        status = LocalAnalysisStatus.RUNNING,
        progress = 0f,
        message = "Decoding video frames and calculating signal heuristics…",
        clipId = clip.id,
        sourceUri = clip.uri,
        sourceStartMs = startMs,
        sourceEndMs = endMs,
        sourceSnapshot = snapshot
    )
    localAnalysisJob = viewModelScope.launch {
        try {
            val result = LocalVideoSceneAnalyzer().analyze(
                context = appContext,
                uri = clip.uri,
                sourceStartMs = startMs,
                sourceEndMs = endMs,
                cancellation = token
            ) { progress ->
                // Decoder callbacks run on IO. Serialize identity checks and lifecycle changes on
                // Main so an old worker cannot overwrite or cancel a newer analysis request.
                viewModelScope.launch {
                    if (localAnalysisToken === token && ensureLocalAnalysisCurrent()) {
                        _localAnalysis.update { state ->
                            if (state.status == LocalAnalysisStatus.RUNNING && state.sourceSnapshot == snapshot) {
                                state.copy(progress = if (progress.isFinite()) progress.coerceIn(0f, 1f) else state.progress)
                            } else state
                        }
                    }
                }
            }
            if (localAnalysisToken === token && ensureLocalAnalysisCurrent()) {
                _localAnalysis.value = _localAnalysis.value.copy(
                    status = LocalAnalysisStatus.READY,
                    progress = 1f,
                    message = "Analysis complete. Review each suggestion before applying it.",
                    sceneCuts = result.cuts,
                    smartTrims = result.smartTrims,
                    autoCrop = result.autoCrop,
                    sampledFrameCount = result.sampledFrameCount,
                    reviewedSuggestionIds = emptySet(),
                    error = null
                )
            }
        } catch (_: CancellationException) {
            if (localAnalysisToken === token) {
                _localAnalysis.update { it.copy(status = LocalAnalysisStatus.CANCELLED, message = "Local analysis cancelled.") }
            }
        } catch (failure: Exception) {
            if (localAnalysisToken === token && ensureLocalAnalysisCurrent()) {
                _localAnalysis.update {
                    it.copy(status = LocalAnalysisStatus.ERROR, message = "Local analysis failed.", error = failure.message ?: failure::class.java.simpleName)
                }
            }
        } finally {
            if (localAnalysisToken === token) {
                localAnalysisToken = null
                localAnalysisJob = null
            }
        }
    }
}

/** Stop both decoder work and the CPU heuristic loop; partial results are not presented as complete. */
fun EditorViewModel.cancelLocalAnalysis() {
    localAnalysisToken?.cancel()
    localAnalysisJob?.cancel()
    localAnalysisToken = null
    localAnalysisJob = null
    _localAnalysis.update {
        if (it.status == LocalAnalysisStatus.RUNNING) it.copy(
            status = LocalAnalysisStatus.CANCELLED,
            message = "Local analysis cancelled.",
            progress = 0f,
            sceneCuts = emptyList(),
            smartTrims = emptyList(),
            autoCrop = null,
            sampledFrameCount = 0,
            reviewedSuggestionIds = emptySet()
        ) else it
    }
}

/** Mark or unmark a displayed candidate. Apply operations accept only currently reviewed IDs. */
fun EditorViewModel.setLocalSuggestionReviewed(id: String, reviewed: Boolean) {
    val state = _localAnalysis.value
    val exists = state.sceneCuts.any { it.id == id } || state.smartTrims.any { it.id == id } ||
        (id == LOCAL_CROP_REVIEW_ID && state.autoCrop != null)
    if (!exists || !state.isReady) return
    if (!ensureLocalAnalysisCurrent()) return
    _localAnalysis.update {
        it.copy(reviewedSuggestionIds = if (reviewed) it.reviewedSuggestionIds + id else it.reviewedSuggestionIds - id)
    }
}

/** Mark a scene cut as seen; review is mandatory before apply. */
fun EditorViewModel.reviewLocalSceneCut(cutId: String) = setLocalSuggestionReviewed(cutId, true)

/** Mark a Smart Trim candidate as seen; review is mandatory before apply. */
fun EditorViewModel.reviewLocalSuggestedTrim(suggestionId: String) = setLocalSuggestionReviewed(suggestionId, true)

/** Mark the signal-based auto-crop candidate as seen; review is mandatory before apply. */
fun EditorViewModel.reviewLocalAutoCrop() = setLocalSuggestionReviewed(LOCAL_CROP_REVIEW_ID, true)

/** Apply all reviewed scene boundaries as adjacent source-time clips, recorded as one undo step. */
fun EditorViewModel.applyLocalSceneCuts(): Boolean {
    val analysis = _localAnalysis.value
    if (!analysis.isReady || analysis.clipId == null || analysis.sourceStartMs == null || analysis.sourceEndMs == null) return false
    if (!ensureLocalAnalysisCurrent()) return false
    val clip = _state.value.project?.clips?.firstOrNull { it.id == analysis.clipId } ?: return staleLocalAnalysis("Analyzed clip no longer exists.")
    if (!matchesAnalyzedWindow(clip, analysis)) {
        return staleLocalAnalysis("The clip changed after analysis. Run local analysis again before applying.")
    }
    if (clip.type != ClipType.VIDEO) return false
    val complexClip = clip.speedMultiplier != 1f || clip.speedCurve != com.apexstudio.app.domain.model.SpeedCurve.LINEAR ||
        clip.speedPoints.isNotEmpty() || !clip.keyframes.isEmpty() || clip.textOverlays.isNotEmpty() ||
        clip.stickers.isNotEmpty() || clip.mask != null
    val hasTransition = _state.value.project?.transitions.orEmpty().any {
        it.fromClipId == clip.id || it.toClipId == clip.id
    }
    // The editor's history currently snapshots clips only, not transition links. Retargeting those
    // links here would leave invalid references after Undo, so reject rather than corrupt history.
    if (complexClip || hasTransition) return staleLocalAnalysis(
        "Scene splitting is unavailable for clips with speed ramps, keyframes, masks, titles, stickers, or transitions. Split manually or remove those edits first to preserve timing and undo."
    )
    val reviewedCuts = analysis.sceneCuts
        .filter { it.id in analysis.reviewedSuggestionIds }
        .map { it.timestampMs }
        .distinct()
        .sorted()
    if (reviewedCuts.isEmpty()) return false

    val boundaries = mutableListOf(clip.trimStartMs)
    for (cut in reviewedCuts) {
        val previous = boundaries.last()
        // Establish ordering before subtraction: malformed suggestion values must not overflow.
        if (cut > previous && cut < clip.trimEndMs &&
            cut - previous >= 100L && clip.trimEndMs - cut >= 100L) boundaries += cut
    }
    if (boundaries.size == 1) return false
    boundaries += clip.trimEndMs
    val pieces = boundaries.zipWithNext().mapIndexed { index, (start, end) ->
        clip.copy(
            id = if (index == 0) clip.id else "clip_${java.util.UUID.randomUUID()}_scene",
            trimStartMs = start,
            trimEndMs = end,
            // Keep the original source duration: each piece still refers to the same media URI
            // and its trim offsets remain absolute source timestamps.
            durationMs = clip.durationMs
        )
    }
    pushUndo()
    _state.update { current ->
        val project = current.project ?: return@update current
        val replaced = buildList {
            project.clips.forEach { item ->
                if (item.id == clip.id) addAll(pieces) else add(item)
            }
        }
        current.copy(project = project.copy(clips = replaced))
    }
    refreshTimelineDuration()
    clearLocalAnalysisAfterApply("Reviewed scene cuts applied as one undoable edit.")
    return true
}

/** Apply one reviewed Smart Trim candidate using the editor's normal trim/ripple/history pathway. */
fun EditorViewModel.applyLocalSuggestedTrim(suggestionId: String): Boolean {
    val analysis = _localAnalysis.value
    if (!analysis.isReady || suggestionId !in analysis.reviewedSuggestionIds) return false
    if (!ensureLocalAnalysisCurrent()) return false
    val suggestion = analysis.smartTrims.firstOrNull { it.id == suggestionId } ?: return false
    val clip = _state.value.project?.clips?.firstOrNull { it.id == analysis.clipId } ?: return staleLocalAnalysis("Analyzed clip no longer exists.")
    if (!matchesAnalyzedWindow(clip, analysis)) return staleLocalAnalysis("The clip changed after analysis. Run local analysis again before applying.")
    if (suggestion.startMs < clip.trimStartMs || suggestion.endMs > clip.trimEndMs || suggestion.endMs - suggestion.startMs < 100L) return false
    trimClip(clip.id, suggestion.startMs, suggestion.endMs)
    clearLocalAnalysisAfterApply("Reviewed Smart Trim applied. Undo is available in the editor.")
    return true
}

/** Apply the reviewed luma-gradient crop through normal clip history; this is not subject detection. */
fun EditorViewModel.applyLocalAutoCrop(): Boolean {
    val analysis = _localAnalysis.value
    val crop = analysis.autoCrop ?: return false
    if (!analysis.isReady || LOCAL_CROP_REVIEW_ID !in analysis.reviewedSuggestionIds) return false
    if (!ensureLocalAnalysisCurrent()) return false
    val clip = _state.value.project?.clips?.firstOrNull { it.id == analysis.clipId } ?: return staleLocalAnalysis("Analyzed clip no longer exists.")
    if (!matchesAnalyzedWindow(clip, analysis)) return staleLocalAnalysis("The clip changed after analysis. Run local analysis again before applying.")
    pushUndo()
    updateClip(clip.id) {
        it.copy(videoCrop = VideoCropSettings(
            left = crop.crop.left,
            top = crop.crop.top,
            right = crop.crop.right,
            bottom = crop.crop.bottom,
            presetId = "local-signal"
        ), faceCropTrack = null)
    }
    clearLocalAnalysisAfterApply("Reviewed 9:16 signal-based crop applied. Undo is available in the editor.")
    return true
}

private fun EditorViewModel.clearLocalAnalysisAfterApply(message: String) {
    _localAnalysis.update {
        it.copy(
            status = LocalAnalysisStatus.IDLE,
            progress = 0f,
            message = message,
            sceneCuts = emptyList(),
            smartTrims = emptyList(),
            autoCrop = null,
            sampledFrameCount = 0,
            reviewedSuggestionIds = emptySet(),
            error = null
        )
    }
}

private fun EditorViewModel.matchesAnalyzedWindow(
    clip: com.apexstudio.app.domain.model.MediaClip,
    analysis: LocalAnalysisUiState
): Boolean = clip.trimStartMs >= 0L && clip.trimEndMs > clip.trimStartMs &&
    clip.trimEndMs <= clip.durationMs && clip.uri == analysis.sourceUri &&
    clip.trimStartMs == analysis.sourceStartMs &&
    clip.trimEndMs == analysis.sourceEndMs

private fun EditorViewModel.staleLocalAnalysis(message: String): Boolean {
    _localAnalysis.update { it.copy(error = message, message = message) }
    return false
}

/** Recheck identity before preview, review, progress or committing an analysis result. */
private fun EditorViewModel.ensureLocalAnalysisCurrent(): Boolean {
    val analysis = _localAnalysis.value
    val snapshot = analysis.sourceSnapshot
    if (snapshot != null && isFaceTrackSnapshotCurrent(snapshot, _state.value.project, projectId)) return true
    localAnalysisToken?.cancel()
    localAnalysisToken = null
    localAnalysisJob?.cancel()
    localAnalysisJob = null
    _localAnalysis.value = analysis.copy(
        status = LocalAnalysisStatus.CANCELLED,
        progress = 0f,
        message = "The project, source or speed changed. Run local analysis again before previewing or applying.",
        error = "The analyzed source changed or its identity is unavailable.",
        sceneCuts = emptyList(),
        smartTrims = emptyList(),
        autoCrop = null,
        sampledFrameCount = 0,
        reviewedSuggestionIds = emptySet(),
    )
    return false
}

/** Only real reviewed-flow candidate times may seek the still-current analyzed source. */
fun EditorViewModel.canPreviewLocalAnalysisTime(timestampMs: Long): Boolean {
    val analysis = _localAnalysis.value
    if (!analysis.isReady) return false
    val exists = analysis.sceneCuts.any { it.timestampMs == timestampMs } ||
        analysis.smartTrims.any { it.startMs == timestampMs || it.endMs - 1L == timestampMs } ||
        (analysis.autoCrop != null && analysis.sourceStartMs == timestampMs)
    if (!exists) return false
    return ensureLocalAnalysisCurrent()
}
