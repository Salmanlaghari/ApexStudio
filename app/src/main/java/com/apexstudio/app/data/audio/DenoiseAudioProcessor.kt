package com.apexstudio.app.data.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import com.apexstudio.app.domain.model.DenoiseStrength
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Media3 [AudioProcessor] that runs voice noise cleanup on a clip's audio.
 *
 * The SAME processor class is used by BOTH paths, which is what makes
 * preview == export by construction:
 * - Preview: the editor's ExoPlayer is built with a
 *   [DenoiseRenderersFactory], whose audio sink runs one SHARED
 *   instance of this processor. Toggling Clean Voice just flips that
 *   instance's [denoiseSettings] (see below) —
 *   no player rebuild, no seek. (Media3 1.5.0 has no
 *   `Player.setAudioEffects`; the sink is the only supported hook.)
 * - Export: `Effects(buildExportAudioProcessors(...))` in ExportEngine
 *   (Transformer), where a fresh instance of this processor sits first
 *   in the chain so it sees the original audio before pitch / volume /
 *   speed effects.
 *
 * How it works: input PCM is de-interleaved, the channels are mixed to
 * mono for *analysis* only, the mono mix runs through a
 * [VoiceNoiseSuppressor] ([SpectralDenoiser] — classical spectral
 * gating, NOT RNNoise; see its KDoc for the honest reasoning), and the
 * resulting per-sample gain ratio (processed / original mono) is applied
 * identically to every channel. Sharing one gain across channels keeps
 * the stereo image intact; per-channel gains would make it wobble.
 *
 * Sample-count parity: the suppressor guarantees total output samples
 * == total input samples (its [VoiceNoiseSuppressor.flush] tops up the
 * tail), and this processor converts frames 1:1, so the processed audio
 * never drifts against video.
 *
 * Safety: unsupported encodings (anything but PCM_16BIT / PCM_FLOAT)
 * fall back to bit-exact passthrough instead of failing playback or
 * export. Non-finite samples are clamped; the gain ratio is clamped to
 * [-2, 2] so the processor can never amplify wildly. Runs on the
 * Media3 audio / transformer thread — never the main thread.
 *
 * Runtime toggle / strength (preview path): the editor mutates these
 * on the shared instance while the player is running. They are read on
 * the audio thread in [queueInput], so they take effect on the next
 * queued buffer with no reconfiguration. When [DenoiseSettings.enabled]
 * is false, [queueInput] is a cheap bit-exact passthrough (no FFT) —
 * the processor stays in the chain, it just stops processing. Export
 * creates a fresh instance per export with the clip's settings and
 * never touches these fields.
 *
 * Lifecycle notes (Media3 calls these): [configure] resets everything
 * (new stream / new clip); [flush] clears only the overlap-add buffers
 * on seek and keeps the learned noise profile (same clip, same room).
 */

/**
 * Runtime Clean Voice settings for the shared preview processor.
 * Held in ONE `@Volatile` var ([DenoiseAudioProcessor.denoiseSettings])
 * so the audio thread always sees a consistent (enabled, strength)
 * pair — two independently-volatile fields could expose a transient
 * mix of old and new values.
 */
data class DenoiseSettings(
    val enabled: Boolean,
    val strength: DenoiseStrength,
)

class DenoiseAudioProcessor(
    strength: DenoiseStrength = DenoiseStrength.MEDIUM,
    enabled: Boolean = true,
) : AudioProcessor {

    /** Runtime Clean Voice settings for the shared preview instance. */
    @Volatile var denoiseSettings: DenoiseSettings = DenoiseSettings(enabled, strength)

    companion object {
        private val EMPTY_BUFFER: ByteBuffer =
            ByteBuffer.allocateDirect(0).order(ByteOrder.LITTLE_ENDIAN)
        private const val RATIO_EPSILON = 1e-4f
        private const val RATIO_CLAMP = 2f
        private const val RATIO_SMOOTH = 0.3f
    }

    private val denoiser = SpectralDenoiser()
    // Strength last applied to the denoiser (setStrength is called
    // only when it changes, not on every queueInput).
    private var appliedStrength: DenoiseStrength? = null

    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private var passthrough = false

    // Queues keep fed-but-unmatched samples: the denoiser emits a prefix
    // of the fed stream, so the head of each queue is exactly the input
    // that produced the denoiser's latest output.
    private val monoInQueue = ArrayDeque<Float>()
    private val rawInQueue = ArrayDeque<Float>()

    private val outputQueue = ArrayDeque<ByteBuffer>()
    private var inputEnded = false
    private var prevRatio = 1f

    override fun isActive(): Boolean = true

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding
        passthrough = encoding != C.ENCODING_PCM_16BIT &&
            encoding != C.ENCODING_PCM_FLOAT
        denoiser.setSampleRate(sampleRate)
        denoiser.reset()
        monoInQueue.clear()
        rawInQueue.clear()
        outputQueue.clear()
        inputEnded = false
        prevRatio = 1f
        // Output format == input format: same rate, channels, encoding.
        return inputAudioFormat
    }

    override fun isEnded(): Boolean = inputEnded && outputQueue.isEmpty()

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        // One atomic snapshot: enabled + strength always consistent.
        val settings = denoiseSettings
        if (passthrough || channelCount <= 0 || !settings.enabled) {
            val copy = ByteBuffer.allocateDirect(inputBuffer.remaining())
                .order(ByteOrder.LITTLE_ENDIAN)
            copy.put(inputBuffer)
            copy.flip()
            outputQueue.add(copy)
            return
        }
        // Strength is applied here (audio thread) rather than in a
        // setter so all denoiser state stays on one thread, and only
        // when it actually changed.
        if (settings.strength != appliedStrength) {
            denoiser.setStrength(settings.strength)
            appliedStrength = settings.strength
        }
        val floats = decode(inputBuffer)
        if (floats.isEmpty()) return
        val frames = floats.size / channelCount
        // Mono mix for analysis + raw queue for gain application. The
        // denoiser emits a prefix of the fed stream, so fed samples stay
        // queued until the denoiser's output catches up with them —
        // removing them early would misalign the gain ratio.
        val monoNew = FloatArray(frames)
        for (f in 0 until frames) {
            var acc = 0f
            for (c in 0 until channelCount) {
                val s = floats[f * channelCount + c]
                rawInQueue.add(s)
                acc += s
            }
            monoNew[f] = acc / channelCount
            monoInQueue.add(acc / channelCount)
        }
        val monoOut = denoiser.process(monoNew)
        val monoIn = FloatArray(monoOut.size) { monoInQueue.removeFirst() }
        emitProcessed(monoIn, monoOut)
    }

    override fun queueEndOfStream() {
        if (passthrough) {
            inputEnded = true
            return
        }
        // Flush the denoiser tail; the mono queue holds exactly the
        // unmatched remainder, which is what the tail corresponds to.
        val monoOut = denoiser.flush()
        val monoIn = FloatArray(monoInQueue.size) { monoInQueue.removeFirst() }
        // monoOut must equal monoIn in length (sample-count parity);
        // if a bug ever breaks that, pad/truncate defensively.
        val aligned = when {
            monoOut.size == monoIn.size -> monoOut
            monoOut.size < monoIn.size -> monoOut + FloatArray(monoIn.size - monoOut.size)
            else -> monoOut.copyOf(monoIn.size)
        }
        emitProcessed(monoIn, aligned)
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        return outputQueue.removeFirstOrNull() ?: EMPTY_BUFFER
    }

    override fun flush() {
        outputQueue.clear()
        monoInQueue.clear()
        rawInQueue.clear()
        denoiser.resetBuffers()
        inputEnded = false
        prevRatio = 1f
    }

    override fun reset() {
        flush()
        denoiser.reset()
        sampleRate = 0
        channelCount = 0
        encoding = C.ENCODING_INVALID
        passthrough = false
    }

    // ---- internals ----

    /** Decodes the input buffer to interleaved float samples in [-1, 1]. */
    private fun decode(inputBuffer: ByteBuffer): FloatArray {
        val src = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        // Media3 expects the processor to consume the input buffer.
        inputBuffer.position(inputBuffer.limit())
        return when (encoding) {
            C.ENCODING_PCM_16BIT -> {
                val count = src.remaining() / 2
                FloatArray(count) { src.short / 32768f }
            }
            C.ENCODING_PCM_FLOAT -> {
                val count = src.remaining() / 4
                FloatArray(count) { src.float.coerceIn(-1f, 1f) }
            }
            else -> FloatArray(0)
        }
    }

    /**
     * Applies the denoiser's per-sample gain ratio to the matching raw
     * frames and enqueues the encoded output. [monoIn] is the input
     * that produced [monoOut] (same length, time-aligned).
     */
    private fun emitProcessed(monoIn: FloatArray, monoOut: FloatArray) {
        val frames = minOf(monoIn.size, monoOut.size)
        val outFloats = FloatArray(frames * channelCount)
        for (f in 0 until frames) {
            val inMono = monoIn[f]
            val absIn = kotlin.math.abs(inMono)
            // Smooth gate: the ratio blends to 1 as the input
            // approaches silence, so there is no discontinuity at
            // RATIO_EPSILON (previously the ratio jumped from 1 to a
            // clamped value the moment |inMono| crossed the threshold).
            val gate = (absIn * absIn) / (absIn * absIn + RATIO_EPSILON * RATIO_EPSILON)
            val rawRatio = if (absIn > 0f) {
                (monoOut[f] / inMono).coerceIn(-RATIO_CLAMP, RATIO_CLAMP)
            } else {
                1f
            }
            val gatedRatio = 1f + (rawRatio - 1f) * gate
            // Smooth the ratio to avoid zipper noise on transients.
            prevRatio = (1f - RATIO_SMOOTH) * prevRatio + RATIO_SMOOTH * gatedRatio
            val ratio = if (prevRatio.isFinite()) prevRatio else 1f
            for (c in 0 until channelCount) {
                val raw = rawInQueue.removeFirst()
                outFloats[f * channelCount + c] = (raw * ratio).coerceIn(-1f, 1f)
            }
        }
        // Any raw frames left in the queue are the denoiser's not-yet-
        // emitted tail: they MUST stay queued for the next call, not be
        // emitted early (that would break time alignment).
        if (outFloats.isNotEmpty()) {
            outputQueue.add(encode(outFloats))
        }
    }

    /** Encodes float samples in [-1, 1] back to the input encoding. */
    private fun encode(samples: FloatArray): ByteBuffer {
        return when (encoding) {
            C.ENCODING_PCM_FLOAT -> {
                val buf = ByteBuffer.allocateDirect(samples.size * 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                for (s in samples) buf.putFloat(s.coerceIn(-1f, 1f))
                buf.flip()
                buf
            }
            else -> { // PCM_16BIT (the only other supported encoding)
                val buf = ByteBuffer.allocateDirect(samples.size * 2)
                    .order(ByteOrder.LITTLE_ENDIAN)
                for (s in samples) {
                    val clamped = (s * 32768f).coerceIn(-32768f, 32767f)
                    buf.putShort(clamped.toInt().toShort())
                }
                buf.flip()
                buf
            }
        }
    }
}
