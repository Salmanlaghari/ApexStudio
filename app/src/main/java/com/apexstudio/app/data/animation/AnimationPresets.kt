package com.apexstudio.app.data.animation

import com.apexstudio.app.domain.model.Keyframe
import com.apexstudio.app.domain.model.KeyframeCurve
import com.apexstudio.app.domain.model.KeyframeTrack
import java.util.UUID

/**
 * 15 Professional Animation Presets (Zoom In, Zoom Out, Slide Left, Slide Right,
 * Slide Up, Slide Down, Rotate, Bounce, Shake, Fade In, Fade Out, Pop, Pulse, Spin, Blur In/Out).
 *
 * Each preset generates real, mathematically interpolated [KeyframeTrack] instances with
 * accurate timestamps and curves that render both in live preview and final video export.
 */
enum class AnimationPresetType(val displayName: String, val category: String) {
    ZOOM_IN("Zoom In", "In"),
    ZOOM_OUT("Zoom Out", "Out"),
    SLIDE_LEFT("Slide Left", "Slide"),
    SLIDE_RIGHT("Slide Right", "Slide"),
    SLIDE_UP("Slide Up", "Slide"),
    SLIDE_DOWN("Slide Down", "Slide"),
    ROTATE("Rotate", "Motion"),
    BOUNCE("Bounce", "Motion"),
    SHAKE("Shake", "Motion"),
    FADE_IN("Fade In", "Fade"),
    FADE_OUT("Fade Out", "Fade"),
    POP("Pop", "Pop"),
    PULSE("Pulse", "Loop"),
    SPIN("Spin", "Motion"),
    BLUR_IN("Blur In", "In"),
    BLUR_OUT("Blur Out", "Out")
}

object AnimationPresets {

    /**
     * Generate a real [KeyframeTrack] for a given animation preset spanning [startMs] to [endMs].
     */
    fun createTrack(
        type: AnimationPresetType,
        startMs: Long = 0L,
        durationMs: Long = 1000L
    ): KeyframeTrack {
        val endMs = startMs + durationMs.coerceAtLeast(300L)
        val midMs = startMs + durationMs / 2

        val keyframes: List<Keyframe> = when (type) {
            AnimationPresetType.ZOOM_IN -> listOf(
                Keyframe(UUID.randomUUID().toString(), startMs, scale = 0.4f, opacity = 0.5f, curve = KeyframeCurve.EASE_OUT),
                Keyframe(UUID.randomUUID().toString(), endMs, scale = 1.0f, opacity = 1.0f, curve = KeyframeCurve.LINEAR)
            )

            AnimationPresetType.ZOOM_OUT -> listOf(
                Keyframe(UUID.randomUUID().toString(), startMs, scale = 1.6f, opacity = 0.6f, curve = KeyframeCurve.EASE_OUT),
                Keyframe(UUID.randomUUID().toString(), endMs, scale = 1.0f, opacity = 1.0f, curve = KeyframeCurve.LINEAR)
            )

            AnimationPresetType.SLIDE_LEFT -> listOf(
                Keyframe(UUID.randomUUID().toString(), startMs, translateX = 1.0f, opacity = 0.7f, curve = KeyframeCurve.EASE_OUT),
                Keyframe(UUID.randomUUID().toString(), endMs, translateX = 0.0f, opacity = 1.0f, curve = KeyframeCurve.LINEAR)
            )

            AnimationPresetType.SLIDE_RIGHT -> listOf(
                Keyframe(UUID.randomUUID().toString(), startMs, translateX = -1.0f, opacity = 0.7f, curve = KeyframeCurve.EASE_OUT),
                Keyframe(UUID.randomUUID().toString(), endMs, translateX = 0.0f, opacity = 1.0f, curve = KeyframeCurve.LINEAR)
            )

            AnimationPresetType.SLIDE_UP -> listOf(
                Keyframe(UUID.randomUUID().toString(), startMs, translateY = 1.0f, opacity = 0.7f, curve = KeyframeCurve.EASE_OUT),
                Keyframe(UUID.randomUUID().toString(), endMs, translateY = 0.0f, opacity = 1.0f, curve = KeyframeCurve.LINEAR)
            )

            AnimationPresetType.SLIDE_DOWN -> listOf(
                Keyframe(UUID.randomUUID().toString(), startMs, translateY = -1.0f, opacity = 0.7f, curve = KeyframeCurve.EASE_OUT),
                Keyframe(UUID.randomUUID().toString(), endMs, translateY = 0.0f, opacity = 1.0f, curve = KeyframeCurve.LINEAR)
            )

            AnimationPresetType.ROTATE -> listOf(
                Keyframe(UUID.randomUUID().toString(), startMs, rotationDeg = -180f, scale = 0.5f, curve = KeyframeCurve.EASE_OUT),
                Keyframe(UUID.randomUUID().toString(), endMs, rotationDeg = 0f, scale = 1.0f, curve = KeyframeCurve.LINEAR)
            )

            AnimationPresetType.BOUNCE -> {
                val q1 = startMs + durationMs / 4
                val q2 = startMs + durationMs / 2
                val q3 = startMs + (durationMs * 3) / 4
                listOf(
                    Keyframe(UUID.randomUUID().toString(), startMs, translateY = -0.6f, curve = KeyframeCurve.EASE_IN),
                    Keyframe(UUID.randomUUID().toString(), q1, translateY = 0.0f, curve = KeyframeCurve.EASE_OUT),
                    Keyframe(UUID.randomUUID().toString(), q2, translateY = -0.2f, curve = KeyframeCurve.EASE_IN),
                    Keyframe(UUID.randomUUID().toString(), q3, translateY = 0.0f, curve = KeyframeCurve.EASE_OUT),
                    Keyframe(UUID.randomUUID().toString(), endMs, translateY = 0.0f, curve = KeyframeCurve.LINEAR)
                )
            }

            AnimationPresetType.SHAKE -> {
                val step = durationMs / 6
                listOf(
                    Keyframe(UUID.randomUUID().toString(), startMs, translateX = 0f, curve = KeyframeCurve.LINEAR),
                    Keyframe(UUID.randomUUID().toString(), startMs + step, translateX = -0.08f, translateY = 0.04f, curve = KeyframeCurve.LINEAR),
                    Keyframe(UUID.randomUUID().toString(), startMs + step * 2, translateX = 0.08f, translateY = -0.04f, curve = KeyframeCurve.LINEAR),
                    Keyframe(UUID.randomUUID().toString(), startMs + step * 3, translateX = -0.05f, translateY = 0.02f, curve = KeyframeCurve.LINEAR),
                    Keyframe(UUID.randomUUID().toString(), startMs + step * 4, translateX = 0.05f, translateY = -0.02f, curve = KeyframeCurve.LINEAR),
                    Keyframe(UUID.randomUUID().toString(), startMs + step * 5, translateX = -0.02f, translateY = 0.01f, curve = KeyframeCurve.LINEAR),
                    Keyframe(UUID.randomUUID().toString(), endMs, translateX = 0f, translateY = 0f, curve = KeyframeCurve.LINEAR)
                )
            }

            AnimationPresetType.FADE_IN -> listOf(
                Keyframe(UUID.randomUUID().toString(), startMs, opacity = 0.0f, curve = KeyframeCurve.EASE_IN_OUT),
                Keyframe(UUID.randomUUID().toString(), endMs, opacity = 1.0f, curve = KeyframeCurve.LINEAR)
            )

            AnimationPresetType.FADE_OUT -> listOf(
                Keyframe(UUID.randomUUID().toString(), startMs, opacity = 1.0f, curve = KeyframeCurve.EASE_IN_OUT),
                Keyframe(UUID.randomUUID().toString(), endMs, opacity = 0.0f, curve = KeyframeCurve.LINEAR)
            )

            AnimationPresetType.POP -> listOf(
                Keyframe(UUID.randomUUID().toString(), startMs, scale = 0.1f, opacity = 0.2f, curve = KeyframeCurve.EASE_OUT),
                Keyframe(UUID.randomUUID().toString(), midMs, scale = 1.25f, opacity = 1.0f, curve = KeyframeCurve.EASE_IN_OUT),
                Keyframe(UUID.randomUUID().toString(), endMs, scale = 1.0f, opacity = 1.0f, curve = KeyframeCurve.LINEAR)
            )

            AnimationPresetType.PULSE -> listOf(
                Keyframe(UUID.randomUUID().toString(), startMs, scale = 1.0f, curve = KeyframeCurve.EASE_IN_OUT),
                Keyframe(UUID.randomUUID().toString(), midMs, scale = 1.18f, curve = KeyframeCurve.EASE_IN_OUT),
                Keyframe(UUID.randomUUID().toString(), endMs, scale = 1.0f, curve = KeyframeCurve.LINEAR)
            )

            AnimationPresetType.SPIN -> listOf(
                Keyframe(UUID.randomUUID().toString(), startMs, rotationDeg = 0f, curve = KeyframeCurve.EASE_IN_OUT),
                Keyframe(UUID.randomUUID().toString(), endMs, rotationDeg = 360f, curve = KeyframeCurve.LINEAR)
            )

            AnimationPresetType.BLUR_IN -> listOf(
                Keyframe(UUID.randomUUID().toString(), startMs, scale = 1.15f, opacity = 0.1f, effectIntensity = 1.0f, curve = KeyframeCurve.EASE_OUT),
                Keyframe(UUID.randomUUID().toString(), endMs, scale = 1.0f, opacity = 1.0f, effectIntensity = 0.0f, curve = KeyframeCurve.LINEAR)
            )

            AnimationPresetType.BLUR_OUT -> listOf(
                Keyframe(UUID.randomUUID().toString(), startMs, scale = 1.0f, opacity = 1.0f, effectIntensity = 0.0f, curve = KeyframeCurve.EASE_IN),
                Keyframe(UUID.randomUUID().toString(), endMs, scale = 0.9f, opacity = 0.0f, effectIntensity = 1.0f, curve = KeyframeCurve.LINEAR)
            )
        }

        return KeyframeTrack(keyframes).sorted()
    }
}
