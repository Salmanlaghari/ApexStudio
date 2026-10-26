package com.apexstudio.app.domain.model.timeline

import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

/** A value pinned to a point on a timeline. Keyframe lists are sampled in ascending [timeMs] order. */
@Serializable
data class Keyframe<T>(
    val timeMs: Long,
    val value: T,
    val curve: Curve = Curve.SMOOTH
)

@Serializable
enum class Curve {
    LINEAR,
    SMOOTH,
    EASE_IN,
    EASE_OUT
}

/** Interpolates two values at a fraction. Implementations should not clamp [fraction]. */
fun interface Type<T> {
    fun interpolate(start: T, end: T, fraction: Float): T
}

@Serializable
data class XY(val x: Float, val y: Float)

@Serializable
data class Pan(val value: Float)

@Serializable
enum class BlendMode {
    NORMAL,
    MULTIPLY,
    SCREEN,
    OVERLAY,
    SOFT_LIGHT
}

object FloatType : Type<Float> {
    override fun interpolate(start: Float, end: Float, fraction: Float): Float =
        start + (end - start) * fraction
}

object XYType : Type<XY> {
    override fun interpolate(start: XY, end: XY, fraction: Float): XY = XY(
        x = FloatType.interpolate(start.x, end.x, fraction),
        y = FloatType.interpolate(start.y, end.y, fraction)
    )
}

object RectFType : Type<RectF> {
    override fun interpolate(start: RectF, end: RectF, fraction: Float): RectF = RectF(
        FloatType.interpolate(start.left, end.left, fraction),
        FloatType.interpolate(start.top, end.top, fraction),
        FloatType.interpolate(start.right, end.right, fraction),
        FloatType.interpolate(start.bottom, end.bottom, fraction)
    )
}

object PanType : Type<Pan> {
    override fun interpolate(start: Pan, end: Pan, fraction: Float): Pan =
        Pan(FloatType.interpolate(start.value, end.value, fraction))
}

object ColorType : Type<Color> {
    override fun interpolate(start: Color, end: Color, fraction: Float): Color = Color(
        red = FloatType.interpolate(start.red, end.red, fraction),
        green = FloatType.interpolate(start.green, end.green, fraction),
        blue = FloatType.interpolate(start.blue, end.blue, fraction),
        alpha = FloatType.interpolate(start.alpha, end.alpha, fraction)
    )
}

/** Discrete values hold the previous value until the next keyframe. */
class StepType<T> : Type<T> {
    override fun interpolate(start: T, end: T, fraction: Float): T = start
}

object BlendModeType : Type<BlendMode> by StepType()

/** Discrete interpolation for arbitrary enum values. */
class EnumType<E : Enum<E>> : Type<E> by StepType()

/**
 * Samples sorted keyframes in O(log n). Before/after the keyed range, the nearest endpoint is held.
 * For duplicate timestamps, the last keyframe at an exact timestamp wins. Callers that edit tracks
 * should keep their keyframe lists sorted; this method intentionally does not sort on the render path.
 */
object Interpolator {
    fun <T> valueAt(keys: List<Keyframe<T>>, nowMs: Long, type: Type<T>): T =
        valueAt(
            keys = keys,
            nowMs = nowMs,
            type = type,
            timeMsOf = { it.timeMs },
            valueOf = { it.value },
            easedFraction = { fraction, previous, _ -> previous.curve.apply(fraction) }
        )

    /**
     * Adapter for existing keyframe data classes. The selectors keep legacy models on the same
     * binary-search path without allocating converted keyframe lists for every rendered frame.
     * Set [exactMatchUsesPriorSegment] only for legacy formats whose incoming key owns the curve.
     */
    internal fun <K, T> valueAt(
        keys: List<K>,
        nowMs: Long,
        type: Type<T>,
        timeMsOf: (K) -> Long,
        valueOf: (K) -> T,
        easedFraction: (fraction: Float, previous: K, next: K) -> Float,
        exactMatchUsesPriorSegment: Boolean = false
    ): T {
        require(keys.isNotEmpty()) { "At least one keyframe is required to sample a value" }

        if (!exactMatchUsesPriorSegment) {
            // upper_bound(nowMs): the first item strictly after the playhead.
            var low = 0
            var high = keys.size
            while (low < high) {
                val middle = low + (high - low) / 2
                if (timeMsOf(keys[middle]) <= nowMs) low = middle + 1 else high = middle
            }

            val previousIndex = low - 1
            if (previousIndex < 0) return valueOf(keys.first())
            if (previousIndex >= keys.lastIndex) return valueOf(keys[previousIndex])
            return interpolateBetween(keys, previousIndex, nowMs, type, timeMsOf, valueOf, easedFraction)
        }

        // lower_bound(nowMs): legacy tracks use the incoming keyframe's curve at exact key times.
        if (nowMs <= timeMsOf(keys.first())) return valueOf(keys.first())
        if (nowMs >= timeMsOf(keys.last())) return valueOf(keys.last())
        var low = 0
        var high = keys.size
        while (low < high) {
            val middle = low + (high - low) / 2
            if (timeMsOf(keys[middle]) < nowMs) low = middle + 1 else high = middle
        }
        val nextIndex = low
        return interpolateBetween(keys, nextIndex - 1, nowMs, type, timeMsOf, valueOf, easedFraction)
    }

    private fun <K, T> interpolateBetween(
        keys: List<K>,
        previousIndex: Int,
        nowMs: Long,
        type: Type<T>,
        timeMsOf: (K) -> Long,
        valueOf: (K) -> T,
        easedFraction: (fraction: Float, previous: K, next: K) -> Float
    ): T {
        val previous = keys[previousIndex]
        val next = keys[previousIndex + 1]
        val previousTime = timeMsOf(previous)
        val nextTime = timeMsOf(next)
        val span = nextTime.toDouble() - previousTime.toDouble()
        if (span <= 0.0) return valueOf(previous)

        val rawFraction = ((nowMs.toDouble() - previousTime.toDouble()) / span).toFloat()
        return type.interpolate(valueOf(previous), valueOf(next), easedFraction(rawFraction, previous, next))
    }
}

private fun Curve.apply(fraction: Float): Float = when (this) {
    Curve.LINEAR -> fraction
    Curve.SMOOTH -> fraction * fraction * (3f - 2f * fraction)
    Curve.EASE_IN -> fraction * fraction
    Curve.EASE_OUT -> 1f - (1f - fraction) * (1f - fraction)
}
