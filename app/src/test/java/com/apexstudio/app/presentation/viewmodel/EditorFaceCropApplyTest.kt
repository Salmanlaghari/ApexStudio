package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.ai.autoreframe.FaceTrackAnalysisResult
import com.apexstudio.app.ai.autoreframe.FaceTrackSample
import com.apexstudio.app.ai.autoreframe.NormalizedCrop
import com.apexstudio.app.ai.autoreframe.ReframeFrame
import com.apexstudio.app.ai.mediapipe.FaceDetection
import com.apexstudio.app.ai.mediapipe.NormalizedBox
import com.apexstudio.app.ai.mediapipe.TrackedFace
import com.apexstudio.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorFaceCropApplyTest {
    private fun result() = FaceTrackAnalysisResult(1920, 1080, listOf(1_000L, 2_000L).map { time ->
        FaceTrackSample(time, listOf(TrackedFace(1L,
            FaceDetection(NormalizedBox(.2f, .2f, .4f, .5f), emptyList()))),
            ReframeFrame(time, NormalizedCrop(.2f, 0f, .51640625f, 1f), true))
    })

    private fun editor(existingTrack: Boolean = false): EditorViewModel {
        val analysis = result()
        val clip = MediaClip("video", "Video", "file:///video.mp4", 10_000L,
            trimStartMs = 1_000L, trimEndMs = 9_000L,
            faceCropTrack = if (existingTrack) analysis.toPersistedCropTrack(9f / 16f) else null)
        val project = Project("project", "Project", 8_000L, clips = listOf(clip))
        return EditorViewModel().also { vm ->
            vm._state.value = vm._state.value.copy(project = project, selectedClipId = clip.id)
            vm._faceTrackReview.value = FaceTrackReviewUiState(status = FaceTrackReviewStatus.READY,
                clipId = clip.id, sourceUri = clip.uri, sourceSnapshot = FaceTrackSourceSnapshot.from(project.id, clip),
                result = analysis, reviewedSampleTimestamps = analysis.samples.map { it.timestampMs }.toSet())
        }
    }

    @Test fun reviewMarksDoNotEnableOutOfScopeFaceReframeOrMutateHistory() {
        val vm = editor()
        val before = vm.state.value.project
        assertFalse(vm.applyReviewedFaceCropTrack())
        assertEquals(before, vm.state.value.project)
        assertTrue(vm.undoStack.isEmpty())
        assertTrue(vm.faceTrackReview.value.error!!.contains("video-only tool scope"))
    }

    @Test fun everyMeasuredSampleMustBeReviewed() {
        val vm = editor()
        vm._faceTrackReview.value = vm.faceTrackReview.value.copy(reviewedSampleTimestamps = setOf(1_000L))
        assertFalse(vm.applyReviewedFaceCropTrack())
        assertTrue(vm.faceTrackReview.value.error!!.contains("every measured sample"))
        assertTrue(vm.undoStack.isEmpty())
    }

    @Test fun staleSourceCannotApplyAReviewedTrack() {
        val vm = editor()
        vm.updateClip("video") { it.copy(uri = "file:///replacement.mp4") }
        assertFalse(vm.applyReviewedFaceCropTrack())
        assertTrue(vm.faceTrackReview.value.error!!.contains("source changed"))
        assertTrue(vm.undoStack.isEmpty())
    }

    @Test fun entirelyFallbackTracksCannotBeAppliedAsFaceTracking() {
        val vm = editor()
        val analysis = result().let { it.copy(samples = it.samples.map { sample ->
            sample.copy(faces = emptyList(), reframe = sample.reframe.copy(faceFound = false))
        }) }
        vm._faceTrackReview.value = vm.faceTrackReview.value.copy(result = analysis)
        assertFalse(vm.applyReviewedFaceCropTrack())
        assertTrue(vm.faceTrackReview.value.error!!.contains("real face detection"))
    }

    @Test fun manualClearRemainsAvailableAndRetainsUndoSnapshotWhileSafetyIsBlocked() {
        val vm = editor(existingTrack = true)
        val original = vm.state.value.project!!.clips.single()
        assertTrue(vm.clearFaceCropTrack("video"))
        assertNull(vm.state.value.project!!.clips.single().faceCropTrack)
        assertEquals(original, vm.undoStack.single().single())
        assertFalse(vm.clearFaceCropTrack("video"))
        assertEquals(1, vm.undoStack.size)
    }

    @Test fun manualCropAndResetReplaceRatherThanDoubleApplyDynamicCrop() {
        val vm = editor(existingTrack = true)
        vm.updateVideoCrop("video", VideoCropSettings(.1f, 0f, .9f, 1f))
        val cropped = vm.state.value.project!!.clips.single()
        assertNull(cropped.faceCropTrack)
        assertEquals(.1f, cropped.videoCrop.left, .0001f)
        vm.updateClip("video") { it.copy(faceCropTrack = result().toPersistedCropTrack(9f / 16f)) }
        vm.resetVideoCrop("video")
        assertNull(vm.state.value.project!!.clips.single().faceCropTrack)
        assertEquals(VideoCropSettings(), vm.state.value.project!!.clips.single().videoCrop)
    }

    @Test fun mediaReplacementNeverTransfersMeasuredFaceCoordinatesToAnotherSource() {
        val vm = editor(existingTrack = true)
        vm.updateClip("video") { it.copy(uri = "file:///different-source.mp4") }
        assertNull(vm.state.value.project!!.clips.single().faceCropTrack)
    }

    @Test fun sourceTimeTrackSurvivesOrdinaryTrimAndSpeedEditsOfTheSameMedia() {
        val vm = editor(existingTrack = true)
        val original = vm.state.value.project!!.clips.single().faceCropTrack
        vm.updateClip("video") { it.copy(trimStartMs = 2_000L, speedMultiplier = 2f) }
        assertEquals(original, vm.state.value.project!!.clips.single().faceCropTrack)
    }
}
