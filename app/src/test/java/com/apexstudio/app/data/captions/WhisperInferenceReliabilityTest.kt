package com.apexstudio.app.data.captions

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class WhisperInferenceReliabilityTest {
    @Test fun concurrentCaptionRequestsShareOneInferenceSlot() = runBlocking {
        val active = AtomicInteger()
        val peak = AtomicInteger()
        (1..4).map {
            async(Dispatchers.Default) {
                WhisperInferenceGate.withLock {
                    val now = active.incrementAndGet()
                    peak.updateAndGet { old -> maxOf(old, now) }
                    try { delay(10) } finally { active.decrementAndGet() }
                }
            }
        }.awaitAll()
        assertEquals(1, peak.get())
    }

    @Test fun cancellationCallbackIsPolledDuringSpectrogramWork() {
        var checks = 0
        try {
            WhisperMelSpectrogram.compute(FloatArray(16_000)) {
                checks++
                if (checks == 2) throw java.util.concurrent.CancellationException("test cancellation")
            }
            throw AssertionError("Expected cooperative cancellation")
        } catch (_: java.util.concurrent.CancellationException) {
            assertEquals(2, checks)
        }
    }
}
