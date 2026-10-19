package com.apexstudio.app.ai.mediapipe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.google.mediapipe.framework.image.BitmapExtractor
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import java.io.Closeable
import java.io.File
import java.nio.ByteOrder
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Portrait background effect. The input frame must have already been decoded/oriented by the caller. */
sealed interface PortraitEffect {
    data class Blur(val strength: Float = 0.6f) : PortraitEffect
    data class Replace(val background: Bitmap) : PortraitEffect
    data class Solid(val color: Int) : PortraitEffect
}

/** Confidence values are row-major person probabilities in the range [0, 1]. */
internal data class PortraitConfidenceMask(val width: Int, val height: Int, val values: FloatArray)

/** Small seam around native inference; tests may supply mask data without pretending to run device inference. */
internal interface PortraitSegmenter : Closeable {
    fun segment(frame: Bitmap): PortraitConfidenceMask
}

/** Synchronous native inference is serialized with close; use [processCancellable] from coroutine workers. */
class MediaPipePortraitEffects internal constructor(private val segmenter: PortraitSegmenter) : Closeable {
    private var closed = false

    /** Segment the frame with the actual MediaPipe model, then blur or replace only the background. */
    @Synchronized
    fun process(frame: Bitmap, effect: PortraitEffect): Bitmap {
        check(!closed) { "Portrait effects processor is closed" }
        requireLiveBitmap(frame, "Frame")
        requirePixelCount(frame.width, frame.height, "Frame")
        when (effect) {
            is PortraitEffect.Blur -> require(effect.strength.isFinite()) { "Blur strength must be finite" }
            is PortraitEffect.Replace -> {
                requireLiveBitmap(effect.background, "Replacement background")
                requirePixelCount(effect.background.width, effect.background.height, "Replacement background")
            }
            is PortraitEffect.Solid -> Unit
        }

        val personMask = segmenter.segment(frame)
        validateMask(personMask)
        val background = when (effect) {
            is PortraitEffect.Blur -> makeBlurredBackground(frame, effect.strength)
            is PortraitEffect.Replace -> coverFit(effect.background, frame.width, frame.height)
            is PortraitEffect.Solid -> Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
                .also { it.eraseColor(effect.color) }
        }
        val callerOwnedBackground = (effect as? PortraitEffect.Replace)?.background
        return try {
            composite(frame, background, personMask)
        } finally {
            if (background !== frame && background !== callerOwnedBackground && !background.isRecycled) {
                background.recycle()
            }
        }
    }

    /** Runs synchronous inference on a worker and discards/recycles a result if cancellation arrives meanwhile. */
    suspend fun processCancellable(frame: Bitmap, effect: PortraitEffect): Bitmap = withContext(Dispatchers.Default) {
        currentCoroutineContext().ensureActive()
        val output = process(frame, effect)
        try {
            currentCoroutineContext().ensureActive()
            output
        } catch (cancelled: Throwable) {
            if (!output.isRecycled) output.recycle()
            throw cancelled
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        segmenter.close()
    }

    private fun validateMask(mask: PortraitConfidenceMask) {
        require(mask.width > 0 && mask.height > 0) { "MediaPipe returned an empty person mask" }
        val count = requirePixelCount(mask.width, mask.height, "Person mask")
        require(mask.values.size == count) { "MediaPipe confidence mask has an invalid buffer size" }
        require(mask.values.all { it.isFinite() && it in 0f..1f }) {
            "MediaPipe confidence mask contains an invalid confidence value"
        }
    }

    private fun makeBlurredBackground(frame: Bitmap, strength: Float): Bitmap {
        val divisor = (4f + strength.coerceIn(0f, 1f) * 20f).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(frame, max(1, frame.width / divisor), max(1, frame.height / divisor), true)
        return try {
            Bitmap.createScaledBitmap(small, frame.width, frame.height, true)
        } finally {
            // Android may return the input object when a requested scale is unchanged (notably 1x1 inputs).
            if (small !== frame && !small.isRecycled) small.recycle()
        }
    }

    private fun coverFit(source: Bitmap, width: Int, height: Int): Bitmap {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val scale = max(width.toFloat() / source.width, height.toFloat() / source.height)
            val transform = Matrix().apply {
                setScale(scale, scale)
                postTranslate((width - source.width * scale) / 2f, (height - source.height * scale) / 2f)
            }
            Canvas(output).drawBitmap(source, transform, Paint(Paint.FILTER_BITMAP_FLAG))
            return output
        } catch (failure: Throwable) {
            if (!output.isRecycled) output.recycle()
            throw failure
        }
    }

    private fun composite(frame: Bitmap, background: Bitmap, mask: PortraitConfidenceMask): Bitmap {
        val w = frame.width
        val h = frame.height
        val foregroundPixels = IntArray(requirePixelCount(w, h, "Frame"))
        val backgroundPixels = IntArray(foregroundPixels.size)
        frame.getPixels(foregroundPixels, 0, w, 0, 0, w, h)
        background.getPixels(backgroundPixels, 0, w, 0, 0, w, h)
        val outputPixels = IntArray(foregroundPixels.size)
        for (y in 0 until h) {
            val my = ((y + 0.5f) * mask.height / h - 0.5f).coerceIn(0f, (mask.height - 1).toFloat())
            val y0 = my.toInt()
            val y1 = minOf(y0 + 1, mask.height - 1)
            val fy = my - y0
            for (x in 0 until w) {
                val mx = ((x + 0.5f) * mask.width / w - 0.5f).coerceIn(0f, (mask.width - 1).toFloat())
                val x0 = mx.toInt()
                val x1 = minOf(x0 + 1, mask.width - 1)
                val fx = mx - x0
                val a = mask.values[y0 * mask.width + x0]
                val b = mask.values[y0 * mask.width + x1]
                val c = mask.values[y1 * mask.width + x0]
                val d = mask.values[y1 * mask.width + x1]
                val confidence = (a * (1f - fx) + b * fx) * (1f - fy) + (c * (1f - fx) + d * fx) * fy
                val alpha = smoothstep(confidence, 0.35f, 0.65f)
                val fg = foregroundPixels[y * w + x]
                val bg = backgroundPixels[y * w + x]
                val red = blend((bg ushr 16) and 0xff, (fg ushr 16) and 0xff, alpha)
                val green = blend((bg ushr 8) and 0xff, (fg ushr 8) and 0xff, alpha)
                val blue = blend(bg and 0xff, fg and 0xff, alpha)
                outputPixels[y * w + x] = (0xff shl 24) or (red shl 16) or (green shl 8) or blue
            }
        }
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            output.setPixels(outputPixels, 0, w, 0, 0, w, h)
            return output
        } catch (failure: Throwable) {
            if (!output.isRecycled) output.recycle()
            throw failure
        }
    }

    private fun blend(background: Int, foreground: Int, alpha: Float): Int =
        (background + (foreground - background) * alpha).toInt().coerceIn(0, 255)

    private fun smoothstep(value: Float, low: Float, high: Float): Float {
        val t = ((value - low) / (high - low)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    companion object {
        fun open(context: Context, modelFile: File): MediaPipePortraitEffects {
            require(modelFile.isFile && modelFile.length() > 0) { "Missing bundled selfie segmentation model" }
            val bytes = modelFile.readBytes()
            val buffer = java.nio.ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
                .apply { put(bytes); rewind() }
            val options = ImageSegmenter.ImageSegmenterOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetBuffer(buffer).setDelegate(Delegate.CPU).build())
                .setRunningMode(RunningMode.IMAGE)
                .setOutputCategoryMask(false)
                .setOutputConfidenceMasks(true)
                .build()
            return MediaPipePortraitEffects(MediaPipeImageSegmenter(ImageSegmenter.createFromOptions(context.applicationContext, options)))
        }

        private fun requireLiveBitmap(bitmap: Bitmap, label: String) {
            require(!bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0) { "$label must be a live bitmap" }
        }

        private fun requirePixelCount(width: Int, height: Int, label: String): Int {
            require(width > 0 && height > 0 && width <= Int.MAX_VALUE / height) { "$label dimensions are invalid or too large" }
            return width * height
        }
    }
}

private class MediaPipeImageSegmenter(private val segmenter: ImageSegmenter) : PortraitSegmenter {
    override fun segment(frame: Bitmap): PortraitConfidenceMask {
        val image = BitmapImageBuilder(frame).build()
        try {
            val result = segmenter.segment(image)
            val masks = result.confidenceMasks().orElse(null).orEmpty()
            try {
                val mask = masks.lastOrNull()
                    ?: throw IllegalStateException("MediaPipe returned no person confidence mask")
                val width = mask.width
                val height = mask.height
                val count = requireMaskPixelCount(width, height)
                val values = maskToFloatArray(mask, width, height, count)
                return PortraitConfidenceMask(width, height, values)
            } finally {
                masks.forEach { it.close() }
            }
        } finally {
            image.close()
        }
    }

    override fun close() = segmenter.close()

    private fun maskToFloatArray(mask: MPImage, width: Int, height: Int, count: Int): FloatArray {
        val format = mask.getContainedImageProperties().firstOrNull()?.imageFormat
        if (format == MPImage.IMAGE_FORMAT_VEC32F1) {
            val buffer = ByteBufferExtractor.extract(mask).duplicate().order(ByteOrder.nativeOrder())
            buffer.rewind()
            val floatBuffer = buffer.asFloatBuffer()
            check(floatBuffer.remaining() >= count) { "MediaPipe confidence mask buffer is truncated" }
            return FloatArray(count).also(floatBuffer::get)
        }
        val bitmap = BitmapExtractor.extract(mask)
        try {
            require(bitmap.width == width && bitmap.height == height) { "MediaPipe bitmap mask dimensions do not match" }
            val pixels = IntArray(count)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            return FloatArray(count) { ((pixels[it] ushr 16) and 0xff) / 255f }
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun requireMaskPixelCount(width: Int, height: Int): Int {
        require(width > 0 && height > 0 && width <= Int.MAX_VALUE / height) { "MediaPipe returned an invalid person mask size" }
        return width * height
    }
}
