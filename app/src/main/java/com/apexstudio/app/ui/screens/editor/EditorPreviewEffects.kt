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

/** Use the decoded source/crop ratio when available; otherwise keep the selected output frame. */
internal fun editorPreviewStageAspectRatio(sourceAspectRatio: Float?, fallbackAspectRatio: Float): Float =
    sourceAspectRatio?.takeIf { it.isFinite() && it in 0.1f..10f }
        ?: fallbackAspectRatio.takeIf { it.isFinite() && it in 0.1f..10f }
        ?: LOCKED_EDITOR_PREVIEW_ASPECT

/** A restrained enlargement makes portrait footage easier to inspect without changing its frame geometry. */
internal fun portraitPreviewScale(aspectRatio: Float?): Float =
    if (aspectRatio != null && aspectRatio.isFinite() && aspectRatio >= 0.1f && aspectRatio < 1f) 1.05f else 1f
