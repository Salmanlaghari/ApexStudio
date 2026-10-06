package com.apexstudio.app.ui.components.templates

import android.content.Context
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.apexstudio.app.data.template.TemplateArtCatalog
import com.apexstudio.app.data.template.TransmissionTemplate
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Render smoke test for the redesigned template previews.
 *
 * Draws every catalog template's artwork — both the static picker tile
 * ([TemplateArtwork]) and the animated dashboard header
 * ([TemplateArtHeader]) — and fails if any of the 33 throws during
 * composition or draw. This is the "apply still renders" gate for the
 * template redesign: the art paths must never crash on any template.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TemplateArtworkSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private data class CatalogEntry(val id: String, val name: String, val accentArgb: Long)

    private fun catalogEntries(context: Context): List<CatalogEntry> {
        val json = context.assets.open("transmission_templates.json").use { input ->
            input.bufferedReader().readText()
        }
        val arr = JSONObject(json).getJSONArray("templates")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            CatalogEntry(
                id = o.getString("id"),
                name = o.getString("name"),
                accentArgb = o.getLong("previewAccentArgb"),
            )
        }
    }

    private fun stubTemplate(entry: CatalogEntry) = TransmissionTemplate(
        id = entry.id,
        name = entry.name,
        previewAccentArgb = entry.accentArgb,
        filterId = "teal_orange",
        fxPresetId = "vignette",
        transitionType = "cross",
    )

    @Test
    fun `every template artwork tile renders without crashing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entries = catalogEntries(context)
        assert(entries.isNotEmpty()) { "Template catalog is empty" }

        entries.forEach { entry ->
            val spec = TemplateArtCatalog.artFor(entry.id)
            val accent = Color(entry.accentArgb.toULong().toLong())
            composeRule.setContent {
                TemplateArtwork(
                    spec = spec,
                    accent = accent,
                    modifier = Modifier.size(76.dp),
                    phase = 0f,
                )
            }
            composeRule.waitForIdle()
        }
    }

    @Test
    fun `every template dashboard header renders without crashing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entries = catalogEntries(context)
        assert(entries.isNotEmpty()) { "Template catalog is empty" }

        entries.forEach { entry ->
            composeRule.setContent {
                TemplateArtHeader(
                    template = stubTemplate(entry),
                    modifier = Modifier
                        .width(220.dp)
                        .height(104.dp),
                )
            }
            composeRule.waitForIdle()
        }
    }

    @Test
    fun `every template artwork renders at each animation phase`() {
        // Catches phase-dependent draw math (divisions, trig) blowing up
        // mid-animation for any archetype.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entries = catalogEntries(context)

        entries.forEach { entry ->
            val spec = TemplateArtCatalog.artFor(entry.id)
            val accent = Color(entry.accentArgb.toULong().toLong())
            listOf(0f, 0.25f, 0.5f, 0.75f, 0.999f).forEach { phase ->
                composeRule.setContent {
                    TemplateArtwork(
                        spec = spec,
                        accent = accent,
                        modifier = Modifier.size(220.dp, 104.dp),
                        phase = phase,
                    )
                }
            }
            composeRule.waitForIdle()
        }
    }
}
