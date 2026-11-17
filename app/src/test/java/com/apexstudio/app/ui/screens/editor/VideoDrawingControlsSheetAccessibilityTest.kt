package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.apexstudio.app.domain.model.VideoDrawingTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
class VideoDrawingControlsSheetAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun brushSwatchesAreNamedReachableAndSelectableOnPortraitPhoneWidth() {
        val colors = listOf(
            "Pink" to 0xFFFF3B82L,
            "Cyan" to 0xFF19D3F3L,
            "Yellow" to 0xFFFFD54FL,
            "Mint" to 0xFF73E6A6L,
            "White" to 0xFFFFFFFFL,
            "Violet" to 0xFF8C6CFFL
        )
        val selectedColor = mutableStateOf(0L)

        compose.setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(360.dp, 80.dp)) {
                    DrawingColorPalette(
                        tool = VideoDrawingTool.BRUSH,
                        colorArgb = selectedColor.value,
                        onColorChange = { selectedColor.value = it }
                    )
                }
            }
        }
        compose.waitForIdle()
        val minimumTargetPx = with(compose.density) { 48.dp.toPx() }

        colors.forEach { (name, argb) ->
            val swatch = compose.onNodeWithContentDescription("Brush color $name")
                .assertIsDisplayed()
                .assertHasClickAction()
            val bounds = swatch.fetchSemanticsNode().boundsInRoot
            assertTrue("$name target width ${bounds.width} is below 48dp", bounds.width >= minimumTargetPx)
            assertTrue("$name target height ${bounds.height} is below 48dp", bounds.height >= minimumTargetPx)

            swatch.performClick()
            compose.runOnIdle { assertEquals("$name callback", argb, selectedColor.value) }
            compose.onNodeWithContentDescription("Brush color $name").assertIsSelected()
        }
    }
}
