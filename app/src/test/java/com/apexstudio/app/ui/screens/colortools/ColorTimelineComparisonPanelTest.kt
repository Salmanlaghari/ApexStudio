package com.apexstudio.app.ui.screens.colortools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.apexstudio.app.data.color.ColorStudioGrade
import com.apexstudio.app.data.export.ExportEngine
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.presentation.viewmodel.ColorTimelineExportSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w375dp-h830dp")
class ColorTimelineComparisonPanelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unsupportedCompositionShowsReasonAndCannotStartAPartialRender() {
        compose.setContent {
            Column(Modifier.width(375.dp).height(600.dp)) {
                ColorTimelineComparisonPanel(null, "Visible PiP is unsupported; nothing was omitted.", .5f, {}, true, {})
            }
        }
        compose.onNodeWithText("Visible PiP is unsupported; nothing was omitted.").assertIsDisplayed()
        compose.onNodeWithText("Render Before / After").assertIsNotEnabled()
    }

    @Test fun supportedTimelineOffersRealRenderButDoesNotPretendASourceFrameIsTheComposition() {
        val clip = MediaClip("one", "One", "file:///one.mp4", 5000, trimEndMs = 5000)
        val snapshot = ColorTimelineExportSnapshot(listOf(clip), ExportEngine.ExportConfig(colorStudioGrade = ColorStudioGrade()))
        compose.setContent {
            Column(Modifier.width(375.dp).height(600.dp)) {
                ColorTimelineComparisonPanel(snapshot, null, .5f, {}, true, {})
            }
        }
        compose.onNodeWithText("Render Before / After").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Render the supported timeline to compare its complete Before and After.").assertIsDisplayed()
        compose.onNodeWithText("Share compared MP4").assertDoesNotExist()
    }
}
