package com.apexstudio.app.ui.components

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.SpeedPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TimelineTrimScaleTest {
    @Test
    fun `trim drag conversion accounts for physical pixel density`() {
        val msToDp = 0.1f // 100 dp per second
        val density = 2f
        val deltaPx = sourceTrimDeltaToPx(1_000L, msToDp, density)

        assertEquals(200f, deltaPx, 0.001f)
        assertEquals(1_000L, sourceTrimDeltaToMs(deltaPx, msToDp, density))
    }

    @Test
    fun `trim conversion preserves negative drags at unit density`() {
        val msToDp = 0.25f
        val density = 1f
        val deltaPx = sourceTrimDeltaToPx(-400L, msToDp, density)

        assertEquals(-100f, deltaPx, 0.001f)
        assertEquals(-400L, sourceTrimDeltaToMs(deltaPx, msToDp, density))
    }

    @Test
    fun `same dp distance commits same time across densities and zoom levels`() {
        listOf(1f, 1.5f, 2f, 3.5f).forEach { density ->
            listOf(0.125f, 0.25f, 0.5f).forEach { scale ->
                assertEquals(800L, sourceTrimDeltaToMs(800f * scale * density, scale, density))
            }
        }
    }

    @Test
    fun `invalid drag and timeline scale do not produce a source edit`() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { invalid ->
            assertEquals(0L, sourceTrimDeltaToMs(invalid, .1f, 2f))
            assertEquals(0L, sourceTrimDeltaToMs(10f, invalid, 2f))
            assertEquals(0L, sourceTrimDeltaToMs(10f, .1f, invalid))
        }
        assertEquals(0L, sourceTrimDeltaToMs(10f, 0f, 2f))
        assertEquals(0L, sourceTrimDeltaToMs(10f, -.1f, 2f))
        assertEquals(0L, sourceTrimDeltaToMs(10f, .1f, 0f))
    }

    @Test
    fun `beat snap threshold is safe for invalid and extreme timeline scales`() {
        assertEquals(16L, beatSnapThresholdMs(0.25f, 2f))
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0f, -0.1f).forEach { invalid ->
            assertEquals(1L, beatSnapThresholdMs(invalid, 2f))
            assertEquals(1L, beatSnapThresholdMs(0.25f, invalid))
        }
        assertEquals(5_000L, beatSnapThresholdMs(Float.MIN_VALUE, 1f))
    }

    @Test
    fun `ramped main head drag maps timeline delta through resulting output duration`() {
        val clip = rampedClip()
        val trimmedStart = timelineTrimStartForOutputDeltaMs(clip, 500L)
        val changed = clip.copy(trimStartMs = trimmedStart)

        assertEquals(500.0, (clip.effectiveDurationMs() - changed.effectiveDurationMs()).toDouble(), 2.0)
        assertNotEquals("output milliseconds are not source milliseconds on this ramp", 1_500L, trimmedStart)
    }

    @Test
    fun `ramped main tail drag maps timeline delta through resulting output duration`() {
        val clip = rampedClip()
        val trimmedEnd = timelineTrimEndForOutputDeltaMs(clip, -500L)
        val changed = clip.copy(trimEndMs = trimmedEnd)

        assertEquals(-500.0, (changed.effectiveDurationMs() - clip.effectiveDurationMs()).toDouble(), 2.0)
        assertNotEquals("output milliseconds are not source milliseconds on this ramp", 2_500L, trimmedEnd)
    }

    @Test
    fun `beat snap maps a speed-ramped video tail back to a source trim boundary`() {
        val clip = rampedClip()
        val timelineStartMs = 2_500L
        val requestedEndMs = timelineTrimEndForOutputDeltaMs(clip, 125L)
        val rawTimelineEndMs = timelineStartMs + clip.copy(trimEndMs = requestedEndMs).effectiveDurationMs()
        val targetBeatMs = rawTimelineEndMs + 50L

        val snappedEndMs = snapVideoTrimEndToBeat(
            clip = clip,
            trimStartMs = clip.trimStartMs,
            requestedEndMs = requestedEndMs,
            timelineStartMs = timelineStartMs,
            beatMarkersMs = listOf(targetBeatMs),
            enabled = true,
            thresholdMs = 100L
        )
        val actualTimelineEndMs = timelineStartMs + clip.copy(trimEndMs = snappedEndMs).effectiveDurationMs()

        assertNotEquals(requestedEndMs, snappedEndMs)
        assertEquals(targetBeatMs.toDouble(), actualTimelineEndMs.toDouble(), 2.0)
    }

    @Test
    fun `flat speed retains one to one output to source trim deltas`() {
        val clip = rampedClip().copy(speedPoints = emptyList())

        assertEquals(1_500L, timelineTrimStartForOutputDeltaMs(clip, 500L))
        assertEquals(2_500L, timelineTrimEndForOutputDeltaMs(clip, -500L))
    }

    @Test fun constantMainSpeedTrimDragMapsOutputDeltaAtTwoXAndHalfX() {
        val fast = rampedClip().copy(speedPoints = emptyList(), speedMultiplier = 2f)
        val slow = rampedClip().copy(speedPoints = emptyList(), speedMultiplier = 0.5f)

        val fastStart = timelineTrimStartForOutputDeltaMs(fast, 500L)
        val fastEnd = timelineTrimEndForOutputDeltaMs(fast, -500L)
        assertEquals(2_000L, fastStart)
        assertEquals(2_000L, fastEnd)
        assertEquals(500L, fast.effectiveDurationMs() - fast.copy(trimStartMs = fastStart).effectiveDurationMs())
        assertEquals(500L, fast.effectiveDurationMs() - fast.copy(trimEndMs = fastEnd).effectiveDurationMs())

        val slowStart = timelineTrimStartForOutputDeltaMs(slow, 500L)
        val slowEnd = timelineTrimEndForOutputDeltaMs(slow, -500L)
        assertEquals(1_250L, slowStart)
        assertEquals(2_750L, slowEnd)
        assertEquals(500L, slow.effectiveDurationMs() - slow.copy(trimStartMs = slowStart).effectiveDurationMs())
        assertEquals(500L, slow.effectiveDurationMs() - slow.copy(trimEndMs = slowEnd).effectiveDurationMs())
    }

    @Test
    fun `ramped trim inversion clamps at source and minimum-span boundaries`() {
        val clip = rampedClip()

        assertEquals(0L, timelineTrimStartForOutputDeltaMs(clip, Long.MIN_VALUE))
        assertEquals(clip.trimEndMs - 100L, timelineTrimStartForOutputDeltaMs(clip, Long.MAX_VALUE))
        assertEquals(clip.trimStartMs + 100L, timelineTrimEndForOutputDeltaMs(clip, Long.MIN_VALUE))
        assertEquals(clip.durationMs, timelineTrimEndForOutputDeltaMs(clip, Long.MAX_VALUE))
    }

    @Test
    fun `zero output drag preserves exact source bounds under rounded fast curves`() {
        val clip = rampedClip().copy(
            trimStartMs = 1_001L,
            trimEndMs = 3_001L,
            speedPoints = listOf(SpeedPoint(0f, 8f), SpeedPoint(1f, 8f)),
        )
        assertEquals(1_001L, timelineTrimStartForOutputDeltaMs(clip, 0L))
        assertEquals(3_001L, timelineTrimEndForOutputDeltaMs(clip, 0L))
    }

    private fun rampedClip() = MediaClip(
        id = "ramped-trim",
        name = "ramped-trim",
        uri = "file:///ramped-trim.mp4",
        type = ClipType.VIDEO,
        durationMs = 4_000L,
        trimStartMs = 1_000L,
        trimEndMs = 3_000L,
        speedPoints = listOf(
            SpeedPoint(0f, 0.5f),
            SpeedPoint(0.5f, 0.5f),
            SpeedPoint(1f, 2f),
        ),
    )
}
