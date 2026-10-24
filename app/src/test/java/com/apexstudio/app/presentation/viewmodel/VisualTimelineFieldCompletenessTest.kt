package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.BackgroundReplaceSettings
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.DenoiseStrength
import com.apexstudio.app.domain.model.GpuFilterConfig
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.OverlayBlend
import com.apexstudio.app.domain.model.PhotoEditSettings
import com.apexstudio.app.domain.model.VideoCropSettings
import com.apexstudio.app.domain.model.VideoFitMode
import com.apexstudio.app.domain.model.WorkspaceEffect
import com.apexstudio.app.domain.model.WorkspaceEffectPresets
import com.apexstudio.app.domain.model.timeline.BlendMode
import com.apexstudio.app.domain.model.timeline.Curve
import com.apexstudio.app.domain.model.timeline.Keyframe
import com.apexstudio.app.domain.model.timeline.OverlayItem
import com.apexstudio.app.domain.model.timeline.OverlayTrack
import com.apexstudio.app.domain.model.timeline.Timeline
import com.apexstudio.app.domain.model.timeline.VideoTrack
import com.apexstudio.app.domain.model.timeline.XY
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import com.apexstudio.app.domain.model.timeline.sharedVideoClip
import com.apexstudio.app.domain.model.timeline.syncSharedTimeline
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VisualTimelineFieldCompletenessTest {
    private val effect = WorkspaceEffect(WorkspaceEffectPresets.WARM, 0L, 500L, intensity = .7f)

    @Test
    fun `legacy visual fields import mutate and project without wrappers`() {
        val gpu = GpuFilterConfig(brightness = .2f, hue = 24f, activeProfileId = "profile")
        val legacy = MediaClip(
            id = "v", name = "video", uri = "file:///v.mp4", durationMs = 4_000L, trimEndMs = 3_000L,
            denoiseEnabled = true, denoiseStrength = DenoiseStrength.STRONG,
            backgroundReplace = BackgroundReplaceSettings.defaultFor("file:///original.mp4"),
            gpuFilterConfig = gpu, projectVideoEffectId = "film", videoCrop = VideoCropSettings(.1f, .2f, .8f, .9f, "free"),
            pipX = .2f, pipY = .3f, pipScale = .4f, pipRotationDeg = 12f, pipOpacity = .6f,
            overlayAspect = 1.5f, overlayBlend = OverlayBlend.SCREEN, overlayBorderWidth = .03f,
            overlayBorderColor = 0xFF112233.toInt(), overlayShadow = .8f, overlayFlipH = true, overlayFlipV = true,
            overlayVolume = .9f, photoEdit = PhotoEditSettings(rotationSteps = 1), workspaceEffects = listOf(effect),
            videoFitMode = VideoFitMode.COVER
        )
        val imported = com.apexstudio.app.domain.model.Project("p", "p", 4_000L, clips = listOf(legacy)).syncSharedTimeline()
        val typed = imported.sharedVideoClip("v")!!
        assertTrue(typed.denoiseEnabled)
        assertEquals(DenoiseStrength.STRONG, typed.denoiseStrength)
        assertEquals(gpu, typed.gpuFilterConfig)
        assertEquals("film", typed.projectVideoEffectId)
        assertEquals(legacy.videoCrop, typed.videoCrop)
        assertEquals(legacy.overlayBlend.toTypedBlendForTest(), typed.overlayBlend)
        assertEquals(legacy.photoEdit, typed.photoEdit)
        assertEquals(listOf(effect), typed.workspaceEffects)
        assertEquals(VideoFitMode.COVER, typed.videoFitMode)

        val edited = typed.copy(
            denoiseEnabled = false, gpuFilterConfig = GpuFilterConfig(sharpness = .8f),
            projectVideoEffectId = "new-effect", videoCrop = VideoCropSettings(.2f, .1f, .7f, .8f),
            overlayBlend = BlendMode.MULTIPLY, overlayBorderWidth = .1f, overlayFlipH = false,
            photoEdit = PhotoEditSettings(rotationSteps = 2), workspaceEffects = listOf(effect.copy(intensity = .3f)),
            videoFitMode = VideoFitMode.FIT
        )
        val out = imported.copy(timeline = Timeline(listOf(VideoTrack(id = "v-track", lane = 0, clips = listOf(edited))))).syncSharedTimeline().clips.single()
        assertFalse(out.denoiseEnabled)
        assertEquals(edited.gpuFilterConfig, out.gpuFilterConfig)
        assertEquals("new-effect", out.projectVideoEffectId)
        assertEquals(edited.videoCrop, out.videoCrop)
        assertEquals(OverlayBlend.MULTIPLY, out.overlayBlend)
        assertEquals(.1f, out.overlayBorderWidth, 0f)
        assertEquals(edited.photoEdit, out.photoEdit)
        assertEquals(VideoFitMode.FIT, out.videoFitMode)
    }

    @Test
    fun `overlay image carries complete visual schema and old defaults remain compatible`() {
        val legacy = MediaClip("i", "image", "file:///i.jpg", 2_000L, trimEndMs = 2_000L, type = ClipType.IMAGE, trackIndex = 2)
        val project = com.apexstudio.app.domain.model.Project("p", "p", 2_000L, clips = listOf(legacy)).syncSharedTimeline()
        val image = project.timeline.tracks.filterIsInstance<OverlayTrack>().single().items.single() as OverlayItem.Image
        assertEquals(0f, image.videoCrop.left, 0f)
        assertEquals(BlendMode.NORMAL, image.overlayBlend)
        assertEquals(GpuFilterConfig(), image.gpuFilterConfig)
        val json = Json { encodeDefaults = false }.encodeToString(Timeline.serializer(), project.timeline)
        assertTrue(json.isNotEmpty())
    }

    @Test
    fun `image overlay legacy base refresh preserves typed transform and opacity keys`() {
        val legacy = MediaClip(
            "i", "image", "file:///i.jpg", 2_000L, trimEndMs = 2_000L,
            type = ClipType.IMAGE, trackIndex = 2
        )
        val project = com.apexstudio.app.domain.model.Project("p", "p", 2_000L, clips = listOf(legacy))
            .syncSharedTimeline()
        val original = project.timeline.tracks.filterIsInstance<OverlayTrack>().single().items.single() as OverlayItem.Image
        val positionKeys = listOf(Keyframe(0L, XY(.1f, .2f)), Keyframe(2_000L, XY(.8f, .9f), Curve.EASE_OUT))
        val scaleKeys = listOf(Keyframe(0L, 1f), Keyframe(2_000L, 1.8f))
        val rotationKeys = listOf(Keyframe(0L, 0f), Keyframe(2_000L, 45f))
        val opacityKeys = listOf(Keyframe(0L, 1f), Keyframe(2_000L, .3f))
        val animated = original.copy(
            transform = original.transform.copy(
                positionKeyframes = positionKeys,
                scaleKeyframes = scaleKeys,
                rotationKeyframes = rotationKeys
            ),
            opacityKeyframes = opacityKeys
        )
        val changed = project.copy(
            clips = listOf(legacy.copy(pipX = .33f, pipY = .44f, pipScale = .56f, pipRotationDeg = 14f, pipOpacity = .65f)),
            timeline = Timeline(project.timeline.tracks.map { track ->
                if (track is OverlayTrack) track.copy(items = listOf(animated)) else track
            })
        ).syncSharedTimeline(refreshLegacyClipFields = true)
        val result = changed.timeline.tracks.filterIsInstance<OverlayTrack>().single().items.single() as OverlayItem.Image

        assertEquals(.33f, result.transform.position.x, 0f)
        assertEquals(.44f, result.transform.position.y, 0f)
        assertEquals(.56f, result.transform.scale, 0f)
        assertEquals(14f, result.transform.rotationDeg, 0f)
        assertEquals(.65f, result.opacity, 0f)
        assertEquals(positionKeys, result.transform.positionKeyframes)
        assertEquals(scaleKeys, result.transform.scaleKeyframes)
        assertEquals(rotationKeys, result.transform.rotationKeyframes)
        assertEquals(opacityKeys, result.opacityKeyframes)
    }

    @Test
    fun `image overlay round trips typed visual animation through shared timeline`() {
        val legacy = MediaClip(
            "i", "image", "file:///i.jpg", 2_000L, trimEndMs = 2_000L,
            type = ClipType.IMAGE, trackIndex = 2
        )
        val project = com.apexstudio.app.domain.model.Project("p", "p", 2_000L, clips = listOf(legacy))
            .syncSharedTimeline()
        val original = project.timeline.tracks.filterIsInstance<OverlayTrack>().single().items.single() as OverlayItem.Image
        val filterKeys = listOf(Keyframe(0L, .2f), Keyframe(2_000L, .9f, Curve.EASE_IN))
        val effectKeys = listOf(Keyframe(0L, .1f), Keyframe(2_000L, .8f))
        val lutKeys = listOf(Keyframe(0L, .3f), Keyframe(2_000L, .7f))
        val saturationKeys = listOf(Keyframe(0L, .6f), Keyframe(2_000L, 1.4f))
        val typed = original.copy(
            filter = "film-grain",
            filterIntensity = .75f,
            filterKeyframes = filterKeys,
            effectIntensity = .45f,
            effectIntensityKeyframes = effectKeys,
            lutId = "lut-cinema",
            lutIntensity = .65f,
            lutKeyframes = lutKeys,
            saturation = 1.2f,
            saturationKeyframes = saturationKeys,
            blendMode = BlendMode.SCREEN
        )
        val roundTripped = project.copy(
            clips = listOf(legacy.copy(pipX = .31f)),
            timeline = Timeline(project.timeline.tracks.map { track ->
                if (track is OverlayTrack) track.copy(items = listOf(typed)) else track
            })
        ).syncSharedTimeline(refreshLegacyClipFields = true)
        val result = roundTripped.timeline.tracks.filterIsInstance<OverlayTrack>().single().items.single() as OverlayItem.Image
        val legacyResult = roundTripped.clips.single()

        assertEquals("film-grain", result.filter)
        assertEquals(.75f, result.filterIntensity, 0f)
        assertEquals(filterKeys, result.filterKeyframes)
        assertEquals(.45f, result.effectIntensity, 0f)
        assertEquals(effectKeys, result.effectIntensityKeyframes)
        assertEquals("lut-cinema", result.lutId)
        assertEquals(lutKeys, result.lutKeyframes)
        assertEquals(1.2f, result.saturation, 0f)
        assertEquals(saturationKeys, result.saturationKeyframes)
        assertEquals(BlendMode.SCREEN, result.blendMode)
        assertEquals(filterKeys.map { it.timeMs }.toSet(), legacyResult.keyframes.keyframes.map { it.timeMs }.toSet())
        assertEquals(filterKeys[1].value, legacyResult.keyframes.keyframes.first { it.timeMs == 2_000L }.filterIntensity, 0f)
        assertEquals(effectKeys[1].value, legacyResult.keyframes.keyframes.first { it.timeMs == 2_000L }.effectIntensity, 0f)
        assertEquals(saturationKeys[1].value, legacyResult.keyframes.keyframes.first { it.timeMs == 2_000L }.saturation!!, 0f)
    }

    private fun OverlayBlend.toTypedBlendForTest() = when (this) {
        OverlayBlend.NORMAL -> BlendMode.NORMAL
        OverlayBlend.SCREEN -> BlendMode.SCREEN
        OverlayBlend.MULTIPLY -> BlendMode.MULTIPLY
        OverlayBlend.OVERLAY -> BlendMode.OVERLAY
        OverlayBlend.SOFT_LIGHT -> BlendMode.SOFT_LIGHT
    }
}
