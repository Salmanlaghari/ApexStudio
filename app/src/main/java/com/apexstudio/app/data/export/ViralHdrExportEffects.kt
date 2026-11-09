package com.apexstudio.app.data.export

import android.content.Context
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import com.apexstudio.app.data.filter.GpuFilterConfig
import com.apexstudio.app.data.filter.LutFilterEngine
import com.apexstudio.app.data.filter.LutFilterGlEffect
import com.apexstudio.app.data.filter.OneTapLooks
import com.apexstudio.app.data.filter.toVideoAdjustments

/** Append the Viral HDR recipe's existing LUT and shared adjustment shader effects. */
@UnstableApi
internal fun addViralHdrEffects(
    context: Context,
    config: GpuFilterConfig,
    effects: MutableList<Effect>
) {
    if (config.activeProfileId != OneTapLooks.VIRAL_HDR_ID) return
    require(OneTapLooks.isValidRenderConfig(config)) {
        "Viral HDR settings are invalid or contain unsupported edits."
    }

    val preset = LutFilterEngine(context).manifest.filters.firstOrNull { it.id == config.filterPresetId }
        ?: throw IllegalArgumentException("The Viral HDR hdr_punch LUT is unavailable.")
    if (config.filterIntensity > 0f) {
        effects.add(LutFilterGlEffect(context, preset, config.filterIntensity))
    }

    val adjustments = config.toVideoAdjustments()
    if (!adjustments.isDefault) {
        effects.add(com.apexstudio.app.data.adjust.AdjustmentsGlEffect(adjustments))
    }
}
