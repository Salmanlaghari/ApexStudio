package com.apexstudio.app.data.background

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.apexstudio.app.data.packs.download.PackManager
import com.apexstudio.app.data.photoedit.PhotoVideoEncoder
import com.apexstudio.app.domain.model.BackgroundReplaceSettings
import com.google.mediapipe.framework.image.BitmapExtractor
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Background replace engine (PR-G): MediaPipe person segmentation.
 *
 * Flow: decode the clip → segment every frame with the official
 * MediaPipe Tasks [ImageSegmenter] (selfie-segmentation model) →
 * composite (blur / colour / image) → re-encode to a new MP4 with
 * the original audio passed through untouched.
 *
 * ## Why pre-render, not a live GL effect
 * The preview player in this app does NOT apply `GlEffect`s
 * (`ExoPlayer.setVideoEffects` is never called — `currentEffects`
 * in EditorScreen is built but unused; the preview is a plain
 * player + Compose overlays). A live GL segmentation effect would
 * therefore be export-only, i.e. exactly the preview/export
 * divergence this feature must not have. Pre-rendering one
 * processed file and pointing the clip at it makes preview and
 * export read the *same pixels by construction*: the preview
 * plays the processed file, and the export exports it.
 *
 * ## Model delivery (PR-C "Blake")
 * The segmentation model (~244 KB) downloads on demand via
 * [PackManager] and is never bundled in the APK. It is loaded
 * into MediaPipe with `BaseOptions.setModelAssetBuffer` (a
 * `ByteBuffer`), which is what lets a PackManager-downloaded
 * file — not an asset — feed the segmenter.
 *
 * ## Honest limits (NOT VERIFIED on-device — see PR description)
 * - CPU delegate only: the GPU delegate has a history of wrong
 *   masks / crashes on some devices, so CPU is the deterministic
 *   choice. Slower, but correct.
 * - The model sees a square 256×256 resize of each frame; the
 *   mask is stretched back to the frame aspect. Edges get a
 *   bilinear + smoothstep feather, but fine hair detail is
 *   limited by the model's 256×256 resolution.
 * - Processing is roughly real-time × 0.3–1× (decode + segment +
 *   composite + encode per frame). It runs off the UI thread
 *   with progress + cancellation.
 */
object BackgroundReplaceEngine {

    private const val TAG = "BgReplaceEngine"

    /** PackManager id for the MediaPipe selfie-segmentation model. */
    const val PACK_ID = "mediapipe-selfie-segmenter"

    /** On-disk model file name (URL basename — see `DownloadEngine.fileNameFor`). */
    const val MODEL_FILE = "selfie_segmenter.tflite"

    /** Model input size (selfie_segmenter is 256×256). */
    const val MODEL_INPUT_SIZE = 256

    private const val VIDEO_MIME_AVC = MediaFormat.MIMETYPE_VIDEO_AVC
    private const val I_FRAME_INTERVAL_S = 1
    private const val MAX_BITRATE = 40_000_000
    private const val MIN_BITRATE = 2_000_000
    private const val IMAGE_READER_MAX_IMAGES = 4
    private const val DECODER_TIMEOUT_US = 10_000L
    private const val IMAGE_WAIT_TIMEOUT_MS = 200L
    private const val IMAGE_WAIT_DEADLINE_MS = 10_000L
    private const val PROGRESS_EMIT_INTERVAL_MS = 250L
    private const val BG_IMAGE_MAX_DIM = 1920

    /** The downloaded model file, or null when not downloaded yet. */
    fun modelFile(context: Context): File =
        File(PackManager.packDir(context, PACK_ID), MODEL_FILE)

    /** True when the model pack is installed with the file on disk. */
    suspend fun isModelReady(context: Context): Boolean = withContext(Dispatchers.IO) {
        PackManager.isDownloaded(context, PACK_ID) && modelFile(context).exists()
    }

    /**
     * Deterministic output file for [clipId] + [settings]: identical
     * settings reuse the same file (no re-processing), any knob
     * change yields a new file. Lives in the app-specific external
     * files dir (`bg_replace/`) — no storage permission needed,
     * removed on uninstall.
     */
    fun outputFileFor(
        context: Context,
        clipId: String,
        settings: BackgroundReplaceSettings
    ): File {
        val dir = context.getExternalFilesDir("bg_replace")
            ?: File(context.filesDir, "bg_replace")
        dir.mkdirs()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(settings.cacheKey().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(12)
        return File(dir, "bg_${clipId}_$digest.mp4")
    }

    /**
     * Delete a processed file, but ONLY inside the `bg_replace` dir —
     * never delete an arbitrary path (the clip's [uri] could be a
     * user file when no processing has happened).
     */
    fun deleteProcessedFile(file: File) {
        try {
            if (file.parentFile?.name == "bg_replace" && file.exists()) file.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete processed file ${file.name}", e)
        }
    }

    /**
     * Process [sourceUri] with [settings] into [outputFile].
     * Writes to `<output>.tmp` and renames on success, so a
     * cancelled / failed run never leaves a half-written file
     * behind at the output path. The caller must delete the
     * output on cancellation if it created a temp file — this
     * function deletes the temp itself on any failure.
     *
     * @param onProgress 0..1, throttled to ~4 emissions/second.
     * @throws Exception with a user-readable message on failure.
     */
    suspend fun process(
        context: Context,
        sourceUri: String,
        settings: BackgroundReplaceSettings,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): File = withContext(Dispatchers.Default) {
        val safe = settings.sanitized()
        val appContext = context.applicationContext
        val model = modelFile(appContext)
        if (!model.exists()) throw IllegalStateException("Segmentation model not downloaded yet.")
        if (safe.mode == com.apexstudio.app.domain.model.BackgroundReplaceMode.IMAGE &&
            safe.imageUri.isNullOrBlank()
        ) {
            throw IllegalArgumentException("Pick a background image first.")
        }

        val tmp = File(outputFile.parentFile, outputFile.name + ".tmp")
        tmp.delete()
        outputFile.delete()

        var segmenter: ImageSegmenter? = null
        var extractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var reader: android.media.ImageReader? = null
        var compositor: FrameCompositor? = null
        var bgImage: Bitmap? = null
        try {
            // --- MediaPipe segmenter (CPU delegate, IMAGE mode). ---
            val modelBytes = model.readBytes()
            val modelBuffer = ByteBuffer.allocateDirect(modelBytes.size).apply {
                put(modelBytes)
                rewind()
            }
            val baseOptions = BaseOptions.builder()
                .setModelAssetBuffer(modelBuffer)
                .setDelegate(Delegate.CPU)
                .build()
            val options = ImageSegmenter.ImageSegmenterOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setOutputCategoryMask(false)
                .setOutputConfidenceMasks(true)
                .build()
            segmenter = ImageSegmenter.createFromOptions(appContext, options)
            val seg = segmenter!!

            // --- Probe: rotation + duration. ---
            val rotation = readRotation(appContext, sourceUri)

            // --- Video extractor. ---
            val ext = MediaExtractor()
            ext.setDataSource(appContext, Uri.parse(sourceUri), null)
            extractor = ext
            var videoTrack = -1
            var videoFormat: MediaFormat? = null
            for (i in 0 until ext.trackCount) {
                val f = ext.getTrackFormat(i)
                if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("video/")) {
                    videoTrack = i
                    videoFormat = f
                    break
                }
            }
            if (videoTrack == -1 || videoFormat == null) {
                throw IllegalArgumentException("No video track in this clip.")
            }
            val format = videoFormat
            ext.selectTrack(videoTrack)
            val srcW = format.getInteger(MediaFormat.KEY_WIDTH)
            val srcH = format.getInteger(MediaFormat.KEY_HEIGHT)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else {
                readDurationUs(appContext, sourceUri)
            }
            if (durationUs <= 0L) throw IllegalStateException("Could not read this video's duration.")
            val fps = if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                format.getInteger(MediaFormat.KEY_FRAME_RATE).coerceIn(1, 120)
            } else 30
            val mime = format.getString(MediaFormat.KEY_MIME)!!

            // Output dims: rotation is baked in (the processed file
            // carries no rotation metadata, so preview/export agree).
            val swapped = rotation == 90 || rotation == 270
            val outW = ((if (swapped) srcH else srcW) / 2) * 2
            val outH = ((if (swapped) srcW else srcH) / 2) * 2
            if (outW < 2 || outH < 2) throw IllegalStateException("Video frame too small.")

            // --- Audio extractor (passthrough, never re-encoded). ---
            // A `val` (not `var`): the local drain functions below
            // smart-cast it, which Kotlin forbids for captured `var`s.
            val aExt = MediaExtractor()
            audioExtractor = aExt
            val audioFormat: MediaFormat? = try {
                aExt.setDataSource(appContext, Uri.parse(sourceUri), null)
                var found: MediaFormat? = null
                for (i in 0 until aExt.trackCount) {
                    val f = aExt.getTrackFormat(i)
                    if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                        aExt.selectTrack(i)
                        found = f
                        break
                    }
                }
                found
            } catch (e: Exception) {
                Log.w(TAG, "No audio track (video-only output)", e)
                null
            }

            // --- Background image (IMAGE mode), decoded once. ---
            if (safe.mode == com.apexstudio.app.domain.model.BackgroundReplaceMode.IMAGE) {
                bgImage = loadBackgroundImage(appContext, safe.imageUri!!)
                    ?: throw IllegalArgumentException("Could not load the background image.")
            }
            compositor = FrameCompositor(outW, outH, safe, bgImage)
            val comp = compositor!!

            // --- Decoder → ImageReader (CPU YUV frames). ---
            val imageQueue = LinkedBlockingQueue<Image>()
            val imgReader = android.media.ImageReader.newInstance(
                srcW, srcH, ImageFormat.YUV_420_888, IMAGE_READER_MAX_IMAGES
            )
            reader = imgReader
            imgReader.setOnImageAvailableListener(
                { r ->
                    try {
                        r.acquireNextImage()?.let { imageQueue.offer(it) }
                    } catch (_: Exception) {
                    }
                },
                Handler(Looper.getMainLooper())
            )
            val dec = MediaCodec.createDecoderByType(mime)
            decoder = dec
            dec.configure(format, imgReader.surface, null, 0)
            dec.start()

            // --- Encoder + muxer. ---
            val bitrate = (outW.toLong() * outH * fps * 15L / 100L).toInt()
                .coerceIn(MIN_BITRATE, MAX_BITRATE)
            val encFormat = MediaFormat.createVideoFormat(VIDEO_MIME_AVC, outW, outH).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
                )
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_S)
            }
            val enc = MediaCodec.createEncoderByType(VIDEO_MIME_AVC)
            encoder = enc
            enc.configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            enc.start()
            val mx = MediaMuxer(tmp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mx

            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var muxerStarted = false
            val audioBuffer = ByteBuffer.allocate(256 * 1024)
            val audioInfo = MediaCodec.BufferInfo()
            var audioEos = false
            var lastProgressEmitMs = 0L

            fun writeAudioUpTo(limitUs: Long) {
                if (audioFormat == null || audioEos || !muxerStarted) return
                val ax = audioExtractor ?: return
                while (true) {
                    val pts = try { ax.sampleTime } catch (_: Exception) { -1L }
                    if (pts < 0L || pts > limitUs) return
                    audioBuffer.clear()
                    val n = try { ax.readSampleData(audioBuffer, 0) } catch (_: Exception) { -1 }
                    if (n < 0) {
                        audioEos = true
                        return
                    }
                    audioInfo.set(0, n, pts, ax.sampleFlags)
                    mx.writeSampleData(audioTrackIndex, audioBuffer, audioInfo)
                    ax.advance()
                }
            }

            /** Drain encoder output into the muxer. Returns true on EOS. */
            fun drainEncoder(endOfStream: Boolean): Boolean {
                var eosWaits = 0
                while (true) {
                    val info = MediaCodec.BufferInfo()
                    val outIndex = enc.dequeueOutputBuffer(
                        info, if (endOfStream) DECODER_TIMEOUT_US else 0L
                    )
                    when {
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            videoTrackIndex = mx.addTrack(enc.outputFormat)
                            if (audioFormat != null) {
                                audioTrackIndex = mx.addTrack(audioFormat)
                            }
                            mx.start()
                            muxerStarted = true
                        }
                        outIndex >= 0 -> {
                            val encoded = enc.getOutputBuffer(outIndex)
                            if (encoded != null) {
                                if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                    info.size = 0
                                }
                                if (info.size > 0 && muxerStarted) {
                                    writeAudioUpTo(info.presentationTimeUs)
                                    encoded.position(info.offset)
                                    encoded.limit(info.offset + info.size)
                                    mx.writeSampleData(videoTrackIndex, encoded, info)
                                }
                            }
                            enc.releaseOutputBuffer(outIndex, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                return true
                            }
                        }
                        // TRY_AGAIN_LATER (or other info codes): in the
                        // EOS drain keep waiting (bounded) — the encoder
                        // may need a moment to flush its last frames.
                        else -> {
                            if (!endOfStream || ++eosWaits > 10) return false
                        }
                    }
                }
            }

            fun feedEncoder(yuv: ByteArray, ptsUs: Long) {
                // The encoder input can be briefly unavailable under
                // load — retry instead of failing the whole run.
                var attempts = 0
                while (true) {
                    coroutineContext.ensureActive()
                    val inIndex = enc.dequeueInputBuffer(DECODER_TIMEOUT_US)
                    if (inIndex >= 0) {
                        val input = enc.getInputBuffer(inIndex)
                            ?: throw IllegalStateException("Video encoder input buffer is null.")
                        if (input.capacity() < yuv.size) {
                            throw IllegalStateException("Video encoder input buffer too small.")
                        }
                        input.clear()
                        input.put(yuv)
                        enc.queueInputBuffer(inIndex, 0, yuv.size, ptsUs, 0)
                        return
                    }
                    if (++attempts > 100) {
                        throw IllegalStateException("Video encoder is not accepting input.")
                    }
                }
            }

            // --- Main loop: decode → segment → composite → encode. ---
            var inputEos = false
            var decoderEos = false
            val decInfo = MediaCodec.BufferInfo()
            while (!decoderEos) {
                coroutineContext.ensureActive()
                if (!inputEos) {
                    val inIndex = dec.dequeueInputBuffer(DECODER_TIMEOUT_US)
                    if (inIndex >= 0) {
                        val input = dec.getInputBuffer(inIndex)
                        if (input != null) {
                            val n = ext.readSampleData(input, 0)
                            if (n < 0) {
                                dec.queueInputBuffer(
                                    inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputEos = true
                            } else {
                                dec.queueInputBuffer(inIndex, 0, n, ext.sampleTime, 0)
                                ext.advance()
                            }
                        }
                    }
                }
                val outIndex = dec.dequeueOutputBuffer(decInfo, DECODER_TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                    outIndex >= 0 -> {
                        val isEos = decInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        if (isEos) {
                            dec.releaseOutputBuffer(outIndex, false)
                            decoderEos = true
                        } else {
                            dec.releaseOutputBuffer(outIndex, true)
                            val image = waitForImage(imageQueue)
                                ?: throw IllegalStateException("Decoder produced no frame.")
                            try {
                                val frame = yuv420ToBitmap(image)
                                try {
                                    val oriented = rotateBitmapIfNeeded(frame, rotation, outW, outH)
                                    try {
                                        val mask = segmentFrame(seg, oriented)
                                        comp.composite(oriented, mask.first, mask.second, mask.third)
                                    } finally {
                                        if (oriented !== frame) {
                                            try { oriented.recycle() } catch (_: Exception) {}
                                        }
                                    }
                                } finally {
                                    try { frame.recycle() } catch (_: Exception) {}
                                }
                                val yuv = PhotoVideoEncoder.argbToNv12(comp.result, outW, outH)
                                feedEncoder(yuv, decInfo.presentationTimeUs)
                                drainEncoder(endOfStream = false)
                                val now = android.os.SystemClock.elapsedRealtime()
                                if (now - lastProgressEmitMs >= PROGRESS_EMIT_INTERVAL_MS) {
                                    lastProgressEmitMs = now
                                    onProgress(
                                        (decInfo.presentationTimeUs.toFloat() / durationUs)
                                            .coerceIn(0f, 1f)
                                    )
                                }
                            } finally {
                                image.close()
                            }
                        }
                    }
                    else -> Unit // TRY_AGAIN_LATER: keep looping
                }
            }

            // --- Flush encoder + remaining audio. ---
            val eosIndex = enc.dequeueInputBuffer(DECODER_TIMEOUT_US)
            if (eosIndex >= 0) {
                enc.queueInputBuffer(
                    eosIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
            }
            drainEncoder(endOfStream = true)
            if (!muxerStarted) throw IllegalStateException("No video frames were encoded.")
            writeAudioUpTo(Long.MAX_VALUE)
            onProgress(1f)

            // --- Finish: stop muxer, rename temp → output. ---
            try { mx.stop() } catch (e: Exception) { Log.w(TAG, "Muxer stop failed", e) }
            if (!tmp.exists() || tmp.length() == 0L) {
                throw IllegalStateException("Encoding produced an empty file.")
            }
            if (!tmp.renameTo(outputFile)) {
                throw IllegalStateException("Could not save the processed video.")
            }
            outputFile
        } catch (e: Exception) {
            // Never leave a half-written temp file behind.
            try { tmp.delete() } catch (_: Exception) {}
            throw e
        } finally {
            try { compositor?.release() } catch (_: Exception) {}
            try { bgImage?.recycle() } catch (_: Exception) {}
            try { decoder?.stop() } catch (_: Exception) {}
            try { decoder?.release() } catch (_: Exception) {}
            try { encoder?.stop() } catch (_: Exception) {}
            try { encoder?.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            try { reader?.close() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
            try { audioExtractor?.release() } catch (_: Exception) {}
            try { segmenter?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Wait for the next decoded [Image] from the reader queue.
     * Returns null after a generous deadline (decoder stall).
     */
    private fun waitForImage(queue: LinkedBlockingQueue<Image>): Image? {
        val deadline = System.currentTimeMillis() + IMAGE_WAIT_DEADLINE_MS
        while (System.currentTimeMillis() < deadline) {
            val img = queue.poll(IMAGE_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (img != null) return img
        }
        return null
    }

    /**
     * Segment [frame] with MediaPipe. Returns (confidence FloatArray,
     * mask width, mask height). The model input is a square resize;
     * the returned mask aligns with that square input.
     */
    private fun segmentFrame(
        segmenter: ImageSegmenter,
        frame: Bitmap
    ): Triple<FloatArray, Int, Int> {
        val input = Bitmap.createScaledBitmap(frame, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, true)
        val mpImage = BitmapImageBuilder(input).build()
        try {
            val result = segmenter.segment(mpImage)
            val maskMp = result.confidenceMasks().orElse(null)?.firstOrNull()
                ?: throw IllegalStateException("Segmenter returned no confidence mask.")
            try {
                val w = maskMp.width
                val h = maskMp.height
                if (w <= 0 || h <= 0) throw IllegalStateException("Segmenter returned an empty mask.")
                return Triple(maskToFloatArray(maskMp, w, h), w, h)
            } finally {
                try { maskMp.close() } catch (_: Exception) {}
            }
        } finally {
            try { mpImage.close() } catch (_: Exception) {}
            try { input.recycle() } catch (_: Exception) {}
        }
    }

    /**
     * Read a confidence-mask [MPImage] as floats. The native format
     * is VEC32F1 (float32 per pixel); anything else falls back to a
     * bitmap read (red channel / 255).
     */
    private fun maskToFloatArray(maskMp: MPImage, w: Int, h: Int): FloatArray {
        val format = maskMp.getContainedImageProperties().firstOrNull()?.imageFormat
        if (format == MPImage.IMAGE_FORMAT_VEC32F1) {
            val buffer = ByteBufferExtractor.extract(maskMp)
            val dup = buffer.duplicate().order(ByteOrder.nativeOrder())
            dup.rewind()
            val floats = dup.asFloatBuffer()
            if (floats.remaining() < w * h) {
                throw IllegalStateException("Segmentation mask buffer too small.")
            }
            val out = FloatArray(w * h)
            floats.get(out)
            return out
        }
        // Fallback: bitmap read (grayscale in the red channel).
        val bmp = BitmapExtractor.extract(maskMp)
        try {
            val px = IntArray(w * h)
            bmp.getPixels(px, 0, w, 0, 0, w, h)
            return FloatArray(w * h) { ((px[it] ushr 16) and 0xff) / 255f }
        } finally {
            try { bmp.recycle() } catch (_: Exception) {}
        }
    }

    /** YUV_420_888 [Image] → ARGB bitmap (BT.601 limited-range, integer math). */
    private fun yuv420ToBitmap(image: Image): Bitmap {
        val w = image.width
        val h = image.height
        val planes = image.planes
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]
        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer
        val yRowStride = yPlane.rowStride
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride
        val argb = IntArray(w * h)
        for (y in 0 until h) {
            val yRow = yRowStride * y
            val uvRow = y / 2
            val uRow = uRowStride * uvRow
            val vRow = vRowStride * uvRow
            for (x in 0 until w) {
                val yy = yBuf.get(yRow + x).toInt() and 0xff
                val u = (uBuf.get(uRow + (x / 2) * uPixelStride).toInt() and 0xff) - 128
                val v = (vBuf.get(vRow + (x / 2) * vPixelStride).toInt() and 0xff) - 128
                val c = yy - 16
                var r = (298 * c + 409 * v + 128) shr 8
                var g = (298 * c - 100 * u - 208 * v + 128) shr 8
                var b = (298 * c + 516 * u + 128) shr 8
                r = r.coerceIn(0, 255)
                g = g.coerceIn(0, 255)
                b = b.coerceIn(0, 255)
                argb[y * w + x] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return Bitmap.createBitmap(argb, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Rotate [frame] by [rotation] degrees (0/90/180/270 from the
     * source metadata) into the output orientation. Returns [frame]
     * itself when no rotation is needed; otherwise a new bitmap
     * (caller recycles it, not the input).
     */
    private fun rotateBitmapIfNeeded(
        frame: Bitmap,
        rotation: Int,
        outW: Int,
        outH: Int
    ): Bitmap {
        if (rotation == 0) return frame
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, matrix, true)
            .also {
                check(it.width == outW && it.height == outH) {
                    "Rotated frame ${it.width}×${it.height} != expected ${outW}×${outH}"
                }
            }
    }

    private fun readRotation(context: Context, uri: String): Int {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(uri))
            return retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
            )?.toIntOrNull() ?: 0
        } catch (_: Exception) {
            return 0
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun readDurationUs(context: Context, uri: String): Long {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(uri))
            val ms = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: return -1L
            return ms * 1000L
        } catch (_: Exception) {
            return -1L
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    /** Decode a background image with sampling (cap [BG_IMAGE_MAX_DIM]). */
    private fun loadBackgroundImage(context: Context, uri: String): Bitmap? {
        return try {
            val parsed = Uri.parse(uri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(parsed)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / sample > BG_IMAGE_MAX_DIM ||
                bounds.outHeight / sample > BG_IMAGE_MAX_DIM
            ) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(parsed)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Background image load failed", e)
            null
        }
    }
}
