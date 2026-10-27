package com.apexstudio.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w375dp-h830dp")
class BottomNavReferenceStyleTest {
    @get:Rule val compose = createComposeRule()

    private val exportItem = BottomNavItem(
        id = "export",
        label = "Export",
        icon = Icons.Default.IosShare,
        selectedColor = Color(0xFF9768FF)
    )

    @Test
    fun selectedAccentColorAloneKeepsTheGlassNavigationStyle() {
        compose.setContent {
            BottomNavBar(
                current = "export",
                onSelect = {},
                items = listOf(exportItem)
            )
        }

        compose.onNodeWithTag("bottom-nav-glass-style").assertIsDisplayed()
    }

    @Test
    fun targetScreensCanExplicitlyUseTheReferenceNavigationStyle() {
        compose.setContent {
            BottomNavBar(
                current = "export",
                onSelect = {},
                items = listOf(exportItem),
                referenceStyle = true
            )
        }

        compose.onNodeWithTag("bottom-nav-reference-style").assertIsDisplayed()
    }
}
