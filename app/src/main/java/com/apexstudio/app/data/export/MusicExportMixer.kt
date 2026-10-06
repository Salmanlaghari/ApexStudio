package com.apexstudio.app.data.export

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import com.apexstudio.app.domain.model.AudioTrack
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor
import kotlin.math.min

/**
 * Mixes timeline music tracks into an exported video file.
 *
 * Media3 Transformer (used by [ExportEngine]) renders the selected clip with
 * all its audio effects, but it cannot mix extra timeline audio tracks on top.
 * This mixer closes that gap: it decodes the exported file's audio plus every
 * unmuted [AudioTrack], mixes them in software (per-track volume, fade
 * in/out, each placed at its [AudioTrack.trimStartMs] timeline offset),
 * re-encodes to AAC and muxes with the video track copied bit-for-bit.
 *
 * Pipeline (all streaming, bounded memory):
 *  1. Decode every source to stereo 44.1 kHz float and mix window-by-window
 *     into a temporary PCM WAV (capped at the video duration).
 *  2. Transcode the WAV to AAC, collecting encoded frames in memory.
 *  3. Mux: video track copied from the input + the mixed AAC track,
 *     interleaved by presentation timestamp.
 *
 * Must be called on a background thread (blocking MediaCodec I/O).
 * Tracks that cannot be decoded are skipped — never fail the whole export.
 */
object MusicExportMixer {

    private const val TAG = "MusicExportMixer"
    private const val SAMPLE_RATE = 44100
    private const val CHANNELS = 2
    private const val WINDOW_FRAMES = 2048
    private const val AAC_BITRATE = 128_000

    data class MixResult(
        val outputFile: File,
        val mixedTracks: Int,
        val skippedTracks: Int
    )

    private class PlacedTrack(
        val track: AudioTrack,
        val decoder: TrackDecoder,
        val startFrame: Long,
        val playFrames: Long,
        var done: Boolean = false
    )

    private data class EncodedAudio(
        val format: MediaFormat,
        val frames: List<EncodedFrame>
    )

    private data class EncodedFrame(
        val data: ByteArray,
        val ptsUs: Long,
        val flags: Int
    )

    fun mixMusicIntoVideo(
        context: Context,
        inputVideo: File,
        tracks: List<AudioTrack>,
        outputVideo: File
    ): MixResult {
        require(inputVideo.exists()) { "Input video missing: ${inputVideo.absolutePath}" }
        outputVideo.parentFile?.mkdirs()

        // ---- 1. Probe input -------------------------------------------------
        val probe = MediaExtractor()
        var videoTrackIdx = -1
        var videoFormat: MediaFormat? = null
        var inputAudioTrackIdx = -1
        try {
            probe.setDataSource(inputVideo.absolutePath)
            for (i in 0 until probe.trackCount) {
                val f = probe.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && videoTrackIdx == -1) {
                    videoTrackIdx = i
                    videoFormat = f
                }
                if (mime.startsWith("audio/") && inputAudioTrackIdx == -1) inputAudioTrackIdx = i
            }
        } finally {
            probe.release()
        }
        val vFormat = videoFormat
            ?: throw IllegalArgumentException("No video track in ${inputVideo.name}")
        val videoDurationUs = if (vFormat.containsKey(MediaFormat.KEY_DURATION)) {
            vFormat.getLong(MediaFormat.KEY_DURATION)
        } else 0L
        val durationCapFrames = if (videoDurationUs > 0) {
            ((videoDurationUs / 1_000_000.0) * SAMPLE_RATE).toLong()
        } else Long.MAX_VALUE

        // ---- 2. Open source decoders ----------------------------------------
        val original: TrackDecoder? = if (inputAudioTrackIdx != -1) {
            try {
                TrackDecoder.open(inputVideo.absolutePath, inputAudioTrackIdx)
            } catch (e: Exception) {
                Log.w(TAG, "Original audio not decodable, mixing music only", e)
                null
            }
        } else null

        val placed = mutableListOf<PlacedTrack>()
        var skipped = 0
        try {
            for (t in tracks) {
                val playMs = t.trimEndMs - t.trimStartMs
                if (playMs <= 0L) {
                    skipped++
                    continue
                }
                val decoder = try {
                    TrackDecoder.openUri(context, t.uri)
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping undecodable music track: ${t.name}", e)
                    null
                }
                if (decoder == null) {
                    skipped++
                    continue
                }
                val startFrame = (t.trimStartMs.coerceAtLeast(0L) * SAMPLE_RATE) / 1000L
                if (startFrame >= durationCapFrames) {
                    decoder.release()
                    skipped++
                    continue
                }
                val playFrames = (playMs * SAMPLE_RATE) / 1000L
                placed.add(PlacedTrack(t, decoder, startFrame, playFrames))
            }

            if (placed.isEmpty()) {
                // Nothing to mix — keep the original file untouched.
                inputVideo.copyTo(outputVideo, overwrite = true)
                return MixResult(outputVideo, 0, skipped)
            }

            // ---- 3. Mix to a temporary WAV ----------------------------------
            val wavFile = File.createTempFile("apex_mix_", ".wav", outputVideo.parentFile)
            try {
                renderMixWav(original, placed, durationCapFrames, wavFile)
                // ---- 4. WAV -> AAC ------------------------------------------
                val encoded = transcodeWavToAac(wavFile)
                // ---- 5. Mux video copy + mixed AAC --------------------------
                muxVideoWithAudio(
                    inputVideo, videoTrackIdx, vFormat,
                    encoded.frames, encoded.format, outputVideo
                )
            } finally {
                wavFile.delete()
            }
            return MixResult(outputVideo, placed.size, skipped)
        } finally {
            original?.release()
            placed.forEach { runCatching { it.decoder.release() } }
        }
    }

    // ------------------------------------------------------------------
    // Pass 1: software mix to PCM WAV
    // ------------------------------------------------------------------

    private fun renderMixWav(
        original: TrackDecoder?,
        placed: List<PlacedTrack>,
        durationCapFrames: Long,
        wavFile: File
    ) {
        val mix = FloatArray(WINDOW_FRAMES * CHANNELS)
        val tmp = FloatArray(WINDOW_FRAMES * CHANNELS)
        val pcm = ByteArray(WINDOW_FRAMES * CHANNELS * 2)

        RandomAccessFile(wavFile, "rw").use { raf ->
            // Placeholder header; rewritten with real sizes at the end.
            raf.write(wavHeader(0))
            var globalFrame = 0L
            var totalFrames = 0L

            while (globalFrame < durationCapFrames) {
                val frames = min(WINDOW_FRAMES.toLong(), durationCapFrames - globalFrame).toInt()
                mix.fill(0f)

                // Original clip audio (Transformer already applied its fx/volume).
                if (original != null) {
                    val n = original.readFrames(tmp, frames)
                    for (i in 0 until n) {
                        mix[i * 2] += tmp[i * 2]
                        mix[i * 2 + 1] += tmp[i * 2 + 1]
                    }
                }

                // Music tracks.
                for (p in placed) {
                    if (p.done) continue
                    val w0 = globalFrame
                    val w1 = globalFrame + frames
                    val s0 = maxOf(w0, p.startFrame)
                    val s1 = minOf(w1, p.startFrame + p.playFrames)
                    if (s1 > s0) {
                        val need = (s1 - s0).toInt()
                        val n = p.decoder.readFrames(tmp, need)
                        val vol = p.track.effectiveVolume()
                        for (i in 0 until n) {
                            val g = s0 + i
                            val env = fadeEnvelope(
                                g - p.startFrame, p.playFrames,
                                p.track.fadeInMs, p.track.fadeOutMs
                            )
                            val v = vol * env
                            val idx = ((g - w0) * 2).toInt()
                            mix[idx] += tmp[i * 2] * v
                            mix[idx + 1] += tmp[i * 2 + 1] * v
                        }
                    }
                    if (w0 >= p.startFrame + p.playFrames || p.decoder.isEos()) {
                        p.done = true
                    }
                }

                // Clamp + convert to PCM16LE.
                for (i in 0 until frames * CHANNELS) {
                    val s = (mix[i] * 32767f).toInt().coerceIn(-32768, 32767)
                    pcm[i * 2] = (s and 0xFF).toByte()
                    pcm[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                }
                raf.write(pcm, 0, frames * CHANNELS * 2)
                globalFrame += frames
                totalFrames = globalFrame

                // All sources exhausted: stop early instead of writing silence
                // up to the duration cap.
                val allDone = (original?.isEos() ?: true) && placed.all { it.done }
                if (allDone) break
            }

            // Rewrite the header with the real data size.
            raf.seek(0)
            // WAV sizes are 32-bit; a >6.7h timeline can't be represented —
            // fail fast (the caller falls back to the unmixed export).
            require(totalFrames <= Int.MAX_VALUE / (CHANNELS * 2)) {
                "Mixed audio exceeds WAV size limits"
            }
            raf.write(wavHeader((totalFrames * CHANNELS * 2).toInt()))
        }
    }

    private fun wavHeader(dataBytes: Int): ByteArray {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(36 + dataBytes)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1) // PCM
        header.putShort(CHANNELS.toShort())
        header.putInt(SAMPLE_RATE)
        header.putInt(SAMPLE_RATE * CHANNELS * 2)
        header.putShort((CHANNELS * 2).toShort())
        header.putShort(16)
        header.put("data".toByteArray())
        header.putInt(dataBytes)
        return header.array()
    }

    // ------------------------------------------------------------------
    // Pass 2: WAV -> AAC
    // ------------------------------------------------------------------

    private fun transcodeWavToAac(wavFile: File): EncodedAudio {
        val encFormat = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNELS
        )
        encFormat.setInteger(
            MediaFormat.KEY_AAC_PROFILE,
            MediaCodecInfo.CodecProfileLevel.AACObjectLC
        )
        encFormat.setInteger(MediaFormat.KEY_BIT_RATE, AAC_BITRATE)
        encFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 32 * 1024)

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
        try {
            val frames = mutableListOf<EncodedFrame>()
            var outFormat: MediaFormat? = null
            val info = MediaCodec.BufferInfo()
            var ptsUs = 0L

            RandomAccessFile(wavFile, "r").use { raf ->
                raf.seek(44) // skip WAV header
                var inputDone = false
                val chunk = ByteArray(16 * 1024)
                while (true) {
                    if (!inputDone) {
                        val inIdx = encoder.dequeueInputBuffer(5000)
                        if (inIdx >= 0) {
                            val ib = encoder.getInputBuffer(inIdx)
                            if (ib == null || ib.remaining() < CHANNELS * 2) {
                                // No usable input buffer right now; try again next lap.
                                if (ib != null) {
                                    encoder.queueInputBuffer(inIdx, 0, 0, ptsUs, 0)
                                }
                            } else {
                                // Whole PCM frames only — a torn frame would
                                // corrupt the encoder's pts bookkeeping.
                                val want = min(chunk.size, ib.remaining()) / (CHANNELS * 2) * (CHANNELS * 2)
                                val n = raf.read(chunk, 0, want)
                                if (n <= 0) {
                                    encoder.queueInputBuffer(
                                        inIdx, 0, 0, ptsUs,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    inputDone = true
                                } else {
                                    ib.put(chunk, 0, n)
                                    val framesIn = n / (CHANNELS * 2)
                                    encoder.queueInputBuffer(inIdx, 0, n, ptsUs, 0)
                                    ptsUs += (framesIn * 1_000_000L) / SAMPLE_RATE
                                }
                            }
                        }
                    }
                    val outIdx = encoder.dequeueOutputBuffer(info, 8000)
                    when {
                        outIdx >= 0 -> {
                            val ob = encoder.getOutputBuffer(outIdx)!!
                            if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 &&
                                info.size > 0
                            ) {
                                val copy = ByteArray(info.size)
                                ob.get(copy)
                                frames.add(
                                    EncodedFrame(copy, info.presentationTimeUs, info.flags)
                                )
                            }
                            encoder.releaseOutputBuffer(outIdx, false)
                            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                        }
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ->
                            outFormat = encoder.outputFormat
                        outIdx == MediaCodec.INFO_TRY_AGAIN_LATER ->
                            if (inputDone) {
                                // Give the encoder a moment, then give up gracefully.
                                Thread.sleep(10)
                            }
                    }
                    if (inputDone && outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        // Drain once more; encoders flush quickly after EOS.
                        var drained = 0
                        while (drained < 50) {
                            val idx = encoder.dequeueOutputBuffer(info, 2000)
                            if (idx >= 0) {
                                val ob = encoder.getOutputBuffer(idx)!!
                                if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 &&
                                    info.size > 0
                                ) {
                                    val copy = ByteArray(info.size)
                                    ob.get(copy)
                                    frames.add(
                                        EncodedFrame(copy, info.presentationTimeUs, info.flags)
                                    )
                                }
                                encoder.releaseOutputBuffer(idx, false)
                                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                            } else break
                            drained++
                        }
                        break
                    }
                }
            }
            val fmt = outFormat ?: encFormat
            return EncodedAudio(fmt, frames)
        } finally {
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
        }
    }

    // ------------------------------------------------------------------
    // Pass 3: mux (video copied + mixed AAC, interleaved by pts)
    // ------------------------------------------------------------------

    private fun muxVideoWithAudio(
        inputVideo: File,
        videoTrackIdx: Int,
        videoFormat: MediaFormat,
        audioFrames: List<EncodedFrame>,
        audioFormat: MediaFormat,
        outputVideo: File
    ) {
        val vEx = MediaExtractor()
        vEx.setDataSource(inputVideo.absolutePath)
        vEx.selectTrack(videoTrackIdx)

        val muxer = MediaMuxer(outputVideo.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val vOut = muxer.addTrack(videoFormat)
            val aOut = muxer.addTrack(audioFormat)
            muxer.start()

            // Size the sample buffer from the track's max input size when
            // declared — 4K keyframes can exceed a small fixed buffer.
            val maxInputSize = if (videoFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } else 0
            val vBuf = ByteBuffer.allocate(maxOf(8 * 1024 * 1024, maxInputSize))
            val vInfo = MediaCodec.BufferInfo()
            var audioIdx = 0

            fun readVideo(): Boolean {
                val n = vEx.readSampleData(vBuf, 0)
                if (n < 0) return false
                vInfo.set(0, n, vEx.sampleTime, vEx.sampleFlags)
                vEx.advance()
                return true
            }

            fun writeVideo() {
                vBuf.position(vInfo.offset)
                vBuf.limit(vInfo.offset + vInfo.size)
                muxer.writeSampleData(vOut, vBuf, vInfo)
            }

            fun writeAudio(f: EncodedFrame) {
                val buf = ByteBuffer.wrap(f.data)
                val bi = MediaCodec.BufferInfo()
                bi.set(0, f.data.size, f.ptsUs, f.flags)
                muxer.writeSampleData(aOut, buf, bi)
            }

            var videoAvail = readVideo()
            while (videoAvail || audioIdx < audioFrames.size) {
                val a = audioFrames.getOrNull(audioIdx)
                if (videoAvail && (a == null || vInfo.presentationTimeUs <= a.ptsUs)) {
                    writeVideo()
                    videoAvail = readVideo()
                } else if (a != null) {
                    writeAudio(a)
                    audioIdx++
                } else {
                    break
                }
            }
            muxer.stop()
        } finally {
            runCatching { muxer.release() }
            runCatching { vEx.release() }
        }
    }

    // ------------------------------------------------------------------
    // Mixer internals
    // ------------------------------------------------------------------

    /**
     * Volume envelope 0..1 for a music frame at [posFrames] inside its play
     * window ([playFrames] long), with linear fade in/out. Pure function,
     * unit-tested.
     */
    internal fun fadeEnvelope(
        posFrames: Long,
        playFrames: Long,
        fadeInMs: Long,
        fadeOutMs: Long
    ): Float {
        if (playFrames <= 0L) return 0f
        val fadeInFrames = (fadeInMs.coerceAtLeast(0L) * SAMPLE_RATE) / 1000L
        val fadeOutFrames = (fadeOutMs.coerceAtLeast(0L) * SAMPLE_RATE) / 1000L
        var e = 1f
        if (fadeInFrames > 0 && posFrames < fadeInFrames) {
            e = min(e, posFrames.toFloat() / fadeInFrames)
        }
        if (fadeOutFrames > 0) {
            val remaining = playFrames - posFrames
            if (remaining < fadeOutFrames) {
                e = min(e, remaining.coerceAtLeast(0L).toFloat() / fadeOutFrames)
            }
        }
        return e.coerceIn(0f, 1f)
    }

    /**
     * Decodes one audio track to stereo 44.1 kHz float frames.
     * Handles file paths, `file://` URIs and `content://` URIs.
     */
    private class TrackDecoder private constructor(
        private val extractor: MediaExtractor,
        private val decoder: MediaCodec
    ) {
        private val info = MediaCodec.BufferInfo()
        private var inputDone = false
        private var outputDone = false
        private var inSampleRate = SAMPLE_RATE
        private var inChannels = CHANNELS
        private var inPcm16 = true

        private var pending = FloatArray(8192 * CHANNELS)
        private var pendingFrames = 0
        private var pendingPos = 0

        // Linear-resample state (carried across decoder output buffers).
        private var rsPos = 0.0
        private val rsCarry = FloatArray(CHANNELS)

        companion object {
            /** Opens track [audioTrackIndex] of a plain file path. */
            fun open(path: String, audioTrackIndex: Int): TrackDecoder {
                val ex = MediaExtractor()
                try {
                    ex.setDataSource(path)
                } catch (e: Exception) {
                    ex.release()
                    throw e
                }
                return openSelected(ex, audioTrackIndex)
            }

            /** Opens the first audio track of a path / file:// / content:// URI. */
            fun openUri(context: Context, uri: String): TrackDecoder {
                val ex = MediaExtractor()
                try {
                    val clean = uri.removePrefix("file://")
                    if (clean.startsWith("content://")) {
                        ex.setDataSource(context, Uri.parse(clean), null)
                    } else {
                        ex.setDataSource(clean)
                    }
                } catch (e: Exception) {
                    ex.release()
                    throw e
                }
                var audioIdx = -1
                for (i in 0 until ex.trackCount) {
                    val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                    if (mime != null && mime.startsWith("audio/")) {
                        audioIdx = i
                        break
                    }
                }
                if (audioIdx == -1) {
                    ex.release()
                    throw IllegalArgumentException("No audio track in $uri")
                }
                return openSelected(ex, audioIdx)
            }

            private fun openSelected(ex: MediaExtractor, idx: Int): TrackDecoder {
                try {
                    val format = ex.getTrackFormat(idx)
                    val mime = format.getString(MediaFormat.KEY_MIME)
                        ?: throw IllegalArgumentException("Missing MIME")
                    ex.selectTrack(idx)
                    val dec = MediaCodec.createDecoderByType(mime)
                    dec.configure(format, null, null, 0)
                    dec.start()
                    return TrackDecoder(ex, dec)
                } catch (e: Exception) {
                    ex.release()
                    throw e
                }
            }
        }

        /** Reads up to [frames] stereo-interleaved 44.1 kHz float samples. 0 = EOS. */
        fun readFrames(out: FloatArray, frames: Int): Int {
            var written = 0
            while (written < frames) {
                if (pendingPos >= pendingFrames && !fillPending()) break
                val avail = pendingFrames - pendingPos
                val n = min(avail, frames - written)
                System.arraycopy(pending, pendingPos * CHANNELS, out, written * CHANNELS, n * CHANNELS)
                pendingPos += n
                written += n
            }
            return written
        }

        fun isEos(): Boolean = outputDone && pendingPos >= pendingFrames

        fun release() {
            runCatching { decoder.stop() }
            runCatching { decoder.release() }
            runCatching { extractor.release() }
        }

        private fun updateFormat(format: MediaFormat) {
            if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    .takeIf { it > 0 } ?: SAMPLE_RATE
            }
            if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    .takeIf { it > 0 } ?: CHANNELS
            }
            // "pcm-encoding" string key avoids the API-28+ constant; 2 = 16-bit.
            inPcm16 = format.getInteger("pcm-encoding", 2) != 3
        }

        /** Decodes the next output buffer into [pending]. False when drained. */
        private fun fillPending(): Boolean {
            val before = pendingFrames
            while (pendingFrames == before) {
                if (!inputDone) {
                    val inIdx = decoder.dequeueInputBuffer(2000)
                    if (inIdx >= 0) {
                        val ib = decoder.getInputBuffer(inIdx)
                        if (ib == null) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0L, 0)
                        } else {
                            val n = extractor.readSampleData(ib, 0)
                            if (n < 0) {
                                decoder.queueInputBuffer(
                                    inIdx, 0, 0, 0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                decoder.queueInputBuffer(inIdx, 0, n, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }
                val outIdx = decoder.dequeueOutputBuffer(info, 8000)
                when {
                    outIdx >= 0 -> {
                        val ob = decoder.getOutputBuffer(outIdx)
                        if (ob != null && info.size > 0) {
                            updateFormat(decoder.outputFormat)
                            convertAndAppend(ob, info.size)
                        }
                        decoder.releaseOutputBuffer(outIdx, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                            return pendingFrames > before
                        }
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ->
                        updateFormat(decoder.outputFormat)
                    outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (inputDone) return pendingFrames > before
                    }
                }
                if (outputDone) return pendingFrames > before
            }
            return true
        }

        private fun nativeSample(le: ByteBuffer, size: Int, frame: Int, ch: Int): Float {
            val srcCh = when {
                inChannels == 1 -> 0
                ch < inChannels -> ch
                else -> 0
            }
            return if (inPcm16) {
                val idx = (frame * inChannels + srcCh) * 2
                if (idx + 1 >= size) 0f else le.getShort(idx) / 32768f
            } else {
                val idx = (frame * inChannels + srcCh) * 4
                if (idx + 3 >= size) 0f else le.getFloat(idx).coerceIn(-1f, 1f)
            }
        }

        /**
         * Converts one decoder output buffer to stereo 44.1 kHz float and
         * appends it to [pending], with linear resampling when the decoder's
         * native rate differs. Resample state ([rsPos]/[rsCarry]) carries
         * across buffers so there are no clicks at buffer boundaries.
         */
        private fun convertAndAppend(ob: ByteBuffer, size: Int) {
            val le = ob.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            val bytesPerSample = if (inPcm16) 2 else 4
            val frames = size / bytesPerSample / inChannels.coerceAtLeast(1)
            if (frames <= 0) return
            val step = inSampleRate.toDouble() / SAMPLE_RATE
            // Upper bound on output frames for this buffer.
            val maxOut = ((frames + 1) / step).toInt() + 2
            ensurePendingCapacity(pendingFrames + maxOut)

            fun s(idx: Int, ch: Int): Float =
                if (idx < 0) rsCarry[ch] else nativeSample(le, size, idx, ch)

            var f = 0
            while (true) {
                val i = floor(rsPos).toInt()
                if (i + 1 >= frames) break
                val frac = (rsPos - i).toFloat()
                for (c in 0 until CHANNELS) {
                    val s0 = s(i, c)
                    val s1 = s(i + 1, c)
                    pending[(pendingFrames + f) * CHANNELS + c] = s0 + (s1 - s0) * frac
                }
                f++
                rsPos += step
            }
            // Carry the last input frame and rebase the position for the next buffer.
            for (c in 0 until CHANNELS) rsCarry[c] = nativeSample(le, size, frames - 1, c)
            rsPos -= frames
            pendingFrames += f
        }

        private fun ensurePendingCapacity(needed: Int) {
            if (pending.size >= needed * CHANNELS) return
            var newSize = pending.size * 2
            while (newSize < needed * CHANNELS) newSize *= 2
            pending = pending.copyOf(newSize)
        }
    }
}
