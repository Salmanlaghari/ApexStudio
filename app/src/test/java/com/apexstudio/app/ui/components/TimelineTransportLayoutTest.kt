package com.apexstudio.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.apexstudio.app.ui.screens.editor.BottomTimelineActionRow
import com.apexstudio.app.ui.screens.editor.PlaybackControlBar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h720dp")
class TimelineTransportLayoutTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun compactTransportPreservesEveryCallbackOnNarrowScreens() {
        val calls = mutableListOf<String>()
        composeRule.setContent {
            Column(Modifier.width(320.dp)) {
                PlaybackControlBar(
                    canUndo = true, canRedo = true,
                    onTogglePlay = { calls += "play" },
                    onUndo = { calls += "undo" }, onRedo = { calls += "redo" },
                    onToggleKeyframe = { calls += "keyframe" },
                    onFullscreenToggle = { calls += "fullscreen" },
                )
            }
        }
        listOf("Undo", "Redo", "Play playback", "Add keyframe", "Toggle fullscreen").forEach {
            composeRule.onNodeWithContentDescription(it).assertIsDisplayed().performClick()
        }
        composeRule.runOnIdle { assertEquals(listOf("undo", "redo", "play", "keyframe", "fullscreen"), calls) }
    }

    @Test fun undoRedoAndKeyframesAreDisabledWhenUnavailable() {
        composeRule.setContent {
            PlaybackControlBar(canUndo = false, canRedo = false, canEditKeyframe = false)
        }
        composeRule.onNodeWithContentDescription("Undo").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Redo").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Keyframe editing unavailable").assertIsNotEnabled()
    }

    @Test fun precisionStripFits320dpAndPreservesNavigationAndZoom() {
        val calls = mutableListOf<String>()
        composeRule.setContent {
            Column(Modifier.width(320.dp)) {
                BottomTimelineActionRow(
                    hasKeyframeAtPlayhead = false, timelineZoom = 1f,
                    onToggleKeyframe = { calls += "add" }, onPrevKeyframe = { calls += "prev" },
                    onNextKeyframe = { calls += "next" }, onZoomIn = { calls += "in" },
                    onZoomOut = { calls += "out" },
                )
            }
        }
        listOf("Previous keyframe", "Add keyframe at playhead", "Next keyframe", "Zoom timeline out", "Zoom timeline in").forEach {
            composeRule.onNodeWithContentDescription(it).assertIsDisplayed().performClick()
        }
        composeRule.runOnIdle { assertEquals(listOf("prev", "add", "next", "out", "in"), calls) }
    }
}
