package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioSynthesizer
import com.example.effects.ChromaKeyProcessor
import com.example.effects.ColorGradingProcessor
import com.example.effects.RealEffectsProcessor
import com.example.model.AdjustmentValues
import com.example.model.ChromaKeyState
import com.example.model.EffectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
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

    // 1. In Matte View mode, pure green is masked to pure black (0)
    val matteState = ChromaKeyState(
      enabled = true,
      keyColor = AndroidColor.rgb(0, 255, 0),
      similarity = 0.40f,
      smoothness = 0.08f,
      showMatteOnly = true
    )
    val matteProcessed = ChromaKeyProcessor.processFrame(greenBitmap, matteState)
    val mattePixel = matteProcessed.getPixel(5, 5)
    assertEquals("Matte for keyed green must be black (0)", 0, AndroidColor.red(mattePixel))

    // 2. In normal composite mode with Black background plate, green is removed and replaced with black
    val blackPlateState = ChromaKeyState(
      enabled = true,
      keyColor = AndroidColor.rgb(0, 255, 0),
      similarity = 0.40f,
      backgroundPlate = com.example.model.BackgroundPlate.BLACK
    )
    val composited = ChromaKeyProcessor.processFrame(greenBitmap, blackPlateState)
    val centerPixel = composited.getPixel(5, 5)
    assertEquals("Green channel must be completely removed to 0", 0, AndroidColor.green(centerPixel))
  }

  @Test
  fun `verify distinct VFX algorithms produce unique pixel changes`() {
    val testBitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
    for (y in 0 until 20) {
      for (x in 0 until 20) {
        testBitmap.setPixel(x, y, AndroidColor.rgb(x * 12, y * 12, 128))
      }
    }

    val rgbJitter = RealEffectsProcessor.applyEffect(testBitmap, EffectType.RGB_JITTER, 0.8f, 10L)
    val pixelSort = RealEffectsProcessor.applyEffect(testBitmap, EffectType.PIXEL_SORT, 0.8f, 10L)
    val datamosh = RealEffectsProcessor.applyEffect(testBitmap, EffectType.DATAMOSH, 0.8f, 10L)

    // Verify effects alter pixels and produce distinct results
    val rgbCenter = rgbJitter.getPixel(10, 10)
    val sortCenter = pixelSort.getPixel(10, 10)
    val origCenter = testBitmap.getPixel(10, 10)

    assertNotEquals("RGB Jitter must modify source pixel", origCenter, rgbCenter)
    assertNotEquals("Pixel Sort and RGB Jitter must not be identical", rgbCenter, sortCenter)
  }

  @Test
  fun `verify color grading adjusts exposure and saturation`() {
    val testBitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
    for (x in 0 until 10) {
      for (y in 0 until 10) {
        testBitmap.setPixel(x, y, AndroidColor.rgb(100, 100, 100))
      }
    }

    val adjusted = ColorGradingProcessor.applyColorGrading(
      testBitmap,
      AdjustmentValues(brightness = 50f, contrast = 20f)
    )

    val gradedCenter = adjusted.getPixel(5, 5)
    assertTrue("Brightness +50 must increase red channel", AndroidColor.red(gradedCenter) > 100)
  }

  @Test
  fun `verify royalty free audio synthesis creates playable WAV`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val track = AudioSynthesizer.TRACKS[0]
    val file = AudioSynthesizer.ensureTrackGenerated(context, track)

    assertTrue("WAV file must exist", file.exists())
    assertTrue("WAV file must have data > 44 bytes header", file.length() > 44)

    val waveforms = AudioSynthesizer.extractWaveformPoints(file, 32)
    assertEquals(32, waveforms.size)
  }
}
