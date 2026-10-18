package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.ai.subtitles.TimingPrecision
import com.apexstudio.app.ai.subtitles.WordCue
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.SpeedCurveMath
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
}
