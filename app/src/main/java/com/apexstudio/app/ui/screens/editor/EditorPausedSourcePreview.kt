package com.apexstudio.app.ui.screens.editor

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.domain.model.ClipTimeMapping
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.SpeedCurveMath
import com.apexstudio.app.ui.theme.EditorPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Actual source frame, not an export-composition preview. Frees live codecs before encoding. */
@Composable
internal fun ColumnScope.EditorPausedSourcePreview(clips: List<MediaClip>, clip: MediaClip?, playheadMs: Long) {
    val context = LocalContext.current
    val sourceTime = clip?.let {
        val localOutputTime = (playheadMs - SpeedCurveMath.timelineStartMsForClip(clips, it)).coerceAtLeast(0L)
        it.trimStartMs + ClipTimeMapping.mainSourceOffsetAtOutputMs(it, localOutputTime)
    } ?: 0L
    val frame by produceState<Result<Bitmap>?>(null, clip?.uri, sourceTime) {
        value = null
        if (clip != null) value = withContext(Dispatchers.IO) {
            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    val uri = Uri.parse(clip.uri)
                    when (uri.scheme) {
                        null, "" -> retriever.setDataSource(clip.uri)
                        "file" -> retriever.setDataSource(uri.path ?: error("Invalid video URI"))
                        else -> retriever.setDataSource(context, uri)
                    }
                    val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    require(w > 0 && h > 0) { "Source dimensions unavailable" }
                    val factor = minOf(1f, 720f / maxOf(w, h))
                    val targetW = (w * factor).toInt().coerceAtLeast(1)
                    val targetH = (h * factor).toInt().coerceAtLeast(1)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        retriever.getScaledFrameAtTime(sourceTime * 1000L, MediaMetadataRetriever.OPTION_CLOSEST, targetW, targetH)
                            ?: error("Source frame unavailable")
                    } else {
                        require(w.toLong() * h <= 12_000_000L) { "Source is too large for a safe preview on Android 8.0" }
                        val full = retriever.getFrameAtTime(sourceTime * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
                            ?: error("Source frame unavailable")
                        val scaled = Bitmap.createScaledBitmap(full, targetW, targetH, true)
                        if (scaled !== full) full.recycle()
                        scaled
                    }
                } finally { retriever.release() }
            }
        }
    }
    Box(Modifier.fillMaxWidth().weight(1f).padding(6.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().aspectRatio(LOCKED_EDITOR_PREVIEW_ASPECT)
            .clip(RoundedCornerShape(10.dp)).background(Color.Black)) {
            frame?.getOrNull()?.let { bitmap ->
                Image(bitmap.asImageBitmap(), "Paused source frame, not the rendered export composition",
                    Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
            if (frame == null && clip != null) CircularProgressIndicator(Modifier.align(Alignment.Center).size(24.dp), color = EditorPalette.NeonCyan)
            if (clip == null || frame?.isFailure == true) Text("Source frame unavailable", color = EditorPalette.TextSecondary,
                modifier = Modifier.align(Alignment.Center), fontSize = 12.sp)
            Text("Source frame only • live playback paused for export", color = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha = .7f)).padding(6.dp), fontSize = 10.sp)
        }
    }
}
