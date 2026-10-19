package com.apexstudio.app.ui.screens.export

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.apexstudio.app.data.export.AudioMixExportNotice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioExportWarningTest {
    @get:Rule val compose = createComposeRule()

    @Test fun failedMixWarningIsVisibleAndClearsForANewExport() {
        val warning = mutableStateOf<String?>(null)
        compose.setContent { AudioExportWarning(warning.value) }
        compose.onNodeWithText(AudioMixExportNotice.MIX_FAILED).assertDoesNotExist()
        compose.runOnIdle { warning.value = AudioMixExportNotice.MIX_FAILED }
        compose.onNodeWithText(AudioMixExportNotice.MIX_FAILED).assertIsDisplayed()
        compose.runOnIdle { warning.value = null }
        compose.onNodeWithText(AudioMixExportNotice.MIX_FAILED).assertDoesNotExist()
    }

    @Test fun skippedTrackCountIsVisibleRatherThanAnUnqualifiedSuccess() {
        val text = AudioMixExportNotice.forSkippedTracks(2)!!
        compose.setContent { AudioExportWarning(text) }
        compose.onNodeWithText(text).assertIsDisplayed()
    }
}
