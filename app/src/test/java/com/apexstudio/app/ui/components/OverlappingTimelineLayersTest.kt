package com.apexstudio.app.ui.components

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
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

    @Test fun mainVideoPipOverlayAndMusicRemainSeparateEditableTimelineRows() {
        val main = MediaClip("main", "Main", "file:///main.mp4", 10_000L,
            trimEndMs = 10_000L, type = ClipType.VIDEO, trackIndex = 0)
        val pip = main.copy(id = "pip", name = "PiP", uri = "file:///pip.mp4",
            type = ClipType.OVERLAY, trackIndex = 1, timelineOffsetMs = 1_000L,
            trimStartMs = 0L, trimEndMs = 4_000L)
        val music = AudioTrack("music", "Music bed", "file:///music.wav",
            trimEndMs = 5_000L, sourceDurationMs = 5_000L, kind = AudioTrack.Kind.MUSIC)
        compose.setContent {
            VideoTimeline(
                clips = listOf(main, pip), audioTracks = listOf(music),
                totalDurationMs = 10_000L, playheadMs = 2_000L,
                onOpenSpeed = {}, onOpenTrim = {},
                modifier = Modifier.requiredSize(840.dp, 500.dp),
            )
        }

        val mainBounds = compose.onNodeWithText("Main video · V1", useUnmergedTree = true)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val pipBounds = compose.onNodeWithText("PiP overlay · V2", useUnmergedTree = true)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val musicBounds = compose.onNodeWithText("Music audio", useUnmergedTree = true)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("timeline-audio-track-music", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(mainBounds.bottom <= pipBounds.top)
        assertTrue(pipBounds.bottom <= musicBounds.top)
    }

    @Test fun selectedStickerRangeHandleCommitsTrimmedProjectRange() {
        val sticker = StickerOverlay(id = "trim-sticker", name = "Star", symbolOrUri = "*",
            startMs = 1_000L, endMs = 4_000L)
        val changes = mutableListOf<Triple<String, Long, Long>>()
        val onStickerRange: (String, Long, Long) -> Unit = { id, start, end ->
            changes.add(Triple(id, start, end))
        }
        compose.setContent {
            val configuration = LocalViewConfiguration.current
            CompositionLocalProvider(
                LocalDensity provides Density(1f),
                LocalViewConfiguration provides object : androidx.compose.ui.platform.ViewConfiguration by configuration {
                    override val touchSlop = 0f
                },
            ) {
                VideoTimeline(
                    clips = emptyList(),
                    stickers = listOf(sticker), selectedStickerId = sticker.id,
                    onSetStickerRange = onStickerRange,
                    totalDurationMs = 10_000L, playheadMs = 0L,
                    onOpenSpeed = {}, onOpenTrim = {},
                    modifier = Modifier.requiredSize(840.dp, 400.dp),
                )
            }
        }

        compose.onNodeWithTag("timeline-sticker-range-handle-start-${sticker.id}", useUnmergedTree = true)
            .performTouchInput {
                down(center)
                moveBy(Offset(36f, 0f))
                up()
            }

        compose.runOnIdle {
            val (id, start, end) = changes.last()
            assertEquals(sticker.id, id)
            assertTrue("drag must change the sticker in-point", start > sticker.startMs)
            assertEquals(sticker.endMs, end)
        }
    }
}
