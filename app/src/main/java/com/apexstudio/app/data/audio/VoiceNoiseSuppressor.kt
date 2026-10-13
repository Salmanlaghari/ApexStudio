package com.apexstudio.app.data.audio

/**
 * Streaming voice-noise suppressor interface (mono, float PCM in [-1, 1]).
 *
 * Implementations are stateful: they buffer internally for overlap-add
 * processing. Contract:
 * - [process] returns the samples that are fully processed so far; it
 *   may return fewer samples than it consumed (overlap-add latency).
 * - [flush] returns the remaining tail. After [flush], the total number
 *   of returned samples across all [process] + [flush] calls equals the
 *   total number of input samples — sample-count parity is what keeps
 *   audio in sync with video, so implementations must never drop or
 *   duplicate samples.
 * - [reset] clears all state (new stream). [resetBuffers] clears only
 *   the overlap-add buffers (seek within the same stream) and keeps the
 *   learned noise profile.
 *
 * The interface is the seam where a future neural backend (e.g. a real
 * RNNoise implementation, once this repo gains NDK/CI support for native
 * code) can be dropped in behind the same [DenoiseAudioProcessor]
 * without touching the preview/export wiring.
 */
interface VoiceNoiseSuppressor {

    /** Full reset: new stream (new clip / new format). */
    fun reset()

    /** Seek reset: clear overlap-add buffers, keep the noise profile. */
    fun resetBuffers()

    /** Feed [input] samples; returns the fully-processed prefix. */
    fun process(input: FloatArray): FloatArray

    /** End of stream: returns the remaining tail (total out == total in). */
    fun flush(): FloatArray
}
