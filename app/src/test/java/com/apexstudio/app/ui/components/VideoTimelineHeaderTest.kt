package com.apexstudio.app.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
    fun addClipRemainsVisibleAndV1MusicNoteAndA1LabelsAreAbsent() {
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

        compose.onNodeWithText("TIMELINE").assertDoesNotExist()
        compose.onNodeWithText("Add Clip").assertDoesNotExist()
        compose.waitForIdle()
        compose.runOnIdle { scrollState.dispatchRawDelta(scrollState.maxValue.toFloat()) }
        compose.onNodeWithTag("timeline-add-media", useUnmergedTree = true).assertIsDisplayed().performClick()
        compose.onNodeWithText("V1").assertDoesNotExist()
        compose.onNodeWithText("A1").assertDoesNotExist()
        compose.onNodeWithText("♫").assertDoesNotExist()

        compose.runOnIdle {
            assertEquals(listOf(ClipType.VIDEO), addedTypes)
        }
    }
}
