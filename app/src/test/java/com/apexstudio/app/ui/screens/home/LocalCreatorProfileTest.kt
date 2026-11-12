package com.apexstudio.app.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.apexstudio.app.ui.screens.settings.LocalCreatorProfile
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w375dp-h830dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LocalCreatorProfileTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun dashboardProfileEditsOnlyLocalDisplayNameAndAvatarSelection() {
        var savedProfile: LocalCreatorProfile? = null
        compose.setContent {
            Box(Modifier.requiredSize(375.dp, 830.dp)) {
                ApexHomeContent(
                    projects = emptyList(),
                    savedProjectIds = emptySet(),
                    searchOpen = false,
                    searchText = "",
                    showAllProjects = false,
                    onSearchOpen = {},
                    onSearchChange = {},
                    onNotifications = {},
                    onCreateVideo = {},
                    onCreatePhoto = {},
                    onOpenProject = {},
                    onRenameProject = {},
                    onDeleteProject = {},
                    onShowAll = {},
                    localProfile = LocalCreatorProfile(displayName = "Raza", avatarId = "violet"),
                    onSaveLocalProfile = { savedProfile = it }
                )
            }
        }

        compose.onNodeWithTag("dashboard-profile").assertIsDisplayed().performClick()
        compose.onNodeWithTag("local-profile-dialog").assertIsDisplayed()
        compose.onNodeWithText("This profile stays on this device. It does not create an account or sync online.").assertIsDisplayed()
        compose.onNodeWithTag("local-profile-name").performTextClearance()
        compose.onNodeWithTag("local-profile-name").performTextInput("Ayesha")
        compose.onNodeWithTag("profile-avatar-cyan").performClick()
        compose.onNodeWithText("Save profile").performClick()

        compose.runOnIdle {
            assertEquals(LocalCreatorProfile(displayName = "Ayesha", avatarId = "cyan"), savedProfile)
        }
    }
}
