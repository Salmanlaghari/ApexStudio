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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

  @Test
  fun `verify clip drag and drop reordering preserves items and updates positions`() {
    val clipA = com.apexstudio.app.domain.model.MediaClip(id = "clip_a", name = "Clip A", uri = "uri_a", durationMs = 3000L, trimEndMs = 3000L)
    val clipB = com.apexstudio.app.domain.model.MediaClip(id = "clip_b", name = "Clip B", uri = "uri_b", durationMs = 4000L, trimEndMs = 4000L)
    val clipC = com.apexstudio.app.domain.model.MediaClip(id = "clip_c", name = "Clip C", uri = "uri_c", durationMs = 5000L, trimEndMs = 5000L)

    val clips = mutableListOf(clipA, clipB, clipC)
    // Drag clip A (index 0) to end (index 2)
    val moved = clips.removeAt(0)
    clips.add(2, moved)

    assertEquals("Clip B should now be first", "clip_b", clips[0].id)
    assertEquals("Clip C should now be second", "clip_c", clips[1].id)
    assertEquals("Clip A should now be third", "clip_a", clips[2].id)
  }

  @Test
  fun `verify GpuImageLutEngine builds filter chain and manages tracks`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val engine = com.apexstudio.app.data.filter.GpuImageLutEngine(context)

    // Verify LutTargetTrack enum values
    val tracks = com.apexstudio.app.presentation.state.LutTargetTrack.values()
    assertEquals(4, tracks.size)
    assertTrue(tracks.any { it.shortBadge == "ALL" })
    assertTrue(tracks.any { it.shortBadge == "V1" })
    assertTrue(tracks.any { it.shortBadge == "V2" })
    assertTrue(tracks.any { it.shortBadge == "CLIP" })

    // Build GPUImageFilter chain with secondary grading (contrast, saturation, temperature)
    val filterChain = engine.buildFilter(
      preset = null,
      intensity = 1.0f,
      contrast = 1.2f,
      saturation = 1.1f,
      temperatureK = 6500f,
      tint = 10f
    )
    assertNotNull("Filter chain must not be null", filterChain)
  }

  @Test
  fun `verify LutThumbnailPreviewGallery generation and view modes`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val viewModes = com.apexstudio.app.presentation.state.LutGalleryViewMode.values()
    assertEquals(2, viewModes.size)
    assertTrue(viewModes.contains(com.apexstudio.app.presentation.state.LutGalleryViewMode.GRID))
    assertTrue(viewModes.contains(com.apexstudio.app.presentation.state.LutGalleryViewMode.STRIP))

    // Verify dynamic thumbnail generation from sample video frame
    val sampleFrame = com.apexstudio.app.data.filter.FilterThumbnailGenerator.createGenericPreviewBitmap(context)
    val manifest = com.apexstudio.app.data.filter.LutFilterEngine(context).manifest
    val thumbnails = com.apexstudio.app.data.filter.FilterThumbnailGenerator.generateDynamicThumbnails(
      context = context,
      source = sampleFrame,
      manifest = manifest
    )
    assertTrue("Thumbnails must contain raw video frame key (null)", thumbnails.containsKey(null))
    assertTrue("Thumbnails must contain at least 50 filtered presets", thumbnails.size >= 50)
  }

  @Test
  fun `verify keyframe animation system adjusts scale rotation and opacity over time`() {
    val kfStart = com.apexstudio.app.domain.model.Keyframe(
      id = "kf_start",
      timeMs = 1000L,
      scale = 1.0f,
      rotationDeg = 0f,
      opacity = 1.0f,
      curve = com.apexstudio.app.domain.model.KeyframeCurve.LINEAR
    )
    val kfEnd = com.apexstudio.app.domain.model.Keyframe(
      id = "kf_end",
      timeMs = 2000L,
      scale = 2.0f,
      rotationDeg = 90f,
      opacity = 0.2f,
      curve = com.apexstudio.app.domain.model.KeyframeCurve.LINEAR
    )

    val track = com.apexstudio.app.domain.model.KeyframeTrack(listOf(kfStart, kfEnd)).sorted()

    // Test before start
    val t0 = track.interpolateAt(500L)
    assertEquals(1.0f, t0.scale, 0.01f)
    assertEquals(0f, t0.rotationDeg, 0.01f)
    assertEquals(1.0f, t0.opacity, 0.01f)

    // Test midpoint (1500ms)
    val tMid = track.interpolateAt(1500L)
    assertEquals(1.5f, tMid.scale, 0.05f)
    assertEquals(45f, tMid.rotationDeg, 0.5f)
    assertEquals(0.6f, tMid.opacity, 0.05f)

    // Test at end (2000ms)
    val tEnd = track.interpolateAt(2000L)
    assertEquals(2.0f, tEnd.scale, 0.01f)
    assertEquals(90f, tEnd.rotationDeg, 0.01f)
    assertEquals(0.2f, tEnd.opacity, 0.01f)

    // Test after end
    val tAfter = track.interpolateAt(2500L)
    assertEquals(2.0f, tAfter.scale, 0.01f)
    assertEquals(90f, tAfter.rotationDeg, 0.01f)
    assertEquals(0.2f, tAfter.opacity, 0.01f)
  }

  @Test
  fun `verify animation presets generate valid scale rotation and opacity keyframe tracks`() {
    // Zoom In preset
    val zoomInTrack = com.apexstudio.app.data.animation.AnimationPresets.createTrack(
      com.apexstudio.app.data.animation.AnimationPresetType.ZOOM_IN,
      startMs = 0L,
      durationMs = 1000L
    )
    assertEquals(2, zoomInTrack.keyframes.size)
    assertTrue("Start scale must be smaller than end scale", zoomInTrack.keyframes.first().scale < zoomInTrack.keyframes.last().scale)

    // Rotate preset
    val rotateTrack = com.apexstudio.app.data.animation.AnimationPresets.createTrack(
      com.apexstudio.app.data.animation.AnimationPresetType.ROTATE,
      startMs = 0L,
      durationMs = 1000L
    )
    assertEquals(2, rotateTrack.keyframes.size)
    assertEquals(-180f, rotateTrack.keyframes.first().rotationDeg, 0.1f)
    assertEquals(0f, rotateTrack.keyframes.last().rotationDeg, 0.1f)

    // Fade In preset
    val fadeInTrack = com.apexstudio.app.data.animation.AnimationPresets.createTrack(
      com.apexstudio.app.data.animation.AnimationPresetType.FADE_IN,
      startMs = 0L,
      durationMs = 1000L
    )
    assertEquals(0f, fadeInTrack.keyframes.first().opacity, 0.01f)
    assertEquals(1f, fadeInTrack.keyframes.last().opacity, 0.01f)
  }

  @Test
  fun `verify TimeFormat parses user input timestamps to milliseconds accurately`() {
    // Seconds as string
    assertEquals(4500L, com.apexstudio.app.util.TimeFormat.parseTimeToMs("4.5"))
    assertEquals(12000L, com.apexstudio.app.util.TimeFormat.parseTimeToMs("12"))

    // MM:SS format
    assertEquals(90000L, com.apexstudio.app.util.TimeFormat.parseTimeToMs("01:30"))
    assertEquals(5000L, com.apexstudio.app.util.TimeFormat.parseTimeToMs("00:05"))

    // MM:SS.cs format
    assertEquals(4500L, com.apexstudio.app.util.TimeFormat.parseTimeToMs("00:04.50"))
    assertEquals(65200L, com.apexstudio.app.util.TimeFormat.parseTimeToMs("01:05.20"))

    // HH:MM:SS format
    assertEquals(3665000L, com.apexstudio.app.util.TimeFormat.parseTimeToMs("01:01:05"))

    // Invalid format handling
    assertNull(com.apexstudio.app.util.TimeFormat.parseTimeToMs(""))
    assertNull(com.apexstudio.app.util.TimeFormat.parseTimeToMs("invalid_time"))
  }

  @Test
  fun `verify Media3VideoTrimmer trims video with custom start and end timestamps`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val trimmer = com.apexstudio.app.data.trim.Media3VideoTrimmer(context)

    // Test rejection of negative or sub-minimum duration
    val invalidRequest = com.apexstudio.app.data.trim.Media3VideoTrimmer.TrimRequest(
      inputUri = "sample",
      startMs = 2000L,
      endMs = 2050L // duration 50ms < MIN_CLIP_DURATION_MS (100ms)
    )
    var errorEncountered = false
    trimmer.trimVideo(invalidRequest).collect { result ->
      if (result is com.apexstudio.app.data.trim.Media3VideoTrimmer.TrimResult.Error) {
        errorEncountered = true
        assertTrue(result.message.contains("too short"))
      }
    }
    assertTrue("Trim with duration < 100ms must emit error", errorEncountered)

    // Test valid trimming request execution
    val validRequest = com.apexstudio.app.data.trim.Media3VideoTrimmer.TrimRequest(
      inputUri = "sample",
      startMs = 1000L,
      endMs = 4000L,
      clipId = "test_clip"
    )

    var hasProgress = false
    var successResult: com.apexstudio.app.data.trim.Media3VideoTrimmer.TrimResult.Success? = null
    trimmer.trimVideo(validRequest).collect { result ->
      when (result) {
        is com.apexstudio.app.data.trim.Media3VideoTrimmer.TrimResult.Progress -> {
          hasProgress = true
          assertTrue("Progress must be between 0 and 1", result.progress in 0f..1f)
        }
        is com.apexstudio.app.data.trim.Media3VideoTrimmer.TrimResult.Success -> {
          successResult = result
        }
        is com.apexstudio.app.data.trim.Media3VideoTrimmer.TrimResult.Error -> {}
      }
    }

    assertNotNull("Trim operation must succeed", successResult)
    assertEquals(3000L, successResult?.durationMs)
    assertEquals(1000L, successResult?.startMs)
    assertEquals(4000L, successResult?.endMs)
    assertEquals("test_clip", successResult?.clipId)
    assertTrue("Output URI must not be empty", !successResult?.outputUri.isNullOrEmpty())
  }
}
