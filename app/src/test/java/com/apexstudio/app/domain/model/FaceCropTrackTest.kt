package com.apexstudio.app.domain.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class FaceCropTrackTest {
    private val width = 1920
    private val height = 1080
    private val cropWidth = (9f / 16f) * height / width

    private fun frame(timeMs: Long, left: Float, faceFound: Boolean = true) = FaceCropKeyframe(
        sourceTimestampMs = timeMs,
        left = left,
        top = 0f,
        right = left + cropWidth,
        bottom = 1f,
        faceFound = faceFound,
    )

    private fun track() = FaceCropTrack(
        sourceWidth = width,
        sourceHeight = height,
        outputAspectRatio = 9f / 16f,
        keyframes = listOf(frame(1_250L, 0.05f), frame(2_250L, 0.45f, faceFound = false)),
    )

    @Test
    fun `interpolates by absolute source milliseconds and clamps outside measured samples`() {
        val track = track()
        assertEquals(0.05f, track.cropAtSourceTime(0L).left, 0.0001f)
        assertEquals(0.05f, track.cropAtSourceTime(1_250L).left, 0.0001f)
        assertEquals(0.25f, track.cropAtSourceTime(1_750L).left, 0.0001f)
        assertEquals(0.45f, track.cropAtSourceTime(9_000L).left, 0.0001f)
    }

    @Test
    fun `interpolation retains crop bounds and output pixel aspect`() {
        val crop = track().cropAtSourceTime(1_750L)
        assertEquals(0.25f, crop.left, 0.0001f)
        assertEquals(0f, crop.top, 0.0001f)
        assertEquals(1f, crop.bottom, 0.0001f)
        assertEquals(cropWidth, crop.right - crop.left, 0.0001f)
        assertEquals(9f / 16f, ((crop.right - crop.left) * width) / ((crop.bottom - crop.top) * height), 0.0001f)
    }

    @Test
    fun `track validates measured timestamps aspect bounds and required geometry`() {
        assertThrows(IllegalArgumentException::class.java) {
            FaceCropTrack(width, height, 9f / 16f, listOf(frame(100L, 0.1f), frame(100L, 0.2f)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FaceCropTrack(width, height, 1f, listOf(frame(100L, 0.1f)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FaceCropKeyframe(100L, 0f, 0f, Float.NaN, 1f, true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FaceCropTrack(width, height, 9f / 16f, emptyList())
        }
    }

    @Test
    fun `optional clip track round trips and old clip json defaults to no track`() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val clip = MediaClip(
            id = "clip-1",
            name = "sample.mp4",
            uri = "file:///sample.mp4",
            durationMs = 4_000L,
            trimEndMs = 4_000L,
            faceCropTrack = track(),
        )
        val encoded = json.encodeToString(MediaClip.serializer(), clip)
        val restored = json.decodeFromString(MediaClip.serializer(), encoded)
        assertEquals(clip.faceCropTrack, restored.faceCropTrack)
        assertEquals(1_250L, restored.faceCropTrack!!.keyframes[0].sourceTimestampMs)

        val oldJson = """{"id":"old","name":"old.mp4","uri":"file:///old.mp4","durationMs":1000,"trimEndMs":1000}"""
        assertNull(json.decodeFromString(MediaClip.serializer(), oldJson).faceCropTrack)
    }
}
