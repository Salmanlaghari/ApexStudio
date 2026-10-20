package com.apexstudio.app.data.export

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.SpeedCurveMath
import com.apexstudio.app.domain.model.VideoCropSettings
import com.apexstudio.app.domain.model.VideoDrawingLayer
import com.apexstudio.app.domain.model.VideoDrawingPoint
import com.apexstudio.app.domain.model.VideoDrawingStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectCompositionExportTest {
    private fun clip(
        id: String = "v1",
        type: ClipType = ClipType.VIDEO,
        track: Int = 0
    ) = MediaClip(
        id = id,
        name = id,
        uri = "file:///$id.mp4",
        durationMs = 5_000L,
        trimStartMs = 250L,
        trimEndMs = 4_500L,
        trackIndex = track,
        type = type
    )

    @Test
    fun `plain main videos with per item trim speed gain and global music are eligible`() {
        val first = clip().copy(speedMultiplier = 1.5f, volume = 0.6f)
        val second = clip("second").copy(speedPoints = SpeedCurveMath.flat(2f))
        val config = ExportEngine.ExportConfig(
            resolution = "1080p",
            aspectRatio = "9:16",
            fps = 30,
            volume = 0.8f,
            pitchSemitones = 2f,
            musicMixTracks = listOf(
                com.apexstudio.app.domain.model.AudioTrack(
                    id = "music",
                    name = "Music",
                    uri = "file:///music.m4a",
                    trimEndMs = 1_000L
                )
            )
        )
        assertNull(projectCompositionUnsupportedReason(listOf(first, second), config))
    }

    @Test
    fun `empty project and non video or non main track media are rejected explicitly`() {
        assertTrue(projectCompositionUnsupportedReason(emptyList(), ExportEngine.ExportConfig())!!.contains("at least one"))
        assertTrue(projectCompositionUnsupportedReason(listOf(clip(type = ClipType.IMAGE)), ExportEngine.ExportConfig())!!.contains("photo"))
        assertTrue(projectCompositionUnsupportedReason(listOf(clip("overlay", track = 1)), ExportEngine.ExportConfig())!!.contains("main-track video"))
        assertTrue(projectCompositionUnsupportedReason(listOf(clip(type = ClipType.AUDIO)), ExportEngine.ExportConfig())!!.contains("audio"))
    }

    @Test
    fun `unsupported per item visual edits are never silently ignored`() {
        val cropped = clip().copy(videoCrop = VideoCropSettings(left = 0.1f))
        assertTrue(projectCompositionUnsupportedReason(listOf(cropped), ExportEngine.ExportConfig())!!.contains("crop edit"))

        val captioned = clip().copy(textOverlays = listOf(com.apexstudio.app.domain.model.TextOverlay(
            id = "title", text = "hello", x = 0.5f, y = 0.5f
        )))
        assertTrue(projectCompositionUnsupportedReason(listOf(captioned), ExportEngine.ExportConfig())!!.contains("captions"))

        val replaced = clip().copy(backgroundReplace = com.apexstudio.app.domain.model.BackgroundReplaceSettings())
        assertTrue(projectCompositionUnsupportedReason(listOf(replaced), ExportEngine.ExportConfig())!!.contains("background-replacement"))
    }

    @Test
    fun `unsupported global edits and audio effects have precise reasons`() {
        val base = listOf(clip())
        assertTrue(projectCompositionUnsupportedReason(base, ExportEngine.ExportConfig().copy(
            transitionType = com.apexstudio.app.data.gl.TransitionEngine.Companion.TransitionType.CROSS_DISSOLVE
        ))!!.contains("transitions"))
        assertTrue(projectCompositionUnsupportedReason(base, ExportEngine.ExportConfig().copy(
            reverbEnabled = true
        ))!!.contains("reverb, echo, or bass boost"))
        assertTrue(projectCompositionUnsupportedReason(base, ExportEngine.ExportConfig().copy(
            overlayAudioSources = listOf(ExportEngine.AudioExportSource(uri = "file:///voice.m4a", trimEndMs = 500L))
        ))!!.contains("timeline audio overlays"))
        assertTrue(projectCompositionUnsupportedReason(base, ExportEngine.ExportConfig().copy(fps = 29))!!.contains("fps"))
    }

    @Test
    fun `whole project export rejects artwork instead of silently omitting it`() {
        val base = listOf(clip())
        assertNull(projectCompositionUnsupportedReason(base, ExportEngine.ExportConfig(
            videoDrawings = listOf(VideoDrawingLayer(id = "empty"))
        )))
        val drawings = listOf(VideoDrawingLayer(
            id = "paint",
            clipId = "v1",
            strokes = listOf(VideoDrawingStroke(points = listOf(VideoDrawingPoint(0.2f, 0.3f))))
        ))
        assertTrue(projectCompositionUnsupportedReason(
            base, ExportEngine.ExportConfig(videoDrawings = drawings)
        )!!.contains("does not support project or clip drawings"))
    }

    @Test
    fun `global LUT grading and FX are accepted when their parameters are valid`() {
        val config = ExportEngine.ExportConfig(
            filterPreset = com.apexstudio.app.data.filter.FilterPreset(
                "cinema", "Cinema", "Cinematic", "luts/cinema.cube"
            ),
            filterIntensity = 0.7f,
            adjustments = com.apexstudio.app.domain.model.VideoAdjustments(brightness = 0.1f),
            fxPreset = com.apexstudio.app.data.fx.FxPreset.GLITCH,
            fxIntensity = 0.6f,
            fxSpeed = 1.3f
        )
        assertNull(projectCompositionUnsupportedReason(listOf(clip()), config))
        assertTrue(projectCompositionUnsupportedReason(listOf(clip()), config.copy(fxSpeed = 5f))!!
            .contains("global FX settings"))
    }

    @Test
    fun `project progress remains finite and monotonic`() {
        assertEquals(0.01f, monotonicProjectExportProgress(0f, 0.01f), 0f)
        assertEquals(0.4f, monotonicProjectExportProgress(0.4f, 0.2f), 0f)
        assertEquals(0.99f, monotonicProjectExportProgress(0.9f, 1f), 0f)
        assertEquals(0.4f, monotonicProjectExportProgress(0.4f, Float.NaN), 0f)
    }

    @Test
    fun `trim ranges must be valid and fit the known source duration`() {
        val invalid = clip().copy(trimEndMs = 5_001L)
        assertEquals(
            "Video clip 'v1' has an invalid trim range.",
            projectCompositionUnsupportedReason(listOf(invalid), ExportEngine.ExportConfig())
        )
    }
}
