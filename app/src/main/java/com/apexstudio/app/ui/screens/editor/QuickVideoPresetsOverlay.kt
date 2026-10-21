package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.apexstudio.app.data.template.ProjectVideoPresets
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.presentation.viewmodel.*

internal enum class QuickVideoPresetSection(val title: String) {
    ENHANCE("Enhance · color + ESPCN"), TEMPLATES("Video templates"), FILTERS("Recommended filters")
}

/** Compact select-only recipes use the shipped deterministic preview/export renderer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickVideoPresetsOverlay(vm: EditorViewModel, section: QuickVideoPresetSection, onClose: () -> Unit) {
    val editor by vm.state.collectAsState()
    val upscale by vm.videoUpscaleUi.collectAsState()
    val target = editor.project?.let { project ->
        project.clips.firstOrNull { it.id == editor.selectedClipId }?.let { project.id to it }
    }
    var error by remember(editor.project?.id, editor.selectedClipId, section) { mutableStateOf<String?>(null) }
    val capturedClip = target?.second
    val eligible = capturedClip != null && capturedClip.type == ClipType.VIDEO && capturedClip.trackIndex == 0 &&
        capturedClip.uri.isNotBlank() && editor.project?.id == target?.first && editor.selectedClipId == capturedClip.id &&
        editor.project?.clips?.firstOrNull { it.id == capturedClip.id } == capturedClip &&
        capturedClip.trimStartMs >= 0L && capturedClip.trimEndMs > capturedClip.trimStartMs &&
        capturedClip.trimEndMs <= capturedClip.durationMs
    val durationBlockReason = capturedClip?.let { clip ->
        com.apexstudio.app.data.video.VideoUpscaleEngine.sourceDurationBlockReason(clip.durationMs)
    }
    val closePanel = {
        if (section == QuickVideoPresetSection.ENHANCE && upscale.processing) vm.cancelExistingVideoUpscale()
        onClose()
    }
    val choices = when (section) {
        QuickVideoPresetSection.ENHANCE -> ProjectVideoPresets.videoEnhancements.map { it.id to it.label }
        QuickVideoPresetSection.TEMPLATES -> ProjectVideoPresets.videoTemplates.map { it.id to it.label }
        QuickVideoPresetSection.FILTERS -> ProjectVideoPresets.effects.map { it.id to it.label }
    }
    ModalBottomSheet(onDismissRequest = closePanel, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(section.title, style = MaterialTheme.typography.titleMedium)
            Text(when (section) {
                QuickVideoPresetSection.ENHANCE -> "Color choices are deterministic GPU adjustments. For real learned detail upscaling, run the bundled on-device ESPCN video processor below."
                QuickVideoPresetSection.TEMPLATES -> "Original built-in color and title recipes. Tap to apply one undoable edit."
                QuickVideoPresetSection.FILTERS -> "RGB color looks use the same shader in preview and export. HD labels describe color, not increased resolution."
            }, style = MaterialTheme.typography.bodySmall)
            if (!eligible) Text("Select an existing main-track project video first.")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                items(choices) { (id, label) ->
                    OutlinedButton(enabled = eligible, onClick = {
                        val applied = target?.let { (projectId, clip) ->
                            if (section == QuickVideoPresetSection.TEMPLATES) vm.applyProjectVideoTemplate(projectId, clip, id)
                            else vm.applyProjectVideoEffect(projectId, clip, id)
                        } == true
                        if (applied) onClose() else error = "The project changed. Re-select the video and try again."
                    }, modifier = Modifier.widthIn(max = 230.dp)) { Text(label) }
                }
            }
            if (section == QuickVideoPresetSection.ENHANCE) {
                Text(
                    "Offline ESPCN x3 · licensed ONNX Model Zoo model. Processes the whole source video on this device; no cloud or prompt. Source limits: even dimensions up to 640×360 (360×640 portrait), 30 seconds, 30 fps, SDR, and silent video or one AAC track. Output is up to 1920×1080 (portrait equivalent). Codec, storage, or model checks may reject a source.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "After success, preview and export use the same processed video file; the original is not overwritten, and one undo restores its URI. The selected timeline trim remains in place.",
                    style = MaterialTheme.typography.bodySmall,
                )
                durationBlockReason?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (upscale.processing) {
                    LinearProgressIndicator(progress = { upscale.progress01 }, modifier = Modifier.fillMaxWidth())
                }
                if (upscale.message.isNotBlank()) Text(upscale.message, color = if (upscale.processing) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
                Button(
                    onClick = { target?.let { vm.applyExistingVideoUpscale(it.first, it.second) } },
                    enabled = eligible && durationBlockReason == null && !upscale.processing,
                    modifier = Modifier.fillMaxWidth().testTag("quick_enhance_espcn"),
                ) { Text("Upscale selected video 3× with ESPCN") }
                if (upscale.processing) {
                    OutlinedButton(onClick = vm::cancelExistingVideoUpscale,
                        modifier = Modifier.fillMaxWidth().testTag("quick_enhance_espcn_cancel")) {
                        Text("Cancel processing")
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = closePanel) { Text("Close") }
        }
    }
}
