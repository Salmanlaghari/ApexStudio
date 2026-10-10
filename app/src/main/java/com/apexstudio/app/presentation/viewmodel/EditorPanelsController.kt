package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.*
import com.apexstudio.app.presentation.state.*
import kotlinx.coroutines.flow.update


fun EditorViewModel.openAdjustmentsPanel() = _state.update { it.copy(adjustmentsPanelOpen = true) }

fun EditorViewModel.closeAdjustmentsPanel() = _state.update { it.copy(adjustmentsPanelOpen = false) }

fun EditorViewModel.updateAdjustments(transform: (VideoAdjustments) -> VideoAdjustments) {
    _state.update { it.copy(adjustments = transform(it.adjustments)) }
}

fun EditorViewModel.resetAdjustments() {
    _state.update { it.copy(adjustments = VideoAdjustments()) }
}

fun EditorViewModel.resetAllAdjustments() {
    _state.update { it.copy(adjustments = VideoAdjustments()) }
}


fun EditorViewModel.openVoiceRecorder() = _state.update { it.copy(voiceRecorderOpen = true) }

fun EditorViewModel.closeVoiceRecorder() = _state.update { it.copy(voiceRecorderOpen = false) }

fun EditorViewModel.addVoiceOverTrack(uri: String, durationMs: Long, name: String) {
    val newTrack = AudioTrack(
        id = java.util.UUID.randomUUID().toString(),
        name = name,
        uri = uri,
        volume = 1f,
        trimEndMs = durationMs
    )
    _state.update { st ->
        val proj = st.project ?: return@update st
        val updatedTracks = proj.audioTracks + newTrack
        st.copy(project = proj.copy(audioTracks = updatedTracks))
    }
}


fun EditorViewModel.openCameraCapture() = _state.update { it.copy(cameraCaptureOpen = true) }

fun EditorViewModel.closeCameraCapture() = _state.update { it.copy(cameraCaptureOpen = false) }


fun EditorViewModel.openCoverPanel() = _state.update { it.copy(coverPanelOpen = true) }

fun EditorViewModel.closeCoverPanel() = _state.update { it.copy(coverPanelOpen = false) }

fun EditorViewModel.setCoverFrame(frameMs: Long) {
    _state.update { st ->
        val proj = st.project ?: return@update st
        st.copy(
            coverFrameMs = frameMs,
            project = proj.copy(coverFrameMs = frameMs)
        )
    }
    persistProject()
}

fun EditorViewModel.setCoverCustomUri(uri: String) {
    _state.update { st ->
        val proj = st.project ?: return@update st
        st.copy(
            coverCustomUri = uri,
            project = proj.copy(coverCustomUri = uri)
        )
    }
    persistProject()
}

fun EditorViewModel.saveCoverWords(text: String, style: String) {
    _state.update { st ->
        st.copy(
            coverText = text,
            coverTextStyle = style
        )
    }
    persistProject()
}


fun EditorViewModel.openChromaKeyPanel() = _state.update { it.copy(chromaKeyPanelOpen = true) }

fun EditorViewModel.closeChromaKeyPanel() = _state.update { it.copy(chromaKeyPanelOpen = false) }

fun EditorViewModel.updateChromaKeySettings(settings: com.apexstudio.app.domain.model.ChromaKeySettings) {
    _state.update { 
        it.copy(
            chromaKeySettings = settings,
            activeFxId = if (settings.enabled) "3d_chromakey" else it.activeFxId,
            fxIntensity = if (settings.enabled) 1.0f else it.fxIntensity
        ) 
    }
}


fun EditorViewModel.openRoyaltyMusicDialog() = _state.update { it.copy(royaltyMusicDialogOpen = true) }

fun EditorViewModel.closeRoyaltyMusicDialog() = _state.update { it.copy(royaltyMusicDialogOpen = false) }


fun EditorViewModel.addRoyaltyTrack(title: String, uri: String, durationMs: Long) {
    addAudioTrack(name = title, uri = uri, kind = com.apexstudio.app.domain.model.AudioTrack.Kind.MUSIC, sourceDurationMs = durationMs)
}


fun EditorViewModel.openHelpDialog() = _state.update { it.copy(helpDialogOpen = true) }

fun EditorViewModel.closeHelpDialog() = _state.update { it.copy(helpDialogOpen = false) }


fun EditorViewModel.openMediaLibrary() = _state.update { it.copy(mediaLibraryOpen = true) }

fun EditorViewModel.closeMediaLibrary() = _state.update { it.copy(mediaLibraryOpen = false) }


// Phase C: open / close the per-clip action menu (Cut, Trim, Add,
// Remove, Move, Split, Delete). Mirrors the setTrimPanelOpen
// pattern from earlier phases. We deliberately do NOT clear the
// selected clip here — selection persists so the clip stays
// highlighted after the menu closes, matching the behaviour of
// the existing trim / filter panels.
fun EditorViewModel.openClipActionMenu(clipId: String, atMs: Long) = _state.update {
    it.copy(clipActionMenuClipId = clipId, clipActionMenuPlayheadMs = atMs)
}

fun EditorViewModel.closeClipActionMenu() = _state.update {
    it.copy(clipActionMenuClipId = null)
}


fun EditorViewModel.openMediaPicker() = _state.update { it.copy(isMediaPickerOpen = true) }

fun EditorViewModel.closeMediaPicker() = _state.update { it.copy(isMediaPickerOpen = false) }


// ---------- Phase 3: Pack panels ----------

fun EditorViewModel.openEditPackPanel() = _state.update { it.copy(editPackPanelOpen = true) }

fun EditorViewModel.closeEditPackPanel() = _state.update { it.copy(editPackPanelOpen = false) }

fun EditorViewModel.openAdjustPackPanel() = _state.update { it.copy(adjustPackPanelOpen = true) }

fun EditorViewModel.closeAdjustPackPanel() = _state.update { it.copy(adjustPackPanelOpen = false) }

fun EditorViewModel.openColorScopesPanel() = _state.update { it.copy(colorScopesPanelOpen = true) }

fun EditorViewModel.closeColorScopesPanel() = _state.update { it.copy(colorScopesPanelOpen = false) }

fun EditorViewModel.openBgRemoverPanel() = _state.update { it.copy(bgRemoverPanelOpen = true) }

fun EditorViewModel.closeBgRemoverPanel() = _state.update { it.copy(bgRemoverPanelOpen = false) }


// ---------- Phase 3: Auto Clip / Music / History / Drafts ----------

fun EditorViewModel.openAutoClipPanel() = _state.update { it.copy(autoClipPanelOpen = true) }

fun EditorViewModel.closeAutoClipPanel() = _state.update { it.copy(autoClipPanelOpen = false) }

fun EditorViewModel.openMusicLibraryPanel() = _state.update { it.copy(musicLibraryPanelOpen = true) }

fun EditorViewModel.closeMusicLibraryPanel() = _state.update { it.copy(musicLibraryPanelOpen = false) }

fun EditorViewModel.openHistoryDraftsPanel(
    tab: com.apexstudio.app.presentation.state.HistoryDraftsTab =
        com.apexstudio.app.presentation.state.HistoryDraftsTab.HISTORY
) = _state.update { it.copy(historyDraftsPanelOpen = true, historyDraftsTab = tab) }

fun EditorViewModel.closeHistoryDraftsPanel() = _state.update { it.copy(historyDraftsPanelOpen = false) }

fun EditorViewModel.setHistoryDraftsTab(
    tab: com.apexstudio.app.presentation.state.HistoryDraftsTab
) = _state.update { it.copy(historyDraftsTab = tab) }

/** Records an auto-save tick (called every 30s while editing). */
fun EditorViewModel.markAutoSaved(nowMs: Long = System.currentTimeMillis()) =
    _state.update { it.copy(lastAutoSaveMs = nowMs) }
