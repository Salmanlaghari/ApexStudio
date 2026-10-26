package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.apexstudio.app.data.filter.FilterManifest
import com.apexstudio.app.data.filter.GpuFilterConfig
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w375dp-h830dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GpuVideoFilterPanelTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `featured Viral HDR look is visible at the top and applies its stable id`() {
        var appliedId: String? = null
        compose.setContent {
            Box(Modifier.width(375.dp).height(830.dp)) {
                GpuVideoFilterPanel(
                    selectedClip = null,
                    config = GpuFilterConfig(),
                    manifest = FilterManifest(categories = emptyList(), filters = emptyList()),
                    selectedTab = 3,
                    compareMode = false,
                    splitPosition = 0.5f,
                    onTabSelected = {},
                    onConfigChange = {},
                    onToggleCompare = {},
                    onSplitPositionChange = {},
                    onApplyToClip = {},
                    onApplyToAllClips = {},
                    onReset = {},
                    onClose = {},
                    onApplyOneTapLook = { appliedId = it }
                )
            }
        }

        compose.onNodeWithText("One-Tap Looks").assertIsDisplayed()
        compose.onNodeWithTag("one-tap-look-viral_hdr").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("viral_hdr", appliedId) }
    }
}
