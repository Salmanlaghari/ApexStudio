package com.apexstudio.app.ui.screens.editor

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.apexstudio.app.domain.model.*
import com.apexstudio.app.presentation.state.EditorState
import com.apexstudio.app.presentation.viewmodel.*
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorReviewPlaybackRegressionTest {
    private val first = MediaClip("first", "First", "file:///first.mp4", 6_000L,
        trimStartMs = 1_000L, trimEndMs = 6_000L)
    private val second = first.copy(id = "second", uri = "file:///second.mp4", speedMultiplier = 2f)
    private val clips = listOf(first, second)

    /** A Player API recorder; these tests validate binding order, not decoder behavior. */
    private class RecordingPlayer {
        var item: MediaItem? = null
        var rate = PlaybackParameters(1.7f)
        var playbackState = Player.STATE_ENDED
        val calls = mutableListOf<String>()
        var seek: Long? = null
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
            when (method.name) {
                "setMediaItem" -> { item = args!![0] as MediaItem; calls += "item"; null }
                "prepare" -> { calls += "prepare"; null }
                "setPlaybackParameters" -> { rate = args!![0] as PlaybackParameters; calls += "rate"; null }
                "seekTo" -> { seek = args!![0] as Long; calls += "seek"; null }
                "setPlayWhenReady" -> { calls += "ready:${args!![0]}"; null }
                "getCurrentMediaItem" -> item
                "getPlaybackState" -> playbackState
                "play" -> { calls += "play"; null }
                "pause" -> { calls += "pause"; null }
                else -> error("Unexpected Player call ${method.name}")
            }
        } as Player
    }

    @Test fun itemBindingResetsOldCurveRateEvenWhenNextConstantMultiplierMatches() {
        val recorder = RecordingPlayer()
        val next = second.copy(speedMultiplier = 1f)
        bindEditorPreviewItem(recorder.player, MediaItem.Builder().setUri(next.uri).setMediaId(next.id).build(),
            next, listOf(first, next), 6_000L, true)
        assertEquals(1f, recorder.rate.speed, 0f)
        assertEquals(2_000L, recorder.seek)
        assertEquals(listOf("item", "prepare", "rate", "seek", "ready:true"), recorder.calls)
        val bad = next.copy(speedMultiplier = Float.NaN)
        bindEditorPreviewItem(recorder.player, MediaItem.fromUri(bad.uri), bad, listOf(bad), 0L, false)
        assertEquals(SpeedCurveMath.sanitizeConstantSpeed(Float.NaN), recorder.rate.speed, 0f)
        assertEquals("ready:false", recorder.calls.last())
    }

    @Test fun replayWaitsForFirstItemBindingInsteadOfSeekingOrPlayingLastItem() {
        val recorder = RecordingPlayer()
        recorder.item = MediaItem.Builder().setMediaId(second.id).build()
        resumeEditorPreview(recorder.player, clips, 0L)
        assertEquals(listOf("pause"), recorder.calls)
        assertNull(recorder.seek)
        recorder.calls.clear()
        recorder.item = MediaItem.Builder().setMediaId(first.id).build()
        resumeEditorPreview(recorder.player, clips, 0L)
        assertEquals(1_000L, recorder.seek)
        assertEquals(listOf("seek", "play"), recorder.calls)
    }

    @Test fun endedSourceBeforeProjectEndSeeksMappedPositionNotTrimHead() {
        val recorder = RecordingPlayer()
        recorder.item = MediaItem.Builder().setMediaId(second.id).build()
        resumeEditorPreview(recorder.player, clips, 6_000L)
        assertEquals(3_000L, recorder.seek)
        assertEquals(listOf("seek", "play"), recorder.calls)
    }

    @Test fun photoAtProjectStartDoesNotReplayAnOldVideo() {
        val recorder = RecordingPlayer()
        recorder.item = MediaItem.Builder().setMediaId(second.id).build()
        resumeEditorPreview(recorder.player, listOf(first.copy(type = ClipType.IMAGE), second), 0L)
        assertEquals(listOf("pause"), recorder.calls)
    }

    @Test fun playAtOrBeyondEndResetsAllProjectClocksWithoutChangingSelectionOrLayers() {
        val audio = AudioTrack("music", "Music", first.uri, trimEndMs = 9_000L, sourceDurationMs = 9_000L)
        val project = Project("p", "Project", 9_000L, clips = clips, audioTracks = listOf(audio))
        for (position in listOf(9_000L, 9_100L)) {
            val vm = EditorViewModel()
            vm._state.value = EditorState(project = project, selectedClipId = second.id,
                durationMs = 9_000L, playerPositionMs = 8_900L, currentTimeMs = 8_900L)
            vm._playerPosition.value = position
            vm.setPlaying(true)
            assertTrue(vm.state.value.isPlaying)
            assertEquals(0L, vm.playerPositionFlow.value)
            assertEquals(0L, vm.state.value.playerPositionMs)
            assertEquals(0L, vm.state.value.currentTimeMs)
            assertEquals(first.id, timelinePreviewClip(clips, vm.playerPositionFlow.value)?.id)
            assertEquals(second.id, vm.state.value.selectedClipId)
            assertEquals(project, vm.state.value.project)
        }
    }

    @Test fun playBeforeEndPreservesLivePosition() {
        val vm = EditorViewModel()
        vm._state.value = EditorState(durationMs = 9_000L, playerPositionMs = 5_000L)
        vm._playerPosition.value = 5_050L
        vm.setPlaying(true)
        assertEquals(5_050L, vm.playerPositionFlow.value)
    }

    @Test fun keyframeControlsRequireActiveSelectedVideoAndRespectBoundariesAndHiddenTracks() {
        val state = EditorState(project = Project("p", "Project", 9_000L, clips = clips),
            selectedClipId = first.id, playerPositionMs = 4_999L)
        assertEquals(first, selectedKeyframeVideo(state))
        assertNull(selectedKeyframeVideo(state.copy(playerPositionMs = 5_000L)))
        assertEquals(second, selectedKeyframeVideo(state.copy(selectedClipId = second.id, playerPositionMs = 5_000L)))
        assertNull(selectedKeyframeVideo(state.copy(selectedClipId = second.id, playerPositionMs = 7_500L)))
        assertNull(selectedKeyframeVideo(state.copy(hiddenVideoLayers = setOf(0))))
        val overlay = first.copy(id = "pip", type = ClipType.OVERLAY, trackIndex = 1,
            timelineOffsetMs = 2_000L, trimStartMs = 0L, trimEndMs = 1_000L)
        val overlayState = state.copy(project = state.project!!.copy(clips = clips + overlay), selectedClipId = overlay.id)
        assertNull(selectedKeyframeVideo(overlayState.copy(playerPositionMs = 1_999L)))
        assertEquals(overlay, selectedKeyframeVideo(overlayState.copy(playerPositionMs = 2_000L)))
        assertNull(selectedKeyframeVideo(overlayState.copy(playerPositionMs = 3_000L)))
        assertNull(selectedKeyframeVideo(overlayState.copy(playerPositionMs = 2_000L, hiddenVideoLayers = setOf(1))))
        assertNull(selectedKeyframeVideo(overlayState.copy(playerPositionMs = 2_000L, selectedTextOverlayId = "text")))
    }
}
