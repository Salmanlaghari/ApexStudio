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
                sourceSnapshot = FaceTrackSourceSnapshot.from("analysis", clip),
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
        assertEquals(original, vm.undoStack.single().project.clips)
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
        assertTrue(vm.localAnalysis.value.error!!.contains("no longer current"))
        assertTrue(vm.undoStack.isEmpty())
    }

    @Test fun normalizedAnalysisCannotAuthorizeRawTrimBoundsOutsideTheSource() {
        for ((start, end) in listOf(2_000L to 25_000L, -1_000L to 12_000L)) {
            val vm = editor()
            val clip = vm.state.value.project!!.clips.single().copy(trimStartMs = start, trimEndMs = end)
            vm._state.update { it.copy(project = it.project!!.copy(clips = listOf(clip))) }
            vm._localAnalysis.update { it.copy(sourceStartMs = start.coerceAtLeast(0L),
                sourceEndMs = minOf(end, clip.durationMs), sourceSnapshot = FaceTrackSourceSnapshot.from("analysis", clip)) }
            vm.reviewLocalSceneCut("cut-6000")
            assertFalse(vm.applyLocalSceneCuts())
            assertEquals(listOf(clip), vm.state.value.project!!.clips)
            assertTrue(vm.undoStack.isEmpty())
        }
    }

    @Test fun malformedExtremeSceneTimestampsCannotOverflowIntoSplitBoundaries() {
        val vm = editor()
        vm._localAnalysis.update { it.copy(sceneCuts = listOf(Long.MIN_VALUE, -1L, 0L, 20_000L, Long.MAX_VALUE)
            .mapIndexed { index, time -> SceneCutSuggestion("invalid-$index", time, .9f) }) }
        vm.localAnalysis.value.sceneCuts.forEach { vm.reviewLocalSceneCut(it.id) }
        val before = vm.state.value.project
        assertFalse(vm.applyLocalSceneCuts())
        assertEquals(before, vm.state.value.project)
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

    @Test fun previewAcceptsOnlyActualCandidateTimes() {
        val vm = editor()
        listOf(6_000L, 4_000L, 7_999L, 2_000L).forEach { assertTrue(vm.canPreviewLocalAnalysisTime(it)) }
        listOf(-1L, 0L, 8_000L, 999_999L).forEach { assertFalse(vm.canPreviewLocalAnalysisTime(it)) }
    }

    @Test fun sameClipIdInAnotherProjectCannotReceiveOldTrimOrCrop() {
        val vm = editor()
        vm.reviewLocalSuggestedTrim("trim-4000-8000")
        vm.reviewLocalAutoCrop()
        vm._state.update { it.copy(project = it.project!!.copy(id = "another-project")) }
        val before = vm.state.value.project
        assertFalse(vm.applyLocalSuggestedTrim("trim-4000-8000"))
        assertFalse(vm.applyLocalAutoCrop())
        assertEquals(before, vm.state.value.project)
        assertTrue(vm.undoStack.isEmpty())
        assertEquals(LocalAnalysisStatus.CANCELLED, vm.localAnalysis.value.status)
        assertTrue(vm.localAnalysis.value.reviewedSuggestionIds.isEmpty())
    }

    @Test fun staleSpeedOrDurationCannotSeekOldSourceSuggestions() {
        val mutations: List<(MediaClip) -> MediaClip> = listOf(
            { it.copy(speedMultiplier = 2f) },
            { it.copy(speedCurve = SpeedCurve.RAMP) },
            { it.copy(rampStartSpeed = .5f) },
            { it.copy(rampEndSpeed = 3f) },
            { it.copy(speedPoints = listOf(SpeedPoint(0f, 1f), SpeedPoint(1f, 2f))) },
            { it.copy(durationMs = 21_000L) },
            { it.copy(type = ClipType.IMAGE) },
        )
        mutations.forEach { mutate ->
            val vm = editor()
            vm._state.update { state -> state.copy(project = state.project!!.copy(
                clips = state.project!!.clips.map(mutate))) }
            assertFalse(vm.canPreviewLocalAnalysisTime(6_000L))
            assertEquals(LocalAnalysisStatus.CANCELLED, vm.localAnalysis.value.status)
            assertTrue(vm.localAnalysis.value.sceneCuts.isEmpty())
            assertTrue(vm.undoStack.isEmpty())
        }
    }

    @Test fun staleSourceCannotBeMarkedReviewed() {
        val vm = editor()
        vm._state.update { state -> state.copy(project = state.project!!.copy(
            clips = state.project!!.clips.map { it.copy(uri = "file:///other.mp4") })) }
        vm.reviewLocalSceneCut("cut-6000")
        assertEquals(LocalAnalysisStatus.CANCELLED, vm.localAnalysis.value.status)
        assertTrue(vm.localAnalysis.value.reviewedSuggestionIds.isEmpty())
    }

    @Test fun missingSourceSnapshotFailsClosedBeforeApply() {
        val vm = editor()
        vm.reviewLocalAutoCrop()
        vm._localAnalysis.update { it.copy(sourceSnapshot = null) }
        assertFalse(vm.applyLocalAutoCrop())
        assertTrue(vm.undoStack.isEmpty())
        assertEquals(VideoCropSettings(), vm.state.value.project!!.clips.single().videoCrop)
    }
}
