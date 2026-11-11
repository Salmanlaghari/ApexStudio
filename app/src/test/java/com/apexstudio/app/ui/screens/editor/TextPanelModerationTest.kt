package com.apexstudio.app.ui.screens.editor

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsActions
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.presentation.viewmodel.CaptionUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TextPanelModerationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun captionEntryStartsOnActualControlsWithoutRunningRecognition() {
        var starts = 0
        compose.setContent {
            TextPanel(overlays = emptyList(), selectedId = null, onAdd = {}, onSelect = {},
                onTextChange = {}, onColorChange = {}, onBgChange = {}, onSizeChange = {},
                onDelete = {}, onClose = {}, initialTab = TextPanelTab.CAPTIONS,
                onAutoCaptions = { starts++ })
        }
        compose.onNodeWithText("Generate Auto Captions").assertIsEnabled()
        compose.runOnIdle { assertEquals(0, starts) }
    }

    @Test
    fun existingVideoCaptionActionIsEnabledWithoutClassifierApprovalAndClearingRemainsAvailable() {
        var clearCalls = 0
        var generateCalls = 0
        compose.setContent {
            TextPanel(
                overlays = listOf(TextOverlay(id = "existing-caption", text = "Manual caption")),
                selectedId = "existing-caption",
                onAdd = {},
                onSelect = {},
                onTextChange = {},
                onColorChange = {},
                onBgChange = {},
                onSizeChange = {},
                onDelete = {},
                onClose = {},
                onAutoCaptions = { generateCalls++ },
                captionUiState = CaptionUiState(),
                onClearCaptions = { clearCalls++ },
                hasAutoCaptions = true,
            )
        }

        compose.onNodeWithText("Auto captions").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("Generate Auto Captions")
            .assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("Clear Auto Captions")
            .assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }

        compose.runOnIdle {
            assertEquals(1, generateCalls)
            assertEquals(1, clearCalls)
        }
    }

    @Test
    fun addTextAndEditFieldInvokeTheirActualCallbacks() {
        var addCalls = 0
        val edits = mutableListOf<String>()
        compose.setContent {
            TextPanel(
                overlays = listOf(TextOverlay(id = "title", text = "Editable title")),
                selectedId = "title",
                onAdd = { addCalls++ },
                onSelect = {},
                onTextChange = { edits += it },
                onColorChange = {},
                onBgChange = {},
                onSizeChange = {},
                onDelete = {},
                onClose = {}
            )
        }

        compose.onNodeWithTag("text-add-overlay").assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithTag("text-content-field").performTextInput(" revised")
        compose.runOnIdle {
            assertEquals(1, addCalls)
            org.junit.Assert.assertTrue(edits.last().contains("revised"))
        }
    }

    @Test
    fun emptyTextFlowExplainsHowToStartAndAddsAnEditableLayer() {
        var addCalls = 0
        compose.setContent {
            TextPanel(overlays = emptyList(), selectedId = null, onAdd = { addCalls++ }, onSelect = {},
                onTextChange = {}, onColorChange = {}, onBgChange = {}, onSizeChange = {},
                onDelete = {}, onClose = {})
        }

        compose.onNodeWithTag("text-quick-guide").assertIsDisplayed()
        compose.onNodeWithText("2 · Add your words").assertIsDisplayed()
        compose.onNodeWithTag("text-empty-add").performClick()
        compose.runOnIdle { assertEquals(1, addCalls) }
    }

    @Test
    fun editTabShowsTapToApplyStylePreviewsAndKeepsExistingText() {
        val starter = com.apexstudio.app.data.text.TextPresetEngine.getPresetsForCategory("Cinematic").first()
        var applied: com.apexstudio.app.data.text.TextPreset? = null
        compose.setContent {
            TextPanel(overlays = listOf(TextOverlay(id = "title", text = "My words")), selectedId = "title",
                onAdd = {}, onSelect = {}, onTextChange = {}, onColorChange = {}, onBgChange = {}, onSizeChange = {},
                onDelete = {}, onClose = {}, onApplyPreset = { applied = it })
        }

        compose.onNodeWithTag("text-starter-gallery").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("text-card-swipe-hint").assertExists()
        compose.onNodeWithTag("text-starter-style-${starter.id}").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(starter.id, applied?.id) }
    }

    @Test
    fun stylesTabShowsSampleTextOnDesignAndMotionCards() {
        val preset = com.apexstudio.app.data.text.TextDesignMotionPack.presets.first()
        compose.setContent {
            TextPanel(overlays = listOf(TextOverlay(id = "title", text = "My words")), selectedId = "title",
                onAdd = {}, onSelect = {}, onTextChange = {}, onColorChange = {}, onBgChange = {}, onSizeChange = {},
                onDelete = {}, onClose = {})
        }

        compose.onNodeWithTag("text-tab-presets").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithTag("text-design-motion-${preset.id}").performScrollTo().assertExists()
        org.junit.Assert.assertTrue(compose.onAllNodesWithText("Your title").fetchSemanticsNodes().isNotEmpty())
    }
}
