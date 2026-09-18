package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.test.core.app.ApplicationProvider
import com.apexstudio.app.R
import com.apexstudio.app.data.audio.FreeRoyaltyMusic
import com.apexstudio.app.data.engine.ChromaKeyProcessor
import com.apexstudio.app.data.engine.ColorGradingProcessor
import com.apexstudio.app.data.engine.RealEffectsProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("ApexStudio", appName)
  }

  @Test
  fun `verify real ChromaKey pixel processing removes green screen`() {
    // 10x10 pure green bitmap
    val greenBitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
    for (x in 0 until 10) {
      for (y in 0 until 10) {
        greenBitmap.setPixel(x, y, AndroidColor.rgb(0, 255, 0))
      }
    }

    // 1. In Matte View mode, pure green is keyed to black
    val matteParams = ChromaKeyProcessor.KeyingParams(
      keyColor = AndroidColor.rgb(0, 255, 0),
      similarity = 0.40f,
      smoothness = 0.08f,
      spillSuppression = 0.5f
    )
    val matteProcessed = ChromaKeyProcessor.processFrame(greenBitmap, matteParams, matteViewOnly = true)
    val mattePixel = matteProcessed.getPixel(5, 5)
    assertEquals("Matte for keyed green must be black (0)", 0, AndroidColor.red(mattePixel))

    // 2. In normal composite mode with Black background plate, green is removed
    val blackBg = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
    for (x in 0 until 10) {
      for (y in 0 until 10) {
        blackBg.setPixel(x, y, AndroidColor.rgb(0, 0, 0))
      }
    }
    val composited = ChromaKeyProcessor.processFrame(greenBitmap, matteParams, background = blackBg)
    val centerPixel = composited.getPixel(5, 5)
    assertEquals("Green channel must be completely keyed out", 0, AndroidColor.green(centerPixel))
  }

  @Test
  fun `verify distinct VFX algorithms produce unique pixel changes`() {
    val testBitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
    for (y in 0 until 20) {
      for (x in 0 until 20) {
        testBitmap.setPixel(x, y, AndroidColor.rgb(x * 12, y * 12, 128))
      }
    }

    val rgbJitter = RealEffectsProcessor.applyEffect(testBitmap, "rgb_jitter", 0.8f, 10L)
    val pixelSort = RealEffectsProcessor.applyEffect(testBitmap, "pixel_sort", 0.8f, 10L)
    val datamosh = RealEffectsProcessor.applyEffect(testBitmap, "datamosh", 0.8f, 10L)

    // Verify effects alter pixels and produce distinct results
    val rgbCenter = rgbJitter.getPixel(10, 10)
    val sortCenter = pixelSort.getPixel(10, 10)
    val origCenter = testBitmap.getPixel(10, 10)

    assertNotEquals("RGB Jitter must modify source pixel", origCenter, rgbCenter)
    assertNotEquals("Pixel Sort and RGB Jitter must not be identical", rgbCenter, sortCenter)
  }

  @Test
  fun `verify color grading adjusts brightness and contrast`() {
    val testBitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
    for (x in 0 until 10) {
      for (y in 0 until 10) {
        testBitmap.setPixel(x, y, AndroidColor.rgb(100, 100, 100))
      }
    }

    val adjusted = ColorGradingProcessor.process(
      testBitmap,
      ColorGradingProcessor.ColorParams(brightness = 0.5f, contrast = 1.2f)
    )

    val gradedCenter = adjusted.getPixel(5, 5)
    assertTrue("Brightness +0.5 must increase red channel", AndroidColor.red(gradedCenter) > 100)
  }

  @Test
  fun `verify royalty free audio synthesis creates playable WAV`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val track = FreeRoyaltyMusic.CATALOG[0]
    val file = FreeRoyaltyMusic.getTrackFile(context, track)

    assertTrue("WAV file must exist", file.exists())
    assertTrue("WAV file must have data > 44 bytes header", file.length() > 44)
  }
}
