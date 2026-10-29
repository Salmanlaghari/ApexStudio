package com.apexstudio.app.ui.screens.editor

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditPackTimeActionsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun selectedClipMotionActionsOpenTheOperationalRangeWorkspace() {
        val opens = AtomicInteger()
        compose.setContent {
            EditPackPanel(
                selectedClipName = "selected.mp4",
                onApplySpeedRamp = { _, _, _, _ -> },
                onApplyFreeze = {},
                onApplySplitScreen = { _, _ -> },
                onApplyZoom = { _, _ -> },
                onClose = {},
                onOpenTimeActions = { opens.incrementAndGet() },
            )
        }

        compose.onNodeWithTag("edit-action-slowmo").assertIsDisplayed().assertIsEnabled().assertHasClickAction()
            .performClick()
        compose.onNodeWithTag("edit-action-reverse").assertIsDisplayed().assertIsEnabled().assertHasClickAction()
            .performClick()
        compose.runOnIdle { assertEquals(2, opens.get()) }
    }

    @Test
    fun motionActionsRequireAnExplicitlySelectedClip() {
        compose.setContent {
            EditPackPanel(
                selectedClipName = null,
                onApplySpeedRamp = { _, _, _, _ -> },
                onApplyFreeze = {},
                onApplySplitScreen = { _, _ -> },
                onApplyZoom = { _, _ -> },
                onClose = {},
                onOpenTimeActions = {},
            )
        }

        compose.onNodeWithTag("edit-action-slowmo").assertIsNotEnabled()
        compose.onNodeWithTag("edit-action-reverse").assertIsNotEnabled()
    }
}
