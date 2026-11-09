package com.apexstudio.app.data.audio.sfx

/** The four curated groups shown by the built-in sound-effects browser. */
enum class SfxCategory(val displayName: String) {
    TRANSITIONS("Transitions"),
    UI_TAPS("UI & Taps"),
    MOMENTS("Moments"),
    HITS("Hits")
}

/** Metadata for one procedural effect. Sound samples are synthesized at runtime. */
data class SfxEffect(
    val id: String,
    val displayName: String,
    val category: SfxCategory,
    val defaultDurationMs: Long
)

/** Single source of truth for the starter effect library. */
object SfxLibrary {
    const val DEFAULT_INTENSITY = 1f

    val effects: List<SfxEffect> = listOf(
        SfxEffect("whoosh", "Whoosh", SfxCategory.TRANSITIONS, 650L),
        SfxEffect("fast_swoosh", "Fast Swoosh", SfxCategory.TRANSITIONS, 280L),
        SfxEffect("riser", "Riser", SfxCategory.TRANSITIONS, 1_400L),
        SfxEffect("downer", "Downer", SfxCategory.TRANSITIONS, 900L),
        SfxEffect("click", "Click", SfxCategory.UI_TAPS, 100L),
        SfxEffect("pop", "Pop", SfxCategory.UI_TAPS, 220L),
        SfxEffect("tap", "Tap", SfxCategory.UI_TAPS, 110L),
        SfxEffect("toggle", "Toggle", SfxCategory.UI_TAPS, 260L),
        SfxEffect("camera_shutter", "Camera Shutter", SfxCategory.MOMENTS, 350L),
        SfxEffect("ding", "Ding", SfxCategory.MOMENTS, 1_100L),
        SfxEffect("sparkle", "Sparkle", SfxCategory.MOMENTS, 800L),
        SfxEffect("success_chime", "Success Chime", SfxCategory.MOMENTS, 650L),
        SfxEffect("cinematic_hit", "Cinematic Hit", SfxCategory.HITS, 1_200L),
        SfxEffect("error_buzz", "Error Buzz", SfxCategory.HITS, 350L)
    )

    private val effectsById = effects.associateBy(SfxEffect::id)

    fun find(id: String): SfxEffect? = effectsById[id]
}

data class SfxLibraryUiState(
    val previewingId: String? = null,
    val renderingIds: Set<String> = emptySet(),
    val addingIds: Set<String> = emptySet(),
    val error: String? = null
)
