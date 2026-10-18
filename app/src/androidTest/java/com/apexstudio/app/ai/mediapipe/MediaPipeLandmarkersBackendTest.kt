package com.apexstudio.app.ai.mediapipe

import android.graphics.Bitmap
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking

/** Device-only smoke: exercises bundled native MediaPipe tasks, not mocked outputs.
 * A blank frame verifies load/inference/lifecycle, NOT detection accuracy or content safety. */
@RunWith(AndroidJUnit4::class)
class MediaPipeLandmarkersBackendTest {
    @Test
    fun `bundled face pose and hand models run inference and detectors close safely`() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
        try {
            BundledMediaPipeModels.openFace(context).use { detector ->
                val faces = detector.detectCancellable(bitmap)
                assertTrue(faces.all { it.landmarks.size >= 400 })
                assertTrue(faces.flatMap { it.landmarks }.all(::hasFiniteCoordinates))
                detector.close() // close is intentionally safe to repeat
                assertThrowsClosed { detector.detect(bitmap) }
            }

            BundledMediaPipeModels.openPose(context).use { detector ->
                val poses = detector.detectCancellable(bitmap)
                assertTrue(poses.all { it.landmarks.size == 33 })
                assertTrue(poses.flatMap { it.landmarks }.all(::hasFiniteCoordinates))
                detector.close()
                assertThrowsClosed { detector.detect(bitmap) }
            }

            BundledMediaPipeModels.openHands(context).use { detector ->
                val hands = detector.detectCancellable(bitmap)
                assertTrue(hands.all { it.landmarks.size == 21 })
                assertTrue(hands.flatMap { it.landmarks }.all(::hasFiniteCoordinates))
                detector.close()
                assertThrowsClosed { detector.detect(bitmap) }
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun hasFiniteCoordinates(point: NormalizedPoint) =
        point.x.isFinite() && point.y.isFinite() && point.z.isFinite()

    private fun assertThrowsClosed(block: () -> Unit) {
        try {
            block()
            throw AssertionError("A closed MediaPipe detector accepted inference")
        } catch (expected: IllegalStateException) {
            assertFalse(expected.message.isNullOrBlank())
        }
    }
}
