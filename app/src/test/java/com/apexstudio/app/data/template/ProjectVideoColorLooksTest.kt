package com.apexstudio.app.data.template

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProjectVideoColorLooksTest {
    private val ids = listOf("natural-hd", "daylight-clean", "vivid-balanced")

    @Test fun everyNewLookResolvesThroughSharedPreviewExportEffect() {
        ids.forEach { id ->
            assertNotNull(ProjectVideoPresets.effect(id))
            assertNotNull(ProjectVideoPresets.colorEffect(id))
        }
        assertNull(ProjectVideoPresets.colorEffect("not-a-look"))
    }

    @Test fun newLooksPreserveNeutralGraysAndAlpha() {
        ids.forEach { id ->
            val matrix = ProjectVideoPresets.effect(id)!!.colorMatrix().array
            assertTrue(matrix.all { it.isFinite() })
            listOf(0f, 64f, 128f, 192f, 255f).forEach { gray ->
                fun channel(row: Int): Float = (matrix[row * 5] * gray + matrix[row * 5 + 1] * gray +
                    matrix[row * 5 + 2] * gray + matrix[row * 5 + 4]).coerceIn(0f, 255f)
                assertEquals(id, channel(0), channel(1), 0.001f)
                assertEquals(id, channel(1), channel(2), 0.001f)
            }
            assertArrayEquals(floatArrayOf(0f, 0f, 0f, 1f, 0f), matrix.copyOfRange(15, 20), 0f)
        }
    }

    @Test fun callersCannotMutateTheCatalogMatrix() {
        val look = ProjectVideoPresets.effect("natural-hd")!!
        val original = look.colorMatrix().array.copyOf()
        look.colorMatrix().setSaturation(0f)
        assertArrayEquals(original, look.colorMatrix().array, 0f)
        assertEquals(ProjectVideoPresets.effects.size, ProjectVideoPresets.effects.map { it.id }.distinct().size)
    }
}
