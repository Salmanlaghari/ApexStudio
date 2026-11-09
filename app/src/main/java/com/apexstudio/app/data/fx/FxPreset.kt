package com.apexstudio.app.data.fx

/**
 * Real-time video FX presets, the FX-tool equivalent of the LUT
 * filter presets. Each preset is a fragment-shader variant rendered
 * by [com.apexstudio.app.data.fx.FxGlEffect] on every preview frame
 * and baked into the exported MP4 via Media3 Transformer.
 *
 * Unlike filters (colour mapping) these FX are *spatial / temporal*
 * looks — scanlines, grain, glitch, aberrations — and animate with
 * the frame's presentation time.
 */
enum class FxPreset(
    val id: String,
    val label: String,
    val category: String = "Trending",
    val isAnimated: Boolean = false,
    val defaultSpeed: Float = 1f
) {
    // Trending / Core
    VIGNETTE("vignette", "Vignette", "Trending"),
    FILM_GRAIN("film_grain", "Film Grain", "Retro & Film"),
    VHS("vhs", "VHS", "Retro & Film"),
    GLITCH("glitch", "Glitch", "Glitch & Digital"),
    PIXELATE("pixelate", "Pixelate", "Glitch & Digital"),
    CHROMATIC("chromatic", "Chromatic", "Trending"),
    SCANLINES("scanlines", "Scanlines", "Retro & Film"),
    SOFT_BLUR("soft_blur", "Soft Blur", "Blur & Motion"),
    BLOOM("bloom", "Bloom", "Light & Optics"),
    SHAKE("shake", "Shake", "Blur & Motion"),
    STROBE("strobe", "Strobe", "Retro & Film"),
    PRISM("prism", "Prism", "Light & Optics"),
    ZOOM_BLUR("zoom_blur", "Zoom Blur", "Blur & Motion"),
    HALATION("halation", "Halation", "Light & Optics"),

    // 1. Light & Optics
    LENS_FLARE("lens_flare", "Lens Flare", "Light & Optics"),
    LIGHT_LEAK("light_leak", "Light Leak", "Light & Optics"),
    ANAMORPHIC("anamorphic", "Anamorphic", "Light & Optics"),
    BOKEH_OVERLAY("bokeh_overlay", "Bokeh Overlay", "Light & Optics"),
    SPARKLE("sparkle", "Sparkle", "Light & Optics"),
    SUN_BEAM("sun_beam", "Sun Beam", "Light & Optics"),
    GLOW_DIFFUSE("glow_diffuse", "Glow Diffuse", "Light & Optics"),

    // 2. Glitch & Digital
    BAD_TV("bad_tv", "Bad TV", "Glitch & Digital"),
    PIXEL_SORT("pixel_sort", "Pixel Sort", "Glitch & Digital"),
    DATAMOSH("datamosh", "Datamosh", "Glitch & Digital"),
    RGB_JITTER("rgb_jitter", "RGB Jitter", "Glitch & Digital"),
    CRT_PHOSPHOR("crt_phosphor", "CRT Phosphor", "Glitch & Digital"),
    INTERLACED("interlaced", "Interlaced", "Glitch & Digital"),
    DIGITAL_DROP("digital_drop", "Digital Drop", "Glitch & Digital"),

    // 3. Retro & Film
    SEPIA_GRAIN("sepia_grain", "Sepia Grain", "Retro & Film"),
    DUST_SCRATCHES("dust_scratches", "Dust & Scratches", "Retro & Film"),
    FILM_DAMAGE("film_damage", "Film Damage", "Retro & Film"),
    BLEACH_BYPASS("bleach_bypass", "Bleach Bypass", "Retro & Film"),
    TECHNICOLOR_STRIP("technicolor_strip", "Technicolor", "Retro & Film"),
    CROSS_PROCESS("cross_process", "Cross Process", "Retro & Film"),
    SOLARIZE("solarize", "Solarize", "Retro & Film"),

    // 4. Blur & Motion
    RADIAL_BLUR("radial_blur", "Radial Blur", "Blur & Motion"),
    TILT_SHIFT("tilt_shift", "Tilt Shift", "Blur & Motion"),
    MOTION_STREAK("motion_streak", "Motion Streak", "Blur & Motion"),
    GHOSTING("ghosting", "Ghosting", "Blur & Motion"),
    SPIN_BLUR("spin_blur", "Spin Blur", "Blur & Motion"),
    CAMERA_WOBBLE("camera_wobble", "Camera Wobble", "Blur & Motion"),
    WHIP_PAN_FX("whip_pan_fx", "Whip Pan", "Blur & Motion"),

    // 5. Stylize & Art
    HALFTONE("halftone", "Halftone", "Stylize & Art"),
    SKETCH_LINES("sketch_lines", "Sketch Lines", "Stylize & Art"),
    POSTERIZE("posterize", "Posterize", "Stylize & Art"),
    EDGE_NEON("edge_neon", "Edge Neon", "Stylize & Art"),
    THERMAL_VISION("thermal_vision", "Thermal Vision", "Stylize & Art"),
    NIGHT_VISION("night_vision", "Night Vision", "Stylize & Art"),
    OIL_PAINT("oil_paint", "Oil Paint", "Stylize & Art"),
    EMBOSS_RELIEF("emboss_relief", "Emboss Relief", "Stylize & Art"),
    INVERT_FX("invert_fx", "Invert FX", "Stylize & Art"),
    KALEIDOSCOPE("kaleidoscope", "Kaleidoscope", "Stylize & Art"),

    // 6. CapCut Viral & Special FX (20+ New Presets)
    HEART_BEAT("heart_beat", "Heart Beat", "Trending"),
    EDGE_GLOW("edge_glow", "Edge Glow", "Trending"),
    SHIMMER_STAR("shimmer_star", "Star Shimmer", "Trending"),
    VERTIGO_DOLLY("vertigo_dolly", "Vertigo Zoom", "Trending"),
    LIGHTNING_AURA("lightning_aura", "Lightning", "Light & Optics"),
    FIRE_EMBER("fire_ember", "Fire Ember", "Light & Optics"),
    CRYSTAL_FACET("crystal_facet", "Diamond Facet", "Light & Optics"),
    DOUBLE_EXPOSURE("double_exposure", "Dream Fog", "Light & Optics"),
    MATRIX_RAIN("matrix_rain", "Matrix Code", "Glitch & Digital"),
    GLITCH_RGB_DISPLACE("glitch_rgb_displace", "RGB Slice", "Glitch & Digital"),
    GLITCH_BLOCK("glitch_block", "Macroblock", "Glitch & Digital"),
    HOLOGRAM_GLITCH("hologram_glitch", "Hologram", "Glitch & Digital"),
    FILM_BURN("film_burn", "Film Burn", "Retro & Film"),
    SUPER_8_WARM("super_8_warm", "Super 8mm", "Retro & Film"),
    SEPIA_FLICKER("sepia_flicker", "1920s Silent", "Retro & Film"),
    WATER_RIPPLE("water_ripple", "Water Ripple", "Blur & Motion"),
    GHOST_TRAIL("ghost_trail", "Ghost Clone", "Blur & Motion"),
    HEATWAVE_WARP("heatwave_warp", "Heatwave", "Blur & Motion"),
    SPEED_LINES("speed_lines", "Speed Lines", "Blur & Motion"),
    BLACK_HOLE_WARP("black_hole_warp", "Vortex Swirl", "Blur & Motion"),
    MIRROR_SPLIT("mirror_split", "Mirror 4-Way", "Stylize & Art"),
    NEON_WIREFRAME("neon_wireframe", "Cyber Grid", "Stylize & Art"),
    COMIC_DOTS("comic_dots", "Pop Comic", "Stylize & Art"),
    COLOR_ISOLATION("color_isolation", "Color Splash", "Stylize & Art"),

    // 7. Animated colour filters (Snapchat-style, time-driven)
    HUE_CYCLE("hue_cycle", "Hue Cycle", "Animated", isAnimated = true, defaultSpeed = 1f),
    PULSE_BEAT("pulse_beat", "Pulse Beat", "Animated", isAnimated = true, defaultSpeed = 1f),
    GRADIENT_SWEEP("gradient_sweep", "Gradient Sweep", "Animated", isAnimated = true, defaultSpeed = 1f),
    LIGHT_LEAK_SWEEP("light_leak_sweep", "Light Leak Sweep", "Animated", isAnimated = true, defaultSpeed = 1f),

    // Requested weather, celebration, and impact effects. Each runs through the
    // same Media3 shader path used for both live video preview and video export.
    RAIN("rain", "Rain", "Weather", isAnimated = true, defaultSpeed = 1f),
    CHERRY_BLOSSOM("cherry_blossom", "Cherry Blossom", "Seasonal", isAnimated = true, defaultSpeed = 0.8f),
    WEDDING_GLOW("wedding_glow", "Wedding Glow", "Portrait", isAnimated = true, defaultSpeed = 0.45f),
    ZOOM_PUNCH("zoom_punch", "Zoom Punch", "Blur & Motion", isAnimated = true, defaultSpeed = 1f),
    CRASH_IMPACT("crash_impact", "Crash Impact", "Trending", isAnimated = true, defaultSpeed = 1f),
    DEFOCUS_BLUR("defocus_blur", "Defocus Blur", "Blur & Motion"),

    // Original Creator Pack profiles: new shader families and parameter/tint/geometry variants.
    CREATOR_PRISM_AURORA("creator_prism_aurora", "Aurora Prism", "Creator Pack"),
    CREATOR_PRISM_CANDY("creator_prism_candy", "Candy Prism", "Creator Pack"),
    CREATOR_PRISM_COPPER("creator_prism_copper", "Copper Prism", "Creator Pack"),
    CREATOR_PRISM_ARCTIC("creator_prism_arctic", "Arctic Prism", "Creator Pack"),
    CREATOR_PRISM_ROSE("creator_prism_rose", "Rose Prism", "Creator Pack"),
    CREATOR_TONE_PEACH("creator_tone_peach", "Peach Split Tone", "Creator Pack"),
    CREATOR_TONE_TEAL("creator_tone_teal", "Teal Split Tone", "Creator Pack"),
    CREATOR_TONE_VIOLET("creator_tone_violet", "Violet Split Tone", "Creator Pack"),
    CREATOR_TONE_SUNSET("creator_tone_sunset", "Sunset Split Tone", "Creator Pack"),
    CREATOR_TONE_MINT("creator_tone_mint", "Mint Split Tone", "Creator Pack"),
    CREATOR_RIPPLE_LAGOON("creator_ripple_lagoon", "Lagoon Ripple", "Creator Pack", true, 0.8f),
    CREATOR_RIPPLE_HEAT("creator_ripple_heat", "Heat Ripple", "Creator Pack", true, 1.2f),
    CREATOR_RIPPLE_MOON("creator_ripple_moon", "Moon Ripple", "Creator Pack", true, 0.65f),
    CREATOR_RIPPLE_RAIN("creator_ripple_rain", "Rain Ripple", "Creator Pack", true, 1.4f),
    CREATOR_RIPPLE_GLASS("creator_ripple_glass", "Glass Ripple", "Creator Pack", true, 0.95f),
    CREATOR_MOSAIC_MICRO("creator_mosaic_micro", "Micro Mosaic", "Creator Pack"),
    CREATOR_MOSAIC_TILES("creator_mosaic_tiles", "Gallery Tiles", "Creator Pack"),
    CREATOR_MOSAIC_DIAMOND("creator_mosaic_diamond", "Diamond Mosaic", "Creator Pack"),
    CREATOR_MOSAIC_BOLD("creator_mosaic_bold", "Bold Mosaic", "Creator Pack"),
    CREATOR_MOSAIC_SOFT("creator_mosaic_soft", "Soft Mosaic", "Creator Pack"),
    CREATOR_CRT_AMBER("creator_crt_amber", "Amber CRT", "Creator Pack", true, 0.8f),
    CREATOR_CRT_CYAN("creator_crt_cyan", "Cyan CRT", "Creator Pack", true, 1.1f),
    CREATOR_CRT_GREEN("creator_crt_green", "Green CRT", "Creator Pack", true, 0.7f),
    CREATOR_CRT_MAGENTA("creator_crt_magenta", "Magenta CRT", "Creator Pack", true, 1.3f),
    CREATOR_CRT_MONO("creator_crt_mono", "Mono CRT", "Creator Pack", true, 0.9f),
    CREATOR_HALATION_HONEY("creator_halation_honey", "Honey Halation", "Creator Pack"),
    CREATOR_HALATION_BERRY("creator_halation_berry", "Berry Halation", "Creator Pack"),
    CREATOR_HALATION_ICE("creator_halation_ice", "Ice Halation", "Creator Pack"),
    CREATOR_HALATION_CORAL("creator_halation_coral", "Coral Halation", "Creator Pack"),
    CREATOR_HALATION_LILAC("creator_halation_lilac", "Lilac Halation", "Creator Pack"),
    CREATOR_INK_POP("creator_ink_pop", "Pop Ink", "Creator Pack"),
    CREATOR_INK_NEWSPRINT("creator_ink_newsprint", "Newsprint", "Creator Pack"),
    CREATOR_INK_RISO("creator_ink_riso", "Riso Ink", "Creator Pack"),
    CREATOR_INK_POSTER("creator_ink_poster", "Poster Ink", "Creator Pack"),
    CREATOR_INK_PASTEL("creator_ink_pastel", "Pastel Ink", "Creator Pack"),
    CREATOR_MIRROR_QUAD("creator_mirror_quad", "Quad Mirror", "Creator Pack"),
    CREATOR_MIRROR_HEX("creator_mirror_hex", "Hex Mirror", "Creator Pack"),
    CREATOR_MIRROR_RADIAL("creator_mirror_radial", "Radial Mirror", "Creator Pack"),
    CREATOR_MIRROR_KALEIDO("creator_mirror_kaleido", "Kaleido Mirror", "Creator Pack"),
    CREATOR_MIRROR_FAN("creator_mirror_fan", "Fan Mirror", "Creator Pack"),
    CREATOR_GLITCH_TANGERINE("creator_glitch_tangerine", "Tangerine Signal", "Creator Pack", true, 0.9f),
    CREATOR_GLITCH_COBALT("creator_glitch_cobalt", "Cobalt Signal", "Creator Pack", true, 1.2f),
    CREATOR_GLITCH_LIME("creator_glitch_lime", "Lime Signal", "Creator Pack", true, 0.75f),
    CREATOR_GLITCH_PLUM("creator_glitch_plum", "Plum Signal", "Creator Pack", true, 1.35f),
    CREATOR_GLITCH_SILVER("creator_glitch_silver", "Silver Signal", "Creator Pack", true, 1f),
    CREATOR_STREAK_ORBIT("creator_streak_orbit", "Orbit Streak", "Creator Pack", true, 0.8f),
    CREATOR_STREAK_RUSH("creator_streak_rush", "Rush Streak", "Creator Pack", true, 1.3f),
    CREATOR_STREAK_DREAM("creator_streak_dream", "Dream Streak", "Creator Pack", true, 0.6f),
    CREATOR_STREAK_SOLAR("creator_streak_solar", "Solar Streak", "Creator Pack", true, 1.1f),
    CREATOR_STREAK_TWILIGHT("creator_streak_twilight", "Twilight Streak", "Creator Pack", true, 0.9f);

    companion object {
        fun byId(id: String?): FxPreset? = values().firstOrNull { it.id == id }

        fun categories(): List<String> = listOf(
            "All",
            "Trending",
            "Animated",
            "Weather",
            "Seasonal",
            "Portrait",
            "Light & Optics",
            "Glitch & Digital",
            "Retro & Film",
            "Blur & Motion",
            "Stylize & Art",
            "Creator Pack"
        )
    }
}
