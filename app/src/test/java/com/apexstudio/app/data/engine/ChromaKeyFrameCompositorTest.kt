package com.apexstudio.app.data.engine

import android.graphics.Bitmap
import android.graphics.Color
import com.apexstudio.app.domain.model.ChromaKeySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChromaKeyFrameCompositorTest {
    @Test
    fun `screen color is replaced by wallpaper and foreground remains visible`() {
        val source = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, Color.GREEN)
            setPixel(1, 0, Color.RED)
        }
        val wallpaper = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, Color.BLUE)
        }
        val compositor = ChromaKeyFrameCompositor(
            width = 2,
            height = 1,
            background = wallpaper,
            settings = ChromaKeySettings(
                enabled = true,
                keyColorArgb = 0xFF00FF00L,
                similarity = 0.2f,
                smoothness = 0.1f,
                spillSuppression = 0f
            )
        )

        try {
            val result = compositor.composite(source)
            assertEquals(Color.BLUE, result.getPixel(0, 0))
            assertEquals(Color.RED, result.getPixel(1, 0))
            compositor.release()
            assertTrue(result.isRecycled)
        } finally {
            compositor.release()
            source.recycle()
            wallpaper.recycle()
        }
    }
}
