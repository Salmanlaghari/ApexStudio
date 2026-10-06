package com.apexstudio.app.data.filter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards `assets/luts/filter_manifest.json` against phantom entries.
 *
 * [LutFilterEngine.loadManifest] builds the filter gallery from this JSON.
 * An entry whose `.cube` asset does not exist shows up in the UI but can
 * never apply a real LUT (it silently falls back to a color matrix), and a
 * category id that references a missing filter id crashes manifest parsing
 * (`first {}` throws). These tests fail the build if the manifest ever
 * drifts from the bundled assets again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FilterManifestJsonTest {

    private fun manifestJson(context: Context): JSONObject {
        val text = context.assets.open("luts/filter_manifest.json").use { input ->
            input.bufferedReader().readText()
        }
        return JSONObject(text)
    }

    @Test
    fun `every manifest filter points at an existing cube asset`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val lutFiles = context.assets.list("luts")?.toSet() ?: emptySet()
        val filters = manifestJson(context).getJSONArray("filters")

        val missing = (0 until filters.length()).mapNotNull { i ->
            val o = filters.getJSONObject(i)
            val id = o.getString("id")
            val fileName = o.getString("asset").substringAfterLast("/")
            if (fileName !in lutFiles) "$id -> ${o.getString("asset")}" else null
        }

        assertTrue(
            "Manifest entries referencing .cube files missing from assets/luts/: $missing",
            missing.isEmpty()
        )
    }

    @Test
    fun `every category filter id resolves to a manifest filter`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val json = manifestJson(context)
        val filterIds = (0 until json.getJSONArray("filters").length())
            .map { i -> json.getJSONArray("filters").getJSONObject(i).getString("id") }
            .toSet()

        val dangling = mutableListOf<String>()
        val categories = json.getJSONArray("categories")
        for (i in 0 until categories.length()) {
            val cat = categories.getJSONObject(i)
            val ids = cat.getJSONArray("filters")
            for (j in 0 until ids.length()) {
                val fid = ids.getString(j)
                if (fid !in filterIds) dangling.add("${cat.getString("id")}/$fid")
            }
        }

        assertTrue(
            "Categories referencing unknown filter ids (would crash LutFilterEngine.loadManifest): $dangling",
            dangling.isEmpty()
        )
    }

    @Test
    fun `manifest has no duplicate filter ids`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val filters = manifestJson(context).getJSONArray("filters")
        val seen = mutableSetOf<String>()
        val duplicates = mutableSetOf<String>()
        for (i in 0 until filters.length()) {
            val id = filters.getJSONObject(i).getString("id")
            if (!seen.add(id)) duplicates.add(id)
        }

        assertTrue("Duplicate filter ids in manifest: $duplicates", duplicates.isEmpty())
    }
}
