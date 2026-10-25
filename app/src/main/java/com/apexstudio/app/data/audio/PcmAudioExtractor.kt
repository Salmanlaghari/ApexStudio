package com.apexstudio.app.data.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.nio.ByteOrder
import java.util.concurrent.CancellationException

/**
 * Extracts a video's audio track as 16 kHz mono float PCM in [-1, 1], suitable for
 * on-device speech models. Compressed track packets are decoded through MediaCodec,
 * then decoder PCM is downmixed and linearly resampled in bounded chunks.
 *
 * Work is synchronous; callers should invoke from Dispatchers.IO. The final FloatArray
 * is necessarily resident in memory, but intermediate PCM is streamed without boxing.
 */
object PcmAudioExtractor {
    private const val TAG = "PcmAudioExtractor"
    const val TARGET_RATE = 16_000
    private const val CODEC_TIMEOUT_US = 10_000L
    private const val STALL_TIMEOUT_NS = 30_000_000_000L
    /** Maximum supported output duration; bounds the returned float samples to 28.8 million. */
    const val MAX_DURATION_SECONDS = 30 * 60
    private const val MAX_OUTPUT_SAMPLES = TARGET_RATE * MAX_DURATION_SECONDS

    /**
     * @return mono float samples at 16 kHz, or null when there is no audio track or
     *   decoding fails. Audio longer than [MAX_DURATION_SECONDS] is rejected.
     *   Unsupported MediaCodec PCM output encodings throw
     *   [UnsupportedPcmEncodingException] instead of being misinterpreted.
     * @param checkCancelled invoked while feeding and draining the decoder; may
     *   throw (for example, kotlinx.coroutines.ensureActive's CancellationException).
     */
    @JvmOverloads
    fun extractMono16k(
        context: Context,
        uri: Uri,
        checkCancelled: () -> Unit = {}
    ): FloatArray? = decodeMono16k(context, uri, checkCancelled, onSample = null)

    /** Decode PCM incrementally into [onSample] instead of buffering a full-track FloatArray. */
    fun forEachMono16kSample(
        context: Context,
        uri: Uri,
        checkCancelled: () -> Unit = {},
        onSample: (Float) -> Unit
    ): Boolean = decodeMono16k(context, uri, checkCancelled, onSample) != null

    private fun decodeMono16k(
        context: Context,
        uri: Uri,
        checkCancelled: () -> Unit,
        onSample: ((Float) -> Unit)?
    ): FloatArray? {
        checkCancelled()
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: run {
                Log.w(TAG, "no audio track in $uri")
                return null
            }
            val trackFormat = extractor.getTrackFormat(trackIndex)
            if (trackFormat.containsKey(MediaFormat.KEY_DURATION) &&
                trackFormat.getLong(MediaFormat.KEY_DURATION) > MAX_DURATION_SECONDS * 1_000_000L
            ) {
                throw AudioDurationLimitException("Audio exceeds the $MAX_DURATION_SECONDS-second extraction limit")
            }
            val mime = trackFormat.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalArgumentException("Audio track has no MIME type")
            extractor.selectTrack(trackIndex)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(trackFormat, null, null, 0)
            codec.start()
            val output = if (onSample == null) FloatSampleBuffer() else null
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var emittedSamples = 0
            var sampleRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            var resampler: StreamingLinearResampler? = null
            var resamplerSourceRate = 0
            var resamplerChannels = 0
            var resamplerEncoding = 0
            var lastProgressNs = System.nanoTime()
            val emitSample: (Float) -> Unit = { sample ->
                if (emittedSamples >= MAX_OUTPUT_SAMPLES) {
                    throw AudioDurationLimitException("Decoded audio exceeds the $MAX_DURATION_SECONDS-second extraction limit")
                }
                emittedSamples++
                if (onSample == null) output!!.append(sample) else onSample(sample)
            }

            while (!outputDone) {
                checkCancelled()
                check(System.nanoTime() - lastProgressNs < STALL_TIMEOUT_NS) { "Audio decoder stalled without input/output progress" }
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                            ?: throw IllegalStateException("Decoder returned a null input buffer")
                        inputBuffer.clear()
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val presentationTimeUs = extractor.sampleTime.coerceAtLeast(0L)
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, presentationTimeUs, 0)
                            extractor.advance()
                        }
                        lastProgressNs = System.nanoTime()
                    }
                }

                checkCancelled()
                val outputIndex = codec.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)
                when {
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        lastProgressNs = System.nanoTime()
                        val format = codec.outputFormat
                        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        pcmEncoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        } else {
                            AudioFormat.ENCODING_PCM_16BIT
                        }
                        if (resampler != null &&
                            (resamplerSourceRate != sampleRate || resamplerChannels != channels || resamplerEncoding != pcmEncoding)
                        ) {
                            throw IllegalStateException("Decoder PCM format changed during output")
                        }
                    }
                    outputIndex >= 0 -> {
                        lastProgressNs = System.nanoTime()
                        try {
                            if (info.size > 0) {
                                val buffer = codec.getOutputBuffer(outputIndex)
                                    ?: throw IllegalStateException("Decoder returned a null output buffer")
                                val start = info.offset
                                val endLong = start.toLong() + info.size.toLong()
                                require(start >= 0 && endLong <= buffer.capacity().toLong()) {
                                    "Invalid decoder output range: $start..$endLong/${buffer.capacity()}"
                                }
                                val chunk = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                                chunk.clear()
                                chunk.position(start)
                                chunk.limit(endLong.toInt())
                                if (resampler == null) {
                                    require(sampleRate > 0 && channels > 0) {
                                        "Decoder returned invalid PCM format: $sampleRate Hz, $channels channels"
                                    }
                                    resamplerSourceRate = sampleRate
                                    resamplerChannels = channels
                                    resamplerEncoding = pcmEncoding
                                    resampler = StreamingLinearResampler(sampleRate, TARGET_RATE, emitSample)
                                }
                                PcmAudioProcessor.process(
                                    chunk.slice().order(ByteOrder.LITTLE_ENDIAN),
                                    pcmEncoding,
                                    channels,
                                    resampler
                                )
                            }
                        } finally {
                            codec.releaseOutputBuffer(outputIndex, false)
                        }
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            resampler?.finish()
            return if (onSample == null) {
                output?.toFloatArray()?.takeIf { it.isNotEmpty() }
            } else {
                FloatArray(0).takeIf { emittedSamples > 0 }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (durationLimit: AudioDurationLimitException) {
            throw durationLimit
        } catch (unsupported: UnsupportedPcmEncodingException) {
            Log.e(TAG, "Decoder returned unsupported PCM output for $uri", unsupported)
            throw unsupported
        } catch (e: Exception) {
            Log.w(TAG, "audio decode failed: $uri", e)
            return null
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {
                // stop() may fail if setup did not reach the started state.
            }
            try {
                codec?.release()
            } finally {
                extractor.release()
            }
        }
    }
}

internal class AudioDurationLimitException(message: String) : IllegalArgumentException(message)
