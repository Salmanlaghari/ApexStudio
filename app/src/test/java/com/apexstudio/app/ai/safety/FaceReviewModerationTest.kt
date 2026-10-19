package com.apexstudio.app.ai.safety

import com.apexstudio.app.ai.AiModelUnavailableException
import com.apexstudio.app.ai.autoreframe.LocalFaceTrackAnalyzer
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.startLocalFaceTrackReview
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FaceReviewModerationTest {
    @Test fun reviewBackendFailsBeforeEvenParsingOrDecodingAMediaSource() = runBlocking {
        try {
            LocalFaceTrackAnalyzer().analyze(RuntimeEnvironment.getApplication(),
                "https://must-not-be-opened.invalid/video.mp4", 0L, 1_000L)
            fail("Face-model review must not bypass mandatory moderation")
        } catch (blocked: AiModelUnavailableException) {
            assertTrue(blocked.message.orEmpty().contains("model-based face-track review"))
            assertTrue(blocked.message.orEmpty().contains("OCR-visible"))
        }
    }

    @Test fun editorEntryIsLockedWithoutProjectMutationUndoOrJobCreation() {
        val clip = MediaClip("video", "Video", "file:///video.mp4", 2_000L, trimEndMs = 2_000L)
        val project = Project("project", "Project", 2_000L, clips = listOf(clip))
        val vm = EditorViewModel()
        vm._state.value = vm.state.value.copy(project = project, selectedClipId = clip.id)
        vm.startLocalFaceTrackReview()
        assertEquals(project, vm.state.value.project)
        assertTrue(vm.undoStack.isEmpty())
        assertNull(vm.faceTrackReviewJob)
        assertNull(vm.faceTrackReviewToken)
        assertTrue(vm.faceTrackReview.value.error.orEmpty().contains("OCR-visible"))
        assertFalse(vm.faceTrackReview.value.isRunning)
    }
}
