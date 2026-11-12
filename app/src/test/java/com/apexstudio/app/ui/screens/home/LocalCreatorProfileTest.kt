package com.apexstudio.app.ui.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.apexstudio.app.ui.screens.settings.LocalCreatorProfile
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun profileAvatarActionRequestsOpeningTheLocalProfile() {
        var opened = false
        compose.setContent {
            ProfileAvatarAction(
                profile = LocalCreatorProfile(displayName = "Raza", avatarId = "violet"),
                onClick = { opened = true }
            )
        }

        compose.onNodeWithTag("dashboard-profile").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(opened) }
    }

    @Test
    fun profileSaveActionIsDisabledWhilePersistenceIsInProgress() {
        var saveCount = 0
        compose.setContent {
            LocalCreatorProfileSaveAction(enabled = false, label = "Saving…") {
                saveCount += 1
            }
        }

        compose.onNodeWithText("Saving…").assertIsDisplayed().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, saveCount) }
    }

    @Test
    fun localProfileSaveDefaultsBlankNamesToCreator() {
        assertEquals(LocalCreatorProfile(displayName = "Creator", avatarId = "amber"), localCreatorProfileForSave("   ", "amber"))
    }

    @Test
    fun localProfileSaveUsesSharedSanitization() {
        assertEquals(
            LocalCreatorProfile(displayName = "A B", avatarId = "violet"),
            localCreatorProfileForSave(" A\tB\n\u200B ", "unknown")
        )
    }

    @Test
    fun failedProfileSaveIsReportedAndCanBeRetried() = runBlocking {
        val profile = LocalCreatorProfile(displayName = "Ayesha", avatarId = "cyan")
        var attempts = 0

        assertFalse(
            trySaveLocalCreatorProfile(profile) {
                attempts += 1
                throw IOException("simulated storage failure")
            }
        )
        assertTrue(trySaveLocalCreatorProfile(profile) { attempts += 1 })
        assertEquals(2, attempts)
    }

    @Test
    fun profileSavePropagatesCoroutineCancellation() = runBlocking {
        var propagated = false
        try {
            trySaveLocalCreatorProfile(LocalCreatorProfile()) {
                throw CancellationException("cancelled")
            }
        } catch (_: CancellationException) {
            propagated = true
        }
        assertTrue(propagated)
    }

    @Test
    fun localProfileFormEditsOnlyLocalDisplayNameAndAvatarSelection() {
        var displayName by mutableStateOf("Raza")
        var avatarId by mutableStateOf("violet")
        var savedProfile: LocalCreatorProfile? = null
        compose.setContent {
            Column {
                LocalCreatorProfileForm(
                    displayName = displayName,
                    onDisplayNameChange = { displayName = it.take(24) },
                    avatarId = avatarId,
                    onAvatarSelected = { avatarId = it }
                )
                LocalCreatorProfileSaveAction {
                    savedProfile = localCreatorProfileForSave(displayName, avatarId)
                }
            }
        }

        // Test the production form and its production save action without Robolectric's platform dialog window.
        compose.onNodeWithText(
            "This profile is stored locally and is not synced by ApexStudio. Android backup or transfer settings may move app data to another device."
        ).assertIsDisplayed()
        compose.runOnIdle { assertNull(savedProfile) }
        compose.onNodeWithTag("local-profile-name").performTextClearance()
        compose.onNodeWithTag("local-profile-name").performTextInput(" Ayesha ")
        compose.onNodeWithTag("profile-avatar-cyan").performClick()
        compose.onNodeWithText("Save profile").performClick()

        compose.runOnIdle {
            assertEquals(LocalCreatorProfile(displayName = "Ayesha", avatarId = "cyan"), savedProfile)
        }
    }
}
