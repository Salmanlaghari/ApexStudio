package com.apexstudio.app.data.media

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/** Resolves the actual project media. Missing sources must never become sample footage. */
object MediaUriResolver {
    private const val TAG = "MediaUriResolver"

    /** Explicit demo/template callers only; never error recovery for user media. */
    fun getFallbackSampleUri(context: Context): Uri {
        val targetFile = File(context.filesDir, "apex_sample_cinematic.mp4")
        if (targetFile.exists() && targetFile.length() > 10_000) return Uri.fromFile(targetFile)
        return try {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                Uri.parse(SampleVideoGenerator.getOrCreateSampleVideo(context))
            }
        } catch (e: Exception) { Uri.fromFile(targetFile) }
    }

    fun resolvePlayableUri(context: Context, rawUri: String?): Uri {
        if (rawUri == "asset://" || rawUri == "sample") return getFallbackSampleUri(context)
        if (rawUri.isNullOrBlank()) return Uri.EMPTY
        if (rawUri.startsWith("content://")) {
            val parsed = Uri.parse(rawUri)
            val cachedFile = File(context.cacheDir, "picker_${rawUri.hashCode().toUInt().toString(16)}.mp4")
            if (cachedFile.exists() && cachedFile.length() > 0L) return Uri.fromFile(cachedFile)
            // Only publish complete copies; failed reads must not leave partial media.
            val temporary = try { File.createTempFile("picker_", ".partial", context.cacheDir) }
                catch (e: Exception) { return parsed }
            try {
                context.contentResolver.openInputStream(parsed)?.use { input ->
                    FileOutputStream(temporary).use { output -> input.copyTo(output) }
                }
                if (temporary.length() > 0L && temporary.renameTo(cachedFile)) return Uri.fromFile(cachedFile)
            } catch (e: Exception) {
                Log.w(TAG, "Project media could not be read; keeping its original URI", e)
            } finally { temporary.delete() }
            return parsed
        }
        // Preserve absolute locations even when unavailable. Never substitute a
        // same-named cache file or clone generated footage under a user's name.
        if (rawUri.startsWith("file://")) return Uri.parse(rawUri)
        if (rawUri.startsWith("/")) return Uri.fromFile(File(rawUri))
        val parsed = Uri.parse(rawUri)
        if (parsed.scheme != null) return parsed
        val cleanName = rawUri.substringAfterLast('/')
        for (dir in listOf(context.filesDir, context.cacheDir, context.getExternalFilesDir(null))) {
            if (dir != null) {
                val candidate = File(dir, cleanName)
                if (candidate.exists() && candidate.length() > 0L) return Uri.fromFile(candidate)
            }
        }
        return Uri.fromFile(File(context.filesDir, cleanName))
    }
}
