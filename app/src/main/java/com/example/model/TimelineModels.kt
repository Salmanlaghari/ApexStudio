package com.example.model

import androidx.compose.ui.graphics.Color
import java.io.File

/**
 * Supported real visual effects with distinct processing pipelines.
 */
enum class EffectType(val displayName: String, val description: String) {
    NONE("None", "Original clean feed"),
    RGB_JITTER("RGB Jitter", "True channel-shift chromatic aberration with dynamic jitter"),
    PIXEL_SORT("Pixel Sort", "Real luminance-thresholded horizontal pixel sorting streaks"),
    DATAMOSH("Datamosh", "Authentic macroblock motion vector distortion & corruption"),
    BLEACH_BYPASS("Bleach Bypass", "Photographic silver retention high-contrast desaturation"),
    VHS_GLITCH("VHS Glitch", "CRT scanlines, phosphor bloom & horizontal tracking tears"),
    THERMAL("Thermal", "Ironbow false-color infrared heatmap gradient mapping"),
    HALFTONE("Halftone Comic", "Sobel edge contours with newsprint dot-matrix screen"),
    VINTAGE_8MM("Vintage 8mm", "Analog film grain, vertical scratches & warm sepia tint"),
    ZOOM_BLUR("Zoom Blur", "Multi-sample radial blur radiating from frame center"),
    CYBERPUNK_GLOW("Cyberpunk Glow", "High-pass neon cyan & magenta luminance bloom")
}

/**
 * Real Chroma Key configuration parameters.
 */
data class ChromaKeyState(
    val enabled: Boolean = false,
    val keyColor: Int = 0xFF00FF00.toInt(), // Default neon green
    val similarity: Float = 0.38f,          // Color distance threshold (0.0 to 1.0)
    val smoothness: Float = 0.18f,          // Edge falloff smoothing (0.0 to 1.0)
    val spillSuppression: Float = 0.55f,    // Green spill cancellation (0.0 to 1.0)
    val showMatteOnly: Boolean = false,     // Black & White alpha matte inspection
    val backgroundPlate: BackgroundPlate = BackgroundPlate.CHECKERBOARD
)

enum class BackgroundPlate(val displayName: String) {
    CHECKERBOARD("Transparency"),
    STUDIO_DARK("Studio Stage"),
    CYBER_CITY("Cyber City"),
    SUNSET_BEACH("Sunset Beach"),
    BLACK("Solid Black")
}

/**
 * 12-factor Color Grading adjustment parameters.
 */
data class AdjustmentValues(
    val brightness: Float = 0f,      // -100 to +100
    val contrast: Float = 0f,        // -100 to +100
    val saturation: Float = 0f,      // -100 to +100
    val hue: Float = 0f,             // -180 to +180
    val exposure: Float = 0f,        // -100 to +100
    val highlights: Float = 0f,      // -100 to +100
    val shadows: Float = 0f,         // -100 to +100
    val temperature: Float = 0f,     // -100 (Cool) to +100 (Warm)
    val tint: Float = 0f,            // -100 (Green) to +100 (Magenta)
    val vignette: Float = 0f,        // 0 to 100
    val sharpness: Float = 0f,       // -100 to +100
    val fade: Float = 0f             // 0 to 100 (lifts black levels)
) {
    val isDefault: Boolean
        get() = brightness == 0f && contrast == 0f && saturation == 0f &&
                hue == 0f && exposure == 0f && highlights == 0f &&
                shadows == 0f && temperature == 0f && tint == 0f &&
                vignette == 0f && sharpness == 0f && fade == 0f
}

/**
 * 12 Curated Color Grading Presets.
 */
data class ColorPreset(
    val id: String,
    val name: String,
    val description: String,
    val values: AdjustmentValues
)

/**
 * Audio Track State for genuine audio playback, fade, mute, and trim.
 */
data class AudioTrackState(
    val title: String = "No Audio Loaded",
    val audioFile: File? = null,
    val durationMs: Long = 0L,
    val volume: Float = 1.0f,         // 0.0f to 2.0f
    val isMuted: Boolean = false,
    val fadeInSec: Float = 0.5f,       // Fade in curve duration (0.0 to 5.0)
    val fadeOutSec: Float = 0.5f,      // Fade out curve duration (0.0 to 5.0)
    val trimStartMs: Long = 0L,        // Clip trim start point
    val trimEndMs: Long = 0L,          // Clip trim end point
    val waveforms: List<Float> = emptyList(), // Normalized amplitude peaks (0.0 to 1.0)
    val isRoyaltyFree: Boolean = false
) {
    val effectiveDurationMs: Long
        get() = if (trimEndMs > trimStartMs) trimEndMs - trimStartMs else durationMs
}

/**
 * Royalty-free library item.
 */
data class RoyaltyFreeTrack(
    val id: String,
    val title: String,
    val genre: String,
    val bpm: Int,
    val durationMs: Long,
    val description: String
)

/**
 * Active panel tool tabs.
 */
enum class EditorTab(val title: String) {
    TIMELINE("Timeline"),
    CHROMA_KEY("3D ChromaKey"),
    EFFECTS("FX / Shaders"),
    ADJUST("Color Grading"),
    AUDIO("Audio Workstation")
}
