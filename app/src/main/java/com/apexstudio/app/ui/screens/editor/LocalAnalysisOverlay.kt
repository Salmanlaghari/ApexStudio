package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexstudio.app.presentation.viewmodel.*
import com.apexstudio.app.ui.theme.ApexPalette
import java.util.Locale

/** Ordinary signal-analysis suggestions are deliberately separate from protected model-based AI. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalAnalysisOverlay(
    vm: EditorViewModel,
    onPreviewSourceTime: (Long) -> Unit,
    onClose: () -> Unit
) {
    val analysis by vm.localAnalysis.collectAsStateWithLifecycle()
    val editor by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf("Scenes") }
    val analyzedClip = editor.project?.clips?.firstOrNull { it.id == analysis.clipId }
    val close = { vm.cancelLocalAnalysis(); onClose() }
    ModalBottomSheet(onDismissRequest = close, containerColor = ApexPalette.BgSurface) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Local analysis · non-AI", style = MaterialTheme.typography.titleLarge,
                    color = ApexPalette.TextPrimary, modifier = Modifier.weight(1f))
                TextButton(onClick = close) { Text("Close") }
            }
            Text("Samples real video frames on this phone. Histogram/activity/gradient rules—not trained AI, face detection or content moderation. Nothing changes until you review and apply.",
                modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall,
                color = ApexPalette.TextSecondary)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(analyzedClip?.name ?: "Select a video in the timeline", modifier = Modifier.weight(1f),
                    maxLines = 1, color = ApexPalette.TextPrimary)
                Button(onClick = { vm.startLocalAnalysis() }, enabled = !analysis.isRunning) {
                    Text(if (analysis.isReady) "Analyze again" else "Analyze")
                }
            }
            if (analysis.isRunning) {
                LinearProgressIndicator(progress = { analysis.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                TextButton(onClick = { vm.cancelLocalAnalysis() }, modifier = Modifier.align(Alignment.End)) { Text("Cancel analysis") }
            }
            Text(analysis.error ?: analysis.message, modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (analysis.error != null) MaterialTheme.colorScheme.error else ApexPalette.TextSecondary)
            if (analysis.isReady) {
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
                                    TextButton(onClick = { onPreviewSourceTime(cut.timestampMs) }) { Text("Preview in video") }
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
                                        TextButton(onClick = { onPreviewSourceTime(trim.startMs) }) { Text("Preview start") }
                                        TextButton(onClick = { onPreviewSourceTime((trim.endMs - 1L).coerceAtLeast(trim.startMs)) }) { Text("End") }
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
                                TextButton(onClick = { onPreviewSourceTime(analysis.sourceStartMs ?: 0L) }) { Text("Preview source") }
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
