package com.apexstudio.app.domain.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

@Serializable
data class MediaClip(
    val id: String,
    val name: String,
    val uri: String,
    val durationMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long,
    val thumbnail: String? = null,
    val trackIndex: Int = 0,
    val type: ClipType = ClipType.VIDEO,
    // Speed ramping: speedMultiplier is the per-clip playback rate
    // (0.25 slow-mo → 8 fast-forward). The user picks one of the
    // SpeedPreset values OR a custom value via the speed panel.
    // speedCurve models acceleration across the clip — LINEAR keeps
    // a constant rate, EASE_IN/OUT ramps the rate, RAMP allows
    // start/end keyframe anchors via [rampStartSpeed, rampEndSpeed].
    val speedMultiplier: Float = 1f,
    val speedCurve: SpeedCurve = SpeedCurve.LINEAR,
    val rampStartSpeed: Float = 1f,
    val rampEndSpeed: Float = 1f,
    // Animated transform track — empty by default. When populated
    // by the Keyframe panel, the GL effect applies the interpolated
    // translate / scale / rotation / opacity on every preview frame
    // and bakes the same into the exported video.
    val keyframes: KeyframeTrack = KeyframeTrack(),
    // Caption / title overlays attached to this clip. Each overlay
    // carries its own text, style, and a normalised (0..1) anchor
    // inside the video frame so the preview (Compose layer) and the
    // export (TextOverlayGlEffect) render the text at exactly the
    // same relative position and size.
    val textOverlays: List<TextOverlay> = emptyList(),
    val stickers: List<StickerOverlay> = emptyList()
)

/**
 * A text overlay (caption / title) rendered on top of a video clip.
 *
 * Position and font scale are *normalised* to the video frame:
 * [x], [y] are the centre of the text as fractions of the frame
 * width / height (0..1), and [sizeScale] multiplies a base font
 * size of ~7% of the frame height. Because both the editor preview
 * and the export GL effect resolve these against the same frame
 * geometry, what you drag on screen is exactly what bakes into the
 * MP4.
 *
 * Colours are stored as 0xAARRGGBB longs so the JSON project file
 * stays readable; null [bgArgb] means no pill behind the text.
 */
@Serializable
data class TextOverlay(
    val id: String,
    val text: String = "Text",
    // Normalised centre (0..1) inside the video frame.
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val sizeScale: Float = 1f,
    val colorArgb: Long = 0xFFFFFFFFL,
    val bgArgb: Long? = null,
    val strokeColorArgb: Long? = null,
    val shadowColorArgb: Long? = null,
    val fontFamily: String = "sans",
    val isItalic: Boolean = false,
    val isBold: Boolean = true,
    val presetId: String? = null,
    // Active window on the clip's timeline (ms). Defaults to the
    // whole clip.
    val startMs: Long = 0L,
    val endMs: Long = Long.MAX_VALUE,
    val animationType: String = "NONE",
    val animationDurationMs: Long = 800L,
    val rotationDeg: Float = 0f,
    val opacity: Float = 1f,
    val keyframes: KeyframeTrack = KeyframeTrack()
) {
    fun isActiveAt(timeMs: Long): Boolean = timeMs in startMs..endMs

    companion object {
        fun of(
            id: String = java.util.UUID.randomUUID().toString(),
            text: String = "Text",
            x: Float = 0.5f,
            y: Float = 0.35f,
            sizeScale: Float = 1f,
            colorArgb: Long = 0xFFFFFFFFL,
            bgArgb: Long? = null,
            strokeColorArgb: Long? = null,
            shadowColorArgb: Long? = null,
            fontFamily: String = "sans",
            isItalic: Boolean = false,
            isBold: Boolean = true,
            presetId: String? = null,
            animationType: String = "NONE",
            animationDurationMs: Long = 800L,
            rotationDeg: Float = 0f,
            opacity: Float = 1f,
            keyframes: KeyframeTrack = KeyframeTrack()
        ): TextOverlay = TextOverlay(
            id = id, text = text, x = x, y = y, sizeScale = sizeScale,
            colorArgb = colorArgb, bgArgb = bgArgb, strokeColorArgb = strokeColorArgb,
            shadowColorArgb = shadowColorArgb, fontFamily = fontFamily,
            isItalic = isItalic, isBold = isBold, presetId = presetId,
            animationType = animationType, animationDurationMs = animationDurationMs,
            rotationDeg = rotationDeg, opacity = opacity, keyframes = keyframes
        )
    }
}

enum class ClipType { VIDEO, OVERLAY, AUDIO, SFX }

@Serializable
enum class SpeedCurve {
    LINEAR,        // constant speed across the clip
    EASE_IN,       // accelerate from start speed → end speed
    EASE_OUT,      // decelerate from start speed → end speed
    EASE_IN_OUT,   // smooth accel + decel
    RAMP           // keyframe-driven (start + end)
}

enum class SpeedPreset(val label: String, val multiplier: Float) {
    SLOW_MO("0.1x", 0.1f),
    QUARTER("0.25x", 0.25f),
    HALF("0.5x", 0.5f),
    NORMAL("1x", 1f),
    DOUBLE("2x", 2f),
    QUAD("4x", 4f),
    FAST("8x", 8f),
    HYPER("10x", 10f);

    companion object {
        fun nearest(value: Float): SpeedPreset =
            values().minBy { kotlin.math.abs(it.multiplier - value) }
    }
}

@Serializable
data class Project(
    val id: String,
    val name: String,
    val durationMs: Long,
    val resolution: String = "4K",
    val fps: Int = 60,
    val clips: List<MediaClip> = emptyList(),
    val audioTracks: List<AudioTrack> = emptyList(),
    /**
     * Last transmission template the user applied to this project.
     * Pure metadata — when the editor opens the project it preloads
     * this template's LUT + FX + intensity so the user starts with
     * the look they had last time. Null means "no preset applied"
     * (the LUT / FX panels are still empty until the user picks one).
     *
     * Default-null + a default-value field means the JSON project
     * file stays backwards-compatible: older saved projects without
     * this field deserialise as null and behave exactly as before.
     */
    val lastTransmissionTemplateId: String? = null,
    // Transition hint chosen by the Transmission panel. Project-level
    // for now (per-clip transitions are a follow-up feature); null
    // means "no transition preset was picked". Persisted alongside
    // lastTransmissionTemplateId so the choice survives an app
    // restart.
    val lastTransitionType: String? = null,
    val lastTransitionDurationMs: Long = 500L,
    val transitions: List<ClipTransition> = emptyList(),
    val stickers: List<StickerOverlay> = emptyList(),
    val coverFrameMs: Long? = null,
    val coverCustomUri: String? = null
)

@Serializable
data class AudioTrack(
    val id: String,
    val name: String,
    val uri: String,
    val volume: Float = 0.75f,
    val isMuted: Boolean = false,
    val isSolo: Boolean = false,
    // Per-track trim + fade (all in ms, relative to the source media).
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L
) {
    /**
     * The kind of audio track, used by the mixer UI to label faders
     * ("Video", "Music", "Voiceover / SFX") and to pick a default
     * colour strip. SFX tracks default to a louder volume than music.
     */
    enum class Kind { ORIGINAL_VIDEO, MUSIC, SFX }

    /** Approximate source duration if known, else 0. */
    val sourceDurationMs: Long
        get() = trimEndMs

    /** Per-track volume multiplier in 0..1, with mute applied. */
    fun effectiveVolume(): Float = if (isMuted) 0f else volume.coerceIn(0f, 1f)
}

/**
 * One keyframe on a clip. Holds the four animated transform
 * properties every editor supports (translateX, translateY, scale,
 * rotation, opacity) at a single point on the clip's local timeline.
 *
 * `timeMs` is the absolute timeline position (in ms) at which the
 * keyframe's value is "pinned". The [KeyframeTrack] interpolates
 * between adjacent keyframes using the curve shape declared on the
 * later of the two.
 */
@Serializable
data class Keyframe(
    val id: String,
    val timeMs: Long,
    val translateX: Float = 0f,
    val translateY: Float = 0f,
    val scale: Float = 1f,
    val rotationDeg: Float = 0f,
    val opacity: Float = 1f,
    val volume: Float = 1f,
    val effectIntensity: Float = 1f,
    val filterIntensity: Float = 1f,
    val curve: KeyframeCurve = KeyframeCurve.LINEAR,
    val handleInX: Float = 0.42f,
    val handleInY: Float = 0.0f,
    val handleOutX: Float = 0.58f,
    val handleOutY: Float = 1.0f
) {
    companion object {
        fun identity(timeMs: Long, id: String = java.util.UUID.randomUUID().toString()) =
            Keyframe(id = id, timeMs = timeMs)
    }
}

@Serializable
enum class KeyframeCurve {
    /**
     * LINEAR is a straight line; the others are cheap analytic
     * approximations good enough for editor previews — a real CapCut
     * implementation would back these with an ML spline, but the math
     * here is the same `easeOutCubic` / `easeInOutQuad` family every
     * editor uses.
     */
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT,
    BEZIER,
    HOLD
}

/**
 * Full animated-transform track attached to a single clip. The
 * track stores the keyframes sorted by time and exposes
 * [interpolateAt] which returns the (translate, scale, rotation,
 * opacity, volume, effectIntensity, filterIntensity) tuple to render at the given ms.
 */
@Serializable
data class KeyframeTrack(
    val keyframes: List<Keyframe> = emptyList()
) {
    fun isEmpty(): Boolean = keyframes.isEmpty()

    fun sorted(): KeyframeTrack = copy(keyframes = keyframes.sortedBy { it.timeMs })

    /**
     * Return the animated transform at [timeMs] (interpolated from
     * the surrounding keyframes). When no keyframes exist, returns
     * the identity transform (translate 0, scale 1, rotation 0,
     * opacity 1). When [timeMs] is before the first or after the
     * last keyframe, clamps to that endpoint.
     */
    fun interpolateAt(timeMs: Long): AnimatedTransform {
        if (keyframes.isEmpty()) return AnimatedTransform.Identity
        val sorted = keyframes.sortedBy { it.timeMs }
        if (timeMs <= sorted.first().timeMs) {
            val k = sorted.first()
            return AnimatedTransform(
                translateX = k.translateX,
                translateY = k.translateY,
                scale = k.scale,
                rotationDeg = k.rotationDeg,
                opacity = k.opacity,
                volume = k.volume,
                effectIntensity = k.effectIntensity,
                filterIntensity = k.filterIntensity
            )
        }
        if (timeMs >= sorted.last().timeMs) {
            val k = sorted.last()
            return AnimatedTransform(
                translateX = k.translateX,
                translateY = k.translateY,
                scale = k.scale,
                rotationDeg = k.rotationDeg,
                opacity = k.opacity,
                volume = k.volume,
                effectIntensity = k.effectIntensity,
                filterIntensity = k.filterIntensity
            )
        }
        for (i in 0 until sorted.size - 1) {
            val a = sorted[i]
            val b = sorted[i + 1]
            if (timeMs in a.timeMs..b.timeMs) {
                return interpolatePair(a, b, timeMs)
            }
        }
        return AnimatedTransform.Identity
    }

    private fun interpolatePair(a: Keyframe, b: Keyframe, t: Long): AnimatedTransform {
        val span = (b.timeMs - a.timeMs).coerceAtLeast(1L)
        val raw = ((t - a.timeMs).toDouble() / span.toDouble()).coerceIn(0.0, 1.0)
        val eased = ease(raw, b.curve, b.handleInX.toDouble(), b.handleInY.toDouble(), b.handleOutX.toDouble(), b.handleOutY.toDouble())
        return AnimatedTransform(
            translateX = lerp(a.translateX, b.translateX, eased),
            translateY = lerp(a.translateY, b.translateY, eased),
            scale = lerp(a.scale, b.scale, eased),
            rotationDeg = lerp(a.rotationDeg, b.rotationDeg, eased),
            opacity = lerp(a.opacity, b.opacity, eased),
            volume = lerp(a.volume, b.volume, eased),
            effectIntensity = lerp(a.effectIntensity, b.effectIntensity, eased),
            filterIntensity = lerp(a.filterIntensity, b.filterIntensity, eased)
        )
    }

    private fun ease(
        t: Double,
        curve: KeyframeCurve,
        p1x: Double = 0.42,
        p1y: Double = 0.0,
        p2x: Double = 0.58,
        p2y: Double = 1.0
    ): Double = when (curve) {
        KeyframeCurve.LINEAR -> t
        KeyframeCurve.EASE_IN -> t * t
        KeyframeCurve.EASE_OUT -> 1.0 - (1.0 - t) * (1.0 - t)
        KeyframeCurve.EASE_IN_OUT -> if (t < 0.5) 2 * t * t else 1 - 2 * (1 - t) * (1 - t)
        KeyframeCurve.BEZIER -> solveCubicBezier(t, p1x, p1y, p2x, p2y)
        KeyframeCurve.HOLD -> 0.0 // first keyframe value until the next one
    }

    /**
     * Solves parametric cubic Bezier curve for given parameter [x] where:
     * P0 = (0,0), P1 = (p1x, p1y), P2 = (p2x, p2y), P3 = (1,1).
     * Solves x(u) = x for u via Newton-Raphson / bisection, then computes y(u).
     */
    private fun solveCubicBezier(
        x: Double,
        p1x: Double,
        p1y: Double,
        p2x: Double,
        p2y: Double
    ): Double {
        if (x <= 0.0) return 0.0
        if (x >= 1.0) return 1.0

        fun sampleCurveX(u: Double): Double =
            3.0 * (1.0 - u) * (1.0 - u) * u * p1x + 3.0 * (1.0 - u) * u * u * p2x + u * u * u

        fun sampleCurveY(u: Double): Double =
            3.0 * (1.0 - u) * (1.0 - u) * u * p1y + 3.0 * (1.0 - u) * u * u * p2y + u * u * u

        fun sampleDerivativeX(u: Double): Double =
            3.0 * (1.0 - u) * (1.0 - u) * p1x + 6.0 * (1.0 - u) * u * (p2x - p1x) + 3.0 * u * u * (1.0 - p2x)

        var u = x
        for (i in 0 until 8) {
            val currentX = sampleCurveX(u) - x
            if (kotlin.math.abs(currentX) < 1e-5) return sampleCurveY(u)
            val dX = sampleDerivativeX(u)
            if (kotlin.math.abs(dX) < 1e-6) break
            u -= currentX / dX
            u = u.coerceIn(0.0, 1.0)
        }

        // Fallback bisection if Newton-Raphson diverges
        var low = 0.0
        var high = 1.0
        u = x
        while (low < high && (high - low) > 1e-4) {
            val currentX = sampleCurveX(u)
            if (kotlin.math.abs(currentX - x) < 1e-4) break
            if (x > currentX) low = u else high = u
            u = (high + low) * 0.5
        }
        return sampleCurveY(u).coerceIn(0.0, 1.0)
    }

    private fun lerp(a: Float, b: Float, t: Double): Float =
        (a + (b - a) * t.toFloat())
}

/** Snapshot of a keyframe track evaluated at a single time point. */
data class AnimatedTransform(
    val translateX: Float,
    val translateY: Float,
    val scale: Float,
    val rotationDeg: Float,
    val opacity: Float,
    val volume: Float = 1f,
    val effectIntensity: Float = 1f,
    val filterIntensity: Float = 1f
) {
    companion object {
        val Identity = AnimatedTransform(0f, 0f, 1f, 0f, 1f, 1f, 1f, 1f)
    }
}

data class ToolItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val color: Color
)

data class LutPreset(
    val id: String,
    val name: String,
    val thumbnail: String
)

data class ExportSettings(
    val resolution: String = "1080p",
    val frameRate: Int = 60,
    val quality: ExportQuality = ExportQuality.HIGH,
    val estimatedSizeGb: Float = 1.8f,
    val bitrateMbps: Int = 18,
    val codec: String = "H.265",
    val activePresetId: String? = null
)

enum class ExportQuality(val label: String) {
    HIGH("High"), MEDIUM("Med"), LOW("Low")
}

@Serializable
data class VideoAdjustments(
    val hdr: Float = 0f,
    val brilliance: Float = 0f,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val exposure: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val sharpness: Float = 0f,
    val fade: Float = 0f,
    val vignette: Float = 0f,
    val grain: Float = 0f
) {
    val isDefault: Boolean
        get() = hdr == 0f &&
                brilliance == 0f &&
                brightness == 0f &&
                contrast == 1f &&
                saturation == 1f &&
                exposure == 0f &&
                highlights == 0f &&
                shadows == 0f &&
                temperature == 0f &&
                tint == 0f &&
                sharpness == 0f &&
                fade == 0f &&
                vignette == 0f &&
                grain == 0f
}

@Serializable
data class StickerOverlay(
    val id: String = java.util.UUID.randomUUID().toString(),
    val symbolOrUri: String = "🔥",
    val category: String = "Emoji",
    val name: String = "Sticker",
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val sizeScale: Float = 1f,
    val rotationDeg: Float = 0f,
    val opacity: Float = 1f,
    val startMs: Long = 0L,
    val endMs: Long = Long.MAX_VALUE,
    val keyframes: KeyframeTrack = KeyframeTrack()
) {
    fun isActiveAt(timeMs: Long): Boolean = timeMs in startMs..endMs
}