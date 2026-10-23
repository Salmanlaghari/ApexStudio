package com.apexstudio.app.data.export

import androidx.media3.common.Effect
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.GlMatrixTransformation
import com.apexstudio.app.data.effect.AspectCropTransformation

/** Output settings expose 24–120 fps; clamp stale or programmatic values to that range. */
internal fun effectiveExportFrameRate(requestedFps: Int): Int = requestedFps.coerceIn(24, 120)

/** Requested output settings, shared by selected-clip and sequential-project exports. */
internal fun exportTargetBitrate(config: ExportEngine.ExportConfig): Int {
    val base = if (config.bitrateMbps > 0) {
        config.bitrateMbps.coerceAtMost(100) * 1_000_000
    } else when (config.resolution.lowercase()) {
        "8k", "4320p" -> 100_000_000
        "4k", "2160p" -> 50_000_000
        "1440p" -> 24_000_000
        "720p" -> 6_000_000
        else -> 18_000_000
    }
    // An explicit bitrate is authoritative; quality only adjusts the automatic preset.
    if (config.bitrateMbps > 0) return base
    val qualityFactor = when (config.quality.lowercase()) {
        "low" -> 0.5f
        "medium" -> 0.75f
        else -> 1f
    }
    // The preset values are calibrated around 60 fps. Keep more bits per second
    // for high-rate exports so bits per encoded frame do not collapse at 90/120 fps.
    // Lowering FPS must not silently downgrade the chosen quality preset.
    val frameRateFactor = (effectiveExportFrameRate(config.fps) / 60f).coerceIn(1f, 2f)
    return (base * qualityFactor * frameRateFactor).toInt().coerceIn(1_000_000, 100_000_000)
}

/**
 * Run after source geometry/speed/animation but before PiP and foreground overlays.
 * A fixed output size avoids accidentally encoding a 4K source for a 720p request.
 * FrameDropEffect caps real video frames; EditedMediaItem.setFrameRate alone is
 * only a hint for sources without a frame rate (for example still images).
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun selectedVideoOutputEffects(config: ExportEngine.ExportConfig): List<Effect> {
    val (width, height) = AspectCropTransformation.targetSizeFor(config.resolution, config.aspectRatio)
    return listOf(
        if (config.preserveInputAspectRatio || config.faceCropConfig != null)
            PreservingOutputSizeTransformation(maxOf(width, height))
        else ProjectOutputSizeTransformation(width, height, config.videoFitMode),
        FrameDropEffect.createDefaultFrameDropEffect(effectiveExportFrameRate(config.fps).toFloat())
    )
}

/** Drawing pixels share the post-crop viewport, including preserve-input and face-crop exports. */
internal fun selectedDrawingCanvasSize(
    config: ExportEngine.ExportConfig,
    sourceAspectRatio: Float?
): Pair<Int, Int> {
    val (targetWidth, targetHeight) = AspectCropTransformation.targetSizeFor(
        config.resolution, config.aspectRatio
    )
    val targetAspect = targetWidth.toFloat() / targetHeight
    val aspect = (config.faceCropConfig?.track?.outputAspectRatio
        ?: if (config.preserveInputAspectRatio) sourceAspectRatio else targetAspect)
        ?.takeIf { it.isFinite() && it > 0f } ?: targetAspect
    if (!config.preserveInputAspectRatio && config.faceCropConfig == null) {
        return targetWidth to targetHeight
    }
    val longEdge = maxOf(targetWidth, targetHeight)
    val shortEdge = ((longEdge / maxOf(aspect, 1f / aspect) / 2f).toInt() * 2).coerceAtLeast(2)
    return if (aspect >= 1f) longEdge to shortEdge else shortEdge to longEdge
}

/** Resize an already-framed canvas/reframe without removing its boundaries/background. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class PreservingOutputSizeTransformation(private val longEdge: Int) : GlMatrixTransformation {
    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        require(inputWidth > 0 && inputHeight > 0 && longEdge >= 2)
        val scale = longEdge.toDouble() / maxOf(inputWidth, inputHeight)
        fun even(value: Int) = ((value * scale / 2).toInt() * 2).coerceAtLeast(2)
        return Size(even(inputWidth), even(inputHeight))
    }

    override fun getGlMatrixArray(presentationTimeUs: Long): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f
    )
}
