package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorManualCropTest {
    private fun editor(type: ClipType = ClipType.VIDEO): EditorViewModel {
        val clip = MediaClip("selected", "Selected", "file:///selected.mp4", 10_000L,
            trimEndMs = 10_000L, type = type)
        val other = clip.copy(id = "other", uri = "file:///other.mp4")
        return EditorViewModel().also {
            it._state.value = it._state.value.copy(project = Project("project", "Project", 10_000L,
                clips = listOf(clip, other)), selectedClipId = clip.id)
        }
    }
    private val crop = VideoCropSettings(.1f, .2f, .8f, .9f)

    @Test fun oneManualCommitStoresTheUnmodifiedUndoSnapshotAndOnlyEditsTheTarget() {
        val vm = editor()
        val before = vm.state.value.project!!
        assertTrue(vm.applyVideoCropDraft(before.id, before.clips.first(), crop))
        assertEquals(crop, vm.state.value.project!!.clips.first().videoCrop)
        assertEquals(before.clips.last(), vm.state.value.project!!.clips.last())
        assertEquals(before.clips, vm.undoStack.single())
    }
    @Test fun unchangedCropDoesNotAddAnotherHistoryEntry() {
        val vm = editor()
        vm.updateVideoCrop("selected", crop)
        vm.updateVideoCrop("selected", crop)
        assertEquals(1, vm.undoStack.size)
        vm.resetVideoCrop("selected")
        vm.resetVideoCrop("selected")
        assertEquals(2, vm.undoStack.size)
    }
    @Test fun switchingSelectionRejectsTheDraftWithoutEditingEitherClip() {
        val vm = editor()
        val before = vm.state.value.project!!
        vm.selectClip("other")
        assertFalse(vm.applyVideoCropDraft(before.id, before.clips.first(), crop))
        assertEquals(before, vm.state.value.project)
        assertTrue(vm.undoStack.isEmpty())
    }
    @Test fun reusedClipIdsInAnotherProjectCannotReceiveAnOldDraft() {
        val vm = editor()
        val before = vm.state.value.project!!
        vm._state.value = vm.state.value.copy(project = before.copy(id = "replacement"))
        assertFalse(vm.applyVideoCropDraft(before.id, before.clips.first(), crop))
        assertTrue(vm.undoStack.isEmpty())
    }
    @Test fun sourceReplacementOrOtherPersistedClipEditsRejectOldDrafts() {
        for (replaceUri in listOf(true, false)) {
            val vm = editor()
            val before = vm.state.value.project!!
            vm.updateClip("selected") { if (replaceUri) it.copy(uri = "file:///replacement.mp4") else it.copy(trimStartMs = 1000L) }
            val current = vm.state.value.project
            assertFalse(vm.applyVideoCropDraft(before.id, before.clips.first(), crop))
            assertEquals(current, vm.state.value.project)
            assertTrue(vm.undoStack.isEmpty())
        }
    }
    @Test fun audioAndMissingTargetsCannotBeCroppedOrPolluteUndo() {
        val vm = editor(ClipType.AUDIO)
        val before = vm.state.value.project!!
        assertFalse(vm.applyVideoCropDraft(before.id, before.clips.first(), crop))
        vm.updateVideoCrop("selected", crop)
        vm.updateVideoCrop("missing", crop)
        assertEquals(before, vm.state.value.project)
        assertTrue(vm.undoStack.isEmpty())
    }
    @Test fun imageCropUpdatesTheActualPhotoRendererStateAndRetainsOtherPhotoEdits() {
        val vm = editor(ClipType.IMAGE)
        vm.updateClip("selected") { it.copy(photoEdit = it.photoEdit.copy(rotationSteps = 1, flipHorizontal = true)) }
        vm.updateVideoCrop("selected", crop)
        val changed = vm.state.value.project!!.clips.first()
        assertEquals(PhotoCropRect(.1f, .2f, .8f, .9f), changed.photoEdit.crop)
        assertEquals(1, changed.photoEdit.rotationSteps)
        assertTrue(changed.photoEdit.flipHorizontal)
        assertEquals(crop, changed.videoCrop)
    }
    @Test fun invalidPresetAspectDoesNotInventSquareGeometry() {
        val vm = editor()
        for (aspect in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            vm.setVideoCropPreset("selected", com.apexstudio.app.data.effect.VideoCropPreset.SQUARE, aspect)
        }
        assertEquals(VideoCropSettings(), vm.state.value.project!!.clips.first().videoCrop)
        assertTrue(vm.undoStack.isEmpty())
    }
}
