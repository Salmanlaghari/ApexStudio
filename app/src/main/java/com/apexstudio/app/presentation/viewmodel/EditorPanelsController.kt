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


fun EditorViewModel.openVoiceRecorder() {
    // Keep the position from the moment recording is requested, rather than
    // wherever playback happens to be when the saved file returns.
    voiceOverStartMs = livePlayheadMs().coerceAtLeast(0L)
    _state.update { it.copy(voiceRecorderOpen = true) }
}

fun EditorViewModel.closeVoiceRecorder() = _state.update { it.copy(voiceRecorderOpen = false) }

fun EditorViewModel.addVoiceOverTrack(uri: String, durationMs: Long, name: String) {
    if (durationMs <= 0L) return
    if (_state.value.project == null) return
    val newTrack = AudioTrack(
        id = java.util.UUID.randomUUID().toString(),
        name = name,
        uri = uri,
        volume = 1f,
        trimEndMs = durationMs,
        kind = AudioTrack.Kind.SFX,
        startTimeMs = voiceOverStartMs.coerceAtLeast(0L),
        sourceDurationMs = durationMs
    )
    _state.update { st ->
        val currentProject = st.project ?: return@update st
        val updatedTracks = currentProject.audioTracks + newTrack
        // Duration uses the playable span (trimEnd - trimStart), not the
        // source out-point. Keep the project's persisted duration in sync
        // with the editor mirror so reopening the draft retains the same end.
        val duration = computeTimelineDurationMs(currentProject.clips, updatedTracks)
        st.copy(
            project = currentProject.copy(audioTracks = updatedTracks, durationMs = duration),
            durationMs = duration,
            selectedAudioTrackId = newTrack.id
        )
    }
    // Keep the mixer list in sync (the old code added the voiceover
    // only to the project list, so it never appeared in the mixer)
    // and PERSIST it (the old code never saved, so a recorded
    // voiceover vanished on reopen — "Voice edit nahi ho raha").
    _audio.update { s -> s.copy(tracks = s.tracks + newTrack) }
    persistProject()
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


internal fun EditorState.selectedChromaKeyTarget(): MediaClip? {
    val selectedId = selectedClipId ?: return null
    val clip = project?.clips?.firstOrNull { it.id == selectedId } ?: return null
    return clip.takeIf { it.type == ClipType.VIDEO || it.type == ClipType.IMAGE || it.type == ClipType.OVERLAY }
}

fun EditorViewModel.openChromaKeyPanel() = _state.update { it.copy(chromaKeyPanelOpen = true) }

fun EditorViewModel.closeChromaKeyPanel() = _state.update { it.copy(chromaKeyPanelOpen = false) }

/** Mask tool bottom sheet. */
fun EditorViewModel.openMaskPanel() = _state.update { it.copy(maskPanelOpen = true) }

fun EditorViewModel.closeMaskPanel() = _state.update { it.copy(maskPanelOpen = false) }

/** Mask tool bottom sheet for the selected text overlay. */
fun EditorViewModel.openTextMaskPanel() = _state.update { it.copy(textMaskPanelOpen = true) }

fun EditorViewModel.closeTextMaskPanel() = _state.update { it.copy(textMaskPanelOpen = false) }

fun EditorViewModel.updateChromaKeySettings(settings: com.apexstudio.app.domain.model.ChromaKeySettings): Boolean {
    var applied = false
    _state.update { state ->
        val target = state.selectedChromaKeyTarget() ?: return@update state
        val updatedClips = state.project?.clips?.map { clip ->
            if (clip.id == target.id) clip.copy(chromaKeySettings = settings) else clip
        } ?: return@update state
        applied = true
        state.copy(
            chromaKeySettings = settings,
            activeFxId = if (settings.enabled) "3d_chromakey" else state.activeFxId,
            fxIntensity = if (settings.enabled) 1.0f else state.fxIntensity,
            project = state.project?.copy(clips = updatedClips)
        )
    }
    if (!applied) return false
    persistProject(refreshLegacyClipFields = true)
    return true
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

// ---------- PR-G: Background replace (per-clip MediaPipe segmentation) ----------
// openBackgroundPanel / closeBackgroundPanel live in
// EditorBackgroundController (they also seed the panel state).


// ---------- Phase 3: Auto Clip / Music / History / Drafts ----------

fun EditorViewModel.openAutoClipPanel() = _state.update { it.copy(autoClipPanelOpen = true) }

fun EditorViewModel.closeAutoClipPanel() = _state.update { it.copy(autoClipPanelOpen = false) }

fun EditorViewModel.openMusicLibraryPanel() = _state.update {
    it.copy(musicLibraryPanelOpen = true, sfxLibraryPanelOpen = false)
}

fun EditorViewModel.closeMusicLibraryPanel() = _state.update { it.copy(musicLibraryPanelOpen = false) }

fun EditorViewModel.openSfxLibraryPanel() = _state.update {
    it.copy(sfxLibraryPanelOpen = true, musicLibraryPanelOpen = false)
}

fun EditorViewModel.closeSfxLibraryPanel() = _state.update { it.copy(sfxLibraryPanelOpen = false) }

fun EditorViewModel.openHistoryDraftsPanel(
    tab: com.apexstudio.app.presentation.state.HistoryDraftsTab =
        com.apexstudio.app.presentation.state.HistoryDraftsTab.HISTORY
) = _state.update { it.copy(historyDraftsPanelOpen = true, historyDraftsTab = tab) }

fun EditorViewModel.closeHistoryDraftsPanel() = _state.update { it.copy(historyDraftsPanelOpen = false) }

fun EditorViewModel.openSignaturePanel() = _state.update { it.copy(signaturePanelOpen = true) }

fun EditorViewModel.closeSignaturePanel() = _state.update { it.copy(signaturePanelOpen = false) }

fun EditorViewModel.setHistoryDraftsTab(
    tab: com.apexstudio.app.presentation.state.HistoryDraftsTab
) = _state.update { it.copy(historyDraftsTab = tab) }

/** Records an auto-save tick (called every 30s while editing). */
fun EditorViewModel.markAutoSaved(nowMs: Long = System.currentTimeMillis()) =
    _state.update { it.copy(lastAutoSaveMs = nowMs) }

/**
 * Open the Mask tool from the MAIN bottom bar — Bug 4, Mask as a
 * TOP-LEVEL feature rather than something you can only reach once the
 * selected-clip bar is showing.
 *
 * The clip bar only appears while a layer is selected, so before this
 * the mask entry point was unreachable with an empty selection, and
 * since `MaskPanelOverlay` stopped falling back to the first clip it
 * would have had no target at all — a dead button. This opens the sheet
 * and, when nothing editable is selected, targets the first layer the
 * mask can act on so the tool always affects something on screen.
 *
 * Selecting a different layer afterwards re-targets immediately, the
 * sheet re-reads `clipLayerAnchor()` on every recomposition.
 */
fun EditorViewModel.openMaskPanelTopLevel() {
    if (_state.value.clipLayerAnchor() == null) {
        val first = _state.value.project?.clips
            ?.firstOrNull { it.isKeyframeTarget() }
            ?: return
        selectClip(first.id)
    }
    openMaskPanel()
}


/** UI-only ownership key for the editor's mutually exclusive feature surfaces. */
internal enum class EditorPanelKey(val stateBacked: Boolean = true) {
    TRIM, GPU_FILTER, COLOR_GRADING, ADJUSTMENTS, FX, TRANSMISSION, TRANSITION,
    TEXT, AUDIO_MIXER, CLIP_VOLUME, AUDIO_FADE, ROYALTY_MUSIC_DIALOG, SPEED, STICKER,
    SIGNATURE, VOICE_RECORDER, AR_FILTER, COVER, CHROMA_KEY, MASK,
    TEXT_MASK, OVERLAY_PROPERTIES, HELP, KEYFRAME, PHOTO_EDIT, EDIT_PACK, ADJUST_PACK,
    COLOR_SCOPES, BG_REMOVER, BACKGROUND_REPLACE, AUTO_CLIP, MUSIC_LIBRARY, SFX_LIBRARY,
    HISTORY_DRAFTS,
    AI_TOOLS(false), VIDEO_PRESETS(false), VIDEO_UPSCALE(false), LOCAL_ANALYSIS(false),
    CROP(false), VIDEO_DRAWING(false), QUICK_PRESETS(false), ADD_MEDIA_MENU(false),
    ROYALTY_MUSIC_SHEET(false), LOCAL_CHALLENGE(false)
}

/** Open state-backed editor sheets, kept in visual priority order for first-frame arbitration. */
internal fun EditorState.openEditorPanelKeys(): Set<EditorPanelKey> = buildSet {
    if (trimPanelOpen) add(EditorPanelKey.TRIM)
    if (gpuFilterPanelOpen) add(EditorPanelKey.GPU_FILTER)
    if (filterPanelOpen || colorGradingLutPanelOpen) add(EditorPanelKey.COLOR_GRADING)
    if (adjustmentsPanelOpen) add(EditorPanelKey.ADJUSTMENTS)
    if (fxPanelOpen) add(EditorPanelKey.FX)
    if (transmissionPanelOpen) add(EditorPanelKey.TRANSMISSION)
    if (transitionPickerOpen) add(EditorPanelKey.TRANSITION)
    if (textPanelOpen) add(EditorPanelKey.TEXT)
    if (audioMixerOpen) add(EditorPanelKey.AUDIO_MIXER)
    if (clipVolumeSheetOpen) add(EditorPanelKey.CLIP_VOLUME)
    if (audioFadeSheetOpen) add(EditorPanelKey.AUDIO_FADE)
    if (royaltyMusicDialogOpen) add(EditorPanelKey.ROYALTY_MUSIC_DIALOG)
    if (speedPanelOpen) add(EditorPanelKey.SPEED)
    if (stickerPanelOpen) add(EditorPanelKey.STICKER)
    if (signaturePanelOpen) add(EditorPanelKey.SIGNATURE)
    if (voiceRecorderOpen) add(EditorPanelKey.VOICE_RECORDER)
    if (arFilterPanelOpen) add(EditorPanelKey.AR_FILTER)
    if (coverPanelOpen) add(EditorPanelKey.COVER)
    if (chromaKeyPanelOpen) add(EditorPanelKey.CHROMA_KEY)
    if (maskPanelOpen) add(EditorPanelKey.MASK)
    if (textMaskPanelOpen) add(EditorPanelKey.TEXT_MASK)
    if (overlayPropsOpen) add(EditorPanelKey.OVERLAY_PROPERTIES)
    if (helpDialogOpen) add(EditorPanelKey.HELP)
    if (keyframePanelOpen) add(EditorPanelKey.KEYFRAME)
    if (photoEditPanelOpen) add(EditorPanelKey.PHOTO_EDIT)
    if (editPackPanelOpen) add(EditorPanelKey.EDIT_PACK)
    if (adjustPackPanelOpen) add(EditorPanelKey.ADJUST_PACK)
    if (colorScopesPanelOpen) add(EditorPanelKey.COLOR_SCOPES)
    if (bgRemoverPanelOpen) add(EditorPanelKey.BG_REMOVER)
    if (backgroundPanelOpen) add(EditorPanelKey.BACKGROUND_REPLACE)
    if (autoClipPanelOpen) add(EditorPanelKey.AUTO_CLIP)
    if (musicLibraryPanelOpen) add(EditorPanelKey.MUSIC_LIBRARY)
    if (sfxLibraryPanelOpen) add(EditorPanelKey.SFX_LIBRARY)
    if (historyDraftsPanelOpen) add(EditorPanelKey.HISTORY_DRAFTS)
}

/** Preserve the most recently opened tool; closing it must not resurrect an older sheet. */
internal fun activeEditorPanelAfterStateChange(
    previous: Set<EditorPanelKey>,
    current: Set<EditorPanelKey>,
    active: EditorPanelKey?
): EditorPanelKey? {
    val newlyOpened = current - previous
    return newlyOpened.lastOrNull()
        ?: if (active?.stateBacked == true && active !in current) null else active
}

/** Close all state-backed peers when another local or state-backed panel takes ownership. */
internal fun EditorViewModel.keepOnlyEditorPanel(active: EditorPanelKey?) {
    _state.update { state ->
        state.copy(
            trimPanelOpen = active == EditorPanelKey.TRIM && state.trimPanelOpen,
            gpuFilterPanelOpen = active == EditorPanelKey.GPU_FILTER && state.gpuFilterPanelOpen,
            filterPanelOpen = active == EditorPanelKey.COLOR_GRADING && state.filterPanelOpen,
            colorGradingLutPanelOpen = active == EditorPanelKey.COLOR_GRADING && state.colorGradingLutPanelOpen,
            adjustmentsPanelOpen = active == EditorPanelKey.ADJUSTMENTS && state.adjustmentsPanelOpen,
            fxPanelOpen = active == EditorPanelKey.FX && state.fxPanelOpen,
            transmissionPanelOpen = active == EditorPanelKey.TRANSMISSION && state.transmissionPanelOpen,
            transitionPickerOpen = active == EditorPanelKey.TRANSITION && state.transitionPickerOpen,
            textPanelOpen = active == EditorPanelKey.TEXT && state.textPanelOpen,
            audioMixerOpen = active == EditorPanelKey.AUDIO_MIXER && state.audioMixerOpen,
            clipVolumeSheetOpen = active == EditorPanelKey.CLIP_VOLUME && state.clipVolumeSheetOpen,
            audioFadeSheetOpen = active == EditorPanelKey.AUDIO_FADE && state.audioFadeSheetOpen,
            royaltyMusicDialogOpen = active == EditorPanelKey.ROYALTY_MUSIC_DIALOG && state.royaltyMusicDialogOpen,
            speedPanelOpen = active == EditorPanelKey.SPEED && state.speedPanelOpen,
            stickerPanelOpen = active == EditorPanelKey.STICKER && state.stickerPanelOpen,
            signaturePanelOpen = active == EditorPanelKey.SIGNATURE && state.signaturePanelOpen,
            voiceRecorderOpen = active == EditorPanelKey.VOICE_RECORDER && state.voiceRecorderOpen,
            arFilterPanelOpen = active == EditorPanelKey.AR_FILTER && state.arFilterPanelOpen,
            coverPanelOpen = active == EditorPanelKey.COVER && state.coverPanelOpen,
            chromaKeyPanelOpen = active == EditorPanelKey.CHROMA_KEY && state.chromaKeyPanelOpen,
            maskPanelOpen = active == EditorPanelKey.MASK && state.maskPanelOpen,
            textMaskPanelOpen = active == EditorPanelKey.TEXT_MASK && state.textMaskPanelOpen,
            overlayPropsOpen = active == EditorPanelKey.OVERLAY_PROPERTIES && state.overlayPropsOpen,
            helpDialogOpen = active == EditorPanelKey.HELP && state.helpDialogOpen,
            keyframePanelOpen = active == EditorPanelKey.KEYFRAME && state.keyframePanelOpen,
            photoEditPanelOpen = active == EditorPanelKey.PHOTO_EDIT && state.photoEditPanelOpen,
            editPackPanelOpen = active == EditorPanelKey.EDIT_PACK && state.editPackPanelOpen,
            adjustPackPanelOpen = active == EditorPanelKey.ADJUST_PACK && state.adjustPackPanelOpen,
            colorScopesPanelOpen = active == EditorPanelKey.COLOR_SCOPES && state.colorScopesPanelOpen,
            bgRemoverPanelOpen = active == EditorPanelKey.BG_REMOVER && state.bgRemoverPanelOpen,
            backgroundPanelOpen = active == EditorPanelKey.BACKGROUND_REPLACE && state.backgroundPanelOpen,
            autoClipPanelOpen = active == EditorPanelKey.AUTO_CLIP && state.autoClipPanelOpen,
            musicLibraryPanelOpen = active == EditorPanelKey.MUSIC_LIBRARY && state.musicLibraryPanelOpen,
            sfxLibraryPanelOpen = active == EditorPanelKey.SFX_LIBRARY && state.sfxLibraryPanelOpen,
            historyDraftsPanelOpen = active == EditorPanelKey.HISTORY_DRAFTS && state.historyDraftsPanelOpen
        )
    }
}
