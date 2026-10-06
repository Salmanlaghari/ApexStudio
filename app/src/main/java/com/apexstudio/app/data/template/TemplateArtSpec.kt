package com.apexstudio.app.data.template

/**
 * Visual identity system for the bundled template catalog.
 *
 * The 33 transmission templates used to render as identical gradient
 * tiles differentiated only by their accent color ("bilkul default").
 * This spec gives every template a genuinely distinct preview identity:
 *
 * - [TemplateArtArchetype]: the layout geometry (grids, diagonals,
 *   circles, arcs, split-screens, ...). Every catalog template is
 *   assigned a UNIQUE archetype, so two templates never share the same
 *   silhouette at thumbnail size.
 * - [ArtNamePlacement]: where the template name sits inside the
 *   dashboard-card header artwork.
 * - [ArtMotionStyle]: the ambient animation applied to the dashboard
 *   header (tiles in the picker stay static so a long row doesn't
 *   become a distraction).
 *
 * The mapping is a pure function of the template id
 * ([TemplateArtCatalog.artFor]) so it is unit-testable without Android,
 * and unknown ids fall back to [TemplateArtCatalog.DEFAULT] instead of
 * crashing the picker.
 */
enum class TemplateArtArchetype {
    DIAGONAL_SPLIT,
    CIRCLE_ORBIT,
    ARC_SWEEP,
    SPLIT_GRID,
    WAVEFORM_BARS,
    VIGNETTE_FRAME,
    SCAN_BANDS,
    HALFTONE_DOTS,
    PRISM_TRIANGLE,
    SUN_RAYS,
    ZIGZAG_BOLT,
    CROSSHAIR,
    FILM_STRIP,
    POLAROID,
    HORIZON_LINE,
    MOSAIC_DIAMONDS,
    SPIRAL_SQUARES,
    CHEVRON,
    TOP_BANNER,
    SIDE_RAIL,
    CORNER_FOLD,
    STACKED_CARDS,
    GRAIN_FIELD,
    RING_PULSE,
    PIXEL_BLOCKS,
    DIAGONAL_STRIPES,
    CURVE_WAVE,
    TARGET_RINGS,
    PLUS_GRID,
    BOTTOM_SHEET,
    VERTICAL_THIRDS,
    X_CROSS,
    CONCENTRIC_BURST,
}

/** Where the template name is overlaid inside the header artwork. */
enum class ArtNamePlacement {
    TOP_LEFT,
    TOP_CENTER,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    CENTER,
}

/** Ambient motion applied to the dashboard-card header artwork. */
enum class ArtMotionStyle {
    /** Gentle scale/alpha breathing. */
    PULSE,

    /** A highlight band sweeping across the art. */
    SWEEP,

    /** Slow horizontal drift of the pattern. */
    DRIFT,

    /** Shapes rising from the bottom edge. */
    RISE,

    /** No motion (also used for all picker tiles). */
    NONE,
}

data class TemplateArtSpec(
    val archetype: TemplateArtArchetype,
    val namePlacement: ArtNamePlacement,
    val motionStyle: ArtMotionStyle,
)

/**
 * Deterministic per-template art assignment. The map below must cover
 * every id in `assets/transmission_templates.json` with a unique
 * [TemplateArtArchetype] — enforced by [TemplateArtSpecTest].
 */
object TemplateArtCatalog {

    val DEFAULT = TemplateArtSpec(
        archetype = TemplateArtArchetype.CROSSHAIR,
        namePlacement = ArtNamePlacement.CENTER,
        motionStyle = ArtMotionStyle.NONE,
    )

    private val specs: Map<String, TemplateArtSpec> = mapOf(
        "golden_hour_voyage" to TemplateArtSpec(
            TemplateArtArchetype.SUN_RAYS, ArtNamePlacement.BOTTOM_LEFT, ArtMotionStyle.RISE
        ),
        "tokyo_neon_run" to TemplateArtSpec(
            TemplateArtArchetype.DIAGONAL_STRIPES, ArtNamePlacement.TOP_LEFT, ArtMotionStyle.SWEEP
        ),
        "noir_detective" to TemplateArtSpec(
            TemplateArtArchetype.VIGNETTE_FRAME, ArtNamePlacement.BOTTOM_CENTER, ArtMotionStyle.NONE
        ),
        "vhs_memory_90s" to TemplateArtSpec(
            TemplateArtArchetype.SCAN_BANDS, ArtNamePlacement.TOP_LEFT, ArtMotionStyle.DRIFT
        ),
        "sunset_blockbuster" to TemplateArtSpec(
            TemplateArtArchetype.HORIZON_LINE, ArtNamePlacement.BOTTOM_LEFT, ArtMotionStyle.RISE
        ),
        "ocean_drive" to TemplateArtSpec(
            TemplateArtArchetype.CURVE_WAVE, ArtNamePlacement.BOTTOM_LEFT, ArtMotionStyle.DRIFT
        ),
        "forest_mystic" to TemplateArtSpec(
            TemplateArtArchetype.GRAIN_FIELD, ArtNamePlacement.CENTER, ArtMotionStyle.NONE
        ),
        "cyber_glitch_2088" to TemplateArtSpec(
            TemplateArtArchetype.ZIGZAG_BOLT, ArtNamePlacement.TOP_LEFT, ArtMotionStyle.PULSE
        ),
        "anime_kawaii_bloom" to TemplateArtSpec(
            TemplateArtArchetype.CIRCLE_ORBIT, ArtNamePlacement.CENTER, ArtMotionStyle.PULSE
        ),
        "dark_phantom_drill" to TemplateArtSpec(
            TemplateArtArchetype.X_CROSS, ArtNamePlacement.TOP_LEFT, ArtMotionStyle.PULSE
        ),
        "kodak_35mm_vintage" to TemplateArtSpec(
            TemplateArtArchetype.FILM_STRIP, ArtNamePlacement.BOTTOM_CENTER, ArtMotionStyle.DRIFT
        ),
        "fuji_velvia_pop" to TemplateArtSpec(
            TemplateArtArchetype.PRISM_TRIANGLE, ArtNamePlacement.TOP_CENTER, ArtMotionStyle.NONE
        ),
        "hyper_velocity_rush" to TemplateArtSpec(
            TemplateArtArchetype.CHEVRON, ArtNamePlacement.TOP_LEFT, ArtMotionStyle.SWEEP
        ),
        "super_slowmo_8k" to TemplateArtSpec(
            TemplateArtArchetype.RING_PULSE, ArtNamePlacement.CENTER, ArtMotionStyle.PULSE
        ),
        "vintage_polaroid_fade" to TemplateArtSpec(
            TemplateArtArchetype.POLAROID, ArtNamePlacement.BOTTOM_CENTER, ArtMotionStyle.NONE
        ),
        "synthwave_sunset_80s" to TemplateArtSpec(
            TemplateArtArchetype.ARC_SWEEP, ArtNamePlacement.BOTTOM_LEFT, ArtMotionStyle.RISE
        ),
        "hollywood_blockbuster_epic" to TemplateArtSpec(
            TemplateArtArchetype.TOP_BANNER, ArtNamePlacement.TOP_CENTER, ArtMotionStyle.NONE
        ),
        "cyber_matrix_neon" to TemplateArtSpec(
            TemplateArtArchetype.PLUS_GRID, ArtNamePlacement.TOP_LEFT, ArtMotionStyle.DRIFT
        ),
        "lofi_rainy_window" to TemplateArtSpec(
            TemplateArtArchetype.DIAGONAL_SPLIT, ArtNamePlacement.BOTTOM_LEFT, ArtMotionStyle.DRIFT
        ),
        "urban_street_drip" to TemplateArtSpec(
            TemplateArtArchetype.PIXEL_BLOCKS, ArtNamePlacement.TOP_LEFT, ArtMotionStyle.SWEEP
        ),
        "pastel_dreamscape" to TemplateArtSpec(
            TemplateArtArchetype.BOTTOM_SHEET, ArtNamePlacement.BOTTOM_LEFT, ArtMotionStyle.RISE
        ),
        "action_blockbuster_warm" to TemplateArtSpec(
            TemplateArtArchetype.SIDE_RAIL, ArtNamePlacement.TOP_LEFT, ArtMotionStyle.SWEEP
        ),
        "technicolor_classic_strip" to TemplateArtSpec(
            TemplateArtArchetype.VERTICAL_THIRDS, ArtNamePlacement.BOTTOM_CENTER, ArtMotionStyle.NONE
        ),
        "supercar_exhaust_phonk" to TemplateArtSpec(
            TemplateArtArchetype.SPLIT_GRID, ArtNamePlacement.TOP_LEFT, ArtMotionStyle.PULSE
        ),
        "studio_radiant_glam" to TemplateArtSpec(
            TemplateArtArchetype.CONCENTRIC_BURST, ArtNamePlacement.CENTER, ArtMotionStyle.PULSE
        ),
        "high_contrast_charcoal_mono" to TemplateArtSpec(
            TemplateArtArchetype.CROSSHAIR, ArtNamePlacement.CENTER, ArtMotionStyle.NONE
        ),
        "desert_golden_sand" to TemplateArtSpec(
            TemplateArtArchetype.MOSAIC_DIAMONDS, ArtNamePlacement.BOTTOM_LEFT, ArtMotionStyle.NONE
        ),
        "subway_night_pulse" to TemplateArtSpec(
            TemplateArtArchetype.WAVEFORM_BARS, ArtNamePlacement.BOTTOM_LEFT, ArtMotionStyle.PULSE
        ),
        "rose_gold_aesthetic" to TemplateArtSpec(
            TemplateArtArchetype.STACKED_CARDS, ArtNamePlacement.BOTTOM_LEFT, ArtMotionStyle.NONE
        ),
        "chill_nature_zen" to TemplateArtSpec(
            TemplateArtArchetype.TARGET_RINGS, ArtNamePlacement.CENTER, ArtMotionStyle.PULSE
        ),
        "ultra_hd_cinema" to TemplateArtSpec(
            TemplateArtArchetype.SPIRAL_SQUARES, ArtNamePlacement.TOP_CENTER, ArtMotionStyle.NONE
        ),
        "laser_arcade_rave" to TemplateArtSpec(
            TemplateArtArchetype.HALFTONE_DOTS, ArtNamePlacement.TOP_LEFT, ArtMotionStyle.PULSE
        ),
        "aurora_kaleidoscope" to TemplateArtSpec(
            TemplateArtArchetype.CORNER_FOLD, ArtNamePlacement.BOTTOM_LEFT, ArtMotionStyle.SWEEP
        ),
        // The "Original / No preset" picker tile is not a catalog
        // template, but it gets a calm neutral identity too.
        "original" to TemplateArtSpec(
            TemplateArtArchetype.VIGNETTE_FRAME, ArtNamePlacement.CENTER, ArtMotionStyle.NONE
        ),
    )

    /** Art spec for a catalog template id, or [DEFAULT] when unknown. */
    fun artFor(templateId: String): TemplateArtSpec = specs[templateId] ?: DEFAULT

    /** Every template id that has an explicit spec. */
    fun knownIds(): Set<String> = specs.keys
}
