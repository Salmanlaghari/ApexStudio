package com.apexstudio.app.data.template

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.apexstudio.app.data.filter.FilterManifest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the LUT ids referenced by `assets/transmission_templates.json`.
 *
 * Templates are graded at runtime through [FilterManifest.presetById] — the
 * static registry scanned from `assets/luts/*.cube`. A template whose
 * `filterId` does not resolve there silently loses its signature grade when
 * applied (see `TimelineTemplateManager.mapTemplateToComposition`), so every
 * id referenced by the catalog must exist in the scanned registry.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransmissionTemplateLutTest {

    private fun templateFilterIds(context: Context): List<Pair<String, String>> {
        val json = context.assets.open("transmission_templates.json").use { input ->
            input.bufferedReader().readText()
        }
        val arr = JSONObject(json).getJSONArray("templates")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            o.getString("id") to o.getString("filterId")
        }
    }

    @Test
    fun `every template filterId resolves to a scanned LUT preset`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FilterManifest.initialize(context)

        val unresolved = templateFilterIds(context).filter { (_, filterId) ->
            FilterManifest.presetById(filterId) == null
        }

        assertTrue(
            "Templates referencing unknown LUTs (grade silently dropped at apply time): $unresolved",
            unresolved.isEmpty()
        )
    }

    @Test
    fun `resolved presets point at real cube assets`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FilterManifest.initialize(context)
        val lutFiles = context.assets.list("luts")?.toSet() ?: emptySet()

        for ((templateId, filterId) in templateFilterIds(context)) {
            val preset = FilterManifest.presetById(filterId)
            assertNotNull(
                "No preset for template '$templateId' filterId '$filterId'",
                preset
            )
            val fileName = preset!!.asset.substringAfterLast("/")
            assertTrue(
                "Template '$templateId' references LUT asset '${preset.asset}' " +
                    "which is not present in assets/luts/",
                fileName in lutFiles
            )
        }
    }

    @Test
    fun `template catalog parses with zero skipped entries`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FilterManifest.initialize(context)
        val manager = TimelineTemplateManager(context)
        val raw = manager.loadTransmissionTemplatesRaw()
        val result = manager.parseTransmissionTemplatesWithStats(raw)

        assertEquals(
            "Skipped templates: ${result.skippedIds}",
            0,
            result.skippedCount
        )
        assertTrue(
            "Expected the full template catalog, got ${result.templates.size}",
            result.templates.isNotEmpty()
        )
    }
}
