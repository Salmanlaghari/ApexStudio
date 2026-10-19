package com.apexstudio.app.ai.mediapipe

import android.graphics.Bitmap
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FaceTrackingInputSafetyTest {
    private fun face(left: Float = .1f, right: Float = .4f) = FaceDetection(
        NormalizedBox(left, .1f, right, .4f),
        listOf(NormalizedPoint(left, .1f), NormalizedPoint(right, .1f), NormalizedPoint(right, .4f)),
    )

    @Test fun `negative time is rejected without mutating tracker`() {
        val tracker = FaceTrackSmoother()
        assertThrows(IllegalArgumentException::class.java) { tracker.update(-1, listOf(face())) }
        assertEquals(1L, tracker.update(0, listOf(face())).single().id)
    }

    @Test fun `malformed detection does not mutate timestamp or interpolation state`() {
        val tracker = FaceTrackSmoother(smoothing = 1f)
        val initial = tracker.update(100, listOf(face())).single()
        val invalid = listOf(
            face(Float.NaN), face(right = .05f), face().copy(landmarks = listOf(NormalizedPoint(.2f, Float.NaN))),
            face().copy(landmarks = listOf(NormalizedPoint(.2f, .2f, Float.POSITIVE_INFINITY))),
        )
        invalid.forEach { detection ->
            assertThrows(IllegalArgumentException::class.java) { tracker.update(200, listOf(detection)) }
        }
        assertEquals(initial.id, tracker.update(150, listOf(face(.11f, .41f))).single().id)
    }

    @Test fun `zero threshold still requires actual spatial overlap`() {
        val tracker = FaceTrackSmoother(iouThreshold = 0f)
        val first = tracker.update(0, listOf(face())).single()
        val other = tracker.update(1, listOf(face(.6f, .9f))).single()
        assertNotEquals(first.id, other.id)
    }

    @Test fun `boundary-crossing detector boxes remain supported`() {
        val tracker = FaceTrackSmoother()
        val first = tracker.update(0, listOf(face(-.1f, .3f))).single()
        assertEquals(first.id, tracker.update(1, listOf(face(-.09f, .31f))).single().id)
    }

    @Test fun `long timeline expires stale tracks without overflow`() {
        val tracker = FaceTrackSmoother(maxGapMs = 700)
        val first = tracker.update(0, listOf(face())).single()
        assertNotEquals(first.id, tracker.update(Long.MAX_VALUE, listOf(face())).single().id)
    }

    @Test fun `invalid privacy mesh rejects whole request without changing source`() {
        val source = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        source.eraseColor(0xff123456.toInt())
        try {
            assertThrows(IllegalArgumentException::class.java) {
                FacePrivacy.pixelateFaces(source, listOf(face(), face(Float.NaN)))
            }
            assertThrows(IllegalArgumentException::class.java) {
                FacePrivacy.pixelateFaces(source, listOf(face().copy(landmarks = emptyList())))
            }
            assertEquals(0xff123456.toInt(), source.getPixel(5, 5))
            assertFalse(source.isRecycled)
        } finally { source.recycle() }
    }

    @Test fun `maximum block size is bounded by bitmap without coordinate overflow`() {
        val source = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        try {
            val output = FacePrivacy.pixelateFaces(source, listOf(face()), Int.MAX_VALUE)
            assertEquals(10, output.width)
            assertFalse(source === output)
            output.recycle()
        } finally { source.recycle() }
    }
}
