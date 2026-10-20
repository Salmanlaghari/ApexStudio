package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
    fun toggleIsDiscoverableAndChangesTheAvailableTimelineHeightWithoutRemovingEitherPane() {
        compose.setContent {
            var isFocused by remember { mutableStateOf(false) }
            Column(Modifier.fillMaxWidth().height(600.dp)) {
                Box(
                    Modifier
                        .weight(editorPreviewLayoutWeight(isFocused))
                        .fillMaxWidth()
                        .testTag("preview-pane")
                )
                TimelineFocusModeControl(
                    isFocused = isFocused,
                    onToggle = { isFocused = !isFocused }
                )
                Box(
                    Modifier
                        .weight(timelineTrackLayoutWeight(isFocused))
                        .fillMaxWidth()
                        .testTag("timeline-pane")
                )
            }
        }

        compose.onNodeWithContentDescription("Expand timeline to full height")
            .assertIsDisplayed()
        val normalPreviewHeight = paneHeight("preview-pane")
        val normalTimelineHeight = paneHeight("timeline-pane")
        assertTrue("normal preview should have useful height", normalPreviewHeight > 100f)
        assertTrue("default mobile editing should prioritize the timeline", normalTimelineHeight > normalPreviewHeight)
        assertTrue("default timeline should be a useful working area", normalTimelineHeight > 300f)

        compose.onNodeWithContentDescription("Expand timeline to full height").performClick()

        compose.onNodeWithContentDescription("Restore preview and normal timeline layout")
            .assertIsDisplayed()
        compose.onNodeWithTag("preview-pane").assertExists()
        compose.onNodeWithTag("timeline-pane").assertExists()
        val focusedPreviewHeight = paneHeight("preview-pane")
        val focusedTimelineHeight = paneHeight("timeline-pane")
        assertTrue("focus mode must collapse preview without disposing it", focusedPreviewHeight < 2f)
        assertTrue("focus mode must allocate remaining height to timeline", focusedTimelineHeight > normalTimelineHeight)
        assertTrue("focus mode must meaningfully grow timeline", focusedTimelineHeight > 500f)

        compose.onNodeWithContentDescription("Restore preview and normal timeline layout").performClick()
        compose.onNodeWithContentDescription("Expand timeline to full height").assertIsDisplayed()
        assertEquals(normalPreviewHeight, paneHeight("preview-pane"), 1f)
        assertEquals(normalTimelineHeight, paneHeight("timeline-pane"), 1f)
    }

    @Test
    fun defaultTimelineFirstSplitKeepsPreviewReachableAt400Dp() {
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
        assertTrue("Timeline should remain the larger working pane: $timelineHeight", timelineHeight > previewHeight)
    }

    private fun paneHeight(tag: String): Float = compose
        .onNodeWithTag(tag)
        .fetchSemanticsNode()
        .size
        .height
        .toFloat()
}
