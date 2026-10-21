package com.apexstudio.app.ui.screens.editor

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.apexstudio.app.domain.model.StickerOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProjectStickerQuickTrayTest {
    @get:Rule val compose = createComposeRule()

    @Test fun traySelectsOnlyRealProjectStickerIdsAndCanBeClosed() {
        val calls = mutableListOf<String>()
        val stickers = listOf(StickerOverlay(id = "one", name = "First", symbolOrUri = "A"),
            StickerOverlay(id = "two", name = "Second", symbolOrUri = "B"))
        compose.setContent {
            ProjectStickerQuickTray(stickers, "one", { calls += it }, { calls += "close" })
        }
        compose.onNodeWithContentDescription("Select project sticker Second").performClick()
        compose.onNodeWithContentDescription("Close project sticker tray").performClick()
        compose.runOnIdle { assertEquals(listOf("two", "close"), calls) }
    }

    @Test fun emptyProjectDoesNotGetFakeReferenceStickersOrTrayControls() {
        compose.setContent { ProjectStickerQuickTray(emptyList(), null, {}, {}) }
        compose.onNodeWithContentDescription("Close project sticker tray").assertDoesNotExist()
        assertTrue(LOCKED_EDITOR_PREVIEW_ASPECT > 1.3f && LOCKED_EDITOR_PREVIEW_ASPECT < 1.4f)
    }
}
