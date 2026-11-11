package com.apexstudio.app.data.template

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.apexstudio.app.data.fx.FxPreset
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
 * Templates are graded at runtime through [FilterManifest.presetById] -- the
 * static registry scanned from `assets/luts/[*].cube`. Template export now
 * rejects an unresolved active filter instead of silently dropping its grade;
 * the bundled catalog should still reference only presets in the registry.
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
            "Templates referencing unknown LUTs (unavailable for export): $unresolved",
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

    @Test
    fun `shorts looks use supported LUTs and FX without synthetic royalty music`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FilterManifest.initialize(context)
        val shortsLooks = ShortsTemplateCatalog.asTransmissionTemplates()
        val invalid = shortsLooks.filter { look ->
            !look.shortsOnly ||
                FilterManifest.presetById(look.filterId) == null ||
                FxPreset.byId(look.fxPresetId) == null ||
                look.royaltyTrackId.isNotBlank() ||
                look.musicTitle.isNotBlank()
        }

        assertEquals(10, shortsLooks.size)
        assertTrue("Invalid Shorts looks: ${invalid.map { it.id }}", invalid.isEmpty())
    }

    @Test
    fun `music manifest validates and covers every Shorts recipe`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val raw = context.assets.open(ShortsMusicRepository.MANIFEST_ASSET).use { input ->
            input.bufferedReader().readText()
        }
        val manifest = ShortsMusicRepository.parseAndValidateManifest(raw)
        val manifestIds = manifest.assets.map { it.id }.toSet()

        assertEquals(10, manifest.assets.size)
        assertTrue(
            "Every music credit should disclose template edits",
            manifest.assets.all { it.changes.isNotBlank() && "Changes: ${it.changes}" in it.creditText }
        )
        assertTrue(
            "Missing music assets for recipes",
            ShortsTemplateCatalog.templates.all { it.musicAssetId in manifestIds }
        )
    }

    @Test
    fun `template export rejects an unsupported active LUT with an actionable error`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FilterManifest.initialize(context)
        val template = TimelineTemplate(
            id = "unknown-lut",
            name = "Unknown LUT test",
            clips = listOf(TemplateClip(
                id = "clip-1",
                name = "Sunset shot",
                uri = "file:///missing-test-video.mp4",
                durationMs = 1_000L
            )),
            filters = listOf(TemplateFilter(
                clipId = "clip-1",
                filterId = "not-in-the-lut-manifest",
                intensity = 0.8f
            ))
        )

        val failure = runCatching {
            TimelineTemplateManager(context).mapTemplateToComposition(template)
        }.exceptionOrNull()

        assertNotNull("Unsupported template LUT must stop composition creation", failure)
        assertTrue("Failure should be an actionable validation error", failure is IllegalArgumentException)
        val message = failure?.message.orEmpty()
        assertTrue(message.contains("not-in-the-lut-manifest"))
        assertTrue(message.contains("Sunset shot"))
        assertTrue(message.contains("choose a supported filter or remove it"))
    }
}
