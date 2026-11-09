package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.Signature
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.state.EditorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorSignatureControllerTest {
    @Test
    fun alwaysOnSignatureCannotBeRemoved() {
        assertFalse(canRemoveSignature(Signature(alwaysOn = true)))
    }

    @Test
    fun optionalSignatureCanBeRemoved() {
        assertTrue(canRemoveSignature(Signature(alwaysOn = false)))
        assertTrue(canRemoveSignature(null))
    }

    @Test
    fun publicSetterCannotBypassAlwaysOnRemovalGuard() {
        val vm = EditorViewModel()
        val signature = Signature(text = "@brand", alwaysOn = true)
        vm._state.value = EditorState().copy(
            project = Project(id = "project", name = "Project", durationMs = 1_000L, signature = signature)
        )

        vm.setSignature(null)

        assertEquals(signature, vm.state.value.project?.signature)
    }

    @Test
    fun alwaysOnFlagCannotBeClearedByMutationOrReplacement() {
        val vm = EditorViewModel()
        val signature = Signature(text = "@brand", alwaysOn = true)
        vm._state.value = EditorState().copy(
            project = Project(id = "project", name = "Project", durationMs = 1_000L, signature = signature)
        )

        vm.updateSignature { it.copy(alwaysOn = false) }
        vm.setSignature(Signature(text = "replacement", alwaysOn = false))

        assertEquals(true, vm.state.value.project?.signature?.alwaysOn)
    }
}
