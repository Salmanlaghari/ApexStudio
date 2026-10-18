package com.apexstudio.app.ui.screens.editor

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.presentation.state.EditorState
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.setVideoCropPreset
import com.apexstudio.app.ui.theme.ApexPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Crop controls target the explicitly selected V1 visual clip; there is deliberately no first-clip fallback. */
@Composable
fun CropToolsOverlay(state: EditorState, vm: EditorViewModel, onClose: () -> Unit) {
    val selectedClip = remember(state.project?.clips, state.selectedClipId) {
        state.project?.clips?.firstOrNull { it.id == state.selectedClipId }
            ?.takeIf { it.type == ClipType.VIDEO || it.type == ClipType.IMAGE || it.type == ClipType.OVERLAY }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ApexPalette.BgSurface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        shadowElevation = 16.dp
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
        CropToolsForClip(state, vm, selectedClip, onClose)
    }
}

@Composable
private fun CropToolsForClip(state: EditorState, vm: EditorViewModel, clip: MediaClip, onClose: () -> Unit) {
    val context = LocalContext.current
    var dimensions by remember(clip.id, clip.uri) { mutableStateOf(0 to 0) }
    LaunchedEffect(clip.id, clip.uri, clip.type) {
        dimensions = withContext(Dispatchers.IO) { sourceDimensions(context, clip) }
    }
    val (width, height) = dimensions
    val sourceAspect = if (width > 0 && height > 0) width.toFloat() / height else 1f
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val visual = clip.thumbnail ?: if (clip.type == ClipType.IMAGE) clip.uri else null
            if (visual != null) {
                AsyncImage(model = visual, contentDescription = "Selected crop source: ${clip.name}", contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier
                    .size(48.dp).clip(RoundedCornerShape(8.dp)).background(ApexPalette.BgElevated))
            } else {
                Icon(Icons.Default.BrokenImage, contentDescription = "Video source", tint = ApexPalette.NeonCyan)
            }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(clip.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(if (width > 0 && height > 0) "Source ${width} × ${height}" else "Reading source dimensions…", color = ApexPalette.TextTertiary, fontSize = 11.sp)
            }
            Text("${clip.type.name.lowercase()}", color = ApexPalette.NeonCyan, fontSize = 10.sp)
        }
        CropPresetPanel(
            activePresetId = clip.videoCrop.presetId,
            onPresetSelected = { preset ->
                if (width > 0 && height > 0) vm.setVideoCropPreset(clip.id, preset, sourceAspect)
            },
            onClose = onClose
        )
    }
}

private fun sourceDimensions(context: android.content.Context, clip: MediaClip): Pair<Int, Int> {
    if (clip.type == ClipType.IMAGE) return imageDimensions(context, clip.uri)
    val retriever = MediaMetadataRetriever()
    val videoSize = try {
        retriever.setDataSource(context, Uri.parse(clip.uri))
        var w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        var h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (rotation % 180 != 0) { val t = w; w = h; h = t }
        w.coerceAtLeast(0) to h.coerceAtLeast(0)
    } catch (_: Exception) { 0 to 0 } finally { try { retriever.release() } catch (_: Exception) {} }
    if (videoSize.first > 0 && videoSize.second > 0) return videoSize
    return imageDimensions(context, clip.uri)
}

private fun imageDimensions(context: android.content.Context, uri: String): Pair<Int, Int> = try {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, options) }
    options.outWidth.coerceAtLeast(0) to options.outHeight.coerceAtLeast(0)
} catch (_: Exception) { 0 to 0 }
