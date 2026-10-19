package com.apexstudio.app.data.picker

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.MediaStore
import android.util.Log
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

    companion object {
        /** Default timeline length for a still-image clip (CapCut-style). */
        const val DEFAULT_IMAGE_DURATION_MS = 3000L

        /**
         * Map a MIME type to a [ClipType]. Still images become
         * [ClipType.IMAGE] so the editor can offer the photo-editing
         * tools; everything unknown stays VIDEO (previous behaviour).
         */
        fun classifyClipType(mimeType: String): ClipType = when {
            mimeType.startsWith("video/") -> ClipType.VIDEO
            mimeType.startsWith("audio/") -> ClipType.AUDIO
            mimeType.startsWith("image/") -> ClipType.IMAGE
            else -> ClipType.VIDEO
        }
    }

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
    // Phase E: dedicated audio launcher. Used when the user taps
    // "+ Add → Audio" on the A1 lane. The contract is AudioOnly so
    // the picker UI hides video / image sources. The result goes
    // through the same _pickedMedia flow as video clips — the VM's
    // onMediaPicked tags the clip as ClipType.AUDIO because the
    // extractMetadata() helper already detects audio/ MIME.
    lateinit var pickAudioMedia: ActivityResultLauncher<String>

    @Composable
    fun registerLaunchers() {
        pickMultipleMedia = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickMultipleVisualMedia()
        ) { uris ->
            if (uris != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    val metadataList = uris.mapNotNull { uri -> extractMetadata(uri) }
                        .filter {
                            it.type == com.apexstudio.app.domain.model.ClipType.VIDEO ||
                                    it.type == com.apexstudio.app.domain.model.ClipType.IMAGE
                        }
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

        // Phase E: audio-only picker. Reuses the same _pickedMedia flow
        // so the existing collect callback in EditorScreen needs no
        // changes — it just sees a list with ClipType.AUDIO metadata.
        pickAudioMedia = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetMultipleContents()
        ) { uris ->
            if (uris != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    val metadataList = uris.mapNotNull { uri -> extractMetadata(uri) }
                        .filter { it.type == com.apexstudio.app.domain.model.ClipType.AUDIO }
                    _pickedMedia.emit(metadataList)
                    _pickGeneration.emit(_pickGeneration.value + 1)
                }
            }
        }
    }

    private fun extractMetadata(uri: Uri): MediaMetadata? {
        return try {
            CrashMarker.mark(context, "MediaPickerHelper.extractMetadata: $uri")
            // Generic document/photo providers often expose only the
            // OpenableColumns contract, not MediaStore's video columns. Do
            // not send still images through either MediaStore's duration
            // query or MediaMetadataRetriever (which cannot read JPEG/PNG).
            val mimeType = context.contentResolver.getType(uri) ?: ""
            if (mimeType.startsWith("image/")) {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, options)
                }
                val image = MediaMetadata(
                    uri = uri.toString(),
                    name = getFileName(uri),
                    durationMs = DEFAULT_IMAGE_DURATION_MS,
                    width = options.outWidth.coerceAtLeast(0),
                    height = options.outHeight.coerceAtLeast(0),
                    fps = 30,
                    type = ClipType.IMAGE
                )
                CrashMarker.clear(context)
                return image
            }
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
            retriever.release()
            val type = classifyClipType(mimeType)
            CrashMarker.clear(context)
            // Still images carry no duration — give photo clips a
            // CapCut-style default still length so they occupy timeline.
            val safeDuration = if (type == ClipType.IMAGE && durationMs <= 0L) DEFAULT_IMAGE_DURATION_MS else durationMs
            MediaMetadata(uri = uri.toString(), name = name, durationMs = safeDuration, width = width, height = height, fps = fps, type = type)
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
                    val type = classifyClipType(mime)
                    val safeDur = if (type == ClipType.IMAGE && dur <= 0L) DEFAULT_IMAGE_DURATION_MS else dur
                    MediaMetadata(uri = uri.toString(), name = name, durationMs = safeDur, width = w, height = h, fps = 30, type = type)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getFileName(uri: Uri): String {
        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }?.let { return it }
                    }
                }
            }
        } catch (_: Exception) {
            // Some providers omit even the optional display-name column.
        }
        return uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: "media_${UUID.randomUUID()}"
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

/**
 * Persist a picked media URI into app-private storage and return the
 * copy's `file://` URI. Picked `content://` URIs carry only a
 * grant-scoped read permission: once the process dies (or the grant
 * lapses), a saved project that still references the original URI can
 * no longer open its media — the overlay preview shows a black box
 * and thumbnails never load. A project-owned copy can never expire.
 *
 * Returns the ORIGINAL uri on any failure (copy is best-effort — a
 * failed copy must never block adding the clip). Files already inside
 * app storage are returned as-is.
 *
 * CLEANUP: copies live in `filesDir/project_media/`. They are reclaimed
 * by [deletePersistedMediaForUris] (project deletion) and
 * [cleanupStalePersistedMedia] (unreferenced files, run on project
 * load) — without those, copies accumulated forever with no cleanup
 * path. A `file://` / absolute-path source is NEVER copied: it
 * carries no expiring grant, so copying would only duplicate a
 * potentially huge file on disk.
 */
const val MAX_PERSIST_COPY_BYTES = 500L * 1024L * 1024L

suspend fun persistPickedMediaToAppStorage(context: Context, uri: String, name: String): String =
    withContext(Dispatchers.IO) {
        try {
            val parsed = Uri.parse(uri)
            // file:// and absolute-path sources never expire (no
            // grant) — return as-is instead of duplicating them,
            // regardless of size.
            if (parsed.scheme == "file") return@withContext uri
            if (uri.startsWith("/")) return@withContext uri
            // Only content:// URIs need a project-owned copy.
            if (parsed.scheme != "content") return@withContext uri
            // Max-size guard: refuse to duplicate a very large
            // source (a multi-GB video would double its disk cost).
            val declaredSize = try {
                context.contentResolver.openFileDescriptor(parsed, "r")?.use { it.statSize } ?: -1L
            } catch (_: Exception) {
                -1L
            }
            if (declaredSize > MAX_PERSIST_COPY_BYTES) {
                Log.w(
                    "MediaPickerHelper",
                    "persist skipped: source is ${declaredSize / (1024L * 1024L)}MB, " +
                        "over the ${MAX_PERSIST_COPY_BYTES / (1024L * 1024L)}MB copy cap — keeping original URI"
                )
                return@withContext uri
            }
            val dir = File(context.filesDir, "project_media")
            if (!dir.exists()) dir.mkdirs()
            val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                .takeLast(64).ifBlank { "media" }
            val dest = File(dir, "${UUID.randomUUID()}_$safeName")
            // Stream-copy with a running byte cap (statSize can be
            // -1 or lie for some providers): abort + delete the
            // partial file instead of duplicating a huge source.
            var copied = 0L
            var aborted = false
            context.contentResolver.openInputStream(parsed)?.use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        copied += n
                        if (copied > MAX_PERSIST_COPY_BYTES) {
                            aborted = true
                            break
                        }
                        output.write(buf, 0, n)
                    }
                }
            } ?: return@withContext uri
            if (aborted || dest.length() <= 0L) {
                dest.delete()
                if (aborted) {
                    Log.w(
                        "MediaPickerHelper",
                        "persist aborted: source exceeded the " +
                            "${MAX_PERSIST_COPY_BYTES / (1024L * 1024L)}MB copy cap — keeping original URI"
                    )
                }
                return@withContext uri
            }
            Uri.fromFile(dest).toString()
        } catch (e: Exception) {
            Log.w("MediaPickerHelper", "persistPickedMedia failed for $uri: ${e.message}")
            uri
        }
    }

/**
 * Delete the `project_media` copies referenced by [uris]. Call when
 * their project is deleted so its copies do not accumulate forever.
 * Only `file://` URIs under `filesDir/project_media/` are touched;
 * anything else is ignored. Best-effort, never throws. Call off the
 * main thread.
 */
fun deletePersistedMediaForUris(context: Context, uris: Collection<String>) {
    try {
        val dirPrefix = File(context.filesDir, "project_media").absolutePath
        for (uri in uris) {
            try {
                val parsed = Uri.parse(uri)
                if (parsed.scheme != "file") continue
                val path = parsed.path ?: continue
                if (!path.startsWith(dirPrefix)) continue
                val f = File(path)
                if (f.exists() && !f.delete()) {
                    Log.w("MediaPickerHelper", "Could not delete persisted copy: $path")
                }
            } catch (e: Exception) {
                Log.w("MediaPickerHelper", "deletePersistedMedia failed for $uri: ${e.message}")
            }
        }
    } catch (e: Exception) {
        Log.w("MediaPickerHelper", "deletePersistedMediaForUris failed: ${e.message}")
    }
}

/**
 * Delete every file in `filesDir/project_media/` that is NOT
 * referenced by [activeUris] (the clip/audio URIs of all saved
 * projects). Reclaims orphaned copies left by deleted projects,
 * failed persists, or pre-cleanup builds. Best-effort, never throws.
 * Call off the main thread (e.g. on project load).
 */
fun cleanupStalePersistedMedia(context: Context, activeUris: Collection<String>) {
    try {
        val dir = File(context.filesDir, "project_media")
        val files = dir.listFiles() ?: return
        // Compare by absolute path: active URIs may be file:// or
        // plain paths.
        val activePaths = activeUris.mapNotNull { uri ->
            try {
                val parsed = Uri.parse(uri)
                when (parsed.scheme) {
                    "file" -> parsed.path
                    null, "" -> uri
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
        }.toSet()
        var reclaimed = 0L
        for (f in files) {
            if (f.absolutePath !in activePaths) {
                val size = f.length()
                if (f.delete()) reclaimed += size
            }
        }
        if (reclaimed > 0L) {
            Log.d("MediaPickerHelper", "cleanupStalePersistedMedia reclaimed ${reclaimed / 1024L}KB")
        }
    } catch (e: Exception) {
        Log.w("MediaPickerHelper", "cleanupStalePersistedMedia failed: ${e.message}")
    }
}
