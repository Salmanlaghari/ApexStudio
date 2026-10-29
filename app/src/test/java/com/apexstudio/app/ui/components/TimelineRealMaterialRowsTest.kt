package com.apexstudio.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.presentation.state.EditorState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Regression coverage for timeline height and rows backed by real project content. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h720dp-mdpi")
class TimelineRealMaterialRowsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun audioAndSfxClipsAloneKeepTheEmptyCompactHeightAndDoNotMakeVisualLayers() {
        val state = stateWithClips(
            clip("audio", ClipType.AUDIO, trackIndex = 0),
            clip("sfx", ClipType.SFX, trackIndex = 4),
        )

        assertEquals(emptyMap<Int, List<MediaClip>>(), TimelineLayerContentIndex.from(state.project!!.clips).videoByLayer)
        assertEquals(28.dp, compactTimelineHeight(state))
    }

    @Test
    fun compactHeightCountsSparseVideoAndImageLayersByClampedContentIndex() {
        val clips = listOf(
            clip("main-image", ClipType.IMAGE, trackIndex = 0),
            clip("video-v4", ClipType.VIDEO, trackIndex = 3),
            clip("image-v4", ClipType.IMAGE, trackIndex = 3),
            clip("clamped-v10", ClipType.VIDEO, trackIndex = 17),
        )
        val index = TimelineLayerContentIndex.from(clips)

        assertEquals(listOf(0, 3, 9), index.videoByLayer.keys.toList())
        // V1 image + two distinct populated supporting layers (V4 and clamped V10).
        assertEquals(240.dp, compactTimelineHeight(stateWithClips(*clips.toTypedArray())))
    }

    @Test
    fun sparseNonzeroVideoAndImageLayersCountWithoutInventingAMainLane() {
        val clips = listOf(
            clip("video-v4", ClipType.VIDEO, trackIndex = 3),
            clip("image-v4", ClipType.IMAGE, trackIndex = 3),
            clip("clamped-v10", ClipType.IMAGE, trackIndex = 12),
        )
        val index = TimelineLayerContentIndex.from(clips)

        assertEquals(listOf(3, 9), index.videoByLayer.keys.toList())
        assertEquals(178.dp, compactTimelineHeight(stateWithClips(*clips.toTypedArray())))
    }

    @Test
    fun sparseOverlayAtTrackThreeRendersAsV4WithoutMainV2OrV3() {
        composeRule.setContent {
            timeline(clips = listOf(clip("overlay-v4", ClipType.OVERLAY, trackIndex = 3)))
        }

        composeRule.onNodeWithContentDescription("Main video track").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("V2 overlay").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("V3 overlay").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("V4 overlay").assertExists()
    }

    @Test
    fun realSfxAudioTrackRendersItsRowWithoutAMainVideoLane() {
        val sfx = AudioTrack(
            id = "sfx-track",
            name = "Sound effect",
            uri = "file:///sound.wav",
            trimEndMs = 10_000L,
            kind = AudioTrack.Kind.SFX,
        )
        composeRule.setContent { timeline(audioTracks = listOf(sfx)) }

        composeRule.onNodeWithContentDescription("Main video track").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Voice and sound effects (A2)").assertExists()
        composeRule.onNodeWithTag("timeline-audio-track-sfx-track", useUnmergedTree = true).assertExists()
    }

    @Test
    fun legacyTextOwnedByNonvisualClipStillGetsATextRowWithoutAMainLane() {
        val title = TextOverlay(id = "legacy-title", text = "Legacy caption")
        val legacyOwner = clip("legacy-audio", ClipType.AUDIO).copy(textOverlays = listOf(title))
        composeRule.setContent {
            timeline(clips = listOf(legacyOwner), textOverlays = listOf(title))
        }

        composeRule.onNodeWithContentDescription("Main video track").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Text").assertExists()
    }

    @androidx.compose.runtime.Composable
    private fun timeline(
        clips: List<MediaClip> = emptyList(),
        audioTracks: List<AudioTrack> = emptyList(),
        textOverlays: List<TextOverlay> = emptyList(),
    ) {
        Box(Modifier.requiredSize(360.dp, 500.dp)) {
            VideoTimeline(
                clips = clips,
                audioTracks = audioTracks,
                textOverlays = textOverlays,
                totalDurationMs = 10_000L,
                playheadMs = 0L,
                onOpenSpeed = {},
                onOpenTrim = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    private fun stateWithClips(vararg clips: MediaClip) = EditorState(
        project = Project(id = "project", name = "Test", durationMs = 10_000L, clips = clips.toList()),
    )

    private fun clip(id: String, type: ClipType, trackIndex: Int = 0) = MediaClip(
        id = id,
        name = id,
        uri = "file:///test/$id.mp4",
        durationMs = 10_000L,
        trimEndMs = 10_000L,
        type = type,
        trackIndex = trackIndex,
    )
}
