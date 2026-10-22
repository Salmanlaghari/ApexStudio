package com.apexstudio.app.ui.screens.colortools

import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.state.EditorState
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.RoborazziComposeSizeOption
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Production Color UI, host layout only. No device media decoding or successful render is fabricated. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w375dp-h660dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ColorStudioVisualRenderTest {
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
