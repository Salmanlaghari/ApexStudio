package com.apexstudio.app.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.apexstudio.app.data.template.TransmissionTemplate
import com.apexstudio.app.domain.model.*
import com.apexstudio.app.presentation.state.*
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


// ---- Transmission templates ----

/**
 * Apply a transmission template to the current editor state.
 *
 * Sets:
 * - [EditorState.activeFilterId] from `template.filterId`
 * - [EditorState.filterIntensity] from `template.filterIntensity`
 * - [EditorState.activeFxId] from `template.fxPresetId`
 * - [EditorState.fxIntensity] from `template.fxIntensity`
 *
 * The preview GL pipeline re-reads these fields on its next
 * recomposition, so the change is live. Persists the chosen
 * template id into [Project.lastTransmissionTemplateId] so the
 * next time the user opens this project, [loadProject] auto-
 * applies it.
 *
 * `id == null` clears the active template (LUT + FX intensities
 * are reset to 0 so the preview shows the original colour). The
 * persisted id is cleared too.
 */
fun EditorViewModel.applyTransmissionTemplate(id: String?) {
    if (id == null) {
        _state.update {
            it.copy(
                activeFilterId = null,
                filterIntensity = 0f,
                activeFxId = null,
                fxIntensity = 0f,
                lastTransitionType = null,
                lastTransitionDurationMs = 500L,
                playbackSpeed = 1.0f
            )
        }
        persistTransmissionTemplate(
            id = null,
            transitionType = null,
            transitionDurationMs = 500L
        )
        return
    }
    val template = _transmissionTemplates.value.firstOrNull { it.id == id }
    if (template == null) {
        Log.w("EditorViewModel", "applyTransmissionTemplate: unknown id '$id' — ignoring")
        return
    }
    applyTransmissionTemplateInternal(template, persistProjectId = projectId)
}


/**
 * Apply a resolved [TransmissionTemplate] without re-looking it
 * up by id. Used by [loadProject] on project open so we can
 * skip the "unknown id" warning path for the auto-apply case.
 *
 * The template's `transitionType` and `transitionDurationMs` are
 * also applied as a project-level hint (see
 * `Project.lastTransitionType`).
 */
internal fun EditorViewModel.applyTransmissionTemplateInternal(template: TransmissionTemplate, persistProjectId: String?) {
    val targetSpeed = if (template.isSlowMotion && template.playbackSpeed > 0f) template.playbackSpeed else 1.0f
    _state.update {
        it.copy(
            activeFilterId = template.filterId,
            filterIntensity = template.filterIntensity.coerceIn(0f, 1f),
            activeFxId = template.fxPresetId,
            fxIntensity = template.fxIntensity.coerceIn(0f, 1f),
            lastTransitionType = template.transitionType,
            lastTransitionDurationMs = template.transitionDurationMs,
            playbackSpeed = targetSpeed
        )
    }
    // If slow motion is included, also update the active clip's playback speed
    val clipId = _state.value.selectedClipId ?: _state.value.project?.clips?.firstOrNull()?.id
    if (clipId != null && template.isSlowMotion) {
        setClipSpeed(clipId, targetSpeed)
    }
    if (template.bpm > 0) {
        setAudioBpm(template.bpm)
    }
    if (template.royaltyTrackId.isNotEmpty() && context != null) {
        val track = com.apexstudio.app.data.audio.FreeRoyaltyMusic.CATALOG.firstOrNull { it.id == template.royaltyTrackId }
        if (track != null) {
            val file = com.apexstudio.app.data.audio.FreeRoyaltyMusic.getTrackFile(context, track)
            addRoyaltyTrack(track.title, file.absolutePath, track.durationMs)
        }
    }
    persistTransmissionTemplate(id = template.id, transitionType = template.transitionType,
        transitionDurationMs = template.transitionDurationMs)
}


/**
 * Save the chosen transmission template id + transition hint on
 * the current project and write the project back to DataStore so
 * the choice survives an app restart. Called from both apply
 * paths (manual + auto).
 */
internal fun EditorViewModel.persistTransmissionTemplate(id: String?, transitionType: String?, transitionDurationMs: Long) {
    val snapshot = _state.value.project ?: return
    if (snapshot.lastTransmissionTemplateId == id &&
        snapshot.lastTransitionType == transitionType &&
        snapshot.lastTransitionDurationMs == transitionDurationMs
    ) return
    val updated = snapshot.copy(
        lastTransmissionTemplateId = id,
        lastTransitionType = transitionType,
        lastTransitionDurationMs = transitionDurationMs
    )
    _state.update { it.copy(project = updated) }
    val repo = projectRepository ?: return
    viewModelScope.launch {
        try {
            repo.saveProject(updated)
        } catch (e: Exception) {
            Log.w("EditorViewModel", "Failed to persist transmission template", e)
        }
    }
}


/**
 * Open or close the transmission-templates bottom sheet. Mirrors
 * the open/close pattern used by [openFilterPanel] / [openFxPanel].
 */
fun EditorViewModel.openTransmissionTemplatesPanel() = _state.update { it.copy(transmissionPanelOpen = true) }


// -------------------------------------------------------------
// Clip-to-Clip Video Transitions Management
// -------------------------------------------------------------

/**
 * Open the Transition Picker bottom sheet for the junction between [fromClipId] and [toClipId].
 */
fun EditorViewModel.openTransitionPicker(fromClipId: String, toClipId: String) {
    _state.update {
        it.copy(
            transitionPickerOpen = true,
            transitionPickerFromClipId = fromClipId,
            transitionPickerToClipId = toClipId
        )
    }
}


/**
 * Close the Transition Picker bottom sheet.
 */
fun EditorViewModel.closeTransitionPicker() {
    _state.update {
        it.copy(
            transitionPickerOpen = false,
            transitionPickerFromClipId = null,
            transitionPickerToClipId = null
        )
    }
}


/**
 * Apply or update a transition between [fromClipId] and [toClipId].
 */
fun EditorViewModel.applyTransition(fromClipId: String, toClipId: String, type: String, durationMs: Long = 500L) {
    val proj = _state.value.project ?: return
    val existing = proj.transitions.filterNot { it.fromClipId == fromClipId && it.toClipId == toClipId }
    val newTransition = ClipTransition(
        fromClipId = fromClipId,
        toClipId = toClipId,
        type = type,
        durationMs = durationMs
    )
    val updatedTransitions = existing + newTransition
    val updatedProject = proj.copy(
        transitions = updatedTransitions,
        lastTransitionType = type,
        lastTransitionDurationMs = durationMs
    )
    _state.update {
        it.copy(
            project = updatedProject,
            lastTransitionType = type,
            lastTransitionDurationMs = durationMs
        )
    }
    persistProject()
}


/**
 * Apply the specified transition across all clip junctions in the timeline.
 */
fun EditorViewModel.applyTransitionToAll(type: String, durationMs: Long = 500L) {
    val proj = _state.value.project ?: return
    val clips = proj.clips
    if (clips.size < 2) return

    val newTransitions = mutableListOf<ClipTransition>()
    for (i in 0 until clips.size - 1) {
        newTransitions.add(
            ClipTransition(
                fromClipId = clips[i].id,
                toClipId = clips[i + 1].id,
                type = type,
                durationMs = durationMs
            )
        )
    }
    val updatedProject = proj.copy(
        transitions = newTransitions,
        lastTransitionType = type,
        lastTransitionDurationMs = durationMs
    )
    _state.update {
        it.copy(
            project = updatedProject,
            lastTransitionType = type,
            lastTransitionDurationMs = durationMs
        )
    }
    persistProject()
}


/**
 * Remove the transition between [fromClipId] and [toClipId].
 */
fun EditorViewModel.removeTransition(fromClipId: String, toClipId: String) {
    val proj = _state.value.project ?: return
    val updatedTransitions = proj.transitions.filterNot { it.fromClipId == fromClipId && it.toClipId == toClipId }
    val updatedProject = proj.copy(transitions = updatedTransitions)
    _state.update { it.copy(project = updatedProject) }
    persistProject()
}


/**
 * Query transition between two clips if one exists.
 */
fun EditorViewModel.getTransitionBetween(fromClipId: String, toClipId: String): ClipTransition? {
    return _state.value.project?.transitions?.firstOrNull { it.fromClipId == fromClipId && it.toClipId == toClipId }
}

fun EditorViewModel.closeTransmissionTemplatesPanel() = _state.update { it.copy(transmissionPanelOpen = false) }
