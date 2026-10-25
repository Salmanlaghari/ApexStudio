package com.apexstudio.app.data.export

import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.EditedMediaItem
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(UnstableApi::class)
class StandaloneAudioCompositionTest {
    private fun audio(id: String) = EditedMediaItem.Builder(
        MediaItem.Builder().setUri("file:///$id.m4a").setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(200L).setEndPositionMs(1_200L).build()
        ).build()
    ).setRemoveVideo(true).build()

    @Test
    fun `overlapping sources are parallel sequences not concatenated items`() {
        val first = audio("video")
        val music = audio("music")
        val result = StandaloneAudioComposition.build(
            listOf(PlacedAudioItem(first, 0L), PlacedAudioItem(music, 0L))
        )
        assertEquals(2, result.sequences.size)
        assertSame(first, result.sequences[0].editedMediaItems.single())
        assertSame(music, result.sequences[1].editedMediaItems.single())
        assertTrue(result.sequences.all { C.TRACK_TYPE_AUDIO in it.trackTypes })
    }

    @Test
    fun `placed source has exact leading silence and retains clipped item`() {
        val source = audio("voiceover")
        val result = StandaloneAudioComposition.build(listOf(PlacedAudioItem(source, 1_300L)))
        val items = result.sequences.single().editedMediaItems
        assertEquals(2, items.size)
        assertEquals(1_300_000L, items[0].durationUs)
        assertSame(source, items[1])
        assertEquals(200L, items[1].mediaItem.clippingConfiguration.startPositionMs)
        assertEquals(1_200L, items[1].mediaItem.clippingConfiguration.endPositionMs)
    }

    @Test
    fun `negative positions have no gap and overflow conversion saturates`() {
        val source = audio("music")
        val result = StandaloneAudioComposition.build(listOf(PlacedAudioItem(source, -10L)))
        assertSame(source, result.sequences.single().editedMediaItems.single())
        assertEquals(Long.MAX_VALUE, ExportAudioTimelineMapping.timelinePositionUs(Long.MAX_VALUE))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty composition rejects before transformer creation`() {
        StandaloneAudioComposition.build(emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `video item cannot leak into standalone audio composition`() {
        val video = EditedMediaItem.Builder(MediaItem.fromUri("file:///video.mp4")).build()
        StandaloneAudioComposition.build(listOf(PlacedAudioItem(video, 0L)))
    }
}
