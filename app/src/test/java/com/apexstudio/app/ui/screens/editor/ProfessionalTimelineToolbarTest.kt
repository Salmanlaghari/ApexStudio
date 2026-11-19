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
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.apexstudio.app.presentation.state.EditorState
import org.junit.Assert.assertEquals
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
    fun layerControlsKeepFunctional44dpTargetsWithoutTheRemovedHeaderOrZoomRow() {
        compose.setContent {
            var state by remember {
                mutableStateOf(EditorState(playerPositionMs = 1_234L, magneticSnapping = true, rippleEditing = true))
            }
            var focused by remember { mutableStateOf(false) }
            Box(Modifier.requiredSize(320.dp, 100.dp).testTag("toolbar-test-root")) {
                TimelineLayerControls(
                    state = state,
                    focused = focused,
                    onToggleFocus = { focused = !focused },
                    onToggleSnapping = { state = state.copy(magneticSnapping = !state.magneticSnapping) },
                    onToggleRippleEditing = { state = state.copy(rippleEditing = !state.rippleEditing) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        compose.onNodeWithTag("timeline-layer-controls").assertIsDisplayed()
        compose.onNodeWithText("TIMELINE").assertDoesNotExist()
        compose.onNodeWithText("00:01.234").assertDoesNotExist()
        compose.onNodeWithContentDescription("Zoom timeline out").assertDoesNotExist()
        compose.onNodeWithContentDescription("Zoom timeline in").assertDoesNotExist()
        compose.onNodeWithContentDescription("Magnetic snapping enabled").performClick()
        compose.onNodeWithContentDescription("Magnetic snapping disabled").assertIsDisplayed()
        compose.onNodeWithContentDescription("Ripple editing enabled").performClick()
        compose.onNodeWithContentDescription("Ripple editing disabled").assertIsDisplayed()
        compose.onNodeWithText("Ripple off").assertIsDisplayed()
        compose.onNodeWithContentDescription("Ripple editing disabled").performClick()
        compose.onNodeWithContentDescription("Ripple editing enabled").assertIsDisplayed()
        compose.onNodeWithContentDescription("Expand timeline to full height").performClick()
        compose.onNodeWithContentDescription("Restore preview and normal timeline layout").assertIsDisplayed()

        val root = compose.onNodeWithTag("toolbar-test-root").fetchSemanticsNode().boundsInRoot
        val controls = compose.onNodeWithTag("timeline-layer-controls").fetchSemanticsNode().boundsInRoot
        val ripple = compose.onNodeWithTag("timeline-ripple-toggle").fetchSemanticsNode().boundsInRoot
        val focus = compose.onNodeWithTag("timeline-focus-toggle").fetchSemanticsNode().boundsInRoot
        val snap = compose.onNodeWithTag("timeline-snap-toggle").fetchSemanticsNode().boundsInRoot
        assertTrue("Controls must stay within a compact phone width: controls=$controls root=$root", controls.right <= root.right)
        assertEquals("The embedded controls use one 44dp row", 44f, controls.height, 1f)
        assertTrue("Snap keeps a compact width", snap.width in 44f..64f)
        assertTrue("Ripple keeps an intrinsic compact width", ripple.width in 44f..80f)
        assertEquals("Snap and Ripple share one control row", snap.top, ripple.top, 1f)
        assertEquals("Focus shares the layer-control row", snap.top, focus.top, 1f)
        assertTrue("Snap target stays at least 44dp", snap.width >= 44f && snap.height >= 44f)
        assertTrue("Ripple target stays at least 44dp", ripple.width >= 44f && ripple.height >= 44f)
        assertTrue("Focus target stays at least 44dp", focus.width >= 44f && focus.height >= 44f)
    }
}
