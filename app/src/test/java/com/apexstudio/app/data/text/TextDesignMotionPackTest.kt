package com.apexstudio.app.data.text

import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.presentation.state.EditorState
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.applyTextDesignMotion
import com.apexstudio.app.presentation.viewmodel.undo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TextDesignMotionPackTest {
    @Test
    fun `catalog contains 120 unique serialized visual and motion payloads`() {
        val pack = TextDesignMotionPack.presets
        assertEquals(120, pack.size)
        assertEquals(120, pack.map { it.id }.toSet().size)
        val json = Json { encodeDefaults = true }
        val serialized = pack.map { preset ->
            json.encodeToString(TextOverlay.serializer(), preset.applyTo(TextOverlay(id = "sample", text = "User title")).copy(presetId = null))
        }.toSet()
        assertEquals(120, serialized.size)

        val supported = setOf(
            "FADE_IN", "SLIDE_UP", "TYPEWRITER", "POP_SPRING", "BLUR_IN",
            "SLIDE_BOUNCE", "PULSE", "BOUNCE", "3D_FLIP_X", "3D_ROTATE_Y"
        )
        assertEquals(supported, TextDesignMotionPack.motions.map { it.id }.toSet())
        assertTrue(pack.all { it.animationType in supported })
        assertTrue(TextDesignMotionPack.motions.all { motion ->
            listOf(140L, 270L, 410L).any { time -> TextAnimEngine.compute(TextOverlay(id = "motion-check", text = "Check", animationType = motion.id), time) != TextAnimEngine.State.Identity }
        })
        assertEquals(12, TextDesignMotionPack.designs.size)
        assertEquals(10, TextDesignMotionPack.motions.size)
    }

    @Test
    fun `applying a pack preset retains editable text and timing and changes persisted style and motion`() {
        val original = TextOverlay(
            id = "user-layer",
            text = "My own headline",
            x = 0.27f,
            y = 0.63f,
            startMs = 123L,
            endMs = 4567L,
            animationType = "NONE"
        )
        val applied = TextDesignMotionPack.presets.first { it.animationType == "TYPEWRITER" }.applyTo(original)
        assertEquals("My own headline", applied.text)
        assertEquals(original.id, applied.id)
        assertEquals(original.x, applied.x)
        assertEquals(original.y, applied.y)
        assertEquals(original.startMs, applied.startMs)
        assertEquals(original.endMs, applied.endMs)
        assertEquals("TYPEWRITER", applied.animationType)
        assertNotEquals(original.presetId, applied.presetId)
        assertTrue(applied.colorArgb != original.colorArgb || applied.bgArgb != original.bgArgb)
    }

    @Test
    fun `applying combination creates one undo snapshot and undo restores original overlay`() {
        val before = TextOverlay(id = "text-id", text = "Editable phrase", animationType = "NONE")
        val clip = MediaClip(
            id = "clip-id", name = "Video", uri = "file:///video.mp4", durationMs = 5000L,
            trimEndMs = 5000L, textOverlays = listOf(before)
        )
        val vm = EditorViewModel()
        vm._state.value = EditorState(project = Project(id = "project", name = "Test", durationMs = 5000L, clips = listOf(clip)), selectedClipId = clip.id, selectedTextOverlayId = before.id)

        vm.applyTextDesignMotion("clip-id", "text-id", TextDesignMotionPack.presets.first())
        assertEquals(1, vm.undoStack.size)
        val edited = vm.state.value.project!!.clips.single().textOverlays.single()
        assertEquals("Editable phrase", edited.text)
        assertNotEquals(before, edited)

        vm.undo()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(before, vm.state.value.project!!.clips.single().textOverlays.single())
        assertFalse(vm.state.value.canUndo)
    }

    @Test
    fun `stale clip or overlay target creates no undo snapshot`() {
        val vm = EditorViewModel()
        vm._state.value = EditorState(project = Project(id = "project", name = "Empty", durationMs = 0L))
        vm.applyTextDesignMotion("missing-clip", "missing-text", TextDesignMotionPack.presets.first())
        assertTrue(vm.undoStack.isEmpty())
    }
    @Test fun `modified or nonselected catalog targets cannot mutate project`() {
        val before = TextOverlay(id = "text", text = "Preserve")
        val clip = MediaClip(id = "v", name = "V", uri = "file:///v.mp4", durationMs = 5000L, trimEndMs = 5000L, textOverlays = listOf(before))
        val vm = EditorViewModel()
        vm._state.value = EditorState(project = Project("p", "P", 5000L, clips = listOf(clip)), selectedClipId = "other")
        vm.applyTextDesignMotion("v", "text", TextDesignMotionPack.presets.first())
        assertTrue(vm.undoStack.isEmpty())
        vm._state.value = vm._state.value.copy(selectedClipId = "v")
        vm.applyTextDesignMotion("v", "text", TextDesignMotionPack.presets.first().copy(animationType = "FAKE"))
        assertTrue(vm.undoStack.isEmpty())
        assertEquals(before, vm.state.value.project!!.clips.single().textOverlays.single())
    }

}
