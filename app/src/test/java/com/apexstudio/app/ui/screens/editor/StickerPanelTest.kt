package com.apexstudio.app.ui.screens.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StickerPanelTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun searchingBundledRomanUrduTagAddsDecodedFluent3dSticker() {
        var added: Triple<String, String, String>? = null
        compose.setContent {
            StickerPanel(
                onAddSticker = { path, category, name -> added = Triple(path, category, name) },
                onClose = {}
            )
        }

        compose.onNodeWithText("3D look").assertIsDisplayed().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("dil")
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithContentDescription("3D Red Heart")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("3D Red Heart").assertIsDisplayed().performClick()

        compose.runOnIdle {
            val result = added
            assertNotNull("The decoded bundled sticker should be insertable", result)
            val inserted = result ?: throw AssertionError("No sticker was inserted")
            assertTrue(inserted.first.startsWith("stickers/"))
            assertEquals("3d", inserted.second)
            assertEquals("3D Red Heart", inserted.third)
        }
    }
}
