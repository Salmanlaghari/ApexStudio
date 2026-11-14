package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.domain.model.StickerOverlay
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.KeyframeTrack
import com.apexstudio.app.domain.model.MediaClip
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

    @Test fun `clip sticker project range converts to output local and is undoable`() {
        val vm = EditorViewModel()
        val globalSticker = StickerOverlay(id = "global", startMs = 200L, endMs = 900L)
        val clipSticker = StickerOverlay(id = "clip-sticker", startMs = 1_000L, endMs = 3_000L)
        val first = MediaClip(
            id = "first", name = "First", uri = "file:///first.mp4", durationMs = 10_000L,
            trimStartMs = 1_000L, trimEndMs = 9_000L, type = ClipType.VIDEO,
            trackIndex = 0, speedMultiplier = 2f,
        )
        val owner = MediaClip(
            id = "owner", name = "Owner", uri = "file:///owner.mp4", durationMs = 6_000L,
            trimEndMs = 6_000L, type = ClipType.VIDEO, trackIndex = 0, stickers = listOf(clipSticker),
        )
        vm._state.value = vm.state.value.copy(project = Project(
            "p", "Project", 10_000L, clips = listOf(first, owner), stickers = listOf(globalSticker),
        ))

        vm.setClipStickerPropertyRange("owner", "clip-sticker", 6_000L, 8_000L)

        val updatedProject = vm.state.value.project!!
        val updatedClipSticker = updatedProject.clips.single { it.id == "owner" }.stickers.single()
        assertEquals(2_000L, updatedClipSticker.startMs)
        assertEquals(4_000L, updatedClipSticker.endMs)
        assertEquals(globalSticker, updatedProject.stickers.single())
        assertTrue(vm.state.value.canUndo)
        vm.undo()
        val restored = vm.state.value.project!!.clips.single { it.id == "owner" }.stickers.single()
        assertEquals(clipSticker.startMs, restored.startMs)
        assertEquals(clipSticker.endMs, restored.endMs)
    }

    @Test fun `clip sticker keyframe toggle and drag use project to output local time`() {
        val vm = EditorViewModel()
        val sticker = StickerOverlay(id = "clip-keyed", startMs = 0L, endMs = 6_000L, keyframes = KeyframeTrack())
        val first = MediaClip(
            id = "first", name = "First", uri = "file:///first.mp4", durationMs = 10_000L,
            trimStartMs = 1_000L, trimEndMs = 9_000L, type = ClipType.VIDEO,
            trackIndex = 0, speedMultiplier = 2f,
        )
        val owner = MediaClip(
            id = "owner", name = "Owner", uri = "file:///owner.mp4", durationMs = 6_000L,
            trimEndMs = 6_000L, type = ClipType.VIDEO, trackIndex = 0, stickers = listOf(sticker),
        )
        vm._state.value = vm.state.value.copy(project = Project("p", "Project", 10_000L, clips = listOf(first, owner)))
        vm._playerPosition.value = 5_500L

        vm.toggleClipStickerKeyframeAtPlayhead("owner", "clip-keyed")
        val added = vm.state.value.project!!.clips.single { it.id == "owner" }.stickers.single()
            .keyframes.keyframes.single()
        assertEquals(1_500L, added.timeMs)

        vm.moveClipStickerKeyframe("owner", "clip-keyed", added.id, 7_000L)
        val moved = vm.state.value.project!!.clips.single { it.id == "owner" }.stickers.single()
            .keyframes.keyframes.single()
        assertEquals(3_000L, moved.timeMs)
    }
}
