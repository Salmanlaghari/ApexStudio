package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w840dp-h400dp-mdpi")
class AdaptiveEditorWorkspaceTest {
    @get:Rule val compose = createComposeRule()

    private fun render(landscape: Boolean, fullscreen: Boolean = false, focused: Boolean = false) {
        compose.setContent {
            Column(Modifier.requiredSize(if (landscape) 840.dp else 375.dp, 400.dp)) {
                AdaptiveEditorWorkspace(landscape, focused = focused, fullscreen = fullscreen,
                    preview = { Box(Modifier.fillMaxWidth().weight(1f).testTag("preview-content")) },
                    timeline = {
                        if (focused) Box(Modifier.fillMaxSize().testTag("timeline-content"))
                        else Box(Modifier.fillMaxWidth().height(160.dp).testTag("timeline-content"))
                    },
                    inspector = { Text("Real properties") })
            }
        }
    }

    @Test fun landscapeKeepsPreviewAboveFullWidthTimelineWithCollapsibleInspector() {
        render(true)
        val preview = compose.onNodeWithTag("landscape-preview").fetchSemanticsNode().boundsInRoot
        val timeline = compose.onNodeWithTag("landscape-timeline").fetchSemanticsNode().boundsInRoot
        assertTrue(preview.bottom <= timeline.top)
        assertTrue(timeline.height > preview.height)
        assertEquals(840f, timeline.width, 1f)
        compose.onNodeWithText("Real properties").assertIsDisplayed()
        compose.onNodeWithContentDescription("Hide video properties").assertHasClickAction().performClick()
        compose.onNodeWithText("Real properties").assertDoesNotExist()
        val expanded = compose.onNodeWithTag("landscape-preview").fetchSemanticsNode().boundsInRoot
        assertTrue("Collapsing properties must enlarge preview", expanded.width > preview.width)
        compose.onNodeWithContentDescription("Show video properties").assertHasClickAction().performClick()
        compose.onNodeWithText("Real properties").assertIsDisplayed()
    }

    @Test fun portraitKeepsBothExistingSlotsWithoutLandscapeInspector() {
        render(false)
        compose.onNodeWithTag("preview-content").assertIsDisplayed()
        compose.onNodeWithTag("timeline-content").assertIsDisplayed()
        compose.onNodeWithTag("landscape-inspector").assertDoesNotExist()
    }

    @Test fun portraitPreviewUsesRemainingHeightWithContentBoundedTimeline() {
        render(false)
        val workspace = compose.onNodeWithTag("adaptive-editor-workspace").fetchSemanticsNode().boundsInRoot.height
        val preview = compose.onNodeWithTag("portrait-preview-pane").fetchSemanticsNode().boundsInRoot.height
        val timeline = compose.onNodeWithTag("portrait-timeline-pane").fetchSemanticsNode().boundsInRoot.height
        assertTrue(preview > 0f)
        assertEquals(160f, timeline, 1f)
        assertTrue("Normal portrait preview should be larger than the row-bounded timeline", preview > timeline)
        assertTrue("portrait panes should fill the available editor height", kotlin.math.abs(preview + timeline - workspace) < 2f)
    }

    @Test fun focusedPortraitExpandsTimelineAndCollapsesPreview() {
        render(false, focused = true)
        val preview = compose.onNodeWithTag("portrait-preview-pane").fetchSemanticsNode().boundsInRoot.height
        val timeline = compose.onNodeWithTag("portrait-timeline-pane").fetchSemanticsNode().boundsInRoot.height
        assertTrue("Focus mode should prioritize the timeline", timeline > preview)
        assertTrue("Focus mode should give the timeline nearly all available height", timeline > 390f)
    }

    @Test fun fullscreenDoesNotConsumeWidthForInspector() {
        render(true, fullscreen = true)
        compose.onNodeWithTag("landscape-inspector").assertDoesNotExist()
        assertEquals(840f, compose.onNodeWithTag("preview-content").fetchSemanticsNode().boundsInRoot.width, 1f)
    }

    @Test fun millisecondTimecodeDoesNotHideFrameNavigation() {
        assertEquals("00:00.033", preciseEditorTime(33L))
        assertEquals("01:01.007", preciseEditorTime(61_007L))
        assertEquals("60:00.000", preciseEditorTime(3_600_000L))
        assertEquals("00:00.000", preciseEditorTime(-1L))
    }
}
