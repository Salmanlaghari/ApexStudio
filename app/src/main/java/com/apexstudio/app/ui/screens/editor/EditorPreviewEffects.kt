package com.apexstudio.app.ui.screens.editor

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.media3.common.Effect
import com.apexstudio.app.domain.model.AnimatedTransform

/** Effects built from the editor's active LUT, adjustments, FX and keyframe state. */
internal val LocalEditorPreviewEffects = staticCompositionLocalOf<List<Effect>> { emptyList() }

/** Keep the output-space crop/preset/workspace stages ahead of the exported grade stages. */
internal fun mergeEditorPreviewEffects(
    geometryAndWorkspaceEffects: List<Effect>,
    editorGradeEffects: List<Effect>,
): List<Effect> = geometryAndWorkspaceEffects + editorGradeEffects

/** Compose grading is only used for stills; video grades and LUT compare render in Media3. */
internal fun shouldRenderComposeGrade(isPhotoClip: Boolean, lutCompareMode: Boolean): Boolean =
    isPhotoClip

/** Video FX are already part of the editor grade graph; photos keep their Compose FX path. */
internal fun shouldRenderComposeFx(isPhotoClip: Boolean): Boolean = isPhotoClip

/** Video keyframes are rendered in Media3; only stills use the Compose transform. */
internal fun composeTransformForPreview(
    isPhotoClip: Boolean,
    animatedTransform: AnimatedTransform,
): AnimatedTransform = if (isPhotoClip) animatedTransform else AnimatedTransform.Identity
