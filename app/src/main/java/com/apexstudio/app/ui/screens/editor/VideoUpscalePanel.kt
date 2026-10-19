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
import com.apexstudio.app.ui.theme.ApexPalette

@Composable
fun VideoUpscaleOverlay(vm: EditorViewModel, onClose: () -> Unit) {
    val editor by vm.state.collectAsState()
    val progress by vm.videoUpscaleUi.collectAsState()
    // Immutable target for this panel lifetime. Changes require reopening, not silent retargeting.
    val target = remember {
        val project = vm.state.value.project
        project?.clips?.firstOrNull { it.id == vm.state.value.selectedClipId }
            ?.let { project.id to it }
    }
    val clip = target?.second
    val eligible = clip != null && clip.type == ClipType.VIDEO && clip.trackIndex == 0 &&
        editor.project?.id == target.first && editor.selectedClipId == clip.id &&
        editor.project?.clips?.firstOrNull { it.id == clip.id } == clip
    VideoUpscalePanel(eligible, progress,
        onApply = { target?.let { vm.applyExistingVideoUpscale(it.first, it.second) } },
        onCancel = vm::cancelExistingVideoUpscale,
        onClose = { if (progress.processing) vm.cancelExistingVideoUpscale(); onClose() })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoUpscalePanel(
    eligible: Boolean,
    progress: VideoUpscaleUiState,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = ApexPalette.BgSurface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("AI Video Upscale · ESPCN x3", style = MaterialTheme.typography.titleLarge)
            Text("Learned, on-device luma upscaling of an existing project video. Not generative AI, not 4K restoration. No prompt or image input.")
            Text("Source: up to 640×360 (or 360×640), even dimensions, at most 30 seconds and 30 fps. Output: 3× each edge, up to 1920×1080 (or portrait equivalent). SDR video, silent or one AAC audio track only; audio must not outlast the video. Codec, storage and model integrity are checked before processing.",
                style = MaterialTheme.typography.bodySmall)
            Text("Processing the whole source may be slow. Original audio and trim are preserved. After success preview and export read the same processed video. Undo restores the original clip.",
                style = MaterialTheme.typography.bodySmall)
            if (!eligible && !progress.processing) Text("Select an unchanged main-track video in this project, then reopen this panel.")
            if (progress.processing) LinearProgressIndicator(progress = { progress.progress01 }, modifier = Modifier.fillMaxWidth())
            if (progress.message.isNotBlank()) Text(progress.message, modifier = Modifier.testTag("video_upscale_status"))
            Button(onClick = onApply, enabled = eligible && !progress.processing,
                modifier = Modifier.fillMaxWidth().testTag("apply_project_video_upscale")) { Text("Upscale selected video 3×") }
            if (progress.processing) OutlinedButton(onClick = onCancel,
                modifier = Modifier.fillMaxWidth().testTag("cancel_project_video_upscale")) { Text("Cancel processing") }
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Close") }
        }
    }
}
