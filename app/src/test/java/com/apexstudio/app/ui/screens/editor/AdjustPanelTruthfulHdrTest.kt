package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.apexstudio.app.domain.model.VideoAdjustments
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdjustPanelTruthfulHdrTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `tone map control describes SDR style without claiming output format on a compact phone`() {
        compose.setContent {
            Box(Modifier.width(320.dp).height(800.dp)) {
                AdjustPanel(
                    adjustments = VideoAdjustments(),
                    onUpdate = {},
                    onReset = {},
                    onResetAll = {},
                    onClose = {}
                )
            }
        }

        compose.onAllNodesWithText("SDR Tone Map").assertCountEquals(2)
        compose.onNodeWithText("SDR-style tone-map only; it does not create HDR video.").assertIsDisplayed()
    }
}
