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
