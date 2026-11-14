package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Dp
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
                    preview = { Box(Modifier.fillMaxWidth().height(180.dp).testTag("preview-content")) },
                    timeline = { fillTimelinePane ->
                        if (focused || fillTimelinePane) Box(Modifier.fillMaxSize().testTag("timeline-content"))
                        else Box(Modifier.fillMaxWidth().height(160.dp).testTag("timeline-content"))
                    },
                    inspector = { Text("Real properties") },
                    compactTimelinePaneHeight = 160.dp)
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
        val handle = compose.onNodeWithTag("timeline-resize-handle").fetchSemanticsNode().boundsInRoot.height
        assertTrue(preview > 0f)
        assertEquals(160f, timeline, 1f)
        assertEquals(32f, handle, 1f)
        assertTrue("Normal portrait preview should be larger than the row-bounded timeline", preview > timeline)
        assertTrue("portrait panes and resize grip should fill the available editor height",
            kotlin.math.abs(preview + timeline + handle - workspace) < 2f)
    }

    @Test fun portraitTimelineCanBeDraggedUpAndDownAndResetToCompact() {
        compose.setContent {
            var manualHeight by remember { mutableStateOf<Dp?>(null) }
            Column(Modifier.requiredSize(375.dp, 600.dp)) {
                AdaptiveEditorWorkspace(
                    landscape = false,
                    focused = false,
                    fullscreen = false,
                    preview = { Box(Modifier.fillMaxSize().testTag("resize-preview-content")) },
                    timeline = { fillTimelinePane ->
                        Box(
                            Modifier.fillMaxWidth()
                                .then(if (fillTimelinePane) Modifier.fillMaxSize() else Modifier.height(160.dp))
                                .testTag("resize-timeline-content")
                        )
                    },
                    inspector = {},
                    compactTimelinePaneHeight = 160.dp,
                    manualTimelinePaneHeight = manualHeight,
                    onManualTimelinePaneHeightChange = { manualHeight = it },
                )
            }
        }

        val compactPreview = paneHeight("portrait-preview-pane")
        val compactTimeline = paneHeight("portrait-timeline-pane")
        compose.onNodeWithTag("timeline-resize-handle").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithTag("timeline-resize-handle").performTouchInput {
            val start = center
            down(start)
            moveTo(start.copy(y = start.y - 100f), delayMillis = 120)
            up()
        }

        val expandedPreview = paneHeight("portrait-preview-pane")
        val expandedTimeline = paneHeight("portrait-timeline-pane")
        assertTrue("Dragging up should grow the timeline viewport", expandedTimeline > compactTimeline + 60f)
        assertTrue("Resizing should give height back from the preview pane", expandedPreview < compactPreview)
        assertTrue("The preview pane remains useful after resizing", expandedPreview >= 180f)

        compose.onNodeWithTag("timeline-resize-handle").performTouchInput {
            val start = center
            down(start)
            moveTo(start.copy(y = start.y + 200f), delayMillis = 120)
            up()
        }
        assertEquals("Dragging down clamps back to the content-bounded timeline", compactTimeline,
            paneHeight("portrait-timeline-pane"), 1f)

        compose.onNodeWithTag("timeline-resize-handle").performTouchInput {
            val start = center
            down(start)
            moveTo(start.copy(y = start.y - 100f), delayMillis = 120)
            up()
        }
        compose.onNodeWithTag("timeline-resize-handle").performClick()
        assertEquals("Tapping the grip restores compact height", compactTimeline, paneHeight("portrait-timeline-pane"), 1f)
    }

    @Test fun timelinePaneHeightClampKeepsCompactRowsAndUsefulPreviewBounds() {
        assertEquals(160.dp, clampTimelinePaneHeight(90.dp, 500.dp, 160.dp, 180.dp, 32.dp))
        assertEquals(288.dp, clampTimelinePaneHeight(400.dp, 500.dp, 160.dp, 180.dp, 32.dp))
        assertEquals(160.dp, clampTimelinePaneHeight(160.dp, 500.dp, 160.dp, 180.dp, 32.dp))
        assertEquals(288.dp, clampTimelinePaneHeight(288.dp, 500.dp, 160.dp, 180.dp, 32.dp))
        assertEquals(0.dp, clampTimelinePaneHeight(120.dp, 190.dp, 160.dp, 180.dp, 32.dp))
    }

    @Test fun focusedPortraitExpandsTimelineWithoutCollapsingPreview() {
        render(false, focused = true)
        val preview = compose.onNodeWithTag("portrait-preview-pane").fetchSemanticsNode().boundsInRoot.height
        val timeline = compose.onNodeWithTag("portrait-timeline-pane").fetchSemanticsNode().boundsInRoot.height
        assertTrue("Focus mode should prioritize the timeline", timeline > preview)
        assertTrue("Focus mode must keep a useful preview visible", preview >= 180f)
        assertTrue("The timeline expands into the remaining workspace", timeline > 200f && timeline < 390f)
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
