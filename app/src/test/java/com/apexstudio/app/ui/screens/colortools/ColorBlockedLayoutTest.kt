package com.apexstudio.app.ui.screens.colortools

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.state.EditorState
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.projectVisualScopeUnsupportedReason
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w375dp-h830dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ColorBlockedLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun blockedPreviewDoesNotDrawAComparisonHandleOverTheReason() {
        compose.setContent {
            Column(Modifier.requiredSize(375.dp, 600.dp)) {
                ColorTimelineComparisonPanel(null, "Visible PiP cannot be rendered; nothing is skipped.", .5f, {}, true, {})
            }
        }
        compose.onNodeWithText("Visible PiP cannot be rendered; nothing is skipped.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Drag to compare before and after").assertDoesNotExist()
        compose.onNodeWithText("Render Before / After").assertIsNotEnabled()
    }

    @Test fun actualImagePairStillOffersTheComparisonHandle() {
        val image = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        compose.setContent {
            BeforeAfterPreview(image, image, .5f, {}, 1.36f, Modifier.fillMaxWidth(), false, null)
        }
        compose.onNodeWithContentDescription("Drag to compare before and after").assertIsDisplayed()
    }

    @Test fun compactColorWorkspaceScrollsWithoutCoveringHeaderOrChangingPiPProject() {
        val main = MediaClip("main", "Main", "file:///missing-main.mp4", 5_000L, trimEndMs = 5_000L)
        val overlay = main.copy(id = "pip", type = ClipType.OVERLAY, trackIndex = 1)
        val project = Project("p", "PiP project", 5_000L, clips = listOf(main, overlay))
        val vm = EditorViewModel().also {
            it._state.value = EditorState(project = project, selectedClipId = overlay.id)
        }
        val blocker = requireNotNull(projectVisualScopeUnsupportedReason(vm.state.value))
        assertTrue(blocker.contains("nothing is skipped"))
        assertTrue(blocker.contains("explicitly hide/remove PiP"))
        assertFalse(blocker.contains("Use selected-clip export to keep"))
        compose.setContent {
            Column(Modifier.requiredSize(375.dp, 430.dp)) {
                ColorStudioScreen("p", {}, {}, vm = vm, embedded = true)
            }
        }
        val close = compose.onNodeWithText("Close").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val mode = compose.onNodeWithText("Project timeline").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Body starts below the fixed header", close.bottom <= mode.top)
        compose.onNodeWithText(blocker).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Drag to compare before and after").assertDoesNotExist()
        compose.onNodeWithText("Compare selected source only").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("This Before/After is the selected source frame only, not a full-timeline preview.")
            .performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("Source mode is not permission to change or drop PiP", project, vm.state.value.project)
            assertEquals(overlay.id, vm.state.value.selectedClipId)
        }
    }
}
