package com.apexstudio.app.data.export

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayAudioRenderLifecycleTest {
    @Test fun `synchronous completion resumes once despite duplicate callbacks`() = runBlocking {
        var cancellations = 0
        awaitCancellableAudioRender { complete, fail ->
            complete()
            complete()
            fail(IllegalStateException("late error"))
            return@awaitCancellableAudioRender { cancellations++ }
        }
        assertEquals(0, cancellations)
    }

    @Test fun `startup failure reaches the caller`() = runBlocking {
        val expected = IllegalStateException("cannot start codec")
        try {
            awaitCancellableAudioRender { _, _ -> throw expected }
            fail("startup must fail")
        } catch (actual: IllegalStateException) {
            assertEquals(expected.message, actual.message)
            assertTrue(actual === expected || actual.cause === expected)
        }
    }

    @Test fun `asynchronous failure reaches the caller exactly once`() = runBlocking {
        val expected = IllegalStateException("decode failed")
        try {
            awaitCancellableAudioRender { complete, failure ->
                failure(expected)
                complete()
                return@awaitCancellableAudioRender {}
            }
            fail("render must fail")
        } catch (actual: IllegalStateException) {
            assertEquals(expected.message, actual.message)
            assertTrue(actual === expected || actual.cause === expected)
        }
    }

    @Test fun `cancellation during startup cancels renderer once when handle becomes available`() = runBlocking {
        var cancellations = 0
        val child = launch {
            val owner = currentCoroutineContext().job
            awaitCancellableAudioRender { _, _ ->
                owner.cancel()
                return@awaitCancellableAudioRender { cancellations++ }
            }
        }
        child.join()
        assertTrue(child.isCancelled)
        assertEquals(1, cancellations)
    }

    @Test fun `selected export caps overlay audio sources at the long rendered video boundary`() {
        val fourHoursMs = 4L * 60 * 60 * 1_000L
        val sources = listOf(
            ExportEngine.AudioExportSource(uri = "file:///start.m4a", timelinePositionMs = 0L),
            ExportEngine.AudioExportSource(uri = "file:///inside.m4a", timelinePositionMs = fourHoursMs - 1L),
            ExportEngine.AudioExportSource(uri = "file:///at-end.m4a", timelinePositionMs = fourHoursMs),
            ExportEngine.AudioExportSource(uri = "file:///after.m4a", timelinePositionMs = fourHoursMs + 1L)
        )

        val bounded = selectOverlayAudioWithinDuration(sources, fourHoursMs)
        assertEquals(sources.take(2), bounded.sources)
        assertEquals(2, bounded.omittedCount)

        val unknownDuration = selectOverlayAudioWithinDuration(sources, null)
        assertEquals(sources, unknownDuration.sources)
        assertEquals(0, unknownDuration.omittedCount)
    }

    @Test fun `mixer cancellation occurs before opening any file or native codec`() {
        val expected = CancellationException("owner cancelled")
        try {
            MusicExportMixer.mixMusicIntoVideo(
                RuntimeEnvironment.getApplication(), File("missing-input.mp4"),
                emptyList(), File("unused-output.mp4")
            ) { throw expected }
            fail("cancellation must propagate")
        } catch (actual: CancellationException) {
            assertSame(expected, actual)
        }
    }
}
