package com.apexstudio.app.data.stickers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards `assets/stickers/animated/manifest.json` (Bug 2 — animated
 * sticker pack) the same way [StickerManifestJsonTest] guards the static
 * pack.
 *
 * The task the pack must satisfy: >= 20 animated stickers, every file
 * present on disk, unique ids, and the six required themes. A manifest
 * entry whose Lottie JSON is missing renders an empty cell in the
 * picker, and a duplicate id breaks the `LazyVerticalGrid` key.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnimatedStickerRepositoryTest {

    private fun entries(context: Context): List<AnimatedStickerEntry> =
        AnimatedStickerRepository.entries(context)

    @Test
    fun `pack bundles at least twenty animations`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entries = entries(context)
        assertTrue(
            "Animated pack must hold >= 20 stickers (Bug 2), got ${entries.size}",
            entries.size >= 20
        )
    }

    @Test
    fun `every animation points at an existing lottie json`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val missing = entries(context).mapNotNull { e ->
            val exists = try {
                context.assets.open(AnimatedStickerRepository.assetUri(e)).close()
                true
            } catch (_: Exception) {
                false
            }
            if (!exists) "${e.id} -> ${AnimatedStickerRepository.assetUri(e)}" else null
        }
        assertTrue("Animated files missing from assets/: $missing", missing.isEmpty())
    }

    @Test
    fun `animation files are lottie json not the static webp packs`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entries = entries(context)
        val bad = entries.filter { !it.file.endsWith(".json") || it.file.endsWith(".webp") }
        assertTrue("Non-Lottie entries (would fall through to the image path): $bad", bad.isEmpty())
        entries.forEach {
            assertTrue(
                "${it.file} must resolve under stickers/animated/",
                AnimatedStickerRepository.assetUri(it).startsWith("stickers/animated/")
            )
        }
    }

    @Test
    fun `animation ids are unique`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val duplicates = entries(context)
            .groupBy { it.id }
            .filterValues { it.size > 1 }
            .keys
        assertTrue("Duplicate animation ids (breaks grid keys): $duplicates", duplicates.isEmpty())
    }

    @Test
    fun `pack covers every required theme`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val categories = AnimatedStickerRepository.categories(entries(context)).toSet()
        val required = setOf("hearts", "stars", "fire", "confetti", "emojis", "animals")
        assertTrue(
            "Every required theme must be present. missing=${required - categories}, got=$categories",
            categories.containsAll(required)
        )
    }

    @Test
    fun `every animation has a name a category and tags`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bad = entries(context).filter { it.name.isBlank() || it.category.isBlank() || it.tags.isEmpty() }
        assertTrue("Incomplete entries (breaks search): ${bad.map { it.id }}", bad.isEmpty())
    }

    @Test
    fun `categories expose every entry exactly once`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entries = entries(context)
        val categories = AnimatedStickerRepository.categories(entries)
        val total = categories.sumOf { cat -> entries.count { it.category == cat } }
        assertEquals("Category partition must cover the whole pack", entries.size, total)
        assertEquals("categories must not repeat", categories.size, categories.distinct().size)
    }

    @Test
    fun `search matches tags names and categories`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entries = entries(context)
        assertNotNullOrBlankQuery(entries)
        val byTag = AnimatedStickerRepository.search(entries, "sparkle")
        assertTrue("tag search must return >= 1 entry", byTag.size >= 1)
        val byCase = AnimatedStickerRepository.search(entries, "KITTY")
        assertTrue("search must be case insensitive", byCase.isNotEmpty())
        val blank = AnimatedStickerRepository.search(entries, "   ")
        assertEquals("blank query returns everything", entries.size, blank.size)
        val nonsense = AnimatedStickerRepository.search(entries, "zzzz-no-such-tag")
        assertTrue("unknown query returns nothing", nonsense.isEmpty())
    }

    private fun assertNotNullOrBlankQuery(entries: List<AnimatedStickerEntry>) {
        assertTrue("fixture precondition: pack must not be empty", entries.isNotEmpty())
    }

    @Test
    fun `parse is lenient about unreadable manifests`() {
        assertTrue(AnimatedStickerRepository.parse("{ not json").isEmpty())
        assertTrue(AnimatedStickerRepository.parse("{}").isEmpty())
        assertTrue(AnimatedStickerRepository.parse("").isEmpty())
    }

    @Test
    fun `parse drops duplicate ids and blank files`() {
        val raw = """{"version":1,"animations":[
            {"id":"a/x","file":"a/x.json","name":"X","category":"a","tags":["t1"]},
            {"id":"a/x","file":"a/x.json","name":"X","category":"a","tags":["t1"]},
            {"id":"a/y","file":"","name":"Y","category":"a","tags":["t2"]}
        ]}"""
        val parsed = AnimatedStickerRepository.parse(raw)
        assertEquals("duplicate id must collapse; blank file dropped", 1, parsed.size)
        assertEquals("a/x", parsed.single().id)
    }

    @Test
    fun `parse precomputes the search haystack`() {
        val raw = """{"version":1,"animations":[
            {"id":"a/x","file":"a/x.json","name":"Heartworm","category":"hearts","tags":["Love","DIAGRAM"]}
        ]}"""
        val parsed = AnimatedStickerRepository.parse(raw).single()
        assertEquals(
            "name + category + every tag, all lower-case",
            "heartworm hearts love diagram",
            parsed.searchHaystack
        )
        assertTrue(parsed.searchHaystack.contains("hearts"))
    }
}
