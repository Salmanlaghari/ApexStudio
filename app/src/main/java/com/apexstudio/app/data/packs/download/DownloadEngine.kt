package com.apexstudio.app.data.packs.download

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Resumable file download engine (PR-C).
 *
 * - HTTP `Range` resume via a `.part` file (servers without Range support restart cleanly)
 * - Progress callbacks on the calling coroutine (UI collects and posts to main itself)
 * - 3 attempts with exponential backoff on transient failures
 * - SHA-256 verification after download (when an expected hash is provided)
 * - Atomic install: downloads to `<name>.part`, verifies, then renames
 *
 * Plain `HttpURLConnection` — zero new dependencies, full control, no APK bloat.
 */
object DownloadEngine {

    private const val TAG = "DownloadEngine"
    private const val MAX_ATTEMPTS = 3
    private const val BUFFER = 32 * 1024

    sealed interface Result {
        data class Success(val file: File) : Result
        data class Failed(val reason: String, val canRetry: Boolean = true) : Result
    }

    /** Thrown for HTTP error responses; carries the status code for reliable retry decisions. */
    class HttpException(val code: Int, message: String) : java.io.IOException(message)

    /**
     * Derives a safe local file name from a pack URL. Centralized here so the
     * engine and [PackManager] (progress seeding) always agree on the `.part`
     * file name. Strips query strings; falls back to `<packId>.bin`.
     */
    internal fun fileNameFor(url: String, packId: String): String =
        url.substringAfterLast('/').substringBefore('?').ifBlank { "$packId.bin" }

    /**
     * Download [url] to [destFile].
     *
     * @param expectedSha256 hex SHA-256 the finished file must match ("" skips verification)
     * @param onProgress invoked with (bytesDone, bytesTotal); bytesTotal may be -1 when unknown
     */
    suspend fun download(
        url: String,
        destFile: File,
        expectedSha256: String = "",
        onProgress: (done: Long, total: Long) -> Unit = { _, _ -> }
    ): Result = withContext(Dispatchers.IO) {
        var lastError = "unknown"
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                return@withContext attemptDownload(url, destFile, expectedSha256, onProgress)
            } catch (e: HttpException) {
                // 429 (rate-limited) and 408 (timeout) are worth retrying with
                // backoff; other 4xx mean the request itself is wrong.
                val retryable = e.code == 429 || e.code == 408 || e.code !in 400..499
                if (!retryable) {
                    return@withContext Result.Failed("Download failed (HTTP ${e.code})", canRetry = false)
                }
                lastError = "HTTP ${e.code}"
                Log.w(TAG, "Download attempt ${attempt + 1} failed: $lastError", e)
                if (attempt < MAX_ATTEMPTS - 1) delay(1500L shl attempt)
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
                Log.w(TAG, "Download attempt ${attempt + 1} failed: $lastError", e)
                if (attempt < MAX_ATTEMPTS - 1) delay(1500L shl attempt)
            }
        }
        Result.Failed("Download failed after $MAX_ATTEMPTS attempts ($lastError)")
    }

    private fun attemptDownload(
        url: String,
        destFile: File,
        expectedSha256: String,
        onProgress: (Long, Long) -> Unit
    ): Result {
        val partFile = File(destFile.parentFile, destFile.name + ".part")
        destFile.parentFile?.mkdirs()

        // If the final file already exists and verifies, skip the download.
        if (destFile.exists() && verifySha256(destFile, expectedSha256)) {
            onProgress(destFile.length(), destFile.length())
            return Result.Success(destFile)
        }

        var downloaded = if (partFile.exists()) partFile.length() else 0L
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ApexStudio-PackManager/1.0")
            if (downloaded > 0) setRequestProperty("Range", "bytes=$downloaded-")
        }

        val code = conn.responseCode
        // Server ignored Range (200 instead of 206) — restart from scratch.
        if (code == HttpURLConnection.HTTP_OK && downloaded > 0) {
            partFile.delete()
            downloaded = 0L
        } else if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
            conn.disconnect()
            throw HttpException(code, "HTTP $code")
        }

        // True total: prefer Content-Range ("bytes 0-99/1000") — for 206 the
        // Content-Length header is only the REMAINING bytes, which would make
        // the progress bar start past 100%.
        val total = conn.getHeaderField("Content-Range")
            ?.substringAfterLast('/', "")
            ?.toLongOrNull()?.takeIf { it > 0 }
            ?: conn.getHeaderField("Content-Length")?.toLongOrNull()?.let {
                if (code == HttpURLConnection.HTTP_PARTIAL) it + downloaded else it
            } ?: -1L

        try {
            conn.inputStream.use { input ->
                RandomAccessFile(partFile, "rw").use { raf ->
                    raf.seek(downloaded)
                    val buf = ByteArray(BUFFER)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        raf.write(buf, 0, n)
                        downloaded += n
                        onProgress(downloaded, total)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }

        if (!verifySha256(partFile, expectedSha256)) {
            partFile.delete()
            return Result.Failed("Checksum mismatch — file deleted, please retry", canRetry = true)
        }

        // Atomic install.
        if (destFile.exists()) destFile.delete()
        if (!partFile.renameTo(destFile)) {
            // Rename across volumes can fail; fall back to copy.
            partFile.copyTo(destFile, overwrite = true)
            partFile.delete()
        }
        onProgress(destFile.length(), destFile.length())
        return Result.Success(destFile)
    }

    /** Verifies [file] against [expectedHex]; blank expected hash always passes. */
    fun verifySha256(file: File, expectedHex: String): Boolean {
        if (expectedHex.isBlank()) return true
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { ins ->
                val buf = ByteArray(BUFFER)
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    digest.update(buf, 0, n)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            actual.equals(expectedHex, ignoreCase = true)
        } catch (e: Exception) {
            Log.w(TAG, "SHA-256 verification error", e)
            false
        }
    }
}
