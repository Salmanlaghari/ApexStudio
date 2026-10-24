package com.apexstudio.app.ui.screens.editor

import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import com.apexstudio.app.data.effect.WorkspaceEffectGlEffect
import com.apexstudio.app.domain.model.WorkspaceEffect

/** Immutable input/output pair consumed by the live Player.setVideoEffects binding. */
internal data class WorkspaceEffectPreviewBinding(
    val sourceEffects: List<WorkspaceEffect>,
    val renderEffects: List<GlEffect>,
)

/**
 * Build preview GL effects with the same renderer factory used by export. Keeping the
 * draft model alongside the GL chain makes preset/range/strength changes invalidation keys.
 */
@OptIn(UnstableApi::class)
internal fun workspaceEffectPreviewBinding(
    effects: List<WorkspaceEffect>,
    frameIntensityProvider: ((Long) -> Float)? = null
): WorkspaceEffectPreviewBinding {
    val snapshot = effects.toList()
    return WorkspaceEffectPreviewBinding(
        sourceEffects = snapshot,
        renderEffects = WorkspaceEffectGlEffect.createEffects(snapshot, frameIntensityProvider = frameIntensityProvider),
    )
}
