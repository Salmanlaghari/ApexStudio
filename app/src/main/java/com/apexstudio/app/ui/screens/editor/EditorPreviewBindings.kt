package com.apexstudio.app.ui.screens.editor

import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import com.apexstudio.app.data.effect.WorkspaceEffectGlEffect
import com.apexstudio.app.data.fx.DownloadedFxEffect
import com.apexstudio.app.data.fx.FxGlEffect
import com.apexstudio.app.data.fx.FxPreset
import com.apexstudio.app.domain.model.WorkspaceEffect
import com.apexstudio.app.domain.model.hasValidParameters

/** Immutable input/output pair consumed by the live Player.setVideoEffects binding. */
internal data class WorkspaceEffectPreviewBinding(
    val sourceEffects: List<WorkspaceEffect>,
    /** Ordered shader slots; numeric draft edits are read live per frame. */
    val renderStageIds: List<String>,
    val renderEffects: List<GlEffect>,
)

/** Paused players need a new decoded frame after live parameters change; playing players read updates per frame. */
internal fun workspaceEffectPausedRefreshSnapshot(
    effects: List<WorkspaceEffect>,
    isPlaying: Boolean,
): List<WorkspaceEffect>? = if (isPlaying) null else effects.toList()

/**
 * Build preview GL effects with the same renderer factory used by export. Keeping the
 * draft model alongside the GL chain makes preset/range/strength changes invalidation keys.
 */
@OptIn(UnstableApi::class)
internal fun workspaceEffectPreviewBinding(
    effects: List<WorkspaceEffect>,
    frameIntensityProvider: ((Long) -> Float)? = null,
    frameEffectProvider: ((Int, Long) -> WorkspaceEffect?)? = null
): WorkspaceEffectPreviewBinding {
    val snapshot = effects.toList()
    return WorkspaceEffectPreviewBinding(
        sourceEffects = snapshot,
        renderStageIds = snapshot.filter { it.hasValidParameters() }.map { it.id },
        renderEffects = WorkspaceEffectGlEffect.createEffects(
            effects = snapshot,
            frameIntensityProvider = frameIntensityProvider,
            includeInactiveSlots = true,
            frameEffectProvider = frameEffectProvider
        ),
    )
}

/** The same global FX snapshot invalidates both single-video and transition preview chains. */
internal data class GlobalFxPreviewBinding(
    val preset: FxPreset?,
    val downloadedEffect: DownloadedFxEffect?,
    val intensity: Float,
    val speed: Float,
) {
    init {
        require((preset != null) != (downloadedEffect != null)) { "Exactly one FX source is required" }
    }

    /** Each preview item owns its renderer; no GL effect instance is shared across items. */
    @OptIn(UnstableApi::class)
    fun createEffect(): GlEffect = preset?.let { FxGlEffect(it, intensity, speed) }
        ?: FxGlEffect(requireNotNull(downloadedEffect), intensity, speed)
}

/**
 * Consume an already integrity-verified profile, never perform I/O in composition.
 * A pending/stale lookup cannot attach a different selection's downloaded profile.
 * Missing-profile diagnostics and export rejection remain at their existing boundaries.
 */
internal fun globalFxPreviewBinding(
    selectedId: String?,
    verifiedDownloadedEffect: DownloadedFxEffect?,
    intensity: Float,
    speed: Float,
): GlobalFxPreviewBinding? {
    if (!(intensity > 0f)) return null
    val preset = FxPreset.byId(selectedId)
    val downloaded = verifiedDownloadedEffect?.takeIf { preset == null && it.id == selectedId }
    if (preset == null && downloaded == null) return null
    return GlobalFxPreviewBinding(preset, downloaded, intensity, speed)
}
