package com.apexstudio.app.data.picker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import com.apexstudio.app.data.crashlog.CrashMarker
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    // Bump a counter on every successful pick so subscribers see a fresh
    // emission even when the user picks the same file twice in a row
    // (StateFlow conflates equal values, which would otherwise make
    // the editor "ignore" a re-pick of the same URI).
    private val _pickedMedia = MutableStateFlow<List<MediaMetadata>>(emptyList())
    val pickedMedia: StateFlow<List<MediaMetadata>> = _pickedMedia

    // Monotonic counter incremented on every emit. The composable
    // collects this together with the metadata so equal payloads still
    // trigger a refresh.
    private val _pickGeneration = MutableStateFlow(0L)
    val pickGeneration: StateFlow<Long> = _pickGeneration

    lateinit var pickMultipleMedia: ActivityResultLauncher<PickVisualMediaRequest>
    lateinit var pickSingleMedia: ActivityResultLauncher<String>

    @Composable
    fun registerLaunchers() {
        pickMultipleMedia = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickMultipleVisualMedia()
        ) { uris ->
            if (uris != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    val metadataList = uris.mapNotNull { uri -> extractMetadata(uri) }
                        .filter { it.type == com.apexstudio.app.domain.model.ClipType.VIDEO }
                    _pickedMedia.emit(metadataList)
                    _pickGeneration.emit(_pickGeneration.value + 1)
                }
            }
        }

        pickSingleMedia = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    val meta = extractMetadata(uri)
                    if (meta != null) {
                        _pickedMedia.emit(listOf(meta))
                        _pickGeneration.emit(_pickGeneration.value + 1)
                    }
                }
            }
        }
    }

    private fun extractMetadata(uri: Uri): MediaMetadata? {
        return try {
            CrashMarker.mark(context, "MediaPickerHelper.extractMetadata: $uri")
            // Prefer MediaStore (pure Java, no native codec path). MediaMetadataRetriever
            // (stagefright) is a known native-crash source on some devices/videos and its
            // crash bypasses try/catch, killing the whole process at "confirm" time.
            val fromStore = queryMediaStore(uri)
            if (fromStore != null) {
                CrashMarker.clear(context)
                return fromStore
            }
            // Fallback only when MediaStore has no data (rare). Still wrapped.
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val fps = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()?.toInt() ?: 30
            val name = getFileName(uri)
            val mimeType = context.contentResolver.getType(uri) ?: ""
            retriever.release()
            val type = if (mimeType.startsWith("video/")) ClipType.VIDEO
            else if (mimeType.startsWith("audio/")) ClipType.AUDIO
            else ClipType.VIDEO
            CrashMarker.clear(context)
            MediaMetadata(uri = uri.toString(), name = name, durationMs = durationMs, width = width, height = height, fps = fps, type = type)
        } catch (e: Exception) {
            CrashMarker.clear(context)
            null
        }
    }

    private fun queryMediaStore(uri: Uri): MediaMetadata? {
        return try {
            val proj = arrayOf(
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DURATION,
                MediaStore.MediaColumns.WIDTH,
                MediaStore.MediaColumns.HEIGHT
            )
            context.contentResolver.query(uri, proj, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val name = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)) ?: "media"
                    val mime = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)) ?: ""
                    val dur = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION))
                    val w = c.getInt(c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH))
                    val h = c.getInt(c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT))
                    val type = if (mime.startsWith("video/")) ClipType.VIDEO
                    else if (mime.startsWith("audio/")) ClipType.AUDIO
                    else ClipType.VIDEO
                    MediaMetadata(uri = uri.toString(), name = name, durationMs = dur, width = w, height = h, fps = 30, type = type)
                } else {
                    null
                }
            }
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
