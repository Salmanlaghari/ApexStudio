package com.apexstudio.app.data.photoedit

import android.graphics.Bitmap
import android.graphics.Color
import com.apexstudio.app.domain.model.PhotoEditSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.tan

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoStabilizationMathTest {
    @Test fun dominantTiltedHorizontalLineProducesOppositeCorrection() {
        val width = 320
        val height = 240
        val pixels = IntArray(width * height) { 0xff202020.toInt() }
        val lineDegrees = 4f
        val tangent = tan(Math.toRadians(lineDegrees.toDouble())).toFloat()
        for (x in 0 until width) {
            val centerY = height / 2f + tangent * (x - width / 2f)
            for (offset in -5..5) {
                val y = (centerY + offset).toInt()
                if (y in 0 until height) pixels[y * width + x] = 0xfff0f0f0.toInt()
            }
        }
        val correction = PhotoStabilizationMath.estimateCorrectionDegrees(width, height, pixels)
        assertNotNull(correction)
        assertEquals(-lineDegrees, correction!!, 1.2f)
    }

    @Test fun ambiguousTextureAndFlatImagesRemainUnchanged() {
        val flat = IntArray(128 * 96) { 0xff777777.toInt() }
        assertNull(PhotoStabilizationMath.estimateCorrectionDegrees(128, 96, flat))

        val checker = IntArray(128 * 96) { index ->
            val x = index % 128
            val y = index / 128
            if ((x / 8 + y / 8) % 2 == 0) 0xffffffff.toInt() else 0xff000000.toInt()
        }
        assertNull(PhotoStabilizationMath.estimateCorrectionDegrees(128, 96, checker))
    }

    @Test fun safeCropRetainsAllRotatedPixelsAndRejectsNoDimensions() {
        val scale = PhotoStabilizationMath.safeCropScale(4000, 3000, 4f)
        assertTrue(scale in 0.8f..1f)
        assertTrue(PhotoStabilizationMath.safeCropScale(4000, 3000, 0f) == 1f)
        assertEquals(scale, PhotoStabilizationMath.safeCropScale(4000, 3000, -4f), 0f)
        val (innerWidth, innerHeight) = PhotoStabilizationMath.inscribedCropSize(320, 240, 4f)
        assertTrue(innerWidth < 320 && innerHeight < 240)
        assertTrue(innerWidth > 250 && innerHeight > 180)
        val (portraitWidth, portraitHeight) = PhotoStabilizationMath.inscribedCropSize(240, 320, 4f)
        assertTrue(portraitWidth < portraitHeight)
    }

    @Test fun rendererAutoLevelsConfidentTiltAndLeavesFlatImagesUntouched() {
        val width = 320
        val height = 240
        val tilted = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.DKGRAY)
            val tangent = tan(Math.toRadians(4.0)).toFloat()
            for (x in 0 until width) {
                val centerY = height / 2f + tangent * (x - width / 2f)
                for (offset in -5..5) {
                    val y = (centerY + offset).toInt()
                    if (y in 0 until height) setPixel(x, y, Color.WHITE)
                }
            }
        }
        val flat = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        val leveled = PhotoEditRenderer.applyEdits(tilted, PhotoEditSettings(stabilizePhoto = true), null)
        try {
            assertTrue(leveled !== tilted)
            assertTrue(leveled.width < tilted.width)
            assertTrue(leveled.height < tilted.height)
            assertEquals(0xff, leveled.getPixel(0, 0) ushr 24)
            assertEquals(0xff, leveled.getPixel(leveled.width - 1, leveled.height - 1) ushr 24)
            assertSame(flat, PhotoEditRenderer.applyEdits(flat, PhotoEditSettings(stabilizePhoto = true), null))
        } finally {
            if (leveled !== tilted) leveled.recycle()
            tilted.recycle()
            flat.recycle()
        }
    }
}
