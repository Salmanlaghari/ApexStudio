package com.apexstudio.app.data.export

import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.util.Size
import androidx.media3.effect.StaticOverlaySettings

internal class ProjectTransitionCompositorSettings(
    private val plan: ProjectTransitionTimeline.Plan,
    outputWidth: Int,
    outputHeight: Int
) : VideoCompositorSettings {
    private val outputWidth = outputWidth.coerceIn(0, Int.MAX_VALUE / 2)
    private val outputHeight = outputHeight.coerceAtLeast(0)

    override fun getOutputSize(inputSizes: List<Size>): Size =
        Size(this.outputWidth * 2, outputHeight)

    override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
        if (inputId !in plan.entries.indices || presentationTimeUs < 0L) return hiddenSettings()
        return settingsFor(roleAt(plan, inputId, presentationTimeUs / 1_000L))
    }

    private fun settingsFor(role: ProjectTransitionCompositorRole): StaticOverlaySettings {
        val visible = role != ProjectTransitionCompositorRole.HIDDEN
        return StaticOverlaySettings.Builder()
            .setAlphaScale(if (visible) 1f else 0f)
            .setScale(0.5f, 1f)
            .setOverlayFrameAnchor(0f, 0f)
            .setBackgroundFrameAnchor(
                if (role == ProjectTransitionCompositorRole.RIGHT) 0.5f else -0.5f,
                0f
            )
            .build()
    }

    private fun hiddenSettings(): StaticOverlaySettings =
        settingsFor(ProjectTransitionCompositorRole.HIDDEN)
}

internal enum class ProjectTransitionCompositorRole {
    LEFT,
    RIGHT,
    HIDDEN
}

internal fun roleAt(
    plan: ProjectTransitionTimeline.Plan,
    inputId: Int,
    projectTimeMs: Long
): ProjectTransitionCompositorRole {
    if (inputId !in plan.entries.indices || projectTimeMs < 0L) {
        return ProjectTransitionCompositorRole.HIDDEN
    }
    val entry = plan.entries[inputId]
    val transition = plan.transitionAt(projectTimeMs)
    return when {
        transition != null && entry == transition.from -> ProjectTransitionCompositorRole.LEFT
        transition != null && entry == transition.to -> ProjectTransitionCompositorRole.RIGHT
        transition == null && plan.entryAt(projectTimeMs) == entry -> ProjectTransitionCompositorRole.LEFT
        else -> ProjectTransitionCompositorRole.HIDDEN
    }
}
