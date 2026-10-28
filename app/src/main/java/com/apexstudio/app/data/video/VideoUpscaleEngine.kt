package com.apexstudio.app.data.video

import android.content.Context
import android.graphics.Bitmap
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
import android.os.HandlerThread
import android.os.StatFs
import android.util.Log
import com.apexstudio.app.ai.onnx.EspcnSuperResolutionAdapter
import com.apexstudio.app.ai.LocalMediaSourcePolicy
import com.apexstudio.app.ai.safety.GlobalModerationEnforcement
import com.apexstudio.app.data.photoedit.PhotoVideoEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

/** Bounded, offline x3 ESPCN video renderer. This is learned upscaling, not 4K restoration. */
object VideoUpscaleEngine {
    const val MAX_INPUT_WIDTH = 640
    const val MAX_INPUT_HEIGHT = 360
    const val MAX_DURATION_MS = 30_000L
    const val SCALE = 3
    private const val MAX_OUTPUT_WIDTH = 1920
    private const val MAX_OUTPUT_HEIGHT = 1080
    private const val MAX_OUTPUT_PIXELS = 2_073_600L
    private const val MAX_PROCESSING_MS = 30 * 60 * 1000L
    private const val MAX_AUDIO_SAMPLE_BYTES = 8 * 1024 * 1024
    private const val MAX_FPS = 30
    private const val MAX_FRAMES = 900L
    private const val MIME_AVC = MediaFormat.MIMETYPE_VIDEO_AVC
    private const val CODEC_TIMEOUT_US = 10_000L
    private const val IMAGE_DEADLINE_MS = 10_000L
    private const val TAG = "VideoUpscaleEngine"
    private val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "VideoUpscale").apply { isDaemon = true } }.asCoroutineDispatcher()

    /** Apply backpressure instead of silently losing frames when model inference is slower than decode. */
    internal fun <T> enqueueFrameWithoutDropping(
        queue: BlockingQueue<T>,
        frame: T,
        stopping: AtomicBoolean
    ): Boolean {
        while (!stopping.get()) {
            if (queue.offer(frame, 100, TimeUnit.MILLISECONDS)) return true
        }
        return false
    }

    data class SourceInfo(val width: Int, val height: Int, val durationMs: Long, val rotation: Int, val frameRate: Int, val durationUs: Long = durationMs * 1000L) {
        val outputWidth: Int get() = (if (rotation == 90 || rotation == 270) height else width) * SCALE
        val outputHeight: Int get() = (if (rotation == 90 || rotation == 270) width else height) * SCALE
    }

    fun sourceDurationBlockReason(durationMs: Long): String? {
        if (durationMs <= MAX_DURATION_MS) return null
        val totalSeconds = durationMs / 1000L
        val clock = java.lang.String.format(java.util.Locale.US, "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L)
        val duration = if (durationMs % 1000L == 0L) clock else clock +
            java.lang.String.format(java.util.Locale.US, ".%03d", durationMs % 1000L)
        return "This video is $duration long. Choose a source video of 00:30 or shorter; a timeline trim will not shorten the source."
    }

    /** Validates practical device bounds before allocating a model session or large frame buffers. */
    fun validateSource(width: Int, height: Int, durationMs: Long, rotation: Int = 0, frameRate: Int = 30): SourceInfo {
        require(width >= 2 && height >= 2 && width % 2 == 0 && height % 2 == 0) { "Video must have even dimensions of at least 2×2." }
        val landscapeOrSmallFrame = width <= MAX_INPUT_WIDTH && height <= MAX_INPUT_HEIGHT
        val portraitOrSmallFrame = width <= MAX_INPUT_HEIGHT && height <= MAX_INPUT_WIDTH
        require(landscapeOrSmallFrame || portraitOrSmallFrame) {
            "AI Video Upscale supports source frames up to 640×360 only (portrait up to 360×640)."
        }
        require(durationMs in 1..MAX_DURATION_MS) { "AI Video Upscale supports videos up to 30 seconds." }
        require(frameRate in 1..MAX_FPS) { "AI Video Upscale supports up to 30 frames per second." }
        require((durationMs * frameRate + 999L) / 1000L <= MAX_FRAMES) { "Video exceeds the 900-frame processing limit." }
        require(rotation in setOf(0, 90, 180, 270)) { "Unsupported video rotation metadata." }
        val info = SourceInfo(width, height, durationMs, rotation, frameRate)
        require(maxOf(info.outputWidth, info.outputHeight) <= MAX_OUTPUT_WIDTH &&
            minOf(info.outputWidth, info.outputHeight) <= MAX_OUTPUT_HEIGHT &&
            info.outputWidth.toLong() * info.outputHeight <= MAX_OUTPUT_PIXELS) {
            "ESPCN x3 output exceeds 1920×1080 landscape / 1080×1920 portrait processing limits."
        }
        return info
    }

    /** No silent audio loss or implicit format conversion. AAC is the supported MP4 passthrough. */
    @Suppress("UNUSED_PARAMETER")
    internal fun validateAudioPassthrough(mime: String?, audioDurationUs: Long?, videoDurationMs: Long) {
        require(mime == MediaFormat.MIMETYPE_AUDIO_AAC) {
            "AI Video Upscale supports AAC audio or silent video only. Convert this source audio to AAC first."
        }
        // AAC duration mismatch is handled by dropping whole packets at the output video boundary.
    }

    /** AAC packets are indivisible during passthrough; never mux a packet that would cross video EOS. */
    internal fun audioPacketEndsWithinVideo(ptsUs: Long, packetDurationUs: Long, videoDurationUs: Long): Boolean =
        ptsUs >= 0L && packetDurationUs > 0L && ptsUs < videoDurationUs && packetDurationUs <= videoDurationUs - ptsUs

    /** AAC packet duration, including common high-efficiency and low-delay profiles. */
    internal fun aacPacketDurationUs(sampleRate: Int, profile: Int?): Long? {
        if (sampleRate <= 0) return null
        val samplesPerPacket = when (profile) {
            MediaCodecInfo.CodecProfileLevel.AACObjectHE,
            MediaCodecInfo.CodecProfileLevel.AACObjectHE_PS -> 2048
            MediaCodecInfo.CodecProfileLevel.AACObjectELD -> 512
            else -> 1024
        }
        return (samplesPerPacket * 1_000_000L + sampleRate - 1L) / sampleRate
    }

    /** Render entire media source to a unique temporary sibling and atomically publish on success. */
    suspend fun process(
        context: Context,
        sourceUri: String,
        outputFile: File,
        onProgress: (Float) -> Unit = {},
        allowPublish: () -> Boolean
    ): File = withContext(dispatcher) {
        val startedAtMs = android.os.SystemClock.elapsedRealtime()
        val app = context.applicationContext
        val source = Uri.parse(sourceUri)
        require(LocalMediaSourcePolicy.accepts(source.scheme, source.authority)) {
            "AI Video Upscale only accepts local project media, not a network URL."
        }
        val (mime, format, info) = probe(app, sourceUri)
        outputFile.parentFile?.mkdirs()
        require(!outputFile.exists()) { "Refusing to overwrite an existing processed video." }
        val outputEstimateBytes = (info.outputWidth.toLong() * info.outputHeight * info.frameRate * 0.12 * info.durationMs / 1000.0 / 8.0).toLong() + 32L * 1024L * 1024L
        require(StatFs(outputFile.parentFile!!.absolutePath).availableBytes >= outputEstimateBytes) {
            "Not enough free storage for the processed video (need about ${outputEstimateBytes / (1024 * 1024)} MiB)."
        }
        val tmp = File(outputFile.parentFile, "${outputFile.name}.${UUID.randomUUID()}.tmp.mp4")
        val adapter = EspcnSuperResolutionAdapter.fromBundledAssets(app)
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var audioExtractor: MediaExtractor? = null
        var videoExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var reader: android.media.ImageReader? = null
        var frameThread: HandlerThread? = null
        var pendingImages: LinkedBlockingQueue<Image>? = null
        val stoppingFrameListener = AtomicBoolean(false)
        var modelSession: ai.onnxruntime.OrtSession? = null
        var published = false
        try {
            modelSession = adapter.openValidatedVideoSession()
            val ext = MediaExtractor().also { videoExtractor = it; it.setDataSource(app, Uri.parse(sourceUri), null) }
            var videoTrack = -1
            for (i in 0 until ext.trackCount) {
                if ((ext.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: "").startsWith("video/")) { videoTrack = i; break }
            }
            check(videoTrack >= 0) { "No video track in the selected media." }
            ext.selectTrack(videoTrack)

            val imageQueue = LinkedBlockingQueue<Image>(2)
            pendingImages = imageQueue
            // One image may be consumed by inference, two wait in the queue, and the listener may
            // hold one while waiting for queue space. Backpressure the decoder rather than dropping.
            val imgReader = android.media.ImageReader.newInstance(info.width, info.height, ImageFormat.YUV_420_888, 4)
            reader = imgReader
            val thread = HandlerThread("VideoUpscaleFrames").apply { start() }
            frameThread = thread
            imgReader.setOnImageAvailableListener({ r ->
                var image: Image? = null
                try {
                    image = r.acquireNextImage()
                    if (image != null && enqueueFrameWithoutDropping(imageQueue, image!!, stoppingFrameListener)) image = null
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                } catch (_: Exception) {
                    // Reader shutdown may race a callback; the main processing path detects any
                    // missing frame and fails rather than publishing a silently incomplete video.
                } finally {
                    image?.close()
                }
            }, Handler(thread.looper))

            val dec = MediaCodec.createDecoderByType(mime).also { decoder = it }
            format.setInteger(MediaFormat.KEY_ROTATION, 0) // rotation is baked exactly once by rotate(frame, metadataRotation).
            dec.configure(format, imgReader.surface, null, 0)
            dec.start()

            val outW = info.outputWidth
            val outH = info.outputHeight
            val fps = info.frameRate
            val bitrate = (outW.toLong() * outH * fps * 12L / 100L).coerceIn(1_500_000L, 18_000_000L).toInt()
            val encFormat = MediaFormat.createVideoFormat(MIME_AVC, outW, outH).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT601_NTSC)
                setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
                setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
            }
            val enc = MediaCodec.createEncoderByType(MIME_AVC).also { encoder = it }
            enc.configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            enc.start()
            val mx = MediaMuxer(tmp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).also { muxer = it }

            val audioExt = MediaExtractor().also { audioExtractor = it }
            var audioFormat: MediaFormat? = null
            audioExt.setDataSource(app, source, null)
            for (i in 0 until audioExt.trackCount) {
                val f = audioExt.getTrackFormat(i)
                if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                    audioExt.selectTrack(i); audioFormat = f; break
                }
            }

            var videoOut = -1
            var audioOut = -1
            var muxStarted = false
            var audioEos = false
            val audioTrackDurationUs = audioFormat?.let { if (it.containsKey(MediaFormat.KEY_DURATION)) it.getLong(MediaFormat.KEY_DURATION) else null }
            val nominalAudioPacketDurationUs = audioFormat?.let {
                val sampleRate = if (it.containsKey(MediaFormat.KEY_SAMPLE_RATE)) it.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 0
                val profile = if (it.containsKey(MediaFormat.KEY_AAC_PROFILE)) it.getInteger(MediaFormat.KEY_AAC_PROFILE) else null
                aacPacketDurationUs(sampleRate, profile)
            }
            val declaredAudioBufferSize = if (audioFormat?.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE) == true)
                audioFormat!!.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(1) else 256 * 1024
            require(declaredAudioBufferSize <= MAX_AUDIO_SAMPLE_BYTES) { "Audio packets exceed the safe passthrough buffer limit." }
            val audioBuffer = ByteBuffer.allocate(maxOf(256 * 1024, declaredAudioBufferSize))
            val audioInfo = MediaCodec.BufferInfo()
            fun copyAudioUntil(timeUs: Long) {
                if (!muxStarted || audioFormat == null || audioEos) return
                while (true) {
                    val pts = try { audioExt.sampleTime } catch (_: Exception) { -1L }
                    if (pts < 0L || pts >= info.durationUs) { audioEos = true; return }
                    if (pts > timeUs) return
                    audioBuffer.clear()
                    val size = audioExt.readSampleData(audioBuffer, 0)
                    if (size < 0) { audioEos = true; return }
                    check(size <= audioBuffer.capacity()) { "Audio packet exceeds its declared passthrough buffer size." }
                    val flags = audioExt.sampleFlags
                    val advanced = audioExt.advance()
                    val nextPts = if (advanced) audioExt.sampleTime else -1L
                    // MediaMuxer cannot represent a partial AAC frame. Use the next packet boundary
                    // when available and the nominal AAC frame duration for the terminal packet;
                    // track duration metadata can include encoder padding/edit-list trimming that
                    // a passthrough mux does not preserve.
                    val packetDurationUs = when {
                        nextPts > pts -> maxOf(nextPts - pts, nominalAudioPacketDurationUs ?: 0L)
                        else -> nominalAudioPacketDurationUs
                            ?: audioTrackDurationUs?.takeIf { it > pts }?.minus(pts)
                    }
                    if (packetDurationUs == null || !audioPacketEndsWithinVideo(pts, packetDurationUs, info.durationUs)) {
                        audioEos = true
                        return
                    }
                    audioInfo.set(0, size, pts, flags)
                    mx.writeSampleData(audioOut, audioBuffer, audioInfo)
                }
            }
            fun drainEncoder(end: Boolean): Boolean {
                var waits = 0
                while (true) {
                    val bi = MediaCodec.BufferInfo()
                    val ix = enc.dequeueOutputBuffer(bi, if (end) CODEC_TIMEOUT_US else 0L)
                    when {
                        ix == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            videoOut = mx.addTrack(enc.outputFormat)
                            if (audioFormat != null) audioOut = mx.addTrack(audioFormat!!)
                            mx.start(); muxStarted = true
                        }
                        ix >= 0 -> {
                            val buffer = enc.getOutputBuffer(ix)
                            if (bi.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) bi.size = 0
                            if (buffer != null && bi.size > 0 && muxStarted) {
                                copyAudioUntil(bi.presentationTimeUs)
                                buffer.position(bi.offset); buffer.limit(bi.offset + bi.size)
                                mx.writeSampleData(videoOut, buffer, bi)
                            }
                            enc.releaseOutputBuffer(ix, false)
                            if (bi.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return true
                        }
                        else -> { if (!end || ++waits >= 50) return false; Thread.sleep(5) }
                    }
                }
            }
            suspend fun feedEncoder(bytes: ByteArray, ptsUs: Long) {
                var attempts = 0
                while (true) {
                    coroutineContext.ensureActive()
                    val index = enc.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (index >= 0) {
                        val input = enc.getInputBuffer(index) ?: error("Encoder input buffer unavailable.")
                        require(input.capacity() >= bytes.size) { "Encoder input buffer is too small." }
                        input.clear(); input.put(bytes)
                        enc.queueInputBuffer(index, 0, bytes.size, ptsUs, 0)
                        return
                    }
                    drainEncoder(false)
                    check(++attempts < 100) { "Video encoder stalled." }
                }
            }

            var inputEos = false
            var outputEos = false
            val decInfo = MediaCodec.BufferInfo()
            var frames = 0L
            var lastProgressAtMs = 0L
            while (!outputEos) {
                coroutineContext.ensureActive()
                check(android.os.SystemClock.elapsedRealtime() - startedAtMs <= MAX_PROCESSING_MS) { "Video upscale exceeded the 30-minute processing limit." }
                if (!inputEos) {
                    val inputIx = dec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputIx >= 0) {
                        val input = dec.getInputBuffer(inputIx) ?: error("Decoder input buffer unavailable.")
                        val n = ext.readSampleData(input, 0)
                        if (n < 0) { dec.queueInputBuffer(inputIx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputEos = true }
                        else { dec.queueInputBuffer(inputIx, 0, n, ext.sampleTime, 0); ext.advance() }
                    }
                }
                val outIx = dec.dequeueOutputBuffer(decInfo, CODEC_TIMEOUT_US)
                when {
                    outIx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                    outIx >= 0 -> {
                        val eos = decInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        dec.releaseOutputBuffer(outIx, true)
                        val image = if (eos) imageQueue.poll(2, TimeUnit.SECONDS)
                            else awaitImage(imageQueue, startedAtMs)
                        if (image == null) {
                            if (eos) outputEos = true else error("Video decoder did not produce a frame.")
                        } else {
                            try {
                                val frame = imageToBitmap(image, yuvParamsFor(format))
                                try {
                                    val oriented = rotate(frame, info.rotation)
                                    try {
                                        val upscaled = GlobalModerationEnforcement.withExistingProjectVideoUpscaling {
                                            adapter.upscaleExistingProjectVideoFrame(oriented, modelSession!!)
                                        }
                                        try {
                                            require(upscaled.width == outW && upscaled.height == outH) { "ESPCN returned unexpected output dimensions." }
                                            feedEncoder(PhotoVideoEncoder.argbToNv12(upscaled, outW, outH), decInfo.presentationTimeUs)
                                        } finally { upscaled.recycle() }
                                    } finally { if (oriented !== frame) oriented.recycle() }
                                } finally { frame.recycle() }
                            } finally { image.close() }
                            drainEncoder(false)
                            frames++
                            check(frames <= MAX_FRAMES) { "Video exceeds the 900-frame processing limit." }
                            val now = android.os.SystemClock.elapsedRealtime()
                            if (now - lastProgressAtMs >= 250L) {
                                lastProgressAtMs = now
                                onProgress((decInfo.presentationTimeUs.toDouble() / (info.durationMs * 1000.0)).toFloat().coerceIn(0f, .99f))
                            }
                            if (eos) outputEos = true
                        }
                    }
                }
            }
            check(frames > 0L) { "No frames were decoded." }
            coroutineContext.ensureActive()
            var eosIndex = -1
            var attempts = 0
            while (eosIndex < 0 && attempts++ < 100) {
                coroutineContext.ensureActive()
                eosIndex = enc.dequeueInputBuffer(CODEC_TIMEOUT_US)
                if (eosIndex < 0) drainEncoder(false)
            }
            check(eosIndex >= 0) { "Video encoder did not accept end-of-stream." }
            enc.queueInputBuffer(eosIndex, 0, 0, info.durationUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            check(drainEncoder(true)) { "Video encoder did not finish." }
            check(muxStarted) { "No video frames were encoded." }
            copyAudioUntil(info.durationUs - 1L)
            mx.stop()
            check(tmp.isFile && tmp.length() > 0L) { "Rendered video is empty." }
            coroutineContext.ensureActive()
            check(allowPublish()) { "Selected project video changed during processing; result was discarded." }
            check(tmp.renameTo(outputFile)) { "Could not publish the rendered video." }
            published = true
            onProgress(1f)
            outputFile
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            if (!published) tmp.delete()
            stoppingFrameListener.set(true)
            try { decoder?.stop() } catch (_: Exception) { }
            try { decoder?.release() } catch (_: Exception) { }
            try { encoder?.stop() } catch (_: Exception) { }
            try { encoder?.release() } catch (_: Exception) { }
            try { muxer?.release() } catch (_: Exception) { }
            try { reader?.setOnImageAvailableListener(null, null) } catch (_: Exception) { }
            try { reader?.close() } catch (_: Exception) { }
            while (true) {
                val queued = pendingImages?.poll() ?: break
                try { queued.close() } catch (_: Exception) { }
            }
            try { frameThread?.quitSafely() } catch (_: Exception) { }
            try { videoExtractor?.release() } catch (_: Exception) { }
            try { audioExtractor?.release() } catch (_: Exception) { }
            try { modelSession?.close() } catch (_: Exception) { }
        }
    }

    private fun probe(context: Context, uri: String): Triple<String, MediaFormat, SourceInfo> {
        val extractor = MediaExtractor()
        val retriever = MediaMetadataRetriever()
        try {
            extractor.setDataSource(context, Uri.parse(uri), null)
            var mime: String? = null
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                val candidateMime = candidate.getString(MediaFormat.KEY_MIME) ?: continue
                if (candidateMime.startsWith("video/")) { mime = candidateMime; format = candidate; break }
            }
            val f = format ?: throw IllegalArgumentException("No video track in this clip.")
            val width = f.getInteger(MediaFormat.KEY_WIDTH)
            val height = f.getInteger(MediaFormat.KEY_HEIGHT)
            val durationUs = if (f.containsKey(MediaFormat.KEY_DURATION)) f.getLong(MediaFormat.KEY_DURATION) else {
                retriever.setDataSource(context, Uri.parse(uri))
                (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000L
            }
            val durationMs = (durationUs + 999L) / 1000L
            val audioTracks = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
                .filter { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            require(audioTracks.size <= 1) { "Multiple source audio tracks are not supported; select a single AAC track before upscaling." }
            audioTracks.singleOrNull()?.let { audio ->
                validateAudioPassthrough(audio.getString(MediaFormat.KEY_MIME),
                    if (audio.containsKey(MediaFormat.KEY_DURATION)) audio.getLong(MediaFormat.KEY_DURATION) else null,
                    durationMs)
            }
            val rotation = try {
                retriever.setDataSource(context, Uri.parse(uri))
                (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0).let { ((it % 360) + 360) % 360 }
            } catch (_: Exception) { 0 }
            val fps = if (f.containsKey(MediaFormat.KEY_FRAME_RATE)) f.getInteger(MediaFormat.KEY_FRAME_RATE) else 30
            val bitDepth = if (f.containsKey("bit-depth")) f.getInteger("bit-depth") else 8
            val profile = if (f.containsKey(MediaFormat.KEY_PROFILE)) f.getInteger(MediaFormat.KEY_PROFILE) else -1
            val tenBitProfile = when (mime) {
                "video/avc" -> profile == 16 // AVC High 10
                "video/hevc" -> profile == 2 || profile == 4096 || profile == 8192 // HEVC Main 10 / HDR10 / HDR10+
                "video/x-vnd.on2.vp9" -> profile == 4 || profile == 8 // VP9 Profile 2 / 3
                "video/av01" -> profile == 2 // AV1 Main 10
                else -> false
            }
            val hasHdr = bitDepth > 8 || tenBitProfile || f.containsKey("hdr-static-info") ||
                (f.containsKey(MediaFormat.KEY_COLOR_TRANSFER) && f.getInteger(MediaFormat.KEY_COLOR_TRANSFER) in
                    setOf(MediaFormat.COLOR_TRANSFER_ST2084, MediaFormat.COLOR_TRANSFER_HLG))
            require(!hasHdr) { "HDR and 10-bit video are not supported; use SDR 8-bit source media." }
            val info = validateSource(width, height, durationMs, rotation, fps).copy(durationUs = durationUs)
            return Triple(mime!!, f, info)
        } finally { extractor.release(); retriever.release() }
    }

    private suspend fun awaitImage(queue: LinkedBlockingQueue<Image>, startedAtMs: Long): Image? {
        val deadline = android.os.SystemClock.elapsedRealtime() + IMAGE_DEADLINE_MS
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            coroutineContext.ensureActive()
            check(android.os.SystemClock.elapsedRealtime() - startedAtMs <= MAX_PROCESSING_MS) { "Video upscale exceeded the 30-minute processing limit." }
            val image = queue.poll(200, TimeUnit.MILLISECONDS)
            if (image != null) return image
        }
        return null
    }

    private fun rotate(source: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return source
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
    }

    private fun imageToBitmap(image: Image, params: YuvParams): Bitmap {
        val crop = image.cropRect
        val w = crop.width(); val h = crop.height()
        require(w > 0 && h > 0 && crop.left >= 0 && crop.top >= 0 &&
            crop.right <= image.width && crop.bottom <= image.height) { "Decoder returned an invalid crop rectangle." }
        val (yp, up, vp) = image.planes
        val yb = yp.buffer; val ub = up.buffer; val vb = vp.buffer
        val yBase = yb.position(); val uBase = ub.position(); val vBase = vb.position()
        val argb = IntArray(Math.multiplyExact(w, h))
        for (y in 0 until h) for (x in 0 until w) {
            val sx = x + crop.left; val sy = y + crop.top
            val yy = yb.get(yBase + sy * yp.rowStride + sx * yp.pixelStride).toInt() and 255
            val u = (ub.get(uBase + (sy / 2) * up.rowStride + (sx / 2) * up.pixelStride).toInt() and 255) - 128
            val v = (vb.get(vBase + (sy / 2) * vp.rowStride + (sx / 2) * vp.pixelStride).toInt() and 255) - 128
            val c = yy - params.yOffset
            val r = ((params.yScale * c + params.rV * v + 128) shr 8).coerceIn(0, 255)
            val g = ((params.yScale * c + params.gU * u + params.gV * v + 128) shr 8).coerceIn(0, 255)
            val b = ((params.yScale * c + params.bU * u + 128) shr 8).coerceIn(0, 255)
            argb[y * w + x] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(argb, w, h, Bitmap.Config.ARGB_8888)
    }

    private data class YuvParams(val yOffset: Int, val yScale: Int, val rV: Int, val gU: Int, val gV: Int, val bU: Int)

    private fun yuvParamsFor(format: MediaFormat): YuvParams {
        val standard = if (format.containsKey(MediaFormat.KEY_COLOR_STANDARD)) format.getInteger(MediaFormat.KEY_COLOR_STANDARD) else 0
        val full = format.containsKey(MediaFormat.KEY_COLOR_RANGE) && format.getInteger(MediaFormat.KEY_COLOR_RANGE) == MediaFormat.COLOR_RANGE_FULL
        return when (standard) {
            MediaFormat.COLOR_STANDARD_BT709 -> if (full) YuvParams(0, 256, 403, -48, -120, 475) else YuvParams(16, 298, 459, -55, -136, 541)
            MediaFormat.COLOR_STANDARD_BT2020 -> if (full) YuvParams(0, 256, 377, -42, -146, 482) else YuvParams(16, 298, 430, -48, -166, 548)
            else -> if (full) YuvParams(0, 256, 359, -88, -183, 454) else YuvParams(16, 298, 409, -100, -208, 516)
        }
    }
}
