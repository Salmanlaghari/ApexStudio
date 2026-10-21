package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.setPlayerError
import com.apexstudio.app.presentation.viewmodel.setPlayerPosition
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class EffectWorkspacePanelTest {
    @get:Rule val compose = createComposeRule()
    private val clip = MediaClip("video", "Video", "file:///source.mp4", 2_000L, trimEndMs = 2_000L)
    private fun editor() = EditorViewModel().also { vm ->
        vm._state.value = vm.state.value.copy(project = Project("project", "Project", 2_000L, clips = listOf(clip)), selectedClipId = clip.id)
    }

    @Test fun `preset previews without mutation then Apply commits exactly one undo entry`() {
        val vm = editor()
        val previews = mutableListOf<MediaClip>()
        var closed = 0
        compose.setContent {
            Box(Modifier.width(400.dp)) {
                EffectWorkspacePanel("project", clip, vm, false, previews::add, {}, {}, { closed++ })
            }
        }
        compose.onNodeWithText("Monochrome").assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle {
            assertEquals(clip, vm.state.value.project!!.clips.single())
            assertFalse(vm.state.value.canUndo)
            assertEquals("mono", previews.last().workspaceEffects.single().id)
        }
        compose.onNodeWithText("Apply").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle {
            assertEquals(1, closed)
            assertEquals("mono", vm.state.value.project!!.clips.single().workspaceEffects.single().id)
            assertEquals(1, vm.undoStack.size)
        }
    }

    @Test fun `split preview undo and cancel do not touch project history`() {
        val vm = editor()
        val previews = mutableListOf<MediaClip>()
        var closed = 0
        compose.setContent {
            Box(Modifier.width(400.dp)) {
                EffectWorkspacePanel("project", clip, vm, false, previews::add, {}, {}, { closed++ })
            }
        }
        compose.onNodeWithText("Split screen").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("2 tiles").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertEquals("split2", previews.last().workspaceEffects.single().id) }
        compose.onNodeWithContentDescription("Undo effect preview").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertEquals(clip, previews.last()) }
        compose.onNodeWithText("Cancel").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle {
            assertEquals(1, closed)
            assertEquals(clip, vm.state.value.project!!.clips.single())
            assertTrue(vm.undoStack.isEmpty())
        }
    }

    @Test fun `holding play at two playback positions records a source range`() {
        val vm = editor()
        val previews = mutableListOf<MediaClip>()
        compose.setContent {
            Box(Modifier.width(400.dp)) {
                EffectWorkspacePanel("project", clip, vm, false, previews::add, {}, {}, {})
            }
        }
        vm.setPlayerPosition(400L, forceStateSync = true)
        compose.runOnIdle { }
        compose.onNodeWithTag("workspace-play-range-control").performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        vm.setPlayerPosition(1_400L, forceStateSync = true)
        compose.runOnIdle { }
        compose.onNodeWithTag("workspace-play-range-control").performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        compose.onNodeWithText("Monochrome").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle {
            val effect = previews.last().workspaceEffects.single()
            assertEquals(400L, effect.startMs)
            assertEquals(1_400L, effect.endMs)
            assertFalse(vm.state.value.canUndo)
        }
    }

    @Test fun `failed preview is explicitly unavailable and cannot be applied`() {
        val vm = editor()
        compose.setContent {
            Box(Modifier.width(400.dp)) {
                EffectWorkspacePanel("project", clip, vm, false, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("Monochrome").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { vm.setPlayerError("Shader unavailable") }
        compose.onNodeWithText("Apply").assertIsNotEnabled()
        compose.onNodeWithText("Preview unavailable:", substring = true).assertExists()
        compose.runOnIdle {
            assertEquals(clip, vm.state.value.project!!.clips.single())
            assertTrue(vm.undoStack.isEmpty())
        }
    }

    @Test fun `holding a preset records playback until release without saving early`() {
        val vm = editor()
        val previews = mutableListOf<MediaClip>()
        compose.setContent {
            Box(Modifier.width(400.dp)) {
                EffectWorkspacePanel("project", clip, vm, false, previews::add, {}, {}, {})
            }
        }
        compose.runOnIdle { vm.setPlayerPosition(400L, forceStateSync = true) }
        compose.onNodeWithText("Monochrome").performTouchInput { longClick(durationMillis = 700L) }
        compose.runOnIdle {
            val effect = previews.last().workspaceEffects.single()
            assertTrue(effect.startMs <= 400L)
            assertTrue(effect.endMs > effect.startMs)
            assertTrue(effect.endMs < clip.trimEndMs)
            assertFalse(vm.state.value.isPlaying)
            assertTrue(vm.undoStack.isEmpty())
        }
        compose.onNodeWithText("Apply").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertEquals(1, vm.undoStack.size) }
    }

}
