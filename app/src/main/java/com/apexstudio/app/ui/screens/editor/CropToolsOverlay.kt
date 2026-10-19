package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.apexstudio.app.data.effect.CropSourceDimensions
import com.apexstudio.app.data.effect.VideoCropPreset
import com.apexstudio.app.data.effect.readCropSourceDimensions
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.manualCropSettings
import com.apexstudio.app.presentation.state.EditorState
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.applyVideoCropDraft
import com.apexstudio.app.presentation.viewmodel.applyVideoCropPreset
import com.apexstudio.app.ui.theme.ApexPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Crop controls target the explicitly selected visual clip; there is no first-clip fallback. */
@Composable
fun CropToolsOverlay(state: EditorState, vm: EditorViewModel, onClose: () -> Unit) {
    val selectedClip = remember(state.project?.clips, state.selectedClipId) {
        state.project?.clips?.firstOrNull { it.id == state.selectedClipId }
            ?.takeIf { it.type == ClipType.VIDEO || it.type == ClipType.IMAGE || it.type == ClipType.OVERLAY }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(), color = ApexPalette.BgSurface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), shadowElevation = 16.dp
    ) {
        if (selectedClip == null) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Crop, contentDescription = null, tint = ApexPalette.NeonCyan)
                Text("Select a video or photo clip to crop", color = Color.White, modifier = Modifier.padding(top = 8.dp))
                Text("Audio selections are never substituted with another clip.", color = ApexPalette.TextTertiary, fontSize = 11.sp)
                Text("Close", color = ApexPalette.NeonCyan, modifier = Modifier.padding(top = 14.dp).clickable(onClick = onClose))
            }
            return@Surface
        }
        // Changing source/selection/project or any persisted clip edit discards an uncommitted draft.
        key(state.project?.id, selectedClip) { CropToolsForClip(state, vm, selectedClip, onClose) }
    }
}

@Composable
private fun CropToolsForClip(state: EditorState, vm: EditorViewModel, clip: MediaClip, onClose: () -> Unit) {
    val context = LocalContext.current
    var dimensions by remember { mutableStateOf<CropSourceDimensions?>(null) }
    var loading by remember { mutableStateOf(true) }
    var retry by remember { mutableIntStateOf(0) }
    var manual by remember { mutableStateOf(false) }
    var applyError by remember { mutableStateOf(false) }
    LaunchedEffect(clip.id, clip.uri, clip.type, clip.photoEdit.normalizedRotationSteps, retry) {
        loading = true
        dimensions = withContext(Dispatchers.IO) { readCropSourceDimensions(context, clip) }
        loading = false
    }
    val size = dimensions
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val visual = clip.thumbnail ?: if (clip.type == ClipType.IMAGE) clip.uri else null
            if (visual != null) {
                AsyncImage(model = visual, contentDescription = "Selected crop source: ${clip.name}", contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier
                    .size(48.dp).clip(RoundedCornerShape(8.dp)).background(ApexPalette.BgElevated))
            } else Icon(Icons.Default.BrokenImage, contentDescription = "Video source", tint = ApexPalette.NeonCyan)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(clip.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(when {
                    loading -> "Reading source dimensions…"
                    size != null -> "Source ${size.width} × ${size.height}"
                    else -> "Could not read dimensions. Manual crop and Reset remain available."
                }, color = ApexPalette.TextTertiary, fontSize = 11.sp)
            }
            if (!loading && size == null) TextButton(onClick = { retry++ }) { Text("Retry") }
        }
        if (manual) {
            ManualCropPanel(initialCrop = clip.manualCropSettings(),
                onApply = { crop ->
                    val projectId = state.project?.id
                    if (projectId != null && vm.applyVideoCropDraft(projectId, clip, crop)) manual = false
                    else applyError = true
                }, onCancel = { manual = false; applyError = false })
            if (applyError) Text("The selected source changed. Close and reopen Crop before applying.",
                color = Color.Red, modifier = Modifier.padding(horizontal = 16.dp), fontSize = 11.sp)
        } else {
            val activeCrop = clip.manualCropSettings()
            CropPresetPanel(
                activePresetId = if (activeCrop.presetId == "free" && !activeCrop.isFullFrame) "custom" else activeCrop.presetId,
                onPresetSelected = { preset ->
                    if (preset == VideoCropPreset.FREE || size != null) {
                        val projectId = state.project?.id
                        applyError = projectId == null || !vm.applyVideoCropPreset(
                            projectId, clip, preset, size?.aspect ?: 1f)
                    }
                }, onClose = onClose, presetsEnabled = size != null,
                onManualEdit = { manual = true; applyError = false }
            )
            if (applyError) Text("The selected source changed. Close and reopen Crop before applying.",
                color = Color.Red, modifier = Modifier.padding(horizontal = 16.dp), fontSize = 11.sp)
        }
    }
}
