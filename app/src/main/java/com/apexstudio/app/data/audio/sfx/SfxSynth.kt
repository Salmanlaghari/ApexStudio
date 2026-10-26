package com.apexstudio.app.data.audio.sfx

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Deterministic mono PCM synthesizer. It has no asset or network dependencies. */
object SfxSynth {
    const val SAMPLE_RATE = 44_100
    const val MAX_DURATION_MS = 30_000L
    const val WAV_HEADER_BYTES = 44
    /** -1 dBFS expressed as a linear amplitude ceiling. */
    const val PEAK_CEILING = 0.89125094f

    /** Render [effectId] to signed 16-bit PCM at 44.1 kHz. */
    fun render(
        effectId: String,
        durationMs: Long,
        intensity: Float = SfxLibrary.DEFAULT_INTENSITY
    ): ShortArray {
        val effect = SfxLibrary.find(effectId)
            ?: throw IllegalArgumentException("Unknown sound effect: $effectId")
        require(durationMs in 1L..MAX_DURATION_MS) { "Duration must be between 1 and $MAX_DURATION_MS ms" }
        require(intensity.isFinite()) { "Intensity must be finite" }

        val safeIntensity = intensity.coerceIn(0f, 1f)
        val frameCount = ((durationMs.toDouble() * SAMPLE_RATE) / 1_000.0).roundToInt().coerceAtLeast(1)
        val noise = java.util.Random(
            effect.id.hashCode().toLong() xor durationMs xor safeIntensity.toBits().toLong()
        )
        val raw = FloatArray(frameCount)
        var phaseA = 0.0
        var phaseB = 0.0
        var previousNoise = 0f
        var highPassState = 0f
        var lowPassState = 0f

        for (frame in 0 until frameCount) {
            val progress = frame.toFloat() / frameCount.toFloat()
            val timeSeconds = frame.toDouble() / SAMPLE_RATE.toDouble()
            val white = noise.nextFloat() * 2f - 1f
            val value = when (effect.id) {
                "whoosh" -> {
                    val sweptNoise = bandPassedNoise(
                        white, progress, 280f, 8_500f, noise,
                        previousNoise, highPassState, lowPassState
                    )
                    val envelope = raisedCosine(progress) * (0.25f + 0.75f * progress)
                    previousNoise = white
                    highPassState = sweptNoise.first
                    lowPassState = sweptNoise.second
                    sweptNoise.third * envelope
                }
                "fast_swoosh" -> {
                    val sweptNoise = bandPassedNoise(
                        white, progress, 1_100f, 11_000f, noise,
                        previousNoise, highPassState, lowPassState
                    )
                    val envelope = raisedCosine(progress) * exp(-progress * 0.55f)
                    previousNoise = white
                    highPassState = sweptNoise.first
                    lowPassState = sweptNoise.second
                    sweptNoise.third * envelope
                }
                "riser" -> {
                    val frequency = 200.0 + 1_000.0 * progress
                    phaseA += 2.0 * PI * frequency / SAMPLE_RATE
                    phaseB += 2.0 * PI * (frequency * 0.5) / SAMPLE_RATE
                    val fadeIn = (progress / 0.22f).coerceIn(0f, 1f)
                    val tail = (1f - progress * 0.18f).coerceAtLeast(0f)
                    ((saw(phaseA) * 0.58f) + (sin(phaseB).toFloat() * 0.42f)) * fadeIn * tail
                }
                "downer" -> {
                    val frequency = 1_200.0 - 1_000.0 * progress
                    phaseA += 2.0 * PI * frequency / SAMPLE_RATE
                    phaseB += 2.0 * PI * (frequency * 0.5) / SAMPLE_RATE
                    val envelope = raisedCosine(progress) * (1f - progress * 0.22f)
                    ((sin(phaseA).toFloat() * 0.72f) + (sin(phaseB).toFloat() * 0.28f)) * envelope
                }
                "click" -> {
                    val tick = if (timeSeconds < 0.002) white else 0f
                    tick * exp(-progress * 32f)
                }
                "pop" -> {
                    val frequency = 780.0 - 480.0 * progress
                    phaseA += 2.0 * PI * frequency / SAMPLE_RATE
                    val envelope = exp(-progress * 7.5f) * raisedCosine(progress)
                    sin(phaseA).toFloat() * envelope
                }
                "tap" -> {
                    val highPassed = white - previousNoise + 0.92f * highPassState
                    val alpha = onePoleAlpha(2_500f, SAMPLE_RATE)
                    lowPassState += alpha * (highPassed - lowPassState)
                    previousNoise = white
                    highPassState = highPassed
                    lowPassState * exp(-progress * 22f) * (if (timeSeconds < 0.005) 1f else 0.7f)
                }
                "toggle" -> {
                    val frequency = if (progress < 0.48f) 620.0 else 930.0
                    phaseA += 2.0 * PI * frequency / SAMPLE_RATE
                    val envelope = min(progress / 0.04f, (1f - progress) / 0.12f).coerceIn(0f, 1f)
                    sin(phaseA).toFloat() * envelope
                }
                "camera_shutter" -> {
                    val first = gaussian(progress, 0.045f, 0.017f)
                    val second = gaussian(progress, 0.42f, 0.024f)
                    (first * 0.95f + second * 0.72f) * white
                }
                "ding" -> {
                    val frequency = 880.0
                    phaseA += 2.0 * PI * frequency / SAMPLE_RATE
                    phaseB += 2.0 * PI * (frequency * 2.01) / SAMPLE_RATE
                    val partial = sin(phaseA).toFloat() * 0.78f + sin(phaseB).toFloat() * 0.22f
                    partial * exp(-progress * 4.2f)
                }
                "sparkle" -> {
                    val note = when {
                        progress < 0.34f -> 1_046.5
                        progress < 0.67f -> 1_318.5
                        else -> 1_569.0
                    }
                    phaseA += 2.0 * PI * note / SAMPLE_RATE
                    phaseB += 2.0 * PI * (note * 2.7) / SAMPLE_RATE
                    val noteStart = when {
                        progress < 0.34f -> 0f
                        progress < 0.67f -> 0.34f
                        else -> 0.67f
                    }
                    val decay = exp(-(progress - noteStart) * 7f)
                    (sin(phaseA).toFloat() * 0.76f + sin(phaseB).toFloat() * 0.24f) * decay
                }
                "success_chime" -> {
                    val first = progress < 0.5f
                    val frequency = if (first) 523.25 else 659.25
                    phaseA += 2.0 * PI * frequency / SAMPLE_RATE
                    val localProgress = if (first) progress / 0.5f else (progress - 0.5f) / 0.5f
                    val envelope = exp(-localProgress * 3.2f) * raisedCosine(progress)
                    sin(phaseA).toFloat() * envelope
                }
                "cinematic_hit" -> {
                    val frequency = 72.0 - 24.0 * progress
                    phaseA += 2.0 * PI * frequency / SAMPLE_RATE
                    val alpha = onePoleAlpha(1_400f, SAMPLE_RATE)
                    lowPassState += alpha * (white - lowPassState)
                    previousNoise = white
                    val thump = sin(phaseA).toFloat() * exp(-progress * 7f)
                    val noiseSwell = lowPassState * gaussian(progress, 0.10f, 0.10f) * 0.75f
                    thump * 0.75f + noiseSwell
                }
                "error_buzz" -> {
                    phaseA += 2.0 * PI * 140.0 / SAMPLE_RATE
                    val square = if (sin(phaseA) >= 0.0) 1f else -1f
                    square * exp(-progress * 5f) * 0.35f
                }
                else -> error("Synthesis recipe missing for ${effect.id}")
            }
            raw[frame] = value.takeIf(Float::isFinite) ?: 0f
        }

        val peak = raw.maxOfOrNull { abs(it) } ?: 0f
        if (peak <= 0f || safeIntensity == 0f) return ShortArray(frameCount)
        val recipeLevel = if (effect.id == "error_buzz") 0.34f else 1f
        val gain = (PEAK_CEILING / peak) * safeIntensity * recipeLevel
        return ShortArray(frameCount) { index ->
            val sample = (raw[index] * gain).coerceIn(-PEAK_CEILING, PEAK_CEILING)
            (sample * Short.MAX_VALUE).roundToInt().toShort()
        }
    }

    private fun bandPassedNoise(
        input: Float,
        progress: Float,
        startCutoffHz: Float,
        endCutoffHz: Float,
        random: java.util.Random,
        previousInput: Float,
        previousHighPass: Float,
        previousLowPass: Float
    ): Triple<Float, Float, Float> {
        @Suppress("UNUSED_PARAMETER")
        val unused = random
        val highPassed = input - previousInput + 0.94f * previousHighPass
        val cutoff = startCutoffHz + (endCutoffHz - startCutoffHz) * progress
        val alpha = onePoleAlpha(cutoff, SAMPLE_RATE)
        val lowPassed = previousLowPass + alpha * (highPassed - previousLowPass)
        return Triple(highPassed, lowPassed, lowPassed)
    }

    private fun onePoleAlpha(cutoffHz: Float, sampleRate: Int): Float =
        (1.0 - exp(-2.0 * PI * cutoffHz.coerceIn(20f, sampleRate * 0.45f) / sampleRate)).toFloat()

    private fun raisedCosine(progress: Float): Float {
        val bounded = progress.coerceIn(0f, 1f)
        return (0.5 - 0.5 * kotlin.math.cos(2.0 * PI * bounded)).toFloat()
    }

    private fun gaussian(value: Float, center: Float, width: Float): Float {
        val scaled = (value - center) / width
        return exp(-0.5f * scaled * scaled)
    }

    private fun saw(phase: Double): Float {
        val cycle = (phase / (2.0 * PI)) % 1.0
        return ((2.0 * cycle) - 1.0).toFloat()
    }
}
