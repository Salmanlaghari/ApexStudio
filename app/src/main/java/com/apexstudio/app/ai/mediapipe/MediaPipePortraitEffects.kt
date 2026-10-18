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

/** Portrait background effect. The input frame must have already been decoded/oriented by the caller. */
sealed interface PortraitEffect {
    data class Blur(val strength: Float = 0.6f) : PortraitEffect
    data class Replace(val background: Bitmap) : PortraitEffect
    data class Solid(val color: Int) : PortraitEffect
}

/** Selfie-segmentation model driven portrait effects; inference is synchronous, call on a worker thread. */
class MediaPipePortraitEffects private constructor(private val segmenter: ImageSegmenter) : Closeable {
    /** Segment the frame with the actual MediaPipe model, then blur or replace only the background. */
    fun process(frame: Bitmap, effect: PortraitEffect): Bitmap {
        require(!frame.isRecycled && frame.width > 0 && frame.height > 0) { "Frame must be a live bitmap" }
        val image = BitmapImageBuilder(frame).build()
        val personMask: FloatArray
        val maskWidth: Int
        val maskHeight: Int
        try {
            val result = segmenter.segment(image)
            // The official selfie model has category 0=background, 1=person; request confidence maps.
            val masks = result.confidenceMasks().orElse(null).orEmpty()
            val mask = masks.lastOrNull()
                ?: throw IllegalStateException("MediaPipe returned no person confidence mask")
            try {
                maskWidth = mask.width
                maskHeight = mask.height
                require(maskWidth > 0 && maskHeight > 0) { "MediaPipe returned an empty person mask" }
                personMask = maskToFloatArray(mask, maskWidth, maskHeight)
            } finally {
                masks.forEach { it.close() }
            }
        } finally {
            image.close()
        }
        val background = when (effect) {
            is PortraitEffect.Blur -> makeBlurredBackground(frame, effect.strength)
            is PortraitEffect.Replace -> coverFit(effect.background, frame.width, frame.height)
            is PortraitEffect.Solid -> Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
                .also { it.eraseColor(effect.color) }
        }
        return try {
            composite(frame, background, personMask, maskWidth, maskHeight)
        } finally {
            if (background !== frame && background !== (effect as? PortraitEffect.Replace)?.background && !background.isRecycled) {
                background.recycle()
            }
        }
    }

    override fun close() = segmenter.close()

    private fun maskToFloatArray(mask: MPImage, width: Int, height: Int): FloatArray {
        val format = mask.getContainedImageProperties().firstOrNull()?.imageFormat
        if (format == MPImage.IMAGE_FORMAT_VEC32F1) {
            val buffer = ByteBufferExtractor.extract(mask).duplicate().order(ByteOrder.nativeOrder())
            buffer.rewind()
            val floatBuffer = buffer.asFloatBuffer()
            check(floatBuffer.remaining() >= width * height) { "MediaPipe confidence mask buffer is truncated" }
            return FloatArray(width * height).also(floatBuffer::get)
        }
        val bitmap = BitmapExtractor.extract(mask)
        try {
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            return FloatArray(pixels.size) { ((pixels[it] ushr 16) and 0xff) / 255f }
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun makeBlurredBackground(frame: Bitmap, strength: Float): Bitmap {
        val divisor = (4f + strength.coerceIn(0f, 1f) * 20f).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(frame, max(1, frame.width / divisor), max(1, frame.height / divisor), true)
        return try {
            Bitmap.createScaledBitmap(small, frame.width, frame.height, true)
        } finally {
            if (small !== frame && !small.isRecycled) small.recycle()
        }
    }

    private fun coverFit(source: Bitmap, width: Int, height: Int): Bitmap {
        require(!source.isRecycled) { "Replacement background bitmap has been recycled" }
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val scale = max(width.toFloat() / source.width, height.toFloat() / source.height)
        val transform = Matrix().apply {
            setScale(scale, scale)
            postTranslate((width - source.width * scale) / 2f, (height - source.height * scale) / 2f)
        }
        Canvas(output).drawBitmap(source, transform, Paint(Paint.FILTER_BITMAP_FLAG))
        return output
    }

    private fun composite(frame: Bitmap, background: Bitmap, mask: FloatArray, maskW: Int, maskH: Int): Bitmap {
        val w = frame.width
        val h = frame.height
        val foregroundPixels = IntArray(w * h)
        val backgroundPixels = IntArray(w * h)
        frame.getPixels(foregroundPixels, 0, w, 0, 0, w, h)
        background.getPixels(backgroundPixels, 0, w, 0, 0, w, h)
        val outputPixels = IntArray(w * h)
        for (y in 0 until h) {
            val my = ((y + 0.5f) * maskH / h - 0.5f).coerceIn(0f, (maskH - 1).toFloat())
            val y0 = my.toInt()
            val y1 = minOf(y0 + 1, maskH - 1)
            val fy = my - y0
            for (x in 0 until w) {
                val mx = ((x + 0.5f) * maskW / w - 0.5f).coerceIn(0f, (maskW - 1).toFloat())
                val x0 = mx.toInt()
                val x1 = minOf(x0 + 1, maskW - 1)
                val fx = mx - x0
                val a = mask[y0 * maskW + x0]
                val b = mask[y0 * maskW + x1]
                val c = mask[y1 * maskW + x0]
                val d = mask[y1 * maskW + x1]
                val confidence = ((a * (1f - fx) + b * fx) * (1f - fy) + (c * (1f - fx) + d * fx) * fy)
                    .coerceIn(0f, 1f)
                val alpha = smoothstep(confidence, 0.35f, 0.65f)
                val fg = foregroundPixels[y * w + x]
                val bg = backgroundPixels[y * w + x]
                val red = blend((bg ushr 16) and 0xff, (fg ushr 16) and 0xff, alpha)
                val green = blend((bg ushr 8) and 0xff, (fg ushr 8) and 0xff, alpha)
                val blue = blend(bg and 0xff, fg and 0xff, alpha)
                outputPixels[y * w + x] = (0xff shl 24) or (red shl 16) or (green shl 8) or blue
            }
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            it.setPixels(outputPixels, 0, w, 0, 0, w, h)
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
            return MediaPipePortraitEffects(ImageSegmenter.createFromOptions(context.applicationContext, options))
        }
    }
}
