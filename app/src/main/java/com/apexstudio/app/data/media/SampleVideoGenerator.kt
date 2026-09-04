package com.apexstudio.app.data.media

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object SampleVideoGenerator {
    private const val TAG = "SampleVideoGenerator"
    private const val SAMPLE_FILE_NAME = "apex_sample_cinematic.mp4"
    private const val WIDTH = 1280
    private const val HEIGHT = 720
    private const val FRAME_RATE = 30
    private const val DURATION_SECONDS = 10
    private const val TOTAL_FRAMES = FRAME_RATE * DURATION_SECONDS
    private const val BIT_RATE = 2_500_000

    suspend fun getOrCreateSampleVideo(context: Context): String = withContext(Dispatchers.IO) {
        val targetFile = File(context.filesDir, SAMPLE_FILE_NAME)
        if (targetFile.exists() && targetFile.length() > 10_000) {
            return@withContext Uri.fromFile(targetFile).toString()
        }

        try {
            generateVideo(targetFile)
            if (targetFile.exists() && targetFile.length() > 10_000) {
                Log.d(TAG, "Successfully generated sample video: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
                return@withContext Uri.fromFile(targetFile).toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate sample video", e)
        }
        Uri.fromFile(targetFile).toString()
    }

    private fun generateVideo(outputFile: File) {
        if (outputFile.exists()) {
            outputFile.delete()
        }

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, WIDTH, HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = encoder.createInputSurface()
        encoder.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false

        val bufferInfo = MediaCodec.BufferInfo()

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 54f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00F0FF")
            textSize = 34f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFCC00")
            textSize = 72f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)

        try {
            for (frameIndex in 0 until TOTAL_FRAMES) {
                // Drain encoder output
                drainEncoder(encoder, muxer, bufferInfo, muxerStarted) { index ->
                    trackIndex = index
                    muxer.start()
                    muxerStarted = true
                }

                // Render frame to inputSurface
                val canvas: Canvas = inputSurface.lockHardwareCanvas()
                try {
                    val progress = frameIndex.toFloat() / TOTAL_FRAMES
                    val timeSec = frameIndex / FRAME_RATE
                    val timeMilli = (frameIndex % FRAME_RATE) * 1000 / FRAME_RATE

                    // Background dynamic color wave
                    val r = (18 + 25 * Math.sin(progress * Math.PI * 2)).toInt().coerceIn(0, 255)
                    val g = (24 + 30 * Math.sin(progress * Math.PI * 2 + 1.0)).toInt().coerceIn(0, 255)
                    val b = (45 + 40 * Math.cos(progress * Math.PI * 2)).toInt().coerceIn(0, 255)
                    canvas.drawColor(Color.rgb(r, g, b))

                    // Center card
                    shapePaint.color = Color.argb(180, 15, 23, 42)
                    val cardRect = RectF(140f, 90f, WIDTH - 140f, HEIGHT - 90f)
                    canvas.drawRoundRect(cardRect, 32f, 32f, shapePaint)

                    // Card border
                    shapePaint.style = Paint.Style.STROKE
                    shapePaint.strokeWidth = 4f
                    shapePaint.color = Color.parseColor("#00F0FF")
                    canvas.drawRoundRect(cardRect, 32f, 32f, shapePaint)
                    shapePaint.style = Paint.Style.FILL

                    // Title
                    canvas.drawText("APEX STUDIO", (WIDTH / 2).toFloat(), 200f, textPaint)
                    canvas.drawText("Media3 Transformer Video Clip", (WIDTH / 2).toFloat(), 260f, subPaint)

                    // Big timecode
                    val timecode = String.format("%02d:%02d.%02d", timeSec / 60, timeSec % 60, timeMilli / 10)
                    canvas.drawText(timecode, (WIDTH / 2).toFloat(), 380f, timePaint)

                    // Moving visual object (proves motion and playback)
                    val ballX = 220f + (WIDTH - 440f) * (0.5f + 0.5f * Math.sin(progress * Math.PI * 4).toFloat())
                    val ballY = 480f + 35f * Math.cos(progress * Math.PI * 6).toFloat()
                    shapePaint.color = Color.parseColor("#E02475")
                    canvas.drawCircle(ballX, ballY, 32f, shapePaint)
                    shapePaint.color = Color.parseColor("#00F0FF")
                    canvas.drawCircle(ballX, ballY, 16f, shapePaint)

                    // Progress track bar
                    val barY = 570f
                    shapePaint.color = Color.argb(120, 255, 255, 255)
                    canvas.drawRoundRect(RectF(220f, barY, WIDTH - 220f, barY + 16f), 8f, 8f, shapePaint)
                    shapePaint.color = Color.parseColor("#00F0FF")
                    canvas.drawRoundRect(RectF(220f, barY, 220f + (WIDTH - 440f) * progress, barY + 16f), 8f, 8f, shapePaint)

                    // Subtitle footer
                    shapePaint.color = Color.parseColor("#94A3B8")
                    shapePaint.textSize = 24f
                    shapePaint.textAlign = Paint.Align.CENTER
                    canvas.drawText("Set Start & End Points to Trim • 10.0s Clip", (WIDTH / 2).toFloat(), 620f, shapePaint)
                } finally {
                    inputSurface.unlockCanvasAndPost(canvas)
                }

                // Feed frame timestamp in nanoseconds
                val ptsNs = (frameIndex * 1_000_000_000L / FRAME_RATE)
                // Hardware surface internally manages PTS or we let bufferInfo carry it
            }

            encoder.signalEndOfInputStream()

            // Drain remaining output buffers
            drainEncoder(encoder, muxer, bufferInfo, muxerStarted, endOfStream = true) { index ->
                trackIndex = index
                muxer.start()
                muxerStarted = true
            }
        } finally {
            try { encoder.stop() } catch (_: Exception) {}
            try { encoder.release() } catch (_: Exception) {}
            try { inputSurface.release() } catch (_: Exception) {}
            if (muxerStarted) {
                try { muxer.stop() } catch (_: Exception) {}
            }
            try { muxer.release() } catch (_: Exception) {}
        }
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        muxerStarted: Boolean,
        endOfStream: Boolean = false,
        onMuxerStart: (Int) -> Unit
    ) {
        val timeoutUs = if (endOfStream) 10_000L else 0L
        var localMuxerStarted = muxerStarted

        while (true) {
            val status = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
            } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (localMuxerStarted) {
                    throw RuntimeException("Format changed twice")
                }
                val newFormat = encoder.outputFormat
                val track = muxer.addTrack(newFormat)
                onMuxerStart(track)
                localMuxerStarted = true
            } else if (status >= 0) {
                val encodedData = encoder.getOutputBuffer(status)
                    ?: throw RuntimeException("Output buffer $status was null")

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    bufferInfo.size = 0
                }

                if (bufferInfo.size != 0 && localMuxerStarted) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    muxer.writeSampleData(0, encodedData, bufferInfo)
                }

                encoder.releaseOutputBuffer(status, false)

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            }
        }
    }
}
