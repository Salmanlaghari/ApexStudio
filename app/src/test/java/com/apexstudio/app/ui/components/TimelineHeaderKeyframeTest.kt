package com.apexstudio.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.apexstudio.app.domain.model.MediaClip
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h720dp-mdpi")
class TimelineHeaderKeyframeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun selectedClipTimelineKeepsRulerAndTracksWithoutAddOrOverflowControls() {
        compose.setContent {
            Box(Modifier.requiredSize(320.dp, 300.dp)) {
                VideoTimeline(
                    clips = listOf(
                        MediaClip(
                            id = "video",
                            name = "video",
                            uri = "file:///video.mp4",
                            durationMs = 10_000L,
                            trimEndMs = 10_000L,
                        )
                    ),
                    selectedClipId = "video",
                    totalDurationMs = 10_000L,
                    playheadMs = 1_000L,
                    onOpenSpeed = {},
                    onOpenTrim = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("timeline-header").assertDoesNotExist()
        compose.onNodeWithTag("timeline-header-keyframe").assertDoesNotExist()
        compose.onNodeWithTag("timeline-header-add-media").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add media").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add video media").assertDoesNotExist()
        compose.onNodeWithTag("timeline-add-media").assertDoesNotExist()
        compose.onNodeWithTag("timeline-add-at-end").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add Clip").assertDoesNotExist()
        compose.onNodeWithTag("timeline-overflow").assertDoesNotExist()
        compose.onNodeWithTag("timeline-ruler", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Main video track").assertIsDisplayed()
    }
}
