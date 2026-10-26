package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.Signature
import kotlinx.coroutines.flow.update

internal fun canRemoveSignature(signature: Signature?): Boolean = signature?.alwaysOn != true

/** Create or edit the one project-owned Signature, persisting every mutation. */
fun EditorViewModel.updateSignature(transform: (Signature) -> Signature) {
    _state.update { state ->
        val project = state.project ?: return@update state
        val current = project.signature ?: Signature()
        val requested = transform(current)
        val next = if (current.alwaysOn) requested.copy(alwaysOn = true) else requested
        state.copy(project = project.copy(signature = next))
    }
    persistProject()
}

fun EditorViewModel.setSignature(signature: Signature?) {
    if (signature == null && !canRemoveSignature(_state.value.project?.signature)) return
    _state.update { state ->
        val project = state.project ?: return@update state
        if (signature == null && !canRemoveSignature(project.signature)) return@update state
        val next = if (project.signature?.alwaysOn == true && signature != null) {
            signature.copy(alwaysOn = true)
        } else signature
        state.copy(project = project.copy(signature = next))
    }
    persistProject()
}

/** alwaysOn signatures are policy-protected and cannot be removed by the editor. */
fun EditorViewModel.removeSignature() {
    if (!canRemoveSignature(_state.value.project?.signature)) return
    setSignature(null)
}
