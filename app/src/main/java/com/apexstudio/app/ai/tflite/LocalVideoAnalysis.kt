package com.apexstudio.app.ai.tflite

import com.apexstudio.app.ai.AiCancellationToken
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** Uniform low-resolution luma sample for offline scene analysis; luma values are normalized 0..1. */
data class LumaFrame(val timestampMs: Long, val width: Int, val height: Int, val luma: FloatArray, val audioRms: Float = 0f) {
    init {
        require(timestampMs >= 0 && width > 0 && height > 0 && luma.size == width * height)
        require(audioRms.isFinite() && audioRms >= 0f)
    }
}

data class SceneBoundary(val timestampMs: Long, val changeScore: Float)
data class Highlight(val startMs: Long, val endMs: Long, val score: Float, val reason: String)
data class SceneAnalysis(val boundaries: List<SceneBoundary>, val highlights: List<Highlight>)

/**
 * Actual deterministic video-signal analysis, not an ML classifier: luma histogram intersection,
 * frame difference, sharpness proxy, clipping/exposure, and optional supplied audio RMS.
 * The caller samples decoded frames; this class never accesses the network or fabricates model output.
 */
object LocalVideoAnalysis {
    fun analyze(
        frames: List<LumaFrame>,
        histogramDistanceThreshold: Float = 0.34f,
        minimumCutGapMs: Long = 500,
        highlightWindowMs: Long = 3_000,
        cancellation: AiCancellationToken = AiCancellationToken()
    ): SceneAnalysis {
        require(histogramDistanceThreshold in 0f..1f && minimumCutGapMs >= 0 && highlightWindowMs > 0)
        if (frames.isEmpty()) return SceneAnalysis(emptyList(), emptyList())
        require(frames.zipWithNext().all { (a, b) -> b.timestampMs >= a.timestampMs && a.width == b.width && a.height == b.height })
        val cuts = mutableListOf<SceneBoundary>()
        val diffs = FloatArray(frames.size)
        for (i in 1 until frames.size) {
            cancellation.throwIfCancelled()
            val d = frameChange(frames[i - 1].luma, frames[i].luma)
            diffs[i] = d
            if (d >= histogramDistanceThreshold &&
                (cuts.lastOrNull()?.let { frames[i].timestampMs - it.timestampMs >= minimumCutGapMs } ?: true)) {
                cuts += SceneBoundary(frames[i].timestampMs, d)
            }
        }
        val windowFrames = max(1, frames.indexOfFirst { it.timestampMs - frames.first().timestampMs >= highlightWindowMs }.let { if (it < 0) frames.size else it })
        val candidates = mutableListOf<Highlight>()
        var start = 0
        while (start < frames.size) {
            cancellation.throwIfCancelled()
            val end = minOf(frames.size, start + windowFrames)
            val slice = frames.subList(start, end)
            val meanMotion = (start + 1 until end).map { diffs[it] }.average().toFloat().takeIf { it.isFinite() } ?: 0f
            val meanSharp = slice.map(::sharpness).average().toFloat()
            val meanExposure = slice.map { exposureQuality(it.luma) }.average().toFloat()
            val meanAudio = slice.map { it.audioRms.coerceIn(0f, 1f) }.average().toFloat()
            // Components have bounded scales. Avoid a hard-coded preference for motion-only clips.
            val score = (0.30f * meanMotion.coerceIn(0f, 1f) +
                0.25f * meanSharp.coerceIn(0f, 1f) + 0.20f * meanExposure + 0.25f * meanAudio).coerceIn(0f, 1f)
            val reason = listOfNotNull(
                if (meanMotion > 0.08f) "visual activity" else null,
                if (meanSharp > 0.12f) "frame detail" else null,
                if (meanAudio > 0.08f) "audio energy" else null
            ).joinToString(", ").ifEmpty { "balanced exposure" }
            candidates += Highlight(slice.first().timestampMs,
                (slice.last().timestampMs + frameDurationEstimate(slice)).coerceAtLeast(slice.first().timestampMs + 1), score, reason)
            start = end
        }
        return SceneAnalysis(cuts, candidates.sortedByDescending { it.score })
    }

    /** Normalized histogram total-variation distance (0 identical, 1 maximally different), blended with sampled pixel difference. */
    fun frameChange(a: FloatArray, b: FloatArray, bins: Int = 16): Float {
        require(a.isNotEmpty() && a.size == b.size && bins >= 2)
        val ha = IntArray(bins); val hb = IntArray(bins)
        var pixel = 0.0
        for (i in a.indices) {
            val av = a[i].coerceIn(0f, 1f); val bv = b[i].coerceIn(0f, 1f)
            ha[(av * bins).toInt().coerceAtMost(bins - 1)]++
            hb[(bv * bins).toInt().coerceAtMost(bins - 1)]++
            pixel += abs(av - bv)
        }
        val hist = ha.indices.sumOf { abs(ha[it] - hb[it]).toDouble() } / (2.0 * a.size)
        val meanPixel = pixel / a.size
        return (0.65 * hist + 0.35 * meanPixel).toFloat().coerceIn(0f, 1f)
    }

    private fun sharpness(frame: LumaFrame): Float {
        if (frame.width < 3 || frame.height < 3) return 0f
        var total = 0.0; var n = 0
        for (y in 1 until frame.height - 1) for (x in 1 until frame.width - 1) {
            val i = y * frame.width + x
            val lap = 4 * frame.luma[i] - frame.luma[i - 1] - frame.luma[i + 1] - frame.luma[i - frame.width] - frame.luma[i + frame.width]
            total += lap * lap; n++
        }
        return (sqrt(total / max(1, n)) * 3.0).toFloat().coerceIn(0f, 1f)
    }

    private fun exposureQuality(pixels: FloatArray): Float {
        if (pixels.isEmpty()) return 0f
        val mean = pixels.average().toFloat()
        val clipped = pixels.count { it < 0.02f || it > 0.98f }.toFloat() / pixels.size
        val midtone = (1f - abs(mean - 0.5f) * 1.6f).coerceIn(0f, 1f)
        return (midtone * (1f - clipped)).coerceIn(0f, 1f)
    }

    private fun frameDurationEstimate(frames: List<LumaFrame>): Long =
        if (frames.size > 1) (frames.last().timestampMs - frames.first().timestampMs) / (frames.size - 1).coerceAtLeast(1) else 1L
}

data class RgbFrameStats(val meanRed: Float, val meanGreen: Float, val meanBlue: Float, val contrast: Float, val saturation: Float)
data class StyleSuggestion(val label: String, val heuristicStrength: Float, val explanation: String)

/** Deterministic descriptive color analysis. These are rule-based suggestions, not generated styles. */
object LocalStyleAnalysis {
    fun analyze(rgb: IntArray): Pair<RgbFrameStats, StyleSuggestion> {
        require(rgb.isNotEmpty())
        var r = 0.0; var g = 0.0; var b = 0.0; var lum = 0.0; var sat = 0.0
        for (c in rgb) {
            val cr = ((c ushr 16) and 255) / 255.0
            val cg = ((c ushr 8) and 255) / 255.0
            val cb = (c and 255) / 255.0
            r += cr; g += cg; b += cb
            val mx = max(cr, max(cg, cb)); val mn = minOf(cr, cg, cb)
            lum += 0.2126 * cr + 0.7152 * cg + 0.0722 * cb
            sat += if (mx == 0.0) 0.0 else (mx - mn) / mx
        }
        val n = rgb.size.toDouble()
        val meanL = lum / n
        val variance = rgb.sumOf { c ->
            val y = (0.2126 * ((c ushr 16) and 255) + 0.7152 * ((c ushr 8) and 255) + 0.0722 * (c and 255)) / 255.0
            (y - meanL) * (y - meanL)
        } / n
        val stats = RgbFrameStats((r / n).toFloat(), (g / n).toFloat(), (b / n).toFloat(), sqrt(variance).toFloat(), (sat / n).toFloat())
        val label = when {
            meanL < 0.32 -> "low-key"
            meanL > 0.72 && stats.contrast < 0.20 -> "bright-soft"
            stats.saturation > 0.48 -> "vivid"
            stats.contrast > 0.28 -> "high-contrast"
            else -> "natural"
        }
        val confidence = when (label) {
            "low-key" -> ((0.32 - meanL) / 0.32).coerceIn(0.25, 0.95)
            "bright-soft" -> ((meanL - 0.72) + (0.20 - stats.contrast)).coerceIn(0.25, 0.85)
            "vivid" -> ((stats.saturation - 0.48) / 0.52).coerceIn(0.25, 0.95)
            "high-contrast" -> ((stats.contrast - 0.28) / 0.72).coerceIn(0.25, 0.95)
            else -> 0.35f
        }.toFloat()
        return stats to StyleSuggestion(label, confidence, "Rule-based summary of sampled RGB brightness, contrast and saturation; no learned style model was run.")
    }
}
