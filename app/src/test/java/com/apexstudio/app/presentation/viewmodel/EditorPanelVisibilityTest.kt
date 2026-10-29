package com.apexstudio.app.presentation.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorPanelVisibilityTest {
    @Test
    fun newestPanelWinsAndClosingItDoesNotRevealAnOlderPanel() {
        val trim = setOf(EditorPanelKey.TRIM)
        val trimAndSpeed = linkedSetOf(EditorPanelKey.TRIM, EditorPanelKey.SPEED)

        assertEquals(
            EditorPanelKey.SPEED,
            activeEditorPanelAfterStateChange(trim, trimAndSpeed, EditorPanelKey.TRIM)
        )
        assertNull(
            activeEditorPanelAfterStateChange(trimAndSpeed, emptySet(), EditorPanelKey.SPEED)
        )
        assertEquals(
            EditorPanelKey.AI_TOOLS,
            activeEditorPanelAfterStateChange(trimAndSpeed, emptySet(), EditorPanelKey.AI_TOOLS)
        )
    }

    @Test
    fun keepingOnePanelOpenClearsItsFeatureSheetPeers() {
        val vm = EditorViewModel()
        vm._state.value = vm.state.value.copy(
            trimPanelOpen = true,
            speedPanelOpen = true,
            audioMixerOpen = true
        )

        vm.keepOnlyEditorPanel(EditorPanelKey.SPEED)

        assertTrue(vm.state.value.speedPanelOpen)
        assertFalse(vm.state.value.trimPanelOpen)
        assertFalse(vm.state.value.audioMixerOpen)
        assertEquals(setOf(EditorPanelKey.SPEED), vm.state.value.openEditorPanelKeys())
    }
}
