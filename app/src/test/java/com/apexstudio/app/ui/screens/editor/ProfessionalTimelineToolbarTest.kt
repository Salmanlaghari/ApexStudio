package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.apexstudio.app.presentation.state.EditorState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp")
class ProfessionalTimelineToolbarTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun controlsReflectAndUpdateRealTimelineStateWithinCompactPhoneWidth() {
        compose.setContent {
            var state by remember {
                mutableStateOf(EditorState(playerPositionMs = 1_234L, magneticSnapping = true, timelineZoom = 0.75f))
            }
            var focused by remember { mutableStateOf(false) }
            Box(Modifier.requiredSize(320.dp, 100.dp).testTag("toolbar-test-root")) {
                ProfessionalTimelineToolbar(
                    state = state,
                    focused = focused,
                    onToggleFocus = { focused = !focused },
                    onToggleSnapping = { state = state.copy(magneticSnapping = !state.magneticSnapping) },
                    onZoomOut = { state = state.copy(timelineZoom = 0.5f) },
                    onZoomIn = { state = state.copy(timelineZoom = 1.5f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        compose.onNodeWithTag("professional-timeline-toolbar").assertIsDisplayed()
        compose.onNodeWithText("00:01.234").assertIsDisplayed()
        compose.onNodeWithText("0.8x").assertIsDisplayed()
        compose.onNodeWithContentDescription("Magnetic snapping enabled").performClick()
        compose.onNodeWithContentDescription("Magnetic snapping disabled").assertIsDisplayed()

        compose.onNodeWithContentDescription("Zoom timeline in").performClick()
        compose.onNodeWithText("1.5x").assertIsDisplayed()
        compose.onNodeWithContentDescription("Expand timeline to full height").performClick()
        compose.onNodeWithContentDescription("Restore preview and normal timeline layout").assertIsDisplayed()

        val root = compose.onNodeWithTag("toolbar-test-root").fetchSemanticsNode().boundsInRoot
        val toolbar = compose.onNodeWithTag("professional-timeline-toolbar").fetchSemanticsNode().boundsInRoot
        assertTrue("Timeline controls must stay within a compact phone width: $toolbar root=$root", toolbar.right <= root.right)
    }
}
