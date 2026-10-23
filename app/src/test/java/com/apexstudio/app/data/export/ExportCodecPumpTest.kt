package com.apexstudio.app.data.export

import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Test

class ExportCodecPumpTest {
    @Test
    fun `temporary empty polls after input EOS do not truncate delayed output`() {
        var now = 0L
        var polls = 0
        val frames = mutableListOf<Int>()
        pumpCodecUntilOutputEos(ExportCodecWatchdog(10_000L) { now }) {
            now += 10L
            polls++
            when (polls) {
                1 -> CodecPumpStep(progressed = true) // queue input EOS
                82 -> { frames.add(1); CodecPumpStep(progressed = true) }
                150 -> { frames.add(2); CodecPumpStep(progressed = true) }
                151 -> CodecPumpStep(outputEnded = true)
                else -> CodecPumpStep()
            }
        }
        assertEquals(151, polls)
        assertEquals(listOf(1, 2), frames)
    }

    @Test
    fun `permanently stalled encoder fails instead of returning partial success`() {
        var now = 0L
        var polls = 0
        val failure = runCatching {
            pumpCodecUntilOutputEos(ExportCodecWatchdog(100L) { now }) {
                now += 10L
                polls++
                CodecPumpStep()
            }
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals(10, polls)
    }

    @Test
    fun `real progress restarts inactivity limit but empty output does not`() {
        var now = 0L
        var polls = 0
        pumpCodecUntilOutputEos(ExportCodecWatchdog(100L) { now }) {
            polls++
            now += 30L
            CodecPumpStep(progressed = polls % 3 == 0, outputEnded = polls == 10)
        }
        assertEquals(10, polls)
    }

    @Test
    fun `EOS returns immediately even with no payload`() {
        var polls = 0
        pumpCodecUntilOutputEos {
            polls++
            CodecPumpStep(outputEnded = true)
        }
        assertEquals(1, polls)
    }

    @Test
    fun `pump failures propagate unchanged`() {
        val original = IllegalArgumentException("bad codec buffer")
        val actual = runCatching {
            pumpCodecUntilOutputEos { throw original }
        }.exceptionOrNull()
        assertSame(original, actual)
    }

    @Test
    fun `encoded copy honors declared offset size and preserves source cursor`() {
        val source = ByteBuffer.allocateDirect(8)
        source.put(byteArrayOf(90, 91, 1, 2, 3, 92, 93, 94))
        source.position(6)
        source.limit(7)
        assertEquals(listOf<Byte>(1, 2, 3), copyEncodedOutput(source, 2, 3).toList())
        assertEquals(6, source.position())
        assertEquals(7, source.limit())
    }

    @Test
    fun `empty encoded payload permits null buffer`() {
        assertEquals(0, copyEncodedOutput(null, 0, 0).size)
    }

    @Test
    fun `invalid encoded byte windows fail without allocation or overflow`() {
        val source = ByteBuffer.allocate(8)
        listOf(-1 to 2, 0 to -1, 7 to 2, Int.MAX_VALUE to 2, 2 to Int.MAX_VALUE).forEach {
            assertTrue(runCatching { copyEncodedOutput(source, it.first, it.second) }.isFailure)
        }
        assertTrue(runCatching { copyEncodedOutput(null, 0, 1) }.isFailure)
        assertEquals(0, source.position())
    }
}
