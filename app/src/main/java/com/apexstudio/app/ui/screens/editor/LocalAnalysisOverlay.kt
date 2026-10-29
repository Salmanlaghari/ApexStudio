package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.viewmodel.*
import com.apexstudio.app.ui.theme.EditorPalette as ApexPalette
import java.util.Locale

/** Review-first video suggestions; face framing remains review-only until application is available. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalAnalysisOverlay(
    vm: EditorViewModel,
    onPreviewSourceTime: (String, Long) -> Unit,
    onClose: () -> Unit
) {
    val analysis by vm.localAnalysis.collectAsStateWithLifecycle()
    val faceReview by vm.faceTrackReview.collectAsStateWithLifecycle()
    val editor by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf("Scenes") }
    val analysisSourceCurrent = isLocalReviewSourceCurrent(analysis.sourceSnapshot, editor.project)
    val faceReviewSourceCurrent = isLocalReviewSourceCurrent(faceReview.sourceSnapshot, editor.project)
    val analyzedClip = editor.project?.clips?.firstOrNull { it.id == analysis.clipId }
    val close = { vm.cancelLocalAnalysis(); vm.cancelLocalFaceTrackReview(); onClose() }
    val previewAnalysisSource: (Long) -> Unit = { timestamp ->
        if (vm.canPreviewLocalAnalysisTime(timestamp)) {
            vm.localAnalysis.value.clipId?.let { onPreviewSourceTime(it, timestamp) }
        }
    }
    ModalBottomSheet(onDismissRequest = close, containerColor = ApexPalette.BgSurface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Video suggestions", style = MaterialTheme.typography.titleLarge,
                    color = ApexPalette.TextPrimary, modifier = Modifier.weight(1f))
                TextButton(onClick = close) { Text("Close") }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(analyzedClip?.name ?: "Choose a video in the timeline", modifier = Modifier.weight(1f),
                    maxLines = 1, color = ApexPalette.TextPrimary)
                Button(onClick = { vm.startLocalAnalysis() }, enabled = !analysis.isRunning && !faceReview.isRunning) {
                    Text(if (analysis.isReady) "Find again" else "Find suggestions")
                }
            }

            val faceReviewTargetClip = editor.project?.let { project ->
                editor.selectedClipId?.let { selectedId -> project.clips.firstOrNull { it.id == selectedId } }
                    ?: project.clips.firstOrNull { it.type == com.apexstudio.app.domain.model.ClipType.VIDEO }
            }
            if (faceReview.isRunning) {
                Text(faceReview.message, modifier = Modifier.padding(horizontal = 16.dp).testTag("face_review_status"),
                    style = MaterialTheme.typography.bodySmall, color = ApexPalette.TextSecondary)
                TextButton(onClick = { vm.cancelLocalFaceTrackReview() }, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancel")
                }
            }
            FaceReviewResultsBlock(
                faceReview = faceReview,
                sourceIsCurrent = faceReviewSourceCurrent,
                vm = vm,
                onPreviewSourceTime = onPreviewSourceTime,
            )
            Text("Review each suggestion before applying it. Nothing changes automatically.",
                modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall,
                color = ApexPalette.TextSecondary)
            Text("Face framing", modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.titleMedium, color = ApexPalette.TextPrimary)
            Text("Review suggested crops from sample frames. Brief faces or movement may be missed; no crop is applied automatically.",
                modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall,
                color = ApexPalette.TextSecondary)

            val selectedFaceCropClip = editor.project?.clips?.firstOrNull {
                it.id == editor.selectedClipId && it.faceCropTrack != null
            }
            if (selectedFaceCropClip != null) {
                OutlinedButton(onClick = { vm.clearFaceCropTrack(selectedFaceCropClip.id) },
                    modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text("Clear existing face framing")
                }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FaceReframeReviewTarget.entries) { target ->
                    FilterChip(selected = faceReview.reframeTarget == target,
                        enabled = !faceReview.isRunning,
                        onClick = { vm.setFaceReframeReviewTarget(target) },
                        label = { Text(target.label) })
                }
            }
            OutlinedButton(onClick = { vm.startLocalFaceTrackReview() },
                enabled = faceReviewTargetClip?.type == com.apexstudio.app.domain.model.ClipType.VIDEO &&
                    !faceReview.isRunning && !analysis.isRunning,
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().testTag("analyze_face_tracks")) {
                Text(if (faceReview.isReady) "Find face suggestions again" else "Find face suggestions")
            }
            Text("Smart edits", modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.titleMedium, color = ApexPalette.TextPrimary)
            if (analysis.isRunning) {
                Text(analysis.message, modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall, color = ApexPalette.TextSecondary)
                TextButton(onClick = { vm.cancelLocalAnalysis() }, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancel")
                }
            }
            Text(analysis.error ?: analysis.message, modifier = Modifier.padding(horizontal = 16.dp).testTag("smart_edit_status"),
                style = MaterialTheme.typography.bodySmall,
                color = if (analysis.error != null) MaterialTheme.colorScheme.error else ApexPalette.TextSecondary)
            if (analysis.isReady && !analysisSourceCurrent) {
                AnalysisNote("These suggestions are out of date. Find suggestions again for the current video.")
            }
            if (analysis.isReady && analysisSourceCurrent) {
                Text("${analysis.sampledFrameCount} video frames sampled. Brief scene changes may be missed.",
                    modifier = Modifier.padding(horizontal = 16.dp).testTag("smart_edit_results_summary"),
                    color = ApexPalette.TextSecondary, style = MaterialTheme.typography.labelSmall)
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("Scenes", "Trim windows", "Crop")) { name ->
                        FilterChip(selected = tab == name, onClick = { tab = name }, label = { Text(name) })
                    }
                }
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (tab) {
                        "Scenes" -> {
                            if (analysis.sceneCuts.isEmpty()) item { AnalysisNote("No scene changes found. Your video has not been changed.") }
                            items(analysis.sceneCuts, key = { it.id }) { cut ->
                                AnalysisCard("Possible scene cut · ${analysisTime(cut.timestampMs)}",
                                    "Suggested timing may need a quick check.") {
                                    TextButton(onClick = { previewAnalysisSource(cut.timestampMs) }) { Text("Preview") }
                                    FilterChip(selected = cut.id in analysis.reviewedSuggestionIds,
                                        onClick = { vm.setLocalSuggestionReviewed(cut.id, cut.id !in analysis.reviewedSuggestionIds) },
                                        label = { Text(if (cut.id in analysis.reviewedSuggestionIds) "Reviewed" else "Mark reviewed") })
                                }
                            }
                        }
                        "Trim windows" -> {
                            if (analysis.smartTrims.isEmpty()) item { AnalysisNote("No highlight window was found. No trim has been applied.") }
                            items(analysis.smartTrims, key = { it.id }) { trim ->
                                AnalysisCard("${analysisTime(trim.startMs)} – ${analysisTime(trim.endMs)}", trim.rationale) {
                                    Row {
                                        TextButton(onClick = { previewAnalysisSource(trim.startMs) }) { Text("Preview start") }
                                        TextButton(onClick = { previewAnalysisSource((trim.endMs - 1L).coerceAtLeast(trim.startMs)) }) { Text("Preview end") }
                                    }
                                    FilterChip(selected = trim.id in analysis.reviewedSuggestionIds,
                                        onClick = { vm.setLocalSuggestionReviewed(trim.id, trim.id !in analysis.reviewedSuggestionIds) },
                                        label = { Text(if (trim.id in analysis.reviewedSuggestionIds) "Reviewed" else "Mark reviewed") })
                                    Button(onClick = { vm.applyLocalSuggestedTrim(trim.id) },
                                        enabled = trim.id in analysis.reviewedSuggestionIds) { Text("Keep this window") }
                                }
                            }
                        }
                        else -> item {
                            val crop = analysis.autoCrop
                            if (crop == null) AnalysisNote("No crop suggestion for this video.")
                            else AnalysisCard("Crop suggestion · 9:16", "A framing suggestion; check the preview before applying.") {
                                TextButton(onClick = { previewAnalysisSource(analysis.sourceStartMs ?: 0L) }) { Text("Preview") }
                                FilterChip(selected = "local-auto-crop" in analysis.reviewedSuggestionIds,
                                    onClick = { vm.setLocalSuggestionReviewed("local-auto-crop", "local-auto-crop" !in analysis.reviewedSuggestionIds) },
                                    label = { Text(if ("local-auto-crop" in analysis.reviewedSuggestionIds) "Reviewed" else "Mark reviewed") })
                                Button(onClick = { vm.applyLocalAutoCrop() },
                                    enabled = "local-auto-crop" in analysis.reviewedSuggestionIds) { Text("Apply crop") }
                            }
                        }
                    }
                }
                if (tab == "Scenes" && analysis.sceneCuts.isNotEmpty()) {
                    Button(onClick = { vm.applyLocalSceneCuts() },
                        enabled = analysis.sceneCuts.any { it.id in analysis.reviewedSuggestionIds },
                        modifier = Modifier.padding(horizontal = 16.dp)) { Text("Split reviewed scenes") }
                }
                Text("Undo restores an applied edit. Smart trim keeps one selected window; it does not remove every quiet section.",
                    modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall,
                    color = ApexPalette.TextSecondary)
            }
        }
    }
}

@Composable
private fun FaceReviewResultsBlock(
    faceReview: FaceTrackReviewUiState,
    sourceIsCurrent: Boolean,
    vm: EditorViewModel,
    onPreviewSourceTime: (String, Long) -> Unit,
) {
    if (faceReview.error != null) {
        Text(faceReview.error, modifier = Modifier.padding(horizontal = 16.dp).testTag("face_review_error"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    if (faceReview.isReady && !sourceIsCurrent) {
        AnalysisNote("The video changed. Find face suggestions again before reviewing these results.")
    }
    if (faceReview.isReady && sourceIsCurrent) {
        val result = faceReview.result
        if (result != null) {
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("face_review_results")) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Face suggestions ready", style = MaterialTheme.typography.titleMedium)
                    Text("${result.samples.size} sample frames. Brief faces or movement may be missed.",
                        modifier = Modifier.testTag("face_review_results_summary"),
                        color = ApexPalette.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text("Review only. Applying automatic face framing to video is not available yet.",
                        style = MaterialTheme.typography.bodySmall, color = ApexPalette.TextSecondary)
                    Text("Reviewed ${faceReview.reviewedSampleTimestamps.size} of ${result.samples.size} frames.",
                        style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (result.samples.isEmpty()) item { AnalysisNote("No video frames could be read for review.") }
                        items(result.samples, key = { it.timestampMs }) { sample ->
                            val crop = sample.reframe.crop
                            AnalysisCard("Frame at ${analysisTime(sample.timestampMs)}",
                                if (sample.faces.isEmpty()) "No face found in this frame. The centered crop is only a fallback suggestion."
                                else "${sample.faces.size} face(s) found. Check this crop before using the suggestion.") {
                                Text("Suggested crop · L ${samplePercent(crop.left)} · T ${samplePercent(crop.top)} · R ${samplePercent(crop.right)} · B ${samplePercent(crop.bottom)}",
                                    style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = {
                                    if (vm.canPreviewFaceTrackSample(sample.timestampMs)) {
                                        faceReview.clipId?.let { onPreviewSourceTime(it, sample.timestampMs) }
                                    }
                                }) { Text("Preview frame") }
                                FilterChip(
                                    selected = sample.timestampMs in faceReview.reviewedSampleTimestamps,
                                    onClick = { vm.setFaceTrackSampleReviewed(sample.timestampMs,
                                        sample.timestampMs !in faceReview.reviewedSampleTimestamps) },
                                    label = { Text(if (sample.timestampMs in faceReview.reviewedSampleTimestamps) "Reviewed" else "Mark reviewed") }
                                )
                            }
                        }
                    }
                }
            }
        }
    } else if (!faceReview.isRunning && faceReview.message.isNotBlank() && faceReview.status != FaceTrackReviewStatus.IDLE) {
        Text(faceReview.message, modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall, color = ApexPalette.TextSecondary)
    }
}

@Composable
private fun AnalysisNote(message: String) {
    Text(message, modifier = Modifier.width(280.dp).padding(16.dp), color = ApexPalette.TextSecondary)
}

@Composable
private fun AnalysisCard(title: String, description: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.width(300.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, maxLines = 4)
            content()
        }
    }
}

internal fun analysisTime(ms: Long): String = String.format(Locale.ROOT, "%.2f s", ms / 1000.0)

private fun samplePercent(value: Float): String = String.format(Locale.ROOT, "%.0f%%", value * 100f)

/** Results are only reviewable while every captured source property still matches. */
internal fun isLocalReviewSourceCurrent(snapshot: FaceTrackSourceSnapshot?, project: Project?): Boolean =
    snapshot != null && project != null && isFaceTrackSnapshotCurrent(snapshot, project, project.id)
