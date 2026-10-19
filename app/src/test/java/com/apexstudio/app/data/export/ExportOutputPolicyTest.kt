package com.apexstudio.app.data.export

import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.GlMatrixTransformation
import org.junit.Assert.*
import org.junit.Test

@androidx.annotation.OptIn(UnstableApi::class)
class ExportOutputPolicyTest {
    @Test fun `720p request actually downsizes matching 4k aspect`() {
        val effects = selectedVideoOutputEffects(ExportEngine.ExportConfig(resolution = "720p", fps = 30))
        val size = (effects.first() as GlMatrixTransformation).configure(3840, 2160)
        assertEquals(1280, size.width)
        assertEquals(720, size.height)
        assertTrue(effects.last() is FrameDropEffect)
    }

    @Test fun `portrait and square output use selected resolution not original pixel count`() {
        val portrait = selectedVideoOutputEffects(ExportEngine.ExportConfig(resolution = "1080p", aspectRatio = "9:16"))
        val size = (portrait.first() as GlMatrixTransformation).configure(2160, 3840)
        assertEquals(1080, size.width)
        assertEquals(1920, size.height)
        val square = selectedVideoOutputEffects(ExportEngine.ExportConfig(resolution = "720p", aspectRatio = "1:1"))
        val squareSize = (square.first() as GlMatrixTransformation).configure(1920, 1080)
        assertEquals(720, squareSize.width)
        assertEquals(720, squareSize.height)
    }

    @Test fun `baked photo canvas retains four by five aspect and solid borders`() {
        val effect = selectedVideoOutputEffects(ExportEngine.ExportConfig(
            resolution = "720p", aspectRatio = "16:9", preserveInputAspectRatio = true
        )).first() as GlMatrixTransformation
        val size = effect.configure(1600, 2000)
        assertEquals(1024, size.width)
        assertEquals(1280, size.height)
        val matrix = effect.getGlMatrixArray(0)
        assertEquals(1f, matrix[0], 0f)
        assertEquals(1f, matrix[5], 0f)
    }

    @Test fun `output transform center crops after source geometry`() {
        val effect = selectedVideoOutputEffects(ExportEngine.ExportConfig(resolution = "720p", aspectRatio = "9:16"))
            .first() as GlMatrixTransformation
        effect.configure(1920, 1080)
        val matrix = effect.getGlMatrixArray(0)
        assertTrue(matrix[0] > 1f)
        assertEquals(1f, matrix[5], 0f)
    }

    @Test fun `quality scales automatic presets but explicit bitrate remains authoritative`() {
        assertEquals(6_000_000, exportTargetBitrate(ExportEngine.ExportConfig(resolution = "720p", quality = "high")))
        assertEquals(3_000_000, exportTargetBitrate(ExportEngine.ExportConfig(resolution = "720p", quality = "low")))
        assertEquals(18_000_000, exportTargetBitrate(ExportEngine.ExportConfig(resolution = "1440p", quality = "medium")))
        assertEquals(12_000_000, exportTargetBitrate(ExportEngine.ExportConfig(bitrateMbps = 12, quality = "low")))
        assertEquals(100_000_000, exportTargetBitrate(ExportEngine.ExportConfig(bitrateMbps = Int.MAX_VALUE)))
    }
}
