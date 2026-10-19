package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.DenoiseStrength
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.SpeedPoint
import org.junit.Assert.*
import org.junit.Test

class EditorOverlayAudioExportSourcesTest {
    private fun overlay(id: String = "overlay") = MediaClip(
        id = id, name = id, uri = "file:///$id.mp4", type = ClipType.OVERLAY,
        durationMs = 10_000L, trimStartMs = 500L, trimEndMs = 4_500L,
        trackIndex = 1, timelineOffsetMs = 2_000L, overlayVolume = 0.75f,
        volume = 0.1f, speedMultiplier = 4f
    )

    @Test fun `regular video route does not duplicate main clip audio`() {
        val main = overlay("main").copy(type = ClipType.VIDEO, trackIndex = 0)
        val photo = overlay("photo").copy(type = ClipType.IMAGE, trackIndex = 0)
        val result = buildOverlayAudioExportSources(listOf(main, overlay(), photo))
        assertEquals(listOf("file:///overlay.mp4"), result.map { it.uri })
    }

    @Test fun `regular route retains overlay gain trim placement curve and clean voice`() {
        val curve = listOf(SpeedPoint(0f, 0.5f), SpeedPoint(1f, 2f))
        val clip = overlay().copy(speedPoints = curve, denoiseEnabled = true,
            denoiseStrength = DenoiseStrength.STRONG)
        val source = buildOverlayAudioExportSources(listOf(clip)).single()
        assertEquals(500L, source.trimStartMs)
        assertEquals(4_500L, source.trimEndMs)
        assertEquals(2_000L, source.timelinePositionMs)
        assertEquals(0.75f, source.sourceGain)
        assertEquals(1f, source.constantSpeedOverride)
        assertEquals(curve, source.speedPoints)
        assertTrue(source.denoiseEnabled)
        assertEquals(DenoiseStrength.STRONG, source.denoiseStrength)
        assertNull(source.trackVolume)
    }

    @Test fun `regular route skips muted hidden and nonfinite overlay volume`() {
        val visible = overlay()
        val hidden = overlay("hidden").copy(trackIndex = 2)
        val muted = overlay("muted").copy(overlayVolume = 0f)
        val negative = overlay("negative").copy(overlayVolume = -1f)
        val nan = overlay("nan").copy(overlayVolume = Float.NaN)
        val infinity = overlay("infinity").copy(overlayVolume = Float.POSITIVE_INFINITY)
        val sources = buildOverlayAudioExportSources(
            listOf(visible, hidden, muted, negative, nan, infinity), setOf(2))
        assertEquals(listOf(visible.uri), sources.map { it.uri })
    }

    @Test fun `photo base and video base share identical overlay source mapping`() {
        val ov = overlay().copy(timelineOffsetMs = -500L)
        val main = overlay("main").copy(trackIndex = 0, type = ClipType.VIDEO)
        val photo = main.copy(type = ClipType.IMAGE)
        val videoSources = buildOverlayAudioExportSources(listOf(main, ov))
        assertEquals(videoSources, buildOverlayAudioExportSources(listOf(photo, ov)))
        assertEquals(0L, videoSources.single().timelinePositionMs)
    }

    @Test fun `regular and standalone overlay routes retain the same canonical source`() {
        val clips = listOf(overlay("a"), overlay("b").copy(timelineOffsetMs = 4_000L))
        assertEquals(buildAudioExportSources(clips, emptyList()), buildOverlayAudioExportSources(clips))
    }
}
