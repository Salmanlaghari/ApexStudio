package com.apexstudio.app.data.effect

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.FaceCropKeyframe
import com.apexstudio.app.domain.model.FaceCropTrack
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.SpeedPoint
import org.junit.Assert.*
import org.junit.Test

class FaceCropGlEffectTest {
    private val track = FaceCropTrack(
        sourceWidth = 1920,
        sourceHeight = 1080,
        outputAspectRatio = 9f / 16f,
        keyframes = listOf(frame(1_000L, .05f), frame(3_000L, .45f)),
    )

    private fun frame(time: Long, left: Float) = FaceCropKeyframe(
        sourceTimestampMs = time, left = left, top = 0f,
        right = left + (9f / 16f) * 1080f / 1920f, bottom = 1f, faceFound = true,
    )

    private fun clip() = MediaClip(
        id = "main", name = "main.mp4", uri = "file:///main.mp4", durationMs = 20_000L,
        trimStartMs = 5_000L, trimEndMs = 9_000L, type = ClipType.VIDEO, faceCropTrack = track,
    )

    @Test fun perFrameSourceTimestampLookupInterpolatesActualCropSamples() {
        val middle = track.cropAtSourceTime(2_000L)
        assertEquals(.25f, middle.left, .0001f)
        assertEquals(.25f + (9f / 16f) * 1080f / 1920f, middle.right, .0001f)
    }

    @Test fun exportPreSpeedSourcePtsAddsOnlyAbsoluteTrimOffset() {
        val config = FaceCropRenderConfig.forMainVideo(clip())!!.exportConfig()
        assertEquals(6_000L, config.sourceTimestampMs(1_000_000L))
        assertEquals(5_000L, config.sourceTimestampMs(0L))
        assertEquals(5_000L, config.sourceTimestampMs(-1L))
        assertEquals(9_000L, config.sourceTimestampMs(Long.MAX_VALUE))
    }

    @Test fun exportSourceClockNeverAppliesConstantOrRampedSpeedTwice() {
        val base = clip()
        val variants = listOf(base, base.copy(speedMultiplier = 2f), base.copy(speedMultiplier = .5f),
            base.copy(speedPoints = listOf(SpeedPoint(0f, .5f), SpeedPoint(1f, 2f))))
        variants.forEach { variant ->
            assertEquals(6_000L, FaceCropRenderConfig.forMainVideo(variant)!!.exportConfig().sourceTimestampMs(1_000_000L))
        }
    }

    @Test fun previewUsesAbsoluteSourcePtsWithoutAddingTrimTwice() {
        val config = FaceCropRenderConfig.forMainVideo(clip())!!.previewConfig()
        assertEquals(6_250L, config.sourceTimestampMs(6_250_000L))
    }

    @Test fun timestampMathRemainsLongSafeBeyondIntegerMilliseconds() {
        val start = Int.MAX_VALUE.toLong() + 10_000L
        val config = FaceCropRenderConfig(track, start, start + 10_000L)
        assertEquals(start + 1_000L, config.sourceTimestampMs(1_000_000L))
        val nearEnd = FaceCropRenderConfig(track, Long.MAX_VALUE - 5L)
        assertEquals(Long.MAX_VALUE, nearEnd.sourceTimestampMs(Long.MAX_VALUE))
    }

    @Test fun previewAndExportShareMainVideoTrackSelectionButExcludePhotosAndOverlays() {
        val source = clip()
        val shared = FaceCropRenderConfig.forMainVideo(source)!!
        assertEquals(track, shared.track)
        assertEquals(shared.track, shared.previewConfig().track)
        assertEquals(shared.track, shared.exportConfig().track)
        assertEquals(source.trimStartMs, shared.trimStartMs)
        assertNull(FaceCropRenderConfig.forMainVideo(source.copy(type = ClipType.IMAGE)))
        assertNull(FaceCropRenderConfig.forMainVideo(source.copy(trackIndex = 1)))
        assertNull(FaceCropRenderConfig.forMainVideo(source.copy(faceCropTrack = null)))
    }
}
