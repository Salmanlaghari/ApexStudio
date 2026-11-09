package com.apexstudio.app.data.repository

import android.content.Context
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.apexstudio.app.domain.model.AudioTrack as LegacyAudioTrack
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.GpuFilterConfig
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.domain.model.Signature
import com.apexstudio.app.domain.model.VideoDrawingLayer
import com.apexstudio.app.domain.model.VideoDrawingPoint
import com.apexstudio.app.domain.model.VideoDrawingStroke
import com.apexstudio.app.domain.model.timeline.VideoTrack
import com.apexstudio.app.domain.model.timeline.AudioTrack as SharedAudioTrack
import com.apexstudio.app.domain.model.timeline.BlendMode
import com.apexstudio.app.domain.model.timeline.ChromaKey
import com.apexstudio.app.domain.model.timeline.Curve
import com.apexstudio.app.domain.model.timeline.Ducking
import com.apexstudio.app.domain.model.timeline.DrawingLayer
import com.apexstudio.app.domain.model.timeline.Keyframe
import com.apexstudio.app.domain.model.timeline.Mask
import com.apexstudio.app.domain.model.timeline.MaskKeyframe
import com.apexstudio.app.domain.model.timeline.OverlayItem
import com.apexstudio.app.domain.model.timeline.OverlayTrack
import com.apexstudio.app.domain.model.timeline.Stroke
import com.apexstudio.app.domain.model.timeline.Timeline
import com.apexstudio.app.domain.model.timeline.Tool
import com.apexstudio.app.domain.model.timeline.XY
import com.apexstudio.app.domain.model.timeline.syncSharedTimeline
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProjectRepositoryMigrationTest {
    @Test
    fun `legacy v0 json migrates to typed timeline without changing timing or global project data`() {
        val legacy = legacyProject("legacy-v0").copy(timingModelVersion = 0)
        val codec = Json { encodeDefaults = true }
        val encoded = codec.encodeToJsonElement(ListSerializer(Project.serializer()), listOf(legacy)) as JsonArray
        val legacyObject = JsonObject(encoded.single().jsonObject - "timeline" - "timelineAuthorityVersion")
        val repository = ProjectRepository(ApplicationProvider.getApplicationContext<Context>())

        val migrated = repository.decode(JsonArray(listOf(legacyObject)).toString()).single()

        assertEquals(Project.CURRENT_TIMELINE_AUTHORITY_VERSION, migrated.timelineAuthorityVersion)
        assertEquals(0, migrated.timingModelVersion)
        assertEquals("clip", migrated.timeline.tracks.filterIsInstance<VideoTrack>().single().clips.single().id)
        assertEquals(legacy.videoDrawings, migrated.videoDrawings)
        assertNull(migrated.timeline.tracks.filterIsInstance<VideoTrack>().single().clips.single().drawingLayer)
        assertEquals(legacy.signature, migrated.signature)

        val reopened = repository.decode(codec.encodeToString(ListSerializer(Project.serializer()), listOf(migrated))).single()
        assertEquals(migrated, reopened)
    }

    @Test
    fun `save and reopen persists current typed timeline authority`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ProjectRepository(context)
        val id = "migration-reopen-${UUID.randomUUID()}"
        val legacy = legacyProject(id)

        try {
            repository.saveProject(legacy)
            val reopened = ProjectRepository(context).findById(id)

            assertNotNull(reopened)
            assertEquals(Project.CURRENT_TIMELINE_AUTHORITY_VERSION, reopened!!.timelineAuthorityVersion)
            assertTrue(reopened.timeline.tracks.filterIsInstance<VideoTrack>().single().clips.any { it.id == "clip" })
        } finally {
            repository.deleteProject(id)
        }
    }

    @Test
    fun `typed-only keyframes and visual fields survive repository save and reopen`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ProjectRepository(context)
        val id = "typed-authority-${UUID.randomUUID()}"
        try {
            repository.saveProject(legacyProject(id))
            val loaded = repository.findById(id)!!
            val track = loaded.timeline.tracks.filterIsInstance<VideoTrack>().single()
            val scaleKeys = listOf(Keyframe(0L, 1f), Keyframe(1_000L, 1.7f, Curve.EASE_IN))
            val updatedClip = track.clips.single().copy(
                transform = track.clips.single().transform.copy(scaleKeyframes = scaleKeys),
                gpuFilterConfig = GpuFilterConfig(brightness = .25f, hue = 12f)
            )
            val edited = loaded.copy(timeline = Timeline(loaded.timeline.tracks.map { current ->
                if (current is VideoTrack) current.copy(clips = listOf(updatedClip)) else current
            }))

            repository.saveProject(edited)
            val reopened = ProjectRepository(context).findById(id)!!
            val savedClip = reopened.timeline.tracks.filterIsInstance<VideoTrack>().single().clips.single()

            assertEquals(scaleKeys, savedClip.transform.scaleKeyframes)
            assertEquals(GpuFilterConfig(brightness = .25f, hue = 12f), savedClip.gpuFilterConfig)
        } finally {
            repository.deleteProject(id)
        }
    }

    @Test
    fun `new visual audio and signature fields survive app save and reopen`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ProjectRepository(context)
        val id = "full-timeline-reopen-${UUID.randomUUID()}"
        val image = MediaClip(
            id = "overlay-image", name = "Overlay", uri = "file:///overlay.png",
            durationMs = 5_000L, trimEndMs = 5_000L, type = ClipType.IMAGE, trackIndex = 2
        )
        val legacy = legacyProject(id).copy(
            clips = legacyProject(id).clips + image,
            audioTracks = listOf(LegacyAudioTrack(
                id = "music", name = "Music", uri = "file:///music.wav",
                trimEndMs = 5_000L, sourceDurationMs = 5_000L
            )),
            signature = Signature(text = "@studio", alwaysOn = true)
        )

        try {
            repository.saveProject(legacy)
            val loaded = repository.findById(id)!!
            val rectKeys = listOf(
                Keyframe(0L, RectF(.1f, .1f, .7f, .8f)),
                Keyframe(1_000L, RectF(.2f, .2f, .8f, .9f), Curve.EASE_IN)
            )
            val maskKeys = listOf(
                MaskKeyframe("mask-kf", 500L, RectF(.15f, .2f, .75f, .85f), rotationDeg = 18f,
                    feather = .2f, opacity = .7f, inverted = true, curve = Curve.EASE_OUT)
            )
            val mask = Mask.Shape(
                kind = Mask.Kind.ELLIPSE, rect = RectF(.1f, .1f, .7f, .8f), feather = .15f,
                keyframes = rectKeys, opacity = .8f, rotationDeg = 12f, maskKeyframes = maskKeys
            )
            val chroma = ChromaKey(
                tolerance = .27f, softness = .12f, spill = .3f, color = Color.Green,
                enabled = true, depth3D = .65f, tiltX = .2f, tiltY = -.1f,
                backgroundType = "matrix_grid", customBackgroundUri = "file:///background.png"
            )
            val drawing = DrawingLayer(
                id = "typed-drawing", ownerClipId = "clip", globalAlpha = .8f,
                globalAlphaKeyframes = listOf(Keyframe(0L, 1f), Keyframe(1_000L, .5f, Curve.EASE_OUT)),
                strokes = listOf(Stroke(
                    points = listOf(Offset(.1f, .2f), Offset(.8f, .9f)), color = Color.Yellow,
                    width = 3f, tool = Tool.PEN, startMs = 100L, alpha = .75f,
                    alphaKeyframes = listOf(Keyframe(100L, 1f), Keyframe(900L, .4f, Curve.SMOOTH))
                ))
            )
            val floatKeys = listOf(Keyframe(0L, .2f), Keyframe(1_000L, .9f, Curve.EASE_OUT))
            val videoBase = loaded.timeline.tracks.filterIsInstance<VideoTrack>().single().clips.single()
            val editedVideo = videoBase.copy(
                filter = "warm", filterIntensity = .7f, filterKeyframes = floatKeys,
                effectIntensity = .6f, effectIntensityKeyframes = floatKeys,
                lutId = "studio-lut", lutIntensity = .8f, lutKeyframes = floatKeys,
                saturation = .85f, saturationKeyframes = floatKeys,
                mask = mask, chromaKey = chroma, drawingLayer = drawing, blendMode = BlendMode.SCREEN
            )
            val imageTrack = loaded.timeline.tracks.filterIsInstance<OverlayTrack>().single()
            val imageBase = imageTrack.items.single() as OverlayItem.Image
            val editedImage = imageBase.copy(
                filter = "overlay-warm", filterIntensity = .65f, filterKeyframes = floatKeys,
                effectIntensity = .55f, effectIntensityKeyframes = floatKeys,
                lutId = "overlay-lut", lutIntensity = .75f, lutKeyframes = floatKeys,
                saturation = .9f, saturationKeyframes = floatKeys,
                mask = mask, chromaKey = chroma, drawingLayer = drawing, blendMode = BlendMode.MULTIPLY
            )
            val audioTrack = loaded.timeline.tracks.filterIsInstance<SharedAudioTrack>().single()
            val panKeys = listOf(Keyframe(0L, -.5f), Keyframe(1_000L, .5f, Curve.SMOOTH))
            val editedAudio = audioTrack.copy(
                volumeKeyframes = floatKeys, pan = .25f, panKeyframes = panKeys,
                ducking = Ducking(amount = .4f, attackMs = 80L, releaseMs = 280L)
            )
            val editedTimeline = Timeline(loaded.timeline.tracks.map { track ->
                when (track) {
                    is VideoTrack -> track.copy(clips = listOf(editedVideo))
                    is OverlayTrack -> track.copy(items = listOf(editedImage))
                    is SharedAudioTrack -> editedAudio
                    else -> track
                }
            })
            val signature = Signature(text = "@studio", color = Color.Cyan, alpha = .65f, alwaysOn = true)

            repository.saveProject(loaded.copy(timeline = editedTimeline, signature = signature))
            val reopened = ProjectRepository(context).findById(id)!!
            val savedVideo = reopened.timeline.tracks.filterIsInstance<VideoTrack>().single().clips.single()
            val savedImage = reopened.timeline.tracks.filterIsInstance<OverlayTrack>().single().items.single() as OverlayItem.Image
            val savedAudio = reopened.timeline.tracks.filterIsInstance<SharedAudioTrack>().single()

            assertEquals(Project.CURRENT_TIMELINE_AUTHORITY_VERSION, reopened.timelineAuthorityVersion)
            assertEquals(floatKeys, savedVideo.filterKeyframes)
            assertEquals(mask, savedVideo.mask)
            assertEquals(chroma, savedVideo.chromaKey)
            assertEquals(drawing, savedVideo.drawingLayer)
            assertEquals(BlendMode.SCREEN, savedVideo.blendMode)
            assertEquals(floatKeys, savedImage.filterKeyframes)
            assertEquals(mask, savedImage.mask)
            assertEquals(chroma, savedImage.chromaKey)
            assertEquals(drawing, savedImage.drawingLayer)
            assertEquals(BlendMode.MULTIPLY, savedImage.blendMode)
            assertEquals(panKeys, savedAudio.panKeyframes)
            assertEquals(Ducking(amount = .4f, attackMs = 80L, releaseMs = 280L), savedAudio.ducking)
            assertEquals(signature, reopened.signature)
        } finally {
            repository.deleteProject(id)
        }
    }

    @Test
    fun `refreshed legacy clip edits survive v1 save and reopen`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ProjectRepository(context)
        val id = "migration-edit-${UUID.randomUUID()}"

        try {
            repository.saveProject(legacyProject(id))
            val loaded = repository.findById(id)!!
            val edited = loaded.copy(
                clips = loaded.clips.map { it.copy(timelineOffsetMs = 350L) }
            ).syncSharedTimeline(
                refreshLegacyClipFields = true
            )
            repository.saveProject(edited)

            val reopened = ProjectRepository(context).findById(id)!!
            val typedClip = reopened.timeline.tracks.filterIsInstance<VideoTrack>().single().clips.single()
            assertEquals(350L, typedClip.inMs)
            assertEquals(350L, reopened.clips.single().timelineOffsetMs)
            assertEquals(Project.CURRENT_TIMELINE_AUTHORITY_VERSION, reopened.timelineAuthorityVersion)
        } finally {
            repository.deleteProject(id)
        }
    }

    private fun legacyProject(id: String): Project {
        val globalDrawing = VideoDrawingLayer(
            id = "global-art",
            clipId = null,
            strokes = listOf(VideoDrawingStroke(points = listOf(VideoDrawingPoint(0.2f, 0.3f))))
        )
        return Project(
            id = id,
            name = "Legacy project",
            durationMs = 5_000L,
            clips = listOf(
                MediaClip(
                    id = "clip",
                    name = "Clip",
                    uri = "file:///clip.mp4",
                    durationMs = 5_000L,
                    trimEndMs = 5_000L
                )
            ),
            videoDrawings = listOf(globalDrawing),
            signature = Signature(text = "@studio"),
            timelineAuthorityVersion = 0
        )
    }
}
