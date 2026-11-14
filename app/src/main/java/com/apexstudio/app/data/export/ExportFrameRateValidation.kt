package com.apexstudio.app.data.export

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import kotlin.math.ceil
import kotlin.math.roundToLong

/** Reported source format only: missing FPS is normal for some VFR/container sources. */
internal data class SourceVideoFormatAudit(val reportedFps: Float?, val width: Int?, val height: Int?) {
    fun diagnosticSummary(): String =
        "sourceReportedFps=${reportedFps ?: "unknown"} sourceWidth=${width ?: "unknown"} sourceHeight=${height ?: "unknown"} metadataOnly=true"
}

internal fun inspectSourceVideoFormat(context: Context, inputUri: String): SourceVideoFormatAudit {
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(context, Uri.parse(inputUri), null)
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            if (!format.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("video/")) continue
            val fps = if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                // Container extractors may expose this Number as either an integer or float.
                runCatching { format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }
                    .getOrElse { runCatching { format.getFloat(MediaFormat.KEY_FRAME_RATE) }.getOrDefault(Float.NaN) }
                    ?.takeIf { it.isFinite() && it > 0f }
            } else null
            fun dimension(key: String): Int? = if (format.containsKey(key))
                runCatching { format.getInteger(key) }.getOrNull()?.takeIf { it > 0 } else null
            return SourceVideoFormatAudit(fps, dimension(MediaFormat.KEY_WIDTH), dimension(MediaFormat.KEY_HEIGHT))
        }
        return SourceVideoFormatAudit(null, null, null)
    } finally {
        extractor.release()
    }
}

/** Actual source-track PTS evidence; null cadence means do not convert a potentially VFR source. */
internal data class SourceFrameCadenceAudit(
    val sampleCount: Int,
    val constantFrameRateFps: Float?,
    val minIntervalUs: Long?,
    val maxIntervalUs: Long?
) {
    fun diagnosticSummary(): String =
        "sourceSamples=$sampleCount constantFrameRate=${constantFrameRateFps != null} " +
            "constantFrameRateFps=${constantFrameRateFps ?: "unknown"} " +
            "sourceMinIntervalUs=${minIntervalUs ?: "unknown"} sourceMaxIntervalUs=${maxIntervalUs ?: "unknown"}"
}

private const val MAX_SOURCE_CADENCE_SAMPLES = 500_000

/** Reads sample timestamps only, without decoding frames; oversized/unreadable tracks stay unknown. */
internal fun inspectSourceFrameCadence(
    context: Context,
    inputUri: String,
    reportedFps: Float?,
    cancellationCheck: () -> Unit = {}
): SourceFrameCadenceAudit {
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(context, Uri.parse(inputUri), null)
        val videoTrack = (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("video/")
        } ?: return SourceFrameCadenceAudit(0, null, null, null)
        extractor.selectTrack(videoTrack)
        val timestampsUs = ArrayList<Long>()
        while (extractor.sampleTrackIndex >= 0) {
            if (timestampsUs.size >= MAX_SOURCE_CADENCE_SAMPLES) {
                return SourceFrameCadenceAudit(timestampsUs.size, null, null, null)
            }
            if (timestampsUs.size % 1_024 == 0) cancellationCheck()
            timestampsUs += extractor.sampleTime
            if (!extractor.advance()) break
        }
        return auditSourceFrameTimestamps(timestampsUs, reportedFps)
    } finally {
        extractor.release()
    }
}

/** Pure PTS classifier for constant-cadence source evidence; decode-order regressions are sorted. */
internal fun auditSourceFrameTimestamps(
    timestampsUs: List<Long>,
    reportedFps: Float?
): SourceFrameCadenceAudit {
    if (timestampsUs.size < 3 || timestampsUs.any { it < 0L }) {
        return SourceFrameCadenceAudit(timestampsUs.size, null, null, null)
    }
    val presentationTimes = timestampsUs.sorted()
    val intervals = presentationTimes.zipWithNext { first, second -> second - first }
    if (intervals.isEmpty() || intervals.any { it <= 0L }) {
        return SourceFrameCadenceAudit(timestampsUs.size, null, null, null)
    }
    val sortedIntervals = intervals.sorted()
    val medianIntervalUs = sortedIntervals[sortedIntervals.size / 2]
    val measuredFps = 1_000_000f / medianIntervalUs
    val reported = reportedFps?.takeIf { it.isFinite() && it > 0f }
    val metadataMatches = reported == null ||
        kotlin.math.abs(measuredFps - reported) <= maxOf(0.5f, reported * 0.02f)
    val intervalToleranceUs = maxOf(1_000L, (medianIntervalUs * 0.05).toLong())
    val constantCadence = metadataMatches && intervals.all {
        kotlin.math.abs(it - medianIntervalUs) <= intervalToleranceUs
    }
    return SourceFrameCadenceAudit(
        sampleCount = timestampsUs.size,
        constantFrameRateFps = measuredFps.takeIf { constantCadence },
        minIntervalUs = intervals.minOrNull(),
        maxIntervalUs = intervals.maxOrNull()
    )
}

/** Facts read from actual muxed video samples; requested 60 fps is checked as a cadence contract. */
internal data class EncodedFrameRateAudit(
    val sampleCount: Int,
    val firstPresentationTimeUs: Long?,
    val lastPresentationTimeUs: Long?,
    /** Number of timestamp regressions in demux/decode order; B-frame reordering is valid. */
    val decodeOrderTimestampRegressionCount: Int,
    /** Equal presentation timestamps after sorting by presentation time. */
    val duplicateTimestampCount: Int,
    val negativeTimestampCount: Int,
    val largeTimestampGapCount: Int,
    val maxTimestampGapUs: Long,
    val minPositiveTimestampGapUs: Long,
    val expectedCfrSampleCount: Long?,
    val encodedAverageFps: Double?,
    val requestedFps: Int,
    val outputDurationMs: Long?,
    val transformerFrameCountDelta: Long?,
    val videoMimeType: String? = null,
    val videoWidth: Int? = null,
    val videoHeight: Int? = null
) {
    /** Null for other targets; 60 fps must be verified from final sample timestamps. */
    val strict60FpsCadenceError: String?
        get() {
            if (requestedFps != 60) return null
            val average = encodedAverageFps
                ?: return "The final video could not be verified at 60 fps because its duration is unavailable."
            val expected = expectedCfrSampleCount
                ?: return "The final video could not be verified at 60 fps because its sample count is unavailable."
            val targetIntervalUs = 1_000_000.0 / 60.0
            val minimumSamples = (expected - 1L).coerceAtLeast(1L)
            val maximumSamples = expected + 1L
            val minimumGapUs = (targetIntervalUs * 0.75).toLong()
            val maximumGapUs = ceil(targetIntervalUs * 1.5).toLong()
            val cadenceIsVerified = sampleCount.toLong() in minimumSamples..maximumSamples &&
                average in 59.5..60.5 &&
                sampleCount > 1 &&
                minPositiveTimestampGapUs >= minimumGapUs &&
                maxTimestampGapUs <= maximumGapUs
            if (cadenceIsVerified) return null
            val measured = "%.2f".format(java.util.Locale.ROOT, average)
            return "The final video did not meet the requested 60 fps cadence " +
                "(measured ${measured} fps; PTS gaps ${minPositiveTimestampGapUs}–${maxTimestampGapUs} µs). " +
                "Try 30 fps or a different output resolution."
        }

    val warning: String?
        get() = when {
            sampleCount <= 0 -> "The exported file has no readable video frames."
            duplicateTimestampCount > 0 || negativeTimestampCount > 0 ->
                "The exported video has invalid frame timestamps."
            strict60FpsCadenceError != null -> strict60FpsCadenceError
            transformerFrameCountDelta != null && kotlin.math.abs(transformerFrameCountDelta) >
                maxOf(2L, (sampleCount * 0.02).roundToLong()) ->
                "The exported video frame count differs from the renderer's report."
            encodedAverageFps != null && requestedFps > 0 &&
                encodedAverageFps > requestedFps * 1.05 ->
                "The encoded video frame rate exceeds the requested output rate."
            else -> null
        }

    fun diagnosticSummary(): String = buildString {
        append("samples=$sampleCount")
        expectedCfrSampleCount?.let { append(" expectedCfrSamples=$it") }
        encodedAverageFps?.let { append(" encodedAverageFps=${"%.2f".format(java.util.Locale.ROOT, it)}") }
        append(" requestedFps=$requestedFps")
        append(" decodeOrderTimestampRegressions=$decodeOrderTimestampRegressionCount")
        append(" duplicatePresentationTimestamps=$duplicateTimestampCount")
        append(" negativeTimestamps=$negativeTimestampCount")
        append(" gapsOverOneAndHalfFrameIntervals=$largeTimestampGapCount")
        append(" maxGapUs=$maxTimestampGapUs")
        outputDurationMs?.let { append(" outputDurationMs=$it") }
        transformerFrameCountDelta?.let { append(" transformerFrameCountDelta=$it") }
        videoMimeType?.let { append(" videoMimeType=$it") }
        if (videoWidth != null && videoHeight != null) append(" videoSize=${videoWidth}x$videoHeight")
    }
}

/** Pure audit logic kept separate from Android demuxing for deterministic unit tests. */
internal fun auditEncodedFrameTimestamps(
    timestampsUs: List<Long>,
    outputDurationMs: Long?,
    requestedFps: Int,
    transformerFrameCount: Int? = null
): EncodedFrameRateAudit {
    var decodeOrderRegressions = 0
    var negative = 0
    for (index in timestampsUs.indices) {
        val timestamp = timestampsUs[index]
        if (timestamp < 0L) negative++
        if (index > 0) {
            // MediaExtractor visits samples in decode order. B-frames can legitimately
            // present earlier than a preceding decoded frame, so this is diagnostic only.
            if (timestamp < timestampsUs[index - 1]) decodeOrderRegressions++
        }
    }
    val presentationTimes = timestampsUs.sorted()
    var duplicates = 0
    var largeGaps = 0
    var maxGap = 0L
    var minPositiveGap = Long.MAX_VALUE
    val targetFps = requestedFps.coerceIn(24, 120)
    val targetIntervalUs = 1_000_000.0 / targetFps
    for (index in 1 until presentationTimes.size) {
        val gap = presentationTimes[index] - presentationTimes[index - 1]
        if (gap == 0L) duplicates++
        else if (gap > 0L) {
            maxGap = maxOf(maxGap, gap)
            minPositiveGap = minOf(minPositiveGap, gap)
            if (gap > targetIntervalUs * 1.5) largeGaps++
        }
    }
    val boundedDuration = outputDurationMs?.takeIf { it > 0L }
    val fps = requestedFps.takeIf { it > 0 }?.coerceIn(24, 120)
    val expected = if (boundedDuration != null && fps != null) {
        ceil(boundedDuration * fps / 1000.0).toLong()
    } else null
    val averageFps = if (boundedDuration != null && presentationTimes.isNotEmpty()) {
        presentationTimes.size * 1000.0 / boundedDuration
    } else null
    val delta = transformerFrameCount?.takeIf { it >= 0 }?.let { it.toLong() - timestampsUs.size }
    return EncodedFrameRateAudit(
        sampleCount = timestampsUs.size,
        firstPresentationTimeUs = presentationTimes.firstOrNull(),
        lastPresentationTimeUs = presentationTimes.lastOrNull(),
        decodeOrderTimestampRegressionCount = decodeOrderRegressions,
        duplicateTimestampCount = duplicates,
        negativeTimestampCount = negative,
        largeTimestampGapCount = largeGaps,
        maxTimestampGapUs = maxGap,
        minPositiveTimestampGapUs = minPositiveGap.takeIf { it != Long.MAX_VALUE } ?: 0L,
        expectedCfrSampleCount = expected,
        encodedAverageFps = averageFps,
        requestedFps = fps ?: 0,
        outputDurationMs = boundedDuration,
        transformerFrameCountDelta = delta
    )
}

/**
 * Reads actual MP4 video sample timestamps; it does not decode pixels or infer duplicate image
 * content. Timestamp gaps are evidence only of sparse presentation timestamps, not of dropped
 * source frames. Decoder failures are surfaced by Media3 Transformer error callbacks; this audit
 * does not inspect source frame-rate metadata, independently decode the source, or compare its
 * sample cadence with the output.
 */
internal fun inspectEncodedVideo(
    context: Context,
    file: File,
    requestedFps: Int,
    transformerFrameCount: Int? = null,
    transformerDurationMs: Long? = null
): EncodedFrameRateAudit {
    require(file.isFile && file.length() > 0L) { "Export output is missing or empty" }
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(context, Uri.fromFile(file), null)
        var videoTrack = -1
        var trackDurationMs: Long? = null
        var videoMimeType: String? = null
        var videoWidth: Int? = null
        var videoHeight: Int? = null
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("video/")) {
                videoTrack = index
                videoMimeType = mime
                videoWidth = if (format.containsKey(MediaFormat.KEY_WIDTH))
                    runCatching { format.getInteger(MediaFormat.KEY_WIDTH) }.getOrNull() else null
                videoHeight = if (format.containsKey(MediaFormat.KEY_HEIGHT))
                    runCatching { format.getInteger(MediaFormat.KEY_HEIGHT) }.getOrNull() else null
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    trackDurationMs = (format.getLong(MediaFormat.KEY_DURATION) / 1000L).takeIf { it > 0L }
                }
                break
            }
        }
        require(videoTrack >= 0) { "Export output has no readable video track" }
        extractor.selectTrack(videoTrack)
        val timestamps = ArrayList<Long>()
        while (extractor.sampleTrackIndex >= 0) {
            val timeUs = extractor.sampleTime
            timestamps += timeUs
            if (!extractor.advance()) break
        }
        val durationMs = trackDurationMs
            ?: transformerDurationMs?.takeIf { it > 0L }
        return auditEncodedFrameTimestamps(
            timestampsUs = timestamps,
            outputDurationMs = durationMs,
            requestedFps = requestedFps,
            transformerFrameCount = transformerFrameCount
        ).copy(videoMimeType = videoMimeType, videoWidth = videoWidth, videoHeight = videoHeight)
    } finally {
        extractor.release()
    }
}

/** Returns an actionable mismatch when the final muxed stream is not the exact requested 8K format. */
internal fun eightKOutputFormatMismatch(
    expectedMimeType: String,
    expectedWidth: Int,
    expectedHeight: Int,
    actualMimeType: String?,
    actualWidth: Int?,
    actualHeight: Int?
): String? {
    if (actualMimeType.equals(expectedMimeType, ignoreCase = true) &&
        actualWidth == expectedWidth && actualHeight == expectedHeight
    ) return null
    return "The encoded stream did not match the requested 8K output. Expected " +
        "$expectedWidth×$expectedHeight $expectedMimeType; got " +
        "${actualWidth ?: "unknown"}×${actualHeight ?: "unknown"} ${actualMimeType ?: "unknown MIME"}."
}
