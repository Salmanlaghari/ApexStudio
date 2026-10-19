package com.apexstudio.app.ai.mediapipe

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MediaPipeLandmarkFeaturesTest {
    @Test
    fun `skeleton topologies use MediaPipe landmark indices`() {
        assertTrue(LandmarkConnections.POSE.contains(11 to 13))
        assertTrue(LandmarkConnections.POSE.contains(23 to 25))
        assertTrue(LandmarkConnections.HAND.contains(0 to 1))
        // Little finger is 17 -> 18 -> 19 -> 20, not a direct 17 -> 20 edge.
        assertTrue(LandmarkConnections.HAND.contains(19 to 20))
        assertTrue(LandmarkConnections.HAND.all { (a, b) -> a in 0..20 && b in 0..20 })
    }

    @Test
    fun `gesture primitives distinguish open hand fist and pointing from landmarks`() {
        assertEquals(GesturePrimitives.Gesture.OPEN_PALM, GesturePrimitives.classify(hand(extended = setOf(0, 1, 2, 3, 4))))
        assertEquals(GesturePrimitives.Gesture.FIST, GesturePrimitives.classify(hand(extended = emptySet())))
        assertEquals(GesturePrimitives.Gesture.POINTING, GesturePrimitives.classify(hand(extended = setOf(1))))
        assertEquals(GesturePrimitives.Gesture.UNKNOWN, GesturePrimitives.classify(HandDetection(emptyList())))
    }

    @Test
    fun `face tracker preserves local track id and expires stale detections`() {
        val tracker = FaceTrackSmoother(iouThreshold = 0.1f, smoothing = 1f, maxGapMs = 100)
        val first = tracker.update(10, listOf(face(NormalizedBox(0.1f, 0.1f, 0.4f, 0.4f)))).single()
        val second = tracker.update(50, listOf(face(NormalizedBox(0.12f, 0.1f, 0.42f, 0.4f)))).single()
        val afterGap = tracker.update(200, listOf(face(NormalizedBox(0.12f, 0.1f, 0.42f, 0.4f)))).single()
        assertEquals(first.id, second.id)
        assertNotEquals(second.id, afterGap.id)
        assertEquals(0.12f, second.detection.box.left, 0.0001f)
    }

    @Test
    fun `face mesh hull pixelates the detected region and leaves outside pixels alone`() {
        val source = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(400) { index ->
            val x = index % 20
            val y = index / 20
            (0xff shl 24) or ((x * 10) shl 16) or ((y * 10) shl 8)
        }
        source.setPixels(pixels, 0, 20, 0, 0, 20, 20)
        val detection = FaceDetection(
            NormalizedBox(0.25f, 0.25f, 0.75f, 0.75f),
            listOf(
                NormalizedPoint(0.25f, 0.25f), NormalizedPoint(0.75f, 0.25f),
                NormalizedPoint(0.75f, 0.75f), NormalizedPoint(0.25f, 0.75f),
                NormalizedPoint(0.5f, 0.5f),
            ),
        )
        val output = FacePrivacy.pixelateFaces(source, listOf(detection), blockSize = 4)
        try {
            assertNotEquals(source.getPixel(6, 5), output.getPixel(6, 5))
            assertEquals(source.getPixel(1, 1), output.getPixel(1, 1))
            assertTrue(output !== source)
        } finally {
            source.recycle()
            output.recycle()
        }
    }

    private fun face(box: NormalizedBox) = FaceDetection(box, listOf(NormalizedPoint(box.centerX, box.centerY)))

    private fun hand(extended: Set<Int>): HandDetection {
        val points = MutableList(21) { NormalizedPoint(0.5f, 0.85f) }
        points[0] = NormalizedPoint(0.5f, 0.9f)
        points[9] = NormalizedPoint(0.5f, 0.5f)
        val fingerIndices = listOf(
            Triple(0, 3, 4), Triple(1, 6, 8), Triple(2, 10, 12),
            Triple(3, 14, 16), Triple(4, 18, 20),
        )
        for ((finger, pip, tip) in fingerIndices) {
            val x = when (finger) { 0 -> 0.22f; 1 -> 0.36f; 2 -> 0.5f; 3 -> 0.64f; else -> 0.78f }
            points[pip] = if (finger in extended) NormalizedPoint(x, 0.36f) else NormalizedPoint(x, 0.72f)
            points[tip] = if (finger in extended) NormalizedPoint(x, 0.08f) else NormalizedPoint(x, 0.75f)
        }
        // Set MCP bases so all four fingers share a plausible palm anchor.
        listOf(5, 9, 13, 17).forEachIndexed { index, mcp -> points[mcp] = NormalizedPoint(0.3f + index * 0.13f, 0.52f) }
        points[2] = NormalizedPoint(0.3f, 0.7f)
        points[3] = if (0 in extended) NormalizedPoint(0.2f, 0.5f) else NormalizedPoint(0.27f, 0.76f)
        points[4] = if (0 in extended) NormalizedPoint(0.08f, 0.3f) else NormalizedPoint(0.25f, 0.8f)
        return HandDetection(points)
    }
}
