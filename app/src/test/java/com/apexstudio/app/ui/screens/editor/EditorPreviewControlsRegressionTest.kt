package com.apexstudio.app.ui.screens.editor

import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.registerMainPlayerForAudioEffects
import com.apexstudio.app.presentation.viewmodel.unregisterMainPlayerForAudioEffects
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
        assertFalse(DEFAULT_PREVIEW_COVER_MODE)
        assertFalse(toggleCoverFitMode(isCoverMode = true))
        assertTrue(toggleCoverFitMode(isCoverMode = false))
    }

    @Test
    fun unregisteringDisposedPlayerDoesNotClearReplacementRegistration() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val vm = EditorViewModel()
        val oldPlayer = ExoPlayer.Builder(context).build()
        val currentPlayer = ExoPlayer.Builder(context).build()
        try {
            vm.registerMainPlayerForAudioEffects(oldPlayer)
            vm.registerMainPlayerForAudioEffects(currentPlayer)
            vm.unregisterMainPlayerForAudioEffects(oldPlayer)
            assertTrue(vm.mainPlayerRef === currentPlayer)

            vm.unregisterMainPlayerForAudioEffects(currentPlayer)
            assertTrue(vm.mainPlayerRef == null)
        } finally {
            oldPlayer.release()
            currentPlayer.release()
        }
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
