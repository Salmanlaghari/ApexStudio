package com.apexstudio.app.ui.screens.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Only completed app-owned exports can leave the app; never expose a file:// URI. */
internal fun completedExportShareIntent(context: Context, output: String, caption: String? = null): Intent {
    val original = Uri.parse(output)
    val root = File(context.getExternalFilesDir(null), "ApexStudio_Exports").canonicalFile
    require(original.scheme == "file") { "This output is not a local completed export." }
    val file = File(requireNotNull(original.path)).canonicalFile
    require(file.parentFile == root && file.isFile && file.length() > 0L) {
        "The completed export is missing or outside the export folder. Export again before sharing."
    }
    val mime = when (file.extension.lowercase(java.util.Locale.ROOT)) {
        "mp4" -> "video/mp4"
        "m4a" -> "audio/mp4"
        else -> throw IllegalArgumentException("Only completed MP4 video or M4A audio can be shared.")
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    return Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        if (!caption.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, caption)
        clipData = ClipData.newUri(context.contentResolver, file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

internal fun shareCompletedExport(context: Context, output: String) {
    context.startActivity(Intent.createChooser(completedExportShareIntent(context, output), "Share exported media"))
}
