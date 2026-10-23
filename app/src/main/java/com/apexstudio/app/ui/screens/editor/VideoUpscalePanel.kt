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
            Text("AI Video Upscale · ESPCN x3", style = MaterialTheme.typography.titleLarge)
            if (!eligible && !progress.processing) Text("Choose an unchanged main-track video to process. Selecting it does not start processing.")
            if (!eligible && !progress.processing && onChooseMainVideo != null) {
                TextButton(onClick = onChooseMainVideo, modifier = Modifier.testTag("choose_main_video_upscale")) {
                    Text("Use main preview video: ${mainVideoName.orEmpty()}")
                }
            }
            durationBlockReason?.let { Text(it, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("video_upscale_duration_blocker")) }
            Text("Learned, on-device luma upscaling of an existing project video. Not generative AI, not 4K restoration. No prompt or image input.")
            Text("Source: up to 640×360 (or 360×640), even dimensions, at most 30 seconds and 30 fps. Output: 3× each edge, up to 1920×1080 (or portrait equivalent). SDR video, silent or one AAC audio track only. Audio past the processed video end is trimmed; because AAC packets are indivisible, the final audio may stop up to one packet early. Codec, storage and model integrity are checked before processing.",
                style = MaterialTheme.typography.bodySmall)
            Text("Processing the whole source may be slow. Source audio stays synchronized and the selected project trim is preserved. After success preview and export read the same processed video. Undo restores the original clip.",
                style = MaterialTheme.typography.bodySmall)
            if (progress.processing) LinearProgressIndicator(progress = { progress.progress01 }, modifier = Modifier.fillMaxWidth())
            if (progress.message.isNotBlank()) Text(progress.message, modifier = Modifier.testTag("video_upscale_status"))
            Button(onClick = onApply, enabled = eligible && durationBlockReason == null && !progress.processing,
                modifier = Modifier.fillMaxWidth().testTag("apply_project_video_upscale")) { Text("Upscale selected video 3×") }
            if (progress.processing) OutlinedButton(onClick = onCancel,
                modifier = Modifier.fillMaxWidth().testTag("cancel_project_video_upscale")) { Text("Cancel processing") }
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Close") }
        }
    }
}
