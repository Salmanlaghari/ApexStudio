package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.domain.model.TextOverlayKaraoke
import com.apexstudio.app.domain.model.timeline.TextTrack
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KaraokePhraseGroupEditingTest {
    @Test
    fun duplicatingOneKaraokeSliceDetachesItFromTheOriginalPhrase() {
        val phrase = "Hello world"
        val fingerprint = TextOverlayKaraoke.fingerprint(phrase)
        fun slice(id: String, startMs: Long) = TextOverlay.of(id = id, text = phrase).copy(
            presetId = "caption_auto",
            startMs = startMs,
            endMs = startMs + 99L,
            highlightStartUtf16 = 0,
            highlightEndUtf16 = 5,
            highlightSourceFingerprint = fingerprint,
            karaokePhraseGroupId = "phrase-one"
        )
        val clip = MediaClip(
            id = "clip", name = "clip", uri = "file:///clip.mp4", durationMs = 1_000L,
            trimEndMs = 1_000L,
            textOverlays = listOf(slice("first-slice", 0L), slice("second-slice", 100L))
        )
        val vm = EditorViewModel().apply {
            _state.update { it.copy(project = Project("project", "Project", 1_000L, clips = listOf(clip))) }
        }

        vm.duplicateTextOverlay(clip.id, "first-slice")
        val duplicateId = vm.state.value.selectedTextOverlayId!!
        assertNull(vm.state.value.project!!.clips.single().textOverlays
            .single { it.id == duplicateId }.karaokePhraseGroupId)

        vm.setTextOverlayText(clip.id, duplicateId, "Independent copy")
        val overlays = vm.state.value.project!!.clips.single().textOverlays.associateBy(TextOverlay::id)
        assertEquals(phrase, overlays.getValue("first-slice").text)
        assertEquals(phrase, overlays.getValue("second-slice").text)
        assertEquals("Independent copy", overlays.getValue(duplicateId).text)
    }

    @Test
    fun textEditPropagatesOnlyWithinOnePhraseAndInvalidatesOldWordOffsets() {
        val originalText = "Hello world"
        val fingerprint = TextOverlayKaraoke.fingerprint(originalText)
        fun caption(id: String, groupId: String, startMs: Long) = TextOverlay.of(text = originalText).copy(
            id = id,
            presetId = "caption_auto",
            startMs = startMs,
            endMs = startMs + 99L,
            highlightStartUtf16 = 0,
            highlightEndUtf16 = 5,
            highlightSourceFingerprint = fingerprint,
            karaokePhraseGroupId = groupId
        )
        val clip = MediaClip(
            id = "clip", name = "clip", uri = "file:///clip.mp4", durationMs = 1_000L,
            trimEndMs = 1_000L,
            textOverlays = listOf(
                caption("first-slice", "phrase-one", 0L),
                caption("second-slice", "phrase-one", 100L),
                caption("adjacent-same-text", "phrase-two", 200L),
                TextOverlay.of(id = "manual-title", text = "Manual title")
            )
        )
        val vm = EditorViewModel().apply {
            _state.update { it.copy(
                project = Project("project", "Project", 1_000L, clips = listOf(clip)),
                selectedClipId = clip.id,
                durationMs = 1_000L
            ) }
        }

        vm.setTextOverlayText(clip.id, "first-slice", "Edited phrase")

        val updatedProject = vm.state.value.project!!
        val overlays = updatedProject.clips.single().textOverlays.associateBy(TextOverlay::id)
        assertEquals("Edited phrase", overlays.getValue("first-slice").text)
        assertEquals("Edited phrase", overlays.getValue("second-slice").text)
        assertEquals(originalText, overlays.getValue("adjacent-same-text").text)
        assertEquals("Manual title", overlays.getValue("manual-title").text)
        assertNull(TextOverlayKaraoke.validRange(overlays.getValue("first-slice")))
        assertEquals(fingerprint, overlays.getValue("second-slice").highlightSourceFingerprint)

        val typedItems = updatedProject.timeline.tracks.filterIsInstance<TextTrack>()
            .flatMap { it.items }.associateBy { it.id }
        assertEquals("Edited phrase", typedItems.getValue("clip:first-slice").text)
        assertEquals("phrase-one", typedItems.getValue("clip:second-slice").karaokePhraseGroupId)
        assertEquals("phrase-two", typedItems.getValue("clip:adjacent-same-text").karaokePhraseGroupId)
        assertEquals(4, updatedProject.clips.single().textOverlays.size)
    }
}
