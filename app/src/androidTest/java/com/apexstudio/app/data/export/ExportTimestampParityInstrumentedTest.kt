package com.apexstudio.app.data.export

import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.apexstudio.app.domain.model.MediaClip
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import kotlin.math.abs

/** Real Media3/codec regression; CI compiles this test but needs a device to execute it. */
@RunWith(AndroidJUnit4::class)
@UnstableApi
class ExportTimestampParityInstrumentedTest {
    @Test
    fun selectedAndProjectExportsPreserveVfrTimingWithinRequestedAverageFrameRate() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val engine = ExportEngine(context)
        val id = UUID.randomUUID().toString()
        val source = File(context.cacheDir, "vfr-timestamp-source-$id.mp4")
        val selectedName = "vfr-selected-$id.mp4"
        val projectName = "vfr-project-$id.mp4"
        val outputDir = File(context.getExternalFilesDir(null), "ApexStudio_Exports")
        val trimStartMs = 200L
        val trimEndMs = 1_800L
        val fps = 24
        var selectedOutput: File? = null
        var projectOutput: File? = null

        fun copyFixture() {
            instrumentation.context.assets.open("export/vfr_gap_audio_fixture.mp4").use { input ->
                source.outputStream().use { output -> input.copyTo(output) }
            }
        }

        try {
            copyFixture()
            val sourceTrack = readVideoTrack(source)
            assertTrue("Fixture must contain deliberately sparse VFR timestamps", sourceTrack.maxGapUs > 500_000L)
            assertTrue("Fixture must retain its continuous audio track", hasTrack(source, "audio/"))
            val sourceAnchorsUs = sourceTrack.presentationTimesUs.filter {
                it >= trimStartMs * 1_000L && it < trimEndMs * 1_000L
            }
            assertTrue("Trimmed VFR interval must contain multiple source timestamp anchors", sourceAnchorsUs.size >= 4)

            val selectedConfig = ExportEngine.ExportConfig(
                resolution = "720p",
                fps = fps,
                aspectRatio = "16:9",
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs
            )
            engine.startExport(Uri.fromFile(source).toString(), selectedConfig, selectedName)
            val selectedResult = awaitTerminalState(engine)
            assertNull("Selected export must complete: ${selectedResult.error}", selectedResult.error)
            selectedOutput = File(requireNotNull(Uri.parse(selectedResult.outputUri).path))
            assertOutputTiming(
                label = "selected export",
                output = readVideoTrack(selectedOutput!!),
                expectedDurationUs = (trimEndMs - trimStartMs) * 1_000L,
                expectedSourceAnchorsUs = sourceAnchorsUs.map { it - trimStartMs * 1_000L },
                requestedFps = fps
            )

            val sourceDurationMs = sourceTrack.durationUs / 1_000L
            assertTrue("Fixture duration must extend beyond the trim", sourceDurationMs > trimEndMs)
            val clips = (0..1).map { index ->
                MediaClip(
                    id = "vfr-timestamp-$id-$index",
                    name = "VFR timestamp fixture $index",
                    uri = Uri.fromFile(source).toString(),
                    durationMs = sourceDurationMs,
                    trimStartMs = trimStartMs,
                    trimEndMs = trimEndMs
                )
            }
            val plan = ProjectCompositionTimeline.build(clips)
            assertEquals("Two trimmed clips must form a sequential project plan", 2, plan.entries.size)
            assertEquals("Project duration must equal the sum of retained clip durations", 3_200L, plan.durationMs)

            engine.startProjectExport(
                clips = clips,
                config = ExportEngine.ExportConfig(resolution = "720p", fps = fps, aspectRatio = "16:9"),
                outputFileName = projectName
            )
            val projectResult = awaitTerminalState(engine)
            assertNull("Whole-project export must complete: ${projectResult.error}", projectResult.error)
            projectOutput = File(requireNotNull(Uri.parse(projectResult.outputUri).path))
            val expectedProjectAnchorsUs = plan.entries.flatMap { entry ->
                sourceAnchorsUs.map { sourceTimeUs ->
                    entry.timelineStartMs * 1_000L + sourceTimeUs - entry.sourceStartMs * 1_000L
                }
            }
            assertOutputTiming(
                label = "whole-project export",
                output = readVideoTrack(projectOutput!!),
                expectedDurationUs = plan.durationMs * 1_000L,
                expectedSourceAnchorsUs = expectedProjectAnchorsUs,
                requestedFps = fps
            )
        } finally {
            instrumentation.runOnMainSync { engine.release() }
            source.delete()
            selectedOutput?.delete()
            projectOutput?.delete()
            File(outputDir, selectedName).delete()
            File(outputDir, projectName).delete()
            File(outputDir, selectedName.removeSuffix(".mp4") + "_music.mp4").delete()
            File(outputDir, projectName.removeSuffix(".mp4") + "_music.mp4").delete()
        }
    }

    private suspend fun awaitTerminalState(engine: ExportEngine): ExportEngine.ExportProgressState =
        withTimeout(180_000L) {
            engine.exportState.first { it.error != null || it.outputUri != null }
        }

    private fun assertOutputTiming(
        label: String,
        output: VideoTrack,
        expectedDurationUs: Long,
        expectedSourceAnchorsUs: List<Long>,
        requestedFps: Int
    ) {
        val timestamps = output.presentationTimesUs
        assertFalse("$label must contain video samples", timestamps.isEmpty())
        assertFalse("$label must not contain negative PTS", timestamps.any { it < 0L })
        assertEquals("$label must not contain duplicate presentation timestamps", timestamps.size, timestamps.distinct().size)
        val targetIntervalUs = 1_000_000.0 / requestedFps
        // Media3's default FrameDropEffect targets an average frame rate, not a minimum gap between
        // every pair of PTS. A VFR source can therefore keep a short local burst when the following
        // source gap is long. Bound total cadence over the output duration, allowing one boundary frame.
        assertTrue("$label must report a positive video duration", output.durationUs > 0L)
        val outputIntervals = timestamps.size - 1L
        val maximumIntervalsAtTargetCadence =
            (requestedFps.toLong() * output.durationUs + 999_999L) / 1_000_000L
        assertTrue(
            "$label averaged too many video intervals for the requested $requestedFps-fps target: " +
                "$outputIntervals intervals over ${output.durationUs}us (maximum $maximumIntervalsAtTargetCadence " +
                "after one-frame boundary tolerance)",
            outputIntervals <= maximumIntervalsAtTargetCadence
        )

        val durationToleranceUs = 150_000L
        assertTrue(
            "$label duration ${output.durationUs}us must stay within ${durationToleranceUs}us of planned $expectedDurationUs us",
            abs(output.durationUs - expectedDurationUs) <= durationToleranceUs
        )
        // Frame dropping at the requested average rate may discard individual source samples. Each
        // temporal anchor should still land within half an output frame of its expected trim- and
        // sequence-relative time; project anchors are checked in both clip segments.
        val anchorToleranceUs = (targetIntervalUs / 2.0).toLong() + 5_000L
        expectedSourceAnchorsUs.forEach { expectedUs ->
            val nearestDistanceUs = timestamps.minOf { abs(it - expectedUs) }
            assertTrue(
                "$label lost or shifted source timestamp anchor $expectedUs us; nearest output PTS is $nearestDistanceUs us away",
                nearestDistanceUs <= anchorToleranceUs
            )
        }
    }

    private fun hasTrack(file: File, mimePrefix: String): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            (0 until extractor.trackCount).any { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty().startsWith(mimePrefix)
            }
        } finally {
            extractor.release()
        }
    }

    private fun readVideoTrack(file: File): VideoTrack {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val index = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("video/")
            } ?: error("${file.name} has no video track")
            val format = extractor.getTrackFormat(index)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else 0L
            extractor.selectTrack(index)
            val timestamps = mutableListOf<Long>()
            while (extractor.sampleTrackIndex >= 0) {
                timestamps += extractor.sampleTime
                if (!extractor.advance()) break
            }
            val presentationTimes = timestamps.sorted()
            val gaps = presentationTimes.zipWithNext { first, second -> second - first }
            return VideoTrack(durationUs, presentationTimes, gaps.maxOrNull() ?: 0L)
        } finally {
            extractor.release()
        }
    }

    private data class VideoTrack(
        val durationUs: Long,
        val presentationTimesUs: List<Long>,
        val maxGapUs: Long
    )
}
