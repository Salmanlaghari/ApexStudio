package com.apexstudio.app.data.photoedit

import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import com.apexstudio.app.domain.model.PhotoCanvasSettings
import com.apexstudio.app.domain.model.PhotoEditSettings
import com.apexstudio.app.domain.model.VideoAdjustments
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoCanvasTest {
    @Test fun `old projects retain original canvas and settings round trip`() {
        val original = Json.decodeFromString<PhotoEditSettings>("{}")
        assertTrue(original.canvas.isOriginal)
        assertTrue(original.isDefault)
        val settings = original.copy(canvas = PhotoCanvasSettings("9:16", 0xFFFFF1D6L, true))
        assertEquals(settings, Json.decodeFromString<PhotoEditSettings>(Json.encodeToString(settings)))
        assertFalse(settings.isDefault)
    }

    @Test fun `canvas geometry fits landscape into square without losing edges`() {
        val geometry = PhotoCanvasGeometry.calculate(400, 200, 1f, false)
        assertEquals(400, geometry.width)
        assertEquals(400, geometry.height)
        assertEquals(0f, geometry.left, 0.001f)
        assertEquals(100f, geometry.top, 0.001f)
        assertEquals(400f, geometry.right, 0.001f)
        assertEquals(300f, geometry.bottom, 0.001f)
    }

    @Test fun `fill crops equally at both sides and canvas stays bounded`() {
        val geometry = PhotoCanvasGeometry.calculate(400, 200, 1f, true)
        assertEquals(-200f, geometry.left, 0.001f)
        assertEquals(600f, geometry.right, 0.001f)
        assertEquals(0f, geometry.top, 0.001f)
        for (preset in PhotoCanvasSettings.PRESETS) {
            val aspect = PhotoCanvasSettings(preset).aspectRatio ?: continue
            val portrait = PhotoCanvasGeometry.calculate(200, 400, aspect, false)
            assertTrue(portrait.width in 1..400 && portrait.height in 1..400)
            assertEquals(aspect, portrait.width.toFloat() / portrait.height, 0.01f)
        }
    }

    @Test fun `invalid geometry is rejected`() {
        assertTrue(runCatching { PhotoCanvasGeometry.calculate(0, 100, 1f, false) }.isFailure)
        assertTrue(runCatching { PhotoCanvasGeometry.calculate(100, 100, Float.NaN, false) }.isFailure)
    }

    @Test fun `EXIF orientation applies rotations mirrors and transposes`() {
        val sourceColors = arrayOf(
            intArrayOf(Color.RED, Color.GREEN, Color.BLUE),
            intArrayOf(Color.CYAN, Color.MAGENTA, Color.YELLOW)
        )
        val src = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888).apply {
            for (y in sourceColors.indices) for (x in sourceColors[y].indices) {
                setPixel(x, y, sourceColors[y][x])
            }
        }
        val expected = mapOf(
            ExifInterface.ORIENTATION_NORMAL to arrayOf(
                intArrayOf(Color.RED, Color.GREEN, Color.BLUE), intArrayOf(Color.CYAN, Color.MAGENTA, Color.YELLOW)
            ),
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL to arrayOf(
                intArrayOf(Color.BLUE, Color.GREEN, Color.RED), intArrayOf(Color.YELLOW, Color.MAGENTA, Color.CYAN)
            ),
            ExifInterface.ORIENTATION_ROTATE_180 to arrayOf(
                intArrayOf(Color.YELLOW, Color.MAGENTA, Color.CYAN), intArrayOf(Color.BLUE, Color.GREEN, Color.RED)
            ),
            ExifInterface.ORIENTATION_FLIP_VERTICAL to arrayOf(
                intArrayOf(Color.CYAN, Color.MAGENTA, Color.YELLOW), intArrayOf(Color.RED, Color.GREEN, Color.BLUE)
            ),
            ExifInterface.ORIENTATION_TRANSPOSE to arrayOf(
                intArrayOf(Color.RED, Color.CYAN), intArrayOf(Color.GREEN, Color.MAGENTA), intArrayOf(Color.BLUE, Color.YELLOW)
            ),
            ExifInterface.ORIENTATION_ROTATE_90 to arrayOf(
                intArrayOf(Color.CYAN, Color.RED), intArrayOf(Color.MAGENTA, Color.GREEN), intArrayOf(Color.YELLOW, Color.BLUE)
            ),
            ExifInterface.ORIENTATION_TRANSVERSE to arrayOf(
                intArrayOf(Color.YELLOW, Color.BLUE), intArrayOf(Color.MAGENTA, Color.GREEN), intArrayOf(Color.CYAN, Color.RED)
            ),
            ExifInterface.ORIENTATION_ROTATE_270 to arrayOf(
                intArrayOf(Color.BLUE, Color.YELLOW), intArrayOf(Color.GREEN, Color.MAGENTA), intArrayOf(Color.RED, Color.CYAN)
            )
        )
        try {
            expected.forEach { (orientation, rows) ->
                val rendered = PhotoEditRenderer.applyExifOrientation(src, orientation)
                try {
                    assertEquals("width for EXIF orientation $orientation", rows[0].size, rendered.width)
                    assertEquals("height for EXIF orientation $orientation", rows.size, rendered.height)
                    for (y in rows.indices) for (x in rows[y].indices) {
                        assertEquals("pixel ($x,$y), EXIF orientation $orientation", rows[y][x], rendered.getPixel(x, y))
                    }
                } finally {
                    if (rendered !== src) rendered.recycle()
                }
            }
            assertEquals(Color.RED, src.getPixel(0, 0))
            assertFalse(src.isRecycled)
        } finally { src.recycle() }
    }

    @Test fun `solid background is not affected by photo grading and source remains intact`() {
        val src = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val result = PhotoEditRenderer.applyEdits(src, PhotoEditSettings(
            adjustments = VideoAdjustments(brightness = -0.5f),
            canvas = PhotoCanvasSettings("1:1", 0xFFFFFFFFL)
        ), null)
        try {
            assertEquals(40, result.width)
            assertEquals(40, result.height)
            assertEquals(Color.WHITE, result.getPixel(20, 2))
            assertNotEquals(Color.WHITE, result.getPixel(20, 20))
            assertEquals(Color.RED, src.getPixel(20, 10))
            assertFalse(src.isRecycled)
        } finally { result.recycle(); src.recycle() }
    }

    @Test fun `fill covers the whole canvas and reset restores original dimensions`() {
        val src = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val result = PhotoEditRenderer.applyEdits(src, PhotoEditSettings(canvas = PhotoCanvasSettings("1:1", 0xFFFFFFFFL, true)), null)
        try {
            assertEquals(Color.RED, result.getPixel(20, 1))
            assertSame(src, PhotoEditRenderer.applyEdits(src, PhotoEditSettings(), null))
            assertEquals(20, src.height)
        } finally { result.recycle(); src.recycle() }
    }

    @Test fun `motion only edits expose reset affordance`() {
        assertFalse(PhotoEditSettings(kenBurns = true).isDefault)
    }
}
