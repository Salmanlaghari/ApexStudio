package com.apexstudio.app.data.stickers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Unit tests for the sticker library's tag-based search and manifest
 * parsing. Runs on Robolectric only because the module's unit-test
 * source set is configured for it; no Android APIs are touched here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StickerPackSearchTest {

    private val sampleJson = """
        {
          "version": 1,
          "pack": "Test",
          "license": "CC-BY 4.0",
          "attribution": "Test attribution",
          "stickers": [
            {"id": "red-heart", "file": "love/red-heart.png", "name": "Red Heart", "category": "love", "tags": ["love", "heart", "red", "romantic"]},
            {"id": "fire", "file": "fire/fire.png", "name": "Fire", "category": "fire", "tags": ["fire", "hot", "flame", "lit"]},
            {"id": "crying-face", "file": "sad/crying-face.png", "name": "Crying Face", "category": "sad", "tags": ["sad", "cry", "tear"]}
          ]
        }
    """.trimIndent()

    private val entries get() = StickerPack.parseManifest(sampleJson)

    @Test
    fun `parseManifest maps file to asset uri`() {
        val entries = entries
        assertEquals(3, entries.size)
        assertEquals("stickers/love/red-heart.png", entries[0].assetUri)
        assertEquals("file:///android_asset/stickers/love/red-heart.png", entries[0].previewUri)
    }

    @Test
    fun `blank query returns everything`() {
        assertEquals(3, StickerPack.search(entries, "").size)
        assertEquals(3, StickerPack.search(entries, "   ").size)
    }

    @Test
    fun `single token matches tags case-insensitively`() {
        val results = StickerPack.search(entries, "LOVE")
        assertEquals(1, results.size)
        assertEquals("red-heart", results[0].id)
    }

    @Test
    fun `token matches name and category too`() {
        assertEquals(1, StickerPack.search(entries, "crying").size)
        assertEquals(1, StickerPack.search(entries, "sad").size)
    }

    @Test
    fun `multiple tokens narrow with AND semantics`() {
        // "red" alone matches red-heart; adding "fire" (absent) excludes it.
        assertEquals(1, StickerPack.search(entries, "red").size)
        assertTrue(StickerPack.search(entries, "red fire").isEmpty())
        assertEquals(1, StickerPack.search(entries, "heart romantic").size)
    }

    @Test
    fun `substring matching finds partial words`() {
        assertEquals("fire", StickerPack.search(entries, "fla").single().id)
    }

    @Test
    fun `unknown token returns empty`() {
        assertTrue(StickerPack.search(entries, "zebra").isEmpty())
    }

    @Test
    fun `categories preserves first-seen order`() {
        assertEquals(listOf("love", "fire", "sad"), StickerPack.categories(entries))
    }

    @Test
    fun `search supports localized 3d tags without breaking existing packs`() {
        val mixedPacks = StickerPack.parseManifest(
            """{"stickers":[
                {"id":"fluent3d-red-heart","file":"3d/red-heart.png","name":"3D Red Heart","category":"3d","tags":["dil","heart","دل","3d"]},
                {"id":"twemoji-fire","file":"fire/fire.webp","name":"Fire","category":"fire","tags":["fire","hot","flame"]},
                {"id":"animated-heart","file":"animated/heart.json","name":"Heart","category":"animated","tags":["heart","love"]}
            ]}"""
        )

        assertEquals("fluent3d-red-heart", StickerPack.search(mixedPacks, "dil").single().id)
        assertEquals("fluent3d-red-heart", StickerPack.search(mixedPacks, "دل").single().id)
        assertEquals("twemoji-fire", StickerPack.search(mixedPacks, "flame").single().id)
        assertEquals("animated-heart", StickerPack.search(mixedPacks, "love").single().id)
    }

    @Test
    fun `search uses locale root for case folding`() {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("fire", StickerPack.search(entries, "FIRE").single().id)
        } finally {
            Locale.setDefault(previousLocale)
        }
    }
}
