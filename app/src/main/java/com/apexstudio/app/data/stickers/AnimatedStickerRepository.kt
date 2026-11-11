package com.apexstudio.app.data.stickers

import android.content.Context
import com.apexstudio.app.data.packs.download.PackManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * A single entry of the animated sticker pack (Bug 2).
 *
 * One entry drives both stages of the pipeline: the picker grid renders
 * the animation to preview it, and tapping it resolves `assetUri` into a
 * `StickerOverlay` so the canvas and the exporter load the very same
 * bytes.
 *
 * @property id `"<category>/<file>"`, stable across renames of the label.
 * @property file Path relative to the `stickers/animated/` directory,
 *   e.g. `hearts/beating-heart.json`.
 * @property name Human label shown under the grid cell.
 * @property category One of the pack's categories (hearts, stars, fire,
 *   confetti, emojis, animals) — the picker groups by this.
 * @property tags Search keywords, lower-case.
 * @property searchHaystack pre-built `name + category + tags` string; see
 *   [AnimatedStickerRepository.search].
 */
@Serializable
data class AnimatedStickerEntry(
    val id: String,
    val file: String,
    val name: String,
    val category: String,
    val tags: List<String> = emptyList(),
    val searchHaystack: String = "",
    val packId: String? = null,
    val bytes: Long? = null,
    val sha256: String = "",
    val license: String = "",
    val source: String = "",
    val motifKey: String = ""
)

/** Manifest as read from `animated/manifest.json`. `animations` is the data. */
@Serializable
private data class AnimatedStickerManifest(
    val version: Int = 1,
    val pack: String = "",
    val license: String = "",
    val source: String = "",
    val attribution: String = "",
    val count: Int = 0,
    val animations: List<AnimatedStickerEntry> = emptyList()
)

@Serializable
private data class DownloadedAnimatedStickerManifest(
    val stickers: List<AnimatedStickerEntry> = emptyList()
)

data class DownloadedStickerCatalog(
    val entries: List<AnimatedStickerEntry>,
    val errors: List<String>
)

/**
 * Reads the animated pack shipped under `assets/stickers/animated/`.
 *
 * Why a repository instead of the hard-coded list that previously lived
 * inside `StickerPanel`: the old list held 7 filenames as string
 * literals, so adding a animation meant editing composable code, the
 * picker could not show categories, and there was no place to assert
 * the count or the license. The manifest is now the single source of
 * truth and the UI simply renders whatever it declares.
 *
 * The parse is deliberately exposed as [parse] operating on a plain
 * `String` so unit tests can feed it the real asset bytes (or fixtures)
 * on the JVM without an `android.content.Context`.
 *
 * Loading is lazy and memoised; the pack is immutable APK data, so the
 * cache is never invalidated.
 */
object AnimatedStickerRepository {

    /** Assets directory, relative to `assets/`. */
    const val ASSETS_DIR = "stickers/animated"

    /** Manifest path, relative to `assets/`. */
    const val MANIFEST_PATH = "$ASSETS_DIR/manifest.json"

    /** Prefix for every [AnimatedStickerEntry.file]. */
    private const val ASSET_PREFIX = "$ASSETS_DIR/"

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cached: List<AnimatedStickerEntry>? = null

    /**
     * Load and memoise the pack. Returns an empty list when the asset
     * is missing or unreadable — the picker then shows its own
     * fallback text instead of crashing the editor.
     */
    @Synchronized
    fun entries(context: Context): List<AnimatedStickerEntry> {
        cached?.let { return it }
        return try {
            val raw = context.assets.open(MANIFEST_PATH)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            parse(raw).also { cached = it }
        } catch (e: Exception) {
            // Missing/corrupt manifest must never take down the panel.
            cached = emptyList()
            emptyList()
        }
    }

    /**
     * Parse a manifest body into entries, with [AnimatedStickerEntry]
     * [AnimatedStickerEntry.searchHaystack] pre-computed so per-keystroke
     * searching never rebuilds it.
     *
     * Unknown keys are ignored so the manifest can gain fields (licence,
     * source, attribution) without breaking older readers.
     */
    fun parse(raw: String): List<AnimatedStickerEntry> = try {
        json.decodeFromString<AnimatedStickerManifest>(raw).animations
            .filter { isSafeRelativeFile(it.file) }
            .distinctBy { it.id }
            .map { entry ->
                entry.copy(
                    searchHaystack = buildString {
                        append(entry.name.lowercase())
                        append(' ')
                        append(entry.category.lowercase())
                        entry.tags.forEach { tag -> append(' '); append(tag.lowercase()) }
                    }
                )
            }
    } catch (e: Exception) {
        emptyList()
    }

    /** Parse installed ZIP manifests and expose only files whose pinned bytes/hash still match. */
    suspend fun loadDownloaded(
        context: Context,
        packs: List<PackManager.PackUiItem>
    ): DownloadedStickerCatalog = withContext(Dispatchers.IO) {
        val entries = mutableListOf<AnimatedStickerEntry>()
        val errors = mutableListOf<String>()
        val ready = packs.filter {
            it.pack.category == "stickers" &&
                (it.state == PackManager.PackState.Downloaded ||
                    it.state == PackManager.PackState.UpdateAvailable)
        }
        for (item in ready) {
            val pack = item.pack
            val root = PackManager.packDir(context.applicationContext, pack.id).canonicalFile
            val manifestFile = try {
                File(root, pack.entry).canonicalFile.takeIf {
                    it.path.startsWith(root.path + File.separator) && it.isFile
                }
            } catch (_: Exception) { null }
            if (manifestFile == null) {
                errors += "${pack.name}: pack manifest is missing or outside the install folder."
                continue
            }
            val packEntries = parseDownloaded(manifestFile.readText(), pack.id)
            if (packEntries.isEmpty() || (pack.itemCount > 0 && packEntries.size != pack.itemCount)) {
                errors += "${pack.name}: expected ${pack.itemCount} sticker records, found ${packEntries.size}."
                continue
            }
            var invalidFiles = 0
            packEntries.forEach { entry ->
                val file = StickerAssetResolver.downloadedFile(context, assetUri(entry))
                val expectedBytes = entry.bytes
                val verified = try {
                    file != null && expectedBytes != null && file.length() == expectedBytes &&
                        entry.sha256.matches(Regex("^[a-f0-9]{64}$")) && sha256(file) == entry.sha256
                } catch (_: Exception) { false }
                if (!verified) {
                    invalidFiles++
                } else {
                    entries += entry
                }
            }
            if (invalidFiles > 0) {
                errors += "${pack.name}: $invalidFiles sticker file(s) failed size/SHA-256 verification."
            }
        }
        DownloadedStickerCatalog(entries, errors)
    }

    /** Parse the entry manifest inside one verified ZIP pack. */
    fun parseDownloaded(raw: String, packId: String): List<AnimatedStickerEntry> = try {
        json.decodeFromString<DownloadedAnimatedStickerManifest>(raw).stickers
            .filter { entry ->
                isSafeRelativeFile(entry.file) && entry.bytes != null &&
                    entry.bytes > 0L && entry.sha256.matches(Regex("^[a-f0-9]{64}$"))
            }
            .distinctBy { it.id }
            .map { entry ->
                StickerAssetResolver.packUri(packId, entry.file)
                entry.copy(
                    category = entry.category.lowercase(),
                    packId = packId,
                    searchHaystack = buildString {
                        append(entry.name.lowercase())
                        append(' ')
                        append(entry.category.lowercase())
                        entry.tags.forEach { tag -> append(' '); append(tag.lowercase()) }
                    }
                )
            }
    } catch (_: Exception) {
        emptyList()
    }

    /** Bundled Android-asset path or stable `stickerpack://` path for an installed download. */
    fun assetUri(entry: AnimatedStickerEntry): String = entry.packId?.let {
        StickerAssetResolver.packUri(it, entry.file)
    } ?: "$ASSET_PREFIX${entry.file}"

    private fun isSafeRelativeFile(path: String): Boolean =
        path.isNotBlank() && !path.startsWith('/') && '\\' !in path &&
            path.split('/').all { it.isNotBlank() && it != "." && it != ".." }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    /**
     * Category names in their first-seen manifest order — this is what
     * gives the picker stable section headings instead of a sorted set.
     */
    fun categories(entries: List<AnimatedStickerEntry>): List<String> =
        entries.map { it.category }.distinct()

    /**
     * Case-insensitive search over [AnimatedStickerEntry.searchHaystack].
     * A blank [query] returns [entries] unchanged.
     */
    fun search(entries: List<AnimatedStickerEntry>, query: String): List<AnimatedStickerEntry> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return entries
        return entries.filter { it.searchHaystack.contains(needle) }
    }
}
