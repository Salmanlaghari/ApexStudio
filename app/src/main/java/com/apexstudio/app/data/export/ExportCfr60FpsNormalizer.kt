package com.apexstudio.app.data.export

import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.StatFs
import android.system.Os
import androidx.media3.common.MimeTypes
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.roundToLong

/** Explicit CFR generation used only when a readable 60 fps H.264 output misses its cadence audit. */
internal object ExportCfr60FpsNormalizer {
    private const val MIN_FREE_BYTES = 64L * 1024L * 1024L

    internal fun shouldNormalize(config: ExportEngine.ExportConfig, audit: EncodedFrameRateAudit): Boolean {
        if (config.fps != 60 || config.videoMimeType != MimeTypes.VIDEO_H264) return false
        if (com.apexstudio.app.domain.model.ExportResolution.canonical(config.resolution) == "8K") return false
        val rendererCountMismatch = audit.transformerFrameCountDelta?.let { delta ->
            abs(delta) > maxOf(2L, (audit.sampleCount * 0.02).roundToLong())
        } == true
        if (rendererCountMismatch) return false
        // Classify from structured audit facts, never from a user-facing error string.
        return audit.strict60FpsCadenceError != null &&
            audit.sampleCount > 1 &&
            audit.outputDurationMs != null &&
            audit.expectedCfrSampleCount != null &&
            audit.videoMimeType == MimeTypes.VIDEO_H264 &&
            audit.videoWidth?.let { it >= 2 } == true &&
            audit.videoHeight?.let { it >= 2 } == true &&
            audit.duplicateTimestampCount == 0 &&
            audit.negativeTimestampCount == 0
    }

    /** Pure argument-array planner; paths remain individual arguments and never pass through a shell. */
    internal fun buildArguments(inputPath: String, outputPath: String, targetBitrate: Int): Array<String> {
        require(inputPath.isNotBlank() && outputPath.isNotBlank()) { "CFR input and output paths are required." }
        require(targetBitrate in 1_000_000..100_000_000) { "CFR target bitrate is outside the supported export range." }
        return arrayOf(
            "-hide_banner", "-y",
            "-i", inputPath,
            "-map", "0:v:0",
            "-map", "0:a?",
            "-vf", "fps=60",
            "-fps_mode", "cfr",
            "-c:v", "libopenh264",
            "-b:v", targetBitrate.toString(),
            "-pix_fmt", "yuv420p",
            "-c:a", "copy",
            "-map_metadata", "0",
            "-metadata:s:v:0", "rotate=0",
            "-movflags", "+faststart",
            "-max_muxing_queue_size", "1024",
            outputPath
        )
    }

    /** Encodes to a temporary sibling file; callers audit it before atomically replacing the source. */
    internal suspend fun transcode(inputFile: File, targetBitrate: Int): File = withContext(Dispatchers.IO) {
        require(inputFile.isFile && inputFile.length() > 0L) { "The rendered video is missing before 60 fps normalization." }
        val parent = inputFile.parentFile ?: throw IllegalStateException("The rendered video has no parent directory.")
        val neededBytes = maxOf(MIN_FREE_BYTES, inputFile.length().coerceAtLeast(1L) * 2L)
        val availableBytes = StatFs(parent.absolutePath).availableBytes
        require(availableBytes >= neededBytes) {
            "Not enough free storage to create and verify a 60 fps copy (need about ${neededBytes / (1024L * 1024L)} MiB)."
        }
        currentCoroutineContext().ensureActive()
        val temporary = File.createTempFile("apex_cfr60_", ".mp4", parent)
        try {
            val command = buildArguments(inputFile.absolutePath, temporary.absolutePath, targetBitrate)
            val session = executeCancellable(command)
            check(ReturnCode.isSuccess(session.returnCode)) {
                "The available OpenH264 encoder could not create the 60 fps copy (FFmpeg code ${session.returnCode?.value ?: "unknown"})."
            }
            check(temporary.isFile && temporary.length() > 0L) {
                "The 60 fps conversion finished without producing a video file."
            }
            currentCoroutineContext().ensureActive()
            temporary
        } catch (cancelled: CancellationException) {
            temporary.delete()
            throw cancelled
        } catch (failure: Throwable) {
            temporary.delete()
            throw failure
        }
    }

    /** POSIX rename within the same directory atomically replaces only after the candidate passes audit. */
    internal fun replaceAtomically(candidate: File, destination: File) {
        require(candidate.isFile && candidate.length() > 0L) { "The verified 60 fps candidate is missing." }
        require(candidate.parentFile?.canonicalPath == destination.parentFile?.canonicalPath) {
            "The 60 fps candidate must be in the export's directory for an atomic replacement."
        }
        Os.rename(candidate.absolutePath, destination.absolutePath)
    }

    /** Reject a CFR candidate if remuxing loses, changes, or shifts any existing audio stream. */
    internal fun verifyAudioPreserved(
        sourceFile: File,
        candidateFile: File,
        sourceVideoDurationMs: Long,
        candidateVideoDurationMs: Long
    ): Int {
        val sourceTracks = audioTracks(sourceFile)
        val candidateTracks = audioTracks(candidateFile)
        check(sourceTracks.size == candidateTracks.size) {
            "The 60 fps conversion changed the number of audio tracks (${sourceTracks.size} to ${candidateTracks.size})."
        }
        if (sourceTracks.isEmpty()) return 0
        check(sourceVideoDurationMs > 0L && candidateVideoDurationMs > 0L) {
            "Audio synchronization could not be verified because a video duration is unavailable."
        }
        sourceTracks.zip(candidateTracks).forEachIndexed { index, (source, candidate) ->
            check(source.mimeType.equals(candidate.mimeType, ignoreCase = true)) {
                "The 60 fps conversion changed audio track ${index + 1}'s codec."
            }
            check(source.sampleRate == null || source.sampleRate == candidate.sampleRate) {
                "The 60 fps conversion changed audio track ${index + 1}'s sample rate."
            }
            check(source.channelCount == null || source.channelCount == candidate.channelCount) {
                "The 60 fps conversion changed audio track ${index + 1}'s channel count."
            }
            val candidateDurationUs = candidate.durationUs
                ?: throw IllegalStateException("The 60 fps conversion's audio duration could not be verified.")
            source.durationUs?.let { sourceDurationUs ->
                val sourceDriftUs = sourceDurationUs - sourceVideoDurationMs * 1_000L
                val candidateDriftUs = candidateDurationUs - candidateVideoDurationMs * 1_000L
                check(abs(candidateDurationUs - sourceDurationUs) <= AV_SYNC_TOLERANCE_US &&
                    abs(candidateDriftUs - sourceDriftUs) <= AV_SYNC_TOLERANCE_US
                ) {
                    "The 60 fps conversion changed audio track ${index + 1}'s duration or A/V synchronization."
                }
            }
        }
        return sourceTracks.size
    }

    private fun audioTracks(file: File): List<AudioTrackSignature> {
        require(file.isFile && file.length() > 0L) { "Cannot inspect audio in a missing or empty export." }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            return (0 until extractor.trackCount).mapNotNull { index ->
                val format = extractor.getTrackFormat(index)
                val mimeType = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (!mimeType.startsWith("audio/")) return@mapNotNull null
                AudioTrackSignature(
                    mimeType = mimeType,
                    sampleRate = format.intOrNull(MediaFormat.KEY_SAMPLE_RATE),
                    channelCount = format.intOrNull(MediaFormat.KEY_CHANNEL_COUNT),
                    durationUs = format.longOrNull(MediaFormat.KEY_DURATION)
                )
            }
        } finally {
            extractor.release()
        }
    }

    private fun MediaFormat.intOrNull(key: String): Int? =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null

    private fun MediaFormat.longOrNull(key: String): Long? =
        if (containsKey(key)) runCatching { getLong(key) }.getOrNull() else null

    private suspend fun executeCancellable(command: Array<String>): FFmpegSession =
        suspendCancellableCoroutine { continuation ->
            if (!continuation.isActive) return@suspendCancellableCoroutine
            val sessionRef = AtomicReference<FFmpegSession?>()
            continuation.invokeOnCancellation {
                sessionRef.get()?.let { FFmpegKit.cancel(it.sessionId) }
            }
            val session = FFmpegKit.executeWithArgumentsAsync(command) { completed ->
                if (completed != null && continuation.isActive) continuation.resume(completed)
                else if (completed == null && continuation.isActive) {
                    continuation.resumeWith(Result.failure(IllegalStateException("FFmpegKit returned no session result.")))
                }
            }
            sessionRef.set(session)
            if (!continuation.isActive) FFmpegKit.cancel(session.sessionId)
        }

    private data class AudioTrackSignature(
        val mimeType: String,
        val sampleRate: Int?,
        val channelCount: Int?,
        val durationUs: Long?
    )

    private const val AV_SYNC_TOLERANCE_US = 150_000L
}
