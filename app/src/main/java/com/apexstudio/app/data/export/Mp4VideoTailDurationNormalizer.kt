package com.apexstudio.app.data.export

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * Corrects a MediaMuxer MP4 whose final video sample inherited an overlong VFR gap.
 *
 * Android's MediaMuxer API documents that an empty BUFFER_FLAG_END_OF_STREAM sample
 * with the intended terminal PTS sets the duration of the preceding video sample.
 * This remux copies all encoded samples and timestamps unchanged; it does not decode
 * or re-encode media.
 */
internal object Mp4VideoTailDurationNormalizer {
    private const val DURATION_TOLERANCE_US = 150_000L
    private const val DEFAULT_BUFFER_BYTES = 8 * 1024 * 1024
    private const val MAX_BUFFER_BYTES = 64 * 1024 * 1024

    internal data class Result(
        val changed: Boolean,
        val targetDurationMs: Long,
        val sourceVideoDurationUs: Long,
        val outputVideoDurationUs: Long,
        val copiedSampleCount: Long = 0L,
        val lastVideoPresentationTimeUs: Long? = null
    )

    /**
     * If [file]'s video track extends beyond the target tolerance or contains a
     * sample at/after the exclusive target end, remux it with an explicit video
     * EOS timestamp and drop out-of-range video samples. The original file is
     * replaced only after the corrected MP4 is closed and verified.
     */
    fun normalize(
        file: File,
        targetDurationMs: Long,
        checkCancelled: () -> Unit = {}
    ): Result {
        require(file.isFile) { "Cannot normalize missing MP4 output" }
        require(targetDurationMs > 0L) { "Target video duration must be positive" }
        val targetDurationUs = Math.multiplyExact(targetDurationMs, 1_000L)

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var temporaryOutput: File? = null
        try {
            extractor.setDataSource(file.absolutePath)
            val formats = (0 until extractor.trackCount).map(extractor::getTrackFormat)
            val videoTrackIndices = formats.indices.filter { index ->
                formats[index].getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            }
            check(videoTrackIndices.size == 1) {
                "Expected one encoded video track, found ${videoTrackIndices.size}"
            }
            val videoTrackIndex = videoTrackIndices.single()
            val videoFormat = formats[videoTrackIndex]
            val sourceVideoDurationUs = videoFormat.durationUsOrZero()
            val sourcePtsBounds = readVideoPtsBounds(file, videoTrackIndex, checkCancelled)
            val endPtsUs = Math.addExact(sourcePtsBounds.firstPtsUs, targetDurationUs)
            if (sourceVideoDurationUs <= targetDurationUs + DURATION_TOLERANCE_US &&
                sourcePtsBounds.lastPtsUs < endPtsUs
            ) {
                return Result(
                    changed = false,
                    targetDurationMs = targetDurationMs,
                    sourceVideoDurationUs = sourceVideoDurationUs,
                    outputVideoDurationUs = sourceVideoDurationUs
                )
            }

            val parent = file.parentFile ?: throw IllegalStateException("MP4 output has no parent directory")
            temporaryOutput = File.createTempFile("apex_vfr_tail_", ".mp4", parent)
            if (!temporaryOutput.delete()) {
                throw IllegalStateException("Cannot prepare temporary MP4 duration-correction output")
            }

            muxer = MediaMuxer(temporaryOutput.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val rotation = videoFormat.integerOrNull(MediaFormat.KEY_ROTATION)
            if (rotation != null && rotation in setOf(90, 180, 270)) {
                muxer.setOrientationHint(rotation)
            }

            val outputTrackIndices = IntArray(formats.size)
            formats.forEachIndexed { index, format ->
                outputTrackIndices[index] = muxer.addTrack(format)
                extractor.selectTrack(index)
            }
            muxer.start()
            muxerStarted = true

            val declaredMaxInputSize = formats.mapNotNull { format ->
                format.integerOrNull(MediaFormat.KEY_MAX_INPUT_SIZE)?.takeIf { it > 0 }
            }.maxOrNull() ?: 0
            val initialBufferBytes = maxOf(
                DEFAULT_BUFFER_BYTES,
                declaredMaxInputSize.coerceAtMost(MAX_BUFFER_BYTES)
            )
            var sampleBuffer = ByteBuffer.allocateDirect(initialBufferBytes)
            val sampleInfo = MediaCodec.BufferInfo()
            var firstVideoPtsUs: Long? = null
            var lastVideoPtsUs: Long? = null
            var videoSampleCount = 0L
            var copiedSampleCount = 0L

            while (true) {
                checkCancelled()
                val inputTrackIndex = extractor.sampleTrackIndex
                if (inputTrackIndex < 0) break

                val presentationTimeUs = extractor.sampleTime
                if (inputTrackIndex == videoTrackIndex && presentationTimeUs >= endPtsUs) {
                    extractor.advance()
                    continue
                }
                val sampleSize = extractor.sampleSize
                if (sampleSize <= 0L || sampleSize > MAX_BUFFER_BYTES.toLong()) {
                    throw IllegalStateException("Unsupported encoded sample size: $sampleSize bytes")
                }
                if (sampleBuffer.capacity() < sampleSize.toInt()) {
                    sampleBuffer = ByteBuffer.allocateDirect(sampleSize.toInt())
                }
                sampleBuffer.clear()
                val sampleFlags = extractor.sampleFlags
                val bytesRead = extractor.readSampleData(sampleBuffer, 0)
                if (bytesRead != sampleSize.toInt()) {
                    throw IllegalStateException("Could not read complete encoded MP4 sample")
                }
                sampleBuffer.position(0)
                sampleBuffer.limit(bytesRead)
                sampleInfo.set(0, bytesRead, presentationTimeUs, sampleFlags)
                muxer.writeSampleData(outputTrackIndices[inputTrackIndex], sampleBuffer, sampleInfo)

                if (inputTrackIndex == videoTrackIndex) {
                    firstVideoPtsUs = firstVideoPtsUs?.let { minOf(it, presentationTimeUs) } ?: presentationTimeUs
                    lastVideoPtsUs = lastVideoPtsUs?.let { maxOf(it, presentationTimeUs) } ?: presentationTimeUs
                    videoSampleCount++
                }
                copiedSampleCount++
                extractor.advance()
            }

            check(videoSampleCount > 0L) { "Cannot set a terminal duration for an empty video track" }
            val videoStartPtsUs = checkNotNull(firstVideoPtsUs)
            check(videoStartPtsUs == sourcePtsBounds.firstPtsUs) {
                "Tail correction unexpectedly removed the first encoded video sample"
            }
            val lastPtsUs = checkNotNull(lastVideoPtsUs)
            check(endPtsUs > lastPtsUs) {
                "Target video end ${endPtsUs}us is not after the last encoded video PTS ${lastPtsUs}us"
            }

            // MediaMuxer documents this zero-byte EOS marker as the supported way
            // to set the original final sample's duration for MPEG-4 output.
            checkCancelled()
            val eosInfo = MediaCodec.BufferInfo().apply {
                set(0, 0, endPtsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
            muxer.writeSampleData(
                outputTrackIndices[videoTrackIndex],
                ByteBuffer.allocateDirect(0),
                eosInfo
            )
            muxer.stop()
            muxerStarted = false
            muxer.release()
            muxer = null
            extractor.release()

            val correctedDurationUs = readVideoTrackDurationUs(temporaryOutput)
            check(correctedDurationUs > 0L && abs(correctedDurationUs - targetDurationUs) <= DURATION_TOLERANCE_US) {
                "MP4 EOS duration correction produced ${correctedDurationUs}us video duration; " +
                    "expected ${targetDurationUs}us within ${DURATION_TOLERANCE_US}us"
            }
            replaceFile(file, temporaryOutput)
            temporaryOutput = null

            return Result(
                changed = true,
                targetDurationMs = targetDurationMs,
                sourceVideoDurationUs = sourceVideoDurationUs,
                outputVideoDurationUs = correctedDurationUs,
                copiedSampleCount = copiedSampleCount,
                lastVideoPresentationTimeUs = lastPtsUs
            )
        } finally {
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
            temporaryOutput?.let { if (it.exists()) it.delete() }
        }
    }

    private fun readVideoTrackDurationUs(file: File): Long {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            val videoTrack = (0 until extractor.trackCount).singleOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: throw IllegalStateException("Corrected MP4 has no unique video track")
            extractor.getTrackFormat(videoTrack).durationUsOrZero()
        } finally {
            runCatching { extractor.release() }
        }
    }

    private data class VideoPtsBounds(val firstPtsUs: Long, val lastPtsUs: Long)

    private fun readVideoPtsBounds(
        file: File,
        videoTrackIndex: Int,
        checkCancelled: () -> Unit
    ): VideoPtsBounds {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            extractor.selectTrack(videoTrackIndex)
            var firstPtsUs: Long? = null
            var lastPtsUs: Long? = null
            while (extractor.sampleTrackIndex >= 0) {
                checkCancelled()
                val presentationTimeUs = extractor.sampleTime
                firstPtsUs = firstPtsUs?.let { minOf(it, presentationTimeUs) } ?: presentationTimeUs
                lastPtsUs = lastPtsUs?.let { maxOf(it, presentationTimeUs) } ?: presentationTimeUs
                extractor.advance()
            }
            return VideoPtsBounds(
                firstPtsUs = checkNotNull(firstPtsUs) { "Cannot normalize an MP4 with no video samples" },
                lastPtsUs = checkNotNull(lastPtsUs) { "Cannot normalize an MP4 with no video samples" }
            )
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun replaceFile(original: File, replacement: File) {
        val parent = original.parentFile ?: throw IllegalStateException("MP4 output has no parent directory")
        val backup = File.createTempFile("apex_vfr_original_", ".mp4", parent)
        if (!backup.delete()) throw IllegalStateException("Cannot prepare MP4 replacement backup")
        if (!original.renameTo(backup)) throw IllegalStateException("Cannot stage original MP4 before duration correction")
        if (!replacement.renameTo(original)) {
            val restored = backup.renameTo(original)
            if (!restored) {
                throw IllegalStateException("Cannot install corrected MP4 or restore the original output")
            }
            throw IllegalStateException("Cannot install corrected MP4 output")
        }
        backup.delete()
    }

    private fun MediaFormat.durationUsOrZero(): Long =
        if (containsKey(MediaFormat.KEY_DURATION)) runCatching { getLong(MediaFormat.KEY_DURATION) }.getOrDefault(0L) else 0L

    private fun MediaFormat.integerOrNull(key: String): Int? =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null
}
