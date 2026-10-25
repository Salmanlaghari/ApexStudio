package com.apexstudio.app.data.export

import androidx.media3.common.util.Size
import androidx.media3.effect.GlMatrixTransformation
import com.apexstudio.app.data.filter.GpuFilterConfig
import com.apexstudio.app.data.filter.OneTapLooks
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.StickerOverlay
import com.apexstudio.app.domain.model.SpeedCurve
import com.apexstudio.app.domain.model.SpeedCurveMath
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.domain.model.VideoCropSettings
import com.apexstudio.app.domain.model.VideoFitMode
import com.apexstudio.app.domain.model.hasValidParameters
import com.apexstudio.app.data.color.hasValidRenderParameters

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
    if (config.colorStudioGrade?.hasValidRenderParameters() == false) {
        return "Project export has an invalid Color Studio grade; reset or adjust it before exporting."
    }
    if (config.videoDrawings.any { it.strokes.isNotEmpty() }) {
        return "Project export does not support project or clip drawings yet; export a selected clip to include artwork."
    }

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
        if (clip.workspaceEffects.size > 16 || clip.workspaceEffects.any { !it.hasValidParameters(clip.durationMs) }) {
            return "Video clip '${clip.name}' has an invalid or unsupported ranged filter; export stopped rather than dropping it."
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
        if (clip.textOverlays.any { !it.startMs.isValidProjectOverlayTime(it.endMs) }) {
            return "Video clip '${clip.name}' has a caption with an invalid time range."
        }
        if (clip.textOverlays.any { text ->
                val spansCut = (text.animationWindowStartMs != null && text.animationWindowStartMs != text.startMs) ||
                    (text.animationWindowEndMs != null && text.animationWindowEndMs != text.endMs)
                spansCut && (text.keyframes.keyframes.isNotEmpty() || text.maskKeyframes.isNotEmpty())
            }) {
            return "Video clip '${clip.name}' has project-spanning text with manual transform or mask keyframes. " +
                "Continuous manual text-keyframe timing across cuts is not supported; keep this title within one clip or use preset text motion. Nothing was dropped."
        }
        if (clip.stickers.any { !it.startMs.isValidProjectOverlayTime(it.endMs) }) {
            return "Video clip '${clip.name}' has a sticker with an invalid time range."
        }
        if (clip.gpuFilterConfig != GpuFilterConfig() &&
            !OneTapLooks.isValidRenderConfig(clip.gpuFilterConfig)
        ) return "Video clip '${clip.name}' has a color/filter edit that project export cannot render."
        if (clip.projectVideoEffectId != null && com.apexstudio.app.data.template.ProjectVideoPresets.effect(clip.projectVideoEffectId) == null) {
            return "Video clip '${clip.name}' has an unknown project-video effect."
        }
        if (clip.videoCrop.sanitized() != clip.videoCrop) {
            return "Video clip '${clip.name}' has an invalid crop edit."
        }
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
    if (config.textOverlays.isNotEmpty() || config.stickers.isNotEmpty()) return "Project export does not support export-wide captions or stickers; attach them to a video clip."
    if (config.workspaceEffects.isNotEmpty()) return "Project export does not support export-wide ranged filters; attach them to a video clip."
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

/** Overlay windows are expressed in the clip's output clock, starting at zero after its trim. */
private fun Long.isValidProjectOverlayTime(endMs: Long): Boolean = this >= 0L && endMs >= this

/** Uses the same emoji-to-text representation and size calibration as selected-clip export. */
internal fun projectEmojiStickerAsTextOverlay(sticker: StickerOverlay): TextOverlay = TextOverlay(
    id = sticker.id,
    text = sticker.symbolOrUri,
    x = sticker.x,
    y = sticker.y,
    sizeScale = sticker.sizeScale * com.apexstudio.app.data.stickers.StickerSpriteRenderer.EMOJI_TEXT_SIZE_FACTOR,
    startMs = sticker.startMs,
    endMs = sticker.endMs,
    rotationDeg = sticker.rotationDeg,
    opacity = sticker.opacity,
    keyframes = sticker.keyframes
)

/**
 * Applies the same crop and size to every item, including clips whose input
 * already has the target aspect ratio. Unlike a no-op aspect conversion, this
 * guarantees a single output resolution across the full sequence.
 */
internal class ProjectOutputSizeTransformation(
    private val outputWidth: Int,
    private val outputHeight: Int,
    private val fitMode: VideoFitMode = VideoFitMode.COVER
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
        val scales = compositionFitScales(inputAspect, outputAspect, fitMode)
        scaleX = scales.first
        scaleY = scales.second
        return Size(outputWidth, outputHeight)
    }

    override fun getGlMatrixArray(presentationTimeUs: Long): FloatArray = floatArrayOf(
        scaleX, 0f, 0f, 0f,
        0f, scaleY, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f
    )
}

/** GL normalized-coordinate scales for placing an aspect-ratio source in a fixed output frame. */
internal fun compositionFitScales(
    sourceAspect: Float,
    outputAspect: Float,
    fitMode: VideoFitMode
): Pair<Float, Float> {
    if (!sourceAspect.isFinite() || sourceAspect <= 0f ||
        !outputAspect.isFinite() || outputAspect <= 0f
    ) return 1f to 1f
    val sourceIsWider = sourceAspect > outputAspect
    return when {
        fitMode == VideoFitMode.COVER && sourceIsWider -> sourceAspect / outputAspect to 1f
        fitMode == VideoFitMode.COVER -> 1f to outputAspect / sourceAspect
        sourceIsWider -> 1f to outputAspect / sourceAspect
        else -> sourceAspect / outputAspect to 1f
    }
}
