package com.apexstudio.app.ai.autoreframe

import com.apexstudio.app.ai.tflite.LumaFrame
import kotlin.math.abs
import kotlin.math.max

/**
 * Rule-based crop from local luma gradients, suitable as an optional suggestion when no face model
 * is installed. It is not face detection; the returned metadata always states the signal source.
 */
data class AutoCropSuggestion(val crop: NormalizedCrop, val attentionCenterX: Float, val attentionCenterY: Float, val method: String)

object SignalAutoCrop {
    fun suggest(frame: LumaFrame, outputAspectRatio: Float, previous: NormalizedCrop? = null,
        smoothing: Float = 0.4f, sourceAspectRatio: Float = frame.width.toFloat() / frame.height): AutoCropSuggestion {
        require(outputAspectRatio.isFinite() && outputAspectRatio > 0f && smoothing in 0f..1f)
        require(sourceAspectRatio.isFinite() && sourceAspectRatio > 0f)
        // Luma samples can be stretched to a fixed analysis size. Crop geometry must use the actual
        // decoded source aspect, otherwise portrait and square inputs are mistakenly treated as 16:9.
        val inputAspect = sourceAspectRatio
        val (cropW, cropH) = if (inputAspect >= outputAspectRatio) outputAspectRatio / inputAspect to 1f
            else 1f to inputAspect / outputAspectRatio
        var weightSum = 0.0; var xSum = 0.0; var ySum = 0.0
        if (frame.width >= 3 && frame.height >= 3) {
            for (y in 1 until frame.height - 1) for (x in 1 until frame.width - 1) {
                val i = y * frame.width + x
                val gx = abs(frame.luma[i + 1] - frame.luma[i - 1])
                val gy = abs(frame.luma[i + frame.width] - frame.luma[i - frame.width])
                val energy = (gx + gy).toDouble()
                xSum += (x.toDouble() / (frame.width - 1)) * energy
                ySum += (y.toDouble() / (frame.height - 1)) * energy
                weightSum += energy
            }
        }
        val targetX = if (weightSum > 0) (xSum / weightSum).toFloat() else 0.5f
        val targetY = if (weightSum > 0) (ySum / weightSum).toFloat() else 0.5f
        val x = ((previous?.centerX ?: targetX) + (targetX - (previous?.centerX ?: targetX)) * smoothing)
            .coerceIn(cropW / 2f, 1f - cropW / 2f)
        val y = ((previous?.centerY ?: targetY) + (targetY - (previous?.centerY ?: targetY)) * smoothing)
            .coerceIn(cropH / 2f, 1f - cropH / 2f)
        return AutoCropSuggestion(NormalizedCrop(x - cropW / 2f, y - cropH / 2f,
            x + cropW / 2f, y + cropH / 2f), targetX, targetY,
            "Luma-gradient centroid only; not a face/subject detector or learned saliency model.")
    }
}
