package com.apexstudio.app.ui.screens.editor

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.TextOverlay
import org.junit.Assert.*
import org.junit.Test

class PreviewTextOwnersTest {
    @Test fun mainTemplateTitleAndActivePipTitleRemainIndependentOfSelection() {
        val main = MediaClip("main", "Main", "file:///main.mp4", 5000L, trimEndMs = 5000L,
            textOverlays = listOf(TextOverlay.of(text = "YOUR STORY")))
        val pip = main.copy(id = "pip", type = ClipType.OVERLAY, trackIndex = 1,
            timelineOffsetMs = 1000L, textOverlays = listOf(TextOverlay.of(text = "PiP title")))
        val owners = previewTextOwners(main, listOf(pip), 2000L)
        assertEquals(listOf("main", "pip"), owners.map { it.id })
        assertEquals(listOf("YOUR STORY", "PiP title"), owners.flatMap { it.textOverlays }.map { it.text })
        assertEquals(listOf(main), previewTextOwners(main, listOf(pip), 0L))
        assertEquals(listOf(main), previewTextOwners(main, emptyList(), 2000L))
    }
}
