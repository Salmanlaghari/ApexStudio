package com.apexstudio.app.data.captions

import com.apexstudio.app.domain.model.TextOverlayKaraoke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionFillerDetectionTest {
    private fun word(id: String, text: String, start: Long, end: Long) = TimedCaptionWord(
        id = id,
        word = text,
        startMs = start,
        endMs = end,
        clipId = "clip",
        overlayId = id
    )

    @Test
    fun `default list uses whole case insensitive words and retains exact measured ranges`() {
        val findings = CaptionFillerDetector.detect(
            listOf(
                word("um", "Um", 120L, 260L),
                word("umbrella", "umbrella", 300L, 520L),
                word("er", "ER", 600L, 710L),
                word("ah", "ah,", 715L, 760L),
                word("uhmish", "uhmish", 730L, 900L)
            )
        )

        assertEquals(listOf("um", "er", "ah"), findings.map { it.timedWord.word.lowercase() })
        assertEquals(listOf(120L to 260L, 600L to 710L, 715L to 760L),
            findings.map { it.timedWord.startMs to it.timedWord.endMs })
        assertEquals(listOf(FillerMatchKind.WORD_LIST, FillerMatchKind.WORD_LIST, FillerMatchKind.WORD_LIST),
            findings.map { it.kind })
    }

    @Test
    fun `second copy of a repeated word is separately flagged and list repeats are deduplicated`() {
        val findings = CaptionFillerDetector.detect(
            listOf(
                word("one", "the", 0L, 100L),
                word("two", "the", 100L, 180L),
                word("three", "um", 190L, 240L),
                word("four", "um", 240L, 310L)
            )
        )

        assertEquals(listOf("two", "three", "four"), findings.map { it.id })
        assertEquals(
            listOf(FillerMatchKind.REPEATED_WORD, FillerMatchKind.WORD_LIST, FillerMatchKind.WORD_LIST_AND_REPEATED),
            findings.map { it.kind }
        )
        assertEquals(listOf(100L to 180L, 190L to 240L, 240L to 310L),
            findings.map { it.timedWord.startMs to it.timedWord.endMs })
    }

    @Test
    fun `editable vocabulary is lowercased and rejects phrases or invalid entries`() {
        assertEquals(listOf("yeah", "yep"), CaptionFillerDetector.normalizeList(listOf(" Yeah ", "yeah", "two words", "!", "YEP")))
        assertTrue(CaptionFillerDetector.detect(listOf(word("a", "yeah", 10L, 20L)), listOf("yeah")).isNotEmpty())
    }

    @Test
    fun `karaoke phrase removal updates every phrase overlay and its highlight offsets`() {
        val phrase = "I um like it"
        val fingerprint = TextOverlayKaraoke.fingerprint(phrase)
        fun caption(id: String, start: Long, end: Long, wordStart: Int, wordEnd: Int) =
            com.apexstudio.app.domain.model.TextOverlay.of(text = phrase).copy(
                id = id,
                presetId = "caption_auto",
                startMs = start,
                endMs = end,
                highlightStartUtf16 = wordStart,
                highlightEndUtf16 = wordEnd,
                highlightSourceFingerprint = fingerprint
            )
        val clip = com.apexstudio.app.domain.model.MediaClip(
            id = "clip", name = "clip", uri = "file:///clip.mp4", durationMs = 1_000L,
            trimEndMs = 1_000L,
            textOverlays = listOf(
                caption("i", 0L, 99L, 0, 1),
                caption("um", 100L, 199L, 2, 4),
                caption("like", 200L, 299L, 5, 9),
                caption("it", 300L, 399L, 10, 12)
            )
        )
        val filler = CaptionFillerDetector.detect(CaptionFillerWordStream.fromMainVideoClips(listOf(clip))).single()
        val updated = CaptionFillerCaptionEditor.removeSelectedWords(clip, listOf(filler.timedWord))

        assertEquals(listOf("i", "like", "it"), updated.map { it.id })
        assertTrue(updated.all { it.text == "I like it" })
        assertEquals(listOf(0 to 1, 2 to 6, 7 to 9), updated.map {
            TextOverlayKaraoke.validRange(it)!!.let { range -> range.startUtf16 to range.endUtf16Exclusive }
        })
        assertTrue(updated.all { it.highlightSourceFingerprint == TextOverlayKaraoke.fingerprint("I like it") })
    }
}
