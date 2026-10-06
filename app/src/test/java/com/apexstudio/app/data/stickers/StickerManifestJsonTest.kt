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
 * Guards `assets/stickers/manifest.json` against phantom entries.
 *
 * [StickerPack.load] builds the sticker picker grid from this manifest.
 * An entry whose PNG asset does not exist shows a permanent loading
 * placeholder in the picker, and a duplicate id breaks grid keys.
 * These tests fail the build if the manifest ever drifts from the
 * bundled assets again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StickerManifestJsonTest {

    private fun loadEntries(context: Context): List<StickerEntry> =
        StickerPack.load(context)

    @Test
    fun `manifest loads a non-empty sticker pack`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entries = loadEntries(context)
        assertTrue(
            "Sticker pack should bundle a usable library (>= 60 stickers), got ${entries.size}",
            entries.size >= 60
        )
    }

    @Test
    fun `every manifest sticker points at an existing png asset`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entries = loadEntries(context)

        val missing = entries.mapNotNull { e ->
            val exists = try {
                context.assets.open(e.assetUri).close()
                true
            } catch (_: Exception) {
                false
            }
            if (!exists) "${e.id} -> ${e.assetUri}" else null
        }

        assertTrue(
            "Manifest entries referencing PNG files missing from assets/stickers/: $missing",
            missing.isEmpty()
        )
    }

    @Test
    fun `manifest has no duplicate sticker ids`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entries = loadEntries(context)
        val duplicates = entries.groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertTrue("Duplicate sticker ids (breaks picker grid keys): $duplicates", duplicates.isEmpty())
    }

    @Test
    fun `every sticker has tags and a category`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entries = loadEntries(context)

        val bad = entries.filter { it.tags.isEmpty() || it.category.isBlank() || it.name.isBlank() }
        assertTrue(
            "Stickers with missing tags/category/name (breaks tag search): ${bad.map { it.id }}",
            bad.isEmpty()
        )
    }

    @Test
    fun `pack spans multiple categories`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entries = loadEntries(context)
        val categories = StickerPack.categories(entries)
        assertTrue(
            "Sticker pack should span several categories, got $categories",
            categories.size >= 5
        )
    }

    @Test
    fun `attribution text is present for the CC-BY 4_0 pack`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val attribution = StickerPack.attribution(context)
        assertTrue(
            "CC-BY 4.0 requires attribution; StickerPack.attribution() must not be blank",
            attribution.isNotBlank()
        )
        assertEquals(
            "Sticker artwork: Twemoji by Twitter, Inc — CC-BY 4.0",
            StickerPack.defaultAttribution()
        )
    }
}
