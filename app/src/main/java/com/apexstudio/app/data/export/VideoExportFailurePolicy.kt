package com.apexstudio.app.data.export

import androidx.media3.transformer.ExportException

internal const val UNSUPPORTED_VIDEO_FORMAT_EXPORT_MESSAGE =
    "This video uses a codec or profile your device cannot decode for export. Try an H.264/AVC MP4 version or another video."

/** Adds specific recovery guidance only when Media3 identifies an unsupported source decode format. */
internal fun videoExportFailureMessage(failure: ExportException): String =
    if (failure.errorCode == ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED) {
        UNSUPPORTED_VIDEO_FORMAT_EXPORT_MESSAGE
    } else {
        failure.message?.takeIf { it.isNotBlank() } ?: "Export failed"
    }

/** Keeps the original exception for diagnostics while exposing the mapped message to project-export callers. */
internal fun videoExportFailureForProject(failure: ExportException): Exception =
    if (failure.errorCode == ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED) {
        IllegalStateException(UNSUPPORTED_VIDEO_FORMAT_EXPORT_MESSAGE, failure)
    } else {
        failure
    }
