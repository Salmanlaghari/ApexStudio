package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.apexstudio.app.domain.model.StickerOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w375dp-h830dp")
class StickerPanelPropertiesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `selected sticker shows working properties and stays compact`() {
        var updated: StickerOverlay? = null
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            Box(Modifier.width(375.dp).height(830.dp)) {
                StickerPanel(
                    onAddSticker = { _, _, _ -> }, onClose = {},
                    selectedSticker = StickerOverlay(id = "one", name = "Star", opacity = 1f, startMs = 500, endMs = 4_000),
                    totalTimelineMs = 8_000,
                    onUpdateSticker = { updated = it }, onDuplicate = {}, onDelete = {}
                )
            }
        }
        compose.onNodeWithTag("sticker-properties-strip").assertIsDisplayed()
        compose.onNodeWithTag("sticker-opacity-slider").assertIsDisplayed().performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(0.4f) }
        compose.runOnIdle { assertEquals(0.4f, updated!!.opacity, 0.02f) }
        compose.onNodeWithText("Range · 0.5s–4.0s").assertIsDisplayed()
        val height = compose.onNodeWithTag("sticker-panel-root").fetchSemanticsNode().boundsInRoot.height
        assertTrue("panel must fit within 300dp, was ${height / density}dp", height <= 300f * density + 1f)
    }

    @Test fun `controls without callbacks are not presented`() {
        compose.setContent {
            StickerPanel(onAddSticker = { _, _, _ -> }, onClose = {}, selectedSticker = StickerOverlay(id = "one"), totalTimelineMs = 10_000)
        }
        compose.onNodeWithTag("sticker-properties-strip").assertDoesNotExist()
    }
}
