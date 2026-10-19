package com.apexstudio.app.data.export

import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.apexstudio.app.domain.model.SpeedPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Builds real Media3 compositions/processors; this does not validate native codecs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(UnstableApi::class)
class OverlayAudioCompositionTest {
    @Test
    fun `overlay items retain trim source gain speed and placement but ignore main clip controls`() {
        val curve = listOf(SpeedPoint(0f, 0.75f), SpeedPoint(1f, 1.5f))
        val source = ExportEngine.AudioExportSource(
            uri = "file:///voiceover.mp4",
            trimStartMs = 250L,
            trimEndMs = 2_250L,
            timelinePositionMs = 1_100L,
            speedPoints = curve,
            sourceGain = 0.4f,
            denoiseEnabled = true
        )
        val engine = ExportEngine(RuntimeEnvironment.getApplication())
        val items = engine.buildPlacedAudioItems(
            ExportEngine.ExportConfig(clipSpeed = 4f, volume = 0f, pitchSemitones = 12f),
            listOf(source),
            overlay = true
        )
        val placed = items.single()
        assertEquals(1_100L, placed.timelinePositionMs)
        assertEquals(250L, placed.item.mediaItem.clippingConfiguration.startPositionMs)
        assertEquals(2_250L, placed.item.mediaItem.clippingConfiguration.endPositionMs)
        assertTrue(placed.item.removeVideo)
        val processors = placed.item.effects.audioProcessors
        assertTrue("source gain should be preserved", processors.any { it is ChannelMixingAudioProcessor })
        assertTrue("source denoise should be preserved", processors.any { it.javaClass.simpleName == "DenoiseAudioProcessor" })
        assertFalse("main clip pitch must not leak onto overlay", processors.any { it is SonicAudioProcessor })
        assertTrue(
            "source speed curve should build Media3's speed-changing processor",
            processors.any { it.javaClass.simpleName.contains("Speed", ignoreCase = true) }
        )

        val composition = StandaloneAudioComposition.build(items)
        assertEquals(1, composition.sequences.size)
        val sequenceItems = composition.sequences.single().editedMediaItems
        assertEquals(2, sequenceItems.size)
        assertEquals(1_100_000L, sequenceItems.first().durationUs)
        assertSame(placed.item, sequenceItems.last())
        assertTrue(composition.forceAudioTrack)
    }

    @Test
    fun `cancelling overlay render invokes renderer cancellation seam`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var cancelCalled = false
        val job = launch {
            awaitCancellableAudioRender { _, _ ->
                started.complete(Unit)
                return@awaitCancellableAudioRender { cancelCalled = true }
            }
        }
        started.await()
        job.cancelAndJoin()
        assertTrue(cancelCalled)
    }

    @Test
    fun `out of range timeline source criterion uses output duration`() {
        val videoDurationMs = 5_000L
        val sources = listOf(
            ExportEngine.AudioExportSource("file:///a.m4a", timelinePositionMs = 4_999L),
            ExportEngine.AudioExportSource("file:///b.m4a", timelinePositionMs = 5_000L),
            ExportEngine.AudioExportSource("file:///c.m4a", timelinePositionMs = Long.MAX_VALUE)
        )
        val inRange = selectOverlayAudioWithinDuration(sources, videoDurationMs)
        assertEquals(listOf(sources.first()), inRange.sources)
        assertEquals(2, inRange.omittedCount)
        assertEquals(0, selectOverlayAudioWithinDuration(sources, null).omittedCount)
    }
}
