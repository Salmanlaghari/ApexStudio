package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.apexstudio.app.data.packs.download.PackManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w375dp-h830dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FxSearchBrowserTest {
    @get:Rule val compose = createComposeRule()

    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun clearSearchHistory() {
        FxSearchCatalog.clearRecent(context)
    }

    @Test
    fun `suggestions search and select a real effect while downloads remain opt in`() {
        val selectedFx = mutableStateOf<String?>(null)
        val packState = mutableStateOf<PackManager.PackState>(PackManager.PackState.NotDownloaded)
        var downloadRequests = 0
        compose.setContent {
            Box(Modifier.width(375.dp).height(520.dp)) {
                FxPanel(
                    activeFxId = selectedFx.value,
                    intensity = 0.7f,
                    onFxSelected = { selectedFx.value = it },
                    onIntensityChange = {},
                    effectsPackState = packState.value,
                    onDownloadEffectsPack = {
                        downloadRequests++
                        packState.value = PackManager.PackState.Downloading(0L, 5_200L)
                    },
                    onClose = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Search effects").performClick()
        compose.onNodeWithTag("fx-search-field").assertIsDisplayed()
        compose.onNodeWithTag("fx-search-suggestion-glitch").performClick()
        compose.onNodeWithTag("fx-search-results-grid").assertIsDisplayed()
        compose.onNodeWithTag("fx-search-result-glitch").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals("glitch", selectedFx.value)
            assertEquals(0, downloadRequests)
            assertEquals(listOf("glitch"), FxSearchCatalog.loadRecent(context))
        }

        compose.onNodeWithTag("fx-search-download-pack").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, downloadRequests) }

        compose.onNodeWithContentDescription("Clear effect search").performClick()
        compose.onNodeWithTag("fx-search-recent-glitch").assertIsDisplayed()
        compose.onNodeWithTag("fx-search-clear-recent").performClick()
        compose.onNodeWithText("Your searches appear here after you preview an effect.").assertIsDisplayed()
        compose.runOnIdle { assertTrue(FxSearchCatalog.loadRecent(context).isEmpty()) }
    }
}
