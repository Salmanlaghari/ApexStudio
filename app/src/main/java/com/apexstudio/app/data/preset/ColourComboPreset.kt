package com.apexstudio.app.data.preset

import com.apexstudio.app.domain.model.VideoAdjustments

/**
 * Phase H: a colour combo bundles a single filter + adjustments + FX
 * preset into one tap. Designed for "looks" — the kind of one-click
 * grades that power users reach for repeatedly (Moody Teal, Cinematic
 * Warm, Vintage Film, B&W Pop, Sunset Glow).
 *
 * Apply via EditorViewModel.applyColourCombo(id). Composes into state
 * the same way the Filters + Adjustments + FX panels do, so the live
 * preview RenderEffect picks it up without a new code path.
 *
 * Built-in library — no JSON asset, no Compose UI for the panel yet
 * (the panel is added by the editor toolbar entry point).
 */
data class ColourComboPreset(
    val id: String,
    val name: String,
    val description: String,
    val filterId: String? = null,
    val filterIntensity: Float = 1f,
    val fxId: String? = null,
    val fxIntensity: Float = 0.6f,
    val adjustments: VideoAdjustments = VideoAdjustments(),
    val previewAccentArgb: Long = 0xFF6E5BFFL
) {
    companion object {
        val BUILTIN: List<ColourComboPreset> = listOf(
            ColourComboPreset(
                id = "cinematic_teal_orange",
                name = "Cinematic Teal & Orange",
                description = "Hollywood blockbuster look — cool shadows, warm highlights.",
                filterId = "cinematic_glow",
                filterIntensity = 0.85f,
                adjustments = VideoAdjustments(
                    saturation = 1.15f,
                    temperature = 0.18f,
                    contrast = 1.1f,
                    highlights = 0.05f,
                    shadows = -0.05f
                )
            ),
            ColourComboPreset(
                id = "moody_noir",
                name = "Moody Noir",
                description = "Low-saturation B&W with deep contrast — film noir feel.",
                filterId = "noir_classic",
                filterIntensity = 0.9f,
                adjustments = VideoAdjustments(
                    saturation = 0.1f,
                    contrast = 1.25f,
                    shadows = -0.15f,
                    grain = 0.2f
                )
            ),
            ColourComboPreset(
                id = "vintage_film",
                name = "Vintage Film",
                description = "Warm sepia tint + soft fade — 70s film stock feel.",
                filterId = "vintage_film",
                filterIntensity = 0.8f,
                adjustments = VideoAdjustments(
                    temperature = 0.12f,
                    saturation = 0.85f,
                    contrast = 0.95f,
                    fade = 0.18f,
                    vignette = 0.25f
                )
            ),
            ColourComboPreset(
                id = "sunset_glow",
                name = "Sunset Glow",
                description = "Magenta highlights, orange mid-tones — golden hour.",
                filterId = "golden_hour",
                filterIntensity = 0.75f,
                adjustments = VideoAdjustments(
                    temperature = 0.25f,
                    saturation = 1.2f,
                    highlights = 0.1f,
                    exposure = 0.05f
                )
            ),
            ColourComboPreset(
                id = "arctic_ice",
                name = "Arctic Ice",
                description = "Cool blue tint with crisp whites — winter / sci-fi feel.",
                filterId = "arctic_frost",
                filterIntensity = 0.8f,
                adjustments = VideoAdjustments(
                    temperature = -0.2f,
                    saturation = 0.95f,
                    contrast = 1.05f,
                    exposure = 0.05f
                )
            ),
            ColourComboPreset(
                id = "vhs_retro",
                name = "VHS Retro",
                description = "80s VHS — chromatic tint + saturation bump + film grain.",
                fxId = "chromatic",
                fxIntensity = 0.5f,
                adjustments = VideoAdjustments(
                    saturation = 1.3f,
                    temperature = 0.08f,
                    grain = 0.3f,
                    fade = 0.1f
                )
            )
        )

        fun byId(id: String?): ColourComboPreset? =
            if (id == null) null else BUILTIN.firstOrNull { it.id == id }
    }
}
