package com.apexstudio.app.ui.screens.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w375dp-h830dp")
class EditorReferenceLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun firstEditorEntryShowsReferenceFilterWorkspaceAndRealTimelineControls() {
        val clip = MediaClip(
            id = "main-video",
            name = "Project video",
            uri = "file:///missing-reference-test-video.mp4",
            durationMs = 12_000L,
            trimEndMs = 12_000L
        )
        val vm = EditorViewModel().also { editor ->
            editor._state.value = editor.state.value.copy(
                project = Project("reference-project", "Reference project", 12_000L, clips = listOf(clip)),
                selectedClipId = null,
                durationMs = 12_000L
            )
        }

        compose.setContent {
            EditorScreen(
                projectId = "reference-project",
                onBack = {},
                onExport = {},
                onColor = {},
                onAudio = {},
                vm = vm
            )
        }

        compose.onNodeWithTag("filter-workspace-panel").assertIsDisplayed()
        compose.onNodeWithText("Apply").assertIsDisplayed()
        compose.onNodeWithTag("editor_global_toolbar").assertIsDisplayed()
        listOf("Trim", "Effects", "Text", "Draw").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        compose.onNodeWithContentDescription("Play playback").assertIsDisplayed()
        compose.onNodeWithTag("timeline-header").assertIsDisplayed()
        val rootBounds = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val ruler = compose.onNodeWithTag("timeline-ruler", useUnmergedTree = true)
        val rulerBounds = ruler.fetchSemanticsNode().boundsInRoot
        assertTrue("Timeline ruler must fit on screen: ruler=$rulerBounds root=$rootBounds",
            rulerBounds.top >= rootBounds.top && rulerBounds.bottom <= rootBounds.bottom)
        ruler.assertIsDisplayed()
    }
}
