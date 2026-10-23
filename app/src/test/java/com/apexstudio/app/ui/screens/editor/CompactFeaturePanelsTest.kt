package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.platform.LocalDensity
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
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w375dp-h830dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompactFeaturePanelsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun clipboardActionsCopyPasteAndResetActualPinValues() {
        val pin = Keyframe.identity(100L, "source").copy(scale = 2f, opacity = .4f)
        var pasted: Pair<Keyframe, Long>? = null
        var reset: Keyframe? = null
        compose.setContent {
            KeyframePanel(track = KeyframeTrack(listOf(pin)), playheadMs = 900L,
                onAdd = {}, onUpdate = { reset = it }, onRemove = {}, onClear = {},
                onClose = {}, onUpsertParam = { _, _ -> }, onPaste = { kf, time -> pasted = kf to time })
        }
        compose.onNodeWithText("Copy selected").performScrollTo().performClick()
        compose.onNodeWithText("Paste at playhead").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(pin to 900L, pasted) }
        compose.onNodeWithText("Reset selected").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(Keyframe.identity(100L, "source").copy(saturation = 1f), reset) }
    }

    @Test
    fun keyframePanelIsBoundedCloseStaysReachableAndGroupsScrollHorizontally() {
        var addedAt: Long? = null
        var densityScale = 1f
        compose.setContent {
            densityScale = LocalDensity.current.density
            Box(Modifier.width(375.dp).height(830.dp)) {
                KeyframePanel(
                    track = KeyframeTrack(keyframes = listOf(Keyframe.identity(0L, "pin-1"))),
                    playheadMs = 500L,
                    onAdd = { addedAt = it },
                    onUpdate = {},
                    onRemove = {},
                    onClear = {},
                    onClose = {},
                    onUpsertParam = { _, _ -> }
                )
            }
        }

        compose.onNodeWithTag("keyframe-panel-root").assertHeightAtMost300Dp(densityScale)
        compose.onNodeWithContentDescription("Close").assertIsDisplayed()
        compose.onNodeWithText("Timeline · 1 pins").assertIsDisplayed()
        compose.onNodeWithText("Add @", substring = true).performClick()
        compose.runOnIdle { assertEquals(500L, addedAt) }
        compose.onNodeWithText("Effects").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Interpolation curve").performScrollTo().assertIsDisplayed()

        // The always-visible timeline card remains a working way to add a pin.
        compose.onNodeWithTag("keyframe-panel-cards").performTouchInput { repeat(7) { swipeLeft() } }
        compose.onNodeWithContentDescription("Close").assertIsDisplayed()
    }

    @Test
    fun speedPanelIsBoundedAndMotionBlurControlsAreReachableByHorizontalScroll() {
        var closed = false
        var densityScale = 1f
        compose.setContent {
            densityScale = LocalDensity.current.density
            Box(Modifier.width(375.dp).height(830.dp)) {
                SpeedRampPanel(
                    selectedClipId = "clip-1",
                    currentSpeed = 1f,
                    activeClipSpeed = 1f,
                    opticalFlowEnabled = true,
                    onSelectPreset = {},
                    onCustomSpeed = {},
                    onToggleOpticalFlow = {},
                    onChangeOpticalFlowIntensity = {},
                    clipIsVideo = true,
                    onClose = { closed = true }
                )
            }
        }

        compose.onNodeWithTag("speed-ramp-panel-root").assertHeightAtMost300Dp(densityScale)
        compose.onNodeWithContentDescription("Close").assertIsDisplayed()
        compose.onNodeWithText("Optical Flow Motion Blur").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Blur intensity · 50%").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close").performClick()
        compose.runOnIdle { assertTrue(closed) }
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertHeightAtMost300Dp(density: Float) {
    val measuredHeight = fetchSemanticsNode().boundsInRoot.height
    assertTrue("Expected panel height <= 300dp, was ${measuredHeight / density}dp", measuredHeight <= 300f * density + 1f)
}
