package com.apexstudio.app.data.audio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Imported audio is copied out of the selected document provider into app-private,
 * persistent storage, so editor projects do not depend on a temporary URI grant.
 *
 * For ACTION_OPEN_DOCUMENT, the caller should persist the read permission before
 * invoking this helper when it intends to retain/use the source URI elsewhere. This
 * helper itself only needs the launch result's temporary permission because it copies
 * the bytes before returning.
 */
data class ImportedLocalAudio(
    /** Absolute local-file path, matching the editor's existing audio-track URI convention. */
    val uri: String,
    val displayName: String,
    val durationMs: Long
)

class LocalAudioImportException(
    val reason: Reason,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    enum class Reason { PERMISSION_DENIED, METADATA_UNAVAILABLE, INVALID_AUDIO, COPY_FAILED }
}

/** Small deterministic rules shared by importer and local unit tests. */
internal object LocalAudioImportPolicy {
    fun requireAudioStream(metadataValue: String?) {
        if (!metadataValue.equals("yes", ignoreCase = true)) {
            throw LocalAudioImportException(
                LocalAudioImportException.Reason.INVALID_AUDIO,
                "The selected file does not contain a readable audio stream."
            )
        }
    }

    fun requireDurationMs(metadataValue: String?): Long {
        val duration = metadataValue?.toLongOrNull() ?: 0L
        if (duration <= 0L) {
            throw LocalAudioImportException(
                LocalAudioImportException.Reason.INVALID_AUDIO,
                "The selected file has no readable audio duration."
            )
        }
        return duration
    }

    fun safeFileName(displayName: String?, fallbackName: String, mimeType: String?): String {
        val candidate = displayName
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: fallbackName
        val cleaned = candidate
            .map { ch -> if (ch.isLetterOrDigit() || ch in " ._-()") ch else '_' }
            .joinToString("")
            .trim()
            .trim('.')
            .take(120)
            .ifBlank { "Imported audio" }
        if (cleaned.contains('.')) return cleaned
        val extension = when (mimeType?.lowercase()) {
            "audio/mp4", "audio/x-m4a" -> ".m4a"
            "audio/mpeg" -> ".mp3"
            "audio/wav", "audio/x-wav" -> ".wav"
            "audio/ogg" -> ".ogg"
            "audio/flac" -> ".flac"
            "audio/aac" -> ".aac"
            else -> ".audio"
        }
        return cleaned + extension
    }
}

/**
 * Reads the provider display name and real media duration, then copies the source
 * bytes into filesDir/ImportedAudio. No synthetic duration fallback is used.
 */
suspend fun importLocalAudio(context: Context, sourceUri: Uri): ImportedLocalAudio =
    withContext(Dispatchers.IO) {
        val coroutineContext = currentCoroutineContext()
        coroutineContext.ensureActive()
        val resolver = context.contentResolver
        val mimeType = try {
            resolver.getType(sourceUri)
        } catch (e: SecurityException) {
            throw LocalAudioImportException(
                LocalAudioImportException.Reason.PERMISSION_DENIED,
                "Permission to read the selected audio file was denied.",
                e
            )
        }

        val displayName = try {
            resolver.query(sourceUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) cursor.getString(index)?.takeIf(String::isNotBlank) else null
                    } else null
                }
        } catch (e: SecurityException) {
            throw LocalAudioImportException(
                LocalAudioImportException.Reason.PERMISSION_DENIED,
                "Permission to read the selected audio file was denied.",
                e
            )
        } catch (_: Exception) {
            null
        } ?: sourceUri.lastPathSegment?.takeIf(String::isNotBlank) ?: "Imported audio"

        val durationMs = try {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, sourceUri)
                LocalAudioImportPolicy.requireAudioStream(
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
                )
                LocalAudioImportPolicy.requireDurationMs(
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                )
            } finally {
                retriever.release()
            }
        } catch (e: LocalAudioImportException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            throw LocalAudioImportException(
                LocalAudioImportException.Reason.PERMISSION_DENIED,
                "Permission to inspect the selected audio file was denied.",
                e
            )
        } catch (e: Exception) {
            throw LocalAudioImportException(
                LocalAudioImportException.Reason.METADATA_UNAVAILABLE,
                "Could not read audio metadata from the selected file.",
                e
            )
        }
        coroutineContext.ensureActive()

        val directory = File(context.filesDir, "ImportedAudio")
        if (!directory.exists() && !directory.mkdirs()) {
            throw LocalAudioImportException(
                LocalAudioImportException.Reason.COPY_FAILED,
                "Could not create local audio storage."
            )
        }
        val outputName = LocalAudioImportPolicy.safeFileName(displayName, "Imported audio", mimeType)
        val target = File(directory, "${UUID.randomUUID()}_$outputName")
        val temporary = File(directory, "${target.name}.part")
        try {
            val input = resolver.openInputStream(sourceUri)
                ?: throw IOException("The selected provider returned no readable file stream.")
            input.use { source -> temporary.outputStream().buffered().use { destination ->
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                var totalBytes = 0L
                while (true) {
                    coroutineContext.ensureActive()
                    val count = source.read(buffer)
                    if (count < 0) break
                    totalBytes += count
                    if (totalBytes > MAX_IMPORT_BYTES) {
                        throw LocalAudioImportException(
                            LocalAudioImportException.Reason.COPY_FAILED,
                            "The selected audio file is too large to import."
                        )
                    }
                    destination.write(buffer, 0, count)
                }
            } }
            if (temporary.length() <= 0L) throw IOException("The selected audio file is empty.")
            coroutineContext.ensureActive()
            if (!temporary.renameTo(target)) throw IOException("Could not finalize the local audio copy.")
        } catch (e: CancellationException) {
            temporary.delete()
            target.delete()
            throw e
        } catch (e: LocalAudioImportException) {
            temporary.delete()
            target.delete()
            throw e
        } catch (e: SecurityException) {
            temporary.delete()
            target.delete()
            throw LocalAudioImportException(
                LocalAudioImportException.Reason.PERMISSION_DENIED,
                "Permission to copy the selected audio file was denied.",
                e
            )
        } catch (e: Exception) {
            temporary.delete()
            target.delete()
            throw LocalAudioImportException(
                LocalAudioImportException.Reason.COPY_FAILED,
                "Could not copy the selected audio file into the project. Try choosing it again.",
                e
            )
        }

        ImportedLocalAudio(
            uri = target.absolutePath,
            displayName = displayName,
            durationMs = durationMs
        )
    }

private const val COPY_BUFFER_BYTES = 64 * 1024
private const val MAX_IMPORT_BYTES = 2L * 1024L * 1024L * 1024L
