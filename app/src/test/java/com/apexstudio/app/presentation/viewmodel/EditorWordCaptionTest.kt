package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.ai.subtitles.TimingPrecision
import com.apexstudio.app.ai.subtitles.WordCue
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.SpeedCurveMath
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.domain.model.TextOverlayKaraoke
import com.apexstudio.app.data.captions.WhisperCaptionEngine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorWordCaptionTest {
    private fun word(text: String = "Hello", start: Long = 100L, end: Long = 400L) =
        WordCue(text, start, end, TimingPrecision.CTC_FORCED_WORD_ALIGNMENT, .85f)

    @Test fun genuineIntervalsRemainMeasuredNotEquallySplit() {
        val segments = alignedCaptionSegments(listOf(word(), word("world", 700L, 1_200L)))
        assertEquals(listOf("Hello", "world"), segments.map { it.text })
        assertEquals(listOf(100L, 700L), segments.map { it.startMs })
        assertEquals(listOf(400L, 1_200L), segments.map { it.endMs })
    }

    @Test fun coarseWhisperTimingCannotMasqueradeAsWordTiming() {
        assertThrows(IllegalArgumentException::class.java) {
            alignedCaptionSegments(listOf(word().copy(precision = TimingPrecision.WHISPER_SEGMENT)))
        }
    }

    @Test fun invalidIntervalsFailAtomically() {
        val invalid = listOf(word(start = -1), word(end = 100), word(text = " "),
            word().copy(confidence = null), word().copy(confidence = Float.NaN),
            word().copy(confidence = 1.1f))
        invalid.forEach { bad ->
            assertThrows(IllegalArgumentException::class.java) { alignedCaptionSegments(listOf(word(), bad)) }
        }
        assertThrows(IllegalArgumentException::class.java) { alignedCaptionSegments(emptyList()) }
    }

    @Test fun overlappingOrReversedWordsFailInsteadOfReorderingTheTranscript() {
        assertThrows(IllegalArgumentException::class.java) {
            alignedCaptionSegments(listOf(word(), word("overlap", 300L, 800L)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            alignedCaptionSegments(listOf(word("later", 700L, 900L), word()))
        }
    }

    @Test fun wordIntervalsUseTheSameSpeedMappedPreviewExportOverlays() {
        val clip = MediaClip(id = "v", name = "v", uri = "file:///v.mp4", durationMs = 10_000L,
            trimStartMs = 2_000L, trimEndMs = 8_000L, speedPoints = SpeedCurveMath.flat(2f))
        val overlays = captionOverlaysFor(clip, alignedCaptionSegments(listOf(word())))
        assertEquals(50L, overlays.single().startMs)
        assertEquals(200L, overlays.single().endMs)
        assertEquals("caption_auto", overlays.single().presetId)
        assertEquals("Hello", overlays.single().text)
    }

    @Test fun wordModeRequiresExplicitEnglishAndLanguageChangeResetsIt() {
        val vm = EditorViewModel()
        vm.setCaptionTimingMode(CaptionTimingMode.ENGLISH_WORDS)
        assertEquals(CaptionTimingMode.PHRASE, vm.captionUi.value.timingMode)
        vm.setCaptionLanguage("en")
        vm.setCaptionTimingMode(CaptionTimingMode.ENGLISH_WORDS)
        assertEquals(CaptionTimingMode.ENGLISH_WORDS, vm.captionUi.value.timingMode)
        vm.setCaptionLanguage("ur")
        assertEquals(CaptionTimingMode.PHRASE, vm.captionUi.value.timingMode)
        assertEquals("ur", vm.captionUi.value.language)
    }

    @Test fun changingTimingModeInvalidatesLateWorkerProgress() {
        val vm = EditorViewModel()
        vm._captionUi.value = CaptionUiState(language = "en", requestId = "old", phase = CaptionPhase.TRANSCRIBING)
        vm.setCaptionTimingMode(CaptionTimingMode.ENGLISH_WORDS)
        vm.updateCaptionRequest("old") { it.copy(progress01 = .9f, phase = CaptionPhase.DONE) }
        assertEquals(CaptionPhase.IDLE, vm.captionUi.value.phase)
        assertNull(vm.captionUi.value.requestId)
        assertEquals(CaptionTimingMode.ENGLISH_WORDS, vm.captionUi.value.timingMode)
    }

    @Test fun karaokeKeepsTheFullWhisperPhraseAndUsesUnevenSpeedMappedHalfOpenWordWindows() {
        val clip = MediaClip(id = "v", name = "v", uri = "file:///v.mp4", durationMs = 10_000L,
            trimStartMs = 0L, trimEndMs = 6_000L, speedPoints = SpeedCurveMath.flat(2f))
        val phrase = WhisperCaptionEngine.CaptionSegment("Hello, brave world!", 0L, 2_000L)
        val words = listOf(word("Hello", 100L, 250L), word("brave", 600L, 850L), word("world", 1_200L, 1_500L))
        val overlays = karaokeCaptionOverlaysFor(clip, listOf(phrase), words)
        val highlighted = overlays.filter { TextOverlayKaraoke.validRange(it) != null }
        assertTrue(overlays.all { it.text == "Hello, brave world!" })
        assertEquals(listOf(50L, 300L, 600L), highlighted.map { it.startMs })
        assertEquals(listOf(124L, 424L, 749L), highlighted.map { it.endMs })
        assertFalse(highlighted[0].isActiveAt(125L)) // inclusive persisted end represents the measured [start,end)
        assertEquals(listOf(0 to 5, 7 to 12, 13 to 18), highlighted.map { it.highlightStartUtf16!! to it.highlightEndUtf16!! })
        for (time in 0L until 1_000L) {
            assertEquals("Exactly one full phrase should be visible at $time", 1, overlays.count { it.isActiveAt(time) })
        }
        assertNull(TextOverlayKaraoke.validRange(overlays.single { it.isActiveAt(200L) }))
        assertNull(TextOverlayKaraoke.validRange(overlays.single { it.isActiveAt(800L) }))
        assertTrue(overlays.none { it.isActiveAt(1_000L) })
    }

    @Test fun constantMainSpeedMapsKaraokeWordBoundariesAtTwoXAndHalfX() {
        val phrase = WhisperCaptionEngine.CaptionSegment("Hello", 1_000L, 3_000L)
        val words = listOf(word("Hello", 1_500L, 2_500L))
        val fastClip = MediaClip(id = "fast", name = "fast", uri = "file:///fast.mp4", durationMs = 10_000L,
            trimStartMs = 2_000L, trimEndMs = 8_000L, speedMultiplier = 2f)
        val slowClip = fastClip.copy(id = "slow", speedMultiplier = 0.5f)

        val fast = karaokeCaptionOverlaysFor(fastClip, listOf(phrase), words)
            .single { TextOverlayKaraoke.validRange(it) != null }
        val slow = karaokeCaptionOverlaysFor(slowClip, listOf(phrase), words)
            .single { TextOverlayKaraoke.validRange(it) != null }
        assertEquals(750L, fast.startMs)
        assertEquals(1_249L, fast.endMs)
        assertEquals(3_000L, slow.startMs)
        assertEquals(4_999L, slow.endMs)
    }

    @Test fun ambiguousOrMissingPhraseWordGroupsAreRejected() {
        val clip = MediaClip(id = "v", name = "v", uri = "file:///v.mp4", durationMs = 3_000L,
            trimStartMs = 0L, trimEndMs = 3_000L)
        val phrase = WhisperCaptionEngine.CaptionSegment("hello world", 0L, 1_000L)
        assertThrows(IllegalArgumentException::class.java) {
            karaokeCaptionOverlaysFor(clip, listOf(phrase), listOf(word("hello", 100L, 200L)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            karaokeCaptionOverlaysFor(clip, listOf(phrase), listOf(word("other", 100L, 200L), word("world", 400L, 500L)))
        }
    }

    @Test fun staleEditedTextDisablesHighlightAndKaraokeFieldsPersist() {
        val source = "A brave phrase"
        val overlay = TextOverlay.of(text = source).copy(highlightStartUtf16 = 2, highlightEndUtf16 = 7,
            highlightSourceFingerprint = TextOverlayKaraoke.fingerprint(source))
        assertEquals(2, TextOverlayKaraoke.validRange(overlay)?.startUtf16)
        assertNull(TextOverlayKaraoke.validRange(overlay.copy(text = "Manually changed")))
        val restored = Json.decodeFromString<TextOverlay>(Json.encodeToString(overlay))
        assertEquals(overlay.highlightStartUtf16, restored.highlightStartUtf16)
        assertEquals(overlay.highlightEndUtf16, restored.highlightEndUtf16)
        assertEquals(overlay.highlightSourceFingerprint, restored.highlightSourceFingerprint)
        assertEquals(0xFFFFFF00L, restored.highlightColorArgb)
    }

    @Test fun legacyWindowsStayInclusiveAndNewProjectRangesAreHalfOpen() {
        val automatic = TextOverlay.of(text = "phrase", presetId = "caption_auto").copy(startMs = 100L, endMs = 199L)
        assertFalse(TextOverlayKaraoke.isVisibleForEditor(automatic, -1L))
        assertFalse(TextOverlayKaraoke.isVisibleForEditor(automatic, 99L))
        assertTrue(TextOverlayKaraoke.isVisibleForEditor(automatic, 100L))
        assertTrue(TextOverlayKaraoke.isVisibleForEditor(automatic, 199L))
        assertFalse(TextOverlayKaraoke.isVisibleForEditor(automatic, 200L))
        assertFalse(TextOverlayKaraoke.isVisibleForEditor(automatic.copy(presetId = null), 99L))
        assertTrue(TextOverlayKaraoke.isVisibleForEditor(automatic.copy(presetId = null), 100L))
        assertTrue(TextOverlayKaraoke.isVisibleForEditor(automatic.copy(presetId = null), 199L))
        val projectRange = automatic.copy(rangeEndExclusive = true)
        assertTrue(TextOverlayKaraoke.isVisibleForEditor(projectRange, 198L))
        assertFalse(TextOverlayKaraoke.isVisibleForEditor(projectRange, 199L))
        assertFalse(TextOverlayKaraoke.isVisibleForEditor(projectRange.copy(presetId = null), 199L))
        val unhighlightedGap = automatic.copy(presetId = null,
            highlightSourceFingerprint = TextOverlayKaraoke.fingerprint("phrase"))
        assertNull(TextOverlayKaraoke.validRange(unhighlightedGap))
        assertFalse(TextOverlayKaraoke.isVisibleForEditor(unhighlightedGap, 200L))
    }
}
