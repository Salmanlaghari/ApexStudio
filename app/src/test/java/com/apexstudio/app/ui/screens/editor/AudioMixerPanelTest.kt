package com.apexstudio.app.ui.screens.editor

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.presentation.state.AudioStudioState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w375dp-h830dp")
class AudioMixerPanelTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun trackKindLabelDoesNotDependOnFaderVolume() {
        assertEquals("Music", audioTrackKindLabel(AudioTrack.Kind.MUSIC))
        assertEquals("Voiceover / SFX", audioTrackKindLabel(AudioTrack.Kind.SFX))
        assertEquals("Original video", audioTrackKindLabel(AudioTrack.Kind.ORIGINAL_VIDEO))
    }

    @Test
    fun sourceTrimBoundsClampMalformedRangesAndRejectTooShortSources() {
        assertEquals(AudioSourceTrimBounds(0L, 1_000L), normalizedAudioSourceTrimBounds(1_000L, -50L, 9_000L))
        assertEquals(AudioSourceTrimBounds(800L, 900L), normalizedAudioSourceTrimBounds(1_000L, 800L, 200L))
        assertNull(normalizedAudioSourceTrimBounds(99L, 0L, 99L))
        assertNull(normalizedAudioSourceTrimBounds(1_000L, 0L, 1_000L, minimumDurationMs = 1_001L))
    }

    @Test
    fun splitIsValidOnlyInsideRetainedPlacedIntervalWithMinimumSegments() {
        val track = AudioTrack(
            id = "music",
            name = "Music",
            uri = "file://music",
            trimStartMs = 1_000L,
            trimEndMs = 5_000L,
            sourceDurationMs = 8_000L,
            startTimeMs = 2_000L
        )
        assertTrue(canSplitAudioTrackAtPlayhead(track, 2_100L))
        assertTrue(canSplitAudioTrackAtPlayhead(track, 5_900L))
        assertFalse(canSplitAudioTrackAtPlayhead(track, 2_099L))
        assertFalse(canSplitAudioTrackAtPlayhead(track, 6_001L))
        assertFalse(canSplitAudioTrackAtPlayhead(track.copy(trimEndMs = 8_001L), 4_000L))
    }

    @Test
    fun trimResetCallsSourceTrimCallbackWithFullSourceEvenForMalformedSavedRange() {
        val track = AudioTrack(
            id = "music",
            name = "Music",
            uri = "file://music",
            trimStartMs = 9_000L,
            trimEndMs = 20_000L,
            sourceDurationMs = 1_000L
        )
        var committed: Triple<String, Long, Long>? = null
        compose.setContent {
            MixerForTest(track = track, playheadMs = 0L,
                onTrim = { id, start, end -> committed = Triple(id, start, end) }, onSplit = {})
        }

        compose.onNodeWithTag("audio-mixer-panel-cards").performScrollToKey("trim-music")
        compose.onNodeWithTag("audio-track-trim-reset-music").performClick()

        assertEquals(Triple("music", 0L, 1_000L), committed)
    }

    @Test
    fun splitButtonInvokesCallbackOnlyWhenPlayheadIsInsidePlacedRetainedAudio() {
        val track = AudioTrack(
            id = "music",
            name = "Music",
            uri = "file://music",
            trimStartMs = 1_000L,
            trimEndMs = 5_000L,
            sourceDurationMs = 8_000L,
            startTimeMs = 2_000L
        )
        var splitTrackId: String? = null
        val playhead = androidx.compose.runtime.mutableLongStateOf(3_000L)
        compose.setContent {
            MixerForTest(track = track, playheadMs = playhead.longValue, onTrim = { _, _, _ -> },
                onSplit = { splitTrackId = it })
        }
        compose.onNodeWithTag("audio-mixer-panel-cards").performScrollToKey("trim-music")
        val splitButton = compose.onNodeWithTag("audio-track-split-music")
        splitButton.assertIsEnabled().performClick()
        assertEquals("music", splitTrackId)

        compose.runOnIdle { playhead.longValue = 1_999L }
        compose.onNodeWithTag("audio-track-split-music").assertIsNotEnabled()
    }

    @Test
    fun audioIntelligenceButtonsInvokeTheirCallbacks() {
        val track = AudioTrack(
            id = "music",
            name = "Music",
            uri = "file://music",
            kind = AudioTrack.Kind.MUSIC,
            trimEndMs = 8_000L,
            sourceDurationMs = 8_000L
        )
        var normalized = false
        var ducked: Pair<String, String>? = null
        var fitted = false
        var silenceRemoved = false
        compose.setContent {
            AudioMixerPanel(
                state = AudioStudioState(tracks = listOf(track, track.copy(id = "voice", kind = AudioTrack.Kind.SFX))),
                muteOriginalVideo = false,
                onMuteOriginal = {}, onAddTrack = { _, _, _ -> }, onRemoveTrack = {},
                onVolume = { _, _ -> }, onMute = {}, onSolo = {}, onTrim = { _, _, _ -> },
                onFadeIn = { _, _ -> }, onFadeOut = { _, _ -> }, onClose = {},
                onNormalizeTrack = { normalized = it == "music" },
                onAutoDuckMusic = { music, voice -> ducked = music to voice },
                onFitMusicToProject = { fitted = it == "music" },
                onRemoveSilence = { silenceRemoved = it == "music" }
            )
        }
        compose.onNodeWithTag("audio-mixer-panel-cards").performScrollToKey("music")
        compose.onNodeWithTag("audio-track-normalize-music").performScrollTo().performClick()
        compose.onNodeWithTag("audio-track-duck-music").performScrollTo().performClick()
        compose.onNodeWithTag("audio-track-fit-music").performScrollTo().performClick()
        compose.onNodeWithTag("audio-track-remove-silence-music").performScrollTo().performClick()
        assertTrue(normalized)
        assertEquals("music" to "voice", ducked)
        assertTrue(fitted)
        assertTrue(silenceRemoved)
    }
}

@androidx.compose.runtime.Composable
private fun MixerForTest(
    track: AudioTrack,
    playheadMs: Long,
    onTrim: (String, Long, Long) -> Unit,
    onSplit: (String) -> Unit
) {
    AudioMixerPanel(
        state = AudioStudioState(tracks = listOf(track)),
        muteOriginalVideo = false,
        onMuteOriginal = {},
        onAddTrack = { _, _, _ -> },
        onRemoveTrack = {},
        onVolume = { _, _ -> },
        onMute = {},
        onSolo = {},
        onTrim = onTrim,
        onSplit = onSplit,
        onFadeIn = { _, _ -> },
        onFadeOut = { _, _ -> },
        onClose = {},
        playheadMs = playheadMs
    )
}
