package com.apexstudio.app.ui.screens.editor

import com.apexstudio.app.data.export.compositionFitScales
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.VideoFitMode
import com.apexstudio.app.presentation.viewmodel.withImportedMainVideoFitDefault
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoFitPlacementTest {
    @Test
    fun `legacy clip defaults to fit while imported clip state can persist cover`() {
        assertEquals(VideoFitMode.FIT, MediaClip("legacy", "legacy", "uri", 1_000L, trimEndMs = 1_000L).videoFitMode)
        assertEquals(VideoFitMode.COVER, MediaClip(
            "new", "new", "uri", 1_000L, trimEndMs = 1_000L,
            videoFitMode = VideoFitMode.COVER
        ).videoFitMode)
    }

    @Test
    fun `only imported main-track videos receive cover default`() {
        val video = MediaClip("video", "video", "uri", 1_000L, trimEndMs = 1_000L)
        val image = video.copy(id = "image", type = ClipType.IMAGE)
        val overlay = video.copy(id = "overlay", type = ClipType.OVERLAY, trackIndex = 1)
        assertEquals(VideoFitMode.COVER, withImportedMainVideoFitDefault(video).videoFitMode)
        assertEquals(VideoFitMode.FIT, withImportedMainVideoFitDefault(image).videoFitMode)
        assertEquals(VideoFitMode.FIT, withImportedMainVideoFitDefault(overlay).videoFitMode)
    }

    @Test
    fun `portrait video fit preserves full image and cover fills landscape composition`() {
        val (fitX, fitY) = compositionFitScales(9f / 16f, 16f / 9f, VideoFitMode.FIT)
        val (coverX, coverY) = compositionFitScales(9f / 16f, 16f / 9f, VideoFitMode.COVER)
        assertEquals(9f / 16f / (16f / 9f), fitX, 0.0001f)
        assertEquals(1f, fitY, 0f)
        assertEquals(1f, coverX, 0f)
        assertEquals((16f / 9f) / (9f / 16f), coverY, 0.0001f)
    }

    @Test
    fun `landscape video fit preserves full image and cover fills portrait composition`() {
        val (fitX, fitY) = compositionFitScales(16f / 9f, 9f / 16f, VideoFitMode.FIT)
        val (coverX, coverY) = compositionFitScales(16f / 9f, 9f / 16f, VideoFitMode.COVER)
        assertEquals(1f, fitX, 0f)
        assertEquals((9f / 16f) / (16f / 9f), fitY, 0.0001f)
        assertEquals((16f / 9f) / (9f / 16f), coverX, 0.0001f)
        assertEquals(1f, coverY, 0f)
    }

    @Test
    fun `square source uses contain letterbox and cover crop without distortion`() {
        val (fitX, fitY) = compositionFitScales(1f, 16f / 9f, VideoFitMode.FIT)
        val (coverX, coverY) = compositionFitScales(1f, 16f / 9f, VideoFitMode.COVER)
        assertEquals(9f / 16f, fitX, 0.0001f)
        assertEquals(1f, fitY, 0f)
        assertEquals(1f, coverX, 0f)
        assertEquals(16f / 9f, coverY, 0.0001f)

        val fitBounds = previewContentBounds(1_600f, 900f, 1f, isCoverMode = false)!!
        val coverBounds = previewContentBounds(1_600f, 900f, 1f, isCoverMode = true)!!
        assertEquals(350f, fitBounds.left, 0.01f)
        assertEquals(1_250f, fitBounds.right, 0.01f)
        assertEquals(0f, coverBounds.left, 0f)
        assertEquals(1_600f, coverBounds.right, 0f)
    }
}
