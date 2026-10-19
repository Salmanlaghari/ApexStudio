package com.apexstudio.app.ui.components

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.TextOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TimelineLayerContentIndexTest {
    @Test
    fun `indexes video image and overlay clips by clamped layer and excludes audio`() {
        val main = clip("main", ClipType.VIDEO, layer = 0)
        val photo = clip("photo", ClipType.IMAGE, layer = 0)
        val overlay = clip("pip", ClipType.OVERLAY, layer = 3)
        val invalidLayer = clip("clamped", ClipType.VIDEO, layer = 17)
        val audio = clip("music", ClipType.AUDIO, layer = 1)

        val index = TimelineLayerContentIndex.from(listOf(main, photo, overlay, invalidLayer, audio))

        assertEquals(listOf(main, photo), index.videoByLayer[0])
        assertEquals(listOf(overlay), index.videoByLayer[3])
        assertEquals(listOf(invalidLayer), index.videoByLayer[9])
        assertFalse(index.videoByLayer.values.flatten().contains(audio))
    }

    @Test
    fun `text ownership uses first owning clip and supports constant-time lookup`() {
        val first = clip("first", ClipType.VIDEO, 0).copy(textOverlays = listOf(TextOverlay(id = "title")))
        val second = clip("second", ClipType.OVERLAY, 1).copy(textOverlays = listOf(TextOverlay(id = "title")))

        val index = TimelineLayerContentIndex.from(listOf(first, second))

        assertEquals(mapOf("title" to "first"), index.textOwnerByOverlayId)
        assertEquals(null, index.textOwnerByOverlayId["missing"])
    }

    private fun clip(id: String, type: ClipType, layer: Int) = MediaClip(
        id = id,
        name = id,
        uri = "file:///$id.mp4",
        durationMs = 1_000L,
        trimEndMs = 1_000L,
        type = type,
        trackIndex = layer,
    )
}
