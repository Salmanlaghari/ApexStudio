package com.apexstudio.app.ui.screens.export

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import kotlin.math.floor

/** Estimate only the video payload; audio/container overhead is intentionally not guessed. */
internal fun estimateVideoPayloadBytes(durationMs: Long, requestedVideoBitrateBps: Int): Long? {
    if (durationMs <= 0L || requestedVideoBitrateBps <= 0) return null
    val bytes = durationMs.toDouble() * requestedVideoBitrateBps.toDouble() / 8_000.0
    if (!bytes.isFinite() || bytes > Long.MAX_VALUE.toDouble()) return null
    return floor(bytes).toLong().coerceAtLeast(0L)
}

internal data class ExportPayloadEstimate(
    val videoBytes: Long,
    val audioBytes: Long?,
    /** Null means audio is expected but its bitrate is not known/configured. */
    val totalPayloadBytes: Long?
)

internal fun estimateExportPayload(
    durationMs: Long,
    requestedVideoBitrateBps: Int,
    audioExpected: Boolean,
    audioBitrateBps: Int? = null
): ExportPayloadEstimate? {
    val videoBytes = estimateVideoPayloadBytes(durationMs, requestedVideoBitrateBps) ?: return null
    if (!audioExpected) return ExportPayloadEstimate(videoBytes, 0L, videoBytes)
    val audioBytes = audioBitrateBps?.let { estimateVideoPayloadBytes(durationMs, it) }
    val totalBytes = audioBytes?.let { audio ->
        if (videoBytes > Long.MAX_VALUE - audio) null else videoBytes + audio
    }
    return ExportPayloadEstimate(videoBytes, audioBytes, totalBytes)
}

internal fun formatByteCount(bytes: Long): String {
    if (bytes < 0L) return "Unavailable"
    if (bytes == 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1000.0 && unit < units.lastIndex) {
        value /= 1000.0
        unit++
    }
    return if (unit == 0) "$bytes B" else String.format(java.util.Locale.US, "%.1f %s", value, units[unit])
}

internal sealed interface ActualOutputSize {
    data class Measured(val bytes: Long) : ActualOutputSize
    data class Unavailable(val reason: String) : ActualOutputSize
}

/** Keep a measured zero distinct from missing, inaccessible, and unknown-size outputs. */
internal fun actualFileSize(exists: Boolean, isRegularFile: Boolean, bytes: Long): ActualOutputSize = when {
    !exists || !isRegularFile -> ActualOutputSize.Unavailable("The completed file is missing or unavailable.")
    bytes < 0L -> ActualOutputSize.Unavailable("The file system did not report a file size.")
    else -> ActualOutputSize.Measured(bytes)
}

internal fun actualContentSize(size: Long?): ActualOutputSize = when {
    size == null -> ActualOutputSize.Unavailable("The provider did not report the completed file size.")
    size < 0L -> ActualOutputSize.Unavailable("The provider reported an invalid completed file size.")
    else -> ActualOutputSize.Measured(size)
}

/** Exceptions (including denied URI access) are represented as unavailable, never as zero bytes. */
internal fun safelyReadActualContentSize(read: () -> Long?): ActualOutputSize = try {
    actualContentSize(read())
} catch (_: SecurityException) {
    ActualOutputSize.Unavailable("Access to the completed file was denied.")
} catch (_: Exception) {
    ActualOutputSize.Unavailable("The completed file size could not be read.")
}

/**
 * Read metadata for the completed file without trusting a raw content-URI path.
 * File URIs are limited to the app's export directory; content URIs use only
 * ContentResolver metadata / a read-only descriptor. No URI or filesystem path is exposed.
 */
internal fun measureCompletedOutputSize(context: Context, rawUri: String): ActualOutputSize {
    return try {
        val uri = Uri.parse(rawUri)
        when (uri.scheme?.lowercase(java.util.Locale.ROOT)) {
            "file" -> {
                val externalRoot = context.getExternalFilesDir(null)
                    ?: return ActualOutputSize.Unavailable("App export storage is unavailable.")
                val root = File(externalRoot, "ApexStudio_Exports").canonicalFile
                val path = uri.path ?: return ActualOutputSize.Unavailable("The completed file URI is invalid.")
                val file = File(path).canonicalFile
                if (file.parentFile != root) {
                    ActualOutputSize.Unavailable("The completed file is outside the app export folder.")
                } else {
                    actualFileSize(file.exists(), file.isFile, if (file.exists() && file.isFile) file.length() else -1L)
                }
            }
            "content" -> safelyReadActualContentSize {
                val resolver = context.contentResolver
                val queried = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (!cursor.moveToFirst()) null else {
                        val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (index < 0 || cursor.isNull(index)) null else cursor.getLong(index)
                    }
                }
                queried ?: resolver.openFileDescriptor(uri, "r")?.use { it.statSize }?.takeIf { it >= 0L }
            }
            else -> ActualOutputSize.Unavailable("The completed file URI type is unsupported.")
        }
    } catch (_: SecurityException) {
        ActualOutputSize.Unavailable("Access to the completed file was denied.")
    } catch (_: Exception) {
        ActualOutputSize.Unavailable("The completed file size could not be read.")
    }
}
