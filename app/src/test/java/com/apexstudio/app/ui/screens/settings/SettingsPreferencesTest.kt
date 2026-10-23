package com.apexstudio.app.ui.screens.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.apexstudio.app.ui.theme.ApexThemeOption
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsPreferencesTest {
    @Test
    fun unknownOrMissingThemeUsesDefaultAndSelectionsPersist() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(ApexThemeOption.default, ApexThemeOption.fromStoredValue(null))
        assertEquals(ApexThemeOption.default, ApexThemeOption.fromStoredValue("unrecognized"))

        SettingsPreferences.setTheme(context, ApexThemeOption.EMBER)
        assertEquals(ApexThemeOption.EMBER, SettingsPreferences.theme(context).first())
        SettingsPreferences.setTheme(context, ApexThemeOption.VIOLET)
        assertEquals(ApexThemeOption.VIOLET, SettingsPreferences.theme(context).first())

        // Leave a predictable default for other tests sharing the Robolectric process.
        SettingsPreferences.setTheme(context, ApexThemeOption.default)
    }

    @Test
    fun themesHaveDistinctAccentsAndPolicyCoversCurrentDataPaths() {
        assertNotEquals(ApexThemeOption.NEON.primary, ApexThemeOption.VIOLET.primary)
        assertNotEquals(ApexThemeOption.VIOLET.primary, ApexThemeOption.EMBER.primary)
        val policy = SettingsPolicyCopy.sections.joinToString(" ") { "${it.title} ${it.body}" }
        assertTrue(policy.contains("Jamendo"))
        assertTrue(policy.contains("GitHub"))
        assertTrue(policy.contains("Snap"))
        assertTrue(policy.contains("Android device backup"))
        assertTrue(policy.contains("no prompt-based generative AI service"))
        assertTrue(policy.contains("public privacy-policy URL"))
    }
}
