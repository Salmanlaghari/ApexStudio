package com.apexstudio.app.ui.screens.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalChallengeOverlayTest {
    @get:Rule val compose = createComposeRule()
    private fun editor(): EditorViewModel {
        val clip = MediaClip(id = "v", name = "V", uri = "file:///v.mp4", durationMs = 5000L, trimEndMs = 5000L)
        return EditorViewModel().also { it._state.value = it.state.value.copy(
            project = Project("p", "P", 5000L, clips = listOf(clip)), selectedClipId = clip.id) }
    }
    @Test fun applyCreatesEditableTitleButDoesNotPublish() {
        val vm = editor()
        var closed = false
        compose.setContent { MaterialTheme { LocalChallengeOverlay(vm) { closed = true } } }
        compose.onNodeWithText("Apply title").assertIsNotEnabled()
        compose.onNodeWithText("Challenge hashtag").performTextInput("#OneTake")
        compose.onNodeWithText("Apply title").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.runOnIdle {
            assertTrue(closed)
            assertEquals("#OneTake", vm.state.value.project!!.clips.single().textOverlays.single().text)
            assertEquals(1, vm.undoStack.size)
            assertNull(vm.export.value.outputUri)
        }
    }
    @Test fun cancelLeavesProjectAndHistoryUnchanged() {
        val vm = editor()
        val before = vm.state.value.project
        var closed = false
        compose.setContent { MaterialTheme { LocalChallengeOverlay(vm) { closed = true } } }
        compose.onNodeWithText("Challenge hashtag").performTextInput("#Draft")
        compose.onNodeWithText("Cancel").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.runOnIdle {
            assertTrue(closed)
            assertEquals(before, vm.state.value.project)
            assertTrue(vm.undoStack.isEmpty())
        }
    }
}
