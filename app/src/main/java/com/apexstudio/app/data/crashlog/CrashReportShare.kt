package com.apexstudio.app.data.crashlog

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Only shares after the owner taps Share and chooses a destination. No network upload. */
object CrashReportShare {
    fun createArchive(context: Context): File {
        val directory = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        val archive = File(directory, "apexstudio-crash-evidence.zip")
        val log = File(context.filesDir, CrashLog.FILENAME)
        val trace = File(context.filesDir, ProcessExitDiagnostics.TRACE_FILENAME)
        ZipOutputStream(archive.outputStream()).use { zip ->
            listOf(log, trace).filter { it.exists() }.forEach { file ->
                zip.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return archive
    }

    fun share(context: Context) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", createArchive(context))
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("Crash evidence", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share crash evidence"))
    }
}
