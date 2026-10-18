package com.apexstudio.app.domain.model

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Pure math for variable speed curves (PR-E). No Android dependencies,
 * so the interpolation / duration logic is unit-testable and shared by
 * the preview, the timeline and the export.
 *
 * Model: a curve is a list of [SpeedPoint]s, timeFraction in 0..1
 * across the TRIMMED clip span, speed in [MIN_SPEED, MAX_SPEED].
 * Between points the speed is interpolated with monotone cubic
 * Hermite (PCHIP / Fritsch–Carlson). Monotone matters: unlike
 * Catmull-Rom / bezier, PCHIP never overshoots, so the interpolated
 * speed always stays inside the neighbouring points' range — no
 * negative speeds, no spikes, no sudden jumps unless the user
 * places two points very close together.
 *
 * Time mapping: output time (what the timeline shows) relates to
 * source time by
 *   output(t) = ∫₀ᵗ 1/speed(u) du
 * so a 0.5x stretch plays twice as long. [effectiveDurationMs],
 * [sourceToOutputMs] and [outputToSourceMs] are all computed from
 * that integral numerically (256 samples + binary search for the
 * inverse). Good to well under a frame of error for any sane curve.
 */
object SpeedCurveMath {

    const val MIN_SPEED = 0.1f
    const val MAX_SPEED = 10f

    /** Speed range of the curve editor graph (0.1x..10x, log scale — matches the CapCut velocity editor). */
    const val EDITOR_MIN_SPEED = 0.1f
    const val EDITOR_MAX_SPEED = 10f

    /** Gridlines drawn on the editor graph (log-spaced). */
    val GRID_SPEEDS = listOf(10f, 4f, 2f, 1f, 0.5f, 0.25f, 0.1f)

    /** log₂(10) — the graph's Y axis spans ±LOG2_10 so 1x sits dead centre. */
    private val LOG2_10 = (ln(10.0) / ln(2.0)).toFloat()

    private const val SAMPLES = 256

    /**
     * Sanitise a user-edited point list into a valid curve:
     * sorted by fraction, fractions clamped to 0..1, speeds clamped to
     * [MIN_SPEED, MAX_SPEED], duplicate fractions merged (last wins),
     * and endpoints pinned to exactly 0 and 1. Returns emptyList() when
     * fewer than 2 usable points remain (caller treats that as "no curve").
     */
    fun sanitize(points: List<SpeedPoint>): List<SpeedPoint> {
        if (points.isEmpty()) return emptyList()
        val merged = linkedMapOf<Float, SpeedPoint>()
        for (p in points) {
            // Never propagate non-finite persisted data into playback parameters or timing math.
            if (!p.timeFraction.isFinite() || !p.speed.isFinite()) continue
            val f = p.timeFraction.coerceIn(0f, 1f)
            val s = p.speed.coerceIn(MIN_SPEED, MAX_SPEED)
            // Last wins on duplicate fractions, keeping the sharp flag.
            merged[f] = p.copy(timeFraction = f, speed = s)
        }
        if (merged.size < 2) return emptyList()
        val sorted = merged.values.sortedBy { it.timeFraction }
        val out = sorted.toMutableList()
        // Pin endpoints to exactly 0 / 1 (keeps the curve defined over
        // the whole clip even if the user dragged an endpoint inward).
        out[0] = out[0].copy(timeFraction = 0f)
        out[out.lastIndex] = out[out.lastIndex].copy(timeFraction = 1f)
        return out
    }

    /** Flat curve at [speed] (the editor's starting point for a clip with no curve). */
    fun flat(speed: Float): List<SpeedPoint> {
        val s = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        return listOf(SpeedPoint(0f, s), SpeedPoint(1f, s))
    }

    /**
     * Speed at [fraction] (0..1). Empty / single-point lists return 1f
     * (no curve). Endpoints clamp; interior uses PCHIP.
     */
    fun speedAt(points: List<SpeedPoint>, fraction: Float): Float {
        val pts = if (points.size >= 2) points else return 1f
        val f = fraction.coerceIn(0f, 1f)
        if (f <= pts.first().timeFraction) return pts.first().speed
        if (f >= pts.last().timeFraction) return pts.last().speed
        // Find the segment [x0, x1] containing f.
        var i = 0
        while (i < pts.lastIndex && f > pts[i + 1].timeFraction) i++
        val p0 = pts[i]
        val p1 = pts[i + 1]
        val h = p1.timeFraction - p0.timeFraction
        if (h <= 0f) return p1.speed
        val t = (f - p0.timeFraction) / h
        // Sharp segment (ends at a sharp point): straight linear
        // interpolation — a near-vertical drop/rise, no easing.
        if (p1.sharp) {
            return (p0.speed + t * (p1.speed - p0.speed)).coerceIn(MIN_SPEED, MAX_SPEED)
        }
        // Smooth segment: PCHIP (Fritsch–Carlson).
        val m = pchipSlopes(pts)
        val m0 = m[i]
        val m1 = m[i + 1]
        // Cubic Hermite basis.
        val t2 = t * t
        val t3 = t2 * t
        val h00 = 2f * t3 - 3f * t2 + 1f
        val h10 = t3 - 2f * t2 + t
        val h01 = -2f * t3 + 3f * t2
        val h11 = t3 - t2
        return (h00 * p0.speed + h10 * h * m0 + h01 * p1.speed + h11 * h * m1)
            .coerceIn(MIN_SPEED, MAX_SPEED)
    }

    /** Fritsch–Carlson monotone slopes for each control point. */
    private fun pchipSlopes(pts: List<SpeedPoint>): FloatArray {
        val n = pts.size
        val d = FloatArray(max(0, n - 1)) { i ->
            val dx = pts[i + 1].timeFraction - pts[i].timeFraction
            if (dx <= 0f) 0f else (pts[i + 1].speed - pts[i].speed) / dx
        }
        val m = FloatArray(n)
        if (n == 2) {
            m[0] = d[0]
            m[1] = d[0]
            return m
        }
        // Endpoints: one-sided, limited to avoid overshoot.
        m[0] = oneSidedSlope(d[0], d[1])
        m[n - 1] = oneSidedSlope(d[n - 2], d[n - 3])
        for (i in 1 until n - 1) {
            if (d[i - 1] * d[i] <= 0f) {
                m[i] = 0f // local extremum → flat tangent (monotone)
            } else {
                // Weighted harmonic mean of the adjacent secants.
                val w1 = 2f * (pts[i + 1].timeFraction - pts[i].timeFraction) +
                    (pts[i].timeFraction - pts[i - 1].timeFraction)
                val w2 = (pts[i + 1].timeFraction - pts[i].timeFraction) +
                    2f * (pts[i].timeFraction - pts[i - 1].timeFraction)
                m[i] = (w1 + w2) / (w1 / d[i - 1] + w2 / d[i])
            }
        }
        return m
    }

    private fun oneSidedSlope(d0: Float, d1: Float): Float {
        // Non-centered 3-point endpoint slope, limited to 3x the first
        // secant and zeroed when it would overshoot (Fritsch–Carlson).
        val m = ((2f * d0) + d1) / 3f
        return when {
            d0 * m <= 0f -> 0f
            abs(m) > 3f * abs(d0) -> 3f * d0
            else -> m
        }
    }

    /**
     * Output (timeline) duration of a clip whose SOURCE span is
     * [sourceDurationMs], under [points]. No curve → source span.
     * Computed as ∫₀¹ 1/speed(f) df × source span.
     */
    fun effectiveDurationMs(sourceDurationMs: Long, points: List<SpeedPoint>): Long {
        val span = sourceDurationMs.coerceAtLeast(0L)
        if (points.size < 2 || span == 0L) return span
        var acc = 0.0
        var prevF = 0.0
        var prevInv = 1.0 / speedAt(points, 0f).toDouble()
        for (i in 1..SAMPLES) {
            val f = i.toDouble() / SAMPLES
            val inv = 1.0 / speedAt(points, f.toFloat()).toDouble()
            acc += (f - prevF) * (prevInv + inv) / 2.0
            prevF = f
            prevInv = inv
        }
        return (acc * span).toLong().coerceAtLeast(1L)
    }

    /**
     * Output (timeline) time for a SOURCE time within the clip.
     * [sourceMs] is 0..[sourceDurationMs] (0 = first trimmed frame).
     */
    fun sourceToOutputMs(sourceMs: Long, sourceDurationMs: Long, points: List<SpeedPoint>): Long {
        val span = sourceDurationMs.coerceAtLeast(0L)
        if (points.size < 2 || span == 0L) return sourceMs.coerceIn(0L, span)
        val target = sourceMs.coerceIn(0L, span).toDouble() / span
        var acc = 0.0
        var prevF = 0.0
        var prevInv = 1.0 / speedAt(points, 0f).toDouble()
        for (i in 1..SAMPLES) {
            val f = i.toDouble() / SAMPLES
            if (f >= target) {
                // Partial trapezoid up to the exact target fraction.
                acc += (target - prevF) * (prevInv + 1.0 / speedAt(points, target.toFloat()).toDouble()) / 2.0
                break
            }
            val inv = 1.0 / speedAt(points, f.toFloat()).toDouble()
            acc += (f - prevF) * (prevInv + inv) / 2.0
            prevF = f
            prevInv = inv
        }
        return (acc * span).toLong().coerceIn(0L, effectiveDurationMs(span, points))
    }

    /**
     * SOURCE time for an OUTPUT (timeline) time within the clip — the
     * inverse of [sourceToOutputMs], found by binary search (the
     * integral is strictly increasing because speed > 0).
     */
    fun outputToSourceMs(outputMs: Long, sourceDurationMs: Long, points: List<SpeedPoint>): Long {
        val span = sourceDurationMs.coerceAtLeast(0L)
        if (points.size < 2 || span == 0L) return outputMs.coerceIn(0L, span)
        val outDur = effectiveDurationMs(span, points)
        val target = outputMs.coerceIn(0L, outDur)
        var lo = 0L
        var hi = span
        // 24 iterations → sub-ms precision on any sane clip length.
        repeat(24) {
            val mid = (lo + hi) / 2
            if (sourceToOutputMs(mid, span, points) < target) lo = mid else hi = mid
        }
        return lo.coerceIn(0L, span)
    }

    /**
     * Timeline start (ms) of [clip] within [clips]: for V1 clips
     * (trackIndex 0, VIDEO/IMAGE) the sum of the effective durations
     * of the preceding V1 clips; for overlay clips the explicit
     * [MediaClip.timelineOffsetMs]. Used by the preview seek/playhead
     * mapping so the playhead lands on the right frame of a
     * speed-ramped clip.
     */
    fun timelineStartMsForClip(clips: List<MediaClip>, clip: MediaClip): Long {
        if (clip.trackIndex != 0) return clip.timelineOffsetMs
        var acc = 0L
        for (c in clips) {
            if (c.id == clip.id) break
            if (c.trackIndex == 0 &&
                (c.type == ClipType.VIDEO || c.type == ClipType.IMAGE)
            ) {
                acc += c.effectiveDurationMs()
            }
        }
        return acc
    }

    // ---- Curve presets (one tap in the editor) ----

    enum class CurvePreset(val label: String, val points: List<SpeedPoint>) {
        SLOW_MO("Slow-mo", listOf(SpeedPoint(0f, 0.5f), SpeedPoint(1f, 0.5f))),
        FAST("Fast", listOf(SpeedPoint(0f, 2f), SpeedPoint(1f, 2f))),
        RAMP_IN(
            "Ramp In",
            listOf(
                SpeedPoint(0f, 2f),
                SpeedPoint(0.5f, 1.2f),
                SpeedPoint(1f, 0.5f)
            )
        ),
        RAMP_OUT(
            "Ramp Out",
            listOf(
                SpeedPoint(0f, 0.5f),
                SpeedPoint(0.5f, 1.2f),
                SpeedPoint(1f, 2f)
            )
        ),
        BULLET_TIME(
            "Bullet Time",
            listOf(
                SpeedPoint(0f, 2f),
                SpeedPoint(0.35f, 0.5f),
                SpeedPoint(0.5f, 0.25f),
                SpeedPoint(0.65f, 0.5f),
                SpeedPoint(1f, 2f)
            )
        ),
        /**
         * "Smooth slow-mo" (CapCut velocity-edit style): flat 1x, a
         * SHARP drop into a long flat 0.1x hold, then a sharp rise
         * back to 1x. The drop/rise endpoints are marked sharp so
         * those segments are straight lines, not eased curves.
         */
        SMOOTH_SLOW_MO(
            "Smooth slow-mo",
            listOf(
                SpeedPoint(0f, 1f),
                SpeedPoint(0.30f, 1f),
                SpeedPoint(0.35f, 0.1f, sharp = true),
                SpeedPoint(0.65f, 0.1f),
                SpeedPoint(0.70f, 1f, sharp = true),
                SpeedPoint(1f, 1f)
            )
        );

        companion object {
            fun byLabel(label: String): CurvePreset? = values().firstOrNull { it.label == label }
        }
    }

    /** Log-scale helpers for the editor graph (Y axis is log₂ speed, ±log₂(10), 1x centred). */
    fun speedToGraphY(speed: Float): Float {
        val log2 = (ln(speed.coerceIn(EDITOR_MIN_SPEED, EDITOR_MAX_SPEED).toDouble()) /
            ln(2.0)).toFloat()
        // Map log₂(speed) ∈ [-LOG2_10, +LOG2_10] → graph Y ∈ [1, 0] (top = fast).
        return 0.5f - log2 / (2f * LOG2_10)
    }

    fun graphYToSpeed(y: Float): Float {
        val log2 = (0.5f - y.coerceIn(0f, 1f)) * 2f * LOG2_10
        // 2^log2, computed via exp to avoid kotlin.math.pow Float overload churn.
        return kotlin.math.exp((log2 * ln(2.0).toFloat()).toDouble()).toFloat()
            .coerceIn(EDITOR_MIN_SPEED, EDITOR_MAX_SPEED)
    }

    /** Clamp so neighbouring points keep at least [minGap] fraction apart. */
    fun clampPointFraction(points: List<SpeedPoint>, index: Int, fraction: Float): Float {
        if (index <= 0) return 0f
        if (index >= points.lastIndex) return 1f
        val minGap = 0.02f
        val lo = points[index - 1].timeFraction + minGap
        val hi = points[index + 1].timeFraction - minGap
        return fraction.coerceIn(min(lo, hi), max(lo, hi))
    }
}

/**
 * Timeline/source mapping shared by overlay preview and video export.
 * Overlay speed curves use the same trimmed-source coordinate system as V1:
 * an empty curve is 1x, and a populated curve determines both timeline
 * duration and the source frame at each timeline timestamp.
 */
object OverlayTimeMapping {
    fun timelineDurationMs(
        sourceDurationMs: Long,
        speedPoints: List<SpeedPoint>
    ): Long = SpeedCurveMath.effectiveDurationMs(sourceDurationMs, SpeedCurveMath.sanitize(speedPoints))

    /** Source-relative offset for an elapsed overlay timeline timestamp. */
    fun sourceOffsetMs(
        outputMs: Long,
        sourceDurationMs: Long,
        speedPoints: List<SpeedPoint>
    ): Long = SpeedCurveMath.outputToSourceMs(
        outputMs, sourceDurationMs, SpeedCurveMath.sanitize(speedPoints)
    )

    /** Instantaneous playback rate at an elapsed overlay timeline timestamp. */
    fun speedAtOutputMs(
        outputMs: Long,
        sourceDurationMs: Long,
        speedPoints: List<SpeedPoint>
    ): Float {
        val points = SpeedCurveMath.sanitize(speedPoints)
        if (points.size < 2 || sourceDurationMs <= 0L) return 1f
        val sourceMs = SpeedCurveMath.outputToSourceMs(outputMs, sourceDurationMs, points)
        return SpeedCurveMath.speedAt(points, sourceMs.toFloat() / sourceDurationMs.toFloat())
    }
}
