package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorChallengeControllerTest {
    private val clip = MediaClip(id = "v", name = "Video", uri = "file:///video.mp4", type = ClipType.VIDEO, durationMs = 5000L, trimEndMs = 5000L)
    private fun editor() = EditorViewModel().also {
        it._state.value = it.state.value.copy(project = Project("p", "Project", 5000L, clips = listOf(clip)), selectedClipId = clip.id)
    }
    @Test fun tagValidationIsLiteralNotAnAiPrompt() {
        assertEquals("#MyChallenge", normalizeChallengeTag("  #MyChallenge  "))
        assertEquals("#اردو_1", normalizeChallengeTag("اردو_1"))
        listOf("", "#", "hello world", "https://example.com", "a".repeat(49), "##tag").forEach { assertNull(normalizeChallengeTag(it)) }
    }
    @Test fun titleIsRealPersistedOverlayAndOneUndoableEdit() {
        val vm = editor()
        val before = vm.state.value.project!!
        assertTrue(vm.applyLocalChallenge("p", clip, "#OneTake"))
        val after = vm.state.value.project!!
        assertEquals("#OneTake", after.clips.single().textOverlays.single().text)
        assertEquals(before, vm.undoStack.single().project)
        assertEquals(after, Json.decodeFromString<Project>(Json.encodeToString(after)))
        vm.undo()
        assertEquals(before, vm.state.value.project)
        vm.redo()
        assertEquals(after, vm.state.value.project)
    }
    @Test fun staleOrInvalidTargetCannotChangeProjectOrHistory() {
        val vm = editor()
        val before = vm.state.value.project
        assertFalse(vm.applyLocalChallenge("wrong", clip, "test"))
        assertFalse(vm.applyLocalChallenge("p", clip.copy(trimStartMs = 1), "test"))
        assertFalse(vm.applyLocalChallenge("p", clip, "bad tag"))
        vm._state.value = vm.state.value.copy(selectedClipId = null)
        assertFalse(vm.applyLocalChallenge("p", clip, "test"))
        assertEquals(before, vm.state.value.project)
        assertTrue(vm.undoStack.isEmpty())
    }
    @Test fun bundledStickerInsertionPreservesMetadataAndHistory() {
        val vm = editor()
        val before = vm.state.value.project!!
        vm.addStickerAsset(assetPath = "stickers/star.png", name = "Star", category = "Shapes")
        val sticker = vm.state.value.project!!.stickers.single()
        assertEquals("Star", sticker.name)
        assertEquals("Shapes", sticker.category)
        assertEquals("stickers/star.png", sticker.assetPath)
        assertEquals(before, vm.undoStack.single().project)
        vm.undo()
        assertEquals(before, vm.state.value.project)
    }

    @Test fun deletingStickerIsUndoableAndMissingIdDoesNotPolluteHistory() {
        val vm = editor()
        vm.addStickerAsset(assetPath = "stickers/star.png", name = "Star", category = "Shapes")
        val beforeDelete = vm.state.value.project!!
        val sticker = beforeDelete.stickers.single()
        vm.removeSticker("missing")
        assertEquals(1, vm.undoStack.size)
        vm.removeSticker(sticker.id)
        assertTrue(vm.state.value.project!!.stickers.isEmpty())
        assertEquals(2, vm.undoStack.size)
        vm.undo()
        assertEquals(beforeDelete, vm.state.value.project)
    }

    @Test fun combiningMarksInValidUnicodeTagsArePreserved() {
        assertEquals("#नमस्ते", normalizeChallengeTag("#नमस्ते"))
        assertEquals("#cafe\u0301", normalizeChallengeTag("#cafe\u0301"))
        assertNull(normalizeChallengeTag("a".repeat(49)))
        assertNull(normalizeChallengeTag("not a tag"))
    }

}
