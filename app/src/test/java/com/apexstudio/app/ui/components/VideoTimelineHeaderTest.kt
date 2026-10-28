package com.apexstudio.app.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoTimelineHeaderTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun addClipRemainsVisibleAndMainVideoAndMusicLaneRolesAreExplicit() {
        val addedTypes = mutableListOf<ClipType>()
        val scrollState = ScrollState(0)
        compose.setContent {
            VideoTimeline(
                clips = listOf(MediaClip(
                    id = "main", name = "Main", uri = "file:///main.mp4",
                    durationMs = 10_000L, trimEndMs = 10_000L, type = ClipType.VIDEO
                )),
                audioTracks = listOf(AudioTrack(id = "music-1", name = "Track music", uri = "file:///music")),
                totalDurationMs = 10_000L,
                playheadMs = 0L,
                onAddMedia = { addedTypes += it },
                onOpenSpeed = {},
                onOpenTrim = {},
                scrollState = scrollState,
                modifier = Modifier.fillMaxSize()
            )
        }

        compose.onNodeWithText("Timeline").assertIsDisplayed()
        compose.onNodeWithTag("timeline-duration").assertExists()
        compose.onNodeWithTag("timeline-header").assertExists()
        compose.onNodeWithContentDescription("Add media").assertIsDisplayed()
        compose.onNodeWithTag("timeline-header-add-media").assertIsDisplayed()
        compose.onNodeWithText("Main video · V1", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Music (A1)").assertIsDisplayed()
        compose.onNodeWithText("♫").assertDoesNotExist()
        compose.waitForIdle()
        compose.runOnIdle { scrollState.dispatchRawDelta(scrollState.maxValue.toFloat()) }
        compose.onNodeWithContentDescription("Add media").assertIsDisplayed().performClick()

        compose.runOnIdle {
            assertEquals(listOf(ClipType.VIDEO), addedTypes)
        }
    }
    @Test
    fun multiSelectActionsAreReachableAtCompactWidthAndInvokeCallbacks() {
        var duplicated = false
        compose.setContent {
            VideoTimeline(
                clips = listOf(MediaClip("main", "Main", "file:///main.mp4", 10_000L, trimEndMs = 10_000L)),
                totalDurationMs = 10_000L,
                playheadMs = 0L,
                multiSelectMode = true,
                selectedClipIds = setOf("main"),
                onDuplicateSelectedClips = { duplicated = true },
                onOpenSpeed = {}, onOpenTrim = {},
                scrollState = ScrollState(0), modifier = Modifier.fillMaxSize()
            )
        }
        compose.onNodeWithTag("timeline-multiselect-actions").performClick()
        compose.onNodeWithTag("timeline-action-duplicate").performClick()
        compose.runOnIdle { assertEquals(true, duplicated) }
    }

}
