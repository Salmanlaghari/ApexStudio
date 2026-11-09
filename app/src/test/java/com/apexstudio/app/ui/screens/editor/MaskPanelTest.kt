package com.apexstudio.app.ui.screens.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.apexstudio.app.domain.model.MaskKeyframe
import com.apexstudio.app.domain.model.MaskPoint
import com.apexstudio.app.domain.model.MaskShape
import com.apexstudio.app.domain.model.VideoMask
import com.apexstudio.app.domain.model.resolveMaskAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
class MaskPanelTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun presetAndShapeBrowsersAreHorizontalAndPresetsAppearFirst() {
        compose.setContent { MaterialTheme { MaskPanel(mask = null, onUpdate = {}, onClose = {}) } }

        compose.onNodeWithContentDescription("Horizontal mask shape strip").assertExists()
        val rect = compose.onNodeWithContentDescription("Mask shape Rectangle").fetchSemanticsNode().boundsInRoot
        val ellipse = compose.onNodeWithContentDescription("Mask shape Ellipse").fetchSemanticsNode().boundsInRoot
        val circle = compose.onNodeWithContentDescription("Mask shape Circle").fetchSemanticsNode().boundsInRoot
        assertEquals("shape chips share a horizontal row", rect.top, ellipse.top, 0.1f)
        assertEquals("shape chips share a horizontal row", rect.top, circle.top, 0.1f)
        assertTrue(rect.left < ellipse.left && ellipse.left < circle.left)
        compose.onNodeWithText("PRESETS").assertExists()
        compose.onNodeWithText("SHAPE").assertExists()
        compose.onNodeWithText("Blur Faces unavailable", substring = true).assertExists()
        compose.onNodeWithContentDescription("Mask shape Blur Faces").assertDoesNotExist()
        val presetTop = compose.onNodeWithText("PRESETS").fetchSemanticsNode().boundsInRoot.top
        val shapeTop = compose.onNodeWithText("SHAPE").fetchSemanticsNode().boundsInRoot.top
        assertTrue("preset row should be above the shape strip", presetTop < shapeTop)
    }

    @Test
    fun tappingShapeAppliesCenteredCompleteDefaultsAndRetainsExistingAnimationKeys() {
        val key = MaskKeyframe(id = "existing", timeMs = 250, centerX = 0.3f, sizeX = 0.2f)
        val custom = VideoMask(
            shape = MaskShape.HEART, centerX = 0.18f, centerY = 0.76f,
            sizeX = 0.1f, sizeY = 0.9f, feather = 0.8f, invert = true,
            opacity = 0.31f, rotationDeg = 121f, presetId = "stale-preset",
            maskKeyframes = listOf(key), drawPoints = listOf(MaskPoint(0f, 0f), MaskPoint(1f, 0f), MaskPoint(0f, 1f))
        )
        var updated: VideoMask? = null
        compose.setContent { MaterialTheme { MaskPanel(mask = custom, onUpdate = { updated = it }, onClose = {}) } }

        compose.onNodeWithContentDescription("Mask shape Rectangle").activateControl()
        compose.runOnIdle {
            val result = requireNotNull(updated)
            assertEquals(MaskShape.RECTANGLE, result.shape)
            assertEquals(0.5f, result.centerX, 0.0001f)
            assertEquals(0.5f, result.centerY, 0.0001f)
            assertEquals(0.58f, result.sizeX, 0.0001f)
            assertEquals(0.58f, result.sizeY, 0.0001f)
            assertEquals(0.15f, result.feather, 0.0001f)
            assertEquals(1f, result.opacity, 0.0001f)
            assertFalse(result.invert)
            assertEquals(0f, result.rotationDeg, 0.0001f)
            assertNull(result.presetId)
            assertTrue("old pen points must not carry into another shape", result.drawPoints.isEmpty())
            assertTrue("other animation keys survive shape selection", key in result.maskKeyframes)
            val atPlayhead = result.resolveMaskAt(0L)
            assertEquals(0.5f, atPlayhead.centerX, 0.0001f)
            assertEquals(0.58f, atPlayhead.sizeX, 0.0001f)
            assertFalse(atPlayhead.invert)
        }
    }

    @Test
    fun tappingPresetAppliesWholePresetRatherThanMergingStaleShapeGeometry() {
        val custom = VideoMask(
            shape = MaskShape.STAR, centerX = 0.1f, centerY = 0.9f,
            sizeX = 0.2f, sizeY = 0.3f, feather = 0.7f, invert = true,
            opacity = 0.2f, rotationDeg = 90f, presetId = "old",
            maskKeyframes = listOf(MaskKeyframe("old-key", 10))
        )
        var updated: VideoMask? = null
        compose.setContent { MaterialTheme { MaskPanel(mask = custom, onUpdate = { updated = it }, onClose = {}) } }

        compose.onNodeWithText("Portrait").activateControl()
        compose.runOnIdle {
            val preset = requireNotNull(updated)
            assertEquals(MaskShape.CIRCLE, preset.shape)
            assertEquals(0.5f, preset.centerX, 0.0001f)
            assertEquals(0.5f, preset.centerY, 0.0001f)
            assertEquals(0.5f, preset.sizeX, 0.0001f)
            assertEquals(0.5f, preset.sizeY, 0.0001f)
            assertEquals(0.35f, preset.feather, 0.0001f)
            assertEquals("portrait", preset.presetId)
            assertFalse(preset.invert)
            assertEquals(1f, preset.opacity, 0.0001f)
            assertTrue("applying a new preset explicitly resets old keys", preset.maskKeyframes.isEmpty())
        }
    }

    @Test
    fun removingMaskDoesNotResurrectWhenNullStateIsShownAgain() {
        val current = mutableStateOf<VideoMask?>(VideoMask.default())
        compose.setContent { MaterialTheme { MaskPanel(mask = current.value, onUpdate = { current.value = it }, onClose = {}) } }
        compose.onNodeWithText("Remove").activateControl()
        compose.runOnIdle { assertNull(current.value) }

        compose.onNodeWithText("Choose a preset or shape to add a centered mask.", substring = true).assertExists()
        compose.onNodeWithText("Fine-tune").assertDoesNotExist()
        compose.runOnIdle { assertNull(current.value) }
    }

    @Test
    fun openingPreservesCustomMaskAndManualControlsStayCollapsedUntilRequested() {
        val custom = VideoMask(
            shape = MaskShape.TRIANGLE, centerX = 0.27f, centerY = 0.63f,
            sizeX = 0.44f, sizeY = 0.35f, feather = 0.41f, invert = true,
            opacity = 0.68f, rotationDeg = -25f, maskKeyframes = listOf(MaskKeyframe("k", 100))
        )
        var updateCount = 0
        compose.setContent { MaterialTheme { MaskPanel(mask = custom, onUpdate = { updateCount++ }, onClose = {}) } }

        compose.onNodeWithText("Fine-tune").assertExists()
        compose.onNodeWithText("Feather").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, updateCount)
            assertEquals(0.27f, custom.centerX, 0.0001f)
            assertEquals(0.41f, custom.feather, 0.0001f)
            assertEquals(1, custom.maskKeyframes.size)
        }

        compose.onNodeWithText("Fine-tune").activateControl()
        compose.onNodeWithText("Feather").assertExists()
        compose.onNodeWithText("Opacity").assertExists()
        compose.onNodeWithText("Rotation").assertExists()
        compose.runOnIdle { assertEquals("opening and expanding fine-tune do not mutate", 0, updateCount) }
    }

    private fun SemanticsNodeInteraction.activateControl() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertIsDisplayed()
        performSemanticsAction(SemanticsActions.OnClick) { action -> action() }
        compose.waitForIdle()
    }
}
