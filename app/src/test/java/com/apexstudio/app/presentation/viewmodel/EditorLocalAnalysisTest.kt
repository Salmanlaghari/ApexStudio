package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.ai.autoreframe.AutoCropSuggestion
import com.apexstudio.app.ai.autoreframe.NormalizedCrop
import com.apexstudio.app.ai.tflite.SceneCutSuggestion
import com.apexstudio.app.ai.tflite.SmartTrimSuggestion
import com.apexstudio.app.domain.model.*
import kotlinx.coroutines.flow.update
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorLocalAnalysisTest {
    private fun editor(speed: Float = 1f): EditorViewModel {
        val clip = MediaClip(id = "source", name = "Video", uri = "file:///video.mp4",
            durationMs = 20_000L, trimStartMs = 2_000L, trimEndMs = 12_000L,
            type = ClipType.VIDEO, speedMultiplier = speed)
        return EditorViewModel().also { vm ->
            vm._state.update { it.copy(project = Project(id = "analysis", name = "Analysis", clips = listOf(clip), durationMs = 10_000L), selectedClipId = clip.id) }
            vm._localAnalysis.value = LocalAnalysisUiState(
                status = LocalAnalysisStatus.READY, progress = 1f, clipId = clip.id, sourceUri = clip.uri,
                sourceStartMs = clip.trimStartMs, sourceEndMs = clip.trimEndMs,
                sceneCuts = listOf(SceneCutSuggestion("cut-6000", 6_000L, .9f)),
                smartTrims = listOf(SmartTrimSuggestion("trim-4000-8000", 4_000L, 8_000L, .8f, "Heuristic")),
                autoCrop = AutoCropSuggestion(NormalizedCrop(.25f, 0f, .75f, 1f), .5f, .5f, "Luma-gradient rule"))
        }
    }

    @Test fun unreviewedSceneCutCannotChangeProject() {
        val vm = editor()
        val before = vm.state.value.project
        assertFalse(vm.applyLocalSceneCuts())
        assertEquals(before, vm.state.value.project)
        assertTrue(vm.undoStack.isEmpty())
    }

    @Test fun reviewedSceneCutUsesSourceCoordinatesAndOneHistoryEntry() {
        val vm = editor()
        val original = vm.state.value.project!!.clips
        vm.setLocalSuggestionReviewed("cut-6000", true)
        assertTrue(vm.applyLocalSceneCuts())
        val clips = vm.state.value.project!!.clips
        assertEquals(listOf(2_000L, 6_000L), clips.map { it.trimStartMs })
        assertEquals(listOf(6_000L, 12_000L), clips.map { it.trimEndMs })
        assertTrue(clips.all { it.durationMs == 20_000L && it.uri == "file:///video.mp4" })
        assertEquals(original, vm.undoStack.single())
        assertEquals(LocalAnalysisStatus.IDLE, vm.localAnalysis.value.status)
        assertFalse(vm.applyLocalSceneCuts())
    }

    @Test fun deselectingReviewedCutPreventsItsApplication() {
        val vm = editor()
        vm.setLocalSuggestionReviewed("cut-6000", true)
        vm.setLocalSuggestionReviewed("cut-6000", false)
        assertFalse(vm.applyLocalSceneCuts())
        assertEquals(1, vm.state.value.project!!.clips.size)
    }

    @Test fun missingSuggestionCannotBeMarkedReviewed() {
        val vm = editor()
        vm.setLocalSuggestionReviewed("not-a-candidate", true)
        assertTrue(vm.localAnalysis.value.reviewedSuggestionIds.isEmpty())
    }

    @Test fun staleTrimRangeCannotBeSplit() {
        val vm = editor()
        vm.reviewLocalSceneCut("cut-6000")
        vm._state.update { state -> state.copy(project = state.project!!.copy(
            clips = state.project!!.clips.map { it.copy(trimStartMs = 3_000L) })) }
        assertFalse(vm.applyLocalSceneCuts())
        assertTrue(vm.localAnalysis.value.error!!.contains("changed"))
        assertTrue(vm.undoStack.isEmpty())
    }

    @Test fun speedEditedClipIsRejectedRatherThanSilentlyChangingTiming() {
        val vm = editor(speed = 2f)
        vm.reviewLocalSceneCut("cut-6000")
        assertFalse(vm.applyLocalSceneCuts())
        assertTrue(vm.localAnalysis.value.error!!.contains("speed"))
        assertTrue(vm.undoStack.isEmpty())
    }

    @Test fun smartTrimRequiresReviewAndKeepsOnlyTheSelectedSourceWindow() {
        val vm = editor()
        assertFalse(vm.applyLocalSuggestedTrim("trim-4000-8000"))
        vm.reviewLocalSuggestedTrim("trim-4000-8000")
        assertTrue(vm.applyLocalSuggestedTrim("trim-4000-8000"))
        val clip = vm.state.value.project!!.clips.single()
        assertEquals(4_000L, clip.trimStartMs)
        assertEquals(8_000L, clip.trimEndMs)
        assertEquals(1, vm.undoStack.size)
        assertEquals(LocalAnalysisStatus.IDLE, vm.localAnalysis.value.status)
    }

    @Test fun cropRequiresReviewAndIsStoredInNormalClipHistory() {
        val vm = editor()
        assertFalse(vm.applyLocalAutoCrop())
        vm.reviewLocalAutoCrop()
        assertTrue(vm.applyLocalAutoCrop())
        val crop = vm.state.value.project!!.clips.single().videoCrop
        assertEquals("local-signal", crop.presetId)
        assertEquals(.25f, crop.left, .0001f)
        assertEquals(.75f, crop.right, .0001f)
        assertEquals(VideoCropSettings(), vm.undoStack.single().single().videoCrop)
    }

    @Test fun cancellationDiscardsPartialSuggestions() {
        val vm = editor()
        vm._localAnalysis.update { it.copy(status = LocalAnalysisStatus.RUNNING, progress = .5f) }
        vm.cancelLocalAnalysis()
        assertEquals(LocalAnalysisStatus.CANCELLED, vm.localAnalysis.value.status)
        assertTrue(vm.localAnalysis.value.sceneCuts.isEmpty())
        assertTrue(vm.localAnalysis.value.smartTrims.isEmpty())
        assertNull(vm.localAnalysis.value.autoCrop)
    }
}
