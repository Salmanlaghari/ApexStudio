package com.apexstudio.app.ui.screens.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModelDownloadsDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun openingShowsExplicitDownloadChoicesWithoutStartingProgress() {
        var dismissed = 0
        compose.setContent { MaterialTheme { ModelDownloadsDialog(onDismiss = { dismissed++ }) } }
        compose.onNodeWithText("Optional AI models").assertExists()
        assertTrue(compose.onAllNodesWithText("Download").fetchSemanticsNodes().isNotEmpty())
        compose.onAllNodesWithText("Cancel").assertCountEquals(0)
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle { assertEquals(1, dismissed) }
    }
}
