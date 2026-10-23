package com.apexstudio.app.domain.model

import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * One crop generated from an actual decoded source frame. Timestamps are absolute source-media
 * milliseconds (not project or trimmed-clip time); this keeps the track stable when the clip is
 * moved or trimmed. Coordinates use normalized top-left-origin source space.
 */
@Serializable
data class FaceCropKeyframe(
    val sourceTimestampMs: Long,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    /** False means the crop is a fallback/interpolated sample, not a face detection. */
    val faceFound: Boolean,
) {
    init {
        require(sourceTimestampMs >= 0L) { "Face crop timestamps must be non-negative source times." }
        require(listOf(left, top, right, bottom).all { it.isFinite() && it in 0f..1f }) {
            "Face crop coordinates must be finite and within source bounds."
        }
        require(right > left && bottom > top) { "Face crop must have positive width and height." }
    }

    internal fun cropAt(other: FaceCropKeyframe, fraction: Float): VideoCropSettings = VideoCropSettings(
        left = left + (other.left - left) * fraction,
        top = top + (other.top - top) * fraction,
        right = right + (other.right - right) * fraction,
        bottom = bottom + (other.bottom - bottom) * fraction,
        presetId = "free",
    )
}

/**
 * Persistable time-varying crop geometry from offline face analysis. Construction validates source
 * geometry, strictly increasing measured timestamps, crop bounds, and fixed output aspect parity.
 * Lookup clamps outside the sampled range and linearly interpolates between actual source samples.
 */
@Serializable
data class FaceCropTrack(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val outputAspectRatio: Float,
    val keyframes: List<FaceCropKeyframe>,
) {
    init {
        require(sourceWidth > 0 && sourceHeight > 0) { "Face crop source dimensions must be positive." }
        require(outputAspectRatio.isFinite() && outputAspectRatio > 0f) { "Output aspect ratio must be positive and finite." }
        require(keyframes.isNotEmpty()) { "A face crop track must contain at least one measured keyframe." }
        require(keyframes.zipWithNext().all { (a, b) -> a.sourceTimestampMs < b.sourceTimestampMs }) {
            "Face crop keyframes must have strictly increasing source timestamps."
        }
        keyframes.forEach { frame ->
            val cropAspect = ((frame.right - frame.left) * sourceWidth) /
                ((frame.bottom - frame.top) * sourceHeight)
            require(cropAspect.isFinite() && abs(cropAspect - outputAspectRatio) <= outputAspectRatio * ASPECT_RELATIVE_TOLERANCE) {
                "Face crop keyframe does not match the track output aspect ratio."
            }
        }
    }

    /** Return interpolated normalized geometry at an absolute source-media timestamp. */
    fun cropAtSourceTime(sourceTimestampMs: Long): VideoCropSettings {
        require(sourceTimestampMs >= 0L) { "Source timestamp must be non-negative." }
        if (keyframes.size == 1 || sourceTimestampMs <= keyframes.first().sourceTimestampMs) {
            return keyframes.first().toSettings()
        }
        if (sourceTimestampMs >= keyframes.last().sourceTimestampMs) return keyframes.last().toSettings()

        // Locate the first keyframe at or after the requested time; strict timestamp ordering is
        // established by construction, so both the interval and interpolation denominator are safe.
        var low = 0
        var high = keyframes.lastIndex
        while (low < high) {
            val mid = (low + high) ushr 1
            if (keyframes[mid].sourceTimestampMs < sourceTimestampMs) low = mid + 1 else high = mid
        }
        val after = keyframes[low]
        if (after.sourceTimestampMs == sourceTimestampMs) return after.toSettings()
        val before = keyframes[low - 1]
        val fraction = (sourceTimestampMs - before.sourceTimestampMs).toFloat() /
            (after.sourceTimestampMs - before.sourceTimestampMs).toFloat()
        return before.cropAt(after, fraction)
    }

    private fun FaceCropKeyframe.toSettings() = VideoCropSettings(
        left = left,
        top = top,
        right = right,
        bottom = bottom,
        presetId = "free",
    )

    companion object {
        private const val ASPECT_RELATIVE_TOLERANCE = 0.02f
    }
}
