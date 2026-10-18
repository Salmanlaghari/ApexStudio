package com.apexstudio.app.ai.autoreframe

import com.apexstudio.app.ai.mediapipe.FaceDetection
import com.apexstudio.app.ai.mediapipe.NormalizedBox
import com.apexstudio.app.ai.tflite.LumaFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoReframeTest {
    @Test fun faceBoxMovesCropToEdgeWithoutLeavingSourceBounds() {
        val face = FaceDetection(NormalizedBox(.84f, .30f, .96f, .50f), emptyList())
        val frame = FaceAwareReframeEngine.cropFor(1920, 1080, 1f, listOf(face), margin = .25f)
        assertTrue(frame.faceFound)
        assertTrue(frame.crop.left >= 0f && frame.crop.right <= 1f)
        assertTrue(frame.crop.top >= 0f && frame.crop.bottom <= 1f)
        assertEquals(.5625f, frame.crop.right - frame.crop.left, .0001f)
        assertTrue(frame.crop.right >= face.box.right)
    }

    @Test fun noFaceUsesHonestCenteredFallbackAndTrackPreservesTimes() {
        val frame = FaceAwareReframeEngine.cropFor(1920, 1080, 1f, emptyList())
        assertFalse(frame.faceFound)
        assertEquals(.5f, frame.crop.centerX, .0001f)
        val track = FaceAwareReframeEngine.track(1920, 1080, 1f, listOf(10L to emptyList(), 20L to emptyList()))
        assertEquals(listOf(10L, 20L), track.map { it.timestampMs })
        assertTrue(track.all { !it.faceFound })
    }

    @Test fun signalCropIsAspectPreservingAndLabelsItsNonFaceMethod() {
        val pixels = FloatArray(100) { i -> if (i % 10 > 6) 1f else 0f }
        val suggestion = SignalAutoCrop.suggest(LumaFrame(0, 10, 10, pixels), 1f)
        assertEquals(1f, suggestion.crop.right - suggestion.crop.left, .0001f)
        assertEquals(1f, suggestion.crop.bottom - suggestion.crop.top, .0001f)
        assertTrue(suggestion.method.contains("not a face"))
    }
}
