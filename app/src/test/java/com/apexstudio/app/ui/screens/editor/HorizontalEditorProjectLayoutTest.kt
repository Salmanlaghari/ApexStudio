package com.apexstudio.app.ui.screens.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
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
@Config(sdk = [34], qualifiers = "w375dp-h830dp")
class HorizontalEditorProjectLayoutTest {
    @get:Rule val compose = createComposeRule()

    private fun editor(aspectRatio: Float): EditorViewModel {
        val clip = MediaClip(
            id = "main-video",
            name = "Project video",
            uri = "file:///missing-horizontal-layout-test.mp4",
            durationMs = 12_000L,
            trimEndMs = 12_000L,
        )
        return EditorViewModel().also { vm ->
            vm._export.value = vm.export.value.copy(
                settings = vm.export.value.settings.copy(
                    aspectRatio = if (aspectRatio > 1f) "16:9" else "9:16"
                )
            )
            vm._state.value = vm.state.value.copy(
                project = Project(
                    id = "layout-project",
                    name = "Layout project",
                    durationMs = 12_000L,
                    clips = listOf(clip),
                    videoAspectRatio = aspectRatio,
                ),
                durationMs = 12_000L,
                videoWidth = if (aspectRatio > 1f) 1920 else 1080,
                videoHeight = if (aspectRatio > 1f) 1080 else 1920,
                audioWaveform = floatArrayOf(0.1f, 0.7f, 0.3f, 0.8f, 0.2f),
            )
        }
    }

    private fun show(vm: EditorViewModel) {
        compose.setContent {
            EditorScreen(
                projectId = "layout-project",
                onBack = {},
                onExport = {},
                onColor = {},
                onAudio = {},
                vm = vm,
            )
        }
    }

    @Test
    fun horizontalCompositionKeepsPreviewAndOnlyAccessiblePrimaryToolsOnCleanTimeline() {
        show(editor(16f / 9f))

        compose.onNodeWithTag("horizontal-project-workspace").assertIsDisplayed()
        compose.onNodeWithTag("horizontal-project-preview-pane").assertIsDisplayed()
        compose.onNodeWithTag("horizontal-project-timeline-pane").assertIsDisplayed()
        compose.onNodeWithTag("portrait-preview-pane").assertDoesNotExist()
        compose.onNodeWithTag("editor-playback-controls").assertIsDisplayed()
        compose.onNodeWithTag("centered-timeline-workspace").assertIsDisplayed()
        compose.onNodeWithTag("timeline-header").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add media").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add video media").assertDoesNotExist()
        compose.onNodeWithTag("timeline-add-media").assertDoesNotExist()
        compose.onNodeWithTag("timeline-add-at-end").assertDoesNotExist()
        compose.onNodeWithTag("timeline-overflow").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add Clip").assertDoesNotExist()
        compose.onNodeWithTag("timeline-ruler", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Main video track").assertIsDisplayed()
        compose.onNodeWithContentDescription("Source audio waveform").assertIsDisplayed()

        val preview = compose.onNodeWithTag("editor-preview-frame").fetchSemanticsNode().boundsInRoot
        val playback = compose.onNodeWithTag("editor-playback-controls").fetchSemanticsNode().boundsInRoot
        val timeline = compose.onNodeWithTag("centered-timeline-workspace").fetchSemanticsNode().boundsInRoot
        val editTools = compose.onNodeWithTag("mobile-editor-edit-tools").fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("Landscape preview should be full-width and 16:9, not stretched: $preview",
            preview.width >= root.width * 0.88f && kotlin.math.abs(preview.width / preview.height - 16f / 9f) < 0.12f)
        assertTrue("Playback must follow the preview without a large blank pane: preview=$preview playback=$playback",
            playback.top >= preview.bottom && playback.top - preview.bottom < 36f)
        assertTrue("Timeline card must follow playback: playback=$playback timeline=$timeline", timeline.top >= playback.bottom)
        assertTrue("The primary tool row must stay inside the screen: edit=$editTools root=$root",
            editTools.bottom <= root.bottom)

        listOf("Trim", "Split", "Text", "Music", "More").forEach { label ->
            compose.onNodeWithText(label).assertIsDisplayed()
            compose.onNodeWithContentDescription(label).assertIsDisplayed()
        }
        compose.onNodeWithTag("mobile-editor-clip-options").assertDoesNotExist()
    }

    @Test
    fun portraitCompositionKeepsAspectAwarePortraitWorkspace() {
        show(editor(9f / 16f))

        compose.onNodeWithTag("horizontal-project-workspace").assertDoesNotExist()
        compose.onNodeWithTag("portrait-preview-pane").assertIsDisplayed()
        compose.onNodeWithTag("portrait-timeline-pane").assertIsDisplayed()
        val preview = compose.onNodeWithTag("editor-preview-frame").fetchSemanticsNode().boundsInRoot
        assertTrue("Portrait video keeps its tall preview frame: $preview", preview.height > preview.width)
    }
}
