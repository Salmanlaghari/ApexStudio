package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.state.EditorState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h720dp")
class ApprovedEditorTransportTest {
    @get:Rule val compose = createComposeRule()
    private val video = MediaClip(id = "video", name = "Video", uri = "file:///video.mp4", durationMs = 5000L, trimEndMs = 5000L)

    @Test fun keyframeControlTargetsOnlySelectedVideoNotAnUnderlyingSelection() {
        val state = EditorState(project = Project(id = "p", name = "Project", durationMs = 5000L, clips = listOf(video)), selectedClipId = video.id)
        assertEquals(video, selectedKeyframeVideo(state))
        assertNull(selectedKeyframeVideo(state.copy(selectedClipId = null)))
        assertNull(selectedKeyframeVideo(state.copy(selectedTextOverlayId = "text")))
        assertNull(selectedKeyframeVideo(state.copy(selectedStickerId = "sticker")))
        assertNull(selectedKeyframeVideo(state.copy(selectedAudioTrackId = "audio")))
        assertNull(selectedKeyframeVideo(state.copy(project = state.project!!.copy(clips = listOf(video.copy(type = ClipType.IMAGE))))))
    }

    @Test fun previewFollowsProjectClockAcrossClipBoundariesWithoutChangingSelection() {
        val second = video.copy(id = "second", trimStartMs = 1000L, trimEndMs = 5000L, speedMultiplier = 2f)
        val clips = listOf(video, second, video.copy(id = "overlay", type = ClipType.OVERLAY, trackIndex = 1))
        assertEquals(video, timelinePreviewClip(clips, 4999L))
        assertEquals(second, timelinePreviewClip(clips, 5000L))
        assertEquals(second, timelinePreviewClip(clips, 6000L))
        assertEquals(second, timelinePreviewClip(clips, 7000L))
        assertEquals(3000L, com.apexstudio.app.domain.model.ClipTimeMapping.sourceAtProjectTime(clips, second, 6000L))
        assertEquals(video, timelinePreviewClip(clips, 0L))
        assertNull(timelinePreviewClip(listOf(clips.last()), 0L))
    }

    @Test fun boundaryNavigationUsesActualTrimmedSpeedAdjustedDurations() {
        val second = video.copy(id = "second", speedMultiplier = 2f)
        val clips = listOf(video, second, video.copy(id = "overlay", type = ClipType.OVERLAY, trackIndex = 1))
        assertEquals(5000L, editorClipBoundary(clips, 0L, 7500L, true))
        assertEquals(7500L, editorClipBoundary(clips, 5000L, 7500L, true))
        assertEquals(5000L, editorClipBoundary(clips, 7000L, 7500L, false))
        assertEquals(0L, editorClipBoundary(clips, 0L, 7500L, false))
        assertEquals(7500L, editorClipBoundary(clips, 7500L, 10000L, true))
    }

    @Test fun approvedTransportAndAboveTimelineKeyframeUseRealCallbacksAt320dp() {
        val calls = mutableListOf<String>()
        compose.setContent {
            Column(Modifier.width(320.dp)) {
                PlaybackControlBar(currentTimeMs = 12000L, durationMs = 45000L,
                    onPrevious = { calls += "previous" }, onNext = { calls += "next" },
                    showInlineKeyframe = false)
                SelectedVideoKeyframeControl(false, { calls += "keyframe" }, { calls += "animation" })
            }
        }
        compose.onNodeWithText("00:12 / 00:45").assertIsDisplayed()
        listOf("Previous clip boundary", "Next clip boundary", "Add keyframe", "Edit video keyframe animation").forEach {
            compose.onNodeWithContentDescription(it).assertIsDisplayed().performClick()
        }
        compose.runOnIdle { assertEquals(listOf("previous", "next", "keyframe", "animation"), calls) }
    }

}
