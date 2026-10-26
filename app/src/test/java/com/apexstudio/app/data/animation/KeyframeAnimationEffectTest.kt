package com.apexstudio.app.data.animation

import android.net.Uri
import com.apexstudio.app.domain.model.AnimatedTransform
import com.apexstudio.app.domain.model.Keyframe
import com.apexstudio.app.domain.model.KeyframeCurve
import com.apexstudio.app.domain.model.KeyframeTrack
import com.apexstudio.app.domain.model.timeline.Curve
import com.apexstudio.app.domain.model.timeline.Keyframe as SharedKeyframe
import com.apexstudio.app.domain.model.timeline.Transform
import com.apexstudio.app.domain.model.timeline.VideoClip as SharedVideoClip
import com.apexstudio.app.domain.model.timeline.XY
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyframeAnimationEffectTest {
    @Test
    fun `visual keyframe properties use mapped project presentation time`() {
        val track = KeyframeTrack(
            listOf(
                Keyframe("start", 0L, opacity = 0.2f, saturation = 0.4f),
                Keyframe("end", 2_000L, opacity = 0.8f, saturation = 1.2f)
            )
        )

        val sample = KeyframeAnimationEffect.sampleAtPresentationTimeUs(
            track = track,
            presentationTimeUs = 1_000_000L,
            timeMsAtPresentationTimeUs = { it / 1_000L + 500L }
        )

        assertEquals(1_500L, sample.timeMs)
        assertEquals(0.65f, sample.transform.opacity, 0.0001f)
        assertEquals(1.0f, sample.saturation, 0.0001f)
    }

    @Test
    fun `typed shared video keys are interpolated at every mapped frame time`() {
        val clip = SharedVideoClip(
            uri = Uri.parse("file:///tmp/shared.mp4"),
            inMs = 0L,
            outMs = 2_000L,
            transform = Transform(
                positionKeyframes = listOf(
                    SharedKeyframe(0L, XY(0f, 0f), Curve.LINEAR),
                    SharedKeyframe(2_000L, XY(2f, 4f))
                ),
                scaleKeyframes = listOf(SharedKeyframe(0L, 1f, Curve.LINEAR), SharedKeyframe(2_000L, 3f))
            ),
            opacityKeyframes = listOf(SharedKeyframe(0L, 0f, Curve.LINEAR), SharedKeyframe(2_000L, 1f)),
            filterKeyframes = listOf(SharedKeyframe(0L, 0f, Curve.LINEAR), SharedKeyframe(2_000L, 1f)),
            lutKeyframes = listOf(SharedKeyframe(0L, 0.2f, Curve.LINEAR), SharedKeyframe(2_000L, 1f)),
            saturationKeyframes = listOf(SharedKeyframe(0L, 0.5f, Curve.LINEAR), SharedKeyframe(2_000L, 1.5f))
        )

        val sample = KeyframeAnimationEffect.sampleSharedAtPresentationTimeUs(
            clip = clip,
            presentationTimeUs = 1_000_000L,
            timeMsAtPresentationTimeUs = { it / 1_000L + 500L }
        )

        assertEquals(1_500L, sample.timeMs)
        assertEquals(1.5f, sample.transform.translateX, 0.0001f)
        assertEquals(3f, sample.transform.translateY, 0.0001f)
        assertEquals(2.5f, sample.transform.scale, 0.0001f)
        assertEquals(0.75f, sample.transform.opacity, 0.0001f)
        assertEquals(0.75f, sample.transform.filterIntensity, 0.0001f)
        assertEquals(0.8f, clip.lutIntensityAt(sample.timeMs), 0.0001f)
        assertEquals(1.25f, sample.saturation, 0.0001f)
    }

    @Test
    fun `preview and export sampler prefer legacy track for custom Bezier curves`() {
        val track = KeyframeTrack(
            listOf(
                Keyframe("start", 0L, scale = 0f),
                Keyframe(
                    "end", 1_000L, scale = 1f, curve = KeyframeCurve.BEZIER,
                    bezierCp1X = 0.1f, bezierCp1Y = 0.9f,
                    bezierCp2X = 0.3f, bezierCp2Y = 1.2f
                )
            )
        )
        val sharedClip = SharedVideoClip(
            uri = Uri.parse("file:///tmp/shared.mp4"),
            inMs = 0L,
            outMs = 1_000L,
            transform = Transform(scaleKeyframes = listOf(
                SharedKeyframe(0L, 0f, Curve.LINEAR),
                SharedKeyframe(1_000L, 100f, Curve.LINEAR)
            ))
        )

        val sample = KeyframeAnimationEffect.sampleForPlayback(
            track = track,
            sharedClip = sharedClip,
            presentationTimeUs = 250_000L
        )
        val legacyExpected = KeyframeAnimationEffect.sampleAtPresentationTimeUs(track, 250_000L)

        assertEquals(0.88327964f, sample.transform.scale, 0.00002f)
        assertEquals(legacyExpected.transform.scale, sample.transform.scale, 0f)
    }

    @Test
    fun `shader transform matches Compose geometry in landscape`() {
        val transform = AnimatedTransform(
            translateX = 0.25f,
            translateY = -0.5f,
            scale = 2f,
            rotationDeg = 90f,
            opacity = 0.4f
        )
        val matrix = KeyframeAnimationEffect.transformToGlMatrix(transform, 1920, 1080)

        assertEquals(0f, matrix[0], 0.0001f)
        assertEquals(-2f * 1920f / 1080f, matrix[1], 0.0001f)
        assertEquals(2f * 1080f / 1920f, matrix[4], 0.0001f)
        assertEquals(0f, matrix[5], 0.0001f)
        assertEquals(0.5f, matrix[12], 0.0001f)
        assertEquals(1f, matrix[13], 0.0001f)
    }

    @Test
    fun `shader transform corrects aspect ratio in portrait`() {
        val transform = AnimatedTransform(
            translateX = 0.25f,
            translateY = -0.5f,
            scale = 2f,
            rotationDeg = 90f,
            opacity = 0.4f
        )
        val matrix = KeyframeAnimationEffect.transformToGlMatrix(transform, 1080, 1920)

        assertEquals(-2f * 1080f / 1920f, matrix[1], 0.0001f)
        assertEquals(2f * 1920f / 1080f, matrix[4], 0.0001f)
    }
}
