package com.apexstudio.app.ai.subtitles

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression on actual CPU emissions from the pinned wav2vec2 model and LibriSpeech audio. */
class EnglishWordAlignmentEmissionsTest {
    @Test fun alignsTheRealEnglishFixtureToUnequalBoundedWordTimes() {
        val metadata = resourceText("ai/english-alignment-logprobs.json")
        assertTrue(metadata.contains("\"modelSha256\": \"${BundledEnglishAlignmentModel.SHA256}\""))
        assertTrue(metadata.contains("LibriSpeech dev-clean 1272-128104-0000, CC-BY-4.0"))
        val expectedLogProbHash = jsonString(metadata, "logProbSha256")
        val frames = jsonInt(metadata, "frames")
        val vocabularySize = jsonInt(metadata, "vocabularySize")
        val frameStrideMs = jsonInt(metadata, "frameStrideMs")
        val firstFrameCenterMs = jsonInt(metadata, "firstFrameCenterMs")
        val durationMs = jsonInt(metadata, "durationMs")
        val transcript = jsonString(metadata, "transcript")

        val bytes = resourceBytes("ai/english-alignment-logprobs.f32le")
        assertEquals(expectedLogProbHash, bytes.sha256())
        assertEquals(frames * vocabularySize * Float.SIZE_BYTES, bytes.size)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val logProbabilities = Array(frames) {
            FloatArray(vocabularySize) { buffer.get() }
        }
        assertEquals(0, buffer.remaining())

        val tokens = tokenizeEnglishTranscript(transcript)
        val result = CtcForcedAligner.align(
            logProbabilities = logProbabilities,
            blankId = 0,
            targetTokenIds = tokens.tokenIds,
            wordIndexForToken = tokens.wordIndices,
            wordTexts = tokens.displayedWords,
            frameDurationMs = frameStrideMs.toFloat(),
            startOffsetMs = firstFrameCenterMs.toLong(),
        )

        assertEquals(transcript.split(' '), result.words.map { it.text })
        assertEquals(17, result.words.size)
        assertTrue("fixture alignment score ${result.score}", result.score >= 0.08f)
        assertTrue(result.words.all { cue ->
            cue.precision == TimingPrecision.CTC_FORCED_WORD_ALIGNMENT &&
                cue.startMs >= 0L && cue.endMs <= durationMs.toLong() && cue.startMs < cue.endMs &&
                cue.confidence != null && cue.confidence >= 0.08f
        })
        assertTrue(result.words.zipWithNext().all { (a, b) -> a.startMs <= b.startMs })
        assertTrue("genuine CTC spans should not be an equal split",
            result.words.map { it.endMs - it.startMs }.distinct().size >= 5)
        assertFalse("fixture must contain multiple distinct word spans",
            result.words.map { it.startMs to it.endMs }.distinct().size <= 1)
        assertNotNull(result.words.first().confidence)
    }

    private fun resourceBytes(path: String): ByteArray =
        requireNotNull(javaClass.classLoader?.getResourceAsStream(path)) { "Missing test resource $path" }.use { it.readBytes() }

    private fun resourceText(path: String): String = resourceBytes(path).toString(Charsets.UTF_8)

    private fun jsonInt(json: String, key: String): Int =
        Regex("\\\"${Regex.escape(key)}\\\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toInt()
            ?: error("Missing integer metadata field $key")

    private fun jsonString(json: String, key: String): String =
        Regex("\\\"${Regex.escape(key)}\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"")
            .find(json)?.groupValues?.get(1) ?: error("Missing string metadata field $key")

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this)
        .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
