package com.apexstudio.app.ai.subtitles

import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CtcAlignmentValidationTest {
    private fun align(
        targets: IntArray = intArrayOf(1, 2),
        mapping: IntArray = intArrayOf(0, 1),
        words: List<String> = listOf("hello", "world"),
        offset: Long = 0L,
        frames: Array<FloatArray> = Array(4) { floatArrayOf(-1f, -1f, -1f) },
        checkCancelled: () -> Unit = {},
    ) = CtcForcedAligner.align(frames, 0, targets, mapping, words, 20f, offset, checkCancelled)

    @Test fun blankCannotBeATranscriptToken() {
        assertThrows(IllegalArgumentException::class.java) { align(targets = intArrayOf(0, 2)) }
    }

    @Test fun everyWordMustHaveTokensInTranscriptOrder() {
        assertThrows(IllegalArgumentException::class.java) { align(mapping = intArrayOf(0, 0)) }
        assertThrows(IllegalArgumentException::class.java) { align(mapping = intArrayOf(1, 0)) }
        assertThrows(IllegalArgumentException::class.java) { align(mapping = intArrayOf(-1, -1)) }
        assertThrows(IllegalArgumentException::class.java) { align(words = listOf("", "world")) }
    }

    @Test fun repeatedTokensNeedASeparatingBlankFrame() {
        assertThrows(IllegalArgumentException::class.java) {
            align(targets = intArrayOf(1, 1), frames = Array(2) { floatArrayOf(-1f, -1f, -1f) })
        }
        val result = align(targets = intArrayOf(1, 1), frames = arrayOf(
            floatArrayOf(-10f, 0f, -10f), floatArrayOf(0f, -10f, -10f), floatArrayOf(-10f, 0f, -10f)
        ))
        assertEquals(2, result.words.size)
        assertEquals(0L, result.words[0].startMs)
        assertEquals(40L, result.words[1].startMs)
    }

    @Test fun rawLogitsAndInvalidSourceOffsetsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { align(offset = -1L) }
        assertThrows(IllegalArgumentException::class.java) { align(offset = Long.MAX_VALUE) }
        assertThrows(IllegalArgumentException::class.java) {
            align(frames = Array(4) { floatArrayOf(0f, 10f, 0f) })
        }
    }

    @Test fun cancellationIsCheckedBeforeViterbiAllocation() {
        var checks = 0
        assertThrows(CancellationException::class.java) {
            align(checkCancelled = { checks++; throw CancellationException("cancel") })
        }
        assertEquals(1, checks)
    }
}
