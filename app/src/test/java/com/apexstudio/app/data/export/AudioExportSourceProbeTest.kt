package com.apexstudio.app.data.export

import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class AudioExportSourceProbeTest {
    private fun source(uri: String, position: Long) = ExportEngine.AudioExportSource(
        uri = uri, trimStartMs = 200L, trimEndMs = 1_200L, timelinePositionMs = position
    )

    @Test fun `actual audio MIME is required rather than video or image kind`() {
        assertTrue(AudioExportSourceProbe.isAudioMime("audio/mp4a-latm"))
        assertTrue(AudioExportSourceProbe.isAudioMime("audio/opus"))
        assertFalse(AudioExportSourceProbe.isAudioMime("video/avc"))
        assertFalse(AudioExportSourceProbe.isAudioMime("image/png"))
        assertFalse(AudioExportSourceProbe.isAudioMime(null))
    }

    @Test fun `silent and image sources are omitted without shifting surviving placement`() {
        val first = source("content://video/with-audio", 0L)
        val image = source("content://image/overlay", 700L)
        val silent = source("content://video/silent", 1_400L)
        val voice = source("file:///voice.m4a", 2_300L)
        val selection = selectAudioExportSources(listOf(first, image, silent, voice)) {
            it == first.uri || it == voice.uri
        }
        assertEquals(listOf(first, voice), selection.sources)
        assertSame(first, selection.sources[0])
        assertSame(voice, selection.sources[1])
        assertEquals(2_300L, selection.sources[1].timelinePositionMs)
        assertEquals(2, selection.skippedSources)
        assertTrue(selection.warning!!.contains("2 sources"))
    }

    @Test fun `complete selection has no warning while fully silent input stays empty`() {
        val input = listOf(source("file:///audio.m4a", 10L))
        assertNull(selectAudioExportSources(input) { true }.warning)
        val empty = selectAudioExportSources(input) { false }
        assertTrue(empty.sources.isEmpty())
        assertEquals(1, empty.skippedSources)
        assertTrue(empty.warning!!.contains("1 source contained no readable audio"))
    }

    @Test fun `cancellation is never converted to a skipped source`() {
        val expected = CancellationException("cancel source probing")
        val actual = runCatching {
            selectAudioExportSources(listOf(source("file:///audio.m4a", 0L))) { throw expected }
        }.exceptionOrNull()
        assertSame(expected, actual)
    }
}
