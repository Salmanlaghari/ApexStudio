package com.apexstudio.app.data.filter

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GpuColorProfileThumbnailGeneratorTest {

    @Test
    fun `every built in GPU profile gets a transformed photo sample`() = runBlocking {
        val source = Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                source.setPixel(
                    x,
                    y,
                    Color.rgb(
                        35 + x * 180 / source.width,
                        45 + y * 170 / source.height,
                        220 - x * 160 / source.width
                    )
                )
            }
        }

        val previews = GpuColorProfileThumbnailGenerator.generate(source, GpuColorProfiles.ALL)
        assertEquals(GpuColorProfiles.ALL.size, previews.size)
        assertTrue(GpuColorProfiles.ALL.all { previews.containsKey(it.id) })
        previews.values.forEach { preview ->
            val bitmap = preview.asAndroidBitmap()
            assertEquals(120, bitmap.width)
            assertEquals(120, bitmap.height)
        }

        val monochrome = previews.getValue("profile_bw").asAndroidBitmap().getPixel(60, 60)
        assertTrue(abs(Color.red(monochrome) - Color.green(monochrome)) <= 2)
        assertTrue(abs(Color.green(monochrome) - Color.blue(monochrome)) <= 2)
        source.recycle()
    }
}
