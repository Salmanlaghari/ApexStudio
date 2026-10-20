package com.apexstudio.app.ui.screens.editor

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
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

        compose.onNodeWithText("Captions").performSemanticsAction(SemanticsActions.OnClick) { it() }
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
}
