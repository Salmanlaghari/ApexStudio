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

    private fun render(landscape: Boolean, fullscreen: Boolean = false) {
        compose.setContent {
            Column(Modifier.requiredSize(if (landscape) 840.dp else 375.dp, 400.dp)) {
                AdaptiveEditorWorkspace(landscape, focused = false, fullscreen = fullscreen,
                    preview = { Box(Modifier.fillMaxWidth().weight(1f).testTag("preview-content")) },
                    timeline = { Box(Modifier.fillMaxWidth().weight(1f).testTag("timeline-content")) },
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
        compose.onNodeWithText("Hide properties ›").performClick()
        compose.onNodeWithText("Real properties").assertDoesNotExist()
        val expanded = compose.onNodeWithTag("landscape-preview").fetchSemanticsNode().boundsInRoot
        assertTrue("Collapsing properties must enlarge preview", expanded.width > preview.width)
        compose.onNodeWithText("‹").performClick()
        compose.onNodeWithText("Real properties").assertIsDisplayed()
    }

    @Test fun portraitKeepsBothExistingSlotsWithoutLandscapeInspector() {
        render(false)
        compose.onNodeWithTag("preview-content").assertIsDisplayed()
        compose.onNodeWithTag("timeline-content").assertIsDisplayed()
        compose.onNodeWithTag("landscape-inspector").assertDoesNotExist()
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
