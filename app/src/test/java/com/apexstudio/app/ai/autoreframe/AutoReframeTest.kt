package com.apexstudio.app.ai.autoreframe

import com.apexstudio.app.ai.mediapipe.FaceDetection
import com.apexstudio.app.ai.mediapipe.NormalizedBox
import com.apexstudio.app.ai.mediapipe.NormalizedPoint
import com.apexstudio.app.ai.tflite.LumaFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoReframeTest {
    @Test fun faceTargetSelectorKeepsEstablishedIdentityInsteadOfSwitchingToLargestFace() {
        val established = com.apexstudio.app.ai.mediapipe.TrackedFace(
            7L, FaceDetection(NormalizedBox(.1f, .2f, .3f, .4f), emptyList()),
        )
        val largerCompetitor = com.apexstudio.app.ai.mediapipe.TrackedFace(
            8L, FaceDetection(NormalizedBox(.5f, .1f, .95f, .8f), emptyList()),
        )

        assertEquals(established, FaceTrackTargetSelector.select(listOf(largerCompetitor, established), 7L))
        assertEquals(largerCompetitor, FaceTrackTargetSelector.select(listOf(established, largerCompetitor), null))
    }

    @Test fun faceTargetSelectorFallsBackWhenTargetDisappearsAndNeverInventsADetection() {
        val remaining = com.apexstudio.app.ai.mediapipe.TrackedFace(
            8L, FaceDetection(NormalizedBox(.5f, .1f, .95f, .8f), emptyList()))
        assertEquals(remaining, FaceTrackTargetSelector.select(listOf(remaining), 7L))
        assertEquals(null, FaceTrackTargetSelector.select(emptyList(), 7L))
    }

    @Test fun faceTargetSelectorRejectsInvertedDegenerateAndNonFiniteBoxes() {
        val valid = com.apexstudio.app.ai.mediapipe.TrackedFace(
            8L, FaceDetection(NormalizedBox(.1f, .1f, .2f, .2f), emptyList()))
        val invalidBoxes = listOf(
            NormalizedBox(.9f, .9f, .1f, .1f),
            NormalizedBox(.1f, .1f, .1f, .9f),
            NormalizedBox(Float.NaN, 0f, 1f, 1f),
            NormalizedBox(0f, 0f, Float.POSITIVE_INFINITY, 1f))
        invalidBoxes.forEach { box ->
            val invalid = com.apexstudio.app.ai.mediapipe.TrackedFace(7L, FaceDetection(box, emptyList()))
            assertEquals(valid, FaceTrackTargetSelector.select(listOf(invalid, valid), 7L))
            assertEquals(null, FaceTrackTargetSelector.select(listOf(invalid), 7L))
        }
    }

    @Test fun faceTargetSelectorRanksVisibleClippedAreaAndRejectsWhollyOffscreenCandidates() {
        val visible = com.apexstudio.app.ai.mediapipe.TrackedFace(
            8L, FaceDetection(NormalizedBox(.1f, .1f, .5f, .9f), emptyList()))
        val mostlyOffscreen = com.apexstudio.app.ai.mediapipe.TrackedFace(
            7L, FaceDetection(NormalizedBox(-2f, .05f, .2f, .95f), emptyList()))
        val whollyOffscreen = com.apexstudio.app.ai.mediapipe.TrackedFace(
            9L, FaceDetection(NormalizedBox(1.1f, .1f, 2f, .9f), emptyList()))

        assertEquals(visible, FaceTrackTargetSelector.select(listOf(mostlyOffscreen, visible), null))
        assertEquals(visible, FaceTrackTargetSelector.select(listOf(whollyOffscreen, visible), 9L))
        assertEquals(
            NormalizedBox(0f, .05f, .2f, .95f),
            FaceTrackTargetSelector.select(listOf(mostlyOffscreen), null)!!.detection.box,
        )
    }

    @Test fun faceBoxMovesCropToEdgeWithoutLeavingSourceBounds() {
        val face = FaceDetection(NormalizedBox(.84f, .30f, .96f, .50f), emptyList())
        val frame = FaceAwareReframeEngine.cropFor(1920, 1080, 1f, listOf(face), margin = .25f)
        assertTrue(frame.faceFound)
        assertTrue(frame.crop.left >= 0f && frame.crop.right <= 1f)
        assertTrue(frame.crop.top >= 0f && frame.crop.bottom <= 1f)
        assertEquals(.5625f, frame.crop.right - frame.crop.left, .0001f)
        assertTrue(frame.crop.right >= face.box.right)
    }

    @Test fun reframeClipsPartiallyVisibleFacesAndIgnoresFacesOutsideSource() {
        val offscreen = FaceDetection(NormalizedBox(1.1f, .1f, 2f, .9f), emptyList())
        val partial = FaceDetection(NormalizedBox(-.1f, .2f, .2f, .6f), emptyList())

        val frame = FaceAwareReframeEngine.cropFor(1920, 1080, 1f, listOf(offscreen, partial))

        assertTrue(frame.faceFound)
        assertEquals(0f, frame.crop.left, .0001f)
        assertTrue(frame.crop.right < .6f)
        val invisibleOnly = FaceAwareReframeEngine.cropFor(1920, 1080, 1f, listOf(offscreen))
        assertFalse(invisibleOnly.faceFound)
        assertEquals(.5f, invisibleOnly.crop.centerX, .0001f)
    }

    @Test fun trackUsesVisibleAreaAndReportsOffscreenOnlySamplesAsNoFace() {
        val offscreen = FaceDetection(NormalizedBox(1.1f, .1f, 2f, .9f), emptyList())
        val partial = FaceDetection(NormalizedBox(-.1f, .2f, .2f, .6f), emptyList())
        val frames = FaceAwareReframeEngine.track(1920, 1080, 1f,
            listOf(0L to listOf(offscreen, partial), 100L to listOf(offscreen)), smoothing = 1f)

        assertTrue(frames[0].faceFound)
        assertEquals(0f, frames[0].crop.left, .0001f)
        assertFalse(frames[1].faceFound)
        assertEquals(.5f, frames[1].crop.centerX, .0001f)
        assertEquals(listOf(0L, 100L), frames.map { it.timestampMs })
    }

    @Test fun noFaceUsesHonestCenteredFallbackAndTrackPreservesTimes() {
        val frame = FaceAwareReframeEngine.cropFor(1920, 1080, 1f, emptyList())
        assertFalse(frame.faceFound)
        assertEquals(.5f, frame.crop.centerX, .0001f)
        val track = FaceAwareReframeEngine.track(1920, 1080, 1f, listOf(10L to emptyList(), 20L to emptyList()))
        assertEquals(listOf(10L, 20L), track.map { it.timestampMs })
        assertTrue(track.all { !it.faceFound })
    }

    @Test fun signalCropIsAspectPreservingAndLabelsItsNonFaceMethod() {
        val pixels = FloatArray(100) { i -> if (i % 10 > 6) 1f else 0f }
        val suggestion = SignalAutoCrop.suggest(LumaFrame(0, 10, 10, pixels), 1f)
        assertEquals(1f, suggestion.crop.right - suggestion.crop.left, .0001f)
        assertEquals(1f, suggestion.crop.bottom - suggestion.crop.top, .0001f)
        assertTrue(suggestion.method.contains("not a face"))
    }

    @Test fun faceTrackSamplingHonorsIntervalAndCapsLongClipsAcrossTheirRange() {
        assertEquals(
            listOf(100L, 200L, 300L, 400L),
            FaceTrackSampling.timestamps(100L, 410L, minimumIntervalMs = 100L, maximumSamples = 10),
        )
        assertEquals(
            listOf(0L, 3_333L, 6_666L, 9_999L),
            FaceTrackSampling.timestamps(0L, 10_000L, minimumIntervalMs = 100L, maximumSamples = 4),
        )
        assertEquals(listOf(7L), FaceTrackSampling.timestamps(7L, 8L, 500L, 180))
    }

    @Test fun inferenceDimensionsDoNotUpscaleAndStayUnderConfiguredBound() {
        assertEquals(640 to 360, FaceTrackSampling.boundedDimensions(1920, 1080, 640))
        assertEquals(320 to 200, FaceTrackSampling.boundedDimensions(320, 200, 640))
    }

    @Test fun reframeTrackIgnoresMalformedFaceBoxes() {
        val invalid = FaceDetection(NormalizedBox(Float.NaN, 0f, 1f, 1f), emptyList())
        val valid = FaceDetection(NormalizedBox(.7f, .2f, .9f, .5f), listOf(NormalizedPoint(.8f, .35f)))
        val frames = FaceAwareReframeEngine.track(
            1920, 1080, 1f,
            listOf(0L to listOf(invalid), 100L to listOf(invalid, valid)),
        )
        assertFalse(frames[0].faceFound)
        assertTrue(frames[1].faceFound)
        assertTrue(frames[1].crop.left >= 0f && frames[1].crop.right <= 1f)
    }
}
