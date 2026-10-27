package com.apexstudio.app.ui.screens.editor

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.media3.common.Effect
import com.apexstudio.app.data.filter.FilterManifest
import com.apexstudio.app.domain.model.AnimatedTransform
import com.apexstudio.app.domain.model.GpuFilterConfig
import com.apexstudio.app.domain.model.StylisticEffectType

/** Effects built from the editor's active LUT, adjustments, FX and keyframe state. */
internal val LocalEditorPreviewEffects = staticCompositionLocalOf<List<Effect>> { emptyList() }

/** Keep crop/preset/workspace stages ahead of the export-parity editor grade stages. */
internal fun mergeEditorPreviewEffects(
    geometryAndWorkspaceEffects: List<Effect>,
    editorGradeEffects: List<Effect>,
): List<Effect> = geometryAndWorkspaceEffects + editorGradeEffects

/** Compose grading remains the photo path and the explicit video A/B compare fallback. */
internal fun shouldRenderComposeGrade(isPhotoClip: Boolean, lutCompareMode: Boolean): Boolean =
    isPhotoClip || lutCompareMode

/** Video FX are processed by Media3; photos retain their Compose FX path. */
internal fun shouldRenderComposeFx(isPhotoClip: Boolean): Boolean = isPhotoClip

/** GPU filter configs use Media3 only when every active operation is export-parity supported. */
internal fun gpuFilterConfigUsesMedia3(config: GpuFilterConfig): Boolean {
    val presetSupported = config.filterPresetId == null || FilterManifest.presetById(config.filterPresetId) != null
    val stylisticSupported = config.stylisticEffect in setOf(
        StylisticEffectType.NONE,
        StylisticEffectType.VIGNETTE,
        StylisticEffectType.SEPIA,
        StylisticEffectType.SHARPEN,
    )
    return presetSupported && config.activeProfileId == null && config.gamma == 1f && stylisticSupported
}

/** Keep the legacy visual fallback for photos, A/B compare, and GPU-only profile/shader modes. */
internal fun shouldRenderComposeGpuFilter(
    isPhotoClip: Boolean,
    compareMode: Boolean,
    config: GpuFilterConfig,
): Boolean = isPhotoClip || compareMode || !gpuFilterConfigUsesMedia3(config)

/** Video keyframes render in Media3; still images retain their Compose transform. */
internal fun composeTransformForPreview(
    isPhotoClip: Boolean,
    animatedTransform: AnimatedTransform,
): AnimatedTransform = if (isPhotoClip) animatedTransform else AnimatedTransform.Identity

/** Live decoded geometry wins; a persisted source ratio prevents a new portrait project flashing landscape. */
internal fun editorPreviewStageAspectRatio(
    sourceAspectRatio: Float?,
    projectAspectRatio: Float?,
    fallbackAspectRatio: Float,
): Float = sourceAspectRatio?.takeIf { it.isFinite() && it in 0.1f..10f }
    ?: projectAspectRatio?.takeIf { it.isFinite() && it in 0.1f..10f }
    ?: fallbackAspectRatio.takeIf { it.isFinite() && it in 0.1f..10f }
    ?: LOCKED_EDITOR_PREVIEW_ASPECT

/** Backwards-compatible two-argument helper for callers without persisted project metadata. */
internal fun editorPreviewStageAspectRatio(sourceAspectRatio: Float?, fallbackAspectRatio: Float): Float =
    editorPreviewStageAspectRatio(
        sourceAspectRatio = sourceAspectRatio,
        projectAspectRatio = null,
        fallbackAspectRatio = fallbackAspectRatio
    )

/** Reserve fixed transport/timeline chrome so a landscape frame never leaves a dead spacer below it. */
internal fun previewStageHeightBudget(availableHeight: Float, reservedHeight: Float): Float {
    if (!availableHeight.isFinite() || !reservedHeight.isFinite()) return 0f
    return (availableHeight - reservedHeight).coerceAtLeast(0f)
}

/** A restrained enlargement makes portrait footage easier to inspect without changing its frame geometry. */
internal fun portraitPreviewScale(aspectRatio: Float?): Float =
    if (aspectRatio != null && aspectRatio.isFinite() && aspectRatio >= 0.1f && aspectRatio < 1f) 1.05f else 1f

/** Pixel-independent size of a source-ratio preview stage fitted inside the available phone viewport. */
internal data class PreviewStageSize(val width: Float, val height: Float)

internal fun fittedPreviewStageSize(
    maxWidth: Float,
    maxHeight: Float,
    aspectRatio: Float?,
): PreviewStageSize? {
    if (!maxWidth.isFinite() || !maxHeight.isFinite() || maxWidth <= 0f || maxHeight <= 0f) return null
    val aspect = aspectRatio?.takeIf { it.isFinite() && it in 0.1f..10f } ?: return null
    return if (maxWidth / maxHeight > aspect) {
        PreviewStageSize(maxHeight * aspect, maxHeight)
    } else {
        PreviewStageSize(maxWidth, maxWidth / aspect)
    }
}
