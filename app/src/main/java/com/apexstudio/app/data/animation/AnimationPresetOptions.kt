package com.apexstudio.app.data.animation

import com.apexstudio.app.domain.model.Keyframe
import com.apexstudio.app.domain.model.KeyframeTrack
import java.util.UUID

/** The animation phase selected in the compact preset picker. */
enum class AnimationPresetMode {
    IN,
    OUT,
    LOOP
}

/**
 * Builds the exact keyframe track the editor should attach to a clip for the
 * selected preset options. No preview-only animation is introduced: callers
 * pass this track through the same clip keyframe path used by preview/export.
 */
object AnimationPresetOptions {
    const val MIN_DURATION_MS = 300L
    const val MAX_DURATION_MS = 3_000L
    const val DEFAULT_DURATION_MS = 1_000L
    const val MIN_INTENSITY = 0.25f
    const val MAX_INTENSITY = 1.5f
    const val DEFAULT_INTENSITY = 1.0f

    /**
     * Create a real track with duration and amplitude applied to every keyframe.
     * Intensity scales each animated value around its identity value; timestamps
     * span exactly the requested, clamped duration. Loop mode tiles a cyclic
     * preset's keyframes across that same total duration.
     */
    fun createTrack(
        type: AnimationPresetType,
        mode: AnimationPresetMode,
        durationMs: Long,
        intensity: Float,
        startMs: Long = 0L
    ): KeyframeTrack {
        val duration = durationMs.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)
        val strength = intensity.coerceIn(MIN_INTENSITY, MAX_INTENSITY)
        val endMs = startMs + duration
        val base = AnimationPresets.createTrack(type, startMs = 0L, durationMs = duration)
            .keyframes
        val transformed = when (mode) {
            AnimationPresetMode.IN -> base.map { frame ->
                frame.copy(timeMs = startMs + frame.timeMs, id = UUID.randomUUID().toString())
                    .withIntensity(strength)
            }
            AnimationPresetMode.OUT -> {
                val naturalExit = type in NATURAL_EXIT_PRESETS
                val exitFrames = if (naturalExit) base else base.asReversed()
                exitFrames.mapIndexed { index, frame ->
                    val sourceOffset = (frame.timeMs - base.first().timeMs).coerceIn(0L, duration)
                    val relativeTime = if (naturalExit) sourceOffset else duration - sourceOffset
                    frame.copy(timeMs = startMs + relativeTime, id = UUID.randomUUID().toString())
                        .withIntensity(strength)
                }.sortedBy { it.timeMs }
            }
            AnimationPresetMode.LOOP -> tileLoop(base, startMs, duration, strength)
        }
        // Preserve an exact endpoint even if a source preset has irregular spans.
        return KeyframeTrack(transformed.map { frame ->
            if (frame.timeMs > endMs) frame.copy(timeMs = endMs) else frame
        }).sorted()
    }

    private fun tileLoop(base: List<Keyframe>, startMs: Long, duration: Long, intensity: Float): List<Keyframe> {
        val cycleCount = 3
        val cycleDuration = duration / cycleCount
        val sourceStart = base.first().timeMs
        val sourceSpan = (base.last().timeMs - sourceStart).coerceAtLeast(1L)
        return buildList {
            for (cycle in 0 until cycleCount) {
                val cycleStart = startMs + cycle * cycleDuration
                base.forEachIndexed { index, frame ->
                    // Omit duplicated seam frames; each preceding cycle already
                    // ends at the same identity pose where the next one begins.
                    if (cycle > 0 && index == 0) return@forEachIndexed
                    val sourceOffset = frame.timeMs - sourceStart
                    val offset = (sourceOffset.toDouble() / sourceSpan * cycleDuration).toLong()
                    val time = if (cycle == cycleCount - 1 && index == base.lastIndex) {
                        startMs + duration
                    } else {
                        (cycleStart + offset).coerceAtMost(startMs + duration)
                    }
                    add(frame.copy(timeMs = time, id = UUID.randomUUID().toString()).withIntensity(intensity))
                }
            }
        }
    }

    private fun Keyframe.withIntensity(value: Float): Keyframe = copy(
        translateX = translateX * value,
        translateY = translateY * value,
        scale = 1f + (scale - 1f) * value,
        rotationDeg = rotationDeg * value,
        opacity = (1f + (opacity - 1f) * value).coerceIn(0f, 1f),
        effectIntensity = (1f + (effectIntensity - 1f) * value).coerceIn(0f, 1f),
        filterIntensity = (1f + (filterIntensity - 1f) * value).coerceIn(0f, 1f),
        volume = 1f + (volume - 1f) * value,
        saturation = saturation?.let { 1f + (it - 1f) * value }
    )

    private val NATURAL_EXIT_PRESETS = setOf(
        AnimationPresetType.ZOOM_OUT,
        AnimationPresetType.FADE_OUT,
        AnimationPresetType.BLUR_OUT,
        AnimationPresetType.ZOOM_FADE_OUT
    )
}
