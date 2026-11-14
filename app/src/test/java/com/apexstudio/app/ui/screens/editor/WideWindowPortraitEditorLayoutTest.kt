package com.apexstudio.app.ui.screens.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1080dp-h800dp")
class WideWindowPortraitEditorLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun portraitProjectOnWideWindowKeepsTallPreviewAndFullWidthTimeline() {
        val clip = MediaClip(
            id = "main-video",
            name = "Project video",
            uri = "file:///missing-wide-window-test.mp4",
            durationMs = 12_000L,
            trimEndMs = 12_000L,
        )
        val vm = EditorViewModel().also { editor ->
            editor._export.value = editor.export.value.copy(
                settings = editor.export.value.settings.copy(aspectRatio = "16:9")
            )
            editor._state.value = editor.state.value.copy(
                project = Project(
                    id = "wide-window-project",
                    name = "Wide window project",
                    durationMs = 12_000L,
                    clips = listOf(clip),
                    videoAspectRatio = 9f / 16f,
                ),
                selectedClipId = clip.id,
                durationMs = 12_000L,
            )
        }

        compose.setContent {
            EditorScreen(
                projectId = "wide-window-project",
                onBack = {},
                onExport = {},
                onColor = {},
                onAudio = {},
                vm = vm,
                entryWorkspace = "edit",
            )
        }
        compose.waitForIdle()

        compose.onNodeWithTag("horizontal-project-workspace").assertDoesNotExist()
        compose.onNodeWithTag("landscape-preview-row").assertDoesNotExist()
        compose.onNodeWithTag("portrait-preview-pane").assertIsDisplayed()
        compose.onNodeWithTag("portrait-timeline-pane").assertIsDisplayed()
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val previewPane = compose.onNodeWithTag("portrait-preview-pane").fetchSemanticsNode().boundsInRoot
        val preview = compose.onNodeWithTag("editor-preview-frame").fetchSemanticsNode().boundsInRoot
        val timelinePane = compose.onNodeWithTag("portrait-timeline-pane").fetchSemanticsNode().boundsInRoot
        val timeline = compose.onNodeWithTag("centered-timeline-workspace").fetchSemanticsNode().boundsInRoot

        assertTrue("Portrait project metadata keeps a tall preview frame: $preview", preview.height > preview.width)
        assertTrue("Preview pane remains full-width: pane=$previewPane root=$root",
            previewPane.width >= root.width * 0.97f)
        assertTrue("Dedicated timeline stays full-width: timeline=$timeline root=$root",
            timeline.width >= root.width * 0.97f)
        assertTrue("Preview remains above the timeline: preview=$previewPane timeline=$timelinePane",
            previewPane.bottom <= timelinePane.top)
        compose.onNodeWithTag("timeline-resize-handle").assertIsDisplayed()

        compose.onNodeWithContentDescription("Open video properties").assertIsDisplayed().performClick()
        compose.onNodeWithTag("video-properties-dialog").assertIsDisplayed()
        compose.onNodeWithText("Position X").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close video properties").performClick()
        compose.onNodeWithTag("video-properties-dialog").assertDoesNotExist()
    }
}
