package com.apexstudio.app.ui.screens.export

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.state.EditorState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoExportScopeTest {
    @get:Rule val compose = createComposeRule()
    private val first = MediaClip("first", "first", "file:///first.mp4", 10_000L, trimEndMs = 10_000L)
    private val second = first.copy(id = "second", trimStartMs = 2_000L, trimEndMs = 8_000L, speedMultiplier = 2f)
    private val overlay = first.copy(id = "overlay", type = ClipType.OVERLAY, trackIndex = 1)
    private fun state(selected: String?, vararg clips: MediaClip) = EditorState(
        project = Project(id = "p", name = "p", durationMs = 20_000L, clips = clips.toList()),
        selectedClipId = selected
    )

    @Test fun selectedMainUsesItsTrimmedSpeedAdjustedDurationNotProjectDuration() {
        val scope = videoExportScope(state("second", first, second, overlay))
        assertEquals(second, scope.baseClip)
        assertEquals(3_000L, scope.durationMs)
        assertEquals(2, scope.mainClipCount)
        assertTrue(scope.notice!!.contains("not all 2 main clips"))
    }

    @Test fun overlaySelectionUsesSameFallbackAsActualExporter() {
        val scope = videoExportScope(state("overlay", overlay, first, second))
        assertEquals(first, scope.baseClip)
        assertEquals(10_000L, scope.durationMs)
        assertEquals(2, scope.mainClipCount)
    }

    @Test fun singlePhotoIsSupportedWithoutMultiClipWarning() {
        val photo = first.copy(type = ClipType.IMAGE)
        val scope = videoExportScope(state("overlay", photo, overlay))
        assertEquals(photo, scope.baseClip)
        assertNull(scope.notice)
    }

    @Test fun audioAndOverlayOnlyProjectsHaveNoVideoSource() {
        val scope = videoExportScope(state("overlay", overlay, first.copy(type = ClipType.AUDIO)))
        assertNull(scope.baseClip)
        assertEquals(0L, scope.durationMs)
        assertEquals(0, scope.mainClipCount)
        assertNotNull(scope.notice)
        assertNull(videoExportScope(EditorState()).baseClip)
    }

    @Test fun multiClipWarningIsDisplayedBeforeExportRatherThanOnlyInDocumentation() {
        val scope = videoExportScope(state("second", first, second))
        compose.setContent { VideoExportScopeWarning(scope) }
        compose.onNodeWithText(scope.notice!!).assertIsDisplayed()
    }

    @Test fun singleMainClipDoesNotDisplayUnavailableProjectCompositionWarning() {
        val scope = videoExportScope(state("first", first))
        compose.setContent { VideoExportScopeWarning(scope) }
        compose.onNodeWithText("Whole-project video export", substring = true).assertDoesNotExist()
    }

    @Test fun exportStatusCallsOutPreparationWhenMedia3HasNoProgressYet() {
        assertEquals("Preparing export…", exportProgressLabel(isExporting = true, progress = 0f))
        assertEquals("Preparing export…", exportProgressLabel(isExporting = true, progress = Float.NaN))
        assertEquals("Exporting…", exportProgressLabel(isExporting = true, progress = 0.01f))
        assertNull(exportProgressLabel(isExporting = false, progress = 0f))
    }
}
