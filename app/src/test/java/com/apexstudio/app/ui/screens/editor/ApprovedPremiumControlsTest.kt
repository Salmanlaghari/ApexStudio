package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.apexstudio.app.domain.model.Keyframe
import com.apexstudio.app.domain.model.KeyframeTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h720dp")
class ApprovedPremiumControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectedVideoStripFits320dpAndRoutesEachControlToRealAction() {
        val actions = mutableListOf<String>()
        compose.setContent {
            Column(Modifier.requiredSize(320.dp, 108.dp)) {
                SelectedVideoKeyframeControl(
                    hasKeyframeAtPlayhead = false,
                    onToggleKeyframe = { actions += "keyframe" },
                    onOpenAnimation = { actions += "animation" },
                    onOpenPosition = { actions += "position" },
                    onOpenScale = { actions += "scale" },
                    onOpenRotation = { actions += "rotation" },
                    onOpenOpacity = { actions += "opacity" },
                    modifier = Modifier
                )
            }
        }
        compose.onNodeWithContentDescription("Selected type: Video").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add keyframe").assertIsDisplayed()
        compose.onNodeWithContentDescription("Edit video keyframe animation").assertIsDisplayed()
        listOf("Position", "Scale", "Rotation", "Opacity").forEach { label ->
            compose.onNodeWithContentDescription("Open $label keyframe controls").assertIsDisplayed()
        }
        val opacityBounds = compose.onNodeWithContentDescription("Open Opacity keyframe controls").fetchSemanticsNode().boundsInRoot
        assertTrue("The final property control stays within the 320dp editor width", opacityBounds.left >= 0f && opacityBounds.right <= 320f)
        listOf("Add keyframe", "Edit video keyframe animation", "Open Position keyframe controls",
            "Open Scale keyframe controls", "Open Rotation keyframe controls", "Open Opacity keyframe controls").forEach {
            compose.onNodeWithContentDescription(it).performClick()
        }
        compose.runOnIdle { assertEquals(listOf("keyframe", "animation", "position", "scale", "rotation", "opacity"), actions) }
    }

    @Test fun keyframePanelInitialPropertyFilterShowsOnlyThatPropertyEditor() {
        compose.setContent {
            KeyframePanel(
                track = KeyframeTrack(listOf(Keyframe(id = "pin", timeMs = 0L))),
                playheadMs = 0L,
                clipDurationMs = 5_000L,
                onAdd = {}, onUpdate = {}, onRemove = {}, onClear = {}, onClose = {}, onUpsertParam = { _, _ -> },
                initialPropertyFilter = KeyframePropertyFilter.OPACITY
            )
        }
        compose.onNodeWithText("Appearance").assertExists()
        compose.onNodeWithText("Transform").assertDoesNotExist()
    }
}
