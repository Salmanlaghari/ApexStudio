package com.apexstudio.app.data.stickers

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer
import java.util.Locale

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
    /** Relative asset path, e.g. "stickers/love/red-heart.webp". */
    val assetUri: String,
    val name: String,
    val category: String,
    val tags: List<String>,
    /**
     * Normalized "name + category + tags" string, pre-computed once in
     * [StickerPack.parseManifest] so [StickerPack.search] doesn't rebuild
     * it for every entry on every keystroke.
     */
    val searchHaystack: String = ""
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
                tags = e.tags,
                searchHaystack = buildString {
                    append(normalizeSearchText(e.name)).append(' ')
                    append(normalizeSearchText(e.category)).append(' ')
                    e.tags.forEach { append(normalizeSearchText(it)).append(' ') }
                }
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
        val tokens = normalizeSearchText(query).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return entries
        return entries.filter { entry ->
            tokens.all { it in entry.searchHaystack }
        }
    }

    /** Locale-independent search folding for Latin and common Arabic-script variants. */
    private fun normalizeSearchText(value: String): String {
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        return buildString(normalized.length) {
            normalized.forEach { character ->
                when (character) {
                    '\u0640' -> Unit // Arabic tatweel is decorative, not searchable.
                    '\u0622', '\u0623', '\u0625', '\u0671' -> append('\u0627') // alef forms
                    '\u0649' -> append('\u064a') // alef maqsura / ya
                    '\u06cc' -> append('\u064a') // Persian/Urdu yeh
                    '\u06a9' -> append('\u0643') // Persian/Urdu kaf
                    else -> if (Character.getType(character) != Character.NON_SPACING_MARK.toInt() &&
                        Character.getType(character) != Character.COMBINING_SPACING_MARK.toInt() &&
                        Character.getType(character) != Character.ENCLOSING_MARK.toInt()
                    ) append(character)
                }
            }
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
        "Emoji artwork from Twemoji (https://twemoji.twitter.com), Copyright 2020 Twitter, Inc and other contributors, licensed under CC-BY 4.0 (https://creativecommons.org/licenses/by/4.0/)"
}
