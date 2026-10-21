package com.apexstudio.app.ui.screens.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import org.robolectric.annotation.GraphicsMode
import org.junit.Assert.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuickVideoPresetsOverlayTest {
    @get:Rule val compose = createComposeRule()

    @Test fun enhanceOffersRealOfflineUpscaleSeparatelyFromColorMatrices() {
        val clip = MediaClip(
            id = "video", name = "Video", uri = "file:///video.mp4",
            durationMs = 10_000L, trimEndMs = 10_000L,
        )
        val vm = EditorViewModel().also {
            it._state.value = it.state.value.copy(
                project = Project("project", "Project", 10_000L, clips = listOf(clip)),
                selectedClipId = clip.id,
            )
        }
        compose.setContent { MaterialTheme {
            QuickVideoPresetsOverlay(vm, QuickVideoPresetSection.ENHANCE) {}
        } }

        compose.onNodeWithTag("quick_enhance_espcn").assertExists().assertIsEnabled()
        compose.onNodeWithText("Offline ESPCN x3 · licensed ONNX Model Zoo model.", substring = true).assertExists()
        compose.onNodeWithText("Source limits: even dimensions up to 640×360 (360×640 portrait), 30 seconds, 30 fps, SDR, and silent video or one AAC track.", substring = true).assertExists()
        compose.onNodeWithText("After success, preview and export use the same processed video file", substring = true).assertExists()
    }
    @Test fun switchingProjectAndSelectionRetargetsTheOpenPresetPanel() {
        val first = MediaClip("one", "One", "file:///one.mp4", durationMs = 5000L, trimEndMs = 5000L)
        val second = first.copy(id = "two", uri = "file:///two.mp4")
        val vm = EditorViewModel()
        vm._state.value = vm.state.value.copy(project = Project("old", "Old", 5000L, clips = listOf(first)), selectedClipId = first.id)
        compose.setContent { MaterialTheme { QuickVideoPresetsOverlay(vm, QuickVideoPresetSection.ENHANCE) {} } }
        compose.runOnIdle {
            vm._state.value = vm.state.value.copy(project = Project("new", "New", 5000L, clips = listOf(second)), selectedClipId = second.id)
        }
        val recipe = com.apexstudio.app.data.template.ProjectVideoPresets.videoEnhancements.first()
        compose.onNodeWithText(recipe.label).assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle {
            assertEquals("new", vm.state.value.project!!.id)
            assertNotEquals(second, vm.state.value.project!!.clips.single())
            assertEquals(1, vm.undoStack.size)
            assertEquals("new", vm.undoStack.single().project!!.id)
        }
    }

}
