package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.domain.model.StickerOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorStickerPropertiesControllerTest {
    @Test fun `property edit validates values and is undoable without dropping sticker source data`() {
        val vm = EditorViewModel()
        val sticker = StickerOverlay(id = "s", name = "Star", assetPath = "stars/star.webp", opacity = 1f)
        vm._state.value = vm.state.value.copy(project = Project("p", "Project", 10_000L, stickers = listOf(sticker)))

        vm.updateStickerProperties("s") { it.copy(opacity = 0.35f, sizeScale = 2.4f, rotationDeg = 45f) }
        val changed = vm.state.value.project!!.stickers.single()
        assertEquals(0.35f, changed.opacity, 0.001f)
        assertEquals(2.4f, changed.sizeScale, 0.001f)
        assertEquals(45f, changed.rotationDeg, 0.001f)
        assertEquals("stars/star.webp", changed.assetPath)
        assertTrue(vm.state.value.canUndo)
        vm.undo()
        assertEquals(sticker, vm.state.value.project!!.stickers.single())
    }

    @Test fun `range is clamped to timeline and remains nonempty`() {
        val vm = EditorViewModel()
        val sticker = StickerOverlay(id = "s", startMs = 100L, endMs = 500L)
        vm._state.value = vm.state.value.copy(project = Project("p", "Project", 1_000L, stickers = listOf(sticker)))

        vm.setStickerPropertyRange("s", 900L, 9_000L)
        val updated = vm.state.value.project!!.stickers.single()
        assertEquals(900L, updated.startMs)
        assertEquals(1_000L, updated.endMs)
        vm.setStickerPropertyRange("s", 2_000L, 0L)
        val bounded = vm.state.value.project!!.stickers.single()
        assertEquals(999L, bounded.startMs)
        assertEquals(1_000L, bounded.endMs)
        vm.undo()
        assertFalse(vm.state.value.project!!.stickers.single() == bounded)
    }
}
