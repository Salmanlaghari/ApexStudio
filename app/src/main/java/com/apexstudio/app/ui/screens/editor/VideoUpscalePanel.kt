package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.VideoUpscaleUiState
import com.apexstudio.app.presentation.viewmodel.applyExistingVideoUpscale
import com.apexstudio.app.presentation.viewmodel.cancelExistingVideoUpscale
import com.apexstudio.app.presentation.viewmodel.selectClip
import com.apexstudio.app.ui.theme.EditorPalette as ApexPalette

@Composable
fun VideoUpscaleOverlay(vm: EditorViewModel, onClose: () -> Unit) {
    val editor by vm.state.collectAsState()
    val progress by vm.videoUpscaleUi.collectAsState()
    // Captured target changes only through the explicit source-choice action below.
    var target by remember {
        val project = vm.state.value.project
        mutableStateOf(project?.clips?.firstOrNull { it.id == vm.state.value.selectedClipId }
            ?.let { project.id to it })
    }
    val clip = target?.second
    val mainVideo = timelinePreviewClip(editor.project?.clips.orEmpty(), editor.playerPositionMs)
        ?.takeIf { it.type == ClipType.VIDEO && it.trackIndex == 0 }
    val eligible = clip != null && clip.type == ClipType.VIDEO && clip.trackIndex == 0 &&
        editor.project?.id == target?.first && editor.selectedClipId == clip.id &&
        editor.project?.clips?.firstOrNull { it.id == clip.id } == clip
    VideoUpscalePanel(eligible, progress,
        onApply = { target?.let { vm.applyExistingVideoUpscale(it.first, it.second) } },
        onCancel = vm::cancelExistingVideoUpscale,
        onClose = { if (progress.processing) vm.cancelExistingVideoUpscale(); onClose() },
        sourceDurationMs = clip?.durationMs,
        onChooseMainVideo = mainVideo?.let { source -> {
            editor.project?.let { project ->
                vm.selectClip(source.id)
                target = project.id to source
            }
        } },
        mainVideoName = mainVideo?.name)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoUpscalePanel(
    eligible: Boolean,
    progress: VideoUpscaleUiState,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
    sourceDurationMs: Long? = null,
    onChooseMainVideo: (() -> Unit)? = null,
    mainVideoName: String? = null,
) {
    val durationBlockReason = sourceDurationMs?.let(com.apexstudio.app.data.video.VideoUpscaleEngine::sourceDurationBlockReason)
    ModalBottomSheet(onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = ApexPalette.BgSurface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Video enhancement", style = MaterialTheme.typography.titleLarge)
            if (!eligible && !progress.processing) Text("Choose a video on the main track. Processing starts only when you tap Enhance.")
            if (!eligible && !progress.processing && onChooseMainVideo != null) {
                TextButton(onClick = onChooseMainVideo, modifier = Modifier.testTag("choose_main_video_upscale")) {
                    Text("Use preview video: ${mainVideoName.orEmpty()}")
                }
            }
            durationBlockReason?.let { Text(it, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("video_upscale_duration_blocker")) }
            if (progress.outputUri != null) {
                Card(modifier = Modifier.fillMaxWidth().testTag("video_upscale_result")) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Enhanced video ready", style = MaterialTheme.typography.titleMedium)
                        Text("Preview the new version. Undo can restore the original.",
                            style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = onClose, modifier = Modifier.fillMaxWidth().testTag("review_project_video_upscale")) {
                            Text("Review video")
                        }
                    }
                }
            }
            Text("Create a sharper 3× version of this video. Preview and export will use it after processing; the original stays available with Undo.")
            Text("Works with videos up to 640×360 (portrait up to 360×640), 30 seconds and 30 fps. Output is up to 1080p (portrait equivalent). Audio must be silent or AAC.",
                style = MaterialTheme.typography.bodySmall)
            Text("The whole source is enhanced, so shortening its timeline trim does not bypass the 30-second limit. Processing can take a little while.",
                style = MaterialTheme.typography.bodySmall)
            if (progress.processing) {
                Text(progress.message.ifBlank { "Enhancing video…" }, modifier = Modifier.testTag("video_upscale_status"))
            } else if (progress.outputUri == null && progress.message.isNotBlank()) {
                Text(progress.message, modifier = Modifier.testTag("video_upscale_status"))
            }
            Button(onClick = onApply, enabled = eligible && durationBlockReason == null && !progress.processing && progress.outputUri == null,
                modifier = Modifier.fillMaxWidth().testTag("apply_project_video_upscale")) { Text("Enhance video") }
            if (progress.processing) OutlinedButton(onClick = onCancel,
                modifier = Modifier.fillMaxWidth().testTag("cancel_project_video_upscale")) { Text("Cancel processing") }
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Close") }
        }
    }
}
