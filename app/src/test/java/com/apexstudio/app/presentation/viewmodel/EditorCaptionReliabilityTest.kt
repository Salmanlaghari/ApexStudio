package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.data.captions.WhisperCaptionEngine.CaptionSegment
import com.apexstudio.app.domain.model.*
import kotlinx.coroutines.flow.update
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorCaptionReliabilityTest {
    private fun clip() = MediaClip(id = "clip", name = "Video", uri = "file:///video.mp4",
        durationMs = 20_000L, trimStartMs = 5_000L, trimEndMs = 15_000L)
    private fun editor(clip: MediaClip = clip()) = EditorViewModel().also { vm ->
        vm._state.update { it.copy(project = Project(id = "project", name = "Project", clips = listOf(clip), durationMs = clip.effectiveDurationMs())) }
    }
    private fun snapshot(vm: EditorViewModel) = CaptionSourceSnapshot("project", vm.state.value.project!!.clips.single())
    private val segments = listOf(CaptionSegment("Hello", 1_000L, 3_000L))

    @Test fun sourceTrimOffsetIsNotAddedToClipRelativeCaptions() {
        val cues = captionOverlaysFor(clip(), segments)
        assertEquals(1_000L, cues.single().startMs)
        assertEquals(3_000L, cues.single().endMs)
    }
    @Test fun constantSpeedCurveMapsBothCaptionBoundaries() {
        val cues = captionOverlaysFor(clip().copy(speedPoints = SpeedCurveMath.flat(2f)), segments)
        assertEquals(500L, cues.single().startMs)
        assertEquals(1_500L, cues.single().endMs)
    }
    @Test fun constantMainSpeedMapsPhraseCaptionsAtTwoXAndHalfX() {
        val fast = captionOverlaysFor(clip().copy(speedMultiplier = 2f), segments).single()
        val slow = captionOverlaysFor(clip().copy(speedMultiplier = 0.5f), segments).single()

        assertEquals(500L, fast.startMs)
        assertEquals(1_500L, fast.endMs)
        assertEquals(2_000L, slow.startMs)
        assertEquals(6_000L, slow.endMs)
    }
    @Test fun rampUsesOriginalTrimmedSpanForBothBoundaries() {
        val c = clip().copy(speedPoints = listOf(SpeedPoint(0f, .5f), SpeedPoint(1f, 3f)))
        val cue = captionOverlaysFor(c, segments).single()
        assertEquals(SpeedCurveMath.sourceToOutputMs(1_000L, 10_000L, c.speedPoints), cue.startMs)
        assertEquals(SpeedCurveMath.sourceToOutputMs(3_000L, 10_000L, c.speedPoints), cue.endMs)
    }
    @Test fun invalidEmptyAndOutOfRangeSegmentsAreNotInserted() {
        val cues = captionOverlaysFor(clip(), listOf(
            CaptionSegment("clipped", -200L, 500L), CaptionSegment("end", 9_000L, Long.MAX_VALUE),
            CaptionSegment("", 1L, 200L), CaptionSegment("reversed", 4_000L, 3_000L),
            CaptionSegment("past end", 11_000L, 12_000L), CaptionSegment("negative", -10L, -1L),
        ))
        assertEquals(2, cues.size)
        assertEquals(0L, cues.first().startMs)
        assertEquals(10_000L, cues.last().endMs)
    }
    @Test fun trimChangeRejectsOldResultWithoutHistoryEntry() {
        val vm = editor(); val before = snapshot(vm)
        vm.updateClip("clip", persist = false) { it.copy(trimStartMs = 6_000L) }
        assertNull(vm.applyCaptionResult(before, segments))
        assertTrue(vm.undoStack.isEmpty())
        assertTrue(vm.state.value.project!!.clips.single().textOverlays.isEmpty())
    }
    @Test fun sourceSpeedAndProjectChangesInvalidateSnapshot() {
        val original = clip(); val before = CaptionSourceSnapshot("project", original)
        assertFalse(before.matches("other", original))
        assertFalse(before.matches("project", original.copy(uri = "file:///different.mp4")))
        assertFalse(before.matches("project", original.copy(speedPoints = SpeedCurveMath.flat(2f))))
        assertFalse(before.matches("project", original.copy(durationMs = 30_000L)))
    }
    @Test fun removedClipCannotReceiveLateResult() {
        val vm = editor(); val before = snapshot(vm)
        vm._state.update { it.copy(project = it.project!!.copy(clips = emptyList())) }
        assertNull(vm.applyCaptionResult(before, segments))
        assertTrue(vm.undoStack.isEmpty())
    }
    @Test fun manualTextAddedDuringInferenceSurvivesAndApplyIsUndoable() {
        val vm = editor(); val before = snapshot(vm)
        val manual = TextOverlay.of(text = "Keep me")
        vm.updateClip("clip", persist = false) { it.copy(textOverlays = listOf(manual)) }
        assertEquals(1, vm.applyCaptionResult(before, segments))
        assertEquals(manual, vm.state.value.project!!.clips.single().textOverlays.first())
        assertEquals(listOf(manual), vm.undoStack.single().single().textOverlays)
    }
    @Test fun editedAutoCaptionIsNotSilentlyOverwritten() {
        val auto = TextOverlay.of(text = "Old", presetId = "caption_auto")
        val vm = editor(clip().copy(textOverlays = listOf(auto))); val before = snapshot(vm)
        vm.updateClip("clip", persist = false) { it.copy(textOverlays = listOf(auto.copy(text = "Edited"))) }
        assertNull(vm.applyCaptionResult(before, segments))
        assertTrue(vm.undoStack.isEmpty())
    }
    @Test fun cancelledOrReplacedRequestCannotPublishLateNativeProgress() {
        val vm = editor()
        vm._captionUi.value = CaptionUiState(phase = CaptionPhase.TRANSCRIBING, requestId = "old")
        vm.cancelAutoCaptions()
        vm.updateCaptionRequest("old") { it.copy(phase = CaptionPhase.TRANSCRIBING, progress01 = .8f) }
        assertEquals(CaptionPhase.IDLE, vm.captionUi.value.phase)
        vm._captionUi.value = CaptionUiState(phase = CaptionPhase.TRANSCRIBING, requestId = "new")
        vm.updateCaptionRequest("old") { it.copy(phase = CaptionPhase.FAILED, message = "old failed") }
        assertEquals("new", vm.captionUi.value.requestId)
        assertEquals(CaptionPhase.TRANSCRIBING, vm.captionUi.value.phase)
    }
    @Test fun clearCancelsRequestAndKeepsManualTextWithUndo() {
        val manual = TextOverlay.of(text = "Manual")
        val auto = TextOverlay.of(text = "Auto", presetId = "caption_auto")
        val vm = editor(clip().copy(textOverlays = listOf(manual, auto)))
        vm._captionUi.value = CaptionUiState(phase = CaptionPhase.TRANSCRIBING, requestId = "old")
        vm.clearAutoCaptions("clip")
        assertNull(vm.captionUi.value.requestId)
        assertEquals(listOf(manual), vm.state.value.project!!.clips.single().textOverlays)
        assertEquals(listOf(manual, auto), vm.undoStack.single().single().textOverlays)
    }
}
