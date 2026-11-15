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

    @Test fun inlineKeyframeControlFits320dpAndRoutesToRealMutation() {
        val actions = mutableListOf<String>()
        compose.setContent {
            Column(Modifier.requiredSize(320.dp, 64.dp)) {
                PlaybackControlBar(
                    currentTimeMs = 0L,
                    durationMs = 10_000L,
                    showInlineKeyframe = true,
                    onToggleKeyframe = { actions += "keyframe" },
                )
            }
        }
        val keyframe = compose.onNodeWithContentDescription("Add keyframe")
        keyframe.assertIsDisplayed()
        val bounds = keyframe.fetchSemanticsNode().boundsInRoot
        assertTrue("The keyframe action stays within the compact phone width", bounds.left >= 0f && bounds.right <= 320f)
        keyframe.performClick()
        compose.runOnIdle { assertEquals(listOf("keyframe"), actions) }
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
