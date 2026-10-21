package com.apexstudio.app.ui.components

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.apexstudio.app.BuildConfig
import com.apexstudio.app.ui.screens.editor.TopAppBarSection
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BuildIdentityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun editorBadgeOpensInstalledBuildDialog() {
        compose.setContent { TopAppBarSection() }
        compose.onNodeWithContentDescription("Build identity").assertIsDisplayed().performClick()
        compose.onNodeWithText("Installed build").assertIsDisplayed()
        compose.onNodeWithText(BuildIdentity.description).assertIsDisplayed()
        compose.onNodeWithText("Copy build info").performClick()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Installed build").assertDoesNotExist()
    }

    @Test fun packagedIdentityMatchesVisibleIdentity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val identity = Properties().apply {
            context.assets.open("build-identity.properties").use { load(it) }
        }
        val exactCommit = BuildConfig.SOURCE_COMMIT.matches(Regex("[0-9a-f]{40}"))
        assertTrue(exactCommit || BuildConfig.SOURCE_COMMIT == "unknown")
        if (BuildConfig.BUILD_RUN != "local") {
            assertTrue("CI artifacts must identify the exact checkout", exactCommit)
        }
        assertEquals(BuildConfig.SOURCE_COMMIT, identity.getProperty("commit"))
        assertEquals(BuildConfig.SOURCE_REF, identity.getProperty("ref"))
        assertEquals(BuildConfig.BUILD_RUN, identity.getProperty("run"))
    }
}
