package com.apexstudio.app.ui.screens.colortools

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.apexstudio.app.data.effect.AspectCropTransformation
import com.apexstudio.app.data.export.ExportEngine
import com.apexstudio.app.data.export.ProjectCompositionTimeline
import com.apexstudio.app.data.export.projectCompositionUnsupportedReason
import com.apexstudio.app.data.color.ColorStudioGrade
import com.apexstudio.app.domain.model.MediaClip
import com.arthenica.ffmpegkit.FFprobeKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

/** Full project-composition Color comparison; this never substitutes source frames for rendered output. */
@OptIn(UnstableApi::class)
object ColorTimelineComparisonRenderer {
    /** An encoded output's probed properties. `frameRate` is parsed from the encoded stream, not requested settings. */
    data class EncodedVideoMetadata(
        val width: Int,
        val height: Int,
        val frameRate: Double,
        val frameCount: Long,
        val durationMs: Long
    )

    data class Comparison(
        val before: File,
        val after: File,
        val grade: ColorStudioGrade,
        val durationMs: Long,
        val fps: Int,
        val frameCount: Long,
        val width: Int,
        val height: Int,
        internal val presentationTimesUs: List<Long>,
        internal val beforeFileLength: Long,
        internal val afterFileLength: Long,
        internal val beforeLastModified: Long,
        internal val afterLastModified: Long
    ) {
        /** Delete the temporary ungraded comparison render when the comparison UI is dismissed. */
        fun deleteBeforePreview() { before.delete() }
    }

    data class FramePair(
        val before: Bitmap,
        val after: Bitmap,
        val frameIndex: Long,
        val timeMs: Long
    ) {
        fun recycle() {
            if (!before.isRecycled) before.recycle()
            if (!after.isRecycled) after.recycle()
        }
    }

    private const val MAX_PREVIEW_DIMENSION = ColorMediaIO.PREVIEW_MAX_DIMENSION
    private const val MAX_1080P_PIXELS = 1_920L * 1_080L
    private const val MAX_1080P_LONG_SIDE = 1_920
    private const val MAX_COMPARISON_DURATION_MS = 30L * 60L * 1_000L
    private const val MAX_COMPARISON_FRAMES = 108_000L // 30 minutes at the project's 60 fps ceiling.

    /**
     * Encode the complete supported project composition twice from the same immutable clip/config snapshot.
     * The only difference is `colorStudioGrade`; global filters, effects, overlays, text and audio config remain intact.
     * The comparison path intentionally caps output at 1080p to bound hardware encode/decode memory.
     */
    suspend fun render(
        context: Context,
        clips: List<MediaClip>,
        config: ExportEngine.ExportConfig,
        onProgress: (Float) -> Unit
    ): Comparison {
        val grade = requireNotNull(config.colorStudioGrade) { "Set a Color Studio grade before rendering a comparison." }
        val beforeConfig = config.copy(colorStudioGrade = null)
        val gradedBlocker = projectCompositionUnsupportedReason(clips, config)
        require(gradedBlocker == null) { gradedBlocker ?: "This project composition is unsupported for Color comparison." }
        val baselineBlocker = projectCompositionUnsupportedReason(clips, beforeConfig)
        require(baselineBlocker == null) { baselineBlocker ?: "This project composition is unsupported for Color comparison." }
        val timelineDurationMs = ProjectCompositionTimeline.build(clips).durationMs
        require(isSupportedComparisonTimeline(timelineDurationMs, config.fps)) {
            "Timeline comparison is limited to 30 minutes / 108,000 encoded frames to bound export time and exact-frame metadata."
        }
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            "Exact same-frame comparison requires Android 9 (API 28) or later."
        }
        val (outputWidth, outputHeight) = AspectCropTransformation.targetSizeFor(config.resolution, config.aspectRatio)
        require(isSupportedComparisonOutput(outputWidth, outputHeight)) {
            "Timeline comparison is limited to 1080p output (at most 1920×1080 pixels). Lower the export resolution first."
        }

        // Re-inspect every actual timeline input immediately before either render; reject HDR/unknown transfer formats.
        clips.forEach { clip ->
            currentCoroutineContext().ensureActive()
            val info = ColorVideoExporter.inspect(context, Uri.parse(clip.uri))
            require(info.supported) { info.reason ?: "Video '${clip.name}' is not supported by the SDR Color comparison." }
        }

        val outputDir = File(context.getExternalFilesDir(null), "ApexStudio_Exports")
            .apply { check(isDirectory || mkdirs()) { "Cannot create the private timeline export directory." } }
        val unique = UUID.randomUUID().toString().replace("-", "")
        val created = mutableListOf<File>()
        try {
            onProgress(0f)
            val before = renderPass(context, clips, beforeConfig, outputDir, "color_timeline_before_$unique.mp4", 0, onProgress)
            created.add(before)
            val after = renderPass(context, clips, config, outputDir, "color_timeline_after_$unique.mp4", 1, onProgress)
            created.add(after)
            currentCoroutineContext().ensureActive()

            val beforeMetadata = probeEncodedVideo(before)
            val afterMetadata = probeEncodedVideo(after)
            require(compatibleEncodedPair(beforeMetadata, afterMetadata)) {
                "The before/after exports do not have matching verified dimensions, frame rate, frame count and duration; no comparison was shown."
            }
            val beforePts = probePresentationTimestampsUs(before)
            val afterPts = probePresentationTimestampsUs(after)
            require(beforePts.size.toLong() == beforeMetadata.frameCount &&
                afterPts.size.toLong() == afterMetadata.frameCount &&
                compatiblePresentationTimestamps(beforePts, afterPts)) {
                "The encoded before/after presentation timestamps are not identical; exact same-frame comparison is unsafe."
            }
            require(beforeMetadata.width == outputWidth && beforeMetadata.height == outputHeight &&
                afterMetadata.width == outputWidth && afterMetadata.height == outputHeight) {
                "The rendered comparison dimensions differ from the requested output geometry."
            }
            onProgress(1f)
            return Comparison(
                before = before,
                after = after,
                grade = grade,
                durationMs = afterMetadata.durationMs,
                fps = afterMetadata.frameRate.roundToInt(),
                frameCount = afterMetadata.frameCount,
                width = afterMetadata.width,
                height = afterMetadata.height,
                presentationTimesUs = beforePts.toList(),
                beforeFileLength = before.length(),
                afterFileLength = after.length(),
                beforeLastModified = before.lastModified(),
                afterLastModified = after.lastModified()
            )
        } catch (failure: Throwable) {
            created.forEach { it.delete() }
            throw failure
        }
    }

    /** Decode the same encoded output frame index from both files; requires API 28 exact-index decoding. */
    suspend fun decodeFrames(result: Comparison, timeMs: Long): FramePair =
        withContext(Dispatchers.IO) {
            require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                "Exact same-frame comparison requires Android 9 (API 28) or later."
            }
            require(result.before.isFile && result.after.isFile &&
                result.before.length() == result.beforeFileLength && result.after.length() == result.afterFileLength &&
                result.before.lastModified() == result.beforeLastModified && result.after.lastModified() == result.afterLastModified) {
                "A compared export changed or is no longer available. Render the comparison again."
            }
            val presentationTimesUs = result.presentationTimesUs
            require(presentationTimesUs.size.toLong() == result.frameCount && presentationTimesUs.isNotEmpty()) {
                "The verified comparison frame clock is unavailable. Render the comparison again."
            }
            val requestedTimeUs = if (timeMs <= 0L) 0L else if (timeMs > Long.MAX_VALUE / 1_000L) Long.MAX_VALUE else timeMs * 1_000L
            val frameIndex = nearestPresentationFrameIndex(presentationTimesUs, requestedTimeUs)
            currentCoroutineContext().ensureActive()
            val before = decodeExactFrame(result.before, frameIndex)
            try {
                currentCoroutineContext().ensureActive()
                val after = decodeExactFrame(result.after, frameIndex)
                try {
                    val (w, h) = boundedDimensions(before.width, before.height, MAX_PREVIEW_DIMENSION)
                    val (aw, ah) = boundedDimensions(after.width, after.height, MAX_PREVIEW_DIMENSION)
                    require(w == aw && h == ah) { "The decoded comparison frames have different geometry." }
                    val scaledBefore = scaleAndRecycle(before, w, h)
                    var scaledAfter: Bitmap? = null
                    try {
                        scaledAfter = scaleAndRecycle(after, aw, ah)
                        currentCoroutineContext().ensureActive()
                        FramePair(scaledBefore, scaledAfter, frameIndex,
                            presentationTimesUs[frameIndex.toInt()] / 1_000L)
                    } catch (failure: Throwable) {
                        if (!scaledBefore.isRecycled) scaledBefore.recycle()
                        scaledAfter?.let { if (!it.isRecycled) it.recycle() }
                        if (!after.isRecycled) after.recycle()
                        throw failure
                    }
                } catch (failure: Throwable) {
                    if (!after.isRecycled) after.recycle()
                    throw failure
                }
            } catch (failure: Throwable) {
                if (!before.isRecycled) before.recycle()
                throw failure
            }
        }

    /** Pure output geometry policy, deliberately capped even when the source/config supports 4K. */
    internal fun isSupportedComparisonOutput(width: Int, height: Int): Boolean =
        width > 0 && height > 0 && maxOf(width, height) <= MAX_1080P_LONG_SIDE &&
            width.toLong() * height.toLong() <= MAX_1080P_PIXELS

    internal fun isSupportedComparisonTimeline(durationMs: Long, fps: Int): Boolean =
        durationMs in 1L..MAX_COMPARISON_DURATION_MS && fps > 0 &&
            durationMs * fps.toLong() / 1_000L <= MAX_COMPARISON_FRAMES

    /** Fail-closed match policy: all encoded clock fields are mandatory and duration may differ by at most one frame + 1ms. */
    internal fun compatibleEncodedPair(before: EncodedVideoMetadata, after: EncodedVideoMetadata): Boolean {
        if (before.width <= 0 || before.height <= 0 || before.width != after.width || before.height != after.height ||
            before.frameCount !in 1L..MAX_COMPARISON_FRAMES || before.frameCount != after.frameCount ||
            before.durationMs !in 1L..MAX_COMPARISON_DURATION_MS || after.durationMs !in 1L..MAX_COMPARISON_DURATION_MS ||
            !before.frameRate.isFinite() || !after.frameRate.isFinite() || before.frameRate <= 0.0 || after.frameRate <= 0.0) return false
        val fpsTolerance = maxOf(0.01, maxOf(before.frameRate, after.frameRate) * 0.0001)
        if (abs(before.frameRate - after.frameRate) > fpsTolerance) return false
        val frameDurationMs = 1000.0 / minOf(before.frameRate, after.frameRate)
        return abs(before.durationMs - after.durationMs) <= frameDurationMs + 1.0
    }

    /** PTS are sorted into presentation order because container/sample extraction may expose decode order with B-frames. */
    internal fun compatiblePresentationTimestamps(before: List<Long>, after: List<Long>): Boolean =
        before.isNotEmpty() && before.size == after.size && before.first() >= 0L &&
            (1 until before.size).all { before[it] > before[it - 1] } && before == after

    /** Map a requested output-clock timestamp to the nearest presentation frame, not requested/configured FPS. */
    internal fun nearestPresentationFrameIndex(sortedPresentationTimesUs: List<Long>, requestedTimeUs: Long): Long {
        require(sortedPresentationTimesUs.isNotEmpty() && sortedPresentationTimesUs == sortedPresentationTimesUs.sorted())
        val target = requestedTimeUs.coerceIn(sortedPresentationTimesUs.first(), sortedPresentationTimesUs.last())
        var low = 0
        var high = sortedPresentationTimesUs.lastIndex
        while (low < high) {
            val mid = (low + high) ushr 1
            if (sortedPresentationTimesUs[mid] < target) low = mid + 1 else high = mid
        }
        if (low == 0) return 0L
        val previous = sortedPresentationTimesUs[low - 1]
        val next = sortedPresentationTimesUs[low]
        return if (target - previous <= next - target) (low - 1).toLong() else low.toLong()
    }

    /** Parse a positive integer or rational FFprobe rate; exposed for regression tests. */
    internal fun parseFrameRate(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val pieces = raw.trim().split('/')
        if (pieces.size == 1) return pieces[0].toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
        if (pieces.size != 2) return null
        val numerator = pieces[0].toDoubleOrNull() ?: return null
        val denominator = pieces[1].toDoubleOrNull()?.takeIf { it > 0.0 } ?: return null
        return (numerator / denominator).takeIf { it.isFinite() && it > 0.0 }
    }

    private suspend fun renderPass(
        context: Context,
        clips: List<MediaClip>,
        config: ExportEngine.ExportConfig,
        outputDir: File,
        filename: String,
        phase: Int,
        onProgress: (Float) -> Unit
    ): File {
        // A distinct engine for each pass is intentional: terminal StateFlow publication precedes its internal finally cleanup.
        val engine = ExportEngine(context)
        val destination = File(outputDir, filename)
        var complete = false
        try {
            check(!destination.exists()) { "A unique comparison output unexpectedly already exists." }
            engine.startProjectExport(clips, config, filename)
            val terminal = engine.exportState.first { state ->
                onProgress(((phase + state.progress.coerceIn(0f, 1f)) / 2f).coerceIn(0f, 1f))
                !state.isExporting && (state.outputUri != null || state.error != null)
            }
            check(terminal.error == null) { terminal.error ?: "Timeline export failed." }
            check(terminal.warning == null) {
                terminal.warning ?: "Timeline export reported a validation warning; comparison was stopped."
            }
            check(terminal.outputUri != null && destination.isFile && destination.length() > 0L) {
                "Timeline export completed without a readable output file."
            }
            complete = true
            return destination
        } finally {
            // The engine publishes its terminal StateFlow before its job's finally cleanup. Join only this isolated pass.
            withContext(NonCancellable) { engine.cancelProjectExportAndAwait() }
            if (!complete) destination.delete()
        }
    }

    private suspend fun probeEncodedVideo(file: File): EncodedVideoMetadata = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        check(file.isFile && file.length() > 0L) { "Rendered comparison file is missing or empty." }
        val session = FFprobeKit.getMediaInformation(file.absolutePath)
        val information = session.mediaInformation
        check(session.returnCode?.value == 0 && information != null) { "Encoded comparison metadata could not be verified." }
        val video = information.streams.firstOrNull { it.type == "video" }
            ?: error("Rendered comparison has no video stream.")
        val width = video.width?.toInt() ?: 0
        val height = video.height?.toInt() ?: 0
        val fps = parseFrameRate(video.getStringProperty("avg_frame_rate"))
            ?: parseFrameRate(video.getStringProperty("r_frame_rate"))
            ?: error("Encoded comparison frame rate could not be verified.")
        val frameCount = video.getStringProperty("nb_frames")?.toLongOrNull()
            ?: error("Encoded comparison frame count could not be verified.")
        val durationSeconds = video.getStringProperty("duration")?.toDoubleOrNull()
            ?: information.duration?.toDoubleOrNull()
            ?: error("Encoded comparison duration could not be verified.")
        check(durationSeconds.isFinite() && durationSeconds > 0.0) { "Encoded comparison duration is invalid." }
        EncodedVideoMetadata(width, height, fps, frameCount, (durationSeconds * 1000.0).roundToInt().toLong())
    }

    /** Extract every actual video sample PTS, then sort to presentation order (bounded to the <=1080p render cap). */
    private suspend fun probePresentationTimestampsUs(file: File): List<Long> = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val videoTrack = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: error("Rendered comparison has no readable video track.")
            extractor.selectTrack(videoTrack)
            val presentationTimes = ArrayList<Long>()
            while (true) {
                currentCoroutineContext().ensureActive()
                val timestamp = extractor.sampleTime
                if (timestamp < 0L) break
                require(presentationTimes.size.toLong() < MAX_COMPARISON_FRAMES) {
                    "The encoded timeline exceeds the safe exact-frame comparison limit."
                }
                presentationTimes.add(timestamp)
                if (!extractor.advance()) break
            }
            require(presentationTimes.isNotEmpty()) { "Rendered comparison contains no video samples." }
            presentationTimes.sort()
            require(presentationTimes.zipWithNext().all { (a, b) -> a < b }) {
                "Rendered comparison has missing or duplicate presentation timestamps."
            }
            presentationTimes
        } finally {
            extractor.release()
        }
    }

    private fun decodeExactFrame(file: File, frameIndex: Long): Bitmap {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val bitmap = retriever.getFrameAtIndex(frameIndex.toInt())
                ?: error("Could not decode encoded comparison frame $frameIndex.")
            return bitmap
        } finally {
            retriever.release()
        }
    }

    private fun boundedDimensions(width: Int, height: Int, maximum: Int): Pair<Int, Int> {
        val scale = minOf(1f, maximum.toFloat() / maxOf(width, height))
        return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
    }

    private fun scaleAndRecycle(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        if (bitmap.width == width && bitmap.height == height) return bitmap
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }
}
