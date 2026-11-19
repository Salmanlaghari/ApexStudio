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
import com.apexstudio.app.presentation.state.TIMELINE_MIN_ZOOM
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
                mutableStateOf(EditorState(playerPositionMs = 1_234L, magneticSnapping = true, rippleEditing = true, timelineZoom = 0.75f))
            }
            var focused by remember { mutableStateOf(false) }
            Box(Modifier.requiredSize(320.dp, 100.dp).testTag("toolbar-test-root")) {
                ProfessionalTimelineToolbar(
                    state = state,
                    focused = focused,
                    onToggleFocus = { focused = !focused },
                    onToggleSnapping = { state = state.copy(magneticSnapping = !state.magneticSnapping) },
                    onToggleRippleEditing = { state = state.copy(rippleEditing = !state.rippleEditing) },
                    onZoomOut = { state = state.copy(timelineZoom = TIMELINE_MIN_ZOOM) },
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
        compose.onNodeWithContentDescription("Ripple editing enabled").performClick()
        compose.onNodeWithContentDescription("Ripple editing disabled").assertIsDisplayed()
        compose.onNodeWithText("Ripple off").assertIsDisplayed()
        compose.onNodeWithContentDescription("Ripple editing disabled").performClick()
        compose.onNodeWithContentDescription("Ripple editing enabled").assertIsDisplayed()

        compose.onNodeWithContentDescription("Zoom timeline out").performClick()
        compose.onNodeWithText("0.01x").assertIsDisplayed()
        compose.onNodeWithContentDescription("Zoom timeline in").performClick()
        compose.onNodeWithText("1.5x").assertIsDisplayed()
        compose.onNodeWithContentDescription("Expand timeline to full height").performClick()
        compose.onNodeWithContentDescription("Restore preview and normal timeline layout").assertIsDisplayed()

        val root = compose.onNodeWithTag("toolbar-test-root").fetchSemanticsNode().boundsInRoot
        val toolbar = compose.onNodeWithTag("professional-timeline-toolbar").fetchSemanticsNode().boundsInRoot
        val ripple = compose.onNodeWithTag("timeline-ripple-toggle").fetchSemanticsNode().boundsInRoot
        val focus = compose.onNodeWithTag("timeline-focus-toggle").fetchSemanticsNode().boundsInRoot
        val snap = compose.onNodeWithTag("timeline-snap-toggle").fetchSemanticsNode().boundsInRoot
        val zoomOut = compose.onNodeWithContentDescription("Zoom timeline out").fetchSemanticsNode().boundsInRoot
        val zoomIn = compose.onNodeWithContentDescription("Zoom timeline in").fetchSemanticsNode().boundsInRoot
        assertTrue("Timeline controls must stay within a compact phone width: $toolbar root=$root", toolbar.right <= root.right)
        assertTrue("Ripple control must stay within a compact phone width: $ripple root=$root", ripple.right <= root.right)
        assertTrue("Focus control must stay within a compact phone width: $focus root=$root", focus.right <= root.right)
        assertTrue("Timeline toolbar should be compact: $toolbar", toolbar.height <= 80f)
        assertTrue("Snap should not stretch across unused row width: $snap", snap.width in 44f..64f)
        assertTrue("Ripple should keep an intrinsic compact width: $ripple", ripple.width in 44f..80f)
        assertEquals("Snap and Ripple share one control row", snap.top, ripple.top, 1f)
        assertEquals("Focus shares the compact control row", snap.top, focus.top, 1f)
        assertEquals("Zoom shares the compact control row", snap.top, zoomOut.top, 1f)
        assertEquals("Zoom buttons share the compact control row", zoomOut.top, zoomIn.top, 1f)
        assertTrue("Snap target should be at least 44dp tall and wide: $snap", snap.width >= 44f && snap.height >= 44f)
        assertTrue("Ripple target should be at least 44dp tall and wide: $ripple", ripple.width >= 44f && ripple.height >= 44f)
        assertTrue("Focus target should be at least 44dp: $focus", focus.width >= 44f && focus.height >= 44f)
        assertTrue("Zoom target should be at least 44dp: $zoomOut", zoomOut.width >= 44f && zoomOut.height >= 44f)
        assertTrue("Zoom-in remains inside the screen width: $zoomIn root=$root", zoomIn.right <= root.right)
    }
}
