package com.apexstudio.app.data.template

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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

    @Test fun warmStoryAndCoolMomentMatricesChangeActualHostRenderedBitmapPixels() {
        val sourceColor = Color.rgb(80, 130, 180)
        val source = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, sourceColor)
        }

        fun renderTemplate(templateId: String): Int {
            val recipe = ProjectVideoPresets.videoTemplate(templateId)!!
            val sharedEffect = ProjectVideoPresets.colorEffect(recipe.effectId)
            assertNotNull("${recipe.id} must resolve into the shared preview/export GL effect", sharedEffect)
            val productionMatrix = ProjectVideoPresets.effect(recipe.effectId)!!.colorMatrix()
            val destination = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(productionMatrix) }
            Canvas(destination).drawBitmap(source, 0f, 0f, paint)
            return destination.getPixel(0, 0).also { destination.recycle() }
        }

        val warm = renderTemplate("warm-story")
        val cool = renderTemplate("cool-moment")
        assertNotEquals("warm recipe must alter rendered pixels", sourceColor, warm)
        assertNotEquals("cool recipe must alter rendered pixels", sourceColor, cool)
        assertNotEquals("warm and cool recipes must render distinct pixels", warm, cool)
        assertEquals(89.8f, Color.red(warm).toFloat(), 1f)
        assertEquals(135.4f, Color.green(warm).toFloat(), 1f)
        assertEquals(172.1f, Color.blue(warm).toFloat(), 1f)
        assertEquals(78.4f, Color.red(cool).toFloat(), 1f)
        assertEquals(130.5f, Color.green(cool).toFloat(), 1f)
        assertEquals(191f, Color.blue(cool).toFloat(), 1f)
        source.recycle()
    }
}
