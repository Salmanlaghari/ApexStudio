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
    ENHANCE("Video enhancement"), TEMPLATES("Video templates"), FILTERS("Recommended filters")
}

/** Compact select-only recipes use the shipped deterministic preview/export renderer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickVideoPresetsOverlay(vm: EditorViewModel, section: QuickVideoPresetSection, onClose: () -> Unit) {
    val editor by vm.state.collectAsState()
    val upscale by vm.videoUpscaleUi.collectAsState()
    val mainPreviewClip = editor.project?.let { project ->
        timelinePreviewClip(project.clips, editor.playerPositionMs)
    }
    val target = editor.project?.let { project ->
        val clip = if (section == QuickVideoPresetSection.TEMPLATES) {
            // Match the source actually feeding the main preview at this playhead. Selection
            // can remain on PiP (or another clip), which is not the template's source.
            timelinePreviewClip(project.clips, editor.playerPositionMs)
        } else {
            project.clips.firstOrNull { it.id == editor.selectedClipId }
        }
        clip?.let { project.id to it }
    }
    var error by remember(editor.project?.id, editor.selectedClipId, section) { mutableStateOf<String?>(null) }
    val capturedClip = target?.second
    val eligible = capturedClip != null && capturedClip.type == ClipType.VIDEO && capturedClip.trackIndex == 0 &&
        capturedClip.uri.isNotBlank() && editor.project?.id == target?.first &&
        (section == QuickVideoPresetSection.TEMPLATES || editor.selectedClipId == capturedClip.id) &&
        editor.project?.clips?.firstOrNull { it.id == capturedClip.id } == capturedClip &&
        capturedClip.trimStartMs >= 0L && capturedClip.trimEndMs > capturedClip.trimStartMs &&
        capturedClip.trimEndMs <= capturedClip.durationMs
    val canSelectMainPreviewForEnhance = section == QuickVideoPresetSection.ENHANCE && !eligible &&
        mainPreviewClip != null && mainPreviewClip.type == ClipType.VIDEO && mainPreviewClip.trackIndex == 0 &&
        mainPreviewClip.uri.isNotBlank() && mainPreviewClip.trimStartMs >= 0L &&
        mainPreviewClip.trimEndMs > mainPreviewClip.trimStartMs && mainPreviewClip.trimEndMs <= mainPreviewClip.durationMs &&
        editor.project?.clips?.firstOrNull { it.id == mainPreviewClip.id } == mainPreviewClip
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
                QuickVideoPresetSection.ENHANCE -> "Create a sharper 3× version of a video in your project. Processing starts only when you tap Enhance."
                QuickVideoPresetSection.TEMPLATES -> "Each recipe applies only its named color look and title as one undoable edit; it does not build a montage or change timing."
                QuickVideoPresetSection.FILTERS -> "RGB color looks use the same shader in preview and export. HD labels describe color, not increased resolution."
            }, style = MaterialTheme.typography.bodySmall)
            if (section == QuickVideoPresetSection.ENHANCE && upscale.outputUri != null) {
                Card(modifier = Modifier.fillMaxWidth().testTag("quick_enhance_result")) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Enhanced video ready", style = MaterialTheme.typography.titleMedium)
                        Text("Preview the new version. Undo restores the original.", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = onClose, modifier = Modifier.fillMaxWidth().testTag("quick_enhance_review_result")) {
                            Text("Review video")
                        }
                    }
                }
            }
            if (section == QuickVideoPresetSection.TEMPLATES) {
                when {
                    capturedClip == null -> Text("No main-track media is available. Add a main-track video first.")
                    capturedClip.type != ClipType.VIDEO -> Text("The main preview is not a video. Move the playhead to a main-track video to use templates.")
                    !eligible -> Text("The main-preview video is not a usable project source. Check its source and trim, then try again.")
                    else -> Text("Applies to main preview video: ${capturedClip.name}. PiP selection is unchanged.")
                }
            } else if (!eligible) Text("Choose a video on the main track.")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                items(choices) { (id, label) ->
                    Column(Modifier.widthIn(max = 230.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(enabled = eligible, onClick = {
                            val applied = target?.let { (projectId, clip) ->
                                if (section == QuickVideoPresetSection.TEMPLATES) {
                                    vm.applyProjectVideoTemplate(projectId, clip, id, allowUnselectedMainTrackTarget = true)
                                } else vm.applyProjectVideoEffect(projectId, clip, id)
                            } == true
                            if (applied) onClose() else error = "The project changed. Re-select the video and try again."
                        }, modifier = Modifier.fillMaxWidth().then(
                            if (section == QuickVideoPresetSection.TEMPLATES) Modifier.testTag("quick_video_template_$id")
                            else Modifier
                        )) { Text(label) }
                        if (section == QuickVideoPresetSection.TEMPLATES) {
                            val recipe = ProjectVideoPresets.videoTemplate(id)
                            val effectLabel = recipe?.let { ProjectVideoPresets.effect(it.effectId)?.label }
                            val titleLabel = recipe?.let { ProjectVideoPresets.textTemplate(it.textTemplateId)?.label }
                            Text("Color: ${effectLabel ?: "Unavailable"} · Title: ${titleLabel ?: "Unavailable"}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (section == QuickVideoPresetSection.ENHANCE) {
                if (canSelectMainPreviewForEnhance) {
                    OutlinedButton(
                        onClick = { mainPreviewClip?.let { vm.selectClip(it.id) } },
                        modifier = Modifier.fillMaxWidth().testTag("quick_enhance_select_main_preview"),
                    ) { Text("Use preview video: ${mainPreviewClip?.name}") }
                    Text("This only selects the video. It will not start processing.",
                        style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Works with videos up to 640×360 (portrait up to 360×640), 30 seconds and 30 fps. Output is up to 1080p (portrait equivalent). Audio must be silent or AAC.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "The whole source is enhanced, so a timeline trim does not bypass the 30-second limit. Preview and export use the enhanced video; Undo restores the original.",
                    style = MaterialTheme.typography.bodySmall,
                )
                durationBlockReason?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (upscale.processing) Text(upscale.message.ifBlank { "Enhancing video…" },
                    modifier = Modifier.testTag("quick_enhance_status"))
                else if (upscale.outputUri == null && upscale.message.isNotBlank()) Text(upscale.message,
                    color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("quick_enhance_status"))
                Button(
                    onClick = { target?.let { vm.applyExistingVideoUpscale(it.first, it.second) } },
                    enabled = eligible && durationBlockReason == null && !upscale.processing && upscale.outputUri == null,
                    modifier = Modifier.fillMaxWidth().testTag("quick_enhance_espcn"),
                ) { Text("Enhance video") }
                if (upscale.processing) {
                    OutlinedButton(onClick = vm::cancelExistingVideoUpscale,
                        modifier = Modifier.fillMaxWidth().testTag("quick_enhance_espcn_cancel")) {
                        Text("Cancel")
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = closePanel) { Text("Close") }
        }
    }
}
