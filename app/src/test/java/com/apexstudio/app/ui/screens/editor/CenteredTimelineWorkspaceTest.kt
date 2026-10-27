package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w375dp-h830dp-mdpi")
class CenteredTimelineWorkspaceTest {
    @get:Rule val compose = createComposeRule()
    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    @Test fun smallWorkspaceStartsAtTransportAndConsumesRemainingHeight() {
        compose.setContent {
            Column(Modifier.requiredSize(375.dp, 120.dp)) {
                Box(Modifier.fillMaxWidth().height(40.dp).testTag("top"))
                CenteredTimelineWorkspace {
                    Box(Modifier.fillMaxSize().testTag("content"))
                }
                Box(Modifier.fillMaxWidth().height(40.dp).testTag("dock"))
            }
        }
        assertEquals(40f, bounds("content").height, 1f)
        assertEquals(bounds("top").bottom, bounds("content").top, 1f)
        assertEquals(bounds("dock").top, bounds("content").bottom, 1f)
    }

    @Test fun workspaceExpandsWithoutInflatingRealTrackHeights() {
        compose.setContent {
            Column(Modifier.requiredSize(375.dp, 300.dp)) {
                Box(Modifier.fillMaxWidth().height(30.dp).testTag("top"))
                CenteredTimelineWorkspace {
                    Box(Modifier.fillMaxSize().testTag("content")) {
                        Column { (1..3).forEach { Box(Modifier.fillMaxWidth().height(40.dp).testTag("track-$it")) } }
                    }
                }
                Box(Modifier.fillMaxWidth().height(30.dp).testTag("dock"))
            }
        }
        assertEquals(240f, bounds("content").height, 1f)
        assertEquals(bounds("top").bottom, bounds("content").top, 1f)
        assertEquals(bounds("dock").top, bounds("content").bottom, 1f)
        (1..3).forEach { compose.onNodeWithTag("track-$it").assertIsDisplayed(); assertEquals(40f, bounds("track-$it").height, 1f) }
    }

    @Test fun realTransportHasNoPropertyStripOrCenteredDeadSpace() {
        val clip = MediaClip("main", "Main", "file:///missing-test-video.mp4", 5_000L, trimEndMs = 5_000L)
        val vm = EditorViewModel().also { it._state.value = it.state.value.copy(
            project = Project("p", "Project", 5_000L, clips = listOf(clip)), selectedClipId = clip.id, durationMs = 5_000L) }
        compose.setContent {
            val state by vm.state.collectAsState()
            Column(Modifier.requiredSize(375.dp, 750.dp).testTag("root")) {
                Box(Modifier.fillMaxWidth().weight(1f).testTag("preview"))
                EditorTransportSection(state, vm, seekPlayerAndState = {}, onOpenAddMediaMenu = {})
                Box(Modifier.fillMaxWidth().height(64.dp).testTag("dock"))
            }
        }
        listOf("Position", "Scale", "Rotation", "Opacity", "Animation").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        val play = compose.onNodeWithContentDescription("Play playback").fetchSemanticsNode().boundsInRoot
        val focus = compose.onNodeWithContentDescription("Expand timeline to full height").fetchSemanticsNode().boundsInRoot
        val ruler = bounds("timeline-ruler")
        val track = compose.onNodeWithContentDescription("Main video track").fetchSemanticsNode().boundsInRoot
        val preview = bounds("preview")
        val timelineHost = bounds("centered-timeline-workspace")
        assertTrue("Production preview uses remaining height above the bounded timeline", preview.height > 340f)
        assertTrue("Normal timeline host stays close to populated track height", timelineHost.height in 100f..200f)
        assertEquals(bounds("root").bottom, bounds("dock").bottom, 1f)
        assertTrue("Focus control immediately follows transport", focus.top >= play.bottom && focus.top - play.bottom <= 20f)
        assertEquals("Only scrub line and compact header separate focus control from ruler",
            focus.bottom + 2f + com.apexstudio.app.ui.components.TIMELINE_HEADER_HEIGHT_DP, ruler.top, 1f)
        assertTrue(track.bottom <= bounds("dock").top)
    }
}
