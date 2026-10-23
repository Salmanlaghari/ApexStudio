package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.state.EditorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExportBaseSelectionTest {
    private fun clip(id: String, type: ClipType, track: Int = 0) = MediaClip(
        id = id, name = id, uri = "file:///$id", durationMs = 31_000L,
        trimEndMs = 31_000L, type = type, trackIndex = track
    )
    private val main = clip("main", ClipType.VIDEO)
    private val overlay = clip("overlay", ClipType.OVERLAY, 1)
    private fun state(selected: String?, vararg clips: MediaClip) = EditorState(
        project = Project(id = "p", name = "p", durationMs = 31_000L, clips = clips.toList()),
        selectedClipId = selected
    )

    @Test
    fun `selected overlay never replaces the base export video`() {
        assertEquals(main, resolveExportBaseClip(state("overlay", main, overlay)))
    }

    @Test
    fun `overlay before V1 in project ordering is never an export base`() {
        assertEquals(main, resolveExportBaseClip(state(null, overlay, main)))
    }

    @Test
    fun `selected V1 clip remains the export base`() {
        val second = main.copy(id = "second")
        assertEquals(second, resolveExportBaseClip(state("second", main, overlay, second)))
    }

    @Test
    fun `selected overlay over photo retains the photo export route`() {
        val photo = clip("photo", ClipType.IMAGE)
        assertEquals(photo, resolveExportBaseClip(state("overlay", photo, overlay)))
    }

    @Test
    fun `audio selection cannot become a video export source`() {
        val audio = clip("audio", ClipType.AUDIO)
        assertEquals(main, resolveExportBaseClip(state("audio", audio, main, overlay)))
    }

    @Test
    fun `missing main track has no video export base`() {
        assertNull(resolveExportBaseClip(state("overlay", overlay)))
        assertNull(resolveExportBaseClip(EditorState()))
    }
}
