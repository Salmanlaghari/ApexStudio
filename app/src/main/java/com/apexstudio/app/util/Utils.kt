package com.apexstudio.app.util

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object WaveformGenerator {
    fun generate(seed: Long, samples: Int, amplitude: Float = 1f): FloatArray {
        val rnd = Random(seed)
        val out = FloatArray(samples)
        val harmonics = listOf(0.18f, 0.10f, 0.06f, 0.04f)
        for (i in 0 until samples) {
            val t = i.toFloat() / samples
            val noise = (rnd.nextFloat() - 0.5f) * 0.6f
            val env = sinEnvelope(t)
            val sum = harmonics.mapIndexed { idx, a ->
                a * sin(2 * Math.PI * (2 + idx * 2.3) * t).toFloat()
            }.sum()
            out[i] = (env * (sum + noise) * amplitude).coerceIn(-1f, 1f)
        }
        return out
    }

    private fun sinEnvelope(t: Float): Float {
        val a = abs(sin(Math.PI * t).toFloat())
        return a
    }

    fun rms(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0f
        for (s in samples) sum += s * s
        return kotlin.math.sqrt(sum / samples.size)
    }
}

object TimeFormat {
    fun msToTimecode(ms: Long, includeFrames: Boolean = false, fps: Int = 60): String {
        val totalSec = ms / 1000.0
        val h = (totalSec / 3600).toInt()
        val m = ((totalSec % 3600) / 60).toInt()
        val s = (totalSec % 60).toInt()
        val frames = ((ms % 1000) / (1000.0 / fps)).toInt()
        val cs = ((ms % 1000) / 10).toInt()
        return if (includeFrames) {
            "%02d:%02d:%02d:%02d".format(h, m, s, frames)
        } else {
            "%02d:%02d:%02d.%02d".format(h, m, s, cs)
        }
    }

    fun msToShort(ms: Long): String {
        val m = (ms / 60_000).toInt()
        val s = ((ms % 60_000) / 1000).toInt()
        return "%02d:%02d".format(m, s)
    }

    fun msToExportTime(ms: Long): String {
        val m = (ms / 60_000).toInt()
        val s = ((ms % 60_000) / 1000).toInt()
        val cs = ((ms % 1000) / 10).toInt()
        return "%02d:%02d:%02d".format(m, s, cs)
    }

    fun formatMs(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val m = totalSec / 60
        val s = totalSec % 60
        // Reference image shows MM:SS.cs (centiseconds, zero-padded).
        // Was previously .t (tenths, no pad) which produced "00:00.8".
        val cs = ((ms.coerceAtLeast(0) % 1000) / 10).toInt()
        return "%02d:%02d.%02d".format(m, s, cs)
    }

    /**
     * Parses user input timestamps into milliseconds.
     * Supports seconds ("4.5", "12"), MM:SS ("01:23"), MM:SS.ss ("00:04.50"), or HH:MM:SS.
     */
    fun parseTimeToMs(input: String): Long? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        trimmed.toDoubleOrNull()?.let { sec ->
            return (sec * 1000.0).toLong()
        }

        val parts = trimmed.split(":")
        try {
            when (parts.size) {
                1 -> return (parts[0].toDouble() * 1000.0).toLong()
                2 -> {
                    val m = parts[0].toLong()
                    val sDouble = parts[1].toDouble()
                    return (m * 60_000L) + (sDouble * 1000.0).toLong()
                }
                3 -> {
                    val p0 = parts[0].toLong()
                    val p1 = parts[1].toLong()
                    val p2Double = parts[2].toDouble()
                    return (p0 * 3600_000L) + (p1 * 60_000L) + (p2Double * 1000.0).toLong()
                }
                4 -> {
                    val h = parts[0].toLong()
                    val m = parts[1].toLong()
                    val s = parts[2].toLong()
                    val f = parts[3].toLong()
                    return (h * 3600_000L) + (m * 60_000L) + (s * 1000L) + (f * 1000L / 60L)
                }
            }
        } catch (ignored: Exception) {}
        return null
    }
}

object Fps {
    const val DEFAULT = 60
    const val MIN = 24
    const val MAX = 60
    const val STEP = 5
}
