package com.apexstudio.app.data.stickers

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/** Public, attribution-friendly sticker/image search backed by Openverse. */
object OnlineStickerRepository {
    data class Result(
        val id: String,
        val title: String,
        val previewUrl: String,
        val imageUrl: String,
        val creator: String?,
        val license: String?
    )

    suspend fun search(query: String, limit: Int = 18): List<Result> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext emptyList()
        val encoded = URLEncoder.encode(clean, Charsets.UTF_8.name())
        val endpoint = URL("https://api.openverse.org/v1/images/?q=$encoded&page_size=${limit.coerceIn(1, 30)}&mature=false")
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "ApexStudio/1.0 sticker-search")
        }
        try {
            if (connection.responseCode !in 200..299) return@withContext emptyList()
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val results = json.optJSONArray("results") ?: return@withContext emptyList()
            buildList {
                for (index in 0 until results.length()) {
                    val item = results.optJSONObject(index) ?: continue
                    val image = item.optString("url").trim()
                    val preview = item.optString("thumbnail").trim().ifBlank { image }
                    if (!image.startsWith("https://") || !preview.startsWith("https://")) continue
                    add(Result(
                        id = item.optString("id").ifBlank { image },
                        title = item.optString("title").ifBlank { "Online sticker" },
                        previewUrl = preview,
                        imageUrl = image,
                        creator = item.optString("creator").takeIf { it.isNotBlank() },
                        license = item.optString("license").takeIf { it.isNotBlank() }
                    ))
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun download(context: Context, result: Result): String? = withContext(Dispatchers.IO) {
        if (!result.imageUrl.startsWith("https://")) return@withContext null
        val directory = File(context.cacheDir, "online-stickers").apply { mkdirs() }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(result.imageUrl.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val output = File(directory, "$digest.png")
        if (output.length() > 0L) return@withContext output.absolutePath
        val connection = (URL(result.imageUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ApexStudio/1.0 sticker-download")
        }
        try {
            if (connection.responseCode !in 200..299) return@withContext null
            if (connection.contentLengthLong > 12L * 1024L * 1024L) return@withContext null
            connection.inputStream.use { input ->
                output.outputStream().use { out ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > 12L * 1024L * 1024L) {
                            output.delete()
                            return@withContext null
                        }
                        out.write(buffer, 0, count)
                    }
                }
            }
            output.takeIf { it.length() > 0L }?.absolutePath
        } finally {
            connection.disconnect()
        }
    }
}
