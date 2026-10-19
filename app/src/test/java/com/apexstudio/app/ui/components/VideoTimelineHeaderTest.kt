package com.apexstudio.app.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.domain.model.ClipType
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
        compose.setContent {
            VideoTimeline(
                clips = emptyList(),
                audioTracks = listOf(AudioTrack(id = "music-1", name = "Track music", uri = "file:///music")),
                totalDurationMs = 10_000L,
                playheadMs = 0L,
                onAddMedia = { addedTypes += it },
                onOpenSpeed = {},
                onOpenTrim = {},
                modifier = Modifier.fillMaxSize()
            )
        }

        compose.onNodeWithText("Add Clip").assertIsDisplayed().performClick()
        compose.onNodeWithText("V1").assertDoesNotExist()
        compose.onNodeWithText("A1").assertDoesNotExist()
        compose.onNodeWithText("♫").assertDoesNotExist()

        compose.runOnIdle {
            assertEquals(listOf(ClipType.VIDEO), addedTypes)
        }
    }
}
