package com.apexstudio.app.ai.autoreframe

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.apexstudio.app.ai.AiCancellationToken
import com.apexstudio.app.ai.LocalMediaSourcePolicy
import com.apexstudio.app.ai.OfflineVideoFrameDecoder
import com.apexstudio.app.ai.mediapipe.BundledMediaPipeModels
import com.apexstudio.app.ai.mediapipe.FaceTrackSmoother
import com.apexstudio.app.ai.mediapipe.TrackedFace
import com.apexstudio.app.domain.model.FaceCropKeyframe
import com.apexstudio.app.domain.model.FaceCropTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/** One actual sampled inference result; an empty [faces] list means no face was detected in that frame. */
data class FaceTrackSample(
    val timestampMs: Long,
    val faces: List<TrackedFace>,
    val reframe: ReframeFrame,
)

data class FaceTrackAnalysisResult(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val samples: List<FaceTrackSample>,
) {
    val sampledFrameCount: Int get() = samples.size

    /** Persist only the actual decoded sample timestamps and their measured crop geometry. */
    fun toPersistedCropTrack(outputAspectRatio: Float): FaceCropTrack = FaceCropTrack(
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        outputAspectRatio = outputAspectRatio,
        keyframes = samples.map { sample ->
            val crop = sample.reframe.crop
            FaceCropKeyframe(
                sourceTimestampMs = sample.timestampMs,
                left = crop.left,
                top = crop.top,
                right = crop.right,
                bottom = crop.bottom,
                faceFound = sample.reframe.faceFound,
            )
        },
    )
}

/** Keep reframing on the established face identity while visible; otherwise start from the largest visible track. */
internal object FaceTrackTargetSelector {
    fun select(faces: List<TrackedFace>, currentTargetId: Long?): TrackedFace? {
        val usable = faces.filter { face ->
            val box = face.detection.box
            val area = (box.right - box.left) * (box.bottom - box.top)
            box.left.isFinite() && box.top.isFinite() && box.right.isFinite() && box.bottom.isFinite() &&
                box.left < box.right && box.top < box.bottom &&
                area.isFinite() && area > 0f
        }
        return usable.firstOrNull { it.id == currentTargetId }
            ?: usable.maxByOrNull { face ->
                val box = face.detection.box
                (box.right - box.left) * (box.bottom - box.top)
            }
    }
}

/** Pure bounded sampling and bitmap-sizing helpers; exposed for deterministic unit tests. */
object FaceTrackSampling {
    /** End-exclusive range, approximately [minimumIntervalMs] apart, spread to the end if capped. */
    fun timestamps(
        startMs: Long,
        endMs: Long,
        minimumIntervalMs: Long,
        maximumSamples: Int,
    ): List<Long> {
        require(startMs >= 0L && endMs > startMs)
        require(minimumIntervalMs > 0L && maximumSamples in 2..360)
        val duration = endMs - startMs
        val desired = (duration - 1L) / minimumIntervalMs + 1L
        if (desired <= maximumSamples) {
            return List(desired.toInt()) { index -> startMs + index.toLong() * minimumIntervalMs }
        }
        val wanted = maximumSamples
        val span = duration - 1L
        val divisor = wanted - 1L
        return List(wanted) { index ->
            val i = index.toLong()
            // Divide before multiplying to avoid overflow on long source timelines.
            val offset = (span / divisor) * i + ((span % divisor) * i) / divisor
            startMs + offset
        }
    }

    /** Return width/height scaled down without changing aspect ratio; never upscale. */
    fun boundedDimensions(width: Int, height: Int, maximumDimension: Int): Pair<Int, Int> {
        require(width > 0 && height > 0 && maximumDimension > 0)
        val longest = maxOf(width, height)
        if (longest <= maximumDimension) return width to height
        val scale = maximumDimension.toDouble() / longest
        return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
    }
}

/**
 * Local full-range face tracking using the bundled, checksum-verified Face Landmarker model. It decodes
 * a bounded set of real frames, runs IMAGE-mode CPU inference at a bounded resolution, associates boxes
 * across sampled frames, and returns reviewable crop samples. It never downloads media/models, renders,
 * exports, or applies edits. Cancellation is checked around each synchronous decode/inference call; a
 * call already executing inside Android's decoder or MediaPipe cannot be interrupted mid-call.
 */
class LocalFaceTrackAnalyzer(
    private val minimumIntervalMs: Long = 500L,
    private val maximumSamples: Int = 180,
    private val maximumInferenceDimension: Int = 640,
    private val maximumFaces: Int = 3,
    private val outputAspectRatio: Float = 9f / 16f,
    private val cropMargin: Float = 0.35f,
) {
    init {
        require(minimumIntervalMs > 0L && maximumSamples in 2..360)
        require(maximumInferenceDimension in 64..2048 && maximumFaces in 1..10)
        require(outputAspectRatio.isFinite() && outputAspectRatio > 0f)
        require(cropMargin.isFinite() && cropMargin in 0f..2f)
    }

    /** Progress is a fraction in 0..1 and is reported after every scheduled timestamp. */
    suspend fun analyze(
        context: Context,
        uri: String,
        sourceStartMs: Long,
        sourceEndMs: Long,
        cancellation: AiCancellationToken = AiCancellationToken(),
        onProgress: (Float) -> Unit = {},
    ): FaceTrackAnalysisResult = withContext(Dispatchers.IO) {
        require(sourceStartMs >= 0L && sourceEndMs > sourceStartMs) { "Invalid source time range" }
        require(sourceEndMs - 1L <= Long.MAX_VALUE / 1_000L) { "Source timestamp exceeds decoder range" }
        val timestamps = FaceTrackSampling.timestamps(sourceStartMs, sourceEndMs, minimumIntervalMs, maximumSamples)
        cancellation.throwIfCancelled()
        currentCoroutineContext().ensureActive()

        val parsedUri = Uri.parse(uri)
        val scheme = parsedUri.scheme
        require(LocalMediaSourcePolicy.accepts(scheme, parsedUri.authority)) {
            "Offline face analysis accepts local file paths and content/resource providers, not network URLs or remote file authorities."
        }
        val localUri = if (scheme == null) Uri.fromFile(File(uri)) else parsedUri
        val retriever = MediaMetadataRetriever()
        val samples = ArrayList<FaceTrackSample>(timestamps.size)
        var sourceWidth = 0
        var sourceHeight = 0
        try {
            retriever.setDataSource(context.applicationContext, localUri)
            cancellation.throwIfCancelled()
            currentCoroutineContext().ensureActive()
            BundledMediaPipeModels.openFace(context.applicationContext, maximumFaces).use { detector ->
                val tracker = FaceTrackSmoother()
                var previousCrop: NormalizedCrop? = null
                var targetTrackId: Long? = null
                timestamps.forEachIndexed { index, timestampMs ->
                    cancellation.throwIfCancelled()
                    currentCoroutineContext().ensureActive()
                    val decoded = OfflineVideoFrameDecoder.decode(
                        retriever,
                        timestampMs * 1_000L,
                        maximumInferenceDimension,
                    )
                    if (decoded != null) {
                        val bitmap = decoded.bitmap
                        val detections = try {
                            cancellation.throwIfCancelled()
                            currentCoroutineContext().ensureActive()
                            if (sourceWidth == 0) {
                                sourceWidth = decoded.sourceWidth
                                sourceHeight = decoded.sourceHeight
                            }
                            val (targetWidth, targetHeight) = FaceTrackSampling.boundedDimensions(
                                bitmap.width, bitmap.height, maximumInferenceDimension,
                            )
                            val inferenceBitmap = if (targetWidth == bitmap.width && targetHeight == bitmap.height) bitmap
                                else Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
                            try {
                                detector.detect(inferenceBitmap)
                            } finally {
                                if (inferenceBitmap !== bitmap) inferenceBitmap.recycle()
                            }
                        } finally {
                            bitmap.recycle()
                        }
                        cancellation.throwIfCancelled()
                        currentCoroutineContext().ensureActive()
                        val tracked = tracker.update(timestampMs, detections)
                        val target = FaceTrackTargetSelector.select(tracked, targetTrackId)
                        if (target != null) targetTrackId = target.id
                        val cropFrame = FaceAwareReframeEngine.cropFor(
                            sourceWidth = sourceWidth,
                            sourceHeight = sourceHeight,
                            outputAspectRatio = outputAspectRatio,
                            faces = target?.let { listOf(it.detection) }.orEmpty(),
                            margin = cropMargin,
                            previous = previousCrop,
                        ).copy(timestampMs = timestampMs)
                        previousCrop = cropFrame.crop
                        samples += FaceTrackSample(timestampMs, tracked, cropFrame)
                    }
                    cancellation.throwIfCancelled()
                    currentCoroutineContext().ensureActive()
                    onProgress((index + 1f) / timestamps.size)
                }
            }
        } finally {
            retriever.release()
        }
        cancellation.throwIfCancelled()
        currentCoroutineContext().ensureActive()
        check(sourceWidth > 0 && sourceHeight > 0) { "No video frames could be decoded for offline face analysis" }
        FaceTrackAnalysisResult(sourceWidth, sourceHeight, samples)
    }
}
