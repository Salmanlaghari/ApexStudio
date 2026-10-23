package com.apexstudio.app.ui.components

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.apexstudio.app.domain.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w840dp-h400dp-mdpi")
class OverlappingTimelineLayersTest {
    @get:Rule val compose = createComposeRule()

    @Test fun overlappingTextAndStickersRemainVisibleAndSelectTheirOwnItems() {
        val titles = listOf(
            TextOverlay(id = "first", text = "First", startMs = 0L, endMs = 5000L),
            TextOverlay(id = "second", text = "Second", startMs = 0L, endMs = 5000L),
        )
        val stickers = listOf(
            StickerOverlay(id = "star", name = "Star", symbolOrUri = "*", startMs = 0L, endMs = 5000L),
            StickerOverlay(id = "circle", name = "Circle", symbolOrUri = "o", startMs = 0L, endMs = 5000L),
        )
        val clip = MediaClip("owner", "Video", "file:///test.mp4", 10_000L, trimEndMs = 10_000L, textOverlays = titles)
        val selectedText = mutableListOf<Pair<String, String>>()
        val selectedStickers = mutableListOf<String?>()
        compose.setContent {
            VideoTimeline(clips = listOf(clip), textOverlays = titles, stickers = stickers,
                totalDurationMs = 10_000L, playheadMs = 0L, onScrub = {},
                onSelectTextOverlay = { owner, id -> selectedText += owner to id },
                onSelectSticker = { selectedStickers += it }, onOpenSpeed = {}, onOpenTrim = {},
                modifier = Modifier.requiredSize(840.dp, 400.dp))
        }
        fun bounds(description: String) = compose.onNodeWithContentDescription(description).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(bounds("Text overlay First").bottom <= bounds("Text overlay Second").top)
        assertTrue(bounds("Sticker Star").bottom <= bounds("Sticker Circle").top)
        compose.onNodeWithContentDescription("Text overlay First").performClick()
        compose.onNodeWithContentDescription("Text overlay Second").performClick()
        compose.onNodeWithContentDescription("Sticker Star").performClick()
        compose.onNodeWithContentDescription("Sticker Circle").performClick()
        compose.runOnIdle {
            assertEquals(listOf("owner" to "first", "owner" to "second"), selectedText)
            assertEquals(listOf("star", "circle"), selectedStickers)
        }
    }
}
