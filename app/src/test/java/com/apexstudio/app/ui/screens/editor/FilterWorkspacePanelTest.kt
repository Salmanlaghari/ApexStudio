package com.apexstudio.app.ui.screens.editor

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.apexstudio.app.domain.model.*
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.undo
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FilterWorkspacePanelTest {
    @get:Rule val compose = createComposeRule()
    private val clip = MediaClip("v", "Video", "file:///v.mp4", 5000L, 1000L, 4000L)
    private fun editor(source: MediaClip = clip) = EditorViewModel().also {
        it._state.value = it.state.value.copy(project = Project("p", "P", 3000L, clips = listOf(source)), selectedClipId = source.id)
    }
    private fun showCard(tag: String) {
        if (tag in setOf("filter-source-card", "filter-tuning-card", "filter-customize-card") &&
            compose.onAllNodesWithText("Range & adjustments").fetchSemanticsNodes().isNotEmpty()
        ) {
            compose.onNodeWithTag("filter-details-toggle").performScrollTo().performClick()
        }
        compose.onNodeWithTag(tag).performScrollTo()
    }
    private fun choose(id: String) {
        showCard("filter-presets-card")
        compose.onNodeWithTag("filter-preset-$id").performScrollTo().performClick()
    }
    private fun moveStart() {
        showCard("filter-source-card")
        compose.onNodeWithTag("filter-source-range").performScrollTo()
        compose.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress) and hasAnyAncestor(hasTestTag("filter-source-range")),
            useUnmergedTree = true
        )[0].performSemanticsAction(SemanticsActions.SetProgress) { it(0.2f) }
    }

    @Test fun `customization and range are real previews but cancel preserves project and history`() {
        val vm = editor(); val before = vm.state.value.project
        var preview: MediaClip? = null; var closed = false
        compose.setContent { FilterWorkspacePanel("p", clip, vm, false, { preview = it }, {}, {}, { closed = true }) }
        choose(WorkspaceEffectPresets.FILTER_SOFT_WARM)
        showCard("filter-customize-card")
        compose.onNodeWithTag("filter-customize-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("filter-exposure").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        moveStart()
        compose.runOnIdle {
            val effect = preview!!.workspaceEffects.single()
            assertEquals(0.5f, effect.exposure, 0.01f)
            assertTrue(effect.startMs > clip.trimStartMs && effect.startMs < clip.trimEndMs)
            assertEquals(clip.trimEndMs, effect.endMs)
            assertEquals(before, vm.state.value.project)
            assertTrue(vm.undoStack.isEmpty())
        }
        compose.onNodeWithText("Cancel").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertTrue(closed); assertEquals(clip, preview)
            assertEquals(before, vm.state.value.project); assertTrue(vm.undoStack.isEmpty())
        }
    }

    @Test fun `multiple ranges preserve prior effects and apply as one undoable edit`() {
        val old = WorkspaceEffect(WorkspaceEffectPresets.CARTOON, 1000L, 4000L)
        val source = clip.copy(workspaceEffects = listOf(old)); val vm = editor(source)
        compose.setContent { FilterWorkspacePanel("p", source, vm, false, {}, {}, {}, {}) }
        choose(WorkspaceEffectPresets.FILTER_SOFT_WARM)
        showCard("filter-tuning-card")
        compose.onNodeWithTag("filter-new-range").performScrollTo().performClick()
        choose(WorkspaceEffectPresets.FILTER_COOL)
        moveStart()
        compose.onNodeWithTag("filter-apply").assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle {
            val effects = vm.state.value.project!!.clips.single().workspaceEffects
            assertEquals(3, effects.size); assertEquals(old, effects.first())
            assertEquals(1, vm.undoStack.size)
            vm.undo()
        }
        compose.runOnIdle { assertEquals(source, vm.state.value.project!!.clips.single()) }
    }

    @Test fun `selection changes disable stale drafts instead of editing another clip`() {
        val vm = editor(); val before = vm.state.value.project
        compose.setContent { FilterWorkspacePanel("p", clip, vm, false, {}, {}, {}, {}) }
        choose(WorkspaceEffectPresets.FILTER_SOFT_WARM)
        compose.runOnIdle { vm._state.value = vm.state.value.copy(selectedClipId = "another") }
        compose.onNodeWithTag("filter-apply").assertIsNotEnabled()
        compose.onNodeWithText("This clip changed", substring = true).assertExists()
        compose.runOnIdle { assertEquals(before, vm.state.value.project); assertTrue(vm.undoStack.isEmpty()) }
    }
    @Test fun `preview errors disable Apply without deleting the draft or project`() {
        val vm = editor(); val before = vm.state.value.project
        compose.setContent { FilterWorkspacePanel("p", clip, vm, false, {}, {}, {}, {}) }
        choose(WorkspaceEffectPresets.FILTER_SOFT_WARM)
        compose.runOnIdle { vm._state.value = vm.state.value.copy(playerError = "Unsupported GPU") }
        compose.onNodeWithTag("filter-apply").assertIsDisplayed().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(before, vm.state.value.project) }
    }

    @Test fun `filter gallery shows a generated sample image for each available look`() {
        val vm = editor()
        compose.setContent { FilterWorkspacePanel("p", clip, vm, false, {}, {}, {}, {}) }
        showCard("filter-presets-card")
        val sampleTag = "filter-sample-${WorkspaceEffectPresets.FILTER_SOFT_WARM}"
        compose.waitUntil(10_000L) {
            compose.onAllNodesWithTag(sampleTag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(sampleTag, useUnmergedTree = true).assertExists()
    }

}
