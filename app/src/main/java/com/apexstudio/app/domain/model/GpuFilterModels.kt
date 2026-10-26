package com.apexstudio.app.domain.model

import kotlinx.serialization.Serializable

/** Serializable GPU color/effect configuration shared by domain, preview and export. */
@Serializable
enum class StylisticEffectType(
    val title: String,
    val description: String,
    val category: String,
    val defaultParam: Float = 0.5f,
    val paramName: String = "Intensity"
) {
    NONE("Original", "No stylistic effect applied", "Basic"),
    VIGNETTE("Vignette", "Soft cinema border shading focusing attention to center", "Cinematic", 0.75f, "Border Depth"),
    HALFTONE("Halftone Print", "Classic retro comic & newspaper dot matrix pattern", "Artistic", 0.4f, "Dot Density"),
    TOON("Cel Cartoon", "Stylized animation contours with quantized color bands", "Artistic", 0.5f, "Threshold"),
    SMOOTH_TOON("Smooth Anime", "Smooth cel-shaded anime look with softened edges", "Artistic", 0.5f, "Quantization"),
    SKETCH("Pencil Sketch", "Hand-drawn monochrome pencil illustration lineart", "Artistic", 0.8f, "Line Weight"),
    PIXELATION("8-Bit Pixel", "Retro arcade game pixel grid aesthetic", "Retro", 0.35f, "Pixel Size"),
    EMBOSS("Metallic Emboss", "Tactile 3D metallic stamped surface relief", "Stylistic"),
    SWIRL("Vortex Swirl", "Gravitational optical vortex spiral distortion", "Distortion"),
    BULGE("Fish-Eye Bulge", "Wide-angle curved glass lens perspective", "Distortion", 0.6f, "Curvature"),
    GLASS_SPHERE("Crystal Ball", "Optical refractive crystal sphere inversion", "Distortion"),
    KUWAHARA("Oil Painting", "Impressionistic painterly brush stroke smoothing", "Artistic"),
    POSTERIZE("Pop Art Poster", "Bold Pop-art color separation and tonal reduction", "Artistic", 0.4f, "Color Bands"),
    CROSSHATCH("Crosshatch", "Finely etched pen-and-ink crosshatched shading", "Artistic", 0.4f, "Hatch Spacing"),
    SOLARIZE("Solarize", "Experimental Sabattier photographic tone reversal", "Stylistic"),
    GAUSSIAN_BLUR("Dreamy Blur", "Soft atmospheric bloom and ethereal glow", "Cinematic"),
    SHARPEN("Crisp Clarity", "Ultra-high definition edge clarity and texture", "Cinematic", 0.6f, "Sharpness"),
    SEPIA("Antique Sepia", "Warm golden 19th-century vintage photograph", "Retro", 0.8f, "Warmth"),
    INVERT("Color Negative", "Striking reversed chromatic optical negative", "Stylistic", 1.0f, "Inversion")
}

@Serializable
data class GpuFilterConfig(
    val filterPresetId: String? = null,
    val filterIntensity: Float = 1.0f,
    val stylisticEffect: StylisticEffectType = StylisticEffectType.NONE,
    val stylisticIntensity: Float = 1.0f,
    val effectParam: Float = 0.5f,
    val brightness: Float = 0f,
    val contrast: Float = 1.0f,
    val saturation: Float = 1.0f,
    val temperature: Float = 5000f,
    val tint: Float = 0f,
    val exposure: Float = 0f,
    val gamma: Float = 1.0f,
    val highlights: Float = 1.0f,
    val shadows: Float = 0f,
    val vibrance: Float = 0f,
    val lightness: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    val hue: Float = 0f,
    val vignette: Float = 0f,
    val grain: Float = 0f,
    val clarity: Float = 0f,
    val denoise: Float = 0f,
    val sharpness: Float = 0f,
    val activeProfileId: String? = null,
    val profileIntensity: Float = 1.0f
) {
    val isDefault: Boolean
        get() = activeProfileId == null && filterPresetId == null && stylisticEffect == StylisticEffectType.NONE &&
            brightness == 0f && contrast == 1f && saturation == 1f && temperature == 5000f && tint == 0f &&
            exposure == 0f && gamma == 1f && highlights == 1f && shadows == 0f && vibrance == 0f &&
            lightness == 0f && whites == 0f && blacks == 0f && hue == 0f && vignette == 0f && grain == 0f &&
            clarity == 0f && denoise == 0f && sharpness == 0f
}

/** Maps the persisted GPU profile config onto the shared Media3 adjustment model. */
fun GpuFilterConfig.toVideoAdjustments(): VideoAdjustments = VideoAdjustments(
    brightness = brightness,
    contrast = contrast,
    saturation = when (stylisticEffect) {
        StylisticEffectType.SEPIA -> saturation * (1f - 0.35f * stylisticIntensity.coerceIn(0f, 1f))
        else -> saturation
    },
    exposure = exposure,
    temperature = (((temperature - 5000f) / 3000f) +
        if (stylisticEffect == StylisticEffectType.SEPIA) 0.35f * stylisticIntensity.coerceIn(0f, 1f) else 0f)
        .coerceIn(-1f, 1f),
    tint = ((tint / 100f) +
        if (stylisticEffect == StylisticEffectType.SEPIA) 0.12f * stylisticIntensity.coerceIn(0f, 1f) else 0f)
        .coerceIn(-1f, 1f),
    highlights = highlights - 1f,
    shadows = shadows,
    sharpness = (sharpness +
        if (stylisticEffect == StylisticEffectType.SHARPEN) stylisticIntensity else 0f).coerceIn(0f, 1f),
    vignette = (vignette +
        if (stylisticEffect == StylisticEffectType.VIGNETTE) stylisticIntensity else 0f).coerceIn(0f, 1f),
    grain = grain,
    lightness = lightness,
    whites = whites,
    blacks = blacks,
    hue = hue,
    clarity = clarity,
    denoise = denoise,
    vibrance = vibrance
)
