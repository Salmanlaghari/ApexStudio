package com.apexstudio.app.data.engine

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.StatFs
import android.system.Os
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/** The local, destructive-to-the-range operation baked into the derived preview/export source. */
enum class RangeTimeOperation { SLOW, REPEAT, REVERSE }

data class RangeTimeRenderResult(val uri: String, val durationMs: Long)

/**
 * Renders a bounded absolute source-time range into a new, durable MP4. The source clip is never
 * modified; callers should use the returned URI for both preview and export to guarantee parity.
 * Processing is local-only and runs on an IO dispatcher. A cancelled coroutine cancels FFmpegKit.
 */
object RangeTimeRenderer {
    const val MAX_CLIP_SPAN_MS = 60_000L
    const val MAX_RANGE_MS = 10_000L
    const val MAX_WIDTH = 1920
    const val MAX_HEIGHT = 1080
    const val MAX_PIXELS = 2_073_600L
    private const val REVERSE_MAX_RANGE_MS = 5_000L
    private const val REVERSE_MAX_LONG_EDGE = 1280
    private const val REVERSE_MAX_SHORT_EDGE = 720
    private const val REVERSE_MAX_ESTIMATED_FRAMES = 150L
    private const val MIN_FREE_BYTES = 64L * 1024L * 1024L
    private const val TAG = "RangeTimeRenderer"

    /** Render the selected absolute source range and retain the rest of the clip unchanged. */
    suspend fun render(
        context: Context,
        clip: MediaClip,
        startMs: Long,
        endMs: Long,
        operation: RangeTimeOperation
    ): RangeTimeRenderResult = withContext(Dispatchers.IO) {
        require(clip.type == ClipType.VIDEO) { "Range Time effects require a video clip." }
        require(clip.trimStartMs >= 0L && clip.trimEndMs > clip.trimStartMs) {
            "The project clip has an invalid source trim range."
        }
        require(clip.trimEndMs - clip.trimStartMs <= MAX_CLIP_SPAN_MS) {
            "Range Time supports project clips up to 60 seconds. Shorten the clip before applying this effect."
        }
        require(startMs >= clip.trimStartMs && endMs <= clip.trimEndMs && endMs > startMs) {
            "Select a non-empty range inside the current clip's absolute source trim."
        }
        require(endMs - startMs <= MAX_RANGE_MS) {
            "Range Time selections are limited to 10 seconds. Select a shorter range."
        }
        if (operation == RangeTimeOperation.REVERSE) {
            require(endMs - startMs <= REVERSE_MAX_RANGE_MS) {
                "Reverse is limited to 5 seconds to keep its frame buffer bounded. Select a shorter range."
            }
        }

        val app = context.applicationContext
        var stagedContentFile: File? = null
        var temporaryOutput: File? = null
        var publishedOutput: File? = null
        try {
            val source = resolveLocalSource(app, clip.uri).also { stagedContentFile = it.second }.first
            val sourceInfo = inspectSource(source)
            require(clip.trimEndMs <= sourceInfo.durationMs + 100L && endMs <= sourceInfo.durationMs) {
                "The selected range is outside the source video's duration."
            }
            val orientedWidth = if (sourceInfo.rotation == 90 || sourceInfo.rotation == 270) sourceInfo.height else sourceInfo.width
            val orientedHeight = if (sourceInfo.rotation == 90 || sourceInfo.rotation == 270) sourceInfo.width else sourceInfo.height
            require(maxOf(orientedWidth, orientedHeight) <= MAX_WIDTH && minOf(orientedWidth, orientedHeight) <= MAX_HEIGHT &&
                orientedWidth.toLong() * orientedHeight <= MAX_PIXELS) {
                "Range Time supports video up to 1920×1080 (or 1080×1920 portrait). Downscale the source first."
            }
            if (operation == RangeTimeOperation.REVERSE) {
                require(maxOf(orientedWidth, orientedHeight) <= REVERSE_MAX_LONG_EDGE &&
                    minOf(orientedWidth, orientedHeight) <= REVERSE_MAX_SHORT_EDGE) {
                    "Reverse is limited to 1280×720 (or 720×1280) to bound its in-memory frame buffer. Downscale this clip first."
                }
                val estimatedFrames = (sourceInfo.frameRate.toLong() * (endMs - startMs) + 999L) / 1000L
                require(sourceInfo.frameRate <= 60) {
                    "Reverse supports source frame rates up to 60 fps to bound its in-memory frame buffer."
                }
                require(estimatedFrames <= REVERSE_MAX_ESTIMATED_FRAMES) {
                    "Reverse is limited to about 150 decoded frames to bound memory. Select a shorter range."
                }
            }

            val derivedDir = File(app.filesDir, "derived/range-time")
            check(derivedDir.exists() || derivedDir.mkdirs()) { "Could not create app storage for the derived video." }
            val freeBytes = StatFs(derivedDir.absolutePath).availableBytes
            val requiredBytes = maxOf(MIN_FREE_BYTES, source.length().coerceAtLeast(1L) * 2L)
            require(freeBytes >= requiredBytes) {
                "Not enough free app storage for a safe derived render (need about ${requiredBytes / (1024 * 1024)} MiB)."
            }

            val unique = UUID.randomUUID().toString()
            val output = File(derivedDir, "range-time-$unique.mp4")
            check(!output.exists()) { "A generated output name unexpectedly already exists." }
            val tmp = File(derivedDir, ".range-time-$unique.tmp.mp4")
            temporaryOutput = tmp
            val command = buildCommand(
                inputPath = source.absolutePath,
                outputPath = tmp.absolutePath,
                clipStartMs = clip.trimStartMs,
                rangeStartMs = startMs,
                rangeEndMs = endMs,
                clipEndMs = clip.trimEndMs,
                operation = operation,
                hasAudio = sourceInfo.hasAudio
            )
            val session = executeCancellable(command)
            check(ReturnCode.isSuccess(session.returnCode)) {
                "FFmpeg could not render the selected range (code ${session.returnCode?.value ?: "unknown"})."
            }
            check(tmp.isFile && tmp.length() > 0L) { "FFmpeg completed without producing a video file." }
            val rendered = inspectRenderedOutput(tmp)
            val expectedDurationMs = clip.trimEndMs - clip.trimStartMs + when (operation) {
                RangeTimeOperation.SLOW, RangeTimeOperation.REPEAT -> endMs - startMs
                RangeTimeOperation.REVERSE -> 0L
            }
            val durationToleranceMs = maxOf(150L, expectedDurationMs / 50L)
            require(kotlin.math.abs(rendered.durationMs - expectedDurationMs) <= durationToleranceMs) {
                "The rendered video's duration did not match the requested range operation."
            }
            currentCoroutineContext().ensureActive()
            // A same-directory hard link publishes a complete file atomically and fails if the
            // UUID destination already exists; unlike rename, it can never replace prior work.
            Os.link(tmp.absolutePath, output.absolutePath)
            publishedOutput = output
            tmp.delete()
            currentCoroutineContext().ensureActive()
            RangeTimeRenderResult(Uri.fromFile(output).toString(), rendered.durationMs)
        } catch (failure: Throwable) {
            temporaryOutput?.delete()
            publishedOutput?.delete()
            throw failure
        } finally {
            stagedContentFile?.delete()
        }
    }

    /** Pure command planner: arguments are passed directly to FFmpegKit, never through a shell. */
    internal fun buildCommand(
        inputPath: String,
        outputPath: String,
        clipStartMs: Long,
        rangeStartMs: Long,
        rangeEndMs: Long,
        clipEndMs: Long,
        operation: RangeTimeOperation,
        hasAudio: Boolean
    ): Array<String> {
        require(clipStartMs >= 0 && clipStartMs <= rangeStartMs && rangeStartMs < rangeEndMs && rangeEndMs <= clipEndMs) {
            "Planner requires a non-empty selected range inside the clip trim."
        }
        val graph = buildFilterGraph(clipStartMs, rangeStartMs, rangeEndMs, clipEndMs, operation, hasAudio)
        val args = mutableListOf("-hide_banner", "-y", "-i", inputPath, "-filter_complex", graph,
            "-map", "[vout]")
        if (hasAudio) args += listOf("-map", "[aout]")
        // The shipped non-GPL FFmpeg AAR enables OpenH264, NOT libx264.
        args += listOf("-c:v", "libopenh264", "-b:v", "8M", "-pix_fmt", "yuv420p")
        if (hasAudio) args += listOf("-c:a", "aac", "-b:a", "192k")
        args += listOf("-map_metadata", "0", "-metadata:s:v:0", "rotate=0", "-movflags", "+faststart", "-max_muxing_queue_size", "1024", outputPath)
        return args.toTypedArray()
    }

    /** Builds trim/effect/concat filters. Selected times and clip trim values are absolute source ms. */
    internal fun buildFilterGraph(
        clipStartMs: Long,
        rangeStartMs: Long,
        rangeEndMs: Long,
        clipEndMs: Long,
        operation: RangeTimeOperation,
        hasAudio: Boolean
    ): String {
        require(clipStartMs >= 0 && clipStartMs <= rangeStartMs && rangeStartMs < rangeEndMs && rangeEndMs <= clipEndMs)
        val segmentPieces = mutableListOf<String>()
        var index = 0

        fun appendSegment(startMs: Long, endMs: Long, affected: Boolean) {
            val vLabel = "v$index"
            val segmentDuration = (endMs - startMs) * if (affected && operation == RangeTimeOperation.SLOW) 2L else 1L
            val v = StringBuilder("[0:v]trim=start=${seconds(startMs)}:end=${seconds(endMs)},setpts=PTS-STARTPTS")
            if (affected) when (operation) {
                RangeTimeOperation.SLOW -> v.append(",setpts=2*(PTS-STARTPTS)")
                RangeTimeOperation.REPEAT -> Unit // A second independent trim of the selected source span follows.
                RangeTimeOperation.REVERSE -> v.append(",reverse,setpts=PTS-STARTPTS")
            }
            // Reverse and retimed clips can end on the prior frame PTS rather than the segment
            // boundary. Clone only that last frame to keep every segment's declared duration exact.
            v.append(",tpad=stop_mode=clone:stop_duration=${seconds(segmentDuration)},trim=duration=${seconds(segmentDuration)}[$vLabel]")
            segmentPieces += v.toString()
            if (hasAudio) {
                val aLabel = "a$index"
                val a = StringBuilder("[0:a]atrim=start=${seconds(startMs)}:end=${seconds(endMs)},asetpts=PTS-STARTPTS")
                if (affected) when (operation) {
                    RangeTimeOperation.SLOW -> a.append(",atempo=0.5")
                    RangeTimeOperation.REPEAT -> Unit
                    RangeTimeOperation.REVERSE -> a.append(",areverse,asetpts=PTS-STARTPTS")
                }
                a.append(",apad,atrim=duration=${seconds(segmentDuration)}[$aLabel]")
                segmentPieces += a.toString()
            }
            index++
        }

        if (rangeStartMs > clipStartMs) appendSegment(clipStartMs, rangeStartMs, affected = false)
        appendSegment(rangeStartMs, rangeEndMs, affected = true)
        if (operation == RangeTimeOperation.REPEAT) appendSegment(rangeStartMs, rangeEndMs, affected = true)
        if (clipEndMs > rangeEndMs) appendSegment(rangeEndMs, clipEndMs, affected = false)
        if (hasAudio) {
            val interleavedInputs = (0 until index).joinToString("") { "[v$it][a$it]" }
            segmentPieces += "$interleavedInputs concat=n=$index:v=1:a=1 [vout][aout]"
        } else {
            val videoInputs = (0 until index).joinToString("") { "[v$it]" }
            segmentPieces += "$videoInputs concat=n=$index:v=1:a=0 [vout]"
        }
        return segmentPieces.joinToString(";")
    }

    private data class SourceInfo(val width: Int, val height: Int, val rotation: Int, val durationMs: Long, val hasAudio: Boolean, val frameRate: Int)
    private data class OutputInfo(val width: Int, val height: Int, val durationMs: Long)

    private fun inspectSource(file: File): SourceInfo {
        val retriever = MediaMetadataRetriever()
        val extractor = MediaExtractor()
        try {
            retriever.setDataSource(file.absolutePath)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            require(width > 0 && height > 0 && durationMs > 0L) { "The selected local file is not a readable video." }
            extractor.setDataSource(file.absolutePath)
            var hasVideo = false
            var hasAudio = false
            var frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                ?.toFloatOrNull()?.toInt()?.takeIf { it in 1..60 } ?: 60
            for (track in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(track)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/")) {
                    hasVideo = true
                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE).takeIf { it > 0 } ?: 60
                    }
                }
                if (mime.startsWith("audio/")) hasAudio = true
            }
            require(hasVideo) { "The selected local file contains no video track." }
            require(rotation in setOf(0, 90, 180, 270)) { "The source video has unsupported rotation metadata." }
            return SourceInfo(width, height, rotation, durationMs, hasAudio, frameRate)
        } finally {
            extractor.release()
            retriever.release()
        }
    }

    private fun inspectRenderedOutput(file: File): OutputInfo {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            require(width >= 2 && height >= 2 && duration > 0L) { "The rendered MP4 failed video metadata verification." }
            return OutputInfo(width, height, duration)
        } finally {
            retriever.release()
        }
    }

    /** Returns a readable local file and an optional temporary content-URI copy to clean up. */
    private suspend fun resolveLocalSource(context: Context, rawUri: String): Pair<File, File?> {
        val uri = Uri.parse(rawUri)
        return when {
            uri.scheme == "file" -> File(uri.path ?: error("Invalid file URI." )).let {
                require(it.isFile && it.canRead()) { "The project video file is unavailable." }
                it to null
            }
            uri.scheme == null && rawUri.startsWith("/") -> File(rawUri).let {
                require(it.isFile && it.canRead()) { "The project video file is unavailable." }
                it to null
            }
            uri.scheme == "content" -> {
                val staged = File(context.cacheDir, "range-time-source-${UUID.randomUUID()}.input")
                try {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: error("The project video cannot be opened from its local content URI.")
                    val stagingBudget = minOf(1024L * 1024L * 1024L,
                        (StatFs(context.cacheDir.absolutePath).availableBytes - MIN_FREE_BYTES).coerceAtLeast(0L))
                    input.use { stream -> FileOutputStream(staged).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var copied = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = stream.read(buffer)
                            if (count < 0) break
                            copied += count
                            require(copied <= stagingBudget) {
                                "Not enough local staging space, or this content-URI source exceeds the 1 GiB safety limit. Import a smaller local file."
                            }
                            output.write(buffer, 0, count)
                        }
                    } }
                    require(staged.isFile && staged.length() > 0L) { "The project video content URI is empty." }
                    staged to staged
                } catch (failure: Throwable) {
                    staged.delete()
                    throw failure
                }
            }
            else -> error("Range Time only accepts local project media (file or content URI); network sources are not rendered.")
        }
    }

    private fun seconds(ms: Long): String = String.format(Locale.US, "%.3f", ms / 1000.0)

    private suspend fun executeCancellable(command: Array<String>): FFmpegSession =
        suspendCancellableCoroutine { continuation ->
            val sessionRef = AtomicReference<FFmpegSession?>()
            val session = FFmpegKit.executeWithArgumentsAsync(command) { completed ->
                if (completed != null && continuation.isActive) continuation.resume(completed)
                else if (completed == null && continuation.isActive) {
                    continuation.resumeWith(Result.failure(IllegalStateException("FFmpegKit returned no session result.")))
                }
            }
            sessionRef.set(session)
            continuation.invokeOnCancellation {
                sessionRef.get()?.let { FFmpegKit.cancel(it.sessionId) }
            }
        }
}
