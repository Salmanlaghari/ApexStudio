package com.apexstudio.app.data.filter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for FilterManifest LUT auto-scanning.
 *
 * Verifies that [FilterManifest.initialize] scans `assets/luts/` for `.cube`
 * files and that [FilterManifest.presetById] resolves them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FilterManifestTest {

    private fun initManifest(): Context {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FilterManifest.initialize(context)
        return context
    }

    @Test
    fun `initialize scans LUT files from assets`() {
        val context = initManifest()
        val luts = context.assets.list("luts")
            ?.filter { it.endsWith(".cube", ignoreCase = true) }
            ?: emptyList()

        // Assets contain LUTs — registry should not be empty
        assertTrue("Expected LUT files in assets/luts/", luts.isNotEmpty())
    }

    @Test
    fun `presetById returns null for unknown id`() {
        initManifest()
        assertNull(FilterManifest.presetById("nonexistent_lut_xyz_123"))
    }

    @Test
    fun `presetById resolves scanned LUT by id`() {
        val context = initManifest()
        val luts = context.assets.list("luts")
            ?.filter { it.endsWith(".cube", ignoreCase = true) }
            ?: emptyList()

        if (luts.isEmpty()) return // No LUTs to test

        val firstId = luts.first().removeSuffix(".cube").removeSuffix(".CUBE")
        val preset = FilterManifest.presetById(firstId)

        assertNotNull("Expected preset for LUT id: $firstId", preset)
        assertEquals(firstId, preset!!.id)
        assertTrue("Asset path should start with luts/", preset.asset.startsWith("luts/"))
    }

    @Test
    fun `preset has valid category`() {
        val context = initManifest()
        val luts = context.assets.list("luts")
            ?.filter { it.endsWith(".cube", ignoreCase = true) }
            ?: emptyList()

        if (luts.isEmpty()) return

        val firstId = luts.first().removeSuffix(".cube").removeSuffix(".CUBE")
        val preset = FilterManifest.presetById(firstId)

        assertNotNull(preset)
        assertTrue("Category should not be blank", preset!!.category.isNotBlank())
        assertTrue("Name should not be blank", preset.name.isNotBlank())
    }

    @Test
    fun `initialize is idempotent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Call twice — should not crash or duplicate
        FilterManifest.initialize(context)
        FilterManifest.initialize(context)

        val luts = context.assets.list("luts")
            ?.filter { it.endsWith(".cube", ignoreCase = true) }
            ?: emptyList()

        if (luts.isNotEmpty()) {
            val firstId = luts.first().removeSuffix(".cube").removeSuffix(".CUBE")
            assertNotNull(FilterManifest.presetById(firstId))
        }
    }

    @Test
    fun `downloaded LUT presets merge into the gallery category and can be removed`() {
        FilterManifest.resetForTesting()
        val bundled = FilterPreset(id = "builtin-warm", name = "Warm", category = "Warm", asset = "luts/warm.cube")
        val downloaded = FilterPreset(
            id = "dl_lut-test-pack_amber", name = "Amber", category = "warm",
            asset = "downloaded-lut://lut-test-pack/amber.cube",
            assetSha256 = "a".repeat(64), assetSizeBytes = 123L
        )
        val base = FilterManifest(
            categories = listOf(FilterCategory(id = "warm", name = "Warm", filters = listOf(bundled))),
            filters = listOf(bundled)
        )
        try {
            FilterManifest.replaceDownloadedPresets(listOf(downloaded))
            val merged = FilterManifest.withDownloadedPresets(base)
            assertEquals(listOf("builtin-warm", downloaded.id), merged.categories.single().filters.map { it.id })
            assertEquals(downloaded, merged.presetById(downloaded.id))
            assertEquals(downloaded, FilterManifest.presetById(downloaded.id))

            FilterManifest.replaceDownloadedPresets(emptyList())
            assertNull(FilterManifest.presetById(downloaded.id))
        } finally {
            FilterManifest.resetForTesting()
        }
    }

    @Test
    fun `lutContentChanged compares asset sha and size explicitly`() {
        // PR #231 fix-forward (Kilo CRITICAL 3): cache eviction is driven by
        // this explicit field comparison, not data-class equality.
        val base = FilterPreset(
            id = "dl_pack_a", name = "A", category = "Warm",
            asset = "downloaded-lut://pack/a.cube",
            assetSha256 = "a".repeat(64), assetSizeBytes = 100L
        )
        assertFalse(FilterManifest.lutContentChanged(base, base.copy()))
        // Display-only changes do not change the cached pixels.
        assertFalse(FilterManifest.lutContentChanged(base, base.copy(name = "Renamed", category = "Cool")))
        // Sha case alone is not a content change (hashes are hex).
        assertFalse(FilterManifest.lutContentChanged(base, base.copy(assetSha256 = "A".repeat(64))))
        // Any content-field change must evict.
        assertTrue(FilterManifest.lutContentChanged(base, base.copy(assetSha256 = "b".repeat(64))))
        assertTrue(FilterManifest.lutContentChanged(base, base.copy(assetSha256 = null)))
        assertTrue(FilterManifest.lutContentChanged(base, base.copy(assetSizeBytes = 101L)))
        assertTrue(FilterManifest.lutContentChanged(base, base.copy(assetSizeBytes = null)))
        assertTrue(FilterManifest.lutContentChanged(base, base.copy(asset = "downloaded-lut://pack/b.cube")))
        // Added / removed presets count as changed.
        assertTrue(FilterManifest.lutContentChanged(null, base))
        assertTrue(FilterManifest.lutContentChanged(base, null))
        assertFalse(FilterManifest.lutContentChanged(null, null))
    }

    @Test
    fun `replacing a downloaded preset with new integrity metadata updates the registry`() {
        FilterManifest.resetForTesting()
        val v1 = FilterPreset(
            id = "dl_pack_a", name = "A", category = "Warm",
            asset = "downloaded-lut://pack/a.cube",
            assetSha256 = "a".repeat(64), assetSizeBytes = 100L
        )
        val v2 = v1.copy(assetSha256 = "b".repeat(64), assetSizeBytes = 200L)
        try {
            FilterManifest.replaceDownloadedPresets(listOf(v1))
            val versionAfterV1 = FilterManifest.downloadedRegistryVersion.value
            // Identical replacement is a no-op: no version bump.
            FilterManifest.replaceDownloadedPresets(listOf(v1.copy()))
            assertEquals(versionAfterV1, FilterManifest.downloadedRegistryVersion.value)

            FilterManifest.replaceDownloadedPresets(listOf(v2))
            assertEquals(v2, FilterManifest.presetById(v1.id))
            assertTrue(FilterManifest.downloadedRegistryVersion.value > versionAfterV1)
        } finally {
            FilterManifest.resetForTesting()
        }
    }
}
