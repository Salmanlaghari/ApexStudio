package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.data.export.ProjectCompositionTimeline
import com.apexstudio.app.domain.model.*
import kotlinx.coroutines.flow.update
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TextProjectRangeControllerTest {
    private fun editor(): EditorViewModel {
        val title = TextOverlay(id = "title", text = "Original", startMs = 500L, endMs = 2_000L,
            fontFamily = "serif", isItalic = true, animationType = "FADE_IN")
        val clips = listOf(
            MediaClip("a", "First", "file:///a.mp4", 4_000L, trimEndMs = 4_000L, textOverlays = listOf(title)),
            MediaClip("b", "Second", "file:///b.mp4", 4_000L, trimEndMs = 4_000L)
        )
        return EditorViewModel().apply {
            _state.update { it.copy(project = Project("text-ranges", "Text ranges",
                ProjectCompositionTimeline.build(clips).durationMs, clips = clips),
                selectedClipId = "a", selectedTextOverlayId = "title") }
        }
    }

    @Test fun globalRangeSpansBothClipsWithoutChangingMediaOrTextStyle() {
        val vm = editor()
        val before = vm.state.value.project!!
        vm.updateTextOverlayProjectRange("title", 1_000L, 7_000L)
        val after = vm.state.value.project!!
        assertEquals(before.clips.map { it.uri }, after.clips.map { it.uri })
        assertEquals(TextOverlayProjectRange(1_000L, 7_000L), vm.textOverlayProjectRange("title"))
        assertTrue(after.clips.all { it.textOverlays.single().id == "title" })
        assertNotNull(after.clips.first().textOverlays.single().projectTextLayerId)
        assertEquals(after.clips.first().textOverlays.single().projectTextLayerId,
            after.clips.last().textOverlays.single().projectTextLayerId)
        assertTrue(after.clips.all { it.textOverlays.single().rangeEndExclusive })
        assertTrue(after.clips.all { it.textOverlays.single().fontFamily == "serif" && it.textOverlays.single().isItalic })
        assertEquals(1_000L, after.clips.first().textOverlays.single().startMs)
        assertEquals(0L, after.clips.last().textOverlays.single().startMs)
        assertTrue(vm.state.value.canUndo)
        vm.undo()
        assertEquals(before.clips, vm.state.value.project!!.clips)
    }

    @Test fun styleEditsUpdateEverySegmentWithoutChangingItsLocalWindow() {
        val vm = editor()
        vm.updateTextOverlayProjectRange("title", 1_000L, 7_000L)
        val initialGroup = vm.state.value.project!!.clips.first().textOverlays.single().projectTextLayerId
        vm.updateTextOverlayProjectRange("title", 1_100L, 6_900L)
        assertEquals(initialGroup, vm.state.value.project!!.clips.first().textOverlays.single().projectTextLayerId)
        val windows = vm.state.value.project!!.clips.map { it.textOverlays.single().let { o -> o.startMs to o.endMs } }
        vm.setTextOverlayText("a", "title", "Across clips")
        val overlays = vm.state.value.project!!.clips.map { it.textOverlays.single() }
        assertTrue(overlays.all { it.text == "Across clips" })
        assertEquals(windows, overlays.map { it.startMs to it.endMs })
    }

    @Test fun duplicateAndDeleteOperateOnTheWholeLogicalLayerAndRemainUndoable() {
        val vm = editor()
        vm.updateTextOverlayProjectRange("title", 1_000L, 7_000L)
        vm.duplicateTextOverlay("a", "title")
        val duplicatedId = vm.state.value.selectedTextOverlayId!!
        assertNotEquals("title", duplicatedId)
        assertEquals(vm.textOverlayProjectRange("title"), vm.textOverlayProjectRange(duplicatedId))
        val originalGroup = vm.state.value.project!!.clips.first().textOverlays.first { it.id == "title" }.projectTextLayerId
        val duplicateGroups = vm.state.value.project!!.clips.map { clip -> clip.textOverlays.first { it.id == duplicatedId }.projectTextLayerId }.distinct()
        assertEquals(1, duplicateGroups.size)
        assertNotNull(duplicateGroups.single())
        assertNotEquals(originalGroup, duplicateGroups.single())
        assertTrue(vm.state.value.project!!.clips.all { it.textOverlays.size == 2 })
        vm.removeTextOverlay("a", "title")
        assertTrue(vm.state.value.project!!.clips.all { clip -> clip.textOverlays.none { it.id == "title" } })
        assertNotNull(vm.textOverlayProjectRange(duplicatedId))
        vm.undo()
        assertEquals(TextOverlayProjectRange(1_000L, 7_000L), vm.textOverlayProjectRange("title"))
    }

    @Test fun unknownTextDoesNotCreateAPlaceholderLayer() {
        val vm = editor()
        val before = vm.state.value.project
        vm.updateTextOverlayProjectRange("missing", 0L, 7_000L)
        assertEquals(before, vm.state.value.project)
        assertFalse(vm.state.value.canUndo)
    }
    @Test fun legacyDuplicateIDsRemainOwnerScopedForStyleDeleteAndDuplicate() {
        val first = TextOverlay(id = "collision", text = "V1", startMs = 100L, endMs = 900L)
        val second = TextOverlay(id = "collision", text = "Overlay", startMs = 200L, endMs = 800L)
        val clips = listOf(
            MediaClip("a", "First", "file:///a.mp4", 1_000L, trimEndMs = 1_000L, textOverlays = listOf(first)),
            MediaClip("b", "Second", "file:///b.mp4", 1_000L, trimEndMs = 1_000L, textOverlays = listOf(second))
        )
        val vm = EditorViewModel().apply {
            _state.update { it.copy(project = Project("collision", "Collision", 2_000L, clips = clips), selectedClipId = "b") }
        }
        vm.setTextOverlayText("b", "collision", "Changed")
        var after = vm.state.value.project!!.clips
        assertEquals(first, after.first().textOverlays.single())
        assertEquals("Changed", after.last().textOverlays.single().text)

        vm.duplicateTextOverlay("b", "collision")
        after = vm.state.value.project!!.clips
        assertEquals(1, after.first().textOverlays.size)
        assertEquals(2, after.last().textOverlays.size)
        val duplicated = after.last().textOverlays.last()
        assertEquals("Changed", duplicated.text)
        assertNull(duplicated.projectTextLayerId)

        vm.removeTextOverlay("b", "collision")
        after = vm.state.value.project!!.clips
        assertEquals(listOf(first), after.first().textOverlays)
        assertEquals(listOf(duplicated), after.last().textOverlays)
    }

    @Test fun projectRangeReplacementDoesNotRemoveSameIdLegacyOverlayFromAnotherClip() {
        val first = TextOverlay(id = "same", text = "V1", startMs = 100L, endMs = 900L)
        val second = TextOverlay(id = "same", text = "Overlay", startMs = 200L, endMs = 800L)
        val clips = listOf(
            MediaClip("a", "First", "file:///a.mp4", 1_000L, trimEndMs = 1_000L, textOverlays = listOf(first)),
            MediaClip("b", "Second", "file:///b.mp4", 1_000L, trimEndMs = 1_000L, textOverlays = listOf(second))
        )
        val vm = EditorViewModel().apply {
            _state.update { it.copy(project = Project("collision-range", "Collision range", 2_000L, clips = clips), selectedClipId = "b") }
        }
        vm.updateTextOverlayProjectRange("same", 1_100L, 1_700L)
        val after = vm.state.value.project!!.clips
        assertEquals(listOf(first), after.first().textOverlays)
        assertEquals(TextOverlayProjectRange(1_100L, 1_700L), vm.textOverlayProjectRange("same"))
        assertEquals(1, after.last().textOverlays.size)
        assertNotNull(after.last().textOverlays.single().projectTextLayerId)
        assertEquals("Overlay", after.last().textOverlays.single().text)
    }

}
