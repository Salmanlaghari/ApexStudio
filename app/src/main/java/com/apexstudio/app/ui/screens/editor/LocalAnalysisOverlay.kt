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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexstudio.app.ai.safety.GlobalModerationEnforcement
import com.apexstudio.app.ai.safety.ProtectedAiAction
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.viewmodel.*
import com.apexstudio.app.ui.theme.ApexPalette
import java.util.Locale

/** Ordinary signal-analysis suggestions are deliberately separate from protected model-based AI. */
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
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Offline analysis & face-track review", style = MaterialTheme.typography.titleLarge,
                    color = ApexPalette.TextPrimary, modifier = Modifier.weight(1f))
                TextButton(onClick = close) { Text("Close") }
            }
            Text("Samples real video frames on this phone. Histogram/activity/gradient rules—not trained AI, face detection or content moderation. Nothing changes until you review and apply.",
                modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall,
                color = ApexPalette.TextSecondary)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(analyzedClip?.name ?: "Select a video in the timeline", modifier = Modifier.weight(1f),
                    maxLines = 1, color = ApexPalette.TextPrimary)
                Button(onClick = { vm.startLocalAnalysis() }, enabled = !analysis.isRunning && !faceReview.isRunning) {
                    Text(if (analysis.isReady) "Analyze again" else "Analyze")
                }
            }
            Text("Offline face tracking review", modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.titleMedium, color = ApexPalette.TextPrimary)
            Text("Face-model review is not enabled in this release's selected video-tool scope. Deterministic scene analysis above remains available; Google Play policy is not presented as requiring a blanket all-frame classifier.",
                modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall,
                color = ApexPalette.TextSecondary)
            val selectedFaceCropClip = editor.project?.clips?.firstOrNull { it.id == editor.selectedClipId && it.faceCropTrack != null }
            if (selectedFaceCropClip != null) {
                OutlinedButton(onClick = { vm.clearFaceCropTrack(selectedFaceCropClip.id) },
                    modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text("Clear existing face reframe")
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
                enabled = GlobalModerationEnforcement.readiness.approved && !faceReview.isRunning && !analysis.isRunning,
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Text(if (!GlobalModerationEnforcement.readiness.approved) "Face review unavailable · outside enabled tool scope"
                    else if (faceReview.isReady) "Analyze face tracks again" else "Analyze face tracks offline")
            }
            if (analysis.isRunning) {
                LinearProgressIndicator(progress = { analysis.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                TextButton(onClick = { vm.cancelLocalAnalysis() }, modifier = Modifier.align(Alignment.End)) { Text("Cancel analysis") }
            }
            if (faceReview.isRunning) {
                LinearProgressIndicator(progress = { faceReview.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                TextButton(onClick = { vm.cancelLocalFaceTrackReview() }, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancel face tracking analysis")
                }
            }
            Text(analysis.error ?: analysis.message, modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (analysis.error != null) MaterialTheme.colorScheme.error else ApexPalette.TextSecondary)
            if (analysis.isReady && !analysisSourceCurrent) {
                AnalysisNote("These suggestions are stale because the analyzed clip or its edit settings changed or were removed. Analyze the current source again before reviewing or applying anything.")
            }
            if (analysis.isReady && analysisSourceCurrent) {
                Text("${analysis.sampledFrameCount} decoded samples · source-time suggestions · sampling can miss short cuts",
                    modifier = Modifier.padding(horizontal = 16.dp), color = ApexPalette.TextSecondary,
                    style = MaterialTheme.typography.labelSmall)
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("Scenes", "Trim windows", "Crop")) { name ->
                        FilterChip(selected = tab == name, onClick = { tab = name }, label = { Text(name) })
                    }
                }
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (tab) {
                        "Scenes" -> {
                            if (analysis.sceneCuts.isEmpty()) item { AnalysisNote("No candidate scene cuts found. Your video has not been changed.") }
                            items(analysis.sceneCuts, key = { it.id }) { cut ->
                                AnalysisCard("Scene at ${analysisTime(cut.timestampMs)}", "A sampled frame-change candidate, not a frame-exact guaranteed cut.") {
                                    TextButton(onClick = { previewAnalysisSource(cut.timestampMs) }) { Text("Preview in video") }
                                    FilterChip(selected = cut.id in analysis.reviewedSuggestionIds,
                                        onClick = { vm.setLocalSuggestionReviewed(cut.id, cut.id !in analysis.reviewedSuggestionIds) },
                                        label = { Text(if (cut.id in analysis.reviewedSuggestionIds) "Reviewed / selected" else "Select reviewed cut") })
                                }
                            }
                        }
                        "Trim windows" -> {
                            if (analysis.smartTrims.isEmpty()) item { AnalysisNote("No usable highlight window was found. No trim has been applied.") }
                            items(analysis.smartTrims, key = { it.id }) { trim ->
                                AnalysisCard("${analysisTime(trim.startMs)} – ${analysisTime(trim.endMs)}", trim.rationale) {
                                    Row {
                                        TextButton(onClick = { previewAnalysisSource(trim.startMs) }) { Text("Preview start") }
                                        TextButton(onClick = { previewAnalysisSource((trim.endMs - 1L).coerceAtLeast(trim.startMs)) }) { Text("End") }
                                    }
                                    FilterChip(selected = trim.id in analysis.reviewedSuggestionIds,
                                        onClick = { vm.setLocalSuggestionReviewed(trim.id, trim.id !in analysis.reviewedSuggestionIds) }, label = { Text("I reviewed this window") })
                                    Button(onClick = { vm.applyLocalSuggestedTrim(trim.id) },
                                        enabled = trim.id in analysis.reviewedSuggestionIds) { Text("Keep this window") }
                                }
                            }
                        }
                        else -> item {
                            val crop = analysis.autoCrop
                            if (crop == null) AnalysisNote("No crop suggestion available for this video.")
                            else AnalysisCard("Signal crop · 9:16", "Luma-gradient suggestion, not subject detection. Apply is undoable; check the main video preview after applying.") {
                                Text(crop.method, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                                TextButton(onClick = { previewAnalysisSource(analysis.sourceStartMs ?: 0L) }) { Text("Preview source") }
                                FilterChip(selected = "local-auto-crop" in analysis.reviewedSuggestionIds,
                                    onClick = { vm.setLocalSuggestionReviewed("local-auto-crop", "local-auto-crop" !in analysis.reviewedSuggestionIds) }, label = { Text("I reviewed the source") })
                                Button(onClick = { vm.applyLocalAutoCrop() },
                                    enabled = "local-auto-crop" in analysis.reviewedSuggestionIds) { Text("Apply crop suggestion") }
                            }
                        }
                    }
                }
                if (tab == "Scenes" && analysis.sceneCuts.isNotEmpty()) {
                    Button(onClick = { vm.applyLocalSceneCuts() },
                        enabled = analysis.sceneCuts.any { it.id in analysis.reviewedSuggestionIds },
                        modifier = Modifier.padding(horizontal = 16.dp)) { Text("Split at selected reviewed cuts") }
                }
                Text("Use the editor's Undo to restore an applied edit. Smart trim keeps one selected window; it does not automatically remove every boring section.",
                    modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall,
                    color = ApexPalette.TextSecondary)
            }
            Text(faceReview.error ?: faceReview.message, modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (faceReview.error != null) MaterialTheme.colorScheme.error else ApexPalette.TextSecondary)
            if (faceReview.isReady && !faceReviewSourceCurrent) {
                AnalysisNote("These face-track samples are stale because the analyzed clip or its edit settings changed or were removed. Analyze the current source again before reviewing or applying anything.")
            }
            if (faceReview.isReady && faceReviewSourceCurrent) {
                val result = faceReview.result
                if (result != null) {
                    Text("${result.sampledFrameCount} decoded samples · sparse sampling can miss brief faces or motion. Review the actual sampled frames below.",
                        modifier = Modifier.padding(horizontal = 16.dp), color = ApexPalette.TextSecondary,
                        style = MaterialTheme.typography.labelSmall)
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (result.samples.isEmpty()) item { AnalysisNote("No video frames were decoded for review.") }
                        items(result.samples, key = { it.timestampMs }) { sample ->
                            val trackIds = sample.faces.map { it.id }.distinct().sorted()
                            val crop = sample.reframe.crop
                            AnalysisCard("Sample at ${analysisTime(sample.timestampMs)}",
                                if (sample.faces.isEmpty()) "No face detected in this sampled frame. The centered crop is a fallback candidate, not a detection."
                                else "Detected ${sample.faces.size} face(s); local track IDs: ${trackIds.joinToString()}. These IDs are ephemeral and may change.") {
                                Text("${faceReview.reframeTarget.label} candidate (not applied): L ${samplePercent(crop.left)} · T ${samplePercent(crop.top)} · R ${samplePercent(crop.right)} · B ${samplePercent(crop.bottom)}",
                                    style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = {
                                    if (vm.canPreviewFaceTrackSample(sample.timestampMs)) {
                                        faceReview.clipId?.let { onPreviewSourceTime(it, sample.timestampMs) }
                                    }
                                }) {
                                    Text("Preview sampled time")
                                }
                                FilterChip(
                                    selected = sample.timestampMs in faceReview.reviewedSampleTimestamps,
                                    onClick = { vm.setFaceTrackSampleReviewed(sample.timestampMs,
                                        sample.timestampMs !in faceReview.reviewedSampleTimestamps) },
                                    label = { Text(if (sample.timestampMs in faceReview.reviewedSampleTimestamps) "Reviewed" else "Mark reviewed") }
                                )
                            }
                        }
                    }
                    val allReviewed = result.samples.isNotEmpty() && result.samples.all {
                        it.timestampMs in faceReview.reviewedSampleTimestamps
                    }
                    Button(onClick = { vm.applyReviewedFaceCropTrack() },
                        enabled = allReviewed && GlobalModerationEnforcement.readiness.approved,
                        modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text("Apply reviewed reframe")
                    }
                    Text(GlobalModerationEnforcement.statusMessage(ProtectedAiAction.FACE_AWARE_REFRAME),
                        modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall,
                        color = ApexPalette.TextSecondary)
                }
            }
        }
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

/** Results are only reviewable while every source property captured at analysis time still matches. */
internal fun isLocalReviewSourceCurrent(snapshot: FaceTrackSourceSnapshot?, project: Project?): Boolean =
    snapshot != null && project != null && isFaceTrackSnapshotCurrent(snapshot, project, project.id)
