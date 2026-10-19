package com.apexstudio.app.ui.screens.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.apexstudio.app.data.template.ProjectVideoPresets
import org.junit.Assert.assertEquals
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
        compose.setContent { MaterialTheme {
            ProjectVideoPresetsPanel(true, "Existing clip", null, { textId = it }, { effectId = it }, {})
        } }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        val text = ProjectVideoPresets.textTemplates.first()
        compose.onNodeWithTag("video_text_template_${text.id}").activateVisibleControl()
        compose.runOnIdle { assertEquals(text.id, textId); assertEquals(null, effectId) }
        val effect = ProjectVideoPresets.effects.first()
        compose.onNodeWithTag("video_effect_${effect.id}").activateVisibleControl()
        compose.runOnIdle { assertEquals(effect.id, effectId) }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
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
