package com.apexstudio.app.data.filter

import android.content.Context
import android.util.Log
import com.apexstudio.app.data.packs.download.PackManager
import com.apexstudio.app.data.packs.download.PackManifestRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStreamReader
import java.security.MessageDigest

/** Loads verified LUT entries from installed, on-demand filter packs. */
object DownloadedLutFilterRepository {
    const val PAYLOAD_TYPE = "cube_lut_v1"
    const val ASSET_PREFIX = "downloaded-lut://"
    private const val TAG = "DownloadedLutFilters"
    private const val MAX_CUBE_BYTES = 1024L * 1024L
    private val SAFE_ID = Regex("[a-z0-9][a-z0-9_-]{0,63}")
    private val SAFE_HASH = Regex("[0-9a-f]{64}")
    private val SAFE_CATEGORY = Regex("[a-zA-Z0-9 &_-]{1,40}")

    /** Refreshes the global registry from installed packs present in the current trusted catalog. */
    suspend fun refreshInstalled(
        context: Context,
        catalog: PackManifestRepo.PackManifestFile? = null
    ): Int = withContext(Dispatchers.IO) {
        val manifest = catalog ?: PackManifestRepo.fetchManifest(context)
        val installedIds = PackManager.installedPackIds(context)
        val presets = manifest.packs
            .filter { SAFE_ID.matches(it.id) && it.category == "filters" && it.payloadType == PAYLOAD_TYPE && it.id in installedIds }
            .flatMap { pack -> loadPackPresets(context, pack) }
        FilterManifest.replaceDownloadedPresets(presets)
        presets.size
    }

    /** Resolve and reverify the installed asset each time it enters a render pipeline. */
    fun loadVerifiedLut(context: Context, preset: FilterPreset): FloatArray? {
        return try {
            val (packId, relativePath) = parseAssetUri(preset.asset) ?: return null
            val root = safePackRoot(context, packId) ?: return null
            if (!PackManager.isDownloaded(context, packId)) return null
            val file = resolveUnder(root, relativePath) ?: return null
            val expectedSize = preset.assetSizeBytes ?: return null
            val expectedHash = preset.assetSha256?.lowercase() ?: return null
            if (expectedSize !in 1L..MAX_CUBE_BYTES || file.length() != expectedSize) return null
            if (!SAFE_HASH.matches(expectedHash)) return null
            val bytes = file.readBytes()
            if (bytes.size.toLong() != expectedSize || sha256(bytes) != expectedHash) return null
            parseCube(bytes)
        } catch (e: Exception) {
            Log.w(TAG, "Downloaded LUT failed verification: ${preset.id}", e)
            null
        }
    }

    private fun loadPackPresets(context: Context, pack: PackManifestRepo.RemotePack): List<FilterPreset> {
        val root = safePackRoot(context, pack.id) ?: return emptyList()
        val entry = resolveUnder(root, pack.entry) ?: return emptyList()
        if (!entry.isFile || entry.length() > 128 * 1024) return emptyList()
        return try {
            val document = JSONObject(entry.readText(Charsets.UTF_8))
            if (document.optInt("schemaVersion") != 1) return emptyList()
            val attribution = document.optString("attribution")
            if (attribution.isBlank() || attribution.length > 300) return emptyList()
            val items = document.optJSONArray("filters") ?: return emptyList()
            if (pack.itemCount > 0 && items.length() != pack.itemCount) return emptyList()
            val seen = mutableSetOf<String>()
            buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val sourceId = item.optString("id")
                    val name = item.optString("name")
                    val category = item.optString("category")
                    val relativePath = item.optString("file")
                    val size = item.optLong("sizeBytes", -1L)
                    val hash = item.optString("sha256").lowercase()
                    if (!SAFE_ID.matches(sourceId) || !seen.add(sourceId)) continue
                    if (name.isBlank() || name.length > 60 || !SAFE_CATEGORY.matches(category)) continue
                    if (size !in 1L..MAX_CUBE_BYTES || !SAFE_HASH.matches(hash)) continue
                    val file = resolveUnder(root, relativePath) ?: continue
                    if (!file.isFile || file.length() != size) continue
                    val bytes = file.readBytes()
                    if (bytes.size.toLong() != size || sha256(bytes) != hash || parseCube(bytes) == null) continue
                    add(
                        FilterPreset(
                            id = "dl_${pack.id}_$sourceId",
                            name = name,
                            category = category,
                            asset = "$ASSET_PREFIX${pack.id}/$relativePath",
                            assetSha256 = hash,
                            assetSizeBytes = size
                        )
                    )
                }
            }.also { Log.i(TAG, "Verified ${it.size}/${items.length()} LUTs from ${pack.id}") }
        } catch (e: Exception) {
            Log.w(TAG, "Invalid LUT pack entry: ${pack.id}/${pack.entry}", e)
            emptyList()
        }
    }

    private fun parseAssetUri(uri: String): Pair<String, String>? {
        if (!uri.startsWith(ASSET_PREFIX)) return null
        val rest = uri.removePrefix(ASSET_PREFIX)
        val slash = rest.indexOf('/')
        if (slash <= 0 || slash == rest.lastIndex) return null
        val packId = rest.substring(0, slash)
        val path = rest.substring(slash + 1)
        if (!SAFE_ID.matches(packId)) return null
        return packId to path
    }

    private fun resolveUnder(root: File, relativePath: String): File? {
        if (relativePath.isBlank() || relativePath.length > 240 || relativePath.startsWith('/') || '\\' in relativePath) return null
        if (relativePath.split('/').any { it.isBlank() || it == "." || it == ".." }) return null
        val canonicalRoot = root.canonicalFile
        val file = File(canonicalRoot, relativePath).canonicalFile
        return file.takeIf { it.path.startsWith(canonicalRoot.path + File.separator) }
    }

    private fun safePackRoot(context: Context, packId: String): File? {
        if (!SAFE_ID.matches(packId)) return null
        val packsRoot = PackManager.packsRoot(context).canonicalFile
        val packRoot = File(packsRoot, packId).canonicalFile
        return packRoot.takeIf { it.parentFile == packsRoot && it.isDirectory }
    }

    private fun parseCube(bytes: ByteArray): FloatArray? {
        if (bytes.isEmpty() || bytes.size > MAX_CUBE_BYTES) return null
        val values = try {
            CubeLutParser.parse(
                BufferedReader(InputStreamReader(ByteArrayInputStream(bytes), Charsets.UTF_8))
            )
        } catch (_: Exception) {
            null
        } ?: return null
        val size = (2..33).firstOrNull { it * it * it * 3 == values.size } ?: return null
        if (values.any { !it.isFinite() || it !in 0f..1f }) return null
        return values
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
