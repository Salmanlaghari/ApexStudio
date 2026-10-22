package com.apexstudio.app.ui.screens.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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

    @Test fun templateWithPipSelectedTargetsMainPreviewVideoAndDoesNothingUntilTapped() {
        val main = MediaClip(
            id = "dragon", name = "Dragon", uri = "file:///dragon.mp4",
            durationMs = 10_000L, trimEndMs = 10_000L,
        )
        val pip = MediaClip(
            id = "pip", name = "PiP", uri = "file:///pip.mp4",
            durationMs = 4_000L, trimEndMs = 4_000L, trackIndex = 1,
            type = com.apexstudio.app.domain.model.ClipType.OVERLAY,
        )
        val project = Project("project", "Project", 10_000L, clips = listOf(main, pip))
        val vm = EditorViewModel().also {
            it._state.value = it.state.value.copy(
                project = project, selectedClipId = pip.id, playerPositionMs = 7_000L,
            )
        }
        compose.setContent { MaterialTheme {
            QuickVideoPresetsOverlay(vm, QuickVideoPresetSection.TEMPLATES) {}
        } }

        compose.onNodeWithText("Applies to main preview video: Dragon. PiP selection is unchanged.").assertExists()
        val recipe = com.apexstudio.app.data.template.ProjectVideoPresets.videoTemplates.first()
        compose.onNodeWithText("Each recipe applies only its named color look and title as one undoable edit; it does not build a montage or change timing.").assertExists()
        compose.onNodeWithText("Color: Warm Film · Title: Bold Title · YOUR STORY").assertExists()
        compose.onNodeWithTag("quick_video_template_${recipe.id}")
            .assertIsEnabled()
        compose.runOnIdle {
            assertEquals(project, vm.state.value.project)
            assertTrue(vm.undoStack.isEmpty())
        }

        compose.onNodeWithTag("quick_video_template_${recipe.id}")
            .performClick()
        compose.runOnIdle {
            val changed = vm.state.value.project!!
            val editedMain = changed.clips.first { it.id == main.id }
            assertNotEquals(main, editedMain)
            assertEquals(recipe.effectId, editedMain.projectVideoEffectId)
            assertEquals(recipe.textTemplateId, editedMain.textOverlays.single().presetId)
            assertEquals("YOUR STORY", editedMain.textOverlays.single().text)
            assertEquals(pip, changed.clips.first { it.id == pip.id })
            assertEquals(pip.id, vm.state.value.selectedClipId)
            assertEquals(editedMain.textOverlays.single().id, vm.state.value.selectedTextOverlayId)
            assertEquals(1, vm.undoStack.size)
            assertEquals(project, vm.undoStack.single().project)
        }
    }

    @Test fun templatesFollowMainPreviewAtPlayheadAcrossMultipleMainClips() {
        val first = MediaClip(
            id = "first", name = "First", uri = "file:///first.mp4",
            durationMs = 4_000L, trimEndMs = 4_000L,
        )
        val second = MediaClip(
            id = "second", name = "Second", uri = "file:///second.mp4",
            durationMs = 10_000L, trimEndMs = 10_000L,
        )
        val project = Project("project", "Project", 14_000L, clips = listOf(first, second))
        val vm = EditorViewModel().also {
            // Selection can be another main clip; the preview source at 5s is the second clip.
            it._state.value = it.state.value.copy(
                project = project, selectedClipId = first.id, playerPositionMs = 5_000L,
            )
        }
        compose.setContent { MaterialTheme {
            QuickVideoPresetsOverlay(vm, QuickVideoPresetSection.TEMPLATES) {}
        } }

        compose.onNodeWithText("Applies to main preview video: Second. PiP selection is unchanged.").assertExists()
        compose.onNodeWithTag("quick_video_template_${com.apexstudio.app.data.template.ProjectVideoPresets.videoTemplates.first().id}")
            .assertIsEnabled().performClick()
        compose.runOnIdle {
            val changed = vm.state.value.project!!
            assertEquals(first, changed.clips.first { it.id == first.id })
            assertNotEquals(second, changed.clips.first { it.id == second.id })
            assertEquals(first.id, vm.state.value.selectedClipId)
        }
    }

    @Test fun templatesStayDisabledAndExplainMissingMainSource() {
        val pip = MediaClip(
            id = "pip", name = "PiP", uri = "file:///pip.mp4",
            durationMs = 4_000L, trimEndMs = 4_000L, trackIndex = 1,
            type = com.apexstudio.app.domain.model.ClipType.OVERLAY,
        )
        val project = Project("project", "Project", 4_000L, clips = listOf(pip))
        val vm = EditorViewModel().also {
            it._state.value = it.state.value.copy(project = project, selectedClipId = pip.id)
        }
        compose.setContent { MaterialTheme {
            QuickVideoPresetsOverlay(vm, QuickVideoPresetSection.TEMPLATES) {}
        } }

        compose.onNodeWithText("No main-track media is available. Add a main-track video first.").assertExists()
        compose.onNodeWithTag("quick_video_template_${com.apexstudio.app.data.template.ProjectVideoPresets.videoTemplates.first().id}")
            .assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(project, vm.state.value.project)
            assertTrue(vm.undoStack.isEmpty())
        }
    }

    @Test fun enhanceRequiresExplicitMainPreviewSelectionWhenPipIsSelected() {
        val main = MediaClip(
            id = "main", name = "Main footage", uri = "file:///main.mp4",
            durationMs = 10_000L, trimEndMs = 10_000L,
        )
        val pip = MediaClip(
            id = "pip", name = "PiP", uri = "file:///pip.mp4",
            durationMs = 4_000L, trimEndMs = 4_000L, trackIndex = 1,
            type = com.apexstudio.app.domain.model.ClipType.OVERLAY,
        )
        val project = Project("project", "Project", 10_000L, clips = listOf(main, pip))
        val vm = EditorViewModel().also {
            it._state.value = it.state.value.copy(project = project, selectedClipId = pip.id)
        }
        compose.setContent { MaterialTheme {
            QuickVideoPresetsOverlay(vm, QuickVideoPresetSection.ENHANCE) {}
        } }

        compose.onNodeWithTag("quick_enhance_espcn").assertIsNotEnabled()
        compose.onNodeWithTag("quick_enhance_select_main_preview").assertExists().assertIsEnabled()
        compose.onNodeWithText("Use main preview video: Main footage").assertExists()
        compose.runOnIdle {
            assertEquals(project, vm.state.value.project)
            assertEquals(pip.id, vm.state.value.selectedClipId)
            assertFalse(vm.videoUpscaleUi.value.processing)
            assertTrue(vm.undoStack.isEmpty())
        }

        compose.onNodeWithTag("quick_enhance_select_main_preview").performClick()
        compose.runOnIdle {
            assertEquals(project, vm.state.value.project)
            assertEquals(main.id, vm.state.value.selectedClipId)
            assertFalse(vm.videoUpscaleUi.value.processing)
            assertTrue(vm.undoStack.isEmpty())
        }
        compose.onNodeWithTag("quick_enhance_espcn").assertIsEnabled()
    }

}
