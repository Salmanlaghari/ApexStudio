package com.apexstudio.app.ui.screens.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.apexstudio.app.data.template.ProjectVideoPresets
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProjectVideoPresetsPanelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fixedChoicesHaveNoTextEntryAndInvokeOnlySelectedPreset() {
        var textId: String? = null
        var effectId: String? = null
        var templateId: String? = null
        var motionId: String? = null
        compose.setContent { MaterialTheme {
            ProjectVideoPresetsPanel(
                true, "Existing clip", null, { textId = it }, { effectId = it }, {},
                onVideoTemplate = { templateId = it }, onTextMotion = { motionId = it },
            )
        } }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        val text = ProjectVideoPresets.textTemplates.first()
        compose.onNodeWithTag("video_text_template_${text.id}").activateVisibleControl()
        compose.runOnIdle { assertEquals(text.id, textId); assertEquals(null, effectId) }
        val effect = ProjectVideoPresets.effects.first()
        compose.onNodeWithTag("video_effect_${effect.id}").activateVisibleControl()
        compose.runOnIdle { assertEquals(effect.id, effectId) }
        val template = ProjectVideoPresets.videoTemplates.first()
        compose.onNodeWithTag("video_template_${template.id}").activateVisibleControl()
        compose.runOnIdle { assertEquals(template.id, templateId) }
        val motion = ProjectVideoPresets.textMotions.first()
        compose.onNodeWithTag("video_text_motion_${motion.id}").activateVisibleControl()
        compose.runOnIdle { assertEquals(motion.id, motionId) }
        val enhancement = ProjectVideoPresets.videoEnhancements.first()
        compose.onNodeWithTag("video_enhancement_${enhancement.id}").activateVisibleControl()
        compose.runOnIdle { assertEquals(enhancement.id, effectId) }
        compose.onNodeWithText("Video Enhance · Deterministic Color").assertExists()
        compose.onNodeWithText("Learned Video Upscale").assertDoesNotExist()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test fun projectOverlayEditsThePreviewVideoWhenAnOverlayIsSelected() {
        val main = MediaClip("main", "Main footage", "file:///main.mp4", 10_000L, trimEndMs = 10_000L)
        val overlay = MediaClip("pip", "Overlay", "file:///pip.mp4", 4_000L, trimEndMs = 4_000L)
            .copy(type = ClipType.OVERLAY, trackIndex = 1)
        val project = Project("project", "Project", 10_000L, clips = listOf(main, overlay))
        val vm = EditorViewModel().also {
            it._state.value = it.state.value.copy(
                project = project, selectedClipId = overlay.id, playerPositionMs = 1_000L
            )
        }
        compose.setContent { MaterialTheme { ProjectVideoPresetsOverlay(vm, onClose = {}) } }
        compose.mainClock.advanceTimeBy(1_000)

        compose.onNodeWithText("Target video: Main footage").assertExists()
        val effect = ProjectVideoPresets.effects.first()
        compose.onNodeWithTag("video_effect_${effect.id}").activateVisibleControl()
        compose.runOnIdle {
            val edited = vm.state.value.project!!
            assertNotEquals(main, edited.clips.first { it.id == main.id })
            assertEquals(overlay, edited.clips.first { it.id == overlay.id })
            assertEquals(overlay.id, vm.state.value.selectedClipId)
            assertEquals(1, vm.undoStack.size)
        }
    }

    @Test fun unsafeMontageSourceKeepsSafeTitleAndEffectActionsEnabled() {
        var textApplied = false
        var effectApplied = false
        compose.setContent { MaterialTheme {
            ProjectVideoPresetsPanel(
                eligible = true,
                targetName = "Animated clip",
                feedback = null,
                onTextTemplate = { textApplied = true },
                onVideoEffect = { effectApplied = true },
                onClose = {},
                onVideoTemplate = {},
                templateEligible = false,
                templateBlockReason = "Templates cannot safely split an animated source.",
            )
        } }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Templates cannot safely split an animated source.").assertExists()
        compose.onNodeWithTag("video_template_warm-story").assertIsNotEnabled()
        val text = ProjectVideoPresets.textTemplates.first()
        compose.onNodeWithTag("video_text_template_${text.id}").activateVisibleControl()
        compose.runOnIdle { assertEquals(true, textApplied) }
        val effect = ProjectVideoPresets.effects.first()
        compose.onNodeWithTag("video_effect_${effect.id}").activateVisibleControl()
        compose.runOnIdle { assertEquals(true, effectApplied) }
    }

    @Test fun noVideoMeansAllPresetActionsAreDisabled() {
        compose.setContent { MaterialTheme {
            ProjectVideoPresetsPanel(false, null, null, { error("No image editing") }, { error("No image editing") }, {})
        } }
        ProjectVideoPresets.textTemplates.forEach {
            compose.onNodeWithTag("video_text_template_${it.id}").assertIsNotEnabled()
        }
        ProjectVideoPresets.effects.forEach {
            compose.onNodeWithTag("video_effect_${it.id}").assertIsNotEnabled()
        }
        ProjectVideoPresets.videoTemplates.forEach {
            compose.onNodeWithTag("video_template_${it.id}").assertIsNotEnabled()
        }
        ProjectVideoPresets.textMotions.forEach {
            compose.onNodeWithTag("video_text_motion_${it.id}").assertIsNotEnabled()
        }
        ProjectVideoPresets.videoEnhancements.forEach {
            compose.onNodeWithTag("video_enhancement_${it.id}").assertIsNotEnabled()
        }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    private fun SemanticsNodeInteraction.activateVisibleControl() {
        performScrollTo()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertIsDisplayed()
        performSemanticsAction(SemanticsActions.OnClick) { action -> action() }
        compose.waitForIdle()
    }
}
