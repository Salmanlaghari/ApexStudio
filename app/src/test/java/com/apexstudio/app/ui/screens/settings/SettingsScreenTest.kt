package com.apexstudio.app.ui.screens.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.apexstudio.app.ui.theme.ApexThemeOption
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h720dp-mdpi")
class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun bothContactsShowOwnerEmailAndReportIsNotDuplicated() {
        compose.setContent { MaterialTheme { SettingsScreen(onBack = {}, onOpenDiagnostics = {}) } }
        compose.onNodeWithText("Suggestions").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("laghariprince2@gmail.com").assertCountEquals(2)
        compose.onAllNodesWithText("Report a problem").assertCountEquals(1)
    }

    @Test
    fun policyEntryOpensReadablePrivacyPolicy() {
        compose.setContent { MaterialTheme { SettingsScreen(onBack = {}, onOpenDiagnostics = {}) } }
        compose.onNodeWithText("Policy").performScrollTo().performClick()
        compose.onNodeWithText("Privacy policy").assertIsDisplayed()
        compose.onNodeWithText("Media and projects").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Privacy policy").assertDoesNotExist()
    }

    @Test
    fun choosingAThemePersistsTheActualPreference() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking { SettingsPreferences.setTheme(context, ApexThemeOption.NEON) }
        compose.setContent { MaterialTheme { SettingsScreen(onBack = {}, onOpenDiagnostics = {}) } }
        compose.onNodeWithText("Themes").performScrollTo().performClick()
        compose.onNodeWithText("Ember Night").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            runBlocking { SettingsPreferences.theme(context).first() } == ApexThemeOption.EMBER
        }
        assertEquals(ApexThemeOption.EMBER, runBlocking { SettingsPreferences.theme(context).first() })
        compose.onNodeWithText("Ember Night").assertIsDisplayed()
        runBlocking { SettingsPreferences.setTheme(context, ApexThemeOption.NEON) }
    }
}
