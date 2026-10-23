package com.apexstudio.app.data.filter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CreatorFilterPackTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val ids = listOf("creator_clean", "creator_peach", "creator_travel", "creator_matte", "creator_night", "creator_mono")

    @Test fun packIsDiscoverableAndPreservesLegacyLooks() {
        val manifest = LutFilterEngine(context).manifest
        assertEquals("creator_essentials", manifest.categories.first().id)
        assertEquals(ids, manifest.categories.first().filters.map { it.id })
        assertNotNull(manifest.presetById("apex_aurora_soft"))
        assertNotNull(manifest.presetById("teal_orange"))
        FilterManifest.initialize(context)
        ids.forEach { assertEquals("Creator Essentials", FilterManifest.presetById(it)?.category) }
    }

    @Test fun everyLatticePointMatchesThePreviewMatrixAndPreservesAlpha() {
        ids.forEach { id ->
            val matrix = FilterColorMatrix.getRawMatrix(id)
            val lut = context.assets.open("luts/$id.cube").bufferedReader().use { CubeLutParser.parse(it) }!!
            assertEquals(17 * 17 * 17 * 3, lut.size)
            assertEquals(listOf(0f, 0f, 0f, 1f, 0f), matrix.takeLast(5))
            var offset = 0
            for (b in 0..16) for (g in 0..16) for (r in 0..16) {
                val rgb = floatArrayOf(r / 16f, g / 16f, b / 16f)
                for (channel in 0..2) {
                    val row = channel * 5
                    val expected = (matrix[row] * rgb[0] + matrix[row + 1] * rgb[1] + matrix[row + 2] * rgb[2] + matrix[row + 4] / 255f).coerceIn(0f, 1f)
                    assertEquals("$id at $r,$g,$b channel $channel", expected, lut[offset++], .000002f)
                }
            }
        }
    }

    @Test fun allNewGradesAreDistinctAndZeroIntensityIsOriginal() {
        val identity = FilterColorMatrix.getRawMatrix(null).toList()
        val grades = ids.map { FilterColorMatrix.getRawMatrix(it).toList() }
        assertEquals(ids.size, grades.toSet().size)
        assertTrue(grades.none { it == identity })
        ids.forEach { assertEquals(identity, FilterColorMatrix.getInterpolatedMatrix(it, 0f).toList()) }
        assertNull(CreatorFilterPack.matrix("unknown"))
    }
}
