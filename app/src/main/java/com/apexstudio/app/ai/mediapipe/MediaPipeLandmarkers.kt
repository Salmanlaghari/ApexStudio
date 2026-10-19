package com.apexstudio.app.ai.mediapipe

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Coordinates and boxes are normalized against the input bitmap (0..1, origin top-left). */
data class NormalizedPoint(val x: Float, val y: Float, val z: Float = 0f, val visibility: Float? = null)
data class NormalizedBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val centerX get() = (left + right) * 0.5f
    val centerY get() = (top + bottom) * 0.5f
}
data class FaceDetection(val box: NormalizedBox, val landmarks: List<NormalizedPoint>)
data class PoseDetection(val landmarks: List<NormalizedPoint>)
data class HandDetection(val landmarks: List<NormalizedPoint>)

/**
 * MediaPipe Tasks Vision IMAGE-mode CPU wrappers. They never download models: callers may supply a
 * reviewed local .task file, or use [BundledMediaPipeModels] to materialize the app's checksum-verified
 * bundled model. Inference is synchronous; call from a worker thread. The bitmap remains owned by the
 * caller and must not be mutated or recycled until detection returns. Calls and close are serialized per detector.
 */
object MediaPipeLandmarkers {
    internal const val MAX_TASK_MODEL_BYTES = 32 * 1024 * 1024

    private fun options(model: File): BaseOptions {
        if (!model.isFile || model.length() == 0L) {
            throw IllegalStateException("Required MediaPipe task model is unavailable locally: ${model.name}")
        }
        val buffer = modelBuffer(model)
        return BaseOptions.builder().setModelAssetBuffer(buffer).build()
    }

    /** Read a bounded local model directly into native storage; never stage an unbounded heap copy. */
    internal fun modelBuffer(model: File): ByteBuffer {
        if (!model.isFile) throw IllegalStateException("Required MediaPipe task model is unavailable locally: ${model.name}")
        val expectedBytes = model.length()
        if (expectedBytes <= 0L || expectedBytes > MAX_TASK_MODEL_BYTES) {
            throw IllegalStateException("MediaPipe task model size is outside the supported 1..$MAX_TASK_MODEL_BYTES byte range: ${model.name}")
        }
        val buffer = ByteBuffer.allocateDirect(expectedBytes.toInt()).order(ByteOrder.nativeOrder())
        FileInputStream(model).use { input ->
            val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(chunk)
                if (count < 0) break
                if (count == 0) continue
                if (count > buffer.remaining()) {
                    throw IllegalStateException("MediaPipe task model changed while it was being read: ${model.name}")
                }
                buffer.put(chunk, 0, count)
            }
        }
        if (buffer.position() != expectedBytes.toInt()) {
            throw IllegalStateException("MediaPipe task model changed while it was being read: ${model.name}")
        }
        buffer.flip()
        return buffer
    }

    fun openFace(context: Context, model: File, maxFaces: Int = 3): FaceDetector {
        require(maxFaces in 1..10)
        val opts = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(options(model))
            .setRunningMode(com.google.mediapipe.tasks.vision.core.RunningMode.IMAGE)
            .setNumFaces(maxFaces)
            .build()
        return FaceDetector(FaceLandmarker.createFromOptions(context.applicationContext, opts))
    }

    fun openPose(context: Context, model: File, maxPoses: Int = 2): PoseDetector {
        require(maxPoses in 1..10)
        val opts = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(options(model))
            .setRunningMode(com.google.mediapipe.tasks.vision.core.RunningMode.IMAGE)
            .setNumPoses(maxPoses)
            .build()
        return PoseDetector(PoseLandmarker.createFromOptions(context.applicationContext, opts))
    }

    fun openHands(context: Context, model: File, maxHands: Int = 2): HandDetector {
        require(maxHands in 1..10)
        val opts = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(options(model))
            .setRunningMode(com.google.mediapipe.tasks.vision.core.RunningMode.IMAGE)
            .setNumHands(maxHands)
            .build()
        return HandDetector(HandLandmarker.createFromOptions(context.applicationContext, opts))
    }

    class FaceDetector internal constructor(private val detector: FaceLandmarker) : Closeable {
        private var closed = false

        @Synchronized
        fun detect(bitmap: Bitmap): List<FaceDetection> {
            check(!closed) { "Face detector is closed" }
            val image = BitmapImageBuilder(bitmap).build()
            try {
                return detector.detect(image).faceLandmarks().map { marks ->
                    val points = marks.map { NormalizedPoint(it.x(), it.y(), it.z(), it.visibility().orElse(null)?.toFloat()) }
                    val xs = points.map { it.x }; val ys = points.map { it.y }
                    FaceDetection(
                        NormalizedBox(xs.minOrNull() ?: 0f, ys.minOrNull() ?: 0f,
                            xs.maxOrNull() ?: 0f, ys.maxOrNull() ?: 0f), points
                    )
                }
            } finally { image.close() }
        }
        /** Runs CPU inference off the caller thread and drops any result if cancellation arrived meanwhile. */
        suspend fun detectCancellable(bitmap: Bitmap): List<FaceDetection> = withContext(Dispatchers.Default) {
            currentCoroutineContext().ensureActive()
            detect(bitmap).also { currentCoroutineContext().ensureActive() }
        }

        @Synchronized
        override fun close() {
            if (closed) return
            closed = true
            detector.close()
        }
    }

    class PoseDetector internal constructor(private val detector: PoseLandmarker) : Closeable {
        private var closed = false

        @Synchronized
        fun detect(bitmap: Bitmap): List<PoseDetection> {
            check(!closed) { "Pose detector is closed" }
            val image = BitmapImageBuilder(bitmap).build()
            try {
                return detector.detect(image).landmarks().map { marks ->
                    PoseDetection(marks.map { NormalizedPoint(it.x(), it.y(), it.z(), it.visibility().orElse(null)?.toFloat()) })
                }
            } finally { image.close() }
        }
        /** Native inference itself is synchronous; cancellation prevents stale results from escaping after it returns. */
        suspend fun detectCancellable(bitmap: Bitmap): List<PoseDetection> = withContext(Dispatchers.Default) {
            currentCoroutineContext().ensureActive()
            detect(bitmap).also { currentCoroutineContext().ensureActive() }
        }

        @Synchronized
        override fun close() {
            if (closed) return
            closed = true
            detector.close()
        }
    }

    class HandDetector internal constructor(private val detector: HandLandmarker) : Closeable {
        private var closed = false

        @Synchronized
        fun detect(bitmap: Bitmap): List<HandDetection> {
            check(!closed) { "Hand detector is closed" }
            val image = BitmapImageBuilder(bitmap).build()
            try {
                return detector.detect(image).landmarks().map { marks ->
                    HandDetection(marks.map { NormalizedPoint(it.x(), it.y(), it.z(), it.visibility().orElse(null)?.toFloat()) })
                }
            } finally { image.close() }
        }
        /** Native inference itself is synchronous; cancellation prevents stale results from escaping after it returns. */
        suspend fun detectCancellable(bitmap: Bitmap): List<HandDetection> = withContext(Dispatchers.Default) {
            currentCoroutineContext().ensureActive()
            detect(bitmap).also { currentCoroutineContext().ensureActive() }
        }

        @Synchronized
        override fun close() {
            if (closed) return
            closed = true
            detector.close()
        }
    }
}
