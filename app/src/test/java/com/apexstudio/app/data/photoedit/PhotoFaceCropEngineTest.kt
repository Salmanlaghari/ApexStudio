package com.apexstudio.app.data.photoedit

import com.apexstudio.app.ai.mediapipe.FaceDetection
import com.apexstudio.app.ai.mediapipe.NormalizedBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoFaceCropEngineTest {
    @Test fun largestFaceGetsAClampedCropWithRequestedSourcePixelAspect() {
        val faces = listOf(
            FaceDetection(NormalizedBox(.08f, .2f, .18f, .4f), emptyList()),
            FaceDetection(NormalizedBox(.70f, .25f, .91f, .64f), emptyList())
        )
        val crop = PhotoFaceCropEngine.cropAroundLargestFace(
            sourceWidth = 1200,
            sourceHeight = 800,
            faces = faces,
            outputAspectRatio = 4f / 5f
        )!!
        assertTrue(crop.left >= 0f && crop.top >= 0f && crop.right <= 1f && crop.bottom <= 1f)
        val pixelAspect = crop.width() * 1200f / (crop.height() * 800f)
        assertEquals(4f / 5f, pixelAspect, 0.002f)
        assertTrue("Largest face should be inside the crop", crop.left < .70f && crop.right > .91f)
        assertTrue(crop.top < .25f && crop.bottom > .64f)
    }

    @Test fun freeCropKeepsSourceAspectAndNoFacesDoesNotInventAFrame() {
        assertNull(PhotoFaceCropEngine.cropAroundLargestFace(640, 480, emptyList()))
        val face = FaceDetection(NormalizedBox(.4f, .3f, .6f, .7f), emptyList())
        val crop = PhotoFaceCropEngine.cropAroundLargestFace(640, 480, listOf(face))!!
        assertEquals(640f / 480f, crop.width() * 640f / (crop.height() * 480f), 0.002f)
    }

    @Test fun outOfFrameDetectionIsClampedBeforeCropGeometry() {
        val face = FaceDetection(NormalizedBox(-.2f, .1f, .3f, 1.4f), emptyList())
        val crop = PhotoFaceCropEngine.cropAroundLargestFace(300, 500, listOf(face), outputAspectRatio = 9f / 16f)!!
        assertTrue(crop.left >= 0f && crop.top >= 0f && crop.right <= 1f && crop.bottom <= 1f)
        assertEquals(9f / 16f, crop.width() * 300f / (crop.height() * 500f), 0.002f)
    }
}
