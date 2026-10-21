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

    @Test fun selectedVideoControlsAreImmediatelyAboveCompactTimelineAndMutateTheRealTrack() {
        val vm = editor()
        compose.setContent {
            val state by vm.state.collectAsState()
            Column(Modifier.requiredSize(375.dp, 400.dp)) {
                EditorTransportSection(state, vm, seekPlayerAndState = {}, onOpenAddMediaMenu = {})
            }
        }
        compose.onNodeWithText("00:00 / 02:59").assertIsDisplayed()
        compose.onNodeWithText("Keyframe").assertIsDisplayed()
        compose.onNodeWithText("Animation").assertIsDisplayed()
        compose.onNodeWithText("Add Clip").assertIsDisplayed()
        val keyframe = compose.onNodeWithContentDescription("Add keyframe").fetchSemanticsNode().boundsInRoot
        val animation = compose.onNodeWithContentDescription("Edit video keyframe animation").fetchSemanticsNode().boundsInRoot
        val header = compose.onNodeWithText("TIMELINE").fetchSemanticsNode().boundsInRoot
        val track = compose.onNodeWithContentDescription("Main video track").fetchSemanticsNode().boundsInRoot
        assertTrue("Keyframe and Animation must share the row above timeline", keyframe.bottom <= header.top && animation.bottom <= header.top)
        assertEquals("No identity/eye gutter at 00:00", 0f, track.left, 1f)
        assertEquals("Filmstrip must remain compact", 56f, track.height, 1f)
        compose.onNodeWithContentDescription("Add keyframe").performClick()
        compose.runOnIdle {
            assertEquals(1, vm.state.value.project!!.clips.single().keyframes.keyframes.size)
            assertEquals(0L, vm.state.value.project!!.clips.single().keyframes.keyframes.single().timeMs)
            assertEquals(clip.uri, vm.state.value.project!!.clips.single().uri)
        }
        compose.onNodeWithContentDescription("Edit video keyframe animation").performClick()
        compose.runOnIdle { assertTrue(vm.state.value.keyframePanelOpen) }
    }

    @Test fun timelineAddClipRequestsRealVideoImportAndDoesNotCreateFakeMedia() {
        val vm = editor()
        var requested: com.apexstudio.app.domain.model.ClipType? = null
        compose.setContent {
            Column(Modifier.requiredSize(375.dp, 400.dp)) {
                EditorTransportSection(vm.state.value, vm, seekPlayerAndState = {}, onOpenAddMediaMenu = { requested = it })
            }
        }
        compose.onNodeWithTag("timeline-add-media").performClick()
        compose.runOnIdle {
            assertEquals(com.apexstudio.app.domain.model.ClipType.VIDEO, requested)
            assertEquals(listOf(clip), vm.state.value.project!!.clips)
        }
    }
}
