package com.apexstudio.app.ui.screens.editor

import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.toggleFullscreenPreview
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorPreviewControlsRegressionTest {
    @Test
    fun coverFitToggleAlternatesPlayerSizingSelection() {
        assertFalse(toggleCoverFitMode(isCoverMode = true))
        assertTrue(toggleCoverFitMode(isCoverMode = false))
    }

    @Test
    fun fullscreenPreviewHidesEditorChromeButNormalPreviewKeepsIt() {
        assertFalse(editorChromeVisible(isFullscreenPreview = true))
        assertTrue(editorChromeVisible(isFullscreenPreview = false))
    }

    @Test
    fun fullscreenToggleStateEntersAndExitsExpandedPreview() {
        val vm = EditorViewModel()
        assertFalse(vm.state.value.isFullscreenPreview)
        vm.toggleFullscreenPreview()
        assertTrue(vm.state.value.isFullscreenPreview)
        vm.toggleFullscreenPreview()
        assertFalse(vm.state.value.isFullscreenPreview)
    }
}
