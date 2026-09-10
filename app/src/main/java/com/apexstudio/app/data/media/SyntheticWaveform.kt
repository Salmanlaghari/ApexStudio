package com.apexstudio.app.data.media

import kotlin.math.sin

/**
 * Deterministic pseudo-waveform generator. Used as a fallback
 * for clips where we can't decode raw PCM (most Android
 * MediaMetadataRetriever builds), so the timeline still has a
 * stable, recognisable shape instead of an empty white row.
 */
object SyntheticWaveform {
    fun generate(seed: Long, samples: Int): FloatArray {
        if (samples <= 0) return FloatArray(0)
        val out = FloatArray(samples)
        // Mix a few sinusoids at irrational frequencies so the
        // resulting envelope has visible peaks AND valleys — a
        // single sinusoid looks too uniform to read as a real
        // audio waveform.
        val rng = java.util.Random(seed)
        val a1 = 0.35f + rng.nextFloat() * 0.25f
        val a2 = 0.20f + rng.nextFloat() * 0.20f
        val a3 = 0.10f + rng.nextFloat() * 0.10f
        val f1 = 2f + rng.nextFloat() * 4f
        val f2 = 7f + rng.nextFloat() * 6f
        val f3 = 13f + rng.nextFloat() * 8f
        val phase = rng.nextFloat() * 6.28f
        for (i in 0 until samples) {
            val t = i.toFloat() / samples
            val v = a1 * sin(t * f1 * Math.PI.toFloat() + phase) +
                    a2 * sin(t * f2 * Math.PI.toFloat()) +
                    a3 * sin(t * f3 * Math.PI.toFloat())
            out[i] = (0.5f + 0.5f * v).coerceIn(0f, 1f)
        }
        return out
    }
}
