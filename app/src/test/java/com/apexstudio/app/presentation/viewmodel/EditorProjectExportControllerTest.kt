package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.*
import androidx.media3.common.MimeTypes
import com.apexstudio.app.data.export.EightKExportAvailability
import com.apexstudio.app.data.export.ExportVideoEncoderPlan
import com.apexstudio.app.data.filter.FilterPreset
import com.apexstudio.app.data.fx.FxPreset
import com.apexstudio.app.data.export.projectCompositionUnsupportedReason
import com.apexstudio.app.data.template.ShortsMusicAsset
import com.apexstudio.app.data.template.ShortsMusicRepository
import com.apexstudio.app.data.template.ShortsTemplateCatalog
import com.apexstudio.app.data.template.ShortsTemplateProjectFactory
import com.apexstudio.app.presentation.state.AudioStudioState
import com.apexstudio.app.presentation.state.EditorState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.serialization.json.Json
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorProjectExportControllerTest {
    private val first = MediaClip("first", "First", "file:///first.mp4", 5000, trimEndMs = 5000)
    private val second = first.copy(id = "second", uri = "file:///second.mp4", speedMultiplier = 2f)
    private val state = EditorState(project = Project("p", "Project", 7500, clips = listOf(first, second)))
    private val settings = ExportSettings(resolution = "1080p", frameRate = 30, aspectRatio = "9:16", bitrateMbps = 12)

    @Test fun `project mode is independent of selected clip and preserves timeline order`() {
        assertEquals(listOf(first, second), projectMainExportClips(state.copy(selectedClipId = second.id)))
        assertNull(wholeProjectExportUnsupportedReason(state, AudioStudioState(), settings))
    }

    @Test fun `global audio and output settings reach project config`() {
        val config = buildProjectExportConfig(state, AudioStudioState(volume = 0.4f, pitchSemitones = 3f), settings)
        assertEquals("1080P", config.resolution)
        assertEquals("9:16", config.aspectRatio)
        assertEquals(30, config.fps)
        assertEquals(12, config.bitrateMbps)
        assertEquals(0.4f, config.volume, 0f)
        assertEquals(3f, config.pitchSemitones, 0f)
        assertEquals(0f, buildProjectExportConfig(state, AudioStudioState(isMuted = true), settings).volume, 0f)
    }

    @Test fun `project adjustments restore into editor state and reach project export config`() {
        val persisted = VideoAdjustments(brightness = .27f, sharpness = .68f, contrast = 1.14f)
        val updated = persisted.copy(brightness = .36f, sharpness = .82f)
        val viewModel = EditorViewModel()
        viewModel.openSavedProject(
            Project("adjustment-project", "Adjusted", 5_000L, clips = listOf(first), videoAdjustments = persisted)
        )

        assertEquals(persisted, viewModel.state.value.adjustments)
        viewModel.updateAdjustments { updated }

        assertEquals(updated, viewModel.state.value.adjustments)
        assertEquals(updated, viewModel.state.value.project?.videoAdjustments)
        assertEquals(updated, buildProjectExportConfig(viewModel.state.value, AudioStudioState(), settings).adjustments)
        viewModel.flushProject()
    }

    @Test fun `historical 8K settings stay 8K and fail closed before a device plan exists`() {
        val settings8k = settings.copy(resolution = "8K Ultra HD")
        val config = buildProjectExportConfig(
            state,
            AudioStudioState(),
            settings8k
        )
        assertEquals("8K", config.resolution)
        assertEquals(30, config.fps)
        assertEquals("9:16", config.aspectRatio)
        assertNotNull(wholeProjectExportUnsupportedReason(state, AudioStudioState(), settings8k))
    }

    @Test fun `verified device encoder plan reaches whole-project config unchanged`() {
        val settings8k = settings.copy(resolution = "8K")
        val plan = ExportVideoEncoderPlan(MimeTypes.VIDEO_H265, "vendor.hevc.encoder")
        val availability = EightKExportAvailability.Supported(plan)
        assertNull(
            wholeProjectExportUnsupportedReason(
                state, AudioStudioState(), settings8k, eightKAvailability = availability
            )
        )
        val config = buildProjectExportConfig(
            state, AudioStudioState(), settings8k, encoderPlan = plan
        )
        assertEquals("8K", config.resolution)
        assertEquals(MimeTypes.VIDEO_H265, config.videoMimeType)
        assertEquals("vendor.hevc.encoder", config.videoEncoderName)
    }

    @Test fun `project music respects mute and solo before engine postmix`() {
        val music = AudioTrack("music", "Music", "file:///music.aac")
        val voice = music.copy(id = "voice", isSolo = true)
        val muted = music.copy(id = "muted", isMuted = true, isSolo = true)
        val edited = state.copy(project = state.project!!.copy(audioTracks = listOf(music, voice, muted)))
        assertEquals(listOf(voice), buildProjectExportConfig(edited, AudioStudioState(), settings).musicMixTracks)
    }

    @Test fun `Shorts factory output keeps its text and licensed music exportable`() {
        val template = ShortsTemplateCatalog.byId("shorts_travel_adventure")!!
        val sourceClip = first.copy(
            id = "shorts-video", uri = "file:///shorts.mp4", durationMs = 45_000L,
            trimStartMs = 0L, trimEndMs = 45_000L, trackIndex = 0, type = ClipType.VIDEO
        )
        val sourceProject = Project("shorts-project", "Shorts", 45_000L, clips = listOf(sourceClip))
        val musicFile = File.createTempFile("shorts-export-test-", ".mp3")
        try {
            musicFile.writeBytes(byteArrayOf(1))
            val url = ShortsMusicRepository.ALLOWED_URL_PREFIX + musicFile.name
            val asset = ShortsMusicAsset(
                id = template.musicAssetId, templateName = template.name,
                trackTitle = "Verified test track", artist = "Test artist",
                fileName = musicFile.name, downloadUrl = url,
                sourcePageUrl = "https://example.org/source", sourceDownloadUrl = url,
                mimeType = "audio/mpeg", bytes = musicFile.length(), durationMs = 45_000L,
                sha256 = java.security.MessageDigest.getInstance("SHA-256").digest(musicFile.readBytes()).joinToString("") { "%02x".format(it) },
                license = "CC BY 4.0",
                licenseUrl = "https://creativecommons.org/licenses/by/4.0/",
                attributionRequired = true, attribution = "Test artist — Verified test track",
                changes = "Trimmed to the project duration"
            )
            val ready = ShortsTemplateProjectFactory.apply(sourceProject, template, asset, musicFile)
            val editorState = EditorState(project = ready, selectedClipId = ready.clips.single().id)
            val config = buildProjectExportConfig(editorState, AudioStudioState(), settings)

            assertEquals(30_000L, ready.durationMs)
            assertEquals(9f / 16f, ready.videoAspectRatio!!, 0f)
            assertEquals("9:16", ProjectAspectRatio.exportPreset(ready.videoAspectRatio))
            assertEquals(VideoFitMode.COVER, ready.clips.single().videoFitMode)
            assertEquals(2, ready.clips.single().textOverlays.size)
            assertEquals(asset.creditText, ready.audioTracks.single().attributionText)
            assertEquals(asset.license, ready.audioTracks.single().sourceLicense)
            assertTrue(ready.audioTracks.single().attributionRequired)
            assertEquals(asset.changes, ready.audioTracks.single().sourceChanges)
            assertEquals(ready.audioTracks, config.musicMixTracks)
            assertNull(wholeProjectExportUnsupportedReason(editorState, AudioStudioState(), settings))
            assertNull(projectCompositionUnsupportedReason(projectMainExportClips(editorState), config))
            val restored = Json.decodeFromString(Project.serializer(), Json.encodeToString(Project.serializer(), ready))
            assertEquals(asset.creditText, restored.audioTracks.single().attributionText)
            assertEquals(asset.sourcePageUrl, restored.audioTracks.single().sourcePageUrl)
            assertEquals(asset.changes, restored.audioTracks.single().sourceChanges)
        } finally {
            musicFile.delete()
        }
    }

    @Test fun `global filter FX and grading are carried into project config`() {
        val lut = FilterPreset("cinema", "Cinema", "Cinematic", "luts/cinema.cube")
        val edited = state.copy(
            activeFilterId = lut.id,
            filterIntensity = 0.65f,
            activeFxId = FxPreset.GLITCH.id,
            fxIntensity = 0.4f,
            fxSpeed = 1.5f,
            adjustments = VideoAdjustments(brightness = 0.12f, saturation = 1.1f)
        )
        val config = buildProjectExportConfig(edited, AudioStudioState(), settings, lut)
        assertEquals(lut, config.filterPreset)
        assertEquals(0.65f, config.filterIntensity, 0f)
        assertEquals(FxPreset.GLITCH, config.fxPreset)
        assertEquals(0.4f, config.fxIntensity, 0f)
        assertEquals(1.5f, config.fxSpeed, 0f)
        assertEquals(edited.adjustments, config.adjustments)
        assertNull(wholeProjectExportUnsupportedReason(edited, AudioStudioState(), settings, lut))
    }

    @Test fun `unsupported edits block project mode rather than dropping from output`() {
        assertNotNull(wholeProjectExportUnsupportedReason(state.copy(activeFilterId = "cinema"), AudioStudioState(), settings))
        assertNotNull(wholeProjectExportUnsupportedReason(state.copy(activeFxId = "not-a-real-fx"), AudioStudioState(), settings))
        assertNotNull(wholeProjectExportUnsupportedReason(state.copy(activeArFilterId = "festival"), AudioStudioState(), settings))
        assertNotNull(wholeProjectExportUnsupportedReason(state.copy(project = state.project!!.copy(
            clips = listOf(first.copy(type = ClipType.IMAGE), second))), AudioStudioState(), settings))
        assertNotNull(wholeProjectExportUnsupportedReason(state, AudioStudioState(reverbEnabled = true), settings))
    }
}
