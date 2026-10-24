package com.apexstudio.app.domain.model.timeline

import android.graphics.RectF
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimelineKeyframeEditingTest {
    private fun videoClip() = VideoClip(
        id = "video",
        uri = Uri.parse("file:///video.mp4"),
        inMs = 0L,
        outMs = 1_000L
    )

    @Test
    fun `add keyframe samples every typed property at playhead`() {
        val clip = videoClip().copy(
            transform = Transform(
                position = XY(0f, 0f),
                scale = 1f,
                positionKeyframes = listOf(
                    Keyframe(0L, XY(0f, 0f), Curve.LINEAR),
                    Keyframe(1_000L, XY(10f, 20f), Curve.LINEAR)
                ),
                scaleKeyframes = listOf(Keyframe(0L, 1f, Curve.LINEAR), Keyframe(1_000L, 3f, Curve.LINEAR))
            ),
            opacity = 1f,
            opacityKeyframes = listOf(Keyframe(0L, 1f, Curve.LINEAR), Keyframe(1_000L, 0f, Curve.LINEAR)),
            volume = 0.8f,
            volumeKeyframes = listOf(Keyframe(0L, 0.8f, Curve.LINEAR), Keyframe(1_000L, 0.4f, Curve.LINEAR)),
            filterIntensity = 0.2f,
            filterKeyframes = listOf(Keyframe(0L, 0.2f, Curve.LINEAR), Keyframe(1_000L, 0.6f, Curve.LINEAR)),
            effectIntensity = 0.1f,
            effectIntensityKeyframes = listOf(Keyframe(0L, 0.1f, Curve.LINEAR), Keyframe(1_000L, 0.9f, Curve.LINEAR)),
            lutIntensity = 0.3f,
            lutKeyframes = listOf(Keyframe(0L, 0.3f, Curve.LINEAR), Keyframe(1_000L, 0.7f, Curve.LINEAR)),
            saturation = 1f,
            saturationKeyframes = listOf(Keyframe(0L, 1f, Curve.LINEAR), Keyframe(1_000L, 0f, Curve.LINEAR))
        )

        val added = clip.addAnimationKeyframe(500L)
        val sampled = clip.animationAt(500L)

        assertEquals(XY(5f, 10f), added.transform.positionKeyframes.single { it.timeMs == 500L }.value)
        assertEquals(sampled.scale, added.transform.scaleKeyframes.single { it.timeMs == 500L }.value, 0f)
        assertEquals(sampled.opacity, added.opacityKeyframes.single { it.timeMs == 500L }.value, 0f)
        assertEquals(sampled.volume, added.volumeKeyframes.single { it.timeMs == 500L }.value, 0f)
        assertEquals(sampled.filterIntensity, added.filterKeyframes.single { it.timeMs == 500L }.value, 0f)
        assertEquals(sampled.effectIntensity, added.effectIntensityKeyframes.single { it.timeMs == 500L }.value, 0f)
        assertEquals(sampled.lutIntensity, added.lutKeyframes.single { it.timeMs == 500L }.value, 0f)
        assertEquals(sampled.saturation, added.saturationKeyframes.single { it.timeMs == 500L }.value, 0f)
        assertEquals(Curve.SMOOTH, added.curveAt(500L))
    }

    @Test
    fun `property edit keeps other tracks intact and delete preserves neighboring keys`() {
        val clip = videoClip().copy(
            transform = Transform(
                positionKeyframes = listOf(Keyframe(0L, XY(0f, 0f)), Keyframe(1_000L, XY(10f, 10f))),
                scaleKeyframes = listOf(Keyframe(0L, 1f), Keyframe(1_000L, 2f))
            ),
            opacityKeyframes = listOf(Keyframe(0L, 1f), Keyframe(1_000L, 0f))
        )

        val edited = clip.upsertAnimationValue(VideoAnimationProperty.SCALE, 500L, 4f)
        assertEquals(4f, edited.transform.scaleAt(500L), 0f)
        assertEquals(0.5f, edited.opacityAt(500L), 0.0001f)
        assertEquals(listOf(0L, 500L, 1_000L), edited.animationKeyframeTimes())

        val removed = edited.removeAnimationKeyframeAt(500L)
        assertEquals(listOf(0L, 1_000L), removed.transform.scaleKeyframes.map(Keyframe<Float>::timeMs))
        assertEquals(listOf(0L, 1_000L), removed.opacityKeyframes.map(Keyframe<Float>::timeMs))
    }

    @Test
    fun `move keyframe preserves independent properties and replaces same-time destination`() {
        val clip = videoClip().copy(
            transform = Transform(positionKeyframes = listOf(
                Keyframe(100L, XY(1f, 2f), Curve.EASE_IN),
                Keyframe(500L, XY(5f, 6f), Curve.LINEAR)
            )),
            opacityKeyframes = listOf(Keyframe(100L, 0.25f, Curve.EASE_OUT))
        )

        val moved = clip.moveAnimationKeyframe(100L, 500L)

        assertEquals(listOf(500L), moved.transform.positionKeyframes.map(Keyframe<XY>::timeMs))
        assertEquals(XY(1f, 2f), moved.transform.positionKeyframes.single().value)
        assertEquals(Curve.EASE_IN, moved.transform.positionKeyframes.single().curve)
        assertEquals(listOf(500L), moved.opacityKeyframes.map(Keyframe<Float>::timeMs))
        assertEquals(0.25f, moved.opacityKeyframes.single().value, 0f)
        assertEquals(Curve.EASE_OUT, moved.opacityKeyframes.single().curve)
    }

    @Test
    fun `rectf type interpolates all four animated bounds`() {
        val keys = listOf(
            Keyframe(0L, RectF(0f, 2f, 10f, 12f), Curve.LINEAR),
            Keyframe(100L, RectF(10f, 12f, 30f, 32f))
        )
        val midpoint = Interpolator.valueAt(keys, 50L, RectFType)
        assertEquals(RectF(5f, 7f, 20f, 22f), midpoint)
    }

    @Test
    fun `timecode collection includes clip boundaries keys and quarter-note grid`() {
        val clip = videoClip().copy(
            transform = Transform(scaleKeyframes = listOf(Keyframe(250L, 1.2f))),
            opacityKeyframes = listOf(Keyframe(700L, 0.5f))
        )
        val timeline = Timeline(tracks = listOf(VideoTrack(id = "v1", lane = 0, clips = listOf(clip))))

        val times = timeline.collectAllTimecodes(bpm = 120)

        assertTrue(times.containsAll(listOf(0L, 250L, 500L, 700L, 1_000L)))
        assertFalse(times.contains(-1L))
    }

    @Test
    fun `snap threshold scales in timeline milliseconds and preserves unsnapped drags`() {
        assertEquals(1_000L, snapTimeMs(1_040L, listOf(1_000L, 1_100L), thresholdPx = 5f, msPerPx = 10f))
        assertEquals(1_040L, snapTimeMs(1_040L, listOf(1_000L), thresholdPx = 3f, msPerPx = 10f))
        assertEquals(1_040L, snapTimeMs(1_040L, listOf(1_000L), thresholdPx = 5f, msPerPx = 0f))
    }

    @Test
    fun `move marker relocates shape mask and per-stroke drawing keys`() {
        val clip = videoClip().copy(
            mask = Mask.Shape(
                kind = Mask.Kind.RECTANGLE,
                rect = RectF(0f, 0f, 1f, 1f),
                keyframes = listOf(Keyframe(100L, RectF(0f, 0f, 0.5f, 0.5f)))
            ),
            drawingLayer = DrawingLayer(
                globalAlphaKeyframes = listOf(Keyframe(100L, 0.8f)),
                strokes = listOf(
                    Stroke(
                        points = listOf(Offset(0.1f, 0.2f)),
                        color = Color.White,
                        width = 2f,
                        tool = Tool.PEN,
                        startMs = 0L,
                        alphaKeyframes = listOf(Keyframe(100L, 0.4f)),
                        transform = Transform(positionKeyframes = listOf(Keyframe(100L, XY(0.2f, 0.3f))))
                    )
                )
            )
        )

        val moved = clip.moveAnimationKeyframe(100L, 200L)
        val movedMask = moved.mask as Mask.Shape
        val movedStroke = moved.drawingLayer!!.strokes.single()

        assertEquals(listOf(200L), movedMask.keyframes.map(Keyframe<RectF>::timeMs))
        assertEquals(listOf(200L), moved.drawingLayer.globalAlphaKeyframes.map(Keyframe<Float>::timeMs))
        assertEquals(listOf(200L), movedStroke.alphaKeyframes.map(Keyframe<Float>::timeMs))
        assertEquals(listOf(200L), movedStroke.transform.positionKeyframes.map(Keyframe<XY>::timeMs))
    }

    @Test
    fun `timecode collection includes every shared track category`() {
        val timeline = Timeline(
            tracks = listOf(
                VideoTrack(id = "v1", lane = 0, clips = listOf(videoClip())),
                AudioTrack(
                    id = "a1",
                    lane = 1,
                    clips = listOf(
                        AudioClip(
                            uri = Uri.parse("file:///audio.wav"),
                            inMs = 100L,
                            outMs = 400L,
                            volumeKeyframes = listOf(Keyframe(150L, 0.7f)),
                            panKeyframes = listOf(Keyframe(200L, -0.5f))
                        )
                    ),
                    panKeyframes = listOf(Keyframe(250L, 0.5f))
                ),
                TextTrack(
                    id = "t1",
                    lane = 2,
                    items = listOf(
                        TextItem(
                            text = "Caption",
                            startMs = 10L,
                            endMs = 600L,
                            rotationKeyframes = listOf(Keyframe(375L, 5f)),
                            colorKeyframes = listOf(Keyframe(350L, Color.Red))
                        )
                    )
                ),
                StickerTrack(
                    id = "s1",
                    lane = 3,
                    items = listOf(
                        StickerItem(
                            symbolOrUri = "★",
                            startMs = 20L,
                            endMs = 700L,
                            positionKeyframes = listOf(Keyframe(425L, XY(0.3f, 0.7f))),
                            rotationKeyframes = listOf(Keyframe(450L, 15f))
                        )
                    )
                ),
                OverlayTrack(
                    id = "o1",
                    lane = 4,
                    items = listOf(
                        OverlayItem.Image(
                            uri = Uri.parse("file:///overlay.png"),
                            inMs = 30L,
                            outMs = 800L,
                            opacityKeyframes = listOf(Keyframe(550L, 0.5f))
                        )
                    )
                )
            )
        )

        val times = timeline.collectAllTimecodes()
        assertTrue(times.containsAll(listOf(10L, 20L, 30L, 100L, 150L, 200L, 250L, 350L, 375L, 400L, 425L, 450L, 550L, 600L, 700L, 800L)))
    }

    @Test
    fun `add keyframe samples mask and drawing values at playhead`() {
        val clip = videoClip().copy(
            mask = Mask.Shape(
                kind = Mask.Kind.RECTANGLE,
                rect = RectF(0f, 0f, 1f, 1f),
                keyframes = listOf(
                    Keyframe(0L, RectF(0f, 0f, 0.4f, 0.4f), Curve.LINEAR),
                    Keyframe(1_000L, RectF(0.2f, 0.2f, 1f, 1f), Curve.LINEAR)
                )
            ),
            drawingLayer = DrawingLayer(
                globalAlpha = 1f,
                globalAlphaKeyframes = listOf(Keyframe(0L, 0f, Curve.LINEAR), Keyframe(1_000L, 1f)),
                strokes = listOf(
                    Stroke(
                        points = listOf(Offset(0f, 0f)),
                        color = Color.White,
                        width = 1f,
                        tool = Tool.PEN,
                        startMs = 0L,
                        alpha = 1f,
                        alphaKeyframes = listOf(Keyframe(0L, 0.2f, Curve.LINEAR), Keyframe(1_000L, 0.8f)),
                        transform = Transform(positionKeyframes = listOf(
                            Keyframe(0L, XY(0f, 0f), Curve.LINEAR),
                            Keyframe(1_000L, XY(1f, 1f))
                        ))
                    )
                )
            )
        )

        val added = clip.addAnimationKeyframe(500L)
        val shape = added.mask as Mask.Shape
        val stroke = added.drawingLayer!!.strokes.single()

        val sampledRect = shape.keyframes.single { it.timeMs == 500L }.value
        assertEquals(0.1f, sampledRect.left, 0.0001f)
        assertEquals(0.1f, sampledRect.top, 0.0001f)
        assertEquals(0.7f, sampledRect.right, 0.0001f)
        assertEquals(0.7f, sampledRect.bottom, 0.0001f)
        assertEquals(0.5f, added.drawingLayer.globalAlphaKeyframes.single { it.timeMs == 500L }.value, 0f)
        assertEquals(0.5f, stroke.alphaKeyframes.single { it.timeMs == 500L }.value, 0f)
        assertEquals(XY(0.5f, 0.5f), stroke.transform.positionKeyframes.single { it.timeMs == 500L }.value)
    }

    @Test
    fun textAndStickerPinsSampleAllTypedPropertiesAndMoveAsOneDiamond() {
        val text = TextItem(
            text = "Hello",
            positionKeyframes = listOf(Keyframe(0L, XY(0f, 0f), Curve.LINEAR), Keyframe(1_000L, XY(1f, 1f), Curve.LINEAR)),
            scaleKeyframes = listOf(Keyframe(0L, 1f, Curve.LINEAR), Keyframe(1_000L, 3f, Curve.LINEAR)),
            rotationKeyframes = listOf(Keyframe(0L, 0f, Curve.LINEAR), Keyframe(1_000L, 20f, Curve.LINEAR)),
            colorKeyframes = listOf(Keyframe(0L, Color.Black, Curve.LINEAR), Keyframe(1_000L, Color.White, Curve.LINEAR)),
            alphaKeyframes = listOf(Keyframe(0L, 0f, Curve.LINEAR), Keyframe(1_000L, 1f, Curve.LINEAR))
        )
        val textPin = text.addAnimationKeyframeAt(500L)
        assertEquals(XY(0.5f, 0.5f), textPin.positionKeyframes.single { it.timeMs == 500L }.value)
        assertEquals(2f, textPin.scaleKeyframes.single { it.timeMs == 500L }.value, 0f)
        assertEquals(10f, textPin.rotationKeyframes.single { it.timeMs == 500L }.value, 0f)
        assertEquals(Color(0xFF808080), textPin.colorKeyframes.single { it.timeMs == 500L }.value)
        assertEquals(0.5f, textPin.alphaKeyframes.single { it.timeMs == 500L }.value, 0f)
        val movedText = textPin.moveAnimationKeyframe(500L, 700L)
        assertEquals(setOf(0L, 700L, 1_000L), movedText.animationKeyframeTimes())
        assertTrue(movedText.removeAnimationKeyframeAt(700L).animationKeyframeTimes().none { it == 700L })

        val sticker = StickerItem(
            symbolOrUri = "★",
            positionKeyframes = listOf(Keyframe(0L, XY(0f, 0f), Curve.LINEAR), Keyframe(1_000L, XY(1f, 1f), Curve.LINEAR)),
            scaleKeyframes = listOf(Keyframe(0L, 1f, Curve.LINEAR), Keyframe(1_000L, 3f, Curve.LINEAR)),
            rotationKeyframes = listOf(Keyframe(0L, 0f, Curve.LINEAR), Keyframe(1_000L, 40f, Curve.LINEAR)),
            alphaKeyframes = listOf(Keyframe(0L, 0f, Curve.LINEAR), Keyframe(1_000L, 1f, Curve.LINEAR))
        )
        val stickerPin = sticker.addAnimationKeyframeAt(500L)
        assertEquals(XY(0.5f, 0.5f), stickerPin.positionKeyframes.single { it.timeMs == 500L }.value)
        assertEquals(2f, stickerPin.scaleKeyframes.single { it.timeMs == 500L }.value, 0f)
        assertEquals(20f, stickerPin.rotationKeyframes.single { it.timeMs == 500L }.value, 0f)
        assertEquals(0.5f, stickerPin.alphaKeyframes.single { it.timeMs == 500L }.value, 0f)
        assertEquals(700L, stickerPin.moveAnimationKeyframe(500L, 700L).animationKeyframeTimes().elementAt(1))
    }
}
