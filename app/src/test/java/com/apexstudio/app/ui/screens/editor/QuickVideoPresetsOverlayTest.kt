package com.apexstudio.app.ui.screens.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuickVideoPresetsOverlayTest {
    @get:Rule val compose = createComposeRule()

    @Test fun enhanceOffersRealOfflineUpscaleSeparatelyFromColorMatrices() {
        val clip = MediaClip(
            id = "video", name = "Video", uri = "file:///video.mp4",
            durationMs = 10_000L, trimEndMs = 10_000L,
        )
        val vm = EditorViewModel().also {
            it._state.value = it.state.value.copy(
                project = Project("project", "Project", 10_000L, clips = listOf(clip)),
                selectedClipId = clip.id,
            )
        }
        compose.setContent { MaterialTheme {
            QuickVideoPresetsOverlay(vm, QuickVideoPresetSection.ENHANCE) {}
        } }

        compose.onNodeWithTag("quick_enhance_espcn").assertExists().assertIsEnabled()
        compose.onNodeWithText("Offline ESPCN x3 · licensed ONNX Model Zoo model.", substring = true).assertExists()
        compose.onNodeWithText("Source limits: even dimensions up to 640×360 (360×640 portrait), 30 seconds, 30 fps, SDR, and silent video or one AAC track.", substring = true).assertExists()
        compose.onNodeWithText("After success, preview and export use the same processed video file", substring = true).assertExists()
    }
}
