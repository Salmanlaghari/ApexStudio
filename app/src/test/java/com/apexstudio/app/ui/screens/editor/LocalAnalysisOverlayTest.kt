package com.apexstudio.app.ui.screens.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.apexstudio.app.ai.autoreframe.FaceTrackAnalysisResult
import com.apexstudio.app.ai.autoreframe.FaceTrackSample
import com.apexstudio.app.ai.autoreframe.NormalizedCrop
import com.apexstudio.app.ai.autoreframe.ReframeFrame
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.FaceTrackReviewStatus
import com.apexstudio.app.presentation.viewmodel.FaceTrackReviewUiState
import com.apexstudio.app.presentation.viewmodel.FaceTrackSourceSnapshot
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w375dp-h830dp-mdpi")
class LocalAnalysisOverlayTest {
    @get:Rule val compose = createComposeRule()

    @Test fun completedFaceSuggestionsAreVisibleWithSimpleLimitsAndNoAutomaticEdit() {
        val clip = MediaClip("clip", "sample.mp4", "file:///sample.mp4", 12_000L, trimEndMs = 12_000L)
        val project = Project("project", "Project", 12_000L, clips = listOf(clip))
        val result = FaceTrackAnalysisResult(
            sourceWidth = 1920,
            sourceHeight = 1080,
            samples = listOf(
                FaceTrackSample(1_000L, emptyList(), ReframeFrame(1_000L,
                    NormalizedCrop(.1f, 0f, .7f, 1f), faceFound = false)),
                FaceTrackSample(2_000L, emptyList(), ReframeFrame(2_000L,
                    NormalizedCrop(.2f, 0f, .8f, 1f), faceFound = false)),
            ),
        )
        val vm = EditorViewModel().also {
            it._state.value = it.state.value.copy(project = project, selectedClipId = clip.id)
            it._faceTrackReview.value = FaceTrackReviewUiState(
                status = FaceTrackReviewStatus.READY,
                progress = 1f,
                clipId = clip.id,
                sourceUri = clip.uri,
                sourceStartMs = 0L,
                sourceEndMs = clip.durationMs,
                sourceSnapshot = FaceTrackSourceSnapshot.from(project.id, clip),
                result = result,
            )
        }

        compose.setContent { MaterialTheme { LocalAnalysisOverlay(vm, onPreviewSourceTime = { _, _ -> }, onClose = {}) } }
        compose.mainClock.advanceTimeBy(1_000)

        compose.onNodeWithTag("face_review_results").assertIsDisplayed()
        compose.onNodeWithText("Face suggestions ready").assertIsDisplayed()
        compose.onNodeWithTag("face_review_results_summary").assertIsDisplayed()
        compose.onNodeWithText("Review only. Applying automatic face framing to video is not available yet.").assertExists()
        compose.onNodeWithText("Offline analysis", substring = true).assertDoesNotExist()
        compose.onNodeWithText("On-device", substring = true).assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(vm.undoStack.isEmpty())
            assertTrue(vm.state.value.project == project)
        }
    }
}
