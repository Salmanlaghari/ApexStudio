package com.apexstudio.app.data.music

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Client for the Jamendo API v3 (https://developer.jamendo.com/v3.0).
 *
 * Why Jamendo and not the Pixabay "music API": Pixabay's documented public API
 * (https://pixabay.com/api/docs/) only exposes image and video endpoints —
 * there is no documented music/audio endpoint, so building the library on an
 * undocumented endpoint would be unverifiable and could break at any time.
 * Jamendo's whole catalogue is Creative-Commons licensed, the API is free with
 * a client_id, and it exposes 500k+ real tracks with search, tags, artwork,
 * stream URLs and download URLs.
 *
 * Free key: register an app at https://developer.jamendo.com/ and put the
 * client id in the `JAMENDO_CLIENT_ID` env var or `local.properties`
 * (it lands in `BuildConfig.JAMENDO_CLIENT_ID`).
 *
 * API terms: https://devportal.jamendo.com/api_terms_of_use — responses are
 * cached locally (metadata cache, 24h TTL) and previews/downloads are
 * user-initiated per track, never bulk-crawled.
 */
class JamendoMusicApi(
    private val clientId: String,
    private val cacheDir: File? = null
) {

    val isConfigured: Boolean get() = clientId.isNotBlank()

    suspend fun popular(page: Int = 0): MusicSearchPage = withContext(Dispatchers.IO) {
        query(mapOf("order" to "popularity_total"), page)
    }

    suspend fun search(query: String, page: Int = 0): MusicSearchPage = withContext(Dispatchers.IO) {
        query(mapOf("search" to query, "order" to "popularity_total"), page)
    }

    /**
     * Genre browse. Implemented via full-text [search]: Jamendo's documented
     * tag-filter parameter name is not stable across doc revisions, while
     * `search` reliably matches genre tags (the API indexes tags in
     * full-text search).
     */
    suspend fun byTag(tag: String, page: Int = 0): MusicSearchPage = withContext(Dispatchers.IO) {
        query(mapOf("search" to tag, "order" to "popularity_total"), page)
    }

    private fun query(extra: Map<String, String>, page: Int): MusicSearchPage {
        if (!isConfigured) return MusicSearchPage(emptyList())
        val params = LinkedHashMap<String, String>()
        params["client_id"] = clientId
        params["format"] = "json"
        params["limit"] = PAGE_SIZE.toString()
        params["offset"] = (page * PAGE_SIZE).toString()
        // mp32 = MP3 stream; include musicinfo for genre tags; 200px artwork.
        params["audioformat"] = "mp32"
        params["include"] = "musicinfo"
        params["imagesize"] = "200"
        params.putAll(extra)
        val url = buildUrl(params)
        val body = getCachedOrFetch(url)
        val tracks = parseJamendoTracks(body).filter { it.isPlayable }
        val total = parseTotal(body)
        val loaded = (page * PAGE_SIZE) + tracks.size
        return MusicSearchPage(tracks, total, hasMore = total > loaded && tracks.isNotEmpty())
    }

    private fun buildUrl(params: Map<String, String>): String {
        val qs = params.entries.joinToString("&") { (k, v) ->
            "$k=${URLEncoder.encode(v, "UTF-8")}"
        }
        return "$BASE_URL?$qs"
    }

    private fun getCachedOrFetch(url: String): String {
        readCache(url)?.let { return it }
        val body = httpGet(url)
        writeCache(url, body)
        return body
    }

    private fun cacheFileFor(url: String): File? {
        val dir = cacheDir ?: return null
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(dir, "jamendo_$digest.json")
    }

    private fun readCache(url: String): String? {
        return try {
            val file = cacheFileFor(url) ?: return null
            if (!file.exists() || !isFresh(file.lastModified(), System.currentTimeMillis())) return null
            file.readText()
        } catch (e: Exception) {
            Log.w(TAG, "Music metadata cache read failed", e)
            null
        }
    }

    private fun writeCache(url: String, body: String) {
        try {
            val file = cacheFileFor(url) ?: return
            file.parentFile?.mkdirs()
            file.writeText(body)
        } catch (e: Exception) {
            Log.w(TAG, "Music metadata cache write failed", e)
        }
    }

    private fun httpGet(url: String): String {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "ApexStudio/1.0 (Android)")
            }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                val err = try {
                    connection.errorStream?.bufferedReader()?.readText()?.take(300)
                } catch (_: Exception) { null }
                throw java.io.IOException("Jamendo API HTTP $code ${err ?: ""}".trim())
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: java.net.UnknownHostException) {
            throw java.io.IOException("No internet connection", e)
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        private const val TAG = "JamendoMusicApi"
        const val BASE_URL = "https://api.jamendo.com/v3.0/tracks/"
        const val PAGE_SIZE = 30
        /** Metadata cache TTL: 24h (keeps API usage well under the free quota). */
        const val CACHE_TTL_MS = 24L * 60 * 60 * 1000

        fun isFresh(cachedAtMs: Long, nowMs: Long): Boolean =
            cachedAtMs > 0 && (nowMs - cachedAtMs) < CACHE_TTL_MS

        /**
         * Pure, unit-testable mapping of a Jamendo `/tracks` JSON payload to
         * [RemoteMusicTrack]s. Never throws: malformed payloads yield an
         * empty list so one bad response can't break the library UI.
         */
        fun parseJamendoTracks(json: String): List<RemoteMusicTrack> {
            if (json.isBlank()) return emptyList()
            return try {
                val root = JSONObject(json)
                val results = root.optJSONArray("results") ?: return emptyList()
                (0 until results.length()).mapNotNull { i ->
                    parseTrack(results.optJSONObject(i))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse Jamendo response", e)
                emptyList()
            }
        }

        fun parseTotal(json: String): Int {
            return try {
                val headers = JSONObject(json).optJSONObject("headers") ?: return 0
                headers.optJSONObject("results_count")?.optInt("total", 0)
                    ?: headers.optInt("results_count", 0)
            } catch (_: Exception) {
                0
            }
        }

        private fun parseTrack(o: JSONObject?): RemoteMusicTrack? {
            if (o == null) return null
            val id = o.optString("id").ifBlank { return null }
            val title = o.optString("name").ifBlank { "Untitled" }
            val artist = o.optString("artist_name").ifBlank { "Unknown artist" }
            val durationMs = o.optLong("duration", 0L).coerceAtLeast(0L) * 1000L
            val licenseUrl = o.optString("license_ccurl")
            val tags = mutableListOf<String>()
            o.optJSONObject("musicinfo")?.optJSONObject("tags")?.let { t ->
                collectTags(t.opt("genres"), tags)
                collectTags(t.opt("vartags"), tags)
            }
            return RemoteMusicTrack(
                id = id,
                title = title,
                artist = artist,
                album = o.optString("album_name"),
                durationMs = durationMs,
                streamUrl = o.optString("audio"),
                downloadUrl = o.optString("audiodownload"),
                artworkUrl = o.optString("album_image"),
                licenseName = licenseShortName(licenseUrl),
                licenseUrl = licenseUrl,
                tags = tags.distinct().take(6)
            )
        }

        private fun collectTags(node: Any?, out: MutableList<String>) {
            when (node) {
                is JSONArray -> for (i in 0 until node.length()) {
                    node.optString(i).takeIf { it.isNotBlank() }?.let { out.add(it) }
                }
                is String -> node.takeIf { it.isNotBlank() }?.let { out.add(it) }
            }
        }

        /**
         * Turns a Creative-Commons deed URL such as
         * `https://creativecommons.org/licenses/by-nc-sa/3.0/` into `CC BY-NC-SA`.
         */
        fun licenseShortName(ccUrl: String): String {
            if (ccUrl.isBlank()) return "CC"
            val slug = ccUrl.substringAfter("/licenses/", "")
                .substringBefore("/").trim().lowercase()
            if (slug.isBlank()) return "CC"
            return "CC " + slug.split("-").joinToString("-") { it.uppercase() }
        }
    }
}
