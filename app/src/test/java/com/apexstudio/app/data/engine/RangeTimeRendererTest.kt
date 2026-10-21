package com.apexstudio.app.data.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RangeTimeRendererTest {
    @Test fun productionCommandUsesTheEncoderActuallyBundledInNonGplAndroidAar() {
        val args = command(RangeTimeOperation.SLOW, hasAudio = true)
        assertEquals("libopenh264", args[args.indexOf("-c:v") + 1])
        assertFalse(args.contains("libx264"))
        assertFalse(args.contains("-crf"))
        assertFalse(args.contains("-preset"))
        assertEquals("8M", args[args.indexOf("-b:v") + 1])
    }

    @Test fun slowKeepsBeforeAndAfterAndSlowsVideoAndAudio() {
        val graph = graph(RangeTimeOperation.SLOW, hasAudio = true)
        assertTrue(graph.contains("[0:v]trim=start=1.000:end=2.000"))
        assertTrue(graph.contains("[0:v]trim=start=2.000:end=4.000"))
        assertTrue(graph.contains("[0:v]trim=start=4.000:end=5.000"))
        assertTrue(graph.contains("setpts=2*(PTS-STARTPTS)"))
        assertTrue(graph.contains("atempo=0.5"))
        assertTrue(graph.contains("[0:a:0]atrim=start=2.000:end=4.000"))
        assertTrue(graph.contains("concat=n=3:v=1:a=1 [vout][aout]"))
        assertTrue("Audio gaps/tails are padded and clipped to each video segment", graph.contains("apad,atrim=duration=4.000"))
    }

    @Test fun audioGraphSelectsOneDeterministicStreamWhenInputHasMultipleAudioTracks() {
        val graph = graph(RangeTimeOperation.REVERSE, hasAudio = true)
        assertTrue(graph.contains("[0:a:0]"))
        assertFalse(graph.contains("[0:a]atrim"))
        assertTrue(graph.contains("areverse,asetpts=PTS-STARTPTS"))
    }

    @Test fun repeatDuplicatesOnlySelectedRangeExactlyOnce() {
        val graph = graph(RangeTimeOperation.REPEAT, hasAudio = true)
        assertEquals(2, Regex("\\[0:v]trim=start=2\\.000:end=4\\.000").findAll(graph).count())
        assertTrue(graph.contains("concat=n=4:v=1:a=1 [vout][aout]"))
        assertTrue(graph.contains("[0:v]trim=start=1.000:end=2.000"))
        assertTrue(graph.contains("[0:v]trim=start=4.000:end=5.000"))
    }

    @Test fun reverseReversesOnlySelectedVideoAndAudio() {
        val graph = graph(RangeTimeOperation.REVERSE, hasAudio = true)
        assertTrue(graph.contains(",reverse,setpts=PTS-STARTPTS"))
        assertTrue(graph.contains(",areverse,asetpts=PTS-STARTPTS"))
        assertTrue(graph.contains("concat=n=3:v=1:a=1 [vout][aout]"))
        assertFalse(graph.contains("concat=n=4"))
    }

    @Test fun silentSourceHasNoAudioGraphOrAudioMap() {
        val graph = graph(RangeTimeOperation.REPEAT, hasAudio = false)
        assertFalse(graph.contains("[0:a]"))
        assertFalse(graph.contains("aout"))
        val args = command(RangeTimeOperation.REPEAT, hasAudio = false)
        assertEquals(1, args.count { it == "-map" })
        assertFalse(args.contains("-c:a"))
    }

    @Test fun plannerUsesArgumentArrayAndDoesNotShellQuotePaths() {
        val args = RangeTimeRenderer.buildCommand(
            inputPath = "/local videos/input clip.mp4",
            outputPath = "/app files/derived clip.mp4",
            clipStartMs = 0,
            rangeStartMs = 0,
            rangeEndMs = 1_000,
            clipEndMs = 2_000,
            operation = RangeTimeOperation.SLOW,
            hasAudio = true
        )
        assertTrue(args.contains("/local videos/input clip.mp4"))
        assertTrue(args.contains("/app files/derived clip.mp4"))
        assertTrue(args.contains("-filter_complex"))
        assertTrue(args.contains("[vout]"))
        assertTrue(args.contains("[aout]"))
        assertFalse(args.any { it.startsWith("'") || it.startsWith("\"") })
        assertTrue(args.last() == "/app files/derived clip.mp4")
    }

    @Test fun selectionMayCoverWholeTrimWithoutEmptySegments() {
        val graph = RangeTimeRenderer.buildFilterGraph(
            clipStartMs = 500, rangeStartMs = 500, rangeEndMs = 1_500, clipEndMs = 1_500,
            operation = RangeTimeOperation.REVERSE, hasAudio = false
        )
        assertTrue(graph.startsWith("[0:v]trim=start=0.500:end=1.500"))
        assertTrue(graph.contains("concat=n=1:v=1:a=0 [vout]"))
    }

    private fun graph(operation: RangeTimeOperation, hasAudio: Boolean) =
        RangeTimeRenderer.buildFilterGraph(1_000, 2_000, 4_000, 5_000, operation, hasAudio)

    private fun command(operation: RangeTimeOperation, hasAudio: Boolean) =
        RangeTimeRenderer.buildCommand("/in.mp4", "/out.mp4", 1_000, 2_000, 4_000, 5_000, operation, hasAudio).toList()
}
