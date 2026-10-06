package com.apexstudio.app.data.template

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the template visual-identity system.
 *
 * The redesign promise is that every catalog template gets a genuinely
 * distinct preview (unique layout geometry, not a color swap), so these
 * tests pin:
 * 1. every id in `assets/transmission_templates.json` has an explicit
 *    art spec (no silent [TemplateArtCatalog.DEFAULT] fallbacks), and
 * 2. no two catalog templates share the same [TemplateArtArchetype].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TemplateArtSpecTest {

    private fun catalogIds(context: Context): List<String> {
        val json = context.assets.open("transmission_templates.json").use { input ->
            input.bufferedReader().readText()
        }
        val arr = JSONObject(json).getJSONArray("templates")
        return (0 until arr.length()).map { arr.getJSONObject(it).getString("id") }
    }

    @Test
    fun `every catalog template has an explicit art spec`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ids = catalogIds(context)
        assertTrue("Template catalog is empty", ids.isNotEmpty())

        val missing = ids.filter { it !in TemplateArtCatalog.knownIds() }
        assertTrue(
            "Templates without an explicit art spec (would fall back to DEFAULT): $missing",
            missing.isEmpty()
        )
    }

    @Test
    fun `no two catalog templates share an archetype`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ids = catalogIds(context)

        val byArchetype = ids.groupBy { TemplateArtCatalog.artFor(it).archetype }
        val dupes = byArchetype.filterValues { it.size > 1 }
        assertTrue(
            "Archetypes shared by multiple templates (previews would look samey): $dupes",
            dupes.isEmpty()
        )
    }

    @Test
    fun `unknown template id falls back to default spec`() {
        val spec = TemplateArtCatalog.artFor("no_such_template_xyz")
        assertEquals(TemplateArtCatalog.DEFAULT, spec)
        assertNotNull(spec.archetype)
        assertNotNull(spec.namePlacement)
        assertNotNull(spec.motionStyle)
    }

    @Test
    fun `art catalog covers the whole archetype set without gaps`() {
        // Sanity: the enum and the catalog stay in sync — every archetype
        // should be reachable so no draw branch rots untested.
        val used = TemplateArtCatalog.knownIds()
            .filter { it != "original" }
            .map { TemplateArtCatalog.artFor(it).archetype }
            .toSet()
        val unused = TemplateArtArchetype.entries.toSet() - used
        assertTrue(
            "Archetypes with no template assigned (dead draw code): $unused",
            unused.isEmpty()
        )
    }
}
