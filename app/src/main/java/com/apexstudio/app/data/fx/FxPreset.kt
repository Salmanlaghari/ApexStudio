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
    val category: String = "Light & Optics",
    val description: String = ""
) {
    // 1. Light & Optics (11)
    VIGNETTE("vignette", "Vignette", "Light & Optics", "Darkened dramatic border falloff"),
    BLOOM("bloom", "Bloom Glow", "Light & Optics", "Dreamy luminous highlight bleeding"),
    PRISM("prism", "Prism Flare", "Light & Optics", "Rainbow prism light dispersion"),
    HALATION("halation", "Halation", "Light & Optics", "Warm filmic edge glow around high contrast"),
    LENS_FLARE("lens_flare", "Lens Flare", "Light & Optics", "Optical anamorphic lens flare & sun streak"),
    LIGHT_LEAK("light_leak", "Light Leak", "Light & Optics", "Organic warm film gate light leak"),
    ANAMORPHIC("anamorphic", "Anamorphic Streak", "Light & Optics", "Horizontal cinematic sci-fi light streak"),
    BOKEH_OVERLAY("bokeh_overlay", "Bokeh Orbs", "Light & Optics", "Soft floating hexagonal out-of-focus orbs"),
    SPARKLE("sparkle", "Glitter Sparkle", "Light & Optics", "Glistening specular twinkle highlights"),
    SUN_BEAM("sun_beam", "Sun Rays", "Light & Optics", "Volumetric atmospheric god rays"),
    GLOW_DIFFUSE("glow_diffuse", "Glow Diffuse", "Light & Optics", "Soft mist diffusion filter look"),

    // 2. Glitch & Digital (10)
    GLITCH("glitch", "Glitch", "Glitch & Digital", "Digital signal break with slice shifts"),
    CHROMATIC("chromatic", "RGB Split", "Glitch & Digital", "Radial chromatic aberration and channel offset"),
    PIXELATE("pixelate", "Pixelate", "Glitch & Digital", "Retro 8-bit dynamic mosaic blocks"),
    BAD_TV("bad_tv", "Bad TV CRT", "Glitch & Digital", "Cathode-ray television distortion & hum"),
    PIXEL_SORT("pixel_sort", "Pixel Sort", "Glitch & Digital", "Luma-based streaks dripping across edges"),
    DATAMOSH("datamosh", "Datamosh", "Glitch & Digital", "Codec vector bleed and ghosted tearing"),
    RGB_JITTER("rgb_jitter", "RGB Jitter", "Glitch & Digital", "High-frequency color fringing pulse"),
    CRT_PHOSPHOR("crt_phosphor", "CRT Phosphor", "Glitch & Digital", "Trinitron subpixel RGB stripe matrix"),
    INTERLACED("interlaced", "Interlaced Scan", "Glitch & Digital", "Analog 480i comb field artifacting"),
    DIGITAL_DROP("digital_drop", "Bit Drop", "Glitch & Digital", "Quantized posterized bit-depth crush"),

    // 3. Retro & Film (11)
    FILM_GRAIN("film_grain", "Film Grain", "Retro & Film", "Organic 35mm silver halide grain texture"),
    VHS("vhs", "VHS Tape", "Retro & Film", "Horizontal wobble, tracking bar, color blur"),
    SCANLINES("scanlines", "Scanlines", "Retro & Film", "Retro arcade monitor horizontal scanlines"),
    STROBE("strobe", "Strobe", "Retro & Film", "Pulsing rhythmic light shutter effect"),
    SEPIA_GRAIN("sepia_grain", "Sepia 1920", "Retro & Film", "Vintage warm sepia tone with heavy grain"),
    DUST_SCRATCHES("dust_scratches", "Dust & Scratches", "Retro & Film", "Projector hairs, dust specks, and celluloid scratches"),
    FILM_DAMAGE("film_damage", "Film Burn", "Retro & Film", "End-of-reel projector burn & orange bleed"),
    BLEACH_BYPASS("bleach_bypass", "Bleach Bypass", "Retro & Film", "Silver retention desaturated high contrast"),
    TECHNICOLOR_STRIP("technicolor_strip", "Technicolor 3-Strip", "Retro & Film", "Vibrant 1950s dye-transfer color process"),
    CROSS_PROCESS("cross_process", "Cross Process", "Retro & Film", "E-6 slide film in C-41 chemistry shift"),
    SOLARIZE("solarize", "Solarize", "Retro & Film", "Sabattier photographic tone reversal"),

    // 4. Blur & Motion (10)
    SOFT_BLUR("soft_blur", "Soft Blur", "Blur & Motion", "Gentle gaussian smoothing blur"),
    ZOOM_BLUR("zoom_blur", "Zoom Blur", "Blur & Motion", "Radial zoom blur pushing toward center"),
    SHAKE("shake", "Cam Shake", "Blur & Motion", "Dynamic handheld camera movement jitter"),
    RADIAL_BLUR("radial_blur", "Radial Blur", "Blur & Motion", "Rotational circular motion blur"),
    TILT_SHIFT("tilt_shift", "Tilt Shift", "Blur & Motion", "Miniature diorama selective focus band"),
    MOTION_STREAK("motion_streak", "Motion Streak", "Blur & Motion", "High-speed directional velocity streaks"),
    GHOSTING("ghosting", "Ghost Trail", "Blur & Motion", "Temporal phosphor persistence lag"),
    SPIN_BLUR("spin_blur", "Spin Blur", "Blur & Motion", "High-velocity vortex spin blur"),
    CAMERA_WOBBLE("camera_wobble", "Cam Wobble", "Blur & Motion", "Floating organic handheld drifting"),
    WHIP_PAN_FX("whip_pan_fx", "Whip Blur", "Blur & Motion", "Horizontal rapid whip pan sweep"),

    // 5. Stylize & Art (10)
    HALFTONE("halftone", "Halftone Dots", "Stylize & Art", "Vintage comic print CMYK halftone dots"),
    SKETCH_LINES("sketch_lines", "Pencil Sketch", "Stylize & Art", "Graphite contour edge sketch rendering"),
    POSTERIZE("posterize", "Posterize Pop", "Stylize & Art", "Warhol pop art color quantization"),
    EDGE_NEON("edge_neon", "Neon Edges", "Stylize & Art", "Glowing luminescent Sobel edge detection"),
    THERMAL_VISION("thermal_vision", "Thermal Vision", "Stylize & Art", "FLIR heat gradient color mapping"),
    NIGHT_VISION("night_vision", "Night Vision", "Stylize & Art", "Phosphor green intensified amplification"),
    OIL_PAINT("oil_paint", "Oil Impression", "Stylize & Art", "Brush-stroked painterly canvas texture"),
    EMBOSS_RELIEF("emboss_relief", "Emboss 3D", "Stylize & Art", "Chiseled bas-relief stone sculpture look"),
    INVERT_FX("invert_fx", "Invert Neg", "Stylize & Art", "Photographic negative color inversion"),
    KALEIDOSCOPE("kaleidoscope", "Kaleidoscope", "Stylize & Art", "Symmetrical optical prism mirroring");

    companion object {
        fun byId(id: String?): FxPreset? = values().firstOrNull { it.id == id }
        fun categories(): List<String> = listOf(
            "All",
            "Light & Optics",
            "Glitch & Digital",
            "Retro & Film",
            "Blur & Motion",
            "Stylize & Art"
        )
    }
}
