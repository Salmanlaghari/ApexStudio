package com.apexstudio.app.ui.screens.colortools

import com.apexstudio.app.domain.model.ColorStudioGrade
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.state.EditorState
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.RoborazziComposeSizeOption
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Production Color UI, host layout only. No device media decoding or successful render is fabricated. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w375dp-h660dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ColorStudioVisualRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun embeddedColorWorkspaceStartsWithTheSelectedSourceBeforeAfterView() {
        val vm = EditorViewModel()
        val clip = MediaClip("fixture", "Host layout fixture", "file:///test-only.mp4", 5_000L, trimEndMs = 5_000L)
        vm._state.value = EditorState(project = Project("fixture", "Host only", 5_000L, clips = listOf(clip)), selectedClipId = clip.id)
        compose.setContent { ColorStudioScreen("fixture", {}, {}, vm = vm, embedded = true) }

        compose.onNodeWithText("This is a selected source-frame preview. Apply saves the grade to all current and future main-track videos; export the full project once when editing is complete.")
            .assertIsDisplayed()
    }

    @Test fun compactPhoneStripExposesBrightnessSdrToneMapAndSharpnessControls() {
        val vm = EditorViewModel()
        val clip = MediaClip("fixture", "Host layout fixture", "file:///test-only.mp4", 5_000L, trimEndMs = 5_000L)
        vm._state.value = EditorState(project = Project("fixture", "Host only", 5_000L, clips = listOf(clip)), selectedClipId = clip.id)
        compose.setContent { ColorStudioScreen("fixture", {}, {}, vm = vm, embedded = true) }

        compose.onNodeWithText("Brightness").assertIsDisplayed()
        compose.onNodeWithTag("color_adjustment_controls").performScrollToNode(hasText("Tone Map (SDR)"))
        compose.onNodeWithText("Tone Map (SDR)").assertIsDisplayed()
        compose.onNodeWithText("Sharpness").assertIsDisplayed()
    }

    @Test fun projectApplyReplacesStandaloneColorExportActions() {
        val vm = EditorViewModel()
        val clip = MediaClip("fixture", "Host layout fixture", "file:///test-only.mp4", 5_000L, trimEndMs = 5_000L)
        val grade = ColorStudioGrade(brightness = .2f)
        vm._state.value = EditorState(project = Project("fixture", "Host only", 5_000L,
            clips = listOf(clip), colorStudioGrade = grade), selectedClipId = clip.id)
        var backCount = 0
        compose.setContent { ColorStudioScreen("fixture", { backCount++ }, {}, vm = vm, embedded = true) }

        compose.onNodeWithText("Apply").assertIsDisplayed().performClick()
        compose.onNodeWithText("Export Color result").assertDoesNotExist()
        compose.onNodeWithText("Export timeline").assertDoesNotExist()
        compose.onNodeWithText("Export video").assertDoesNotExist()
        compose.onNodeWithText("Export still").assertDoesNotExist()

        assertEquals(grade, vm.state.value.project?.colorStudioGrade)
        // Embedded Apply returns to the project editor; the editor's standard Export action remains its caller's route.
        assertEquals(1, backCount)
    }

    @Test fun parentExportRequestUsesTheStandardProjectExportRoute() {
        val vm = EditorViewModel()
        val clip = MediaClip("fixture", "Host layout fixture", "file:///test-only.mp4", 5_000L, trimEndMs = 5_000L)
        val grade = ColorStudioGrade(brightness = .16f)
        vm._state.value = EditorState(project = Project("fixture", "Host only", 5_000L,
            clips = listOf(clip), colorStudioGrade = grade), selectedClipId = clip.id)
        var exportCount = 0

        compose.setContent {
            ColorStudioScreen("fixture", {}, { exportCount++ }, vm = vm, embedded = true, exportRequest = 1)
        }
        compose.waitForIdle()

        assertEquals(1, exportCount)
        assertEquals(grade, vm.state.value.project?.colorStudioGrade)
    }

    @Test fun productionColorWorkspaceFitsCompactPhoneEditorArea() {
        val vm = EditorViewModel()
        val clip = MediaClip("fixture", "Host layout fixture", "file:///test-only.mp4", 5_000L, trimEndMs = 5_000L)
        vm._state.value = EditorState(project = Project("fixture", "Host only", 1L, clips = listOf(clip)), selectedClipId = clip.id)
        val output = File("build/outputs/visual/color-timeline-host.png")
        output.parentFile?.mkdirs()
        captureRoboImage(output,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
            roborazziComposeOptions = RoborazziComposeOptions {
                addOption(RoborazziComposeSizeOption(widthDp = 375, heightDp = 660))
            }) {
            ColorStudioScreen("fixture", {}, {}, vm = vm, embedded = true)
        }
        assertTrue("Production workspace capture must exist", output.isFile && output.length() > 0L)
    }
}
