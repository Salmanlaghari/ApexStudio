package com.apexstudio.app.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportCodecGuardTest {

    private class FakeCodec {
        var configureCalls = 0
        var startCalls = 0
        var releaseCalls = 0

        fun configure(shouldFail: Boolean) {
            configureCalls++
            if (shouldFail) throw IllegalStateException("configure failed")
        }

        fun start(shouldFail: Boolean) {
            startCalls++
            if (shouldFail) throw IllegalStateException("start failed")
        }

        fun release() {
            releaseCalls++
        }
    }

    @Test
    fun `configure failure releases created codec exactly once`() {
        val codec = FakeCodec()
        val failure = runCatching {
            setupWithCleanup(
                resource = codec,
                setup = {
                    it.configure(shouldFail = true)
                    it.start(shouldFail = false)
                },
                cleanup = { it.release() }
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(1, codec.configureCalls)
        assertEquals(0, codec.startCalls)
        assertEquals(1, codec.releaseCalls)
    }

    @Test
    fun `start failure releases configured codec exactly once`() {
        val codec = FakeCodec()
        val failure = runCatching {
            setupWithCleanup(
                resource = codec,
                setup = {
                    it.configure(shouldFail = false)
                    it.start(shouldFail = true)
                },
                cleanup = { it.release() }
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(1, codec.configureCalls)
        assertEquals(1, codec.startCalls)
        assertEquals(1, codec.releaseCalls)
    }

    @Test
    fun `watchdog allows delayed drain before inactivity limit`() {
        var nowMs = 1_000L
        val watchdog = ExportCodecWatchdog(timeoutMs = 10_000L, nowMs = { nowMs })

        nowMs += 9_999L // output dequeue returned TRY_AGAIN_LATER; no EOS yet
        watchdog.throwIfTimedOut()
        assertEquals(1L, watchdog.remainingMs())

        nowMs += 1L
        assertTrue(runCatching { watchdog.throwIfTimedOut() }.isFailure)
    }

    @Test
    fun `queued input and delayed output progress restart inactivity timeout`() {
        var nowMs = 0L
        val watchdog = ExportCodecWatchdog(timeoutMs = 10_000L, nowMs = { nowMs })

        nowMs += 9_000L
        watchdog.throwIfTimedOut() // temporary no-output delay is tolerated
        watchdog.progressed() // a queued input or output/format event advances codec
        assertEquals(10_000L, watchdog.remainingMs())

        nowMs += 9_999L
        watchdog.throwIfTimedOut()
        assertFalse(watchdog.remainingMs() <= 0L)
        nowMs += 1L
        assertTrue(runCatching { watchdog.throwIfTimedOut() }.isFailure)
    }

    @Test
    fun `successful setup returns resource without cleanup`() {
        val codec = FakeCodec()
        val result = setupWithCleanup(
            resource = codec,
            setup = {
                it.configure(shouldFail = false)
                it.start(shouldFail = false)
            },
            cleanup = { it.release() }
        )

        assertSame(codec, result)
        assertEquals(0, codec.releaseCalls)
    }
}
