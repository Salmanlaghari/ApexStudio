package com.apexstudio.app.data.stickers

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Bundled sticker library: openly-licensed PNG packs shipped under
 * `assets/stickers/` with a `manifest.json` (file, tags, category).
 *
 * The current pack is Twemoji (CC-BY 4.0) — see
 * `assets/stickers/ATTRIBUTION.txt`. Attribution is shown at the
 * bottom of the sticker picker and must be preserved in the PR that
 * touches these files.
 */
@Serializable
data class StickerManifestEntry(
    val id: String,
    val file: String,
    val name: String,
    val category: String,
    val tags: List<String>
)

@Serializable
private data class StickerManifest(
    val version: Int = 1,
    val pack: String = "",
    val license: String = "",
    val attribution: String = "",
    val stickers: List<StickerManifestEntry> = emptyList()
)

/** One sticker ready for the picker UI. */
data class StickerEntry(
    val id: String,
    /** Relative asset path, e.g. "stickers/love/red-heart.png". */
    val assetUri: String,
    val name: String,
    val category: String,
    val tags: List<String>
) {
    /** `file:///android_asset/...` URI usable by image loaders. */
    val previewUri: String get() = "file:///android_asset/$assetUri"
}

object StickerPack {

    const val ASSETS_DIR = "stickers"
    const val MANIFEST_FILE = "manifest.json"

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parse the manifest from its raw JSON text. Pure function so it is
     * unit-testable without Android assets.
     */
    fun parseManifest(manifestJson: String): List<StickerEntry> {
        val manifest = json.decodeFromString<StickerManifest>(manifestJson)
        return manifest.stickers.map { e ->
            StickerEntry(
                id = e.id,
                assetUri = "$ASSETS_DIR/${e.file}",
                name = e.name,
                category = e.category,
                tags = e.tags
            )
        }
    }

    /** Load the bundled pack from `assets/stickers/manifest.json`. */
    fun load(context: Context): List<StickerEntry> = try {
        val text = context.assets.open("$ASSETS_DIR/$MANIFEST_FILE").use { input ->
            input.bufferedReader().readText()
        }
        parseManifest(text)
    } catch (_: Exception) {
        emptyList()
    }

    /** Distinct category ids in first-seen order. */
    fun categories(entries: List<StickerEntry>): List<String> =
        entries.map { it.category }.distinct()

    /**
     * Tag-based search. A sticker matches when every whitespace-separated
     * query token appears (as a substring) in its name, category, or tags.
     * Empty/blank query returns [entries] unchanged.
     */
    fun search(entries: List<StickerEntry>, query: String): List<StickerEntry> {
        val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return entries
        return entries.filter { entry ->
            val haystack = buildString {
                append(entry.name.lowercase()).append(' ')
                append(entry.category.lowercase()).append(' ')
                entry.tags.forEach { append(it.lowercase()).append(' ') }
            }
            tokens.all { it in haystack }
        }
    }

    /** The attribution line shown in the picker footer and About screen. */
    fun attribution(context: Context): String = try {
        val text = context.assets.open("$ASSETS_DIR/$MANIFEST_FILE").use { input ->
            input.bufferedReader().readText()
        }
        json.decodeFromString<StickerManifest>(text).attribution
            .ifBlank { defaultAttribution() }
    } catch (_: Exception) {
        defaultAttribution()
    }

    fun defaultAttribution(): String =
        "Stickers: Twemoji by Twitter, Inc — CC-BY 4.0"
}
