package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.data.captions.CaptionFillerCaptionEditor
import com.apexstudio.app.data.captions.CaptionFillerCutPlanner
import com.apexstudio.app.data.captions.CaptionFillerDetector
import com.apexstudio.app.data.captions.CaptionFillerWordStream
import com.apexstudio.app.data.captions.DetectedCaptionFiller
import com.apexstudio.app.data.captions.TimelineCutRange
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.presentation.state.OverlayTransform
import java.util.UUID
import kotlinx.coroutines.flow.update

/** Review data is ephemeral editor state; the user's vocabulary is persisted separately in app settings. */
data class CaptionFillerReviewState(
    val isOpen: Boolean = false,
    val findings: List<DetectedCaptionFiller> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val fillerWords: List<String> = CaptionFillerDetector.defaultWords,
    val message: String? = null
)

fun EditorViewModel.openCaptionFillerReview(fillerWords: List<String>) {
    val project = _state.value.project ?: return
    val words = CaptionFillerDetector.normalizeList(fillerWords)
    val found = CaptionFillerDetector.detect(CaptionFillerWordStream.fromMainVideoClips(project.clips), words)
    _captionFillerReviewState.value = CaptionFillerReviewState(
        isOpen = true,
        findings = found,
        selectedIds = found.map { it.id }.toSet(),
        fillerWords = words,
        message = if (found.isEmpty()) "No measured filler words found in the caption track." else null
    )
}

fun EditorViewModel.setCaptionFillerSelected(id: String, selected: Boolean) {
    _captionFillerReviewState.update { state ->
        if (!state.isOpen || state.findings.none { it.id == id }) state
        else state.copy(selectedIds = if (selected) state.selectedIds + id else state.selectedIds - id)
    }
}

fun EditorViewModel.closeCaptionFillerReview() {
    _captionFillerReviewState.value = CaptionFillerReviewState()
}

/** Applies the approved source-time cuts and caption edits as one project snapshot/undo entry. */
fun EditorViewModel.applyCaptionFillerRemoval() {
    val review = _captionFillerReviewState.value
    if (!review.isOpen) return
    val project = _state.value.project ?: return closeCaptionFillerReview()
    val currentFindings = CaptionFillerDetector.detect(
        CaptionFillerWordStream.fromMainVideoClips(project.clips), review.fillerWords
    ).distinctBy { it.id }.associateBy { it.id }
    val selected = review.selectedIds.mapNotNull(currentFindings::get)
    if (selected.isEmpty()) {
        closeCaptionFillerReview()
        return
    }
    if (selected.size != review.selectedIds.size) {
        _captionFillerReviewState.value = review.copy(
            findings = currentFindings.values.toList(),
            selectedIds = review.selectedIds.intersect(currentFindings.keys),
            message = "Captions changed while this review was open. Check the refreshed list before applying."
        )
        return
    }

    val cuts = selected.mapNotNull { finding ->
        val word = finding.timedWord
        if (word.startMs >= 0L && word.endMs > word.startMs) TimelineCutRange(word.startMs, word.endMs) else null
    }
    val plan = CaptionFillerCutPlanner.plan(project.clips, cuts)
    if (plan.removedDurationMs <= 0L) {
        _captionFillerReviewState.value = review.copy(message = "No approved filler ranges overlap the main video timeline.")
        return
    }
    val wordsByClip = selected.map { it.timedWord }.groupBy { it.clipId }
    val changedCaptions = project.clips.associate { clip ->
        clip.id to CaptionFillerCaptionEditor.removeSelectedWords(clip, wordsByClip[clip.id].orEmpty())
    }
    val normalizedCuts = mergeTimelineCuts(cuts)
    val newClips = mutableListOf<MediaClip>()
    project.clips.forEach { clip ->
        val plannedSegments = plan.keptSegmentsByClipId[clip.id]
        if (plannedSegments != null) {
            val rewritten = clip.copy(textOverlays = changedCaptions[clip.id] ?: clip.textOverlays)
            plannedSegments.forEachIndexed { index, segment ->
                val overlays = sliceOverlays(rewritten.textOverlays, segment.localOutputStartMs, segment.localOutputEndMs)
                    .map { overlay -> if (index == 0) overlay else overlay.copy(id = UUID.randomUUID().toString()) }
                newClips += rewritten.copy(
                    id = if (index == 0) clip.id else "clip_filler_${UUID.randomUUID()}",
                    trimStartMs = segment.sourceStartMs,
                    trimEndMs = segment.sourceEndMs,
                    textOverlays = overlays
                )
            }
        } else {
            val updatedCaptionClip = if (clip.id in changedCaptions) {
                clip.copy(textOverlays = changedCaptions.getValue(clip.id))
            } else clip
            newClips += if (clip.trackIndex == 0) updatedCaptionClip else updatedCaptionClip.copy(
                timelineOffsetMs = mapAfterCuts(clip.timelineOffsetMs, normalizedCuts)
            )
        }
    }

    val newDuration = com.apexstudio.app.presentation.viewmodel.computeTimelineDurationMs(newClips, project.audioTracks)
    val oldPlayhead = livePlayheadMs()
    val newPlayhead = mapAfterCuts(oldPlayhead, normalizedCuts).coerceIn(0L, newDuration)
    pushUndo()
    _playerPosition.value = newPlayhead
    _state.update { state ->
        val activeProject = state.project ?: return@update state
        if (activeProject.id != project.id) return@update state
        state.copy(
            project = activeProject.copy(clips = newClips, durationMs = newDuration),
            durationMs = newDuration,
            currentTimeMs = newPlayhead,
            playerPositionMs = newPlayhead,
            selectedClipId = state.selectedClipId?.takeIf { id -> newClips.any { it.id == id } }
                ?: newClips.firstOrNull { it.trackIndex == 0 && it.type == ClipType.VIDEO }?.id,
            selectedOverlayClipId = state.selectedOverlayClipId?.takeIf { id ->
                newClips.any { it.id == id && it.type == ClipType.OVERLAY }
            },
            selectedTextOverlayId = state.selectedTextOverlayId?.takeIf { id ->
                newClips.any { clip -> clip.textOverlays.any { it.id == id } }
            },
            overlayClipId = state.overlayClipId?.takeIf { id -> newClips.any { it.id == id } },
            overlayTransform = if (state.overlayClipId != null && newClips.none { it.id == state.overlayClipId })
                OverlayTransform.Identity else state.overlayTransform,
            canUndo = true,
            canRedo = false
        )
    }
    persistProject()
    closeCaptionFillerReview()
}

private fun sliceOverlays(overlays: List<TextOverlay>, segmentStartMs: Long, segmentEndMs: Long): List<TextOverlay> =
    overlays.mapNotNull { overlay ->
        val start = maxOf(overlay.startMs, segmentStartMs)
        val end = minOf(CaptionFillerWordStream.overlayEndExclusive(overlay), segmentEndMs)
        if (end <= start) return@mapNotNull null
        val newStart = (start - segmentStartMs).coerceAtLeast(0L)
        val newEndExclusive = (end - segmentStartMs).coerceAtLeast(newStart + 1L)
        overlay.copy(
            startMs = newStart,
            endMs = if (overlay.rangeEndExclusive) newEndExclusive else newEndExclusive - 1L
        )
    }

private fun mapAfterCuts(timeMs: Long, cuts: List<TimelineCutRange>): Long {
    var removed = 0L
    for (cut in cuts) {
        if (timeMs <= cut.startMs) break
        removed += if (timeMs >= cut.endMs) cut.endMs - cut.startMs else timeMs - cut.startMs
    }
    return (timeMs - removed).coerceAtLeast(0L)
}

private fun mergeTimelineCuts(cuts: List<TimelineCutRange>): List<TimelineCutRange> {
    val sorted = cuts.sortedBy { it.startMs }
    val merged = mutableListOf<TimelineCutRange>()
    sorted.forEach { cut ->
        val last = merged.lastOrNull()
        if (last == null || cut.startMs > last.endMs) merged += cut
        else merged[merged.lastIndex] = TimelineCutRange(last.startMs, maxOf(last.endMs, cut.endMs))
    }
    return merged
}
