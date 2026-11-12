package com.apexstudio.app.data.background

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.apexstudio.app.data.packs.download.PackManager
import com.apexstudio.app.data.packs.download.PackManifestRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

@Serializable
data class WallpaperCatalog(
    val schemaVersion: Int = 1,
    val packId: String,
    val packVersion: Int,
    val license: String,
    val wallpapers: List<WallpaperAsset>
)

@Serializable
data class WallpaperAsset(
    val id: String,
    val name: String,
    val category: String,
    val assetPath: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val sha256: String,
    val license: String
)

data class InstalledWallpaper(
    val asset: WallpaperAsset,
    val file: File
) {
    val uri: Uri get() = Uri.fromFile(file)
}

/**
 * Reads the small serializable catalog bundled with the app and resolves only
 * wallpaper files from its verified on-demand pack. The archive checksum is
 * checked by DownloadEngine; this repository also pins every extracted image
 * so a removed, replaced, or damaged file can never be selected or processed.
 */
object WallpaperCatalogRepository {
    const val PACK_ID = "apexstudio-wallpapers-vol-01"
    const val PAYLOAD_TYPE = "wallpaper_images_v1"
    private const val CATALOG_ASSET = "backgrounds/wallpapers.json"
    private const val PACK_ENTRY = "manifest.json"

    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    fun bundledCatalog(context: Context): WallpaperCatalog =
        context.assets.open(CATALOG_ASSET).bufferedReader(Charsets.UTF_8).use {
            json.decodeFromString<WallpaperCatalog>(it.readText())
        }

    /** Called by pack installation before PackManager marks the archive downloaded. */
    fun validateInstalledPack(
        context: Context,
        pack: PackManifestRepo.RemotePack
    ): Boolean = runCatching {
        if (pack.id != PACK_ID || pack.category != "backgrounds" || pack.payloadType != PAYLOAD_TYPE) {
            return false
        }
        val bundled = bundledCatalog(context)
        require(bundled.schemaVersion == 1 && bundled.packId == pack.id && bundled.packVersion == pack.version)
        require(bundled.wallpapers.isNotEmpty() && bundled.wallpapers.size == pack.itemCount)
        require(bundled.wallpapers.map { it.id }.distinct().size == bundled.wallpapers.size)
        require(bundled.wallpapers.map { it.assetPath }.distinct().size == bundled.wallpapers.size)

        val root = PackManager.packDir(context, pack.id)
        val installedCatalog = safeEntryFile(root, pack.entry.ifBlank { PACK_ENTRY })
            ?: return false
        require(installedCatalog.isFile)
        require(pack.entrySha256.isNotBlank() && sha256(installedCatalog) == pack.entrySha256.lowercase())
        val extracted = installedCatalog.inputStream().bufferedReader(Charsets.UTF_8).use {
            json.decodeFromString<WallpaperCatalog>(it.readText())
        }
        require(extracted == bundled) { "Installed wallpaper catalog differs from the app-pinned catalog." }
        bundled.wallpapers.all { asset -> validateAssetFile(root, asset) }
    }.getOrDefault(false)

    /** Return only files whose pack is installed and whose bytes still match their catalog pins. */
    suspend fun installedWallpapers(context: Context): List<InstalledWallpaper> = withContext(Dispatchers.IO) {
        runCatching {
            val catalog = bundledCatalog(context)
            if (!PackManager.isDownloaded(context, catalog.packId)) return@withContext emptyList()
            val root = PackManager.packDir(context, catalog.packId)
            catalog.wallpapers.mapNotNull { asset ->
                val file = safeEntryFile(root, asset.assetPath) ?: return@mapNotNull null
                if (validateAssetFile(root, asset)) InstalledWallpaper(asset, file) else null
            }
        }.getOrDefault(emptyList())
    }

    /** Resolve and re-hash one entry immediately before it is used for processing. */
    fun resolveInstalledWallpaper(context: Context, wallpaperId: String): InstalledWallpaper? = runCatching {
        val catalog = bundledCatalog(context)
        if (!PackManager.isDownloaded(context, catalog.packId)) return null
        val asset = catalog.wallpapers.firstOrNull { it.id == wallpaperId } ?: return null
        val root = PackManager.packDir(context, catalog.packId)
        val file = safeEntryFile(root, asset.assetPath) ?: return null
        if (!validateAssetFile(root, asset)) return null
        InstalledWallpaper(asset, file)
    }.getOrNull()

    /** Map picker IDs shipped by older versions to stable catalog IDs. */
    fun resolveLegacyId(value: String?): String? = when (value) {
        "cyber_portal", "studio_neon" -> "cyber-portal"
        "neon_city", "sci_fi" -> "neon-city"
        "virtual_studio", "cinema_gradient" -> "virtual-studio"
        "deep_space" -> "deep-space"
        "matrix_grid" -> "matrix-grid"
        "blur_bokeh" -> "cinema-bokeh"
        else -> value?.takeIf { it in KNOWN_IDS }
    }

    private val KNOWN_IDS = setOf(
        "cyber-portal", "neon-city", "virtual-studio", "deep-space", "matrix-grid",
        "aurora-borealis", "ocean-dusk", "sandstone-sunrise", "cinema-bokeh", "rose-gold"
    )

    private fun validateAssetFile(root: File, asset: WallpaperAsset): Boolean {
        if (asset.id.isBlank() || asset.name.isBlank() || asset.width <= 0 || asset.height <= 0 ||
            asset.sizeBytes <= 0L || !asset.sha256.matches(Regex("[0-9a-fA-F]{64}"))
        ) return false
        val file = safeEntryFile(root, asset.assetPath) ?: return false
        if (!file.isFile || file.length() != asset.sizeBytes || sha256(file) != asset.sha256.lowercase()) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        return bounds.outWidth == asset.width && bounds.outHeight == asset.height
    }

    private fun safeEntryFile(root: File, relative: String): File? {
        if (relative.isBlank() || relative.startsWith('/') || '\\' in relative) return null
        if (relative.split('/').any { it.isBlank() || it == "." || it == ".." }) return null
        val base = root.canonicalFile
        val entry = File(base, relative).canonicalFile
        return entry.takeIf { it.path.startsWith(base.path + File.separator) }
    }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.inputStream().use { it.readBytes() })
        .joinToString("") { "%02x".format(it) }
}
