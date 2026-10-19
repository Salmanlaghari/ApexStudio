package com.apexstudio.app.data.export

import com.apexstudio.app.domain.model.SpeedPoint
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Regression coverage for configuring each standalone audio item's processors. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(UnstableApi::class)
class ExportAudioSourceConfigTest {
    @Test
    fun `variable speed curve receives its own trimmed source span`() {
        val curve = listOf(SpeedPoint(0f, 0.5f), SpeedPoint(1f, 2f))
        val source = ExportEngine.AudioExportSource(
            uri = "file:///audio.m4a",
            trimStartMs = 1_250L,
            trimEndMs = 5_250L,
            speedPoints = curve
        )
        val base = ExportEngine.ExportConfig(clipSpeed = 1f)

        val itemConfig = ExportEngine.audioConfigForSource(base, source)

        assertEquals(1_250L, itemConfig.trimStartMs)
        assertEquals(5_250L, itemConfig.trimEndMs)
        assertEquals(4_000L, itemConfig.trimEndMs - itemConfig.trimStartMs)
        assertEquals(curve, itemConfig.speedPoints)
        assertEquals(1f, itemConfig.clipSpeed)
    }

    @Test
    fun `per-source clean voice remains configured with the same audio item`() {
        val source = ExportEngine.AudioExportSource(
            uri = "file:///voice.m4a",
            trimStartMs = 300L,
            trimEndMs = 1_300L,
            denoiseEnabled = true
        )

        val itemConfig = ExportEngine.audioConfigForSource(
            ExportEngine.ExportConfig(),
            source
        )

        assertEquals(300L, itemConfig.trimStartMs)
        assertEquals(1_300L, itemConfig.trimEndMs)
        assertEquals(true, itemConfig.denoiseEnabled)
    }
}
