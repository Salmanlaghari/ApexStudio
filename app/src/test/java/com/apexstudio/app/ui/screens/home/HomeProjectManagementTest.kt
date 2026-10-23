package com.apexstudio.app.ui.screens.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.apexstudio.app.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeProjectManagementTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun projectsShortcutShowsCountAndRunsItsNavigationAction() {
        var clicked = false
        compose.setContent {
            ProjectsShortcut(projectCount = 3, onClick = { clicked = true })
        }

        compose.onNodeWithText("3 projects available").assertIsDisplayed()
        compose.onNodeWithTag("dashboard-projects-shortcut").performClick()
        compose.runOnIdle { assertTrue(clicked) }
    }

    @Test
    fun renameChangesOnlyTheNameAndRejectsBlankNames() {
        val project = Project(
            id = "saved-1",
            name = "Before",
            durationMs = 4_200L,
            resolution = "1080p",
            fps = 30,
            lastTransmissionTemplateId = "cinematic",
            lastTransitionType = "fade",
            lastTransitionDurationMs = 750L,
            coverFrameMs = 1_500L,
            lastEditedMs = 99L,
            isDraft = true
        )

        assertEquals(project.copy(name = "Renamed"), renameHomeProject(project, "  Renamed  "))
        assertNull(renameHomeProject(project, "   "))
    }

    @Test
    fun renameDialogReturnsEditedNameOnlyAfterExplicitSave() {
        var name by mutableStateOf("Old name")
        var savedName: String? = null
        compose.setContent {
            RenameProjectDialog(
                projectName = name,
                onNameChange = { name = it },
                onDismiss = {},
                onConfirm = { savedName = it }
            )
        }

        compose.onNodeWithTag("home-rename-project-dialog").assertIsDisplayed()
        compose.onNodeWithText("Old name").performTextInput(" new")
        compose.onNodeWithText("Save name").performClick()
        compose.runOnIdle { assertEquals("Old name new", savedName) }
    }

    @Test
    fun projectRowExposesSeparateRenameAndDeleteActions() {
        var opened = false
        var renamed = false
        var deleted = false
        val project = Project(id = "saved-2", name = "Trip edit", durationMs = 1_000L)
        compose.setContent {
            ProjectRow(
                project = project,
                onOpen = { opened = true },
                onRename = { renamed = true },
                onDelete = { deleted = true }
            )
        }

        compose.onNodeWithTag("home-project-rename-saved-2").performClick()
        compose.runOnIdle {
            assertTrue(renamed)
            assertFalse(deleted)
            assertFalse(opened)
        }
        compose.onNodeWithTag("home-project-delete-saved-2").performClick()
        compose.runOnIdle { assertTrue(deleted) }
    }

    @Test
    fun nonPersistedFallbackRowDoesNotOfferRenameOrDelete() {
        val project = Project(id = "fallback", name = "Example", durationMs = 1_000L)
        compose.setContent {
            ProjectRow(
                project = project,
                onOpen = {},
                onRename = {},
                onDelete = {},
                canManage = false
            )
        }

        compose.onNodeWithText("Example").assertIsDisplayed()
        compose.onNodeWithTag("home-project-rename-fallback").assertDoesNotExist()
        compose.onNodeWithTag("home-project-delete-fallback").assertDoesNotExist()
    }

    @Test
    fun deleteRequiresConfirmationAndNamesTheProject() {
        var confirmed = false
        var dismissed = false
        compose.setContent {
            DeleteProjectDialog(
                projectName = "Trip edit",
                onDismiss = { dismissed = true },
                onConfirm = { confirmed = true }
            )
        }

        compose.onNodeWithText("\"Trip edit\" and its saved editing data will be deleted. This cannot be undone.").assertIsDisplayed()
        compose.runOnIdle {
            assertFalse(confirmed)
            assertFalse(dismissed)
        }
        compose.onNodeWithText("Delete project").performClick()
        compose.runOnIdle { assertTrue(confirmed) }
    }
}
