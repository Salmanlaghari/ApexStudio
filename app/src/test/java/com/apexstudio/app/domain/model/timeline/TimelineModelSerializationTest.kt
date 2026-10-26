package com.apexstudio.app.domain.model.timeline

import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimelineModelSerializationTest {
    @Test
    fun `timeline round trips every track family and platform values through project json`() {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(1f, 1f)
        }
        val drawing = DrawingLayer(
            strokes = listOf(
                Stroke(
                    points = listOf(Offset(0.1f, 0.2f), Offset(0.8f, 0.9f)),
                    color = Color(0.2f, 0.4f, 0.6f, 0.8f),
                    width = 0.01f,
                    tool = Tool.PEN,
                    startMs = 20L,
                    alphaKeyframes = listOf(Keyframe(20L, 0.25f, Curve.LINEAR), Keyframe(80L, 1f))
                )
            ),
            globalAlphaKeyframes = listOf(Keyframe(0L, 0.5f, Curve.LINEAR), Keyframe(100L, 1f))
        )
        val video = VideoClip(
            id = "video-clip",
            uri = Uri.parse("file:///video.mp4"),
            inMs = 0L,
            outMs = 1_000L,
            transform = Transform(positionKeyframes = listOf(Keyframe(0L, XY(0f, 0f)), Keyframe(1_000L, XY(1f, 1f)))),
            filter = "warm",
            filterKeyframes = listOf(Keyframe(0L, 0.2f), Keyframe(1_000L, 0.8f)),
            mask = Mask.Shape(
                kind = Mask.Kind.CIRCLE,
                rect = RectF(0.1f, 0.2f, 0.8f, 0.9f),
                keyframes = listOf(Keyframe(0L, RectF(0f, 0f, 1f, 1f)))
            ),
            drawingLayer = drawing
        )
        val timeline = Timeline(
            tracks = listOf(
                VideoTrack(id = "video", lane = 0, clips = listOf(video)),
                AudioTrack(
                    id = "audio", lane = 1,
                    clips = listOf(AudioClip(id = "audio-clip", uri = Uri.parse("file:///voice.wav"), inMs = 0L, outMs = 500L)),
                    panKeyframes = listOf(Keyframe(0L, -1f, Curve.LINEAR), Keyframe(500L, 1f))
                ),
                TextTrack(
                    id = "text", lane = 2,
                    items = listOf(
                        TextItem(
                            id = "title",
                            text = "Title",
                            colorKeyframes = listOf(Keyframe(0L, Color.Red, Curve.LINEAR), Keyframe(100L, Color.Blue))
                        )
                    )
                ),
                StickerTrack(id = "stickers", lane = 3, items = listOf(StickerItem(id = "sticker", symbolOrUri = "star"))),
                OverlayTrack(
                    id = "overlay", lane = 4,
                    items = listOf(OverlayItem.Image(id = "image", uri = Uri.parse("file:///overlay.png"), inMs = 0L, outMs = 100L))
                ),
                OverlayTrack(id = "mask-overlay", lane = 5, items = listOf(OverlayItem.Video(
                    id = "overlay-video", uri = Uri.parse("file:///overlay.mp4"), inMs = 0L, outMs = 100L,
                    mask = Mask.Pen(path = path)
                )))
            )
        )

        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val encoded = json.encodeToString(Timeline.serializer(), timeline)
        val restored = json.decodeFromString(Timeline.serializer(), encoded)

        assertEquals(6, restored.tracks.size)
        val restoredVideo = (restored.tracks[0] as VideoTrack).clips.single()
        assertEquals(Uri.parse("file:///video.mp4"), restoredVideo.uri)
        assertEquals(0.8f, restoredVideo.filterKeyframes.last().value, 0f)
        assertEquals(XY(0.5f, 0.5f), restoredVideo.transform.positionAt(500L))
        assertEquals(0.5f, restoredVideo.filterIntensityAt(500L), 0.0001f)
        val restoredMask = restoredVideo.mask as Mask.Shape
        assertEquals(0.1f, restoredMask.rect.left, 0f)
        assertEquals(1f, restoredMask.keyframes.single().value.right, 0f)
        val restoredStroke = restoredVideo.drawingLayer!!.strokes.single()
        assertEquals(2, restoredStroke.points.size)
        assertEquals(0.6f, restoredStroke.color.blue, 0.0001f)
        assertTrue(restoredStroke.points.first() == Offset(0.1f, 0.2f))
        assertFalse((restoredVideo.mask as Mask.Shape).rect.isEmpty)
        assertEquals(0f, (restored.tracks[1] as AudioTrack).panAt(250L), 0.0001f)
        val restoredText = (restored.tracks[2] as TextTrack).items.single()
        assertEquals(Color.Red, restoredText.colorKeyframes.first().value)
        val middleTextColor = restoredText.colorAt(50L)
        assertEquals(0.5f, middleTextColor.red, 0.003f)
        assertEquals(0.5f, middleTextColor.blue, 0.003f)
        val restoredPen = ((restored.tracks[5] as OverlayTrack).items.single() as OverlayItem.Video).mask as Mask.Pen
        assertFalse(restoredPen.path.isEmpty)
        assertTrue(restored.tracks[5] is OverlayTrack)

        val project = com.apexstudio.app.domain.model.Project(
            id = "timeline-project", name = "Timeline project", durationMs = 1_000L,
            timeline = restored,
            hiddenVideoLayers = setOf(2),
            lockedVideoLayers = setOf(3),
            signature = com.apexstudio.app.domain.model.Signature(
                text = "@apex",
                logoUri = Uri.parse("file:///signature.png"),
                position = com.apexstudio.app.domain.model.SignatureCorner.TOP_LEFT,
                fontSize = 22f,
                color = Color(0.2f, 0.4f, 0.6f, 0.8f),
                alpha = 0.7f,
                marginDp = 18f,
                alwaysOn = true
            )
        )
        val projectJson = json.encodeToString(com.apexstudio.app.domain.model.Project.serializer(), project)
        val restoredProject = json.decodeFromString<com.apexstudio.app.domain.model.Project>(projectJson)
        assertEquals(6, restoredProject.timeline.tracks.size)
        assertEquals(Uri.parse("file:///video.mp4"), (restoredProject.timeline.tracks[0] as VideoTrack).clips.single().uri)
        assertEquals(setOf(2), restoredProject.hiddenVideoLayers)
        assertEquals(setOf(3), restoredProject.lockedVideoLayers)
        assertEquals(Uri.parse("file:///signature.png"), restoredProject.signature?.logoUri)
        assertEquals("@apex", restoredProject.signature?.text)
        assertEquals(0.6f, restoredProject.signature?.color?.blue ?: 0f, 0.001f)
        assertTrue(restoredProject.signature?.alwaysOn == true)
    }
}
