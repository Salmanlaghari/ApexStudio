package com.apexstudio.app.data.photoedit

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import com.apexstudio.app.data.export.CodecPumpStep
import com.apexstudio.app.data.export.ExportCodecWatchdog
import com.apexstudio.app.data.export.pumpCodecUntilOutputEos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Encodes a single edited photo bitmap as a short H.264 video so the
 * existing Media3 Transformer export pipeline (which only accepts
 * video inputs) can export photo clips with text overlays, stickers,
 * transitions, etc. exactly like video clips.
 *
 * The bitmap is repeated at 10 fps. Duration is quantized to complete frames;
 * the codec/muxer may apply its own final-sample duration behavior.
 */
object PhotoVideoEncoder {

    private const val TAG = "PhotoVideoEncoder"
    private const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC
    private const val FRAME_RATE = 10
    private const val FRAME_DURATION_US = 1_000_000L / FRAME_RATE
    private const val I_FRAME_INTERVAL_S = 1
    private const val MAX_DURATION_MS = 24L * 60L * 60L * 1000L

    // Bitrate scales with resolution (~0.5 bits/pixel/frame at 10fps) to avoid
    // generation loss in the intermediate before Transformer re-encodes.
    // Clamped to 40 Mbps (typical MediaCodec H.264 ceiling).
    private fun bitrateFor(width: Int, height: Int): Int =
        (width.toLong() * height.toLong() * FRAME_RATE / 2L).toInt()
            .coerceAtLeast(2_000_000)
            .coerceAtMost(40_000_000)

    /** Return null for invalid or impractically long requests; round valid durations up to frames. */
    internal fun frameCountForDurationMs(durationMs: Long): Int? {
        if (durationMs <= 0L || durationMs > MAX_DURATION_MS) return null
        val wholeSeconds = durationMs / 1000L
        val remainingMs = durationMs % 1000L
        val frames = wholeSeconds * FRAME_RATE +
            (remainingMs * FRAME_RATE + 999L) / 1000L
        return frames.takeIf { it in 1L..Int.MAX_VALUE.toLong() }?.toInt()
    }

    /**
     * Encode [bitmap] into a temp .mp4 in the app cache dir lasting approximately
     * [durationMs] (quantized to 100 ms frames). Returns the finalized file, or null on failure.
     */
    suspend fun encodeStillImage(
        context: Context,
        bitmap: Bitmap,
        durationMs: Long,
        /** Phase 4: Ken Burns pan/zoom — animates the still into motion. */
        kenBurns: Boolean = false
    ): File? = withContext(Dispatchers.Default) {
        val frameCount = frameCountForDurationMs(durationMs) ?: return@withContext null
        val coroutineContext = currentCoroutineContext()
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var frame: Bitmap? = null
        var kbBitmap: Bitmap? = null
        var outFile: File? = null
        var muxerStarted = false
        var completed = false
        try {
            coroutineContext.ensureActive()
            // YUV420 needs even dimensions.
            val width = (bitmap.width / 2) * 2
            val height = (bitmap.height / 2) * 2
            if (width < 2 || height < 2) return@withContext null
            frame = if (bitmap.width != width || bitmap.height != height) {
                Bitmap.createScaledBitmap(bitmap, width, height, true)
            } else bitmap

            val format = MediaFormat.createVideoFormat(MIME, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrateFor(width, height))
                setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_S)
            }
            codec = MediaCodec.createEncoderByType(MIME)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            outFile = File.createTempFile("photo_clip_", ".mp4", context.cacheDir)
            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var trackIndex = -1
            val staticYuv = argbToNv12(frame, width, height)
            // Ken Burns frame scratch (reused — no per-frame allocation).
            kbBitmap = if (kenBurns) Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) else null
            val kbCanvas = kbBitmap?.let { android.graphics.Canvas(it) }
            val kbPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
            }

            /** YUV for frame [i]: static, or animated zoom/pan for Ken Burns. */
            fun yuvForFrame(i: Int): ByteArray {
                val workingBitmap = kbBitmap ?: return staticYuv
                val canvas = kbCanvas ?: return staticYuv
                val progress = if (frameCount > 1) i.toFloat() / (frameCount - 1) else 0f
                // Gentle push-in 1.00 -> 1.18 with a slow diagonal drift.
                val scale = 1f + 0.18f * progress
                val dw = width * scale
                val dh = height * scale
                // Drift from top-left toward bottom-right.
                val dx = (width - dw) * (0.5f - 0.5f * progress)
                val dy = (height - dh) * (0.5f - 0.5f * progress)
                canvas.drawColor(android.graphics.Color.BLACK)
                canvas.drawBitmap(
                    frame!!,
                    null,
                    android.graphics.RectF(dx, dy, dx + dw, dy + dh),
                    kbPaint
                )
                return argbToNv12(workingBitmap, width, height)
            }

            val bufferInfo = MediaCodec.BufferInfo()
            val watchdog = ExportCodecWatchdog()
            var framesQueued = 0
            var outputTrackAdded = false
            pumpCodecUntilOutputEos(watchdog) {
                coroutineContext.ensureActive()
                var progressed = false

                // Feed input, one frame per available input buffer.
                if (framesQueued < frameCount) {
                    val inputIndex = codec!!.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = requireNotNull(codec!!.getInputBuffer(inputIndex))
                        inputBuffer.clear()
                        val frameYuv = yuvForFrame(framesQueued)
                        check(inputBuffer.remaining() >= frameYuv.size) { "Codec input buffer is too small" }
                        inputBuffer.put(frameYuv)
                        val ptsUs = framesQueued.toLong() * FRAME_DURATION_US
                        val lastFrame = framesQueued == frameCount - 1
                        codec!!.queueInputBuffer(
                            inputIndex,
                            0,
                            frameYuv.size,
                            ptsUs,
                            if (lastFrame) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                        )
                        framesQueued++
                        progressed = true
                    }
                }

                val outputIndex = codec!!.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        check(!outputTrackAdded) { "Encoder output format changed more than once" }
                        trackIndex = muxer!!.addTrack(codec!!.outputFormat)
                        muxer!!.start()
                        muxerStarted = true
                        outputTrackAdded = true
                        CodecPumpStep(progressed = true)
                    }
                    outputIndex >= 0 -> {
                        try {
                            val encoded = requireNotNull(codec!!.getOutputBuffer(outputIndex))
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && bufferInfo.size > 0) {
                                check(muxerStarted && trackIndex >= 0) { "Encoded sample arrived before muxer track setup" }
                                check(bufferInfo.offset >= 0 && bufferInfo.size >= 0 &&
                                    bufferInfo.offset <= encoded.capacity() - bufferInfo.size) {
                                    "Invalid encoded output buffer range"
                                }
                                encoded.position(bufferInfo.offset)
                                encoded.limit(bufferInfo.offset + bufferInfo.size)
                                muxer!!.writeSampleData(trackIndex, encoded, bufferInfo)
                            }
                            progressed = true
                            CodecPumpStep(
                                progressed = progressed,
                                outputEnded = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            )
                        } finally {
                            codec!!.releaseOutputBuffer(outputIndex, false)
                        }
                    }
                    else -> CodecPumpStep(progressed = progressed)
                }
            }

            check(framesQueued == frameCount) { "Encoder ended before all still frames were queued" }
            check(muxerStarted) { "Encoder finished without a muxer track" }
            // Do not expose the output until finalization succeeds. In particular,
            // stop() flushes muxer metadata and can itself fail for incomplete output.
            muxer.stop()
            muxerStarted = false
            val result = requireNotNull(outFile)
            check(result.exists() && result.length() > 0L) { "Muxer produced an empty output file" }
            completed = true
            result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.e(TAG, "encodeStillImage failed", e)
            null
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            if (muxerStarted) try { muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            if (frame != null && frame !== bitmap) try { frame?.recycle() } catch (_: Exception) {}
            try { kbBitmap?.recycle() } catch (_: Exception) {}
            if (!completed) try { outFile?.delete() } catch (_: Exception) {}
        }
    }

    /**
     * ARGB → NV12 (YUV420 semi-planar) byte array: Y plane followed by interleaved U,V.
     * Pure integer math; ~10ms for a 1080p frame on modern phones.
     */
    fun argbToNv12(bitmap: Bitmap, width: Int, height: Int): ByteArray {
        require(width > 0 && height > 0 && width % 2 == 0 && height % 2 == 0) {
            "NV12 requires positive even dimensions"
        }
        require(bitmap.width >= width && bitmap.height >= height) {
            "Bitmap must be at least the requested dimensions"
        }
        val frameSizeLong = width.toLong() * height.toLong()
        require(frameSizeLong <= Int.MAX_VALUE.toLong() * 2L / 3L) {
            "Bitmap dimensions are too large for an NV12 byte array"
        }
        val frameSize = frameSizeLong.toInt()
        val yuvSize = frameSizeLong + frameSizeLong / 2L
        require(yuvSize <= Int.MAX_VALUE) { "NV12 output is too large" }

        val argb = IntArray(frameSize)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)
        val yuv = ByteArray(yuvSize.toInt())
        var yIndex = 0
        var uvIndex = frameSize
        for (j in 0 until height) {
            for (i in 0 until width) {
                val c = argb[j * width + i]
                val r = (c ushr 16) and 0xff
                val g = (c ushr 8) and 0xff
                val b = c and 0xff
                // BT.601
                val y = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255)
                yuv[yIndex++] = y.toByte()
                if (j % 2 == 0 && i % 2 == 0) {
                    val u = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255)
                    val v = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255)
                    // NV12 = Y plane, then interleaved U,V (NV21 would be V,U).
                    yuv[uvIndex++] = u.toByte()
                    yuv[uvIndex++] = v.toByte()
                }
            }
        }
        return yuv
    }
}
