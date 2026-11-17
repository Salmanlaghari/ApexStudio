package com.apexstudio.app.data.export

import androidx.media3.common.util.UnstableApi
import com.apexstudio.app.domain.model.ClipTransition
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.SpeedCurveMath
import com.apexstudio.app.domain.model.SpeedPoint
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@UnstableApi
class TransitionAudioSpeedProviderTest {
    private fun video(id: String, speedPoints: List<SpeedPoint> = emptyList()) = MediaClip(
        id = id,
        name = id,
        uri = "file:///$id.mp4",
        durationMs = 10_000L,
        trimStartMs = 1_000L,
        trimEndMs = 9_000L,
        speedPoints = speedPoints
    )

    @Test
    fun `incoming transition audio samples the original clip speed curve phase`() {
        val curve = listOf(
            SpeedPoint(0f, 2f),
            SpeedPoint(0.5f, 0.75f),
            SpeedPoint(1f, 1.5f)
        )
        val outgoing = video("outgoing")
        val incoming = video("incoming", curve)
        val plan = ProjectTransitionTimeline.build(
            listOf(outgoing, incoming),
            listOf(ClipTransition(
                id = "outgoing-incoming",
                fromClipId = outgoing.id,
                toClipId = incoming.id,
                durationMs = 1_000L
            ))
        )
        val segment = plan.audioSegments.single { it.clip.id == incoming.id }
        val sourceOffsetMs = segment.sourceStartMs - segment.entry.sourceStartMs
        val curveSpanMs = segment.entry.sourceEndMs - segment.entry.sourceStartMs
        assertTrue("incoming audio should begin inside the original clip", sourceOffsetMs > 0L)

        val itemConfig = ExportEngine.ExportConfig(
            trimStartMs = segment.sourceStartMs,
            trimEndMs = segment.sourceEndMs,
            clipSpeed = incoming.speedMultiplier,
            speedPoints = curve
        )
        val provider = ExportEngine(RuntimeEnvironment.getApplication())
            .buildTransitionAudioSpeedProvider(itemConfig, segment)
            ?: error("expected a speed provider for the variable-speed clip")

        fun expectedSpeedAt(itemTimeUs: Long): Float {
            val sourceOffsetUs = sourceOffsetMs * 1_000L + itemTimeUs
            val curveSpanUs = curveSpanMs * 1_000L
            val fraction = (sourceOffsetUs.toDouble() / curveSpanUs.toDouble()).toFloat()
            return SpeedCurveMath.speedAt(curve, fraction)
        }

        val startSpeed = provider.getSpeed(0L)
        assertEquals(expectedSpeedAt(0L), startSpeed, 0.0001f)
        assertEquals(expectedSpeedAt(100_000L), provider.getSpeed(100_000L), 0.0001f)
        assertTrue(
            "a segment-local reset would incorrectly restart at the first curve value",
            abs(startSpeed - SpeedCurveMath.speedAt(curve, 0f)) > 0.1f
        )

        val sourceOffsetUs = sourceOffsetMs * 1_000L
        val expectedNextBoundaryUs =
            ((sourceOffsetUs / 10_000L) + 1L) * 10_000L - sourceOffsetUs
        assertEquals(expectedNextBoundaryUs, provider.getNextSpeedChangeTimeUs(0L))
    }
}
