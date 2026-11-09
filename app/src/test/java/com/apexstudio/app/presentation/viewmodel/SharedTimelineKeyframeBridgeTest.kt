package com.apexstudio.app.presentation.viewmodel

import android.net.Uri
import androidx.compose.ui.graphics.Color
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.SpeedCurve
import com.apexstudio.app.domain.model.SpeedPoint
import com.apexstudio.app.domain.model.AudioTrack as LegacyAudioTrack
import com.apexstudio.app.domain.model.VolumeKeyframe
import com.apexstudio.app.domain.model.MaskKeyframe
import com.apexstudio.app.domain.model.MaskPoint
import com.apexstudio.app.domain.model.MaskShape
import com.apexstudio.app.domain.model.VideoMask
import com.apexstudio.app.domain.model.VideoDrawingLayer
import com.apexstudio.app.domain.model.VideoDrawingStroke
import com.apexstudio.app.domain.model.VideoDrawingPoint
import com.apexstudio.app.domain.model.VideoDrawingTool
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.domain.model.StickerOverlay
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.domain.model.timeline.StickerItem
import com.apexstudio.app.domain.model.timeline.StickerTrack
import com.apexstudio.app.domain.model.timeline.TextItem
import com.apexstudio.app.domain.model.timeline.TextTrack
import com.apexstudio.app.domain.model.timeline.Timeline
import com.apexstudio.app.domain.model.timeline.VideoTrack
import com.apexstudio.app.domain.model.timeline.OverlayTrack
import com.apexstudio.app.domain.model.timeline.OverlayItem
import com.apexstudio.app.domain.model.timeline.XY
import com.apexstudio.app.domain.model.timeline.AudioClip
import com.apexstudio.app.domain.model.timeline.AudioTrack as SharedAudioTrack
import com.apexstudio.app.domain.model.timeline.Curve
import com.apexstudio.app.domain.model.timeline.Keyframe
import com.apexstudio.app.domain.model.timeline.Ducking
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

import com.apexstudio.app.domain.model.timeline.sharedVideoClip
import com.apexstudio.app.domain.model.timeline.syncSharedTimeline
import com.apexstudio.app.domain.model.timeline.applyLegacyAudioTrackChanges
import com.apexstudio.app.domain.model.timeline.toTypedMask
import com.apexstudio.app.domain.model.timeline.toLegacyMask
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedTimelineKeyframeBridgeTest {
    @Test
    fun `legacy scalar edit preserves typed volume and pan keyframes and ducking`() {
        val before = LegacyAudioTrack("a", "A", "file:///a.wav", volume = .5f, pan = -.2f)
        val typed = SharedAudioTrack(
            id = "shared-audio-a", lane = 10,
            volume = .5f, pan = -.2f,
            volumeKeyframes = listOf(Keyframe(100L, .2f, Curve.EASE_IN)),
            panKeyframes = listOf(Keyframe(200L, .7f, Curve.EASE_OUT)),
            ducking = Ducking(amount = .8f)
        )
        val after = before.copy(volume = .9f, pan = .4f)
        val updated = applyLegacyAudioTrackChanges(before, after, typed)
        assertEquals(.9f, updated.volume, 0f)
        assertEquals(.4f, updated.pan, 0f)
        assertEquals(typed.volumeKeyframes, updated.volumeKeyframes)
        assertEquals(typed.panKeyframes, updated.panKeyframes)
        assertEquals(typed.ducking, updated.ducking)
    }

    @Test
    fun `changed legacy audio keyframe updates typed track and clip`() {
        val before = LegacyAudioTrack(
            "a", "A", "file:///a.wav",
            volumeKeyframes = listOf(VolumeKeyframe("v", 100L, .2f))
        )
        val typed = SharedAudioTrack(
            id = "shared-audio-a", lane = 10,
            clips = listOf(AudioClip(id = "a", uri = Uri.parse("file:///a.wav"), inMs = 0L, outMs = 1_000L)),
            volumeKeyframes = listOf(Keyframe(100L, .2f, Curve.LINEAR))
        )
        val after = before.copy(volumeKeyframes = listOf(VolumeKeyframe("v", 100L, .8f)))
        val updated = applyLegacyAudioTrackChanges(before, after, typed)
        assertEquals(.8f, updated.volumeKeyframes.single().value, 0f)
        assertEquals(.8f, updated.clips.single().volumeKeyframes.single().value, 0f)
    }

    @Test
    fun `placement outMs survives typed and legacy synchronization`() {
        val legacy = LegacyAudioTrack(
            "a", "A", "file:///a.wav", trimStartMs = 1_000L,
            trimEndMs = 4_000L, sourceDurationMs = 10_000L, startTimeMs = 2_000L
        )
        val imported = Project("p", "p", 10_000L, audioTracks = listOf(legacy)).syncSharedTimeline()
        val typed = imported.timeline.tracks.filterIsInstance<SharedAudioTrack>().single()
        val edited = typed.copy(clips = listOf(typed.clips.single().copy(outMs = 9_500L)))
        val roundTrip = imported.copy(timeline = Timeline(listOf(edited))).syncSharedTimeline()
        assertEquals(9_500L, roundTrip.timeline.tracks.filterIsInstance<SharedAudioTrack>().single().clips.single().outMs)
        assertEquals(9_500L, roundTrip.audioTracks.single().startTimeMs +
            (roundTrip.audioTracks.single().trimEndMs - roundTrip.audioTracks.single().trimStartMs))
    }

    @Test
    fun `clip drawings import into typed layer and typed edits project back without global duplication`() {
        val clip = MediaClip("clip", "Clip", "file:///clip.mp4", 5_000L, trimEndMs = 5_000L)
        val clipLayer = VideoDrawingLayer(
            id = "clip-layer", clipId = "clip",
            strokes = listOf(VideoDrawingStroke(
                points = listOf(VideoDrawingPoint(.2f, .3f)), colorArgb = 0xFF123456L,
                tool = VideoDrawingTool.ERASER, opacity = .4f, startMs = 20L, endMs = 900L
            ))
        )
        val global = VideoDrawingLayer(id = "global-layer", clipId = null, strokes = clipLayer.strokes)
        val imported = Project("p", "p", 5_000L, clips = listOf(clip), videoDrawings = listOf(global, clipLayer))
            .syncSharedTimeline()
        val typed = imported.sharedVideoClip("clip")!!
        assertEquals("clip-layer", typed.drawingLayer!!.id)
        assertEquals("clip", typed.drawingLayer.ownerClipId)
        assertEquals(.4f, typed.drawingLayer.strokes.single().alpha, 0f)
        assertEquals(20L, typed.drawingLayer.strokes.single().startMs)
        assertEquals(1, imported.videoDrawings.count { it.clipId == null })
        assertEquals(1, imported.videoDrawings.count { it.clipId == "clip" })

        val editedTyped = typed.copy(drawingLayer = typed.drawingLayer.copy(
            strokes = typed.drawingLayer.strokes.map { it.copy(alpha = .81f, tool = com.apexstudio.app.domain.model.timeline.Tool.PEN) }
        ))
        val editedTimeline = Timeline(listOf(VideoTrack(id = "v", lane = 0, clips = listOf(editedTyped))))
        val projected = imported.copy(timeline = editedTimeline).syncSharedTimeline()
        val out = projected.videoDrawings.single { it.clipId == "clip" }
        assertEquals("clip-layer", out.id)
        assertEquals(VideoDrawingTool.BRUSH, out.strokes.single().tool)
        assertEquals(.81f, out.strokes.single().opacity, 0f)
        assertEquals(1, projected.videoDrawings.count { it.clipId == null })
    }
    @Test
    fun `audio import maps every legacy field and typed keyframes with stable identity`() {
        val legacy = LegacyAudioTrack(
            id = "music-1", name = "Original name", uri = "file:///old.wav",
            volume = .72f, isMuted = true, isSolo = true,
            volumeKeyframes = listOf(VolumeKeyframe("vk", 250L, .4f)), pan = -.3f,
            panKeyframes = listOf(Keyframe(400L, .6f, Curve.EASE_IN)),
            trimStartMs = 120L, trimEndMs = 4_200L, startTimeMs = 900L,
            sourceDurationMs = 8_000L, fadeInMs = 150L, fadeOutMs = 275L,
            kind = LegacyAudioTrack.Kind.SFX
        )
        val imported = Project("p", "p", 10_000L, audioTracks = listOf(legacy))
            .syncSharedTimeline()
        val track = imported.timeline.tracks.filterIsInstance<SharedAudioTrack>().single()
        val clip = track.clips.single()
        assertEquals("shared-audio-music-1", track.id)
        assertEquals("Original name", track.name)
        assertEquals(10, track.lane)
        assertEquals(.72f, track.volume, 0f)
        assertTrue(track.isMuted)
        assertTrue(track.isSolo)
        assertEquals(SharedAudioTrack.AudioKind.SFX, track.kind)
        assertEquals(.6f, track.panKeyframes.single().value, 0f)
        assertEquals(.4f, track.volumeKeyframes.single().value, 0f)
        assertEquals("music-1", clip.id)
        assertEquals(120L, clip.trimStartMs)
        assertEquals(4_200L, clip.trimEndMs)
        assertEquals(8_000L, clip.sourceDurationMs)
        assertEquals(150L, clip.fadeInMs)
        assertEquals(275L, clip.fadeOutMs)
        assertEquals(900L, clip.inMs)
        assertEquals(4_980L, clip.outMs)
    }

    @Test
    fun `typed audio edits project all fields back and preserve id and lane`() {
        val legacy = LegacyAudioTrack("music-1", "Old", "file:///old.wav", trimEndMs = 1_000L)
        val imported = Project("p", "p", 10_000L, audioTracks = listOf(legacy)).syncSharedTimeline()
        val originalTrack = imported.timeline.tracks.filterIsInstance<SharedAudioTrack>().single()
        val editedTrack = originalTrack.copy(
            name = "Typed name", volume = .33f, isMuted = true, isSolo = true,
            volumeKeyframes = listOf(Keyframe(90L, .22f, Curve.LINEAR)),
            pan = .8f, panKeyframes = listOf(Keyframe(120L, -.7f, Curve.EASE_OUT)),
            kind = SharedAudioTrack.AudioKind.ORIGINAL_VIDEO,
            clips = listOf(originalTrack.clips.single().copy(
                uri = Uri.parse("file:///typed.wav"), inMs = 1_500L, outMs = 3_500L,
                trimStartMs = 40L, trimEndMs = 2_040L, sourceDurationMs = 9_000L,
                fadeInMs = 60L, fadeOutMs = 70L
            ))
        )
        val projected = imported.copy(timeline = Timeline(listOf(editedTrack))).syncSharedTimeline()
        val legacyOut = projected.audioTracks.single()
        assertEquals("Typed name", legacyOut.name)
        assertEquals("file:///typed.wav", legacyOut.uri)
        assertEquals(.33f, legacyOut.volume, 0f)
        assertTrue(legacyOut.isMuted)
        assertTrue(legacyOut.isSolo)
        assertEquals(LegacyAudioTrack.Kind.ORIGINAL_VIDEO, legacyOut.kind)
        assertEquals(.22f, legacyOut.volumeKeyframes.single().volume, 0f)
        assertEquals(-.7f, legacyOut.panKeyframes.single().value, 0f)
        assertEquals(40L, legacyOut.trimStartMs)
        assertEquals(2_040L, legacyOut.trimEndMs)
        assertEquals(1_500L, legacyOut.startTimeMs)
        assertEquals(9_000L, legacyOut.sourceDurationMs)
        assertEquals(60L, legacyOut.fadeInMs)
        assertEquals(70L, legacyOut.fadeOutMs)
        val syncedAgain = projected.syncSharedTimeline()
        val stable = syncedAgain.timeline.tracks.filterIsInstance<SharedAudioTrack>().single()
        assertEquals(editedTrack.id, stable.id)
        assertEquals(editedTrack.lane, stable.lane)
        assertEquals(editedTrack.clips.single().id, stable.clips.single().id)
        assertEquals(editedTrack.volume, stable.volume, 0f)
    }

    @Test
    fun `silence split refreshes affected lanes and preserves unrelated typed audio`() {
        val ducking = Ducking(amount = .8f, attackMs = 90L, releaseMs = 240L)
        val original = LegacyAudioTrack(
            id = "music", name = "Music", uri = "file:///music.wav", volume = .7f,
            trimStartMs = 1_000L, trimEndMs = 2_000L, sourceDurationMs = 5_000L,
            startTimeMs = 100L,
            volumeKeyframes = listOf(VolumeKeyframe("v1", 1_050L, .2f)),
            panKeyframes = listOf(Keyframe(1_050L, -.4f, Curve.LINEAR))
        )
        val second = original.copy(
            id = "music-split-2", name = "Music · 2", startTimeMs = 1_100L,
            trimStartMs = 3_000L, trimEndMs = 4_000L,
            volumeKeyframes = listOf(VolumeKeyframe("v2", 1_150L, .6f)),
            panKeyframes = listOf(Keyframe(1_150L, .5f, Curve.LINEAR))
        )
        val unrelated = LegacyAudioTrack("voice", "Voice", "file:///voice.wav", trimEndMs = 900L)
        val staleMusic = SharedAudioTrack(
            id = "shared-audio-music", lane = 17,
            clips = listOf(AudioClip(id = "music", uri = Uri.parse(original.uri), inMs = 999L, outMs = 999L)),
            volume = .1f, ducking = ducking
        )
        val unrelatedTyped = SharedAudioTrack(
            id = "shared-audio-voice", lane = 23,
            clips = listOf(AudioClip(id = "voice", uri = Uri.parse(unrelated.uri), inMs = 0L, outMs = 900L)),
            volume = .42f
        )
        val synced = Project(
            "p", "p", 5_000L, audioTracks = listOf(original, second, unrelated),
            timeline = Timeline(listOf(staleMusic, unrelatedTyped))
        ).syncSharedTimeline(
            refreshLegacyAudioTrackIds = setOf(original.id, second.id),
            preservedAudioDucking = mapOf(original.id to ducking, second.id to ducking)
        )
        val typed = synced.timeline.tracks.filterIsInstance<SharedAudioTrack>()
        val first = typed.find { it.id == "shared-audio-music" }
            ?: error("missing first lane: ${typed.map { it.id }}")
        val split = typed.find { it.id == "shared-audio-${second.id}" }
            ?: error("missing split lane: ${typed.map { it.id }}")
        val voice = typed.find { it.id == "shared-audio-voice" }
            ?: error("missing unrelated lane: ${typed.map { it.id }}")

        assertEquals(17, first.lane)
        assertEquals(100L, first.clips.single().inMs)
        assertEquals(2_000L, first.clips.single().trimEndMs)
        assertEquals(listOf(1_050L), first.volumeKeyframes.map(Keyframe<Float>::timeMs))
        assertEquals(listOf(1_050L), first.panKeyframes.map(Keyframe<Float>::timeMs))
        assertEquals(ducking, first.ducking)
        assertEquals(3_000L, split.clips.single().trimStartMs)
        assertEquals(4_000L, split.clips.single().trimEndMs)
        assertEquals(listOf(1_150L), split.volumeKeyframes.map(Keyframe<Float>::timeMs))
        assertEquals(listOf(1_150L), split.panKeyframes.map(Keyframe<Float>::timeMs))
        assertEquals(ducking, split.ducking)
        assertEquals(.42f, voice.volume, 0f)
        assertEquals(23, voice.lane)
    }

    @Test
    fun `legacy masks import into typed fields and all mask kinds project`() {
        val legacy = VideoMask(
            shape = MaskShape.ELLIPSE, centerX = .31f, centerY = .42f, sizeX = .63f, sizeY = .74f,
            feather = .17f, invert = true, opacity = .61f, enabled = false, rotationDeg = 23f,
            presetId = "oval", maskKeyframes = listOf(MaskKeyframe("mk", 120L, .2f, .3f, .4f, .5f, 11f, .12f, .8f, true))
        )
        val clip = MediaClip("clip", "Clip", "file:///clip.mp4", 5_000L, trimEndMs = 5_000L, mask = legacy)
        val project = Project("p", "p", 5_000L, clips = listOf(clip))
        val imported = project.syncSharedTimeline().sharedVideoClip("clip")!!.mask as com.apexstudio.app.domain.model.timeline.Mask.Shape
        assertEquals(com.apexstudio.app.domain.model.timeline.Mask.Kind.ELLIPSE, imported.kind)
        assertEquals(legacy.centerX, imported.rect.centerX(), 0f)
        assertEquals(legacy.sizeY, imported.rect.height(), 0f)
        assertEquals(legacy.opacity, imported.opacity, 0f)
        assertEquals(legacy.enabled, imported.enabled)
        assertEquals(legacy.rotationDeg, imported.rotationDeg, 0f)
        assertEquals(legacy.presetId, imported.presetId)
        assertEquals(legacy.maskKeyframes.single().id, imported.maskKeyframes.single().id)
        val draw = VideoMask(shape = MaskShape.DRAW, drawPoints = listOf(MaskPoint(.1f, .2f), MaskPoint(.8f, .2f), MaskPoint(.5f, .9f)))
        val blur = VideoMask(shape = MaskShape.BLUR_FACE, centerX = .4f, sizeX = .2f, enabled = false)
        assertEquals(draw.drawPoints, draw.toTypedMask().toLegacyMask().drawPoints)
        assertEquals(blur, blur.toTypedMask().toLegacyMask())
        val edited = imported.copy(rect = android.graphics.RectF(.1f, .2f, .9f, .8f), opacity = .33f,
            maskKeyframes = imported.maskKeyframes.map { it.copy(rect = android.graphics.RectF(.2f, .3f, .6f, .7f), rotationDeg = 44f) })
        val editedTimeline = com.apexstudio.app.domain.model.timeline.Timeline(listOf(
            com.apexstudio.app.domain.model.timeline.VideoTrack(id = "v", lane = 0, clips = listOf(project.sharedVideoClip("clip")!!.copy(mask = edited)))
        ))
        val projected = project.copy(timeline = editedTimeline).syncSharedTimeline().clips.single().mask!!
        assertEquals(.5f, projected.centerX, 0f)
        assertEquals(.33f, projected.opacity, 0f)
        assertEquals(44f, projected.maskKeyframes.single().rotationDeg, 0f)
        assertTrue(projected.enabled.not())
    }
    @Test
    fun `typed text edits project all persisted style fields to legacy output`() {
        val clip = MediaClip(
            id = "clip", name = "Clip", uri = "file:///clip.mp4", durationMs = 5_000L,
            trimEndMs = 5_000L,
            textOverlays = listOf(TextOverlay(id = "title", text = "legacy"))
        )
        val typed = TextItem(
            id = "clip:title", text = "typed", position = XY(0.2f, 0.3f), scale = 1.5f,
            color = Color.Red, bgArgb = 0xFF112233L, strokeColorArgb = 0xFF445566L,
            shadowColorArgb = 0xFF778899L, fontFamily = "serif", isBold = false, isItalic = true,
            presetId = "caption", animationType = "SLIDE_UP", animationDurationMs = 321L,
            outroAnimationType = "FADE_OUT", outroAnimationDurationMs = 222L,
            textAlign = "LEFT", letterSpacingEm = 0.2f, gradientStartArgb = 0xFFFF0000L,
            gradientEndArgb = 0xFF00FF00L, highlightStartUtf16 = 1, highlightEndUtf16 = 3,
            highlightSourceFingerprint = "fp", highlightColorArgb = 0xFFABCDEF,
            animationWindowStartMs = 10L, animationWindowEndMs = 900L,
            projectTextLayerId = "layer", rangeEndExclusive = true, startMs = 10L, endMs = 900L
        )
        val project = Project(
            id = "p", name = "p", durationMs = 5_000L, clips = listOf(clip),
            timeline = Timeline(listOf(TextTrack(id = "texts", lane = 1, items = listOf(typed))))
        )

        val projected = project.syncSharedTimeline()
        val legacy = projected.clips.single().textOverlays.single()
        assertEquals("typed", legacy.text)
        assertEquals(0.2f, legacy.x, 0f)
        assertEquals(1.5f, legacy.sizeScale, 0f)
        assertEquals(0xFFFF0000L, legacy.colorArgb)
        assertEquals(0xFF112233L, legacy.bgArgb)
        assertEquals("serif", legacy.fontFamily)
        assertFalse(legacy.isBold)
        assertEquals("SLIDE_UP", legacy.animationType)
        assertEquals(321L, legacy.animationDurationMs)
        assertEquals("layer", legacy.projectTextLayerId)
        assertEquals(true, legacy.rangeEndExclusive)
    }

    @Test
    fun `typed sticker edits project symbol metadata geometry and asset fields`() {
        val project = Project(
            id = "p", name = "p", durationMs = 5_000L,
            stickers = listOf(StickerOverlay(id = "sticker", symbolOrUri = "old")),
            timeline = Timeline(listOf(StickerTrack(id = "stickers", lane = 1, items = listOf(
                StickerItem(
                    id = "sticker", symbolOrUri = "new", category = "Shapes", name = "Circle",
                    position = XY(0.1f, 0.8f), scale = 2f, rotationDeg = 12f, alpha = 0.6f,
                    assetPath = "shapes/circle.webp", cropLeft = 0.1f, cropTop = 0.2f,
                    cropRight = 0.9f, cropBottom = 0.8f, cutoutShape = "OVAL",
                    startMs = 100L, endMs = 900L
                )
            ))))
        )

        val legacy = project.syncSharedTimeline().stickers.single()
        assertEquals("new", legacy.symbolOrUri)
        assertEquals("Shapes", legacy.category)
        assertEquals("Circle", legacy.name)
        assertEquals(0.1f, legacy.x, 0f)
        assertEquals(0.8f, legacy.y, 0f)
        assertEquals("shapes/circle.webp", legacy.assetPath)
        assertEquals(0.1f, legacy.cropLeft, 0f)
        assertEquals("OVAL", legacy.cutoutShape)
        assertEquals(100L, legacy.startMs)
    }

    @Test
    fun `video metadata and variable speed survive import typed edit sync and projection`() {
        val points = listOf(SpeedPoint(0f, .5f), SpeedPoint(.4f, 2f, sharp = true), SpeedPoint(1f, 1.25f))
        val legacy = MediaClip(
            id = "video", name = "Source", uri = "file:///source.mp4", durationMs = 12_000L,
            trimStartMs = 1_000L, trimEndMs = 9_000L, thumbnail = "thumb://old", trackIndex = 0,
            groupId = "group-a", speedMultiplier = 1.5f, speedCurve = SpeedCurve.RAMP,
            rampStartSpeed = .5f, rampEndSpeed = 1.25f, speedPoints = points
        )
        val imported = Project("p", "p", 20_000L, clips = listOf(legacy)).syncSharedTimeline()
        val typed = imported.sharedVideoClip("video")!!
        assertEquals("Source", typed.sourceName)
        assertEquals(12_000L, typed.sourceDurationMs)
        assertEquals(9_000L, typed.trimEndMs)
        assertEquals("thumb://old", typed.thumbnail)
        assertEquals("group-a", typed.groupId)
        assertEquals(points, typed.speedPoints)
        val edited = typed.copy(
            sourceName = "Typed source", sourceDurationMs = 15_000L, trimStartMs = 2_000L,
            trimEndMs = 11_000L, thumbnail = "thumb://typed", groupId = "group-typed",
            speed = 2f, speedCurve = SpeedCurve.EASE_IN, rampStartSpeed = .75f,
            rampEndSpeed = 2f, speedPoints = listOf(SpeedPoint(0f, .75f), SpeedPoint(1f, 2f))
        )
        val projected = imported.copy(timeline = Timeline(listOf(VideoTrack(id = "lane", lane = 0, clips = listOf(edited))))).syncSharedTimeline()
        val out = projected.clips.single()
        assertEquals("Typed source", out.name)
        assertEquals(15_000L, out.durationMs)
        assertEquals(2_000L, out.trimStartMs)
        assertEquals(11_000L, out.trimEndMs)
        assertEquals("thumb://typed", out.thumbnail)
        assertEquals("group-typed", out.groupId)
        assertEquals(SpeedCurve.EASE_IN, out.speedCurve)
        assertEquals(edited.speedPoints, out.speedPoints)
        assertEquals("video", projected.sharedVideoClip("video")!!.id)
    }

    @Test
    fun `image and overlay video metadata preserve identity trim lane group and ids`() {
        val image = MediaClip(
            id = "image", name = "Photo", uri = "file:///photo.jpg", durationMs = 6_000L,
            trimStartMs = 100L, trimEndMs = 5_000L, thumbnail = "thumb://photo", type = ClipType.IMAGE,
            trackIndex = 2, timelineOffsetMs = 300L, groupId = "image-group"
        )
        val overlay = MediaClip(
            id = "overlay", name = "PiP", uri = "file:///pip.mp4", durationMs = 8_000L,
            trimStartMs = 500L, trimEndMs = 7_000L, thumbnail = "thumb://pip", type = ClipType.OVERLAY,
            trackIndex = 3, timelineOffsetMs = 400L, groupId = "overlay-group",
            speedCurve = SpeedCurve.RAMP, speedPoints = listOf(SpeedPoint(0f, .5f), SpeedPoint(1f, 1.5f))
        )
        val imported = Project("p", "p", 20_000L, clips = listOf(image, overlay)).syncSharedTimeline()
        val imageItem = imported.timeline.tracks.filterIsInstance<OverlayTrack>().single { it.lane == 2 }
            .items.single() as OverlayItem.Image
        assertEquals("image", imageItem.id)
        assertEquals(6_000L, imageItem.sourceDurationMs)
        assertEquals(5_000L, imageItem.trimEndMs)
        assertEquals("image-group", imageItem.groupId)
        val overlayItem = imported.timeline.tracks.filterIsInstance<OverlayTrack>().single { it.lane == 3 }
            .items.single() as OverlayItem.Video
        assertEquals("overlay", overlayItem.id)
        assertEquals("PiP", overlayItem.sourceName)
        assertEquals("overlay-group", overlayItem.groupId)
        assertEquals(overlay.speedPoints, overlayItem.speedPoints)
        val editedImage = imageItem.copy(sourceName = "Typed photo", sourceDurationMs = 7_000L, groupId = "typed-image-group")
        val editedOverlay = overlayItem.copy(sourceName = "Typed PiP", trimStartMs = 900L, trimEndMs = 6_500L, groupId = "typed-overlay-group")
        val editedTimeline = Timeline(listOf(
            OverlayTrack(id = "images", lane = 2, items = listOf(editedImage)),
            OverlayTrack(id = "overlays", lane = 3, items = listOf(editedOverlay))
        ))
        val projected = imported.copy(timeline = editedTimeline).syncSharedTimeline()
        assertEquals("Typed photo", projected.clips.first { it.id == "image" }.name)
        assertEquals("typed-image-group", projected.clips.first { it.id == "image" }.groupId)
        assertEquals("Typed PiP", projected.clips.first { it.id == "overlay" }.name)
        assertEquals(900L, projected.clips.first { it.id == "overlay" }.trimStartMs)
        assertEquals(6_500L, projected.clips.first { it.id == "overlay" }.trimEndMs)
        assertEquals("typed-overlay-group", projected.clips.first { it.id == "overlay" }.groupId)
        assertEquals(3, projected.timeline.tracks.filterIsInstance<OverlayTrack>().single { it.lane == 3 }.lane)
    }

    @Test
    fun `legacy refresh imports direct edits removes deleted overlays and retains typed-only items`() {
        val clip = MediaClip(
            id = "clip", name = "Clip", uri = "file:///clip.mp4", durationMs = 5_000L,
            trimEndMs = 5_000L,
            textOverlays = listOf(TextOverlay(id = "keep", text = "before"), TextOverlay(id = "gone", text = "gone"))
        )
        val project = Project(
            id = "p", name = "p", durationMs = 5_000L, clips = listOf(clip),
            stickers = listOf(StickerOverlay(id = "keep-sticker", symbolOrUri = "before"), StickerOverlay(id = "gone-sticker")),
            audioTracks = listOf(LegacyAudioTrack(
                id = "audio", name = "Music", uri = "file:///music.wav", volume = .75f,
                trimEndMs = 5_000L, sourceDurationMs = 5_000L
            )),
            videoDrawings = listOf(VideoDrawingLayer(
                id = "drawing", clipId = "clip",
                strokes = listOf(VideoDrawingStroke(points = listOf(VideoDrawingPoint(.2f, .3f)), opacity = .4f))
            ))
        )
        val imported = project.syncSharedTimeline(importLegacyAnimation = true)
        val textKeys = listOf(Keyframe(250L, .4f, Curve.LINEAR))
        val stickerKeys = listOf(Keyframe(300L, 1.4f, Curve.EASE_IN))
        val typedOnlyText = TextItem(id = "typed-only-text", text = "Typed")
        val typedOnlySticker = StickerItem(id = "typed-only-sticker", symbolOrUri = "typed")
        val editedTimeline = Timeline(imported.timeline.tracks.map { track ->
            when (track) {
                is TextTrack -> track.copy(items = track.items.map {
                    if (it.id == "clip:keep") it.copy(alphaKeyframes = textKeys) else it
                } + typedOnlyText)
                is StickerTrack -> track.copy(items = track.items.map {
                    if (it.id == "keep-sticker") it.copy(rotationKeyframes = stickerKeys) else it
                } + typedOnlySticker)
                else -> track
            }
        })
        val legacyClip = imported.clips.single().copy(
            timelineOffsetMs = 350L,
            textOverlays = imported.clips.single().textOverlays
                .filterNot { it.id == "gone" }
                .map { if (it.id == "keep") it.copy(text = "after") else it }
        )
        val current = imported.copy(
            clips = listOf(legacyClip),
            stickers = imported.stickers.filterNot { it.id == "gone-sticker" }
                .map { it.copy(symbolOrUri = "after") },
            audioTracks = imported.audioTracks.map { it.copy(name = "Updated music", volume = .31f, pan = -.6f) },
            videoDrawings = imported.videoDrawings.map { layer ->
                layer.copy(strokes = layer.strokes.map { it.copy(opacity = .9f) })
            },
            timeline = editedTimeline
        )

        val synced = current.syncSharedTimeline(
            importLegacyAnimation = true,
            refreshLegacyClipFields = true,
            refreshLegacyAudioFields = true,
            refreshLegacyDrawingClipIds = setOf("clip"),
            refreshLegacyTextStickerFields = true
        )

        assertEquals(350L, synced.sharedVideoClip("clip")!!.inMs)
        assertEquals(.9f, synced.sharedVideoClip("clip")!!.drawingLayer!!.strokes.single().alpha, 0f)
        val audio = synced.timeline.tracks.filterIsInstance<SharedAudioTrack>().single()
        assertEquals("Updated music", audio.name)
        assertEquals(.31f, audio.volume, 0f)
        assertEquals(-.6f, audio.pan, 0f)

        val textItems = synced.timeline.tracks.filterIsInstance<TextTrack>().single().items
        assertEquals(setOf("clip:keep", "typed-only-text"), textItems.map { it.id }.toSet())
        assertEquals("after", textItems.single { it.id == "clip:keep" }.text)
        assertEquals(textKeys, textItems.single { it.id == "clip:keep" }.alphaKeyframes)
        assertTrue(textItems.single { it.id == "clip:keep" }.legacyBacked)

        val stickerItems = synced.timeline.tracks.filterIsInstance<StickerTrack>().single().items
        assertEquals(setOf("keep-sticker", "typed-only-sticker"), stickerItems.map { it.id }.toSet())
        assertEquals("after", stickerItems.single { it.id == "keep-sticker" }.symbolOrUri)
        assertEquals(stickerKeys, stickerItems.single { it.id == "keep-sticker" }.rotationKeyframes)
        assertTrue(stickerItems.single { it.id == "keep-sticker" }.legacyBacked)
    }
}
