package com.apexstudio.app.data.ar

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * On-device face detection engine using Google ML Kit.
 * Extracts normalized facial landmarks (eyes, nose, mouth, bounding box, tilt angles)
 * for real-time AR filter tracking on preview and camera.
 */
data class DetectedFaceData(
    val bounds: Rect,
    val leftEye: PointF?,
    val rightEye: PointF?,
    val noseBase: PointF?,
    val mouthCenter: PointF?,
    val leftCheek: PointF?,
    val rightCheek: PointF?,
    val headEulerAngleY: Float,
    val headEulerAngleZ: Float,
    val imageWidth: Int,
    val imageHeight: Int
) {
    /**
     * Normalized coordinates (0f..1f) for Compose Canvas rendering.
     */
    val normBoundsLeft: Float get() = if (imageWidth > 0) bounds.left.toFloat() / imageWidth else 0.25f
    val normBoundsTop: Float get() = if (imageHeight > 0) bounds.top.toFloat() / imageHeight else 0.20f
    val normBoundsRight: Float get() = if (imageWidth > 0) bounds.right.toFloat() / imageWidth else 0.75f
    val normBoundsBottom: Float get() = if (imageHeight > 0) bounds.bottom.toFloat() / imageHeight else 0.80f

    val normLeftEye: PointF get() = leftEye?.let { PointF(it.x / imageWidth, it.y / imageHeight) }
        ?: PointF(0.38f, 0.40f)
    val normRightEye: PointF get() = rightEye?.let { PointF(it.x / imageWidth, it.y / imageHeight) }
        ?: PointF(0.62f, 0.40f)
    val normNose: PointF get() = noseBase?.let { PointF(it.x / imageWidth, it.y / imageHeight) }
        ?: PointF(0.50f, 0.50f)
    val normMouth: PointF get() = mouthCenter?.let { PointF(it.x / imageWidth, it.y / imageHeight) }
        ?: PointF(0.50f, 0.65f)

    companion object {
        val DEFAULT = DetectedFaceData(
            bounds = Rect(200, 200, 800, 900),
            leftEye = PointF(380f, 400f),
            rightEye = PointF(620f, 400f),
            noseBase = PointF(500f, 500f),
            mouthCenter = PointF(500f, 650f),
            leftCheek = PointF(330f, 530f),
            rightCheek = PointF(670f, 530f),
            headEulerAngleY = 0f,
            headEulerAngleZ = 0f,
            imageWidth = 1000,
            imageHeight = 1000
        )
    }
}

object ArFaceEngine {
    private val detectorOptions by lazy {
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.15f)
            .build()
    }

    private val detector by lazy {
        FaceDetection.getClient(detectorOptions)
    }

    suspend fun detectFaces(bitmap: Bitmap): List<DetectedFaceData> = suspendCancellableCoroutine { continuation ->
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            detector.process(image)
                .addOnSuccessListener { faces: List<Face> ->
                    val list = faces.map { face ->
                        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
                        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
                        val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position
                        val mouth = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position
                        val leftCheek = face.getLandmark(FaceLandmark.LEFT_CHEEK)?.position
                        val rightCheek = face.getLandmark(FaceLandmark.RIGHT_CHEEK)?.position

                        DetectedFaceData(
                            bounds = face.boundingBox,
                            leftEye = leftEye,
                            rightEye = rightEye,
                            noseBase = nose,
                            mouthCenter = mouth,
                            leftCheek = leftCheek,
                            rightCheek = rightCheek,
                            headEulerAngleY = face.headEulerAngleY,
                            headEulerAngleZ = face.headEulerAngleZ,
                            imageWidth = bitmap.width,
                            imageHeight = bitmap.height
                        )
                    }
                    continuation.resume(list)
                }
                .addOnFailureListener { error ->
                    Log.w("ArFaceEngine", "Face detection failed: ${error.message}")
                    continuation.resume(emptyList())
                }
        } catch (e: Exception) {
            Log.e("ArFaceEngine", "Error launching face detection", e)
            continuation.resume(emptyList())
        }
    }
}
