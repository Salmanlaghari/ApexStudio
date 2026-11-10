package com.apexstudio.app.data.export

import androidx.media3.transformer.ExportException

internal const val UNSUPPORTED_VIDEO_FORMAT_EXPORT_MESSAGE =
    "This video uses a codec or profile your device cannot decode for export. Try an H.264/AVC MP4 version or another video."

internal const val VIDEO_EXPORT_MUXING_TIMEOUT_MESSAGE =
    "Export timed out while writing the MP4. Long gaps between video frames, common in some screen recordings, can stall export. Convert the source to a constant-frame-rate H.264/AVC MP4 and retry."

/** Adds recovery guidance for unsupported source formats and Media3 long-gap muxing timeouts. */
internal fun videoExportFailureMessage(failure: ExportException): String =
    when (failure.errorCode) {
        ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> UNSUPPORTED_VIDEO_FORMAT_EXPORT_MESSAGE
        ExportException.ERROR_CODE_MUXING_TIMEOUT -> VIDEO_EXPORT_MUXING_TIMEOUT_MESSAGE
        else -> failure.message?.takeIf { it.isNotBlank() } ?: "Export failed"
    }

/** Keeps the original exception for diagnostics while exposing the mapped message to project-export callers. */
internal fun videoExportFailureForProject(failure: ExportException): Exception =
    when (failure.errorCode) {
        ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
            IllegalStateException(UNSUPPORTED_VIDEO_FORMAT_EXPORT_MESSAGE, failure)
        ExportException.ERROR_CODE_MUXING_TIMEOUT ->
            IllegalStateException(VIDEO_EXPORT_MUXING_TIMEOUT_MESSAGE, failure)
        else -> failure
    }
