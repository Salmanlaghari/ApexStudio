package com.apexstudio.app.data.picker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableStateFlow
import androidx.compose.runtime.StateFlow
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class MediaMetadata(
    val uri: String,
    val name: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val fps: Int,
    val type: ClipType
)

class MediaPickerHelper(private val context: Context) {

    private val _pickedMedia = MutableStateFlow<List<MediaMetadata>>(emptyList())
    val pickedMedia: StateFlow<List<MediaMetadata>> = _pickedMedia

    val pickMultipleMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris != null) {
            CoroutineScope(Dispatchers.IO).launch {
                val metadataList = uris.mapNotNull { uri ->
                    extractMetadata(uri)
                }
                _pickedMedia.emit(metadataList)
            }
        }
    }

    val pickSingleMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            CoroutineScope(Dispatchers.IO).launch {
                val meta = extractMetadata(uri)
                if (meta != null) {
                    _pickedMedia.emit(listOf(meta))
                }
            }
        }
    }

    private fun extractMetadata(uri: Uri): MediaMetadata? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, uri)

            val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L

            val widthStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val width = widthStr?.toIntOrNull() ?: 0
            val height = heightStr?.toIntOrNull() ?: 0

            val fpsStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            val fps = fpsStr?.toFloatOrNull()?.toInt() ?: 30

            val name = getFileName(uri)
            val mimeType = context.contentResolver.getType(uri) ?: ""

            retriever.release()
            inputStream.close()

            val type = if (mimeType.startsWith("video/")) ClipType.VIDEO
            else if (mimeType.startsWith("audio/")) ClipType.AUDIO
            else ClipType.VIDEO

            MediaMetadata(
                uri = uri.toString(),
                name = name,
                durationMs = durationMs,
                width = width,
                height = height,
                fps = fps,
                type = type
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "media_${UUID.randomUUID()}"
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIdx = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (nameIdx >= 0) {
                    name = it.getString(nameIdx)
                }
            }
        }
        return name
    }

    fun toMediaClip(metadata: MediaMetadata, trackIndex: Int = 0): MediaClip {
        return MediaClip(
            id = UUID.randomUUID().toString(),
            name = metadata.name,
            uri = metadata.uri,
            durationMs = metadata.durationMs,
            trimStartMs = 0L,
            trimEndMs = metadata.durationMs,
            thumbnail = null,
            trackIndex = trackIndex,
            type = metadata.type
        )
    }
}
