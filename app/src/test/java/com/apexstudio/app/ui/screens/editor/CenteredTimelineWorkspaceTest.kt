package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w375dp-h830dp-mdpi")
class CenteredTimelineWorkspaceTest {
    @get:Rule val compose = createComposeRule()

    private fun bounds(tag: String) =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    @Test
    fun compactWorkspaceClampsToSmallAvailableHeightAndStaysCentered() {
        compose.setContent {
            Column(Modifier.requiredSize(375.dp, 120.dp)) {
                Box(Modifier.fillMaxWidth().height(40.dp).testTag("top"))
                CenteredTimelineWorkspace(contentHeight = 100.dp, isFocused = false) {
                    Box(Modifier.fillMaxWidth().height(20.dp).testTag("compact-content"))
                }
                Box(Modifier.fillMaxWidth().height(40.dp).testTag("dock"))
            }
        }

        val top = bounds("top")
        val content = bounds("compact-content")
        val dock = bounds("dock")
        assertEquals("content fits the remaining 40dp, not the larger cap", 20f, content.height, 1f)
        assertEquals("content is centered in the space between fixed siblings",
            (top.bottom + dock.top) / 2f, (content.top + content.bottom) / 2f, 1f)
        assertTrue(content.top >= top.bottom && content.bottom <= dock.top)
    }

    @Test
    fun focusedWorkspaceExpandsAndKeepsMultipleTracksVisible() {
        compose.setContent {
            Column(Modifier.requiredSize(375.dp, 300.dp)) {
                Box(Modifier.fillMaxWidth().height(30.dp).testTag("focus-top"))
                CenteredTimelineWorkspace(contentHeight = 60.dp, isFocused = true) {
                    Box(Modifier.fillMaxSize().testTag("focused-content")) {
                        Column(Modifier.fillMaxSize()) {
                            (1..3).forEach { index ->
                                Box(Modifier.fillMaxWidth().height(40.dp).testTag("track-$index"))
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(30.dp).testTag("focus-dock"))
            }
        }

        val top = bounds("focus-top")
        val dock = bounds("focus-dock")
        val focusedContent = bounds("focused-content")
        val first = bounds("track-1")
        val second = bounds("track-2")
        val third = bounds("track-3")
        listOf("track-1", "track-2", "track-3").forEach {
            compose.onNodeWithTag(it).assertIsDisplayed()
        }
        assertEquals("focused content expands into the remaining viewport", 240f, focusedContent.height, 1f)
        assertEquals(top.bottom, focusedContent.top, 1f)
        assertEquals(dock.top, focusedContent.bottom, 1f)
        assertTrue(first.top >= top.bottom && third.bottom <= dock.top)
        assertTrue(first.bottom <= second.top && second.bottom <= third.top)
    }

    @Test
    fun realTransportKeepsPreviewAndBottomDockFixedAndCentersTimelineWorkspace() {
        val clip = MediaClip(
            id = "main", name = "Main", uri = "file:///missing-test-video.mp4",
            durationMs = 5_000L, trimEndMs = 5_000L
        )
        val vm = EditorViewModel().also {
            it._state.value = it.state.value.copy(
                project = Project("p", "Project", 5_000L, clips = listOf(clip)),
                selectedClipId = clip.id,
                durationMs = 5_000L
            )
        }

        compose.setContent {
            val state by vm.state.collectAsState()
            Column(Modifier.requiredSize(375.dp, 750.dp).testTag("editor-root")) {
                Box(Modifier.fillMaxWidth().height(340.dp).testTag("preview"))
                EditorTransportSection(state, vm, seekPlayerAndState = {}, onOpenAddMediaMenu = {})
                Box(Modifier.fillMaxWidth().height(64.dp).testTag("bottom-dock"))
            }
        }

        val root = bounds("editor-root")
        val preview = bounds("preview")
        val dock = bounds("bottom-dock")
        val propertyBottom = compose.onNodeWithText("Opacity").fetchSemanticsNode().boundsInRoot.bottom
        val ruler = compose.onNodeWithTag("timeline-ruler", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val track = compose.onNodeWithContentDescription("Main video track").fetchSemanticsNode().boundsInRoot

        assertEquals("fixed preview geometry is unchanged", 340f, preview.height, 1f)
        assertEquals("bottom dock remains flush with the root bottom", root.bottom, dock.bottom, 1f)
        assertTrue("ruler and main track remain between property controls and dock",
            propertyBottom <= ruler.top && track.bottom <= dock.top)
        val restoredHeaderHeight = 44f * compose.density.density
        val timelineCenter = (ruler.top - restoredHeaderHeight + track.bottom) / 2f
        val availableCenter = (propertyBottom + dock.top) / 2f
        assertEquals("real ruler/track workspace is vertically centered", availableCenter, timelineCenter, 25f)
    }
}
