package com.apexstudio.app.data.export

import androidx.media3.transformer.ExportException

internal const val UNSUPPORTED_VIDEO_FORMAT_EXPORT_MESSAGE =
    "This video uses a codec or profile your device cannot decode for export. Try an H.264/AVC MP4 version or another video."

internal const val VIDEO_EXPORT_MUXING_TIMEOUT_MESSAGE =
    "Export timed out while writing the MP4. Long gaps between video frames, common in some screen recordings, can stall export. Convert the source to a constant-frame-rate H.264/AVC MP4 and retry."

/**
 * A Media3 transformer failure prepared for the project-export route.
 *
 * The user-facing message is resolved up front, and the original [ExportException]
 * is preserved as [cause] together with its `errorCode`/`errorCodeName`, so the
 * mapping never discards the diagnostics the raw exception carried. Callers get
 * this concrete type from [videoExportFailureForProject] instead of a plain
 * `Exception` they would have to downcast blindly.
 */
internal class ProjectExportException(
    val errorCode: Int,
    val errorCodeName: String,
    message: String,
    cause: ExportException
) : Exception(message, cause) {
    val sourceException: ExportException get() = cause as ExportException
}

/** Adds recovery guidance for unsupported source formats and Media3 long-gap muxing timeouts. */
internal fun videoExportFailureMessage(failure: ExportException): String =
    when (failure.errorCode) {
        ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> UNSUPPORTED_VIDEO_FORMAT_EXPORT_MESSAGE
        ExportException.ERROR_CODE_MUXING_TIMEOUT -> VIDEO_EXPORT_MUXING_TIMEOUT_MESSAGE
        // Keep the original message whenever one exists. A blank message carries no
        // information, so fall back with the Media3 error-code name attached instead
        // of silently replacing the failure with a bare generic string.
        else -> failure.message?.takeIf { it.isNotBlank() }
            ?: "Export failed (${failure.errorCodeName})"
    }

/**
 * Maps a transformer failure for the project-export route, preserving the original
 * exception as the cause and its error code/name on the returned [ProjectExportException].
 */
internal fun videoExportFailureForProject(failure: ExportException): ProjectExportException =
    ProjectExportException(
        errorCode = failure.errorCode,
        errorCodeName = failure.errorCodeName,
        message = videoExportFailureMessage(failure),
        cause = failure
    )

/**
 * Resolves the user-visible message for a failure caught by the project-export route.
 *
 * The mapped guidance wins even when a wrapper exception in between lost its own
 * message: a [ProjectExportException] or [ExportException] anywhere in the chain is
 * resolved through [videoExportFailureMessage] instead of trusting `failure.message`
 * alone, which is exactly the surface that hid the guidance before this fix.
 */
internal fun projectExportFailureMessage(failure: Throwable): String {
    if (failure is ProjectExportException) {
        return failure.message?.takeIf { it.isNotBlank() }
            ?: videoExportFailureMessage(failure.sourceException)
    }
    if (failure is ExportException) return videoExportFailureMessage(failure)
    failure.message?.takeIf { it.isNotBlank() }?.let { return it }
    return when (val cause = failure.cause) {
        is ProjectExportException, is ExportException -> projectExportFailureMessage(cause)
        else -> "Project export failed"
    }
}
