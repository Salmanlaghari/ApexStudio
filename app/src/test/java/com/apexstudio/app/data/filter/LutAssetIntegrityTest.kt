package com.apexstudio.app.data.filter

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
import java.io.BufferedReader
import java.io.InputStreamReader

/** Exhaustively validates each bundled .cube, manifest reference and category membership. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LutAssetIntegrityTest {
    @Test fun everyCubeAssetParsesAndEveryManifestEntryResolves() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val lutFiles = context.assets.list("luts")!!.filter { it.endsWith(".cube", true) }.toSet()
        assertTrue("Expected bundled LUTs", lutFiles.isNotEmpty())
        val text = context.assets.open("luts/filter_manifest.json").bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        val filters = root.getJSONArray("filters")
        val ids = mutableSetOf<String>()
        val referencedAssets = mutableSetOf<String>()
        for (i in 0 until filters.length()) {
            val item = filters.getJSONObject(i)
            val id = item.getString("id")
            assertTrue("Duplicate filter id $id", ids.add(id))
            val path = item.getString("asset")
            assertTrue("$path missing from assets", path.removePrefix("luts/") in lutFiles)
            referencedAssets.add(path.removePrefix("luts/"))
            val parsed = context.assets.open(path).use { input ->
                CubeLutParser.parse(BufferedReader(InputStreamReader(input)))
            }
            assertNotNull("Malformed LUT $path", parsed)
            assertTrue("empty LUT data for $path", parsed!!.isNotEmpty())
        }
        assertTrue("Viral HDR must resolve to the bundled hdr_punch LUT", OneTapLooks.VIRAL_HDR.config.filterPresetId in ids)
        val categories = root.getJSONArray("categories")
        val categorized = mutableSetOf<String>()
        for (i in 0 until categories.length()) {
            val arr = categories.getJSONObject(i).getJSONArray("filters")
            for (j in 0 until arr.length()) {
                val id = arr.getString(j)
                assertTrue("Category references absent filter $id", id in ids)
                assertTrue("Filter $id assigned more than once", categorized.add(id))
            }
        }
        assertEquals("Every manifest filter needs one category", ids, categorized)
        // Also catch malformed/orphan cube assets even if a legacy catalog omits one.
        for (name in lutFiles) {
            context.assets.open("luts/$name").use { input ->
                assertNotNull("Orphan or malformed cube $name", CubeLutParser.parse(BufferedReader(InputStreamReader(input))))
            }
        }
        assertTrue("Every declared LUT asset must exist", lutFiles.containsAll(referencedAssets))
    }

    @Test fun originalApexGradesAreDistinctAndNonIdentity() {
        val ids = listOf("apex_aurora_soft", "apex_copper_city", "apex_botanical_film", "apex_midnight_cyan", "apex_pastel_lift")
        val matrices = ids.map { FilterColorMatrix.getRawMatrix(it).toList() }
        assertTrue(matrices.all { it.size == 20 && it != FilterColorMatrix.getRawMatrix(null).toList() })
        assertEquals(ids.size, matrices.toSet().size)
    }
}
