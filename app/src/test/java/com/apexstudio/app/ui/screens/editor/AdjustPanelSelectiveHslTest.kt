package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.apexstudio.app.domain.model.HslBandAdjustment
import com.apexstudio.app.domain.model.VideoAdjustments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdjustPanelSelectiveHslTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun hslBandSliderResetAndBeforeAfterToggleAreFunctionalOnACompactPhone() {
        val adjustments = mutableStateOf(VideoAdjustments())
        val compareMode = mutableStateOf(false)

        compose.setContent {
            Box(Modifier.width(320.dp).height(800.dp)) {
                AdjustPanel(
                    adjustments = adjustments.value,
                    onUpdate = { transform -> adjustments.value = transform(adjustments.value) },
                    onReset = {},
                    onResetAll = { adjustments.value = VideoAdjustments() },
                    onClose = {},
                    compareMode = compareMode.value,
                    onToggleCompare = { compareMode.value = !compareMode.value }
                )
            }
        }

        compose.onNodeWithTag("adjust_compare_toggle").assertTextEquals("Before").performClick()
        assertTrue(compareMode.value)
        compose.onNodeWithTag("adjust_compare_toggle").assertTextEquals("After")

        compose.onNodeWithText("HSL").performClick()
        compose.onNodeWithTag("hsl_saturation_slider").assertIsDisplayed()
        compose.onNodeWithTag("hsl_range_yellow").performClick()
        compose.onNodeWithTag("hsl_saturation_slider").performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
            setProgress(0.4f)
        }
        compose.waitForIdle()
        assertEquals(0.4f, adjustments.value.selectiveHsl.yellow.saturation, 0.01f)

        compose.onNodeWithTag("hsl_reset_range").performClick()
        compose.waitForIdle()
        assertEquals(HslBandAdjustment(), adjustments.value.selectiveHsl.yellow)

        compose.onNodeWithTag("hsl_saturation_slider").assertHeightIsAtLeast(48.dp)
        val swatchRow = compose.onNodeWithTag("hsl_color_range_row")
        swatchRow.performTouchInput { swipeLeft() }
        compose.waitForIdle()
        val rowBounds = swatchRow.fetchSemanticsNode().boundsInRoot
        val magentaBounds = compose.onNodeWithTag("hsl_range_magenta").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Magenta swatch should be fully reachable on a compact phone: row=$rowBounds item=$magentaBounds",
            magentaBounds.left >= rowBounds.left && magentaBounds.right <= rowBounds.right
        )

        compose.onNodeWithText("Colours").performClick()
        compose.onNodeWithTag("adjust_active_tool_title").assertTextEquals("Saturation")
        compose.onNodeWithText("Details").performClick()
        compose.onNodeWithTag("adjust_active_tool_title").assertTextEquals("Sharpness")
    }
}
