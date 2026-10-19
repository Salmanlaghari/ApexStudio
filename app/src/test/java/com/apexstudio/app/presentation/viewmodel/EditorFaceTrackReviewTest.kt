package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.ai.autoreframe.FaceTrackAnalysisResult
import com.apexstudio.app.ai.autoreframe.FaceTrackSample
import com.apexstudio.app.ai.autoreframe.NormalizedCrop
import com.apexstudio.app.ai.autoreframe.ReframeFrame
import com.apexstudio.app.ai.mediapipe.FaceDetection
import com.apexstudio.app.ai.mediapipe.NormalizedBox
import com.apexstudio.app.ai.mediapipe.TrackedFace
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.domain.model.SpeedPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorFaceTrackReviewTest {
    private fun editor(): EditorViewModel {
        val detection = FaceDetection(NormalizedBox(.2f, .1f, .5f, .6f), emptyList())
        val clip = MediaClip("clip", "Video", "file:///video.mp4", 3_000L, trimStartMs = 500L,
            trimEndMs = 2_000L, speedPoints = listOf(SpeedPoint(0f, 1f), SpeedPoint(1f, 2f)))
        val project = Project("project", "Project", 3_000L, clips = listOf(clip))
        val result = FaceTrackAnalysisResult(
            sourceWidth = 1920,
            sourceHeight = 1080,
            samples = listOf(
                FaceTrackSample(1_000L, listOf(TrackedFace(1L, detection)),
                    ReframeFrame(1_000L, NormalizedCrop(.1f, 0f, .7f, 1f), faceFound = true)),
                FaceTrackSample(1_500L, emptyList(),
                    ReframeFrame(1_500L, NormalizedCrop(.2f, 0f, .8f, 1f), faceFound = false)),
            )
        )
        return EditorViewModel().also { vm ->
            vm._state.value = vm._state.value.copy(project = project, selectedClipId = clip.id)
            vm._faceTrackReview.value = FaceTrackReviewUiState(
                status = FaceTrackReviewStatus.READY,
                progress = 1f,
                clipId = "clip",
                sourceUri = "file:///video.mp4",
                sourceStartMs = 1_000L,
                sourceEndMs = 2_000L,
                sourceSnapshot = FaceTrackSourceSnapshot.from(project.id, clip),
                result = result,
            )
        }
    }

    @Test fun onlyActualCompletedSampleTimesCanBeMarkedReviewed() {
        val vm = editor()
        vm.setFaceTrackSampleReviewed(1_000L, true)
        vm.setFaceTrackSampleReviewed(99_000L, true)
        assertEquals(setOf(1_000L), vm.faceTrackReview.value.reviewedSampleTimestamps)
        vm.setFaceTrackSampleReviewed(1_000L, false)
        assertTrue(vm.faceTrackReview.value.reviewedSampleTimestamps.isEmpty())
    }

    @Test fun reviewMarksAreUnavailableUntilAnalysisIsReady() {
        val vm = editor()
        vm._faceTrackReview.value = vm.faceTrackReview.value.copy(status = FaceTrackReviewStatus.RUNNING)
        vm.setFaceTrackSampleReviewed(1_000L, true)
        assertTrue(vm.faceTrackReview.value.reviewedSampleTimestamps.isEmpty())
    }

    @Test fun snapshotInvalidatesProjectClipSourceDurationTrimAndSpeedMutations() {
        val clip = MediaClip("clip", "Video", "file:///video.mp4", 3_000L,
            trimStartMs = 500L, trimEndMs = 2_000L,
            speedPoints = listOf(SpeedPoint(0f, 1f), SpeedPoint(1f, 2f)))
        val project = Project("project", "Project", 3_000L, clips = listOf(clip))
        val snapshot = FaceTrackSourceSnapshot.from(project.id, clip)
        assertTrue(isFaceTrackSnapshotCurrent(snapshot, project, null))
        assertFalse(isFaceTrackSnapshotCurrent(snapshot, project.copy(id = "other"), null))
        assertFalse(isFaceTrackSnapshotCurrent(snapshot, project.copy(clips = listOf(clip.copy(id = "replacement"))), null))
        assertFalse(isFaceTrackSnapshotCurrent(snapshot, project.copy(clips = listOf(clip.copy(uri = "file:///replacement.mp4"))), null))
        assertFalse(isFaceTrackSnapshotCurrent(snapshot, project.copy(clips = listOf(clip.copy(type = ClipType.IMAGE))), null))
        assertFalse(isFaceTrackSnapshotCurrent(snapshot, project.copy(clips = listOf(clip.copy(durationMs = 4_000L))), null))
        assertFalse(isFaceTrackSnapshotCurrent(snapshot, project.copy(clips = listOf(clip.copy(trimStartMs = 600L))), null))
        assertFalse(isFaceTrackSnapshotCurrent(snapshot, project.copy(clips = listOf(clip.copy(trimEndMs = 2_100L))), null))
        assertFalse(isFaceTrackSnapshotCurrent(snapshot, project.copy(clips = listOf(clip.copy(speedMultiplier = 2f))), null))
        assertFalse(isFaceTrackSnapshotCurrent(snapshot, project.copy(clips = listOf(
            clip.copy(speedCurve = com.apexstudio.app.domain.model.SpeedCurve.RAMP))), null))
        assertFalse(isFaceTrackSnapshotCurrent(snapshot, project.copy(clips = listOf(clip.copy(rampStartSpeed = .5f))), null))
        assertFalse(isFaceTrackSnapshotCurrent(snapshot, project.copy(clips = listOf(clip.copy(rampEndSpeed = 3f))), null))
        assertFalse(isFaceTrackSnapshotCurrent(snapshot, project.copy(clips = listOf(
            clip.copy(speedPoints = listOf(SpeedPoint(0f, 1f), SpeedPoint(1f, 3f))))), null))
        assertFalse(isFaceTrackSnapshotCurrent(snapshot, null, null))
    }

    @Test fun staleSourceCannotBePreviewedOrMarkedAndDiscardsReadyReview() {
        val vm = editor()
        val currentProject = vm._state.value.project!!
        vm._state.value = vm._state.value.copy(project = currentProject.copy(
            clips = currentProject.clips.map { it.copy(uri = "file:///changed.mp4") }))
        assertFalse(vm.canPreviewFaceTrackSample(1_000L))
        vm.setFaceTrackSampleReviewed(1_000L, true)
        assertEquals(FaceTrackReviewStatus.CANCELLED, vm.faceTrackReview.value.status)
        assertNull(vm.faceTrackReview.value.result)
        assertTrue(vm.faceTrackReview.value.reviewedSampleTimestamps.isEmpty())
    }

    @Test fun cancellationDiscardsPartialFaceTrackSamplesAndReviewMarks() {
        val vm = editor()
        vm._faceTrackReview.value = vm.faceTrackReview.value.copy(
            status = FaceTrackReviewStatus.RUNNING,
            progress = .5f,
            reviewedSampleTimestamps = setOf(1_000L),
        )
        vm.cancelLocalFaceTrackReview()
        assertEquals(FaceTrackReviewStatus.CANCELLED, vm.faceTrackReview.value.status)
        assertEquals(0f, vm.faceTrackReview.value.progress)
        assertNull(vm.faceTrackReview.value.result)
        assertTrue(vm.faceTrackReview.value.reviewedSampleTimestamps.isEmpty())
    }

    @Test fun cancellingAfterCompletionKeepsReviewableSamples() {
        val vm = editor()
        val result = vm.faceTrackReview.value.result
        vm.cancelLocalFaceTrackReview()
        assertEquals(FaceTrackReviewStatus.READY, vm.faceTrackReview.value.status)
        assertEquals(result, vm.faceTrackReview.value.result)
        assertFalse(vm.faceTrackReview.value.isRunning)
    }

    @Test fun targetsRecomputeOnlyReviewGeometryAndResetReviewMarks() {
        val vm = editor()
        val project = vm.state.value.project
        val original = vm.faceTrackReview.value.result!!
        for (target in listOf(FaceReframeReviewTarget.SQUARE, FaceReframeReviewTarget.LANDSCAPE,
            FaceReframeReviewTarget.PORTRAIT)) {
            vm.setFaceTrackSampleReviewed(1_000L, true)
            assertTrue(vm.setFaceReframeReviewTarget(target))
            val result = vm.faceTrackReview.value.result!!
            assertEquals(target, vm.faceTrackReview.value.reframeTarget)
            assertEquals(original.samples.map { it.timestampMs }, result.samples.map { it.timestampMs })
            assertEquals(original.samples.map { it.faces }, result.samples.map { it.faces })
            result.samples.forEach { sample ->
                val crop = sample.reframe.crop
                val aspect = (crop.right - crop.left) * result.sourceWidth /
                    ((crop.bottom - crop.top) * result.sourceHeight)
                assertEquals(target.aspectRatio, aspect, .0001f)
                assertTrue(crop.left >= 0f && crop.top >= 0f && crop.right <= 1f && crop.bottom <= 1f)
            }
            assertTrue(vm.faceTrackReview.value.reviewedSampleTimestamps.isEmpty())
            assertEquals(project, vm.state.value.project)
            assertTrue(vm.undoStack.isEmpty())
        }
    }

    @Test fun targetChangesAreRejectedWhileInferenceIsRunning() {
        val vm = editor()
        vm._faceTrackReview.value = vm.faceTrackReview.value.copy(status = FaceTrackReviewStatus.RUNNING)
        val before = vm.faceTrackReview.value
        assertFalse(vm.setFaceReframeReviewTarget(FaceReframeReviewTarget.SQUARE))
        assertEquals(before, vm.faceTrackReview.value)
    }

    @Test fun staleSourceCannotBeReframedEvenForReview() {
        val vm = editor()
        val project = vm._state.value.project!!
        vm._state.value = vm._state.value.copy(project = project.copy(id = "another-project"))
        assertFalse(vm.setFaceReframeReviewTarget(FaceReframeReviewTarget.SQUARE))
        assertEquals(FaceTrackReviewStatus.CANCELLED, vm.faceTrackReview.value.status)
        assertNull(vm.faceTrackReview.value.result)
    }

    @Test fun targetCanBeSelectedBeforeAClipIsAnalyzed() {
        val vm = EditorViewModel()
        assertTrue(vm.setFaceReframeReviewTarget(FaceReframeReviewTarget.SQUARE))
        assertEquals(FaceReframeReviewTarget.SQUARE, vm.faceTrackReview.value.reframeTarget)
        assertNull(vm.faceTrackReview.value.result)
        assertTrue(vm.undoStack.isEmpty())
    }
}
