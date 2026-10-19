package com.apexstudio.app.ai.mediapipe

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaPipePortraitEffectsTest {
    @Test
    fun `process calls serialize with each other and close is idempotent`() {
        val fake = ProbeSegmenter(delayMillis = 40)
        val effects = MediaPipePortraitEffects(fake)
        val frame = bitmap(2, 2, Color.RED)
        val pool = Executors.newFixedThreadPool(3)
        try {
            val start = CountDownLatch(1)
            val results = (1..2).map {
                pool.submit {
                    start.await()
                    effects.process(frame, PortraitEffect.Solid(Color.BLUE)).also { it.recycle() }
                }
            }
            start.countDown()
            results.forEach { it.get(5, TimeUnit.SECONDS) }
            assertEquals(1, fake.maxConcurrent.get())

            effects.close()
            effects.close()
            assertEquals(1, fake.closeCount.get())
            try {
                effects.process(frame, PortraitEffect.Solid(Color.BLUE))
                throw AssertionError("Expected a closed processor to reject process")
            } catch (expected: IllegalStateException) {
                assertTrue(expected.message.orEmpty().contains("closed"))
            }
        } finally {
            pool.shutdownNow()
            frame.recycle()
            effects.close()
        }
    }

    @Test
    fun `rejects malformed and non finite confidence masks`() {
        val frame = bitmap(2, 2, Color.RED)
        try {
            listOf(
                PortraitConfidenceMask(2, 2, floatArrayOf(1f)),
                PortraitConfidenceMask(2, 2, floatArrayOf(1f, 1f, Float.NaN, 1f)),
                PortraitConfidenceMask(2, 2, floatArrayOf(1f, 1f, -0.1f, 1f)),
                PortraitConfidenceMask(Int.MAX_VALUE, 2, FloatArray(0)),
            ).forEach { badMask ->
                val effects = MediaPipePortraitEffects(ProbeSegmenter(mask = badMask))
                try {
                    effects.process(frame, PortraitEffect.Solid(Color.BLUE))
                    throw AssertionError("Expected malformed mask to be rejected: $badMask")
                } catch (expected: IllegalArgumentException) {
                    // Invalid dimensions, length, and probability values are rejected before compositing.
                } finally {
                    effects.close()
                }
            }
        } finally {
            frame.recycle()
        }
    }

    @Test
    fun `one pixel blur does not recycle or alias the caller frame`() {
        val frame = bitmap(1, 1, Color.MAGENTA)
        val effects = MediaPipePortraitEffects(ProbeSegmenter(mask = PortraitConfidenceMask(1, 1, floatArrayOf(1f))))
        try {
            val output = effects.process(frame, PortraitEffect.Blur(1f))
            try {
                assertNotSame(frame, output)
                assertFalse(frame.isRecycled)
                assertEquals(Color.MAGENTA, output.getPixel(0, 0))
            } finally {
                output.recycle()
            }
            assertFalse(frame.isRecycled)
        } finally {
            effects.close()
            frame.recycle()
        }
    }

    @Test
    fun `cancellation while inference is running discards the output`() = runBlocking {
        val enteredInference = CompletableDeferred<Unit>()
        val allowInferenceToReturn = CompletableDeferred<Unit>()
        val fake = object : PortraitSegmenter {
            override fun segment(frame: Bitmap): PortraitConfidenceMask {
                enteredInference.complete(Unit)
                runBlocking { allowInferenceToReturn.await() }
                return PortraitConfidenceMask(1, 1, floatArrayOf(1f))
            }
            override fun close() = Unit
        }
        val frame = bitmap(1, 1, Color.GREEN)
        val effects = MediaPipePortraitEffects(fake)
        val job = async(Dispatchers.Default) { effects.processCancellable(frame, PortraitEffect.Solid(Color.BLUE)) }
        try {
            withTimeout(5_000) { enteredInference.await() }
            job.cancel()
            allowInferenceToReturn.complete(Unit)
            try {
                job.await()
                throw AssertionError("Expected cancellation to discard the processed result")
            } catch (expected: CancellationException) {
                assertFalse(frame.isRecycled)
            }
        } finally {
            allowInferenceToReturn.complete(Unit)
            effects.close()
            frame.recycle()
        }
    }

    private fun bitmap(width: Int, height: Int, color: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    private class ProbeSegmenter(
        private val mask: PortraitConfidenceMask = PortraitConfidenceMask(1, 1, floatArrayOf(1f)),
        private val delayMillis: Long = 0,
    ) : PortraitSegmenter {
        private val active = AtomicInteger()
        val maxConcurrent = AtomicInteger()
        val closeCount = AtomicInteger()

        override fun segment(frame: Bitmap): PortraitConfidenceMask {
            val nowActive = active.incrementAndGet()
            maxConcurrent.updateAndGet { maxOf(it, nowActive) }
            try {
                if (delayMillis > 0) Thread.sleep(delayMillis)
                return mask
            } finally {
                active.decrementAndGet()
            }
        }

        override fun close() {
            closeCount.incrementAndGet()
        }
    }
}
