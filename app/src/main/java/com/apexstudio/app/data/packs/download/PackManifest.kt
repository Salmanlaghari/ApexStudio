package com.apexstudio.app.data.packs.download

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * On-demand pack manifest (PR-C / "Blake": everything heavy downloads on demand).
 *
 * Versioned JSON describing downloadable content packs. The canonical manifest
 * lives in `packs/manifest.json` in the repo and is published on the
 * `packs-v1` GitHub Release; the app fetches it from [MANIFEST_URL] and
 * caches it locally so the Packs UI works offline after the first fetch.
 *
 * Schema is documented in `packs/README.md`.
 */
object PackManifestRepo {

    private const val TAG = "PackManifest"

    /**
     * Raw-GitHub-hosted manifest (always current with `main`; no separate
     * server, no release ceremony). To pin a release instead, point this at a
     * `releases/download/<tag>/packs-manifest.json` asset (see packs/README.md).
     */
    const val MANIFEST_URL =
        "https://raw.githubusercontent.com/Salmanlaghari/ApexStudio/main/packs/manifest.json"

    private const val CACHE_NAME = "manifest-cache.json"

    /**
     * Why the last fetch failed (null when the last fetch succeeded). Read by
     * [PackManager] to surface catalog errors in the UI instead of failing
     * silently.
     */
    @Volatile
    var lastFetchError: String? = null
        private set

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Serializable
    data class RemotePack(
        val id: String,
        val name: String,
        val description: String = "",
        val category: String = "filters",
        val version: Int = 1,
        val versionName: String = "1.0",
        val sizeBytes: Long = 0L,
        val url: String = "",
        val sha256: String = "",
        val minAppVersion: String = "1.0.0",
        val entry: String = "",
        /** "available" or "coming_soon". Coming-soon packs render disabled, never as dead buttons. */
        val status: String = "available"
    ) {
        val isAvailable: Boolean get() = status == "available" && url.isNotBlank()
    }

    @Serializable
    data class PackManifestFile(
        val manifestVersion: Int = 1,
        val updatedAt: String = "",
        val packs: List<RemotePack> = emptyList()
    )

    private fun cacheFile(context: Context): File =
        File(context.filesDir, "packs/$CACHE_NAME")

    /**
     * Fetch the manifest, falling back to the local cache and finally to an
     * empty manifest. Never throws — callers always get a usable result, and
     * [lastFetchError] describes what went wrong for the UI.
     *
     * Supply-chain note: the manifest URL tracks the `main` branch. A
     * compromised manifest can at worst deny service or point at attacker
     * URLs — it cannot inject malicious *content*, because every downloaded
     * pack payload is SHA-256-verified against the hash in the manifest
     * before install ([DownloadEngine]). Treat unexpected manifest changes
     * with suspicion during review.
     */
    suspend fun fetchManifest(context: Context): PackManifestFile = withContext(Dispatchers.IO) {
        val remote = try {
            val conn = (URL(MANIFEST_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "ApexStudio-PackManager/1.0")
                instanceFollowRedirects = true
            }
            conn.inputStream.use { ins ->
                val text = ins.bufferedReader().readText()
                json.decodeFromString<PackManifestFile>(text).also {
                    // Cache the fresh manifest for offline use.
                    try {
                        val f = cacheFile(context)
                        f.parentFile?.mkdirs()
                        f.writeText(text)
                    } catch (e: Exception) {
                        Log.w(TAG, "Manifest cache write failed", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Manifest fetch failed, trying cache", e)
            lastFetchError = "Couldn't refresh the pack catalog (${e.message ?: "network error"})"
            null
        }
        if (remote != null) {
            lastFetchError = null
            return@withContext remote
        }

        try {
            val cached = cacheFile(context).takeIf { it.exists() }?.readText()
                ?: run {
                    lastFetchError = "Couldn't load the pack catalog — check your connection and retry"
                    return@withContext PackManifestFile()
                }
            // Serving the cache after a failed refresh: lastFetchError already
            // says "Couldn't refresh…" from the catch block above.
            json.decodeFromString<PackManifestFile>(cached)
        } catch (e: Exception) {
            Log.w(TAG, "Manifest cache read failed", e)
            lastFetchError = "Couldn't load the pack catalog — check your connection and retry"
            PackManifestFile()
        }
    }

    /** Human-readable size, e.g. "1.8 KB", "150 MB". */
    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "—"
        val units = arrayOf("B", "KB", "MB", "GB")
        var v = bytes.toDouble()
        var u = 0
        while (v >= 1024 && u < units.lastIndex) { v /= 1024; u++ }
        return if (u == 0) "${bytes} B" else String.format("%.1f %s", v, units[u])
    }

    /** Category display names for the Packs UI. */
    fun categoryTitle(category: String): String = when (category) {
        "filters" -> "Filter Packs"
        "transitions" -> "Transition Packs"
        "effects" -> "Effect Packs"
        "models" -> "AI Models"
        "camera" -> "Camera & Lenses"
        else -> category.replaceFirstChar { it.uppercase() }
    }
}
