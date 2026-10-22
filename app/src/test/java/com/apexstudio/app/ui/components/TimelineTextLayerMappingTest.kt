package com.apexstudio.app.ui.components

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.TextOverlay
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineTextLayerMappingTest {
    @Test fun explicitlyLinkedSegmentsBecomeOneGlobalLaneLayerWithFirstOwner() {
        fun clip(id: String, duration: Long, text: List<TextOverlay> = emptyList()) = MediaClip(
            id = id, name = id, uri = "file:///$id", durationMs = duration,
            trimStartMs = 0L, trimEndMs = duration, type = ClipType.VIDEO,
            textOverlays = text
        )
        val first = TextOverlay.of(id = "shared", text = "Across").copy(startMs = 800L, endMs = 1_000L,
            projectTextLayerId = "linked", rangeEndExclusive = true)
        val second = first.copy(startMs = 0L, endMs = 300L)
        val clips = listOf(clip("a", 1_000L, listOf(first)), clip("b", 500L, listOf(second)))
        val layers = mapProjectTextLayers(clips, listOf(first, second), mapOf("shared" to "a"))
        assertEquals(1, layers.size)
        assertEquals("a", layers.single().ownerClipId)
        assertEquals(800L, layers.single().startMs)
        assertEquals(1_300L, layers.single().endMs)
    }

    @Test fun legacySameIdTextRemainsSeparateAndKeepsItsActualOwner() {
        val first = TextOverlay("shared", text = "First", startMs = 800L, endMs = 1_000L)
        val second = first.copy(text = "Second", startMs = 0L, endMs = 300L)
        val clips = listOf(
            MediaClip("a", "a", "file:///a", 1_000L, trimEndMs = 1_000L, textOverlays = listOf(first)),
            MediaClip("b", "b", "file:///b", 1_000L, trimEndMs = 1_000L, textOverlays = listOf(second)))
        val layers = mapProjectTextLayers(clips, listOf(first, second), mapOf("shared" to "a"))
        assertEquals(2, layers.size)
        assertEquals(listOf("a", "b"), layers.map { it.ownerClipId })
        assertEquals(listOf(800L, 1_000L), layers.map { it.startMs })
        assertEquals(listOf("First", "Second"), layers.map { it.overlay.text })
    }
}
