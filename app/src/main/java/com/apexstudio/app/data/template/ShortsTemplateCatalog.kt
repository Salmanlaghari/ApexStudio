package com.apexstudio.app.data.template

import com.apexstudio.app.domain.model.TextOverlay

/** A fixed, editor-only 30-second Shorts recipe. */
data class ShortsTemplate(
    val id: String,
    val visualPresetId: String = id,
    val name: String,
    val category: String,
    val shortDescription: String,
    val sampleTitle: String,
    val sampleSubtitle: String,
    val titleColorArgb: Long,
    val subtitleColorArgb: Long,
    val titleFontFamily: String,
    val subtitleFontFamily: String,
    val titleAnimationId: String,
    val subtitleAnimationId: String,
    val musicAssetId: String,
    val filterId: String,
    val filterIntensity: Float,
    val fxPresetId: String,
    val fxIntensity: Float,
    val accentArgb: Long,
    val transitionType: String = "cross",
    val durationMs: Long = 30_000L,
) {
    val title: String get() = name
    val description: String get() = shortDescription
    val backgroundStartArgb: Long get() = accentArgb
    val backgroundEndArgb: Long get() = titleColorArgb
    val titleAnimationType: String get() = titleAnimationId
    val subtitleAnimationType: String get() = subtitleAnimationId
    val targetDurationMs: Long get() = durationMs
    init {
        require(id == visualPresetId)
        require(durationMs == 30_000L)
        require(titleAnimationId in VALID_ANIMATIONS)
        require(subtitleAnimationId in VALID_ANIMATIONS)
        require(filterIntensity in 0f..1f && fxIntensity in 0f..1f)
    }

    /** Converts this editor-only recipe to the shared transmission preset model. */
    fun asTransmissionTemplate(): TransmissionTemplate = TransmissionTemplate(
        id = visualPresetId,
        name = name,
        description = shortDescription,
        resolution = "1080p",
        fps = 60,
        aspectRatio = "9:16",
        filterId = filterId,
        filterIntensity = filterIntensity,
        fxPresetId = fxPresetId,
        fxIntensity = fxIntensity,
        transitionType = transitionType,
        transitionDurationMs = 500L,
        defaultIntensity = 1f,
        previewAccentArgb = accentArgb,
        tags = listOf("shorts", category.lowercase()),
        royaltyTrackId = "",
        musicTitle = "",
        musicGenre = category,
        bpm = 120,
        shortsOnly = true,
    )

    companion object {
        val VALID_ANIMATIONS: Set<String> = setOf("FADE_IN", "SLIDE_UP", "SLIDE_BOUNCE", "BOUNCE")
    }
}

/** The complete, deliberately distinct Shorts gallery. */
object ShortsTemplateCatalog {
    val templates: List<ShortsTemplate> = listOf(
        ShortsTemplate("shorts_travel_adventure", name = "Travel Adventure", category = "Travel", shortDescription = "A bright, forward-moving journey cut for places and discoveries.", sampleTitle = "CHASE THE HORIZON", sampleSubtitle = "One more road, one more story", titleColorArgb = 0xFFFFFFFFL, subtitleColorArgb = 0xFFFFD166L, titleFontFamily = "bebas", subtitleFontFamily = "poppins", titleAnimationId = "SLIDE_UP", subtitleAnimationId = "FADE_IN", musicAssetId = "travel-adventure", filterId = "golden_hour", filterIntensity = 0.9f, fxPresetId = "lens_flare", fxIntensity = 0.35f, accentArgb = 0xFFFFB347L),
        ShortsTemplate("shorts_food_story", name = "Food Story", category = "Food", shortDescription = "Warm, appetite-forward pacing for recipes, bites, and kitchen reveals.", sampleTitle = "THE FIRST BITE", sampleSubtitle = "Simple ingredients. Big flavor.", titleColorArgb = 0xFFFFF3E0L, subtitleColorArgb = 0xFFFF8A65L, titleFontFamily = "serif", subtitleFontFamily = "poppins", titleAnimationId = "BOUNCE", subtitleAnimationId = "SLIDE_UP", musicAssetId = "food-and-cooking", filterId = "sunset_gold", filterIntensity = 0.82f, fxPresetId = "bloom", fxIntensity = 0.28f, accentArgb = 0xFFFF7043L),
        ShortsTemplate("shorts_fitness_challenge", name = "Fitness Challenge", category = "Fitness", shortDescription = "High-energy challenge framing that lands the goal before the payoff.", sampleTitle = "30 SECONDS. GO.", sampleSubtitle = "Show up stronger than yesterday", titleColorArgb = 0xFFE8F5E9L, subtitleColorArgb = 0xFFA5D6A7L, titleFontFamily = "anton", subtitleFontFamily = "montserrat", titleAnimationId = "SLIDE_BOUNCE", subtitleAnimationId = "FADE_IN", musicAssetId = "fitness-motivation", filterId = "neon_green", filterIntensity = 0.86f, fxPresetId = "shake", fxIntensity = 0.22f, accentArgb = 0xFF66BB6AL),
        ShortsTemplate("shorts_gaming_highlight", name = "Gaming Highlight", category = "Gaming", shortDescription = "Punchy digital framing for clutch plays, reactions, and winning moments.", sampleTitle = "CLUTCH MODE", sampleSubtitle = "That was not supposed to happen", titleColorArgb = 0xFFE1F5FEL, subtitleColorArgb = 0xFF80DEEA, titleFontFamily = "monospace", subtitleFontFamily = "montserrat", titleAnimationId = "SLIDE_BOUNCE", subtitleAnimationId = "BOUNCE", musicAssetId = "gaming", filterId = "matrix_green", filterIntensity = 0.9f, fxPresetId = "glitch", fxIntensity = 0.42f, accentArgb = 0xFF00E5FFL),
        ShortsTemplate("shorts_fashion_lookbook", name = "Fashion Lookbook", category = "Fashion", shortDescription = "Polished editorial title treatment for outfits, details, and transitions.", sampleTitle = "LOOK ONE", sampleSubtitle = "Wear the moment", titleColorArgb = 0xFFFFF8E1L, subtitleColorArgb = 0xFFF8BBD0L, titleFontFamily = "serif", subtitleFontFamily = "montserrat", titleAnimationId = "FADE_IN", subtitleAnimationId = "SLIDE_UP", musicAssetId = "fashion-and-beauty", filterId = "fresh_face", filterIntensity = 0.78f, fxPresetId = "prism", fxIntensity = 0.3f, accentArgb = 0xFFEC407AL),
        ShortsTemplate("shorts_nature_moment", name = "Nature Moment", category = "Nature", shortDescription = "Quiet, spacious typography for small details and slow natural wonder.", sampleTitle = "PAUSE HERE", sampleSubtitle = "The world is still moving", titleColorArgb = 0xFFF1F8E9L, subtitleColorArgb = 0xFFC5E1A5L, titleFontFamily = "serif", subtitleFontFamily = "sans", titleAnimationId = "FADE_IN", subtitleAnimationId = "FADE_IN", musicAssetId = "nature-and-calm", filterId = "forest_green", filterIntensity = 0.74f, fxPresetId = "soft_blur", fxIntensity = 0.18f, accentArgb = 0xFF81C784L),
        ShortsTemplate("shorts_tech_showcase", name = "Tech Showcase", category = "Technology", shortDescription = "Crisp product-story framing that makes a feature feel tangible.", sampleTitle = "MEET YOUR NEXT TOOL", sampleSubtitle = "Designed for the way you work", titleColorArgb = 0xFFE3F2FDL, subtitleColorArgb = 0xFF90CAF9L, titleFontFamily = "monospace", subtitleFontFamily = "sans", titleAnimationId = "SLIDE_UP", subtitleAnimationId = "FADE_IN", musicAssetId = "tech-product-showcase", filterId = "ocean_blue", filterIntensity = 0.8f, fxPresetId = "scanlines", fxIntensity = 0.2f, accentArgb = 0xFF42A5F5L),
        ShortsTemplate("shorts_sports_hype", name = "Sports Hype", category = "Sports", shortDescription = "Impact-first hype language for speed, competition, and the final play.", sampleTitle = "MAKE THE PLAY", sampleSubtitle = "Pressure makes moments", titleColorArgb = 0xFFFFEBEEL, subtitleColorArgb = 0xFFFF8A80L, titleFontFamily = "anton", subtitleFontFamily = "sans", titleAnimationId = "BOUNCE", subtitleAnimationId = "SLIDE_BOUNCE", musicAssetId = "sports-action", filterId = "street_blue", filterIntensity = 0.88f, fxPresetId = "motion_streak", fxIntensity = 0.45f, accentArgb = 0xFFEF5350L),
        ShortsTemplate("shorts_daily_vlog", name = "Daily Vlog", category = "Lifestyle", shortDescription = "A friendly creator frame for everyday wins, routines, and candid updates.", sampleTitle = "A DAY IN MY LIFE", sampleSubtitle = "Little moments, honestly shared", titleColorArgb = 0xFFFFFDE7L, subtitleColorArgb = 0xFFFFCC80L, titleFontFamily = "poppins", subtitleFontFamily = "poppins", titleAnimationId = "SLIDE_UP", subtitleAnimationId = "FADE_IN", musicAssetId = "daily-creator-vlog", filterId = "polaroid_fade", filterIntensity = 0.7f, fxPresetId = "light_leak", fxIntensity = 0.25f, accentArgb = 0xFFFFA726L),
        ShortsTemplate("shorts_comedy_reveal", name = "Comedy Reveal", category = "Comedy", shortDescription = "Playful setup-and-reveal pacing for jokes, surprises, and reactions.", sampleTitle = "WAIT FOR IT...", sampleSubtitle = "The plan was almost perfect", titleColorArgb = 0xFFFFFDE7L, subtitleColorArgb = 0xFFFFF176L, titleFontFamily = "cursive", subtitleFontFamily = "sans", titleAnimationId = "BOUNCE", subtitleAnimationId = "SLIDE_BOUNCE", musicAssetId = "comedy-and-fun", filterId = "fresh_face", filterIntensity = 0.83f, fxPresetId = "shake", fxIntensity = 0.3f, accentArgb = 0xFFFFD54FL),
    )

    val recipes: List<ShortsTemplate> get() = templates
    private val indexedById = templates.associateBy { it.id }
    fun byId(id: String): ShortsTemplate? = indexedById[id]
    fun find(id: String): ShortsTemplate? = byId(id)
    fun asTransmissionTemplates(): List<TransmissionTemplate> = templates.map { it.asTransmissionTemplate() }
}

/** Pure conversion of the two sample specs into clip-local, bounded overlays. */
fun ShortsTemplate.sampleOverlays(durationMs: Long = this.durationMs): List<TextOverlay> {
    val end = durationMs.coerceAtLeast(1L)
    val titleEnd = (end * 0.42f).toLong().coerceIn(1L, end)
    val subtitleStart = (end * 0.36f).toLong().coerceIn(0L, end - 1L)
    return listOf(
        TextOverlay.of(id = "$id-title", text = sampleTitle, x = 0.5f, y = 0.34f, sizeScale = 1.28f, colorArgb = titleColorArgb, shadowColorArgb = 0xBB000000L, fontFamily = titleFontFamily, isBold = true, animationType = titleAnimationId, animationDurationMs = 700L, startMs = 0L, endMs = titleEnd),
        TextOverlay.of(id = "$id-subtitle", text = sampleSubtitle, x = 0.5f, y = 0.72f, sizeScale = 0.72f, colorArgb = subtitleColorArgb, bgArgb = 0x88000000L, fontFamily = subtitleFontFamily, isBold = false, animationType = subtitleAnimationId, animationDurationMs = 650L, startMs = subtitleStart, endMs = end),
    )
}
