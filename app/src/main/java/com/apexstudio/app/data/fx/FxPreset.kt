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
enum class FxPreset(val id: String, val label: String, val category: String = "Trending") {
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
    CHROMAKEY_3D("3d_chromakey", "3D ChromaKey VFX", "Stylize & Art");

    companion object {
        fun byId(id: String?): FxPreset? = values().firstOrNull { it.id == id }

        fun categories(): List<String> = listOf(
            "All",
            "Trending",
            "Light & Optics",
            "Glitch & Digital",
            "Retro & Film",
            "Blur & Motion",
            "Stylize & Art"
        )
    }
}
