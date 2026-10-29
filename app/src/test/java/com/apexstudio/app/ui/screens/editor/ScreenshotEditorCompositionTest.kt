package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Uses the production transport/timeline wiring, not a drawn approval mockup. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w375dp-h830dp-mdpi")
class ScreenshotEditorCompositionTest {
    @get:Rule val compose = createComposeRule()
    private val clip = MediaClip("main", "42085.mp4", "file:///missing-test-video.mp4", 179_000L, 0L, 179_000L)

    private fun editor() = EditorViewModel().also { vm ->
        vm._state.value = vm.state.value.copy(
            project = Project("42085", "ApexStudio", 179_000L, clips = listOf(clip)),
            selectedClipId = clip.id,
            durationMs = 179_000L,
        )
    }

    @Test fun lowerToolsKeepKeyframeAndFullAnimationPropertiesAccessible() {
        val vm = editor()
        val original = vm.state.value.project
        val picker = com.apexstudio.app.data.picker.MediaPickerHelper(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        compose.setContent {
            val state by vm.state.collectAsState()
            EditorBottomToolbarSection(state, vm, picker)
        }
        compose.onNodeWithText("Keyframe").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, vm.state.value.project!!.clips.single().keyframes.keyframes.size) }
        compose.onNodeWithText("Animation").performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(vm.state.value.keyframePanelOpen)
            assertEquals(original!!.clips.single().uri, vm.state.value.project!!.clips.single().uri)
        }
    }

    @Test fun selectedVideoKeepsCleanTimelineAndNoPropertiesStrip() {
        val vm = editor()
        compose.setContent {
            val state by vm.state.collectAsState()
            Column(Modifier.requiredSize(375.dp, 400.dp)) {
                EditorTransportSection(state, vm, seekPlayerAndState = {}, onOpenAddMediaMenu = {})
            }
        }
        compose.onNodeWithText("00:00 / 02:59").assertIsDisplayed()
        listOf("Animation", "Position", "Scale", "Rotation", "Opacity").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        compose.onNodeWithTag("timeline-header").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add media").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add video media").assertDoesNotExist()
        compose.onNodeWithTag("timeline-add-media").assertDoesNotExist()
        compose.onNodeWithTag("timeline-add-at-end").assertDoesNotExist()
        compose.onNodeWithTag("timeline-overflow").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add Clip").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add keyframe").assertDoesNotExist()
        val ruler = compose.onNodeWithTag("timeline-ruler", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val track = compose.onNodeWithContentDescription("Main video track").fetchSemanticsNode().boundsInRoot
        assertTrue("The ruler stays directly above the video track", ruler.bottom <= track.top && track.top - ruler.bottom < 12f)
        assertEquals("No identity/eye gutter at 00:00", 0f, track.left, 1f)
        assertEquals("V1 filmstrip uses the compact visual height", 52f, track.height, 1f)
    }

    @Test fun mainToolbarMediaFlowRemainsAvailableAfterRemovingTimelineAddAction() {
        val vm = editor()
        var mediaRequested = 0
        val picker = com.apexstudio.app.data.picker.MediaPickerHelper(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        compose.setContent {
            EditorBottomToolbarSection(vm.state.value, vm, picker, onMedia = { mediaRequested++ })
        }
        compose.onNodeWithText("Media").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, mediaRequested) }
    }

    @Test fun mainToolbarMoreFlowRemainsAvailableForAdditionalTools() {
        val vm = editor()
        val picker = com.apexstudio.app.data.picker.MediaPickerHelper(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        compose.setContent {
            EditorBottomToolbarSection(vm.state.value, vm, picker)
        }

        compose.onNodeWithText("More").assertIsDisplayed().performClick()
        compose.onNodeWithText("SFX").assertIsDisplayed()
    }
}
