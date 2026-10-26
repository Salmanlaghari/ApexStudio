package com.apexstudio.app.data.effect

import android.graphics.Bitmap
import android.graphics.Color
import com.apexstudio.app.domain.model.WorkspaceFilterPresetAdditions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkspaceFilterThumbnailGeneratorTest {
    @Test
    fun `filter thumbnail applies the same look at the requested strength`() {
        val source = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, Color.rgb(88, 112, 138))
            setPixel(1, 0, Color.rgb(194, 116, 76))
            setPixel(0, 1, Color.rgb(50, 150, 95))
            setPixel(1, 1, Color.rgb(220, 205, 174))
        }

        val original = source.getPixel(0, 0)
        val fullStrength = WorkspaceFilterThumbnailGenerator.applyPreset(source, "filter_amber", 1f)
        val halfStrength = WorkspaceFilterThumbnailGenerator.applyPreset(source, "filter_amber", 0.5f)

        assertNotEquals("The filter preview must not be just the source image", original, fullStrength.getPixel(0, 0))
        assertNotEquals("Strength should change the preview result", fullStrength.getPixel(0, 0), halfStrength.getPixel(0, 0))
        assertEquals(source.width, fullStrength.width)
        assertEquals(source.height, fullStrength.height)
    }

    @Test
    fun `all filter gallery ids have deterministic previews`() {
        val ids = listOf(
            "filter_soft_warm", "filter_cool", "filter_faded", "filter_amber", "filter_rose",
            "filter_mint", "filter_teal_orange", "filter_crisp", "filter_matte", "filter_bw_contrast",
            "filter_sepia", "filter_lavender",
        ) + WorkspaceFilterPresetAdditions.ids
        val source = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, Color.rgb(35, 78, 122))
            setPixel(1, 0, Color.rgb(198, 92, 66))
            setPixel(2, 0, Color.rgb(174, 184, 98))
            setPixel(0, 1, Color.rgb(196, 148, 180))
            setPixel(1, 1, Color.rgb(68, 146, 112))
            setPixel(2, 1, Color.rgb(215, 202, 170))
        }

        val outputs = ids.map { id ->
            val first = WorkspaceFilterThumbnailGenerator.applyPreset(source, id)
            val second = WorkspaceFilterThumbnailGenerator.applyPreset(source, id)
            assertEquals("$id changed size", source.width, first.width)
            assertEquals("$id is nondeterministic", first.getPixel(1, 0), second.getPixel(1, 0))
            first.getPixel(1, 0)
        }
        assertEquals("All ids are unique", ids.size, ids.toSet().size)
        assertTrue("The gallery should contain the original and 50+ added looks", ids.size >= 62)
        assertTrue("The sample catalog should produce a range of distinct looks", outputs.toSet().size > 20)
    }
}
