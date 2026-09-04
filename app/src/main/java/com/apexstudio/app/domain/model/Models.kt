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
    val textOverlays: List<TextOverlay> = emptyList()
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
    val endMs: Long = Long.MAX_VALUE
) {
    fun isActiveAt(timeMs: Long): Boolean = timeMs in startMs..endMs

    companion object {
        fun of(
            id: String = java.util.UUID.randomUUID().toString(),
            text: String = "Text",
            x: Float = 0.5f,
            y: Float = 0.5f,
            sizeScale: Float = 1f,
            colorArgb: Long = 0xFFFFFFFFL,
            bgArgb: Long? = null,
            strokeColorArgb: Long? = null,
            shadowColorArgb: Long? = null,
            fontFamily: String = "sans",
            isItalic: Boolean = false,
            isBold: Boolean = true,
            presetId: String? = null
        ): TextOverlay = TextOverlay(
            id = id, text = text, x = x, y = y, sizeScale = sizeScale,
            colorArgb = colorArgb, bgArgb = bgArgb, strokeColorArgb = strokeColorArgb,
            shadowColorArgb = shadowColorArgb, fontFamily = fontFamily,
            isItalic = isItalic, isBold = isBold, presetId = presetId
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
    QUARTER("0.25x", 0.25f),
    HALF("0.5x", 0.5f),
    NORMAL("1x", 1f),
    DOUBLE("2x", 2f),
    QUAD("4x", 4f),
    FAST("8x", 8f);

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
    val lastTransmissionTemplateId: String? = null
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
    val curve: KeyframeCurve = KeyframeCurve.LINEAR
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
    HOLD
}

/**
 * Full animated-transform track attached to a single clip. The
 * track stores the keyframes sorted by time and exposes
 * [interpolateAt] which returns the (translate, scale, rotation,
 * opacity) tuple to render at the given ms.
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
            return AnimatedTransform(k.translateX, k.translateY, k.scale, k.rotationDeg, k.opacity)
        }
        if (timeMs >= sorted.last().timeMs) {
            val k = sorted.last()
            return AnimatedTransform(k.translateX, k.translateY, k.scale, k.rotationDeg, k.opacity)
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
        val eased = ease(raw, b.curve)
        return AnimatedTransform(
            translateX = lerp(a.translateX, b.translateX, eased),
            translateY = lerp(a.translateY, b.translateY, eased),
            scale = lerp(a.scale, b.scale, eased),
            rotationDeg = lerp(a.rotationDeg, b.rotationDeg, eased),
            opacity = lerp(a.opacity, b.opacity, eased)
        )
    }

    private fun ease(t: Double, curve: KeyframeCurve): Double = when (curve) {
        KeyframeCurve.LINEAR -> t
        KeyframeCurve.EASE_IN -> t * t
        KeyframeCurve.EASE_OUT -> 1.0 - (1.0 - t) * (1.0 - t)
        KeyframeCurve.EASE_IN_OUT -> if (t < 0.5) 2 * t * t else 1 - 2 * (1 - t) * (1 - t)
        KeyframeCurve.HOLD -> 0.0 // first keyframe value until the next one
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
    val opacity: Float
) {
    companion object {
        val Identity = AnimatedTransform(0f, 0f, 1f, 0f, 1f)
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
    val resolution: String = "8K Ultra HD",
    val frameRate: Int = 60,
    val quality: ExportQuality = ExportQuality.HIGH,
    val estimatedSizeGb: Float = 1.8f
)

enum class ExportQuality(val label: String) {
    HIGH("High"), MEDIUM("Med"), LOW("Low")
}