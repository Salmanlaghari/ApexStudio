package com.apexstudio.app.data.photoedit

import android.graphics.Bitmap
import android.graphics.Color
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
