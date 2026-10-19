package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.*
import com.apexstudio.app.presentation.state.EditorState
import org.junit.Assert.*
import org.junit.Test

class PhotoExportConfigTest {
    private val photo = MediaClip(
        id = "photo", name = "Photo", uri = "file:///photo.jpg", durationMs = 5000,
        trimStartMs = 1000, trimEndMs = 4000, type = ClipType.IMAGE
    )
    private fun config(state: EditorState = EditorState(), clip: MediaClip = photo) =
        buildPhotoExportConfig(state, clip, "1440p", 30, "high", "9:16", 12)

    @Test fun `previous export completion cannot delete a new photo intermediate`() {
        val previous = com.apexstudio.app.data.export.ExportEngine.ExportProgressState(outputUri = "file:///old.mp4")
        assertFalse(isNewPhotoExportTerminal(previous, previous))
        assertFalse(isNewPhotoExportTerminal(previous, previous.copy(isExporting = true, outputUri = null)))
        assertTrue(isNewPhotoExportTerminal(previous, previous.copy(outputUri = "file:///new.mp4")))
        assertTrue(isNewPhotoExportTerminal(previous, previous.copy(error = "Fast failure", outputUri = null)))
    }

    @Test fun `photo export retains aspect bitrate resolution and frame rate`() {
        val result = config()
        assertEquals("9:16", result.aspectRatio)
        assertEquals(12, result.bitrateMbps)
        assertEquals("1440p", result.resolution)
        assertEquals(30, result.fps)
        assertEquals("high", result.quality)
    }

    @Test fun `explicit photo canvas is not cropped again by video export settings`() {
        val canvas = photo.copy(photoEdit = PhotoEditSettings(canvas = PhotoCanvasSettings(aspectPreset = "4:5")))
        assertTrue(config(clip = canvas).preserveInputAspectRatio)
        assertFalse(config().preserveInputAspectRatio)
    }

    @Test fun `photo export keeps FX timing without double applying baked grading`() {
        val result = config(EditorState(fxSpeed = 1.8f, fxIntensity = 0.4f))
        assertEquals(1.8f, result.fxSpeed, 0f)
        assertEquals(0.4f, result.fxIntensity, 0f)
        assertNull(result.filterPreset)
        assertEquals(0f, result.filterIntensity, 0f)
        assertEquals(VideoAdjustments(), result.adjustments)
    }

    @Test fun `photo source trim is baked exactly once`() {
        val result = config()
        assertEquals(0L, result.trimStartMs)
        assertEquals(0L, result.trimEndMs)
    }

    @Test fun `photo trim resolves unknown endpoint and clamps to source duration`() {
        assertEquals(3000L, photoExportSourceDurationMs(photo))
        assertEquals(4000L, photoExportSourceDurationMs(photo.copy(trimEndMs = Long.MAX_VALUE)))
        assertEquals(4000L, photoExportSourceDurationMs(photo.copy(trimEndMs = 0)))
        assertEquals(4000L, photoExportSourceDurationMs(photo.copy(trimEndMs = 9000)))
        assertEquals(4000L, photoExportSourceDurationMs(photo.copy(trimStartMs = -100)))
    }

    @Test fun `empty photo trim is rejected instead of exporting the whole photo`() {
        for (clip in listOf(photo.copy(trimEndMs = 1000), photo.copy(trimStartMs = 6000),
            photo.copy(durationMs = 0), photo.copy(trimEndMs = -1))) {
            try {
                photoExportSourceDurationMs(clip)
                fail("Expected unusable photo timing to be rejected")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun `legacy photo constant speed cannot diverge from image timeline`() {
        val clip = photo.copy(speedMultiplier = 4f)
        val result = config(clip = clip)
        assertEquals(3000L, clip.effectiveDurationMs())
        assertEquals(1f, result.clipSpeed, 0f)
    }

    @Test fun `image variable speed curve reaches the export processor`() {
        val curve = listOf(SpeedPoint(0f, 0.5f), SpeedPoint(1f, 2f))
        assertEquals(curve, config(clip = photo.copy(speedPoints = curve)).speedPoints)
    }

    @Test fun `photo layer snapshot retains visible PiP only and original timing`() {
        val overlay = photo.copy(id = "pip", type = ClipType.OVERLAY, trackIndex = 1,
            timelineOffsetMs = 700L, overlayVolume = 0.5f)
        val hidden = overlay.copy(id = "hidden", trackIndex = 2)
        val state = EditorState(
            project = Project(id = "p", name = "p", durationMs = 5000,
                clips = listOf(photo, overlay, hidden)), hiddenVideoLayers = setOf(2)
        )
        val result = config(state)
        assertEquals(1, result.pipOverlays.size)
        assertEquals(700L, result.pipOverlays.single().offsetMs)
        assertEquals(1, result.overlayAudioSources.size)
        assertEquals(700L, result.overlayAudioSources.single().timelinePositionMs)
        // The config is detached from subsequent immutable editor snapshots.
        assertEquals(1, result.pipOverlays.size)
        assertTrue(config(state.copy(hiddenVideoLayers = setOf(1, 2))).pipOverlays.isEmpty())
    }

    @Test fun `photo music uses project mute solo semantics`() {
        val music = AudioTrack("m", "Music", "file:///m.m4a")
        val solo = music.copy(id = "v", isSolo = true)
        val state = EditorState(project = Project(id = "p", name = "p", durationMs = 5000,
            clips = listOf(photo), audioTracks = listOf(music, solo)))
        assertEquals(listOf(solo), config(state).musicMixTracks)
    }
}
