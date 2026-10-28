package com.apexstudio.app.ui.screens.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreviewProcessingOverlayTest {
    @get:Rule val compose = createComposeRule()

    @Test fun determinateProgressIsCenteredAnnouncedAndCancellable() {
        var cancels = 0
        compose.setContent {
            MaterialTheme {
                PreviewProcessingOverlay(
                    title = "Enhancing video",
                    status = "About halfway done",
                    progress = .45f,
                    onCancel = { cancels++ },
                )
            }
        }
        compose.onNodeWithTag("preview_processing_overlay").assertIsDisplayed()
        compose.onNodeWithContentDescription("Enhancing video. About halfway done. 45 percent complete")
            .assertIsDisplayed()
        compose.onNodeWithText("45%").assertIsDisplayed()
        compose.onNodeWithTag("preview_processing_cancel").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, cancels) }
    }

    @Test fun indeterminateImportProgressStillHasReadableAccessibleStatus() {
        compose.setContent {
            MaterialTheme {
                PreviewProcessingOverlay(
                    title = "Preparing video",
                    status = "Checking the selected file",
                    progress = null,
                )
            }
        }
        compose.onNodeWithContentDescription("Preparing video. Checking the selected file")
            .assertIsDisplayed()
        compose.onNodeWithText("Preparing video").assertIsDisplayed()
        compose.onNodeWithText("Checking the selected file").assertIsDisplayed()
    }
}
