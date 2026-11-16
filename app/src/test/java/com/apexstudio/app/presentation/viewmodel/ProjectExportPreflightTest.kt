package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.*
import com.apexstudio.app.data.filter.OneTapLooks
import com.apexstudio.app.presentation.state.AudioStudioState
import com.apexstudio.app.presentation.state.EditorState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProjectExportPreflightTest {
    private val video = MediaClip("v", "Video", "file:///video.mp4", 5000, trimEndMs = 5000)
    private val state = EditorState(project = Project("p", "Project", 5000, clips = listOf(video)))

    @Test fun `plain project passes global visual preflight`() {
        assertNull(projectVisualScopeUnsupportedReason(state))
    }

    @Test fun `missing project fails clearly`() {
        assertNotNull(projectVisualScopeUnsupportedReason(EditorState()))
    }

    @Test fun `legacy audio and sound effect clips are not filtered without a gate`() {
        for ((type, expected) in listOf(
            ClipType.AUDIO to "legacy audio clip",
            ClipType.SFX to "legacy sound-effect clip"
        )) {
            val legacy = video.copy(id = type.name, name = type.name, type = type)
            val edited = state.copy(project = state.project!!.copy(clips = listOf(video, legacy)))
            assertTrue(projectVisualScopeUnsupportedReason(edited)!!.contains(expected))
        }
    }

    @Test fun `global LUT FX and standard grading are supported`() {
        for (edited in listOf(
            state.copy(activeFilterId = "lut"),
            state.copy(activeFxId = "glitch"),
            state.copy(adjustments = VideoAdjustments(brightness = 0.2f))
        )) assertNull(projectVisualScopeUnsupportedReason(edited))
    }

    @Test fun `persisted Viral HDR is accepted without applying its mirrored LUT twice`() {
        val viral = OneTapLooks.VIRAL_HDR.applyWithIntensity(1f)
        val edited = state.copy(
            project = state.project!!.copy(clips = listOf(video.copy(gpuFilterConfig = viral))),
            activeGpuFilterConfig = viral,
            activeFilterId = "hdr_punch",
            filterIntensity = 0.85f
        )

        assertTrue(OneTapLooks.isValidRenderConfig(viral))
        assertEquals(viral, edited.project!!.clips.single().gpuFilterConfig)
        assertNull(wholeProjectExportUnsupportedReason(
            edited,
            AudioStudioState(),
            ExportSettings(),
            filterPreset = null
        ))
    }

    @Test fun `unsupported AR GPU grading and crop remain fail closed`() {
        for (edited in listOf(
            state.copy(activeArFilterId = "ar"),
            state.copy(activeGpuFilterConfig = state.activeGpuFilterConfig.copy(brightness = 0.1f)),
            state.copy(cropRect = state.cropRect.copy(left = 0.1f))
        )) assertNotNull(projectVisualScopeUnsupportedReason(edited))
    }

    @Test fun `project stickers reach whole project export while invalid timing still fails closed`() {
        val sticker = StickerOverlay(id = "global", startMs = 200L, endMs = 900L)
        val edited = state.copy(project = state.project!!.copy(stickers = listOf(sticker)))
        assertNull(projectVisualScopeUnsupportedReason(edited))
        assertEquals(
            listOf(sticker),
            buildProjectExportConfig(edited, AudioStudioState(), ExportSettings()).stickers
        )
        assertNull(wholeProjectExportUnsupportedReason(edited, AudioStudioState(), ExportSettings()))

        val invalid = edited.copy(project = edited.project!!.copy(stickers = listOf(sticker.copy(startMs = -1L))))
        assertTrue(wholeProjectExportUnsupportedReason(invalid, AudioStudioState(), ExportSettings())!!
            .contains("project sticker with an invalid time range"))
    }

    @Test fun `supported persisted junction reaches whole project composition export`() {
        val next = video.copy(id = "next", name = "Next")
        val saved = state.copy(project = state.project!!.copy(
            clips = listOf(video, next),
            transitions = listOf(ClipTransition(
                fromClipId = "v", toClipId = "next", type = "cross_dissolve", durationMs = 500L
            )),
            lastTransitionType = null
        ))

        assertNull(projectVisualScopeUnsupportedReason(saved))
        assertNull(wholeProjectExportUnsupportedReason(saved, AudioStudioState(), ExportSettings()))
    }

    @Test fun `visible overlay blocks composition but hidden overlay stays hidden`() {
        val overlay = video.copy(id = "pip", type = ClipType.OVERLAY, trackIndex = 1, overlayVolume = 0.75f)
        val withOverlay = state.copy(project = state.project!!.copy(clips = listOf(video, overlay)))
        assertNotNull(projectVisualScopeUnsupportedReason(withOverlay))
        assertNull(projectVisualScopeUnsupportedReason(withOverlay.copy(hiddenVideoLayers = setOf(1))))

        // Selected-video export maps PiP audio, while whole-project export refuses
        // the unsupported visible composition before its main-track filter can drop it.
        assertEquals(listOf(video), projectMainExportClips(withOverlay))
        assertEquals(listOf(overlay.uri), buildOverlayAudioExportSources(withOverlay.project!!.clips).map { it.uri })
        assertTrue(wholeProjectExportUnsupportedReason(
            withOverlay, AudioStudioState(), ExportSettings()
        )!!.contains("cannot render visible PiP"))
        assertNull(wholeProjectExportUnsupportedReason(
            withOverlay.copy(hiddenVideoLayers = setOf(1)), AudioStudioState(), ExportSettings()
        ))
    }

    @Test fun `whole-project export fails closed for multiple typed audio clips in one lane`() {
        val legacyProjection = AudioTrack(
            id = "music-1", name = "Music", uri = "file:///music-1.m4a",
            trimEndMs = 1_000L, sourceDurationMs = 1_000L
        )
        val first = com.apexstudio.app.domain.model.timeline.AudioClip(
            id = "music-1", uri = android.net.Uri.parse("file:///music-1.m4a"),
            inMs = 0L, outMs = 1_000L, trimStartMs = 0L, trimEndMs = 1_000L, sourceDurationMs = 1_000L
        )
        val second = com.apexstudio.app.domain.model.timeline.AudioClip(
            id = "music-2", uri = android.net.Uri.parse("file:///music-2.m4a"),
            inMs = 1_500L, outMs = 2_500L, trimStartMs = 250L, trimEndMs = 1_250L, sourceDurationMs = 2_000L
        )
        val lane = com.apexstudio.app.domain.model.timeline.AudioTrack(
            id = "shared-audio-music-1", lane = 10, clips = listOf(first, second)
        )
        val project = state.project!!.copy(
            audioTracks = listOf(legacyProjection),
            timelineAuthorityVersion = Project.CURRENT_TIMELINE_AUTHORITY_VERSION,
            timeline = com.apexstudio.app.domain.model.timeline.Timeline(tracks = listOf(lane))
        )
        val withMultiClipLane = state.copy(project = project)

        // Demonstrate the lossy legacy export projection: only the first URI is available.
        assertEquals(listOf("music-1"), buildProjectExportConfig(
            withMultiClipLane, AudioStudioState(), ExportSettings(), includeSharedVideoClips = false
        ).musicMixTracks.map { it.id })
        val reason = wholeProjectExportUnsupportedReason(
            withMultiClipLane, AudioStudioState(), ExportSettings()
        )
        assertTrue(reason, reason!!.contains("multiple clips in audio lane"))

        val singleClipLane = lane.copy(clips = listOf(first))
        val supportedSingleClip = withMultiClipLane.copy(project = project.copy(
            timeline = com.apexstudio.app.domain.model.timeline.Timeline(tracks = listOf(singleClipLane))
        ))
        assertNull(wholeProjectExportUnsupportedReason(
            supportedSingleClip, AudioStudioState(), ExportSettings()
        ))
    }

    @Test fun `whole-project config passes all enabled project music tracks to postmix`() {
        val first = AudioTrack(
            id = "music-1", name = "Music 1", uri = "file:///music-1.m4a",
            trimEndMs = 30_000L, sourceDurationMs = 30_000L
        )
        val second = AudioTrack(
            id = "voice-2", name = "Voice 2", uri = "file:///voice-2.m4a",
            trimEndMs = 12_000L, sourceDurationMs = 12_000L, startTimeMs = 8_000L,
            kind = AudioTrack.Kind.SFX
        )
        val muted = first.copy(id = "muted", name = "Muted", uri = "file:///muted.m4a", isMuted = true)
        val withAudio = state.copy(project = state.project!!.copy(audioTracks = listOf(first, second, muted)))

        val config = buildProjectExportConfig(
            withAudio, AudioStudioState(), ExportSettings(), includeSharedVideoClips = false
        )
        assertEquals(listOf(first, second), config.musicMixTracks)
        assertTrue(config.overlayAudioSources.isEmpty())
        assertNull(wholeProjectExportUnsupportedReason(withAudio, AudioStudioState(), ExportSettings()))
    }

    @Test fun `restored non-main photo and video cannot be filtered out before export preflight`() {
        for (type in listOf(ClipType.IMAGE, ClipType.VIDEO)) {
            val other = video.copy(id = "other", type = type, trackIndex = 2)
            val saved = state.copy(project = state.project!!.copy(clips = listOf(video, other)))
            assertTrue(projectVisualScopeUnsupportedReason(saved)!!.contains("cannot be omitted"))
            assertNull(projectVisualScopeUnsupportedReason(saved.copy(hiddenVideoLayers = setOf(2))))
            assertEquals(listOf(video, other), saved.project!!.clips)
        }
    }
}
