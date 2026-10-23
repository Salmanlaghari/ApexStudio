package com.apexstudio.app.presentation.viewmodel

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CaptionEntryRouteTest {
    @Test fun captionsEntryIsExplicitAndOrdinaryTextEntryResetsIt() {
        val vm = EditorViewModel()
        vm.openCaptionsPanel()
        assertTrue(vm.state.value.textPanelOpen)
        assertTrue(vm.state.value.textPanelStartsOnCaptions)
        vm.closeTextPanel()
        assertFalse(vm.state.value.textPanelOpen)
        assertFalse(vm.state.value.textPanelStartsOnCaptions)
        vm.openCaptionsPanel()
        vm.openTextPanel()
        assertTrue(vm.state.value.textPanelOpen)
        assertFalse(vm.state.value.textPanelStartsOnCaptions)
    }
}
