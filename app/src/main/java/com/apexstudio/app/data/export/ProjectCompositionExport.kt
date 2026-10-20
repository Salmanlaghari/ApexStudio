package com.apexstudio.app.data.export

import androidx.media3.common.util.Size
import androidx.media3.effect.GlMatrixTransformation
import com.apexstudio.app.data.filter.GpuFilterConfig
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.SpeedCurve
import com.apexstudio.app.domain.model.SpeedCurveMath
import com.apexstudio.app.domain.model.VideoCropSettings

/** Monotonic, finite progress for multi-phase project export (0..99% before completion). */
internal fun monotonicProjectExportProgress(previous: Float, reported: Float): Float {
    val safePrevious = previous.takeIf { it.isFinite() }?.coerceIn(0f, 0.99f) ?: 0f
    val safeReported = reported.takeIf { it.isFinite() }?.coerceIn(0f, 0.99f) ?: safePrevious
    return maxOf(safePrevious, safeReported)
}

/**
 * Pure, fail-closed gate for the deliberately small sequential V1 export path.
 * Every item is accounted for; this never filters or silently normalizes clips.
 * Global project-state visual layers are checked by the caller separately.
 */
internal fun projectCompositionUnsupportedReason(
    clips: List<MediaClip>,
    config: ExportEngine.ExportConfig
): String? {
    if (clips.isEmpty()) return "Project export needs at least one main-track video clip."

    clips.forEach { clip ->
        if (clip.type != ClipType.VIDEO || clip.trackIndex != 0) {
            val mediaKind = when (clip.type) {
                ClipType.IMAGE -> "photo"
                ClipType.OVERLAY -> "PiP/overlay video"
                ClipType.AUDIO -> "audio"
                ClipType.SFX -> "sound effect"
                ClipType.VIDEO -> "video outside the main track"
            }
            return "Project export supports main-track video clips only; '${clip.name}' is a $mediaKind."
        }
        if (clip.uri.isBlank()) return "Video clip '${clip.name}' has no media URI."
        if (clip.durationMs <= 0L) return "Video clip '${clip.name}' has no valid source duration."
        if (clip.trimStartMs < 0L || clip.trimEndMs <= clip.trimStartMs || clip.trimEndMs > clip.durationMs) {
            return "Video clip '${clip.name}' has an invalid trim range."
        }
        if (!clip.speedMultiplier.isFinite() || clip.speedMultiplier !in SpeedCurveMath.MIN_SPEED..SpeedCurveMath.MAX_SPEED) {
            return "Video clip '${clip.name}' has an unsupported playback speed."
        }
        if (clip.speedCurve != SpeedCurve.LINEAR || clip.rampStartSpeed != 1f || clip.rampEndSpeed != 1f) {
            return "Video clip '${clip.name}' uses a legacy speed ramp that project export cannot render."
        }
        if (clip.speedPoints.isNotEmpty() && SpeedCurveMath.sanitize(clip.speedPoints) != clip.speedPoints) {
            return "Video clip '${clip.name}' has an invalid or non-canonical speed curve."
        }
        if (!clip.volume.isFinite() || clip.volume !in 0f..2f) {
            return "Video clip '${clip.name}' has an invalid audio gain."
        }
        if (clip.backgroundReplace != null) return "Video clip '${clip.name}' has a background-replacement edit."
        if (!clip.keyframes.isEmpty()) return "Video clip '${clip.name}' has transform keyframes."
        if (clip.textOverlays.isNotEmpty()) return "Video clip '${clip.name}' has captions or text overlays."
        if (clip.stickers.isNotEmpty()) return "Video clip '${clip.name}' has stickers."
        if (clip.gpuFilterConfig != GpuFilterConfig()) return "Video clip '${clip.name}' has a color/filter edit."
        if (clip.projectVideoEffectId != null && com.apexstudio.app.data.template.ProjectVideoPresets.effect(clip.projectVideoEffectId) == null) {
            return "Video clip '${clip.name}' has an unknown project-video effect."
        }
        if (clip.videoCrop != VideoCropSettings()) return "Video clip '${clip.name}' has a crop edit."
        if (clip.faceCropTrack != null) return "Video clip '${clip.name}' has an automatic face-crop track."
        if (clip.mask != null) return "Video clip '${clip.name}' has a shape-mask edit."
        if (!clip.photoEdit.isDefault) return "Video clip '${clip.name}' has photo-only edits."
    }

    val supportedResolutions = setOf("720p", "1080p", "1440p", "4k", "2160p")
    if (config.resolution.lowercase() !in supportedResolutions) {
        return "Project export does not support resolution '${config.resolution}'."
    }
    if (config.aspectRatio !in setOf("16:9", "9:16", "1:1")) {
        return "Project export does not support aspect ratio '${config.aspectRatio}'."
    }
    if (config.preserveInputAspectRatio) return "Project export needs one fixed output aspect ratio."
    if (config.fps !in setOf(24, 25, 30, 50, 60)) return "Project export supports 24, 25, 30, 50, or 60 fps."
    if (config.quality.lowercase() !in setOf("low", "medium", "high")) {
        return "Project export does not support quality '${config.quality}'."
    }
    if (config.bitrateMbps !in 0..100) return "Project export has an invalid output bitrate."
    if (!config.volume.isFinite() || config.volume !in 0f..2f) return "Project export has an invalid global audio gain."
    if (!config.pitchSemitones.isFinite() || config.pitchSemitones !in -24f..24f) {
        return "Project export has an invalid global audio pitch."
    }

    // Global LUT, grading, and FX are applied to every item in the project
    // composition. Keep invalid parameters fail-closed instead of silently
    // clamping values differently from the selected-clip export.
    if (!config.filterIntensity.isFinite() || config.filterIntensity !in 0f..1f) {
        return "Project export has an invalid global filter intensity."
    }
    if (!config.fxIntensity.isFinite() || config.fxIntensity !in 0f..1f ||
        !config.fxSpeed.isFinite() || config.fxSpeed !in 0.1f..4f) {
        return "Project export has invalid global FX settings."
    }
    if (config.videoCrop != null || config.faceCropConfig != null || config.cropRect != null) {
        return "Project export does not support a global crop or face reframe."
    }
    if (config.clipSpeed != 1f || config.speedPoints.isNotEmpty()) return "Project export speed must be set on each video clip."
    if (!config.keyframes.isEmpty()) return "Project export does not support global keyframes."
    if (config.arFilterId != null || config.arFilterIntensity != 0f || config.arFilterCustomText.isNotEmpty()) {
        return "Project export does not support AR filters."
    }
    if (config.transitionType != null || config.transitionGlId != null) return "Project export does not support transitions."
    if (config.textOverlays.isNotEmpty() || config.stickers.isNotEmpty()) return "Project export does not support captions or stickers."
    if (config.rotationAngle != 0f || config.isFlippedHorizontal || config.isFlippedVertical) {
        return "Project export does not support global rotation or flips."
    }
    if (config.trimStartMs != 0L || config.trimEndMs != 0L) return "Project export trims must be set on each video clip."
    if (config.reverbEnabled || config.echoEnabled || config.bassBoostEnabled) {
        return "Project export does not support global reverb, echo, or bass boost."
    }
    if (config.pipOverlays.isNotEmpty() || config.mask != null) return "Project export does not support PiP or masks."
    if (config.overlayAudioSources.isNotEmpty()) return "Project export does not support timeline audio overlays."
    // musicMixTracks are supported as an explicit postmix after video composition.
    return null
}

/**
 * Applies the same crop and size to every item, including clips whose input
 * already has the target aspect ratio. Unlike a no-op aspect conversion, this
 * guarantees a single output resolution across the full sequence.
 */
internal class ProjectOutputSizeTransformation(
    private val outputWidth: Int,
    private val outputHeight: Int
) : GlMatrixTransformation {
    private var scaleX = 1f
    private var scaleY = 1f

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        if (inputWidth <= 0 || inputHeight <= 0 || outputWidth <= 0 || outputHeight <= 0) {
            scaleX = 1f
            scaleY = 1f
            return Size(outputWidth, outputHeight)
        }
        val inputAspect = inputWidth.toFloat() / inputHeight
        val outputAspect = outputWidth.toFloat() / outputHeight
        if (inputAspect > outputAspect) {
            scaleX = inputAspect / outputAspect
            scaleY = 1f
        } else {
            scaleX = 1f
            scaleY = outputAspect / inputAspect
        }
        return Size(outputWidth, outputHeight)
    }

    override fun getGlMatrixArray(presentationTimeUs: Long): FloatArray = floatArrayOf(
        scaleX, 0f, 0f, 0f,
        0f, scaleY, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f
    )
}
