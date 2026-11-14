package com.apexstudio.app.data.export

import androidx.media3.common.Effect
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.transformer.VideoEncoderSettings
import androidx.media3.effect.GlMatrixTransformation
import com.apexstudio.app.data.effect.AspectCropTransformation
import com.apexstudio.app.domain.model.SpeedCurveMath
import com.apexstudio.app.domain.model.SpeedPoint

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
 * Media3 1.11.x can stall muxing when continuous audio crosses a long video-sample gap.
 * Below 60 fps keep the existing long-gap fill. At 60 fps and above, ask the codec to
 * repeat at the target interval. This is best-effort only; a strict final audit and explicit
 * CFR fallback, when eligible, establish the encoded cadence contract.
 */
internal const val EXPORT_REPEAT_PREVIOUS_FRAME_INTERVAL_US = 100_000L

internal fun exportRepeatPreviousFrameIntervalUs(requestedFps: Int): Long {
    val fps = effectiveExportFrameRate(requestedFps)
    return if (fps >= 60) (1_000_000L + fps / 2L) / fps else EXPORT_REPEAT_PREVIOUS_FRAME_INTERVAL_US
}

/** Encoder settings shared by selected-video and sequential project exports. */
internal fun exportVideoEncoderSettings(config: ExportEngine.ExportConfig): VideoEncoderSettings =
    VideoEncoderSettings.Builder()
        .setBitrate(exportTargetBitrate(config))
        .setRepeatPreviousFrameIntervalUs(exportRepeatPreviousFrameIntervalUs(config.fps))
        .build()

/** A 0.5-fps metadata tolerance avoids changing cadence for nominal/rounded source rates. */
private const val SOURCE_FRAME_RATE_TOLERANCE_FPS = 0.5f

internal data class SourceFrameRateAdjustment(
    val dropSourceFrames: Boolean
)

/**
 * Compare the reported source rate after its fastest playback segment with the requested output.
 * This policy only decides whether to drop frames from a known faster video source. A peak speed
 * enables an item-wide rate limiter, but FrameDropEffect drops only samples arriving above its
 * target cadence; slower/equal sections are not forced to a different rate. Unknown, equal, and
 * lower-rate sources keep their timestamps. A post-export CFR repair is considered separately and
 * only when the full source PTS sequence proves an integer-multiple constant cadence; VFR, unknown,
 * and speed-changed sources are never normalized this way. Do not use EditedMediaItem.setFrameRate
 * as a video conversion. Still-photo exports retain their separate image-to-video frame-rate setting.
 */
internal fun sourceFrameRateAdjustment(
    sourceFrameRate: Float?,
    requestedFps: Int,
    constantSpeed: Float = 1f,
    speedPoints: List<SpeedPoint> = emptyList(),
    speedEffectApplied: Boolean = true
): SourceFrameRateAdjustment {
    val sourceFps = sourceFrameRate?.takeIf { it.isFinite() && it > 0f }
        ?: return SourceFrameRateAdjustment(dropSourceFrames = false)
    if (!speedEffectApplied) {
        return SourceFrameRateAdjustment(
            dropSourceFrames = sourceFps > effectiveExportFrameRate(requestedFps) + SOURCE_FRAME_RATE_TOLERANCE_FPS
        )
    }
    val curve = SpeedCurveMath.sanitize(speedPoints)
    val peakSpeed = if (curve.size >= 2) curve.maxOf { it.speed }
        else SpeedCurveMath.sanitizeConstantSpeed(constantSpeed)
    val effectiveSourceFps = sourceFps * peakSpeed
    if (!effectiveSourceFps.isFinite()) return SourceFrameRateAdjustment(dropSourceFrames = false)
    val difference = effectiveSourceFps - effectiveExportFrameRate(requestedFps)
    return SourceFrameRateAdjustment(
        dropSourceFrames = difference > SOURCE_FRAME_RATE_TOLERANCE_FPS
    )
}

/** Scan only sources that can be safely resampled onto the requested interval without fractional drift. */
internal fun shouldInspectSourceCadenceForCfrNormalization(
    sourceFrameRate: Float?,
    requestedFps: Int,
    speedEffectApplied: Boolean
): Boolean {
    if (speedEffectApplied) return false
    val sourceFps = sourceFrameRate?.takeIf { it.isFinite() && it > 0f } ?: return false
    val targetFps = effectiveExportFrameRate(requestedFps)
    if (targetFps >= 60 || sourceFps <= targetFps + SOURCE_FRAME_RATE_TOLERANCE_FPS) return false
    val ratio = sourceFps / targetFps
    return ratio >= 1.5f && kotlin.math.abs(ratio - kotlin.math.round(ratio)) <= 0.02f
}

/** True only when metadata and the complete source PTS sequence prove an integer-multiple CFR input. */
internal fun isVerifiedFasterCfrSource(
    sourceFrameRate: Float?,
    sourceCadence: SourceFrameCadenceAudit?,
    requestedFps: Int,
    speedEffectApplied: Boolean
): Boolean {
    if (!shouldInspectSourceCadenceForCfrNormalization(sourceFrameRate, requestedFps, speedEffectApplied)) {
        return false
    }
    val reportedFps = sourceFrameRate?.takeIf { it.isFinite() && it > 0f } ?: return false
    val measuredFps = sourceCadence?.constantFrameRateFps ?: return false
    return kotlin.math.abs(measuredFps - reportedFps) <= maxOf(0.5f, reportedFps * 0.02f)
}

/**
 * Run after source geometry/speed/animation but before PiP and foreground overlays.
 * A fixed output size avoids accidentally encoding a 4K source for a 720p request.
 * Only a known source cadence above the target gets FrameDropEffect; equal/lower/unknown sources
 * retain their presentation timestamps. The effect reduces average rate, not every VFR gap.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun selectedVideoOutputEffects(
    config: ExportEngine.ExportConfig,
    sourceFrameRate: Float? = null,
    speedEffectApplied: Boolean = true
): List<Effect> {
    val (width, height) = AspectCropTransformation.targetSizeFor(config.resolution, config.aspectRatio)
    val cadence = sourceFrameRateAdjustment(
        sourceFrameRate,
        config.fps,
        config.clipSpeed,
        config.speedPoints,
        speedEffectApplied
    )
    return buildList {
        add(if (config.preserveInputAspectRatio || config.faceCropConfig != null)
            PreservingOutputSizeTransformation(maxOf(width, height))
        else ProjectOutputSizeTransformation(width, height, config.videoFitMode))
        if (cadence.dropSourceFrames) {
            add(FrameDropEffect.createDefaultFrameDropEffect(effectiveExportFrameRate(config.fps).toFloat()))
        }
    }
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
