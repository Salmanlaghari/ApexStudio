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
enum class FxPreset(val id: String, val label: String) {
    VIGNETTE("vignette", "Vignette"),
    FILM_GRAIN("film_grain", "Film Grain"),
    VHS("vhs", "VHS"),
    GLITCH("glitch", "Glitch"),
    PIXELATE("pixelate", "Pixelate"),
    CHROMATIC("chromatic", "Chromatic"),
    SCANLINES("scanlines", "Scanlines"),
    SOFT_BLUR("soft_blur", "Soft Blur");

    companion object {
        fun byId(id: String?): FxPreset? = values().firstOrNull { it.id == id }
    }
}
