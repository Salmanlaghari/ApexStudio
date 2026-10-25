package com.apexstudio.app.data.export

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Keyframe
import com.apexstudio.app.domain.model.KeyframeTrack
import com.apexstudio.app.domain.model.StickerOverlay
import com.apexstudio.app.domain.model.SpeedCurveMath
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.domain.model.VideoCropSettings
import com.apexstudio.app.domain.model.VideoDrawingLayer
import com.apexstudio.app.domain.model.VideoDrawingPoint
import com.apexstudio.app.domain.model.VideoDrawingStroke
import com.apexstudio.app.domain.model.WorkspaceEffect
import com.apexstudio.app.domain.model.WorkspaceEffectPresets
import com.apexstudio.app.data.color.ColorStudioGrade
import com.apexstudio.app.data.color.ColorWheelVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.apexstudio.app.data.filter.GpuFilterConfig
import com.apexstudio.app.data.filter.StylisticEffectType
import kotlinx.serialization.json.Json
import com.apexstudio.app.domain.model.MaskModel
import com.apexstudio.app.domain.model.MaskPoint
import com.apexstudio.app.domain.model.MaskShape

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

    private fun faceCropTrack(aspect: Float = 9f / 16f) = com.apexstudio.app.domain.model.FaceCropTrack(
        sourceWidth = 1920,
        sourceHeight = 1080,
        outputAspectRatio = aspect,
        keyframes = listOf(
            com.apexstudio.app.domain.model.FaceCropKeyframe(
                sourceTimestampMs = 0L,
                left = 0.2f,
                top = 0f,
                right = 0.2f + aspect * 1080f / 1920f,
                bottom = 1f,
                faceFound = true
            )
        )
    )

    @Test
    fun `valid per clip face crop tracks are exported but ambiguous crops fail closed`() {
        val tracked = clip().copy(faceCropTrack = faceCropTrack())
        assertNull(projectCompositionUnsupportedReason(listOf(tracked), ExportEngine.ExportConfig()))

        val ambiguous = tracked.copy(videoCrop = VideoCropSettings(left = 0.1f, right = 0.9f))
        assertTrue(projectCompositionUnsupportedReason(listOf(ambiguous), ExportEngine.ExportConfig())!!
            .contains("combines a dynamic face-crop track with a static crop"))

        val unsafeAspect = clip().copy(faceCropTrack = com.apexstudio.app.domain.model.FaceCropTrack(
            sourceWidth = 1920,
            sourceHeight = 1080,
            outputAspectRatio = 20f,
            keyframes = listOf(com.apexstudio.app.domain.model.FaceCropKeyframe(
                sourceTimestampMs = 0L,
                left = 0f,
                top = 0f,
                right = 1f,
                bottom = 1920f / (1080f * 20f),
                faceFound = true
            ))
        ))
        assertTrue(projectCompositionUnsupportedReason(listOf(unsafeAspect), ExportEngine.ExportConfig())!!
            .contains("face-crop output aspect ratio outside the safe export range"))
    }

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
    fun `project export accepts clip keyframes but still rejects export wide keyframes`() {
        val animated = clip().copy(
            speedMultiplier = 2f,
            keyframes = KeyframeTrack(listOf(Keyframe.identity(timeMs = 1_000L)))
        )

        assertNull(projectCompositionUnsupportedReason(listOf(animated), ExportEngine.ExportConfig()))
        assertTrue(projectCompositionUnsupportedReason(
            listOf(clip()), ExportEngine.ExportConfig(keyframes = KeyframeTrack(
                listOf(Keyframe.identity(timeMs = 1_000L))
            ))
        )!!.contains("global keyframes"))
    }

    @Test
    fun `empty project and non video or non main track media are rejected explicitly`() {
        assertTrue(projectCompositionUnsupportedReason(emptyList(), ExportEngine.ExportConfig())!!.contains("at least one"))
        assertTrue(projectCompositionUnsupportedReason(listOf(clip(type = ClipType.IMAGE)), ExportEngine.ExportConfig())!!.contains("photo"))
        assertTrue(projectCompositionUnsupportedReason(listOf(clip("overlay", track = 1)), ExportEngine.ExportConfig())!!.contains("main-track video"))
        assertTrue(projectCompositionUnsupportedReason(listOf(clip(type = ClipType.AUDIO)), ExportEngine.ExportConfig())!!.contains("audio"))
    }

    @Test
    fun `per clip captions templates sticker variants and crop are eligible`() {
        val source = clip().copy(
            videoCrop = VideoCropSettings(left = 0.1f, top = 0.05f, right = 0.9f, bottom = 0.95f),
            textOverlays = listOf(TextOverlay(
                id = "title", text = "YOUR STORY", startMs = 300L, endMs = 1_400L, presetId = "title-bold"
            )),
            stickers = listOf(
                StickerOverlay(id = "emoji", symbolOrUri = "🔥", startMs = 500L, endMs = 1_800L, rotationDeg = 25f, opacity = 0.4f),
                StickerOverlay(id = "png", symbolOrUri = "love/red-heart.webp", assetPath = "stickers/love/red-heart.webp")
            )
        )
        assertNull(projectCompositionUnsupportedReason(listOf(source), ExportEngine.ExportConfig()))

        val emoji = projectEmojiStickerAsTextOverlay(source.stickers.first())
        assertEquals(500L, emoji.startMs)
        assertEquals(1_800L, emoji.endMs)
        assertEquals("🔥", emoji.text)
        assertEquals(25f, emoji.rotationDeg, 0f)
        assertEquals(0.4f, emoji.opacity, 0f)
        assertEquals(source.stickers.first().keyframes, emoji.keyframes)
        assertEquals(
            source.stickers.first().sizeScale * com.apexstudio.app.data.stickers.StickerSpriteRenderer.EMOJI_TEXT_SIZE_FACTOR,
            emoji.sizeScale,
            0f
        )
    }

    @Test
    fun `spanning manual text keyframes block while preset motion and local pins stay eligible`() {
        val source = clip()
        val localEnd = source.trimEndMs - source.trimStartMs
        val title = TextOverlay(id = "spanning", text = "Across cuts", startMs = 0L, endMs = localEnd,
            animationType = "PULSE", animationWindowStartMs = 0L, animationWindowEndMs = localEnd + 1_000L)
        assertNull(projectCompositionUnsupportedReason(listOf(source.copy(textOverlays = listOf(title))), ExportEngine.ExportConfig()))
        val pinned = title.copy(keyframes = com.apexstudio.app.domain.model.KeyframeTrack(listOf(
            com.apexstudio.app.domain.model.Keyframe(id = "pin", timeMs = 100L))))
        assertTrue(projectCompositionUnsupportedReason(listOf(source.copy(textOverlays = listOf(pinned))),
            ExportEngine.ExportConfig())!!.contains("manual transform or mask keyframes"))
        val local = pinned.copy(animationWindowEndMs = localEnd)
        assertNull(projectCompositionUnsupportedReason(listOf(source.copy(textOverlays = listOf(local))), ExportEngine.ExportConfig()))
    }

    @Test
    fun `malformed per clip overlay timing and unsupported visuals remain fail closed`() {
        val invalidCaption = clip().copy(textOverlays = listOf(TextOverlay(
            id = "title", text = "hello", startMs = 1_000L, endMs = 500L
        )))
        assertTrue(projectCompositionUnsupportedReason(listOf(invalidCaption), ExportEngine.ExportConfig())!!.contains("caption with an invalid time range"))

        val invalidSticker = clip().copy(stickers = listOf(StickerOverlay(startMs = -1L)))
        assertTrue(projectCompositionUnsupportedReason(listOf(invalidSticker), ExportEngine.ExportConfig())!!.contains("sticker with an invalid time range"))

        val malformedCrop = clip().copy(videoCrop = VideoCropSettings(left = Float.NaN))
        assertTrue(projectCompositionUnsupportedReason(listOf(malformedCrop), ExportEngine.ExportConfig())!!.contains("invalid crop edit"))

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
        assertTrue(projectCompositionUnsupportedReason(base, ExportEngine.ExportConfig().copy(
            textOverlays = listOf(TextOverlay(id = "global", text = "global"))
        ))!!.contains("export-wide captions or stickers"))
        assertTrue(projectCompositionUnsupportedReason(base, ExportEngine.ExportConfig().copy(fps = 29))!!.contains("fps"))
    }

    @Test
    fun `whole project export accepts artwork for per clip rendering`() {
        val base = listOf(clip())
        assertNull(projectCompositionUnsupportedReason(base, ExportEngine.ExportConfig(
            videoDrawings = listOf(VideoDrawingLayer(id = "empty"))
        )))
        val stroke = VideoDrawingStroke(points = listOf(VideoDrawingPoint(0.2f, 0.3f)))
        val drawings = listOf(
            VideoDrawingLayer(id = "project-wide", strokes = listOf(stroke)),
            VideoDrawingLayer(id = "paint", clipId = "v1", strokes = listOf(stroke)),
            VideoDrawingLayer(id = "other-clip", clipId = "v2", strokes = listOf(stroke))
        )
        assertEquals(
            listOf("project-wide", "paint"),
            projectDrawingLayersForClipExport(drawings, "v1").map { it.id }
        )
        assertNull(projectCompositionUnsupportedReason(
            base, ExportEngine.ExportConfig(videoDrawings = drawings)
        ))
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

    @Test
    fun `project export rejects malformed persisted filter customization instead of dropping it`() {
        val invalid = clip().copy(workspaceEffects = listOf(
            WorkspaceEffect(WorkspaceEffectPresets.FILTER_TEAL_ORANGE, 250L, 1_500L, exposure = Float.NaN)
        ))
        assertTrue(projectCompositionUnsupportedReason(listOf(invalid), ExportEngine.ExportConfig())!!
            .contains("invalid or unsupported ranged filter"))
    }

    @Test
    fun `project export rejects export wide ranged filters but accepts clip ranged filters`() {
        val effect = WorkspaceEffect(WorkspaceEffectPresets.FILTER_TEAL_ORANGE, 250L, 1_500L)

        assertTrue(projectCompositionUnsupportedReason(
            listOf(clip()), ExportEngine.ExportConfig(workspaceEffects = listOf(effect))
        )!!.contains("export-wide ranged filters"))
        assertNull(projectCompositionUnsupportedReason(
            listOf(clip().copy(workspaceEffects = listOf(effect))), ExportEngine.ExportConfig()
        ))
    }

    @Test
    fun `Color Studio grade runs through eligible project composition but malformed values block export`() {
        val grade = ColorStudioGrade(brightness = 0.1f, contrast = 1.2f,
            shadows = ColorWheelVector(0.2f, -0.3f))
        assertNull(projectCompositionUnsupportedReason(
            listOf(clip(), clip("second")), ExportEngine.ExportConfig(colorStudioGrade = grade)
        ))
        assertTrue(projectCompositionUnsupportedReason(
            listOf(clip()), ExportEngine.ExportConfig(colorStudioGrade = grade.copy(brightness = Float.NaN))
        )!!.contains("invalid Color Studio grade"))
        assertTrue(projectCompositionUnsupportedReason(
            listOf(clip()), ExportEngine.ExportConfig(colorStudioGrade = grade.copy(
                highlights = ColorWheelVector(2f, 0f)
            ))
        )!!.contains("invalid Color Studio grade"))
    }

    @Test
    fun `per clip GPU filter state stays attached to its own project item`() {
        val first = clip().copy(gpuFilterConfig = GpuFilterConfig(
            stylisticEffect = StylisticEffectType.VIGNETTE,
            stylisticIntensity = 0.75f
        ))
        val second = clip("second")
        assertEquals(
            listOf(
                ProjectItemFilterExport(first.id, first.gpuFilterConfig),
                ProjectItemFilterExport(second.id, GpuFilterConfig())
            ),
            projectItemFilterExports(listOf(first, second))
        )
        assertNull(projectCompositionUnsupportedReason(listOf(first, second), ExportEngine.ExportConfig()))
        assertTrue(second.gpuFilterConfig.isDefault)
        assertTrue(first.gpuFilterConfig.toProjectExportAdjustments().vignette > 0f)
    }

    @Test
    fun `default per clip GPU filter preserves export defaults`() {
        val adjustments = GpuFilterConfig().toProjectExportAdjustments()
        assertTrue(adjustments.isDefault)
        assertNull(projectGpuFilterUnsupportedReason(GpuFilterConfig()))
    }

    @Test
    fun `per clip GPU filter survives project item persistence reload`() {
        val source = clip().copy(gpuFilterConfig = GpuFilterConfig(
            filterPresetId = "creator_clean",
            filterIntensity = 0.6f,
            brightness = 0.15f
        ))
        val restored = Json.decodeFromString<MediaClip>(
            Json.encodeToString(MediaClip.serializer(), source)
        )
        assertEquals(source.gpuFilterConfig, restored.gpuFilterConfig)
        assertEquals(source.id, restored.id)
    }

    @Test
    fun `malformed per clip GPU filter is rejected`() {
        val invalid = clip().copy(gpuFilterConfig = GpuFilterConfig(filterIntensity = Float.NaN))
        assertTrue(projectCompositionUnsupportedReason(listOf(invalid), ExportEngine.ExportConfig())!!
            .contains("invalid per-clip filter/LUT"))
    }

    @Test
    fun `whole project export refuses per clip GPU operations its Media3 renderer cannot represent`() {
        val unsupported = listOf(
            GpuFilterConfig(gamma = 1.2f) to "gamma grading",
            GpuFilterConfig(activeProfileId = "film") to "color profile",
            GpuFilterConfig(stylisticEffect = StylisticEffectType.HALFTONE) to "stylistic effect",
            GpuFilterConfig(filterPresetId = "missing-preset") to "unknown per-clip filter/LUT preset"
        )
        unsupported.forEach { (gpuConfig, expected) ->
            val reason = projectCompositionUnsupportedReason(
                listOf(clip().copy(gpuFilterConfig = gpuConfig)), ExportEngine.ExportConfig()
            )!!
            assertTrue("expected '$expected' in '$reason'", reason.contains(expected))
        }
    }

    @Test
    fun `whole project export accepts valid main-track clip masks`() {
        val masked = clip().copy(mask = MaskModel.defaultForShape(MaskShape.ELLIPSE))
        assertNull(projectCompositionUnsupportedReason(listOf(masked), ExportEngine.ExportConfig()))
    }

    @Test
    fun `whole project export rejects unavailable and invalid clip masks explicitly`() {
        val blur = clip().copy(mask = MaskModel.defaultForShape(MaskShape.BLUR_FACE))
        assertTrue(projectCompositionUnsupportedReason(listOf(blur), ExportEngine.ExportConfig())!!
            .contains("unavailable Blur Faces"))
        val invalidPen = clip().copy(mask = MaskModel.defaultForShape(MaskShape.DRAW)
            .copy(drawPoints = listOf(MaskPoint(0f, 0f), MaskPoint(1f, 1f))))
        assertTrue(projectCompositionUnsupportedReason(listOf(invalidPen), ExportEngine.ExportConfig())!!
            .contains("invalid pen mask geometry"))
    }
}
