package com.apexstudio.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.Keyframe
import com.apexstudio.app.domain.model.KeyframeTrack
import com.apexstudio.app.domain.model.MediaClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h720dp-mdpi")
class TimelineHeaderKeyframeTest {
    @get:Rule val compose = createComposeRule()

    private fun video(
        id: String = "video",
        type: ClipType = ClipType.VIDEO,
        trackIndex: Int = 0,
        timelineOffsetMs: Long = 0L,
        keyframes: KeyframeTrack = KeyframeTrack(),
    ) = MediaClip(
        id = id,
        name = id,
        uri = "file:///$id.mp4",
        durationMs = 10_000L,
        trimEndMs = 10_000L,
        type = type,
        trackIndex = trackIndex,
        timelineOffsetMs = timelineOffsetMs,
        keyframes = keyframes,
    )

    private fun setTimeline(
        clips: List<MediaClip> = listOf(video()),
        selectedClipId: String? = "video",
        selectedTextOverlayId: String? = null,
        selectedStickerId: String? = null,
        selectedAudioTrackId: String? = null,
        widthDp: Int = 320,
        onToggle: (String) -> Unit = {},
    ) {
        compose.setContent {
            Box(Modifier.requiredSize(widthDp.dp, 300.dp)) {
                VideoTimeline(
                    clips = clips,
                    selectedClipId = selectedClipId,
                    selectedTextOverlayId = selectedTextOverlayId,
                    selectedStickerId = selectedStickerId,
                    selectedAudioTrackId = selectedAudioTrackId,
                    totalDurationMs = 10_000L,
                    playheadMs = 1_000L,
                    onToggleKeyframeAtPlayhead = onToggle,
                    onOpenSpeed = {},
                    onOpenTrim = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        compose.waitForIdle()
    }

    private fun assertHeaderActionVisible() = compose.onNodeWithTag("timeline-header-keyframe")
        .assertIsDisplayed()

    private fun assertHeaderActionHidden() = compose.onNodeWithTag("timeline-header-keyframe")
        .assertDoesNotExist()

    @Test fun selectedVideoShowsAction() { setTimeline(); assertHeaderActionVisible() }
    @Test fun noSelectionHidesAction() { setTimeline(selectedClipId = null); assertHeaderActionHidden() }
    @Test fun audioSelectionHidesUnderlyingVideoAction() { setTimeline(selectedAudioTrackId = "audio"); assertHeaderActionHidden() }
    @Test fun textSelectionHidesUnderlyingVideoAction() { setTimeline(selectedTextOverlayId = "title"); assertHeaderActionHidden() }
    @Test fun stickerSelectionHidesUnderlyingVideoAction() { setTimeline(selectedStickerId = "sticker"); assertHeaderActionHidden() }
    @Test fun imageSelectionHidesAction() { setTimeline(clips = listOf(video(type = ClipType.IMAGE))); assertHeaderActionHidden() }
    @Test fun activeOverlayShowsAction() { setTimeline(clips = listOf(video(type = ClipType.OVERLAY, trackIndex = 1))); assertHeaderActionVisible() }
    @Test fun inactiveOverlayHidesAction() { setTimeline(clips = listOf(video(type = ClipType.OVERLAY, trackIndex = 1, timelineOffsetMs = 2_000L))); assertHeaderActionHidden() }

    @Test
    fun actionUsesSelectedClipIdAndItsAccessibleLabelTracksAddThenRemove() {
        val callbackIds = mutableListOf<String>()
        compose.setContent {
            var clips by remember { mutableStateOf(listOf(video(id = "selected"))) }
            VideoTimeline(
                clips = clips,
                selectedClipId = "selected",
                totalDurationMs = 10_000L,
                playheadMs = 1_000L,
                onToggleKeyframeAtPlayhead = { clipId ->
                    callbackIds += clipId
                    clips = clips.map { clip ->
                        if (clip.id != clipId) clip else {
                            val existing = clip.keyframes.keyframes.firstOrNull {
                                kotlin.math.abs(it.timeMs - 1_000L) <= 150L
                            }
                            clip.copy(
                                keyframes = KeyframeTrack(
                                    if (existing == null) clip.keyframes.keyframes + Keyframe(id = "added", timeMs = 1_000L)
                                    else clip.keyframes.keyframes.filterNot { it.id == existing.id },
                                ),
                            )
                        }
                    }
                },
                onOpenSpeed = {},
                onOpenTrim = {},
            )
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Add keyframe")
            .assertIsDisplayed()
            .performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Remove keyframe")
            .assertIsDisplayed()
            .performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Add keyframe").assertIsDisplayed()
        assertEquals(listOf("selected", "selected"), callbackIds)
    }

    @Test
    fun headerActionHasA40DpTargetAndAllActionsFitAt320Dp() {
        assertHeaderFitsAtWidth(320)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w375dp-h720dp-mdpi")
    fun headerActionHasA40DpTargetAndAllActionsFitAt375Dp() {
        assertHeaderFitsAtWidth(375)
    }

    private fun assertHeaderFitsAtWidth(width: Int) {
        setTimeline(widthDp = width)
        val header = compose.onNodeWithTag("timeline-header").fetchSemanticsNode().boundsInRoot
        val keyframe = compose.onNodeWithTag("timeline-header-keyframe").fetchSemanticsNode().boundsInRoot
        assertTrue("Keyframe target should be at least 40dp square: $keyframe", keyframe.width >= 40f && keyframe.height >= 40f)
        assertTrue("Keyframe action must remain inside the $width dp header: header=$header action=$keyframe", keyframe.left >= header.left && keyframe.right <= header.right)
        compose.onNodeWithTag("timeline-reveal-playhead").assertIsDisplayed()
        compose.onNodeWithTag("timeline-multiselect").assertIsDisplayed()
        compose.onNodeWithTag("timeline-header-add-media").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add clip").assertIsDisplayed()
    }
}
