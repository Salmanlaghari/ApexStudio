package com.apexstudio.app.data.filter

import kotlinx.serialization.Serializable

/**
 * Pre-configured GPUImage Color Profile definition.
 * Bundles curated color grading adjustments, tone curves, and stylistic shaders.
 */
@Serializable
data class GpuColorProfile(
    val id: String,
    val name: String,
    val category: String, // "Cinematic", "Vintage", "B&W", "Mood", "Artistic", "Vibrant"
    val description: String,
    val iconEmoji: String,
    val gradientColors: List<Long>,
    val config: GpuFilterConfig
) {
    /**
     * Blends this profile's settings against the default baseline according to [intensity] (0.0 .. 1.0).
     */
    fun applyWithIntensity(intensity: Float): GpuFilterConfig {
        val clamped = intensity.coerceIn(0f, 1f)
        return GpuFilterConfig(
            filterPresetId = config.filterPresetId,
            filterIntensity = config.filterIntensity * clamped,
            stylisticEffect = config.stylisticEffect,
            stylisticIntensity = config.stylisticIntensity * clamped,
            effectParam = config.effectParam,
            brightness = config.brightness * clamped,
            contrast = 1.0f + (config.contrast - 1.0f) * clamped,
            saturation = 1.0f + (config.saturation - 1.0f) * clamped,
            temperature = 5000f + (config.temperature - 5000f) * clamped,
            tint = config.tint * clamped,
            exposure = config.exposure * clamped,
            gamma = 1.0f + (config.gamma - 1.0f) * clamped,
            highlights = 1.0f + (config.highlights - 1.0f) * clamped,
            shadows = config.shadows * clamped,
            vibrance = config.vibrance * clamped,
            activeProfileId = id,
            profileIntensity = clamped
        )
    }
}

/**
 * Curated collection of pre-configured GPUImage Color Profiles,
 * featuring 'Vintage', 'B&W', 'Cinematic', and other aesthetic looks.
 */
object GpuColorProfiles {

    // 1. Cinematic (Teal & Orange)
    val CINEMATIC = GpuColorProfile(
        id = "profile_cinematic",
        name = "Cinematic",
        category = "Cinematic",
        description = "Blockbuster Hollywood teal & orange grade with deep cinema blacks and vignette",
        iconEmoji = "🎬",
        gradientColors = listOf(0xFF00E5FF, 0xFFFF6D00),
        config = GpuFilterConfig(
            contrast = 1.35f,
            saturation = 1.25f,
            temperature = 4600f,
            tint = 12f,
            exposure = 0.1f,
            highlights = 0.85f,
            shadows = 0.15f,
            vibrance = 0.25f,
            stylisticEffect = StylisticEffectType.VIGNETTE,
            stylisticIntensity = 0.65f,
            effectParam = 0.65f,
            activeProfileId = "profile_cinematic"
        )
    )

    // 2. Vintage (1970s Analog Film)
    val VINTAGE = GpuColorProfile(
        id = "profile_vintage",
        name = "Vintage",
        category = "Vintage",
        description = "1970s Kodachrome analog film warmth with softened contrast, amber tint & golden hues",
        iconEmoji = "🎞️",
        gradientColors = listOf(0xFFFFB300, 0xFF8D6E63),
        config = GpuFilterConfig(
            contrast = 0.95f,
            saturation = 0.90f,
            temperature = 6400f,
            tint = 16f,
            exposure = 0.15f,
            gamma = 1.15f,
            highlights = 0.9f,
            shadows = 0.2f,
            stylisticEffect = StylisticEffectType.SEPIA,
            stylisticIntensity = 0.35f,
            effectParam = 0.5f,
            activeProfileId = "profile_vintage"
        )
    )

    // 3. B&W (Classic Silver Gelatin)
    val BLACK_AND_WHITE = GpuColorProfile(
        id = "profile_bw",
        name = "B&W",
        category = "B&W",
        description = "Timeless Hollywood silver gelatin black & white with punchy dynamic contrast",
        iconEmoji = "⚫",
        gradientColors = listOf(0xFFEEEEEE, 0xFF212121),
        config = GpuFilterConfig(
            contrast = 1.45f,
            saturation = 0.0f,
            brightness = 0.02f,
            exposure = 0.05f,
            gamma = 1.05f,
            highlights = 0.95f,
            shadows = 0.08f,
            stylisticEffect = StylisticEffectType.NONE,
            stylisticIntensity = 0f,
            activeProfileId = "profile_bw"
        )
    )

    // 4. B&W Noir (Dramatic Deep Shadows)
    val BW_NOIR = GpuColorProfile(
        id = "profile_bw_noir",
        name = "B&W Noir",
        category = "B&W",
        description = "High-contrast film noir with crushed shadows and atmospheric perimeter vignette",
        iconEmoji = "🕵️",
        gradientColors = listOf(0xFFB0BEC5, 0xFF000000),
        config = GpuFilterConfig(
            contrast = 1.65f,
            saturation = 0.0f,
            brightness = -0.05f,
            highlights = 0.9f,
            shadows = 0.04f,
            stylisticEffect = StylisticEffectType.VIGNETTE,
            stylisticIntensity = 0.8f,
            effectParam = 0.5f,
            activeProfileId = "profile_bw_noir"
        )
    )

    // 5. Cyberpunk (Neon Tokyo Nights)
    val CYBERPUNK = GpuColorProfile(
        id = "profile_cyberpunk",
        name = "Cyberpunk",
        category = "Artistic",
        description = "Electric futuristic neon magenta and cobalt cyan night atmosphere",
        iconEmoji = "⚡",
        gradientColors = listOf(0xFFFF007F, 0xFF00F0FF),
        config = GpuFilterConfig(
            contrast = 1.35f,
            saturation = 1.45f,
            temperature = 3600f,
            tint = 45f,
            vibrance = 0.5f,
            stylisticEffect = StylisticEffectType.SHARPEN,
            stylisticIntensity = 0.4f,
            effectParam = 0.5f,
            activeProfileId = "profile_cyberpunk"
        )
    )

    // 6. Golden Hour (Sunset Glow)
    val GOLDEN_HOUR = GpuColorProfile(
        id = "profile_golden_hour",
        name = "Golden Hour",
        category = "Mood",
        description = "Warm golden dusk ambiance with saturated sunset rays and soft highlights",
        iconEmoji = "🌅",
        gradientColors = listOf(0xFFFF9100, 0xFFFF1744),
        config = GpuFilterConfig(
            contrast = 1.15f,
            saturation = 1.35f,
            temperature = 6800f,
            tint = 18f,
            exposure = 0.2f,
            gamma = 1.08f,
            highlights = 0.9f,
            shadows = 0.15f,
            activeProfileId = "profile_golden_hour"
        )
    )

    // 7. Moody Nordic (Cold Thriller)
    val MOODY_NORDIC = GpuColorProfile(
        id = "profile_moody_nordic",
        name = "Moody Nordic",
        category = "Cinematic",
        description = "Atmospheric cold cinematic thriller tone with muted saturation and deep shadows",
        iconEmoji = "❄️",
        gradientColors = listOf(0xFF78909C, 0xFF263238),
        config = GpuFilterConfig(
            contrast = 1.35f,
            saturation = 0.72f,
            temperature = 4100f,
            tint = -10f,
            shadows = 0.05f,
            stylisticEffect = StylisticEffectType.VIGNETTE,
            stylisticIntensity = 0.6f,
            effectParam = 0.6f,
            activeProfileId = "profile_moody_nordic"
        )
    )

    // 8. Retro VHS (80s Analog Tape)
    val RETRO_VHS = GpuColorProfile(
        id = "profile_retro_vhs",
        name = "Retro VHS",
        category = "Vintage",
        description = "80s magnetic videotape nostalgia with saturated hues and subtle crosshatch texture",
        iconEmoji = "📼",
        gradientColors = listOf(0xFFAB47BC, 0xFFFF7043),
        config = GpuFilterConfig(
            contrast = 1.15f,
            saturation = 1.35f,
            temperature = 5700f,
            tint = 16f,
            exposure = 0.1f,
            vibrance = 0.3f,
            stylisticEffect = StylisticEffectType.CROSSHATCH,
            stylisticIntensity = 0.18f,
            effectParam = 0.4f,
            activeProfileId = "profile_retro_vhs"
        )
    )

    // 9. Vivid HDR (Punchy Dynamic Range)
    val VIVID_HDR = GpuColorProfile(
        id = "profile_vivid_hdr",
        name = "Vivid HDR",
        category = "Vibrant",
        description = "High dynamic range punch with amplified clarity, vibrance, and rich color pop",
        iconEmoji = "🌈",
        gradientColors = listOf(0xFF00E676, 0xFF2979FF),
        config = GpuFilterConfig(
            contrast = 1.25f,
            saturation = 1.45f,
            vibrance = 0.6f,
            exposure = 0.15f,
            highlights = 0.9f,
            shadows = 0.2f,
            stylisticEffect = StylisticEffectType.SHARPEN,
            stylisticIntensity = 0.55f,
            effectParam = 0.6f,
            activeProfileId = "profile_vivid_hdr"
        )
    )

    // 10. Pastel Dream (Airy Softness)
    val PASTEL_DREAM = GpuColorProfile(
        id = "profile_pastel_dream",
        name = "Pastel Dream",
        category = "Mood",
        description = "Ethereal luminous pastel look with lifted matte shadows and soft dreamy bloom",
        iconEmoji = "🌸",
        gradientColors = listOf(0xFFF8BBD0, 0xFFE1BEE7),
        config = GpuFilterConfig(
            contrast = 0.85f,
            saturation = 1.15f,
            exposure = 0.35f,
            temperature = 5300f,
            gamma = 1.25f,
            shadows = 0.25f,
            stylisticEffect = StylisticEffectType.GAUSSIAN_BLUR,
            stylisticIntensity = 0.22f,
            effectParam = 0.5f,
            activeProfileId = "profile_pastel_dream"
        )
    )

    // 11. Emerald Matrix (Sci-Fi Cyber)
    val EMERALD_MATRIX = GpuColorProfile(
        id = "profile_emerald_matrix",
        name = "Emerald Matrix",
        category = "Artistic",
        description = "Cybernetic terminal phosphor green wash with sharp high-contrast contours",
        iconEmoji = "🟢",
        gradientColors = listOf(0xFF00FF66, 0xFF003311),
        config = GpuFilterConfig(
            contrast = 1.28f,
            saturation = 1.15f,
            temperature = 4800f,
            tint = -58f,
            stylisticEffect = StylisticEffectType.SHARPEN,
            stylisticIntensity = 0.5f,
            effectParam = 0.5f,
            activeProfileId = "profile_emerald_matrix"
        )
    )

    // 12. Warm Coffee (Cozy Mocha Matte)
    val WARM_COFFEE = GpuColorProfile(
        id = "profile_warm_coffee",
        name = "Warm Coffee",
        category = "Mood",
        description = "Rich roasted espresso tones with cozy matte shadows and comforting warmth",
        iconEmoji = "☕",
        gradientColors = listOf(0xFF6D4C41, 0xFFD7CCC8),
        config = GpuFilterConfig(
            contrast = 1.05f,
            saturation = 0.85f,
            temperature = 6500f,
            tint = 12f,
            brightness = -0.04f,
            stylisticEffect = StylisticEffectType.SEPIA,
            stylisticIntensity = 0.45f,
            effectParam = 0.6f,
            activeProfileId = "profile_warm_coffee"
        )
    )

    // 13. Bleach Bypass (Silver Cinema)
    val BLEACH_BYPASS = GpuColorProfile(
        id = "profile_bleach_bypass",
        name = "Bleach Bypass",
        category = "Cinematic",
        description = "Classic silver retention film process with harsh contrast and desaturated skin tones",
        iconEmoji = "⚔️",
        gradientColors = listOf(0xFFECEFF1, 0xFF37474F),
        config = GpuFilterConfig(
            contrast = 1.55f,
            saturation = 0.58f,
            temperature = 4700f,
            exposure = 0.08f,
            highlights = 0.8f,
            stylisticEffect = StylisticEffectType.EMBOSS,
            stylisticIntensity = 0.12f,
            effectParam = 0.4f,
            activeProfileId = "profile_bleach_bypass"
        )
    )

    // 14. Comic Book Pop (Halftone Graphic)
    val COMIC_POP = GpuColorProfile(
        id = "profile_comic_pop",
        name = "Comic Pop",
        category = "Artistic",
        description = "Bold graphic novel look with high contrast and classic halftone dot matrix printing",
        iconEmoji = "💥",
        gradientColors = listOf(0xFFFFEB3B, 0xFFE91E63),
        config = GpuFilterConfig(
            contrast = 1.4f,
            saturation = 1.35f,
            stylisticEffect = StylisticEffectType.HALFTONE,
            stylisticIntensity = 0.65f,
            effectParam = 0.45f,
            activeProfileId = "profile_comic_pop"
        )
    )

    // 15. Anime Cel (Smooth Contours)
    val ANIME_CEL = GpuColorProfile(
        id = "profile_anime_cel",
        name = "Anime Cel",
        category = "Artistic",
        description = "Vibrant Japanese anime style with smoothed cel contours and bright exposure",
        iconEmoji = "🎨",
        gradientColors = listOf(0xFFFF4081, 0xFF448AFF),
        config = GpuFilterConfig(
            contrast = 1.2f,
            saturation = 1.3f,
            exposure = 0.18f,
            stylisticEffect = StylisticEffectType.SMOOTH_TOON,
            stylisticIntensity = 0.6f,
            effectParam = 0.5f,
            activeProfileId = "profile_anime_cel"
        )
    )

    // 16. Cool Slate (Architectural B&W)
    val COOL_SLATE = GpuColorProfile(
        id = "profile_cool_slate",
        name = "Cool Slate",
        category = "B&W",
        description = "Architectural monochrome infused with an icy cyan-blue shadow undertone",
        iconEmoji = "🏙️",
        gradientColors = listOf(0xFF80DEEA, 0xFF263238),
        config = GpuFilterConfig(
            contrast = 1.32f,
            saturation = 0.12f,
            temperature = 3900f,
            highlights = 0.95f,
            activeProfileId = "profile_cool_slate"
        )
    )

    /**
     * All curated pre-configured GPUImage Color Profiles.
     */
    val ALL: List<GpuColorProfile> = listOf(
        CINEMATIC,
        VINTAGE,
        BLACK_AND_WHITE,
        BW_NOIR,
        CYBERPUNK,
        GOLDEN_HOUR,
        MOODY_NORDIC,
        RETRO_VHS,
        VIVID_HDR,
        PASTEL_DREAM,
        EMERALD_MATRIX,
        WARM_COFFEE,
        BLEACH_BYPASS,
        COMIC_POP,
        ANIME_CEL,
        COOL_SLATE
    )

    val CATEGORIES: List<String> = listOf("All", "Cinematic", "Vintage", "B&W", "Mood", "Artistic", "Vibrant")

    fun findById(id: String?): GpuColorProfile? = ALL.firstOrNull { it.id == id }
}
