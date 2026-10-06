package com.apexstudio.app.data.photoedit

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * Encodes a single edited photo bitmap as a short H.264 video so the
 * existing Media3 Transformer export pipeline (which only accepts
 * video inputs) can export photo clips with text overlays, stickers,
 * transitions, etc. exactly like video clips.
 *
 * The bitmap is repeated for [durationMs] at a low frame rate — it is
 * a still image, so 10 fps keeps the encode fast and the file small;
 * the Transformer re-times to the export fps anyway.
 */
object PhotoVideoEncoder {

    private const val TAG = "PhotoVideoEncoder"
    private const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC
    private const val FRAME_RATE = 10
    private const val I_FRAME_INTERVAL_S = 1
    // Bitrate scales with resolution (~0.5 bits/pixel/frame at 10fps) to avoid
    // generation loss in the intermediate before Transformer re-encodes.
    private fun bitrateFor(width: Int, height: Int): Int =
        (width.toLong() * height.toLong() * FRAME_RATE / 2L).toInt().coerceAtLeast(2_000_000)

    /**
     * Encode [bitmap] into a temp .mp4 in the app cache dir lasting
     * [durationMs]. Returns the file, or null on failure.
     */
    suspend fun encodeStillImage(
        context: Context,
        bitmap: Bitmap,
        durationMs: Long
    ): File? = withContext(Dispatchers.Default) {
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        try {
            // YUV420 needs even dimensions.
            val width = (bitmap.width / 2) * 2
            val height = (bitmap.height / 2) * 2
            if (width < 2 || height < 2) return@withContext null
            val frame = if (bitmap.width != width || bitmap.height != height) {
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

            val outFile = File(context.cacheDir, "photo_clip_${System.currentTimeMillis()}.mp4")
            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var trackIndex = -1
            var muxerStarted = false

            val safeDurationMs = durationMs.coerceAtLeast(1000L)
            val frameCount = ((safeDurationMs * FRAME_RATE) / 1000L).toInt().coerceAtLeast(FRAME_RATE)
            val yuv = argbToNv12(frame, width, height)
            val bufferInfo = MediaCodec.BufferInfo()

            var framesQueued = 0
            var eosQueued = false
            var iterations = 0
            // Safety cap: frames + 10% proportional drain slack (min 100).
            // The loop always ends at BUFFER_FLAG_END_OF_STREAM; the cap
            // only guards against a misbehaving codec spinning forever.
            val maxIterations = frameCount + (frameCount / 10).coerceAtLeast(100)
            while (iterations++ < maxIterations) {
                // Feed input.
                if (!eosQueued) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inIndex)!!
                        inputBuffer.clear()
                        if (framesQueued < frameCount) {
                            inputBuffer.put(yuv)
                            val ptsUs = framesQueued * 1_000_000L / FRAME_RATE
                            codec.queueInputBuffer(
                                inIndex, 0, yuv.size, ptsUs,
                                if (framesQueued == frameCount - 1) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                            )
                            if (framesQueued == frameCount - 1) eosQueued = true
                            framesQueued++
                        } else {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            eosQueued = true
                        }
                    }
                }
                // Drain output.
                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outIndex >= 0 -> {
                        val encoded = codec.getOutputBuffer(outIndex)!!
                        // Codec-config data was already consumed via
                        // addTrack; never write it as a sample.
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size > 0 && muxerStarted) {
                            encoded.position(bufferInfo.offset)
                            encoded.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, encoded, bufferInfo)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
                if (eosQueued && outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // Keep draining until EOS arrives.
                }
            }

            if (frame != bitmap) frame.recycle()
            outFile.takeIf { it.exists() && it.length() > 0L }
        } catch (e: Exception) {
            Log.e(TAG, "encodeStillImage failed", e)
            null
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            try { muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    /**
     * ARGB → NV12 (YUV420 semi-planar) byte array. Pure integer math;
     * ~10ms for a 1080p frame on modern phones.
     */
    fun argbToNv12(bitmap: Bitmap, width: Int, height: Int): ByteArray {
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)
        val yuv = ByteArray(width * height * 3 / 2)
        val frameSize = width * height
        var yIndex = 0
        var uvIndex = frameSize
        for (j in 0 until height) {
            for (i in 0 until width) {
                val c = argb[j * width + i]
                val r = (c ushr 16) and 0xff
                val g = (c ushr 8) and 0xff
                val b = c and 0xff
                // BT.601
                var y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                y = y.coerceIn(0, 255)
                yuv[yIndex++] = y.toByte()
                if (j % 2 == 0 && i % 2 == 0) {
                    var u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    var v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    u = u.coerceIn(0, 255)
                    v = v.coerceIn(0, 255)
                    // NV12 = Y plane, then interleaved VU.
                    yuv[uvIndex++] = v.toByte()
                    yuv[uvIndex++] = u.toByte()
                }
            }
        }
        return yuv
    }
}
