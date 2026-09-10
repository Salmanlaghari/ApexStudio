package com.apexstudio.app.data.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AudioWaveformData(
    val samples: FloatArray,
    val durationMs: Long,
    val peakLevel: Float
)

/**
 * Media analysis helpers.
 *
 * [analyzeAudioWaveform] extracts a *real* amplitude envelope for the
 * A1 track: the selected audio track is decoded to raw 16-bit PCM with
 * MediaCodec (MP3/AAC/whatever the container holds), then the decoded
 * samples are bucketed into [sampleCount] bars covering the [trimStartMs]
 * → [trimEndMs] window. Compressed frames are never read as PCM — that
 * produced noise-shaped garbage for every AAC/MP3 source.
 */
class MediaAnalyzer {

    suspend fun analyzeAudioWaveform(
        uri: String,
        context: Context,
        sampleCount: Int = 200,
        trimStartMs: Long = 0L,
        trimEndMs: Long = Long.MAX_VALUE
    ): AudioWaveformData = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var pfd: ParcelFileDescriptor? = null
        var decoder: MediaCodec? = null
        try {
            val parsedUri = android.net.Uri.parse(uri)
            pfd = context.contentResolver.openFileDescriptor(parsedUri, "r")
                ?: return@withContext emptyWaveform(sampleCount)
            extractor.setDataSource(pfd.fileDescriptor)
            val trackIndex = findAudioTrack(extractor)
            if (trackIndex < 0) return@withContext emptyWaveform(sampleCount)
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext emptyWaveform(sampleCount)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else 0L

            val startUs = (trimStartMs.coerceAtLeast(0L)) * 1000L
            val endUs = if (trimEndMs == Long.MAX_VALUE || trimEndMs <= 0L) {
                if (durationUs > 0L) durationUs else 0L
            } else {
                trimEndMs * 1000L
            }
            if (durationUs <= 0L || sampleRate <= 0 || channelCount <= 0) {
                return@withContext emptyWaveform(sampleCount)
            }
            val clampEnd = endUs.coerceIn(startUs, durationUs)
            if (clampEnd <= startUs) return@withContext emptyWaveform(sampleCount)

            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            // Decoded PCM frames (per-channel samples) inside the trim window.
            val totalFrames = ((clampEnd - startUs) * sampleRate) / 1_000_000L
                .coerceAtLeast(1L)
            val sums = DoubleArray(sampleCount)
            val counts = LongArray(sampleCount)
            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var fedOnce = false
            var idleStreak = 0
            var safetyIterations = 0

            while (!outputEnded && safetyIterations < 500_000) {
                safetyIterations++
                var progressed = false
                // --- Feed the decoder ---
                if (!inputEnded) {
                    val inIndex = decoder.dequeueInputBuffer(10_000L)
                    if (inIndex >= 0) {
                        if (extractor.sampleSize <= 0) {
                            decoder.queueInputBuffer(
                                inIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputEnded = true
                            fedOnce = true
                        } else {
                            val inBuf = decoder.getInputBuffer(inIndex)
                            if (inBuf != null) {
                                inBuf.clear()
                                val read = extractor.readSampleData(inBuf, 0).coerceAtLeast(0)
                                val ptsUs = extractor.sampleTime.coerceAtLeast(0L)
                                decoder.queueInputBuffer(inIndex, 0, read, ptsUs, 0)
                                fedOnce = true
                                extractor.advance()
                            }
                        }
                        progressed = true
                    }
                }

                // --- Drain the decoder ---
                val outIndex = decoder.dequeueOutputBuffer(info, 10_000L)
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    progressed = true
                    continue
                }
                if (outIndex >= 0) {
                    progressed = true
                    idleStreak = 0
                    val codecConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    val isEos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    if (!codecConfig && info.size > 0 && info.presentationTimeUs >= startUs) {
                        val outBuf = decoder.getOutputBuffer(outIndex)
                        if (outBuf != null) {
                            accumulatePcm(
                                outBuf, info, startUs, sampleRate, channelCount,
                                totalFrames, sums, counts
                            )
                        }
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                    if (isEos) outputEnded = true
                } else {
                    // Nothing available this round. If we're still feeding
                    // input there may be more frames coming; once input is
                    // exhausted the decoder drains itself. Break out if the
                    // pipeline sits idle for a while (no EOS seen — avoid
                    // spinning forever on malformed input).
                    if (progressed) idleStreak = 0 else idleStreak++
                    if (idleStreak > 400) break
                }
            }
            decoder.stop()
            decoder.release()
            decoder = null

            // Average each bucket (mean amplitude) then normalise to the peak.
            val samples = FloatArray(sampleCount)
            var peak = 0f
            for (i in 0 until sampleCount) {
                if (counts[i] > 0L) {
                    samples[i] = (sums[i] / counts[i]).toFloat()
                    if (samples[i] > peak) peak = samples[i]
                }
            }
            if (peak > 0f) {
                for (i in samples.indices) samples[i] = (samples[i] / peak).coerceIn(0f, 1f)
            }
            AudioWaveformData(samples, clampEnd / 1000L - startUs / 1000L, peak)
        } catch (e: Exception) {
            // Decoding failed (exotic codec / corrupt file) — return a
            // flat line rather than noise-shaped garbage.
            emptyWaveform(sampleCount)
        } finally {
            try { extractor.release() } catch (_: Exception) { }
            try { decoder?.release() } catch (_: Exception) { }
            try { pfd?.close() } catch (_: Exception) { }
        }
    }

    /**
     * Fold a decoded PCM buffer into the [sampleCount] amplitude buckets.
     * [totalFrames] is the number of per-channel sample frames in the
     * trim window, so bucket = frameIndex * sampleCount / totalFrames.
     * Every 16-bit sample contributes its absolute amplitude (stereo
     * interleaves both channels — fine for an envelope display).
     */
    private fun accumulatePcm(
        buffer: java.nio.ByteBuffer,
        info: MediaCodec.BufferInfo,
        startUs: Long,
        sampleRate: Int,
        channelCount: Int,
        totalFrames: Long,
        sums: DoubleArray,
        counts: LongArray
    ) {
        val sampleCount = sums.size
        buffer.position(info.offset)
        val limit = info.offset + info.size
        if (limit > buffer.capacity()) return
        // PCM frame (per-channel sample) at the start of this buffer.
        var startFrame = ((info.presentationTimeUs - startUs) * sampleRate) / 1_000_000L
        if (startFrame < 0L) startFrame = 0L
        val shortsPerFrame = channelCount.coerceAtLeast(1)
        var s = info.offset
        var shortIndex = 0
        while (s + 1 < limit) {
            val low = (buffer.get(s).toInt() and 0xFF)
            val high = (buffer.get(s + 1).toInt() shl 8)
            val amp = kotlin.math.abs((low or high).toShort().toFloat()) / 32768f
            val frame = startFrame + shortIndex / shortsPerFrame
            if (frame < totalFrames) {
                val bucket = ((frame * sampleCount) / totalFrames).toInt()
                if (bucket in 0 until sampleCount) {
                    sums[bucket] += amp
                    counts[bucket]++
                }
            }
            shortIndex++
            s += 2
        }
    }

    suspend fun getMediaDuration(uri: String, context: Context): Long =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, android.net.Uri.parse(uri))
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val duration = durationStr?.toLongOrNull() ?: 0L
                retriever.release()
                duration
            } catch (e: Exception) {
                0L
            }
        }

    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) return i
        }
        return -1
    }

    private fun emptyWaveform(sampleCount: Int): AudioWaveformData =
        AudioWaveformData(FloatArray(sampleCount.coerceAtLeast(1)), 0L, 0f)
}
