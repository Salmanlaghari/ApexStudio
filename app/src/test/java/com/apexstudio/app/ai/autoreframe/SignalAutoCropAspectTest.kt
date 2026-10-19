package com.apexstudio.app.ai.autoreframe

import com.apexstudio.app.ai.tflite.LumaFrame
import org.junit.Assert.assertEquals
import org.junit.Test

class SignalAutoCropAspectTest {
    private val stretchedSample = LumaFrame(0L, 32, 18, FloatArray(32 * 18) { .5f })

    @Test fun portraitSourceDoesNotGetCroppedAsLandscapeThumbnail() {
        val crop = SignalAutoCrop.suggest(stretchedSample, 9f / 16f, sourceAspectRatio = 9f / 16f).crop
        assertEquals(0f, crop.left, .0001f)
        assertEquals(1f, crop.right, .0001f)
        assertEquals(0f, crop.top, .0001f)
        assertEquals(1f, crop.bottom, .0001f)
    }

    @Test fun squareSourceUsesSquareGeometryDespiteWideSample() {
        val crop = SignalAutoCrop.suggest(stretchedSample, 1f, sourceAspectRatio = 1f).crop
        assertEquals(1f, crop.right - crop.left, .0001f)
        assertEquals(1f, crop.bottom - crop.top, .0001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidSourceAspectIsRejected() {
        SignalAutoCrop.suggest(stretchedSample, 1f, sourceAspectRatio = Float.NaN)
    }
}
