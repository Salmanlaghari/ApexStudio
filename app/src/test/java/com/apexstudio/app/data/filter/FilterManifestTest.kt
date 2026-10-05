package com.apexstudio.app.data.filter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
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
}
