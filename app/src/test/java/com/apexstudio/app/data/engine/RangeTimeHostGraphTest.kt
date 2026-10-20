package com.apexstudio.app.data.engine

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Runs the Android filter graph against host FFmpeg. Only the encoder is swapped because the
 * host has x264 while the Android AAR has OpenH264. This is NOT device-runtime/encoder proof. */
class RangeTimeHostGraphTest {
    private fun run(vararg args: String): ByteArray {
        val log = File.createTempFile("range-time-process", ".log")
        try {
            val process = ProcessBuilder(*args).redirectError(log).start()
            val result = process.inputStream.readBytes()
            assertTrue("Process timed out: ${args.first()}", process.waitFor(45, TimeUnit.SECONDS))
            assertEquals(log.readText(), 0, process.exitValue())
            return result
        } finally { log.delete() }
    }
    private fun runHostGraph(arguments: Array<String>) {
        assertEquals("libopenh264", arguments[arguments.indexOf("-c:v") + 1])
        val hostArgs = arguments.copyOf()
        hostArgs[hostArgs.indexOf("-c:v") + 1] = "libx264"
        run("/usr/bin/ffmpeg", *hostArgs)
    }
    private fun probe(file: File, entry: String) = run("/usr/bin/ffprobe", "-v", "error", "-show_entries", entry,
        "-of", "default=noprint_wrappers=1:nokey=1", file.path).toString(Charsets.UTF_8).trim()

    @Test fun `production filter graph renders slow repeat reverse with audio and retains pixel ordering`() {
        assumeTrue(File("/usr/bin/ffmpeg").canExecute() && File("/usr/bin/ffprobe").canExecute())
        val folder = Files.createTempDirectory("apex-range-graph-").toFile()
        try {
            val input = File(folder, "source with spaces.mkv")
            run("/usr/bin/ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i",
                "nullsrc=s=64x48:r=10:d=4,geq=r='N*6':g='64':b='64',format=yuv420p",
                "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=48000:duration=4",
                "-c:v", "ffv1", "-c:a", "pcm_s16le", "-shortest", input.path)
            for (operation in RangeTimeOperation.entries) {
                val output = File(folder, "${operation.name}.mp4")
                val arguments = RangeTimeRenderer.buildCommand(input.path, output.path, 500L, 1_000L, 2_000L, 3_500L, operation, true)
                runHostGraph(arguments)
                val duration = probe(output, "format=duration").toDouble()
                val expected = if (operation == RangeTimeOperation.REVERSE) 3.0 else 4.0
                assertTrue("$operation duration $duration", kotlin.math.abs(duration - expected) < .25)
                assertTrue(probe(output, "stream=codec_type").contains("audio"))
                assertTrue(probe(output, "stream=codec_type").contains("video"))
            }
            val reverse = File(folder, "REVERSE.mp4")
            val red = run("/usr/bin/ffmpeg", "-v", "error", "-i", reverse.path,
                "-vf", "scale=1:1", "-pix_fmt", "rgb24", "-f", "rawvideo", "-")
                .toList().chunked(3).map { it.first().toInt() and 255 }
            assertTrue("Need decoded frames for before/range/after", red.size >= 29)
            // Before selection: ascending; selected second: descending; after selection: ascending.
            assertTrue("Head must remain forward: $red", red[4] > red[0])
            assertTrue("Reverse must really reverse pixels: $red", red[5] > red[13])
            assertTrue("Tail must remain forward: $red", red[25] > red[17])
            val silent = File(folder, "silent.mp4")
            val silentArgs = RangeTimeRenderer.buildCommand(input.path, silent.path, 0L, 0L, 1_000L, 2_000L, RangeTimeOperation.REPEAT, false)
            runHostGraph(silentArgs)
            assertFalse("Silent planner must not invent audio", probe(silent, "stream=codec_type").contains("audio"))
        } finally { folder.deleteRecursively() }
    }
}
