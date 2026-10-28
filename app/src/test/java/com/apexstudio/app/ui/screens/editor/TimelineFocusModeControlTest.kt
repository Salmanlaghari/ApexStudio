package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class TimelineFocusModeControlTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun toggleKeepsPreviewHeightStableAndGivesRemainingSpaceToTimeline() {
        compose.setContent {
            var isFocused by remember { mutableStateOf(false) }
            Column(Modifier.fillMaxWidth().height(600.dp)) {
                AdaptiveEditorWorkspace(
                    landscape = false,
                    focused = isFocused,
                    fullscreen = false,
                    preview = { Box(Modifier.fillMaxWidth().height(180.dp).testTag("preview-pane")) },
                    timeline = {
                        if (isFocused) Box(Modifier.fillMaxSize().testTag("timeline-pane"))
                        else Box(Modifier.fillMaxWidth().height(150.dp).testTag("timeline-pane"))
                    },
                    inspector = {}
                )
                TimelineFocusModeControl(
                    isFocused = isFocused,
                    onToggle = { isFocused = !isFocused }
                )
            }
        }

        compose.onNodeWithContentDescription("Expand timeline to full height").assertIsDisplayed()
        val normalPreviewHeight = paneHeight("preview-pane")
        val normalTimelineHeight = paneHeight("timeline-pane")
        assertTrue("normal preview should have useful height", normalPreviewHeight > 100f)
        assertTrue("default mobile editing should prioritize the source-ratio preview", normalPreviewHeight > normalTimelineHeight)
        assertTrue("default timeline should retain an accessible working area", normalTimelineHeight > 120f)

        compose.onNodeWithContentDescription("Expand timeline to full height").performClick()

        compose.onNodeWithContentDescription("Restore preview and normal timeline layout").assertIsDisplayed()
        compose.onNodeWithTag("preview-pane").assertExists()
        compose.onNodeWithTag("timeline-pane").assertExists()
        val focusedPreviewHeight = paneHeight("preview-pane")
        val focusedTimelineHeight = paneHeight("timeline-pane")
        assertEquals("focus mode preserves the preview frame height", normalPreviewHeight, focusedPreviewHeight, 1f)
        assertTrue("focus mode allocates remaining height to timeline", focusedTimelineHeight > normalTimelineHeight)
        assertTrue("focus mode meaningfully grows timeline", focusedTimelineHeight > 300f)

        compose.onNodeWithContentDescription("Restore preview and normal timeline layout").performClick()
        compose.onNodeWithContentDescription("Expand timeline to full height").assertIsDisplayed()
        assertEquals(normalPreviewHeight, paneHeight("preview-pane"), 1f)
        assertEquals(normalTimelineHeight, paneHeight("timeline-pane"), 1f)
    }

    @Test
    fun previewFirstSplitKeepsTimelineReachableAt400Dp() {
        compose.setContent {
            var isFocused by remember { mutableStateOf(false) }
            Column(Modifier.fillMaxWidth().height(400.dp)) {
                Box(
                    Modifier
                        .weight(editorPreviewLayoutWeight(isFocused))
                        .fillMaxWidth()
                        .testTag("preview-pane")
                )
                TimelineFocusModeControl(isFocused = isFocused, onToggle = { isFocused = !isFocused })
                Box(
                    Modifier
                        .weight(timelineTrackLayoutWeight(isFocused))
                        .fillMaxWidth()
                        .testTag("timeline-pane")
                )
            }
        }

        val previewHeight = paneHeight("preview-pane")
        val timelineHeight = paneHeight("timeline-pane")
        assertTrue("Preview should stay reachable in the default mobile layout: $previewHeight", previewHeight > 100f)
        assertTrue("Preview should use the larger pane for portrait source footage: $previewHeight", previewHeight > timelineHeight)
        assertTrue("Timeline remains visible and reachable: $timelineHeight", timelineHeight > 70f)
    }

    private fun paneHeight(tag: String): Float = compose
        .onNodeWithTag(tag)
        .fetchSemanticsNode()
        .size
        .height
        .toFloat()
}
