package com.apexstudio.app.ui.screens.editor

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.apexstudio.app.data.filter.FilterManifest
import com.apexstudio.app.presentation.state.LutTargetTrack
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ColorGradingLutPanelFunctionalControlsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `unsupported fine tune controls are absent and LUT comparison remains interactive`() {
        var compareToggled = false
        compose.setContent {
            ColorGradingLutPanel(
                manifest = FilterManifest(categories = emptyList(), filters = emptyList()),
                activeFilterId = null,
                intensity = 1f,
                targetTrack = LutTargetTrack.ALL_TRACKS,
                compareMode = false,
                splitPosition = 0.5f,
                favoriteIds = emptySet(),
                customLuts = emptyList(),
                onSelectFilter = {},
                onIntensityChange = {},
                onTargetTrackChange = {},
                onToggleFavorite = {},
                onToggleCompare = { compareToggled = true },
                onSplitPositionChange = {},
                onReset = {},
                onClose = {}
            )
        }

        compose.onNodeWithText("Color Grading & Filters").assertIsDisplayed()
        compose.onNodeWithContentDescription("White Balance & Tone").assertDoesNotExist()
        compose.onNodeWithContentDescription("Compare Before/After").performClick()
        compose.runOnIdle { assertTrue(compareToggled) }
    }
}
