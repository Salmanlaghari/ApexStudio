package com.apexstudio.app.ui.screens.editor

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.apexstudio.app.data.filter.LutFilterEngine
import com.apexstudio.app.data.media.MediaUriResolver
import com.apexstudio.app.data.picker.MediaPickerHelper
import com.apexstudio.app.data.template.TransmissionTemplate
import com.apexstudio.app.domain.model.AnimatedTransform
import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.presentation.state.AudioStudioState
import com.apexstudio.app.presentation.state.EditorState
import com.apexstudio.app.presentation.state.clipLayerAnchor
import com.apexstudio.app.presentation.state.keyframeAnchor
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.*
import com.apexstudio.app.ui.theme.ApexPalette
import com.apexstudio.app.presentation.viewmodel.generateAutoCaptions
import com.apexstudio.app.presentation.viewmodel.clearAutoCaptions
import com.apexstudio.app.presentation.viewmodel.resetCaptionUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Modal overlay sections extracted from [EditorScreen] to keep it under the
 * JVM 64KB method limit. Pure extraction — no behavior changes.
 */

/** Trim bottom-sheet overlay. */
@Composable
fun TrimPanelOverlay(
    state: EditorState,
    vm: EditorViewModel,
    exoPlayer: ExoPlayer?,
    onExport: () -> Unit
) {
    if (!state.trimPanelOpen) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            // WindowInsets: keep the panel clear of the status bar
            // (title) and the navigation bar (bottom buttons).
            .statusBarsPadding()
            .navigationBarsPadding()
            .background(Color.Transparent) // Pro Phase 1: locked preview stays visible
            .clickable { vm.closeTrimPanel() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
            val clipToTrim = state.project?.clips?.firstOrNull { it.id == state.selectedClipId }
                ?: state.project?.clips?.firstOrNull()
            TrimPanel(
                clip = clipToTrim,
                currentPlayheadMs = state.playerPositionMs,
                isTransformerTrimming = state.isTransformerTrimming,
                transformerTrimProgress = state.transformerTrimProgress,
                transformerTrimMessage = state.transformerTrimMessage,
                transformerTrimError = state.transformerTrimError,
                onTrimChange = { start, end ->
                    clipToTrim?.let {
                        vm.trimClip(it.id, start, end)
                        exoPlayer?.seekTo(start)
                    }
                },
                onSetStartAtPlayhead = {
                    clipToTrim?.let {
                        vm.setTrimStartAtPlayhead(it.id)
                        exoPlayer?.seekTo(state.playerPositionMs)
                    }
                },
                onSetEndAtPlayhead = {
                    clipToTrim?.let {
                        vm.setTrimEndAtPlayhead(it.id)
                        exoPlayer?.seekTo(state.playerPositionMs)
                    }
                },
                onResetTrim = { clipToTrim?.let { vm.resetTrim(it.id) } },
                onPreviewTrimmed = {
                    clipToTrim?.let {
                        exoPlayer?.seekTo(it.trimStartMs)
                        if (!state.isPlaying) vm.togglePlay()
                    }
                },
                onTrimWithTransformer = { clipId, startMs, endMs, replaceInTimeline ->
                    vm.trimClipWithMedia3Transformer(clipId, startMs, endMs, replaceInTimeline) { _ ->
                        exoPlayer?.seekTo(0L)
                    }
                },
                onCancelTransformerTrim = { vm.cancelMedia3Trim() },
                onClearTransformerStatus = { vm.clearTransformerTrimStatus() },
                onExport = onExport,
                onClose = { vm.closeTrimPanel() }
            )
        }
    }
}

/** GPU video filter bottom-sheet overlay. */
@Composable
fun GpuFilterPanelOverlay(
    state: EditorState,
    vm: EditorViewModel,
    filterEngine: LutFilterEngine
) {
    if (!state.gpuFilterPanelOpen) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            // WindowInsets: keep the panel clear of the status bar
            // (title) and the navigation bar (bottom buttons).
            .statusBarsPadding()
            .navigationBarsPadding()
            .background(Color.Transparent) // Pro Phase 1: locked preview stays visible
            .clickable { vm.closeGpuFilterPanel() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
            val activeClip = state.selectedClipId?.let { id ->
                state.project?.clips?.firstOrNull { it.id == id }
            } ?: state.project?.clips?.firstOrNull()

            GpuVideoFilterPanel(
                selectedClip = activeClip,
                config = state.activeGpuFilterConfig,
                manifest = filterEngine.manifest,
                selectedTab = state.gpuFilterSelectedTab,
                compareMode = state.gpuFilterCompareMode,
                splitPosition = state.gpuFilterSplitPosition,
                lutThumbnails = state.filterThumbnails,
                onTabSelected = { vm.setGpuFilterSelectedTab(it) },
                onConfigChange = { vm.setGpuFilterConfig(it) },
                onToggleCompare = { vm.toggleGpuFilterCompareMode() },
                onSplitPositionChange = { vm.setGpuFilterSplitPosition(it) },
                onApplyToClip = { vm.applyGpuFilterToSelectedClip() },
                onApplyToAllClips = { vm.applyGpuFilterToAllClips() },
                onReset = { vm.resetGpuFilter() },
                onClose = { vm.closeGpuFilterPanel() }
            )
        }
    }
}

/** Color grading / LUT bottom-sheet overlay. */
@Composable
fun ColorGradingPanelOverlay(
    state: EditorState,
    vm: EditorViewModel,
    filterEngine: LutFilterEngine
) {
    if (!(state.filterPanelOpen || state.colorGradingLutPanelOpen)) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            // WindowInsets: keep the panel clear of the status bar
            // (title) and the navigation bar (bottom buttons).
            .statusBarsPadding()
            .navigationBarsPadding()
            .background(Color.Transparent) // Pro Phase 1: locked preview stays visible
            .clickable {
                vm.closeFilterPanel()
                vm.closeColorGradingLutPanel()
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
            ColorGradingLutPanel(
                manifest = filterEngine.manifest,
                activeFilterId = state.activeFilterId,
                intensity = state.filterIntensity,
                targetTrack = state.lutTargetTrack,
                compareMode = state.lutCompareMode,
                splitPosition = state.lutCompareSplitPosition,
                favoriteIds = state.lutFavoriteIds,
                customLuts = state.customImportedLuts,
                contrast = state.lutContrast,
                saturation = state.lutSaturation,
                temperatureK = state.lutTemperature,
                tint = state.lutTint,
                thumbnails = state.filterThumbnails,
                galleryViewMode = state.lutGalleryViewMode,
                isLoadingThumbnails = state.filterThumbnailsLoading,
                onGalleryViewModeChange = { vm.setLutGalleryViewMode(it) },
                onRefreshThumbnails = { vm.refreshFilterThumbnailsFromVideo(force = true) },
                onSelectFilter = { vm.setActiveFilter(it) },
                onIntensityChange = { vm.setFilterIntensity(it) },
                onTargetTrackChange = { vm.setLutTargetTrack(it) },
                onToggleFavorite = { vm.toggleLutFavorite(it) },
                onToggleCompare = { vm.toggleLutCompareMode() },
                onSplitPositionChange = { vm.setLutCompareSplitPosition(it) },
                onContrastChange = { vm.setLutContrast(it) },
                onSaturationChange = { vm.setLutSaturation(it) },
                onTemperatureChange = { vm.setLutTemperature(it) },
                onTintChange = { vm.setLutTint(it) },
                onReset = { vm.resetLutGrading() },
                onClose = {
                    vm.closeFilterPanel()
                    vm.closeColorGradingLutPanel()
                }
            )
        }
    }
}

/** Adjustments bottom-sheet overlay. */
@Composable
fun AdjustmentsPanelOverlay(
    state: EditorState,
    vm: EditorViewModel
) {
    if (!state.adjustmentsPanelOpen) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent) // Pro Phase 1: locked preview stays visible
            .clickable { vm.closeAdjustmentsPanel() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) {}
        ) {
            AdjustPanel(
                adjustments = state.adjustments,
                onUpdate = { vm.updateAdjustments(it) },
                onReset = { vm.resetAdjustments() },
                onResetAll = { vm.resetAllAdjustments() },
                onClose = { vm.closeAdjustmentsPanel() }
            )
        }
    }
}

/** Animated FX bottom-sheet overlay. */
@Composable
fun FxPanelOverlay(
    state: EditorState,
    vm: EditorViewModel
) {
    if (!state.fxPanelOpen) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            // WindowInsets: keep the panel clear of the status bar
            // (title) and the navigation bar (bottom buttons).
            .statusBarsPadding()
            .navigationBarsPadding()
            .background(Color.Transparent) // Pro Phase 1: locked preview stays visible
            .clickable { vm.closeFxPanel() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
            FxPanel(
                activeFxId = state.activeFxId,
                intensity = state.fxIntensity,
                onFxSelected = { vm.setActiveFx(it) },
                onIntensityChange = { vm.setFxIntensity(it) },
                speed = state.fxSpeed,
                onSpeedChange = { vm.setFxSpeed(it) },
                onKeyframesClick = {
                    vm.setKeyframePanelOpen(true)
                    vm.closeFxPanel()
                },
                onClose = { vm.closeFxPanel() }
            )
        }
    }
}

/** Snap Camera Kit Lenses overlay (full-screen inside the editor). */
@Composable
fun LensesPanelOverlay(
    state: EditorState,
    vm: EditorViewModel
) {
    if (!state.lensesPanelOpen) return
    // LensesScreen is self-contained (own ViewModel); embedding it as an
    // overlay keeps the editor state alive underneath.
    com.apexstudio.app.camerakit.LensesScreen(onBack = { vm.closeLensesPanel() })
}

/** Transmission templates bottom-sheet overlay. */
@Composable
fun TransmissionTemplatesOverlay(
    state: EditorState,
    vm: EditorViewModel,
    templates: List<TransmissionTemplate>
) {
    if (!state.transmissionPanelOpen) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent) // Pro Phase 1: locked preview stays visible
            .clickable { vm.closeTransmissionTemplatesPanel() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
            TransmissionTemplatesPanel(
                templates = templates,
                activeTemplateId = state.project?.lastTransmissionTemplateId,
                onTemplateApplied = { vm.applyTransmissionTemplate(it) },
                onClose = { vm.closeTransmissionTemplatesPanel() }
            )
        }
    }
}

/** Transition picker bottom-sheet overlay. */
@Composable
fun TransitionPickerOverlay(
    state: EditorState,
    vm: EditorViewModel
) {
    if (!state.transitionPickerOpen) return
    val fromClip = state.project?.clips?.firstOrNull { it.id == state.transitionPickerFromClipId }
    val toClip = state.project?.clips?.firstOrNull { it.id == state.transitionPickerToClipId }
    val currentTransition = state.project?.transitions?.firstOrNull {
        it.fromClipId == state.transitionPickerFromClipId && it.toClipId == state.transitionPickerToClipId
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent) // Pro Phase 1: locked preview stays visible
            .clickable { vm.closeTransitionPicker() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
            TransitionPickerSheet(
                fromClip = fromClip,
                toClip = toClip,
                currentTransition = currentTransition,
                onApplyTransition = { type, durMs ->
                    val fId = state.transitionPickerFromClipId
                    val tId = state.transitionPickerToClipId
                    if (fId != null && tId != null) {
                        vm.applyTransition(fId, tId, type, durMs)
                    }
                },
                onApplyToAll = { type, durMs ->
                    vm.applyTransitionToAll(type, durMs)
                },
                onRemoveTransition = {
                    val fId = state.transitionPickerFromClipId
                    val tId = state.transitionPickerToClipId
                    if (fId != null && tId != null) {
                        vm.removeTransition(fId, tId)
                    }
                },
                onClose = { vm.closeTransitionPicker() }
            )
        }
    }
}

/** Text tools bottom-sheet overlay. */
@Composable
fun TextPanelOverlay(
    state: EditorState,
    vm: EditorViewModel
) {
    if (!state.textPanelOpen) return
    val textClip = state.project?.clips?.firstOrNull { it.id == state.selectedClipId }
        ?: state.project?.clips?.firstOrNull()
    val textOverlays = textClip?.textOverlays ?: emptyList()
    val activeOverlayId = state.selectedTextOverlayId
    val captionUiState by vm.captionUi.collectAsState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent) // Pro Phase 1: locked preview stays visible
            .clickable { vm.closeTextPanel() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
            TextPanel(
                overlays = textOverlays,
                selectedId = activeOverlayId,
                onAdd = { textClip?.let { vm.addTextOverlay(it.id) } },
                onSelect = { vm.selectTextOverlay(it) },
                onTextChange = { text ->
                    if (textClip != null && activeOverlayId != null) {
                        vm.setTextOverlayText(textClip.id, activeOverlayId, text)
                    }
                },
                onColorChange = { argb ->
                    if (textClip != null && activeOverlayId != null) {
                        vm.setTextOverlayColor(textClip.id, activeOverlayId, argb)
                    }
                },
                onBgChange = { argb ->
                    if (textClip != null && activeOverlayId != null) {
                        vm.setTextOverlayBg(textClip.id, activeOverlayId, argb)
                    }
                },
                onSizeChange = { scale ->
                    if (textClip != null && activeOverlayId != null) {
                        vm.setTextOverlaySize(textClip.id, activeOverlayId, scale)
                    }
                },
                onFontFamilyChange = { font ->
                    if (textClip != null && activeOverlayId != null) {
                        vm.setTextOverlayFont(textClip.id, activeOverlayId, font)
                    }
                },
                onStyleChange = { bold, italic ->
                    if (textClip != null && activeOverlayId != null) {
                        vm.setTextOverlayStyle(textClip.id, activeOverlayId, bold, italic)
                    }
                },
                onShadowChange = { shadow ->
                    if (textClip != null && activeOverlayId != null) {
                        vm.setTextOverlayShadow(textClip.id, activeOverlayId, shadow)
                    }
                },
                onAlignChange = { align ->
                    if (textClip != null && activeOverlayId != null) {
                        vm.setTextOverlayAlign(textClip.id, activeOverlayId, align)
                    }
                },
                onLetterSpacingChange = { spacing ->
                    if (textClip != null && activeOverlayId != null) {
                        vm.setTextOverlayLetterSpacing(textClip.id, activeOverlayId, spacing)
                    }
                },
                onGradientChange = { gradient ->
                    if (textClip != null && activeOverlayId != null) {
                        vm.setTextOverlayGradient(textClip.id, activeOverlayId, gradient)
                    }
                },
                onAnimDurationChange = { dur ->
                    if (textClip != null && activeOverlayId != null) {
                        vm.setTextOverlayAnimDuration(textClip.id, activeOverlayId, dur)
                    }
                },
                onOutroChange = { outro ->
                    if (textClip != null && activeOverlayId != null) {
                        vm.setTextOverlayOutro(textClip.id, activeOverlayId, outro)
                    }
                },
                onOutroDurationChange = { dur ->
                    if (textClip != null && activeOverlayId != null) {
                        vm.setTextOverlayOutroDuration(textClip.id, activeOverlayId, dur)
                    }
                },
                onDuplicate = { overlayId ->
                    textClip?.let { vm.duplicateTextOverlay(it.id, overlayId) }
                },
                onDelete = { overlayId ->
                    textClip?.let { vm.removeTextOverlay(it.id, overlayId) }
                },
                onApplyPreset = { preset ->
                    if (textClip != null && activeOverlayId != null) {
                        vm.applyTextPreset(textClip.id, activeOverlayId, preset)
                    }
                },
                onAnimationChange = { anim ->
                    if (textClip != null && activeOverlayId != null) {
                        vm.setTextOverlayAnimation(textClip.id, activeOverlayId, anim)
                    }
                },
                onClose = { vm.closeTextPanel() },
                // One-tap Text Reveal template: needs a clip to attach
                // the text to; applies at the playhead, then closes
                // the panel so the result is visible immediately.
                canApplyTemplate = textClip != null,
                onApplyTextReveal = {
                    vm.applyTextRevealTemplate()
                    vm.closeTextPanel()
                },
                onOpenTextMask = { vm.openTextMaskPanel() },
                captionUiState = captionUiState,
                onAutoCaptions = {
                    textClip?.let {
                        vm.resetCaptionUi()
                        vm.generateAutoCaptions(it.id)
                    }
                },
                onClearCaptions = { textClip?.let { vm.clearAutoCaptions(it.id) } },
                onCaptionLanguageChange = { vm.setCaptionLanguage(it) },
                onCancelCaptions = { vm.cancelAutoCaptions() },
                hasAutoCaptions = textOverlays.any { it.presetId == "caption_auto" }
            )
        }
    }
}

/** Audio mixer bottom-sheet overlay. */
@Composable
fun AudioMixerPanelOverlay(
    state: EditorState,
    audioState: AudioStudioState,
    vm: EditorViewModel,
    audioPickerLauncher: ActivityResultLauncher<String>,
    onOpenRoyaltyFreeSheet: () -> Unit,
    audioSessionId: Int = 0
) {
    if (!state.audioMixerOpen) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent) // Pro Phase 1: locked preview stays visible
            .clickable { vm.closeAudioMixer() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
            AudioMixerPanel(
                state = audioState,
                muteOriginalVideo = audioState.isMuted,
                onMuteOriginal = { vm.setMuteOriginalVideo(it) },
                onAddTrack = { name, uri, kind -> vm.addAudioTrack(name, uri, kind) },
                onImportLocalAudio = {
                    audioPickerLauncher.launch("audio/*")
                },
                onOpenRoyaltyFreeMusic = onOpenRoyaltyFreeSheet,
                onRemoveTrack = { vm.removeAudioTrack(it) },
                // Volume fader: routes to the volume-keyframe upsert
                // when the track has volume keyframes, else to the
                // scalar volume (behaviour unchanged). Writes use
                // the LIVE playhead — the ~10Hz state mirror can lag
                // by up to 100ms and would misplace the pin.
                onVolume = { trackId, vol ->
                    vm.setAudioTrackVolumeAuto(trackId, vol, vm.livePlayheadMs())
                },
                onMute = { trackId -> vm.toggleAudioTrackMute(trackId) },
                onSolo = { trackId -> vm.toggleAudioTrackSolo(trackId) },
                onTrim = { trackId, start, end -> vm.setAudioTrackTrim(trackId, start, end) },
                onFadeIn = { trackId, ms -> vm.setAudioTrackFadeIn(trackId, ms) },
                onFadeOut = { trackId, ms -> vm.setAudioTrackFadeOut(trackId, ms) },
                onClose = { vm.closeAudioMixer() },
                audioSessionId = audioSessionId,
                playheadMs = state.playerPositionMs,
                onAddVolumeKeyframe = { trackId ->
                    vm.addAudioVolumeKeyframe(trackId, vm.livePlayheadMs())
                },
                onRemoveVolumeKeyframe = { trackId, kfId ->
                    vm.removeAudioVolumeKeyframe(trackId, kfId)
                },
                onSeekTo = { targetMs -> vm.seekTo(targetMs) }
            )
        }
    }
}

/** Royalty-free music sheets (both the audio-mixer entry sheet and the dialog). */
@Composable
fun RoyaltyMusicSheetOverlay(
    showRoyaltyFreeSheet: Boolean,
    onCloseRoyaltyFreeSheet: () -> Unit,
    state: EditorState,
    vm: EditorViewModel,
    audioPickerLauncher: ActivityResultLauncher<String>
) {
    if (showRoyaltyFreeSheet) {
        // Bottom-aligned overlay: the sheet was composed bare (top-aligned
        // in the parent Box), so its title drew under the status bar.
        // No scrim/clickable here — the sheet has its own close control
        // and touches outside it keep passing through to the editor.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                RoyaltyFreeMusicSheet(
                    onClose = onCloseRoyaltyFreeSheet,
                    onSelectSong = { title, filePath, durationMs ->
                        vm.addAudioTrack(
                            name = title,
                            uri = filePath,
                            kind = AudioTrack.Kind.MUSIC,
                            sourceDurationMs = durationMs
                        )
                        onCloseRoyaltyFreeSheet()
                    }
                )
            }
        }
    }

    if (state.royaltyMusicDialogOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                RoyaltyFreeMusicSheet(
                    onClose = { vm.closeRoyaltyMusicDialog() },
                    onSelectSong = { title, filePath, durationMs ->
                        vm.addRoyaltyTrack(title, filePath, durationMs)
                        vm.closeRoyaltyMusicDialog()
                    },
                    // Device import: reuse the existing MediaStore audio picker; the
                    // picked file lands on the audio track via audioPickerLauncher.
                    onImportAudio = {
                        vm.closeRoyaltyMusicDialog()
                        audioPickerLauncher.launch("audio/*")
                    }
                )
            }
        }
    }
}

/** Speed ramp bottom-sheet overlay. */
@Composable
fun SpeedPanelOverlay(
    state: EditorState,
    vm: EditorViewModel
) {
    if (!state.speedPanelOpen) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent) // Pro Phase 1: locked preview stays visible
            .clickable { vm.closeSpeedPanel() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
            val currentClip = state.project?.clips?.firstOrNull { it.id == state.selectedClipId } ?: state.project?.clips?.firstOrNull()
            SpeedRampPanel(
                selectedClipId = state.selectedClipId,
                currentSpeed = state.playbackSpeed,
                activeClipSpeed = currentClip?.speedMultiplier ?: state.playbackSpeed,
                opticalFlowEnabled = state.opticalFlowMotionBlur,
                opticalFlowIntensity = state.opticalFlowBlurIntensity,
                onSelectPreset = { preset ->
                    vm.setPlaybackSpeed(preset.multiplier)
                    state.selectedClipId?.let { cid -> vm.setClipSpeed(cid, preset.multiplier) }
                },
                onCustomSpeed = { speed ->
                    vm.setPlaybackSpeed(speed)
                    state.selectedClipId?.let { cid -> vm.setClipSpeed(cid, speed) }
                },
                onToggleOpticalFlow = { enabled ->
                    vm.setOpticalFlowMotionBlur(enabled)
                },
                onChangeOpticalFlowIntensity = { intensity ->
                    vm.setOpticalFlowMotionBlur(state.opticalFlowMotionBlur, intensity)
                },
                // Variable speed curve (PR-E): commits on gesture end.
                speedPoints = currentClip?.speedPoints ?: emptyList(),
                clipIsVideo = (currentClip?.type
                    ?: com.apexstudio.app.domain.model.ClipType.VIDEO) ==
                    com.apexstudio.app.domain.model.ClipType.VIDEO,
                clipSourceDurationMs = (currentClip?.trimEndMs ?: 0L) -
                    (currentClip?.trimStartMs ?: 0L),
                onCurveChanged = { pts ->
                    state.selectedClipId?.let { cid -> vm.setClipSpeedPoints(cid, pts) }
                },
                onClearCurve = {
                    state.selectedClipId?.let { cid -> vm.setClipSpeedPoints(cid, emptyList()) }
                },
                onClose = { vm.closeSpeedPanel() }
            )
        }
    }
}

/** Sticker picker bottom-sheet overlay. */
@Composable
fun StickerPanelOverlay(
    state: EditorState,
    vm: EditorViewModel
) {
    if (!state.stickerPanelOpen) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent) // Pro Phase 1: locked preview stays visible
            .clickable { vm.closeStickerPanel() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
            StickerPanel(
                onAddSticker = { assetPath, cat, name -> vm.addStickerAsset(assetPath, cat, name) },
                onClose = { vm.closeStickerPanel() }
            )
        }
    }
}

/** Voice recorder bottom-sheet overlay. */
@Composable
fun VoiceRecorderOverlay(
    state: EditorState,
    vm: EditorViewModel
) {
    if (!state.voiceRecorderOpen) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent) // Pro Phase 1: locked preview stays visible
            .clickable { vm.closeVoiceRecorder() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
            VoiceRecorderPanel(
                onAddRecording = { uri, dur, name -> vm.addVoiceOverTrack(uri, dur, name) },
                onClose = { vm.closeVoiceRecorder() }
            )
        }
    }
}

/** Camera capture bottom-sheet overlay. */
@Composable
fun CameraCaptureOverlay(
    state: EditorState,
    vm: EditorViewModel
) {
    if (!state.cameraCaptureOpen) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent) // Pro Phase 1: locked preview stays visible
            .clickable { vm.closeCameraCapture() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
            CameraCapturePanel(
                onCapturePicked = { meta -> vm.onMediaPicked(meta) },
                selectedArFilterId = state.activeArFilterId,
                onSelectArFilter = { vm.selectArFilter(it) },
                onClose = { vm.closeCameraCapture() }
            )
        }
    }
}

/** AR face filter bottom-sheet overlay. */
@Composable
fun ArFilterPanelOverlay(
    state: EditorState,
    vm: EditorViewModel
) {
    if (!state.arFilterPanelOpen) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent) // Pro Phase 1: locked preview stays visible
            .clickable { vm.closeArFilterPanel() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
            ArFilterPanel(
                activeFilterId = state.activeArFilterId,
                intensity = state.arFilterIntensity,
                customText = state.arFilterCustomText,
                onFilterSelected = { vm.selectArFilter(it) },
                onIntensityChange = { vm.setArFilterIntensity(it) },
                onCustomTextChange = { vm.setArFilterCustomText(it) },
                onClose = { vm.closeArFilterPanel() }
            )
        }
    }
}

/** Cover frame bottom-sheet overlay. */
@Composable
fun CoverPanelOverlay(
    state: EditorState,
    vm: EditorViewModel,
    mediaPicker: MediaPickerHelper
) {
    if (!state.coverPanelOpen) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent) // Pro Phase 1: locked preview stays visible
            .clickable { vm.closeCoverPanel() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
            CoverPanel(
                currentPlayheadMs = state.playerPositionMs,
                coverFrameMs = state.project?.coverFrameMs,
                customCoverUri = state.project?.coverCustomUri,
                onSelectFrameAtPlayhead = { vm.setCoverFrame(it) },
                onSelectCustomCover = {
                    mediaPicker.pickSingleMedia.launch("image/*")
                    vm.closeCoverPanel()
                },
                onClose = { vm.closeCoverPanel() }
            )
        }
    }
}

/** Chroma key bottom-sheet overlay. */
@Composable
fun ChromaKeyPanelOverlay(
    state: EditorState,
    vm: EditorViewModel,
    mediaPicker: MediaPickerHelper
) {
    if (!state.chromaKeyPanelOpen) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent) // Pro Phase 1: locked preview stays visible
            .clickable { vm.closeChromaKeyPanel() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
            ChromaKeyPanel(
                settings = state.chromaKeySettings,
                onUpdate = { vm.updateChromaKeySettings(it) },
                onPickCustomBg = {
                    mediaPicker.pickSingleMedia.launch("image/*")
                },
                onClose = { vm.closeChromaKeyPanel() }
            )
        }
    }
}

/** Mask tool bottom-sheet overlay. */
@Composable
fun MaskPanelOverlay(
    state: EditorState,
    vm: EditorViewModel
) {
    if (!state.maskPanelOpen) return
    // Bug 4: the mask may edit ONLY the selected video/image layer. The
    // old `?: clips.firstOrNull()` fallback silently retargeted it to the
    // project's first clip, and openMaskPanelTopLevel() already guarantees
    // a target whenever the sheet is called for without a selection.
    val selectedClip = state.clipLayerAnchor() ?: return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clickable { vm.closeMaskPanel() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
            MaskPanel(
                mask = selectedClip?.mask,
                keyframeTimeMs = state.playerPositionMs,
                onUpdate = { mask ->
                    val clipId = selectedClip?.id ?: return@MaskPanel
                    if (mask == null) vm.clearClipMask(clipId) else vm.setClipMask(clipId, mask)
                },
                onClose = { vm.closeMaskPanel() }
            )
        }
    }
}

/**
 * Mask tool bottom-sheet overlay for the selected TEXT overlay —
 * the same MaskPanel as the clip mask (shape / feather / opacity /
 * invert), bound to the text's mask via [updateTextMask]. Mask
 * keyframes are edited on the timeline's gold mask strip; removing
 * the mask here also drops its keyframes.
 */
@Composable
fun TextMaskPanelOverlay(
    state: EditorState,
    vm: EditorViewModel
) {
    if (!state.textMaskPanelOpen) return
    val selectedClip = state.project?.clips?.firstOrNull { it.id == state.selectedClipId }
        ?: state.project?.clips?.firstOrNull()
    val overlayId = state.selectedTextOverlayId
    val overlay = selectedClip?.textOverlays?.firstOrNull { it.id == overlayId }
    if (selectedClip == null || overlay == null) {
        LaunchedEffect(Unit) { vm.closeTextMaskPanel() }
        return
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clickable { vm.closeTextMaskPanel() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
            MaskPanel(
                mask = overlay.mask,
                onUpdate = { mask -> vm.updateTextMask(selectedClip.id, overlay.id, mask) },
                onClose = { vm.closeTextMaskPanel() }
            )
        }
    }
}

/** Overlay (PiP) properties panel overlay. */
@Composable
fun OverlayPropertiesPanelOverlay(
    state: EditorState,
    vm: EditorViewModel
) {
    if (!state.overlayPropsOpen) return
    val selectedClip = state.project?.clips?.firstOrNull { it.id == state.selectedClipId }
    // Only overlays have a properties panel; if the selection moved to
    // a non-overlay while the panel was open, close it.
    if (selectedClip == null || selectedClip.type != ClipType.OVERLAY) {
        LaunchedEffect(Unit) { vm.closeOverlayPropsPanel() }
        return
    }
    // Probe result for this clip is owned by the preview section; the
    // panel re-probes cheaply (cached) to decide whether to show volume.
    val context = LocalContext.current
    var isVideo by remember(selectedClip.id, selectedClip.uri) {
        mutableStateOf<Boolean?>(null)
    }
    LaunchedEffect(selectedClip.id, selectedClip.uri) {
        isVideo = com.apexstudio.app.data.media.OverlayMediaProbe.isVideo(
            context, selectedClip.uri
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clickable { vm.closeOverlayPropsPanel() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
            OverlayPropertiesPanel(
                clip = selectedClip,
                isVideo = isVideo == true,
                playheadMs = state.playerPositionMs,
                onOpacity = { vm.setOverlayClipOpacity(selectedClip.id, it) },
                // Bug 3: explicit layout mirroring the canvas gestures,
                // plus the overlay's own source trim.
                onLayout = { x, y, scale, rotation ->
                    vm.setOverlayLayout(selectedClip.id, x, y, scale, rotation)
                },
                onTrim = { s, e ->
                    vm.trimOverlayClip(selectedClip.id, s, e)
                },
                onBlend = { vm.setOverlayBlend(selectedClip.id, it) },
                onMaskShape = { shape ->
                    // Undo: one entry per panel session (armed flag).
                    vm.armOverlayUndo()
                    val mask = shape.toVideoMask()
                    if (mask == null) vm.clearClipMask(selectedClip.id)
                    else vm.setClipMask(selectedClip.id, mask)
                },
                onBorder = { width, color ->
                    vm.setOverlayBorder(selectedClip.id, width, color)
                },
                onShadow = { vm.setOverlayShadow(selectedClip.id, it) },
                onFlip = { h, v -> vm.setOverlayFlip(selectedClip.id, h, v) },
                onVolume = { vm.setOverlayVolume(selectedClip.id, it) },
                onTrack = { layer -> vm.moveClipToVideoLayer(selectedClip.id, layer) },
                onAddKeyframe = {
                    vm.captureOverlayKeyframe(selectedClip.id, state.playerPositionMs)
                },
                onSeekKeyframe = { vm.seekTo(it) },
                onRemoveKeyframe = { kfId ->
                    vm.removeOverlayKeyframe(selectedClip.id, kfId)
                },
                onClose = { vm.closeOverlayPropsPanel() }
            )
        }
    }
}

/** Help dialog overlay. */
@Composable
fun HelpDialogOverlay(
    state: EditorState,
    vm: EditorViewModel
) {
    if (!state.helpDialogOpen) return
    HelpDialog(onDismiss = { vm.closeHelpDialog() })
}

/** Keyframe editor bottom-sheet overlay.
 *
 * Opens the visually-designed Animation CARDS grid first (Prince: "manual
 * sliders ki jagah ready-made animation CARDS banao"). The manual slider
 * panel (Keyframe Studio) is reachable ONLY through the "Customized" card.
 */
@Composable
fun KeyframePanelOverlay(
    state: EditorState,
    vm: EditorViewModel
) {
    if (!state.keyframePanelOpen) return
    // Bug 5: the panel may edit ONLY the video layer under selection.
    // The old `?: clips.firstOrNull()` fallback silently retargeted it to
    // the project's first clip when the selection was cleared, so an
    // auto-keyframe slider write landed on a layer the user never touched.
    val selectedClip = state.keyframeAnchor()
    val track = selectedClip?.keyframes ?: com.apexstudio.app.domain.model.KeyframeTrack()
    var showManualPanel by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent) // Pro Phase 1: locked preview stays visible
            .clickable { vm.setKeyframePanelOpen(false) },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
            if (showManualPanel) {
                KeyframePanel(
                    track = track,
                    playheadMs = state.playerPositionMs,
                    clipDurationMs = selectedClip?.durationMs ?: state.durationMs,
                    canAdd = selectedClip != null,
                    onAdd = { atMs ->
                        selectedClip?.let {
                            vm.addKeyframe(it.id, atMs)
                        }
                    },
                    onUpdate = { kf ->
                        selectedClip?.let {
                            vm.updateKeyframe(it.id, kf.id) { _ -> kf }
                        }
                    },
                    // CapCut-style auto-keyframe: slider changes at the
                    // playhead upsert one parameter there (no manual
                    // "+" needed once the clip has keyframes). Writes
                    // use the LIVE playhead — the ~10Hz state mirror
                    // can lag by up to 100ms and would misplace the
                    // keyframe (same class as the mixer fix above).
                    onUpsertParam = { param, value ->
                        selectedClip?.let {
                            vm.upsertKeyframeParam(it.id, vm.livePlayheadMs(), param, value)
                        }
                    },
                    onRemove = { kfId ->
                        selectedClip?.let {
                            vm.removeKeyframe(it.id, kfId)
                        }
                    },
                    onClear = {
                        selectedClip?.let {
                            vm.clearKeyframes(it.id)
                        }
                    },
                    // Back to the cards grid (manual panel lives only behind "Customized").
                    onClose = { showManualPanel = false }
                )
            } else {
                KeyframePresetCards(
                    onApplyPreset = { preset ->
                        vm.applyAnimationPreset(preset)
                    },
                    onOpenCustomized = { showManualPanel = true },
                    onClose = { vm.setKeyframePanelOpen(false) }
                )
            }
        }
    }
}

/** Add-media bottom-sheet overlay. */
@Composable
fun AddMediaMenuSheetOverlay(
    show: Boolean,
    vm: EditorViewModel,
    mediaPicker: MediaPickerHelper,
    onDismiss: () -> Unit
) {
    if (!show) return
    AddMediaMenuSheet(
        onPickVideo = {
            vm.setPendingAddAsOverlay(false)
            vm.setPendingAddAsAudio(false)
            mediaPicker.pickMultipleMedia.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
        },
        onPickOverlay = {
            vm.setPendingAddAsOverlay(true)
            vm.setPendingAddAsAudio(false)
            mediaPicker.pickMultipleMedia.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
            )
        },
        onPickAudio = {
            vm.setPendingAddAsOverlay(false)
            vm.setPendingAddAsAudio(true)
            mediaPicker.pickAudioMedia.launch("audio/*")
        },
        onDismiss = onDismiss
    )
}

/**
 * Main video preview area. ColumnScope extension so the weight(1f) layout
 * behaves exactly as before extraction.
 */
@Composable
fun androidx.compose.foundation.layout.ColumnScope.EditorPreviewSection(
    state: EditorState,
    vm: EditorViewModel,
    exoPlayer: ExoPlayer?,
    isCoverMode: Boolean,
    currentSelectedClip: MediaClip?,
    seekPlayerAndState: (Long) -> Unit,
    onOpenAddMediaMenu: () -> Unit,
    // Dedicated muted player for the live video PiP overlay. Created and
    // released by the screen; bound to the active video overlay clip below.
    overlayPlayer: ExoPlayer? = null,
    // Overlay media-kind probe results, owned by EditorScreen (single
    // source of truth shared with the player binding): true = video,
    // false = image (positive evidence), ABSENT = pending or
    // unanswerable (never defaulted to image).
    overlayIsVideo: Map<String, Boolean> = emptyMap()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        // Selecting a V2+ overlay must not make its media/keyframes the
        // background preview source; PipOverlayCanvas owns that layer.
        val selectedClip = resolveMainPreviewClip(
            state.project?.clips.orEmpty(), state.selectedClipId
        )
        val animatedTransform = remember(selectedClip, state.playerPositionMs) {
            selectedClip?.keyframes?.interpolateAt(state.playerPositionMs)
                ?: AnimatedTransform.Identity
        }
        // All overlay clips across video layers V2..V10 — the interactive
        // canvas shows the ones active at the playhead and lets the user
        // edit them. Hidden layers are skipped.
        val overlayClips = remember(state.project?.clips, state.hiddenVideoLayers) {
            state.project?.clips?.filter {
                it.type == ClipType.OVERLAY && it.trackIndex !in state.hiddenVideoLayers
            } ?: emptyList()
        }
        // Media kind per overlay clip comes from the `overlayIsVideo`
        // param (owned by EditorScreen, shared with the player
        // binding — one probe, no lag, no disagreement). The clip
        // type is always OVERLAY, so the media itself must answer
        // (OverlayMediaProbe, cached per URI). The old code branched
        // on `it.type == ClipType.VIDEO` over this OVERLAY-only list,
        // so the player was never bound and video overlays rendered
        // as an empty black box (Coil cannot decode video).
        // The video overlay clip the shared muted player is bound to:
        // the first VIDEO overlay active at the playhead.
        val activeOverlayVideoClip = overlayClips.firstOrNull {
            overlayIsVideo[it.id] == true && it.isOverlayActiveAt(state.playerPositionMs)
        }

        // Bind the overlay player to the active video overlay clip
        // (trimmed range, looping, muted). Re-binds when the active
        // clip changes (including entering/leaving its window).
        LaunchedEffect(
            overlayPlayer, activeOverlayVideoClip?.id, activeOverlayVideoClip?.uri,
            activeOverlayVideoClip?.trimStartMs, activeOverlayVideoClip?.trimEndMs
        ) {
            val player = overlayPlayer ?: return@LaunchedEffect
            val clip = activeOverlayVideoClip
            if (clip == null) {
                player.stop()
                player.clearMediaItems()
                return@LaunchedEffect
            }
            val endMs = clip.trimEndMs.takeIf { it > clip.trimStartMs && it != Long.MAX_VALUE }
            val item = androidx.media3.common.MediaItem.Builder()
                .setUri(clip.uri)
                .setClippingConfiguration(
                    androidx.media3.common.MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.trimStartMs.coerceAtLeast(0L))
                        .apply { if (endMs != null) setEndPositionMs(endMs) }
                        .build()
                )
                .build()
            player.setMediaItem(item)
            player.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
            // Overlay volume (properties panel): video overlays are
            // audible at their own gain alongside V1 — NOT forced
            // muted (the old `volume = 0f` made the volume control
            // meaningless and video overlays silently voiceless).
            player.volume = clip.overlayVolume.coerceIn(0f, 2f)
            player.prepare()
            player.pause()
            val rel = (state.playerPositionMs - clip.timelineOffsetMs).coerceAtLeast(0L)
            player.seekTo(rel)
            if (state.isPlaying) player.play()
        }
        // Volume follows the properties-panel slider without rebinding.
        LaunchedEffect(overlayPlayer, activeOverlayVideoClip?.id, activeOverlayVideoClip?.overlayVolume) {
            val player = overlayPlayer ?: return@LaunchedEffect
            player.volume = (activeOverlayVideoClip?.overlayVolume ?: 0f).coerceIn(0f, 2f)
        }
        // Play/pause the overlay in sync with the main timeline.
        LaunchedEffect(overlayPlayer, state.isPlaying, activeOverlayVideoClip?.id) {
            val player = overlayPlayer ?: return@LaunchedEffect
            if (activeOverlayVideoClip == null) return@LaunchedEffect
            if (state.isPlaying) player.play() else player.pause()
        }
        // Drift correction: after a scrub (or clock drift) the overlay
        // player is re-seeked to the overlay-relative playhead position.
        // Cheap long comparisons; only seeks past a deadband.
        LaunchedEffect(state.playerPositionMs) {
            val player = overlayPlayer ?: return@LaunchedEffect
            val clip = activeOverlayVideoClip ?: return@LaunchedEffect
            if (player.playbackState != androidx.media3.common.Player.STATE_READY) return@LaunchedEffect
            val rel = (state.playerPositionMs - clip.timelineOffsetMs).coerceAtLeast(0L)
            val deadband = if (state.isPlaying) 450L else 120L
            if (kotlin.math.abs(player.currentPosition - rel) > deadband) {
                player.seekTo(rel)
            }
        }

        val stickers = (state.project?.stickers ?: emptyList()) + (selectedClip?.stickers ?: emptyList())
        val textOverlays = selectedClip?.textOverlays ?: emptyList()
        var showUpcomingPreview by remember { mutableStateOf(false) }

        // Auto-hide screen controls: hidden by default, visible for 2s on video screen tap
        var screenControlsVisible by remember { mutableStateOf(false) }
        var hideControlsJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

        fun triggerScreenControls() {
            screenControlsVisible = true
            showUpcomingPreview = true
            hideControlsJob?.cancel()
            hideControlsJob = scope.launch {
                delay(2000L)
                screenControlsVisible = false
                showUpcomingPreview = false
            }
        }

        // Small filename chip (bottom-left in the preview): the
        // selected clip/track name, moved here from the clip bar.
        val selectionFileLabel = when (resolveToolbarSelection(state)) {
            ToolbarSelectionKind.VIDEO, ToolbarSelectionKind.PHOTO ->
                state.project?.clips?.firstOrNull { it.id == state.selectedClipId }?.name
            ToolbarSelectionKind.AUDIO ->
                state.project?.audioTracks?.firstOrNull { it.id == state.selectedAudioTrackId }?.name
                    ?: state.project?.clips?.firstOrNull { it.id == state.selectedClipId }?.name
            ToolbarSelectionKind.TEXT -> "Text"
            ToolbarSelectionKind.STICKER -> "Sticker"
            ToolbarSelectionKind.NONE -> null
        }

        VideoPreviewArea(
            selectionFileLabel = selectionFileLabel,
            exoPlayer = exoPlayer,
            chromaKeySettings = state.chromaKeySettings,
            overlayClips = overlayClips,
            selectedOverlayClipId = state.selectedOverlayClipId,
            overlayPlayer = overlayPlayer,
            overlayPlayerClipId = activeOverlayVideoClip?.id,
            overlayIsVideo = overlayIsVideo,
            onSelectOverlayClip = { vm.selectOverlayClip(it) },
            onMoveOverlayClip = { id, dx, dy, persist ->
                vm.moveOverlayClip(id, dx, dy, persist)
            },
            onScaleOverlayClip = { id, scale, persist ->
                vm.scaleOverlayClip(id, scale, persist)
            },
            onRotateOverlayClip = { id, deltaDeg, persist ->
                vm.rotateOverlayClip(id, deltaDeg, persist)
            },
            onOverlayGestureEnd = { vm.persistOverlayClipGesture(it) },
            onRemoveOverlayClip = { vm.removeOverlayClip(it) },
            onUpsertOverlayKeyframe = { id, timeMs, x, y, scale, rotation, opacity, persist ->
                vm.upsertOverlayKeyframe(id, timeMs, x, y, scale, rotation, opacity, persist)
            },
            isCoverMode = isCoverMode,
            adjustments = state.adjustments,
            activeFilterId = state.activeFilterId,
            filterIntensity = state.filterIntensity,
            gpuFilterConfig = state.activeGpuFilterConfig,
            gpuFilterCompareMode = state.gpuFilterCompareMode,
            gpuFilterSplitPosition = state.gpuFilterSplitPosition,
            lutCompareMode = state.lutCompareMode,
            lutCompareSplitPosition = state.lutCompareSplitPosition,
            activeArFilterId = state.activeArFilterId,
            arFilterIntensity = state.arFilterIntensity,
            arFilterCustomText = state.arFilterCustomText,
            playerError = state.playerError,
            activeFxId = state.activeFxId,
            fxIntensity = state.fxIntensity,
            fxSpeed = state.fxSpeed,
            isPlaying = state.isPlaying,
            animatedTransform = animatedTransform,
            // CapCut-style V1 pinch-to-zoom (stable lambda: the VM
            // reads the current selection + playhead on each event).
            onPinchZoom = remember(vm) { { zoom: Float -> vm.pinchZoomSelectedClip(zoom) } },
            // Per-clip saturation keyframes: factor from the
            // selected clip's track at the playhead (1f = no pins).
            keyframedSaturationFactor = animatedTransform.saturation ?: 1f,
            currentTimeMs = state.playerPositionMs,
            durationMs = state.durationMs,
            // Shape mask (Mask tool) on the selected main clip, with
            // drag-to-move/resize while the mask panel is open.
            // Overlay (V2 PiP) clips preview their mask as a hard clip on
            // the PiP box itself, so the main-frame scrim is restricted to
            // non-overlay clips.
            mask = currentSelectedClip?.takeIf { it.type != ClipType.OVERLAY }?.mask,
            maskEditMode = state.maskPanelOpen,
            onMaskCenterLive = { x, y ->
                currentSelectedClip?.id?.let { vm.setMaskCenter(it, x, y, persist = false) }
            },
            onMaskCenterFinished = { x, y ->
                currentSelectedClip?.id?.let { vm.setMaskCenter(it, x, y, persist = true) }
            },
            onMaskSizeLive = { sx, sy ->
                currentSelectedClip?.id?.let { vm.setMaskSize(it, sx, sy, persist = false) }
            },
            onMaskSizeFinished = { sx, sy ->
                currentSelectedClip?.id?.let { vm.setMaskSize(it, sx, sy, persist = true) }
            },
            // Only V1 images replace the background preview. Image overlays
            // are rendered independently in PipOverlayCanvas via Coil.
            photoClip = selectedClip?.takeIf { it.type == ClipType.IMAGE },
            photoCropActive = state.photoEditPanelOpen &&
                    state.photoEditTab == com.apexstudio.app.presentation.state.PhotoEditTab.CROP,
            onPhotoCropRectChange = { rect ->
                selectedClip?.id?.let { vm.setPhotoCrop(it, rect) }
            },
            onTapVideo = {
                triggerScreenControls()
            },
            onRetryLoad = {
                exoPlayer?.let { player ->
                    vm.setPlayerError(null)
                    try {
                        val fallbackUri = MediaUriResolver.resolvePlayableUri(context, null)
                        player.setMediaItem(MediaItem.fromUri(fallbackUri))
                        player.prepare()
                        player.play()
                    } catch (e: Exception) {
                        vm.setPlayerError("Error reloading video: ${e.message}")
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        val controlsAlpha by animateFloatAsState(
            targetValue = if (screenControlsVisible) 1f else 0f,
            animationSpec = tween(250),
            label = "controlsAlpha"
        )

        // Auto-hiding Floating Left Quick Tool Rail (AR Face, Effects, Filters, 3D Chroma, Adjust, Text)
        if (controlsAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 6.dp)
                    .graphicsLayer { alpha = controlsAlpha }
            ) {
                LeftToolRail(
                    onArFilters = { vm.openArFilterPanel() },
                    onEffects = { vm.openFxPanel() },
                    onFilters = { vm.openFilterPanel() },
                    onAdjust = { vm.openAdjustmentsPanel() },
                    onChromaKey = { vm.openChromaKeyPanel() },
                    onText = { vm.openTextPanel() },
                    onSticker = { vm.openStickerPanel() }
                )
            }
        }

        // Auto-hiding Floating Right Quick Tool Rail (Add Media, Audio Mixer, Voice Record, Camera)
        if (controlsAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 6.dp)
                    .graphicsLayer { alpha = controlsAlpha }
            ) {
                RightToolRail(
                    onAdd = { onOpenAddMediaMenu() },
                    onAudio = { vm.openAudioMixer() },
                    onRecord = { vm.openVoiceRecorder() },
                    onCamera = { vm.openCameraCapture() }
                )
            }
        }

        // Screen Controls Auto-hide Indicator Badge
        if (controlsAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .graphicsLayer { alpha = controlsAlpha }
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "⚡ Tap screen to show controls • Auto-hiding in 2s",
                    color = ApexPalette.NeonCyan,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Screen-Level Wording & Object Overlays (Positioned relative to Screen, NOT clipped by video view)
        ScreenWordingObjects(
            stickers = stickers,
            textOverlays = textOverlays,
            coverText = state.coverText,
            coverTextStyle = state.coverTextStyle,
            selectedTextOverlayId = state.selectedTextOverlayId,
            currentTimeMs = state.currentTimeMs,
            isPlaying = state.isPlaying,
            onSelectTextOverlay = { id -> vm.selectTextOverlay(id) },
            onMoveTextOverlay = { id, dx, dy ->
                val clipId = selectedClip?.id ?: state.project?.clips?.firstOrNull()?.id
                if (clipId != null) {
                    // CapCut behaviour: when the text has keyframes the
                    // drag writes a keyframe at the playhead (the text
                    // animates); otherwise the base position moves.
                    val ov = selectedClip?.textOverlays?.firstOrNull { it.id == id }
                    vm.moveTextOverlayAtPlayhead(
                        clipId, id,
                        (ov?.x ?: 0.5f) + dx, (ov?.y ?: 0.5f) + dy
                    )
                }
            },
            onDeleteTextOverlay = { id ->
                selectedClip?.let { vm.removeTextOverlay(it.id, id) }
            },
            onDuplicateTextOverlay = { id ->
                selectedClip?.let { vm.duplicateTextOverlay(it.id, id) }
            },
            onEditTextOverlay = { id ->
                vm.selectTextOverlay(id)
                vm.openTextPanel()
            },
            onSizeScaleChange = { id, scale ->
                selectedClip?.let { vm.setTextOverlaySize(it.id, id, scale) }
            },
            // Sticker canvas editing (feature/sticker-library)
            selectedStickerId = state.selectedStickerId,
            onSelectSticker = { vm.selectSticker(it) },
            onMoveSticker = { id, dx, dy, persist -> vm.moveSticker(id, dx, dy, persist) },
            onScaleSticker = { id, scale, persist -> vm.setStickerSizeScale(id, scale, persist) },
            onRotateSticker = { id, dDeg, persist -> vm.rotateSticker(id, dDeg, persist) },
            onStickerGestureEnd = { id -> vm.updateSticker(id) { it } },
            onRemoveSticker = { vm.removeSticker(it) },
            onCropSticker = { id, l, t, r, b -> vm.setStickerCrop(id, l, t, r, b) },
            onCutoutSticker = { id, shape -> vm.setStickerCutout(id, shape) },
            modifier = Modifier.fillMaxSize()
        )

        // Upcoming frames preview overlay triggered on screen tap
        UpcomingFramesPreviewOverlay(
            visible = showUpcomingPreview,
            videoUri = selectedClip?.uri,
            currentTimeMs = state.playerPositionMs,
            durationMs = state.durationMs,
            onSeekTo = { seekPlayerAndState(it) },
            onPlayPreviewSnippet = {
                scope.launch {
                    if (!state.isPlaying) vm.togglePlay()
                    delay(3000L)
                    if (state.isPlaying) vm.togglePlay()
                }
            },
            onDismiss = { showUpcomingPreview = false },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        )
    }
}

/**
 * Transport row + adjust bar + timeline quick actions + timeline track area.
 * ColumnScope extension so the weight(1f) layout behaves exactly as before
 * extraction.
 */
@Composable
fun androidx.compose.foundation.layout.ColumnScope.EditorTransportSection(
    state: EditorState,
    vm: EditorViewModel,
    seekPlayerAndState: (Long) -> Unit,
    onOpenAddMediaMenu: (ClipType) -> Unit
) {
    // Mockup transport row: undo + redo (left), BIG blue-gradient play
    // (center), keyframe diamond+ (always visible in the new layout —
    // tapping it with no clip selected is a safe no-op), fullscreen.
    // No time counter / prev-next here: the time pill lives in the preview.
    val kfClip = state.project?.clips?.firstOrNull { it.id == state.selectedClipId }
    val hasKeyframeAtPlayhead =
        kfClip?.keyframes?.keyframes?.any { kotlin.math.abs(it.timeMs - state.playerPositionMs) <= 150L } == true

    PlaybackControlBar(
        isPlaying = state.isPlaying,
        canUndo = state.canUndo,
        canRedo = state.canRedo,
        hasKeyframeAtPlayhead = hasKeyframeAtPlayhead,
        onTogglePlay = { vm.togglePlay() },
        onUndo = { vm.undo() },
        onRedo = { vm.redo() },
        onToggleKeyframe = {
            kfClip?.let { vm.toggleKeyframeAtPlayheadFor(it.id) }
        },
        onFullscreenToggle = { vm.toggleFullscreenPreview() }
    )

    // The timeline area is just the timeline: the Adjust chips row
    // and the duplicate tool rows were removed (their functions live
    // in the bottom bars — see ContextualBottomToolbar). The timeline
    // gets all the vertical space they used to take.

    TimelineTrackArea(
        state = state,
        onScrub = { targetMs ->
            val snapped = vm.findMagneticSnapPoint(targetMs)
            seekPlayerAndState(snapped)
        },
        onSelectClip = { vm.selectClip(it) },
        // Tapping empty timeline space clears the selection (hides the
        // clip action bar again).
        onDeselect = { vm.clearSelection() },
        onAddMedia = onOpenAddMediaMenu,
        onSplitClip = { clipId, atMs -> vm.splitClip(clipId, atMs) },
        onDuplicateClip = { clipId -> vm.duplicateClip(clipId) },
        onDeleteClip = { clipId -> vm.deleteClip(clipId) },
        onDeleteClips = { ids -> vm.deleteClips(ids) },
        onReorderClips = { fromIndex, toIndex -> vm.reorderClips(fromIndex, toIndex) },
        onSetClipOffset = { clipId, offsetMs -> vm.setClipTimelineOffset(clipId, offsetMs) },
        onTrimClip = { clipId, startMs, endMs -> vm.trimClip(clipId, startMs, endMs) },
        onToggleKeyframeAtPlayhead = { clipId -> vm.toggleKeyframeAtPlayheadFor(clipId) },
        onMoveKeyframe = { clipId, kfId, newTimeMs -> vm.moveKeyframe(clipId, kfId, newTimeMs) },
        onToggleTextKeyframeAtPlayhead = { clipId, overlayId ->
            vm.toggleTextKeyframeAtPlayhead(clipId, overlayId)
        },
        onMoveTextKeyframe = { clipId, overlayId, kfId, newTimeMs ->
            vm.moveTextKeyframe(clipId, overlayId, kfId, newTimeMs)
        },
        onToggleStickerKeyframeAtPlayhead = { stickerId ->
            vm.toggleStickerKeyframeAtPlayhead(stickerId)
        },
        onMoveStickerKeyframe = { stickerId, kfId, newTimeMs ->
            vm.moveStickerKeyframe(stickerId, kfId, newTimeMs)
        },
        onToggleTextMaskKeyframeAtPlayhead = { clipId, overlayId ->
            vm.toggleTextMaskKeyframeAtPlayhead(clipId, overlayId)
        },
        onMoveTextMaskKeyframe = { clipId, overlayId, kfId, newTimeMs ->
            vm.moveTextMaskKeyframe(clipId, overlayId, kfId, newTimeMs)
        },
        onSetZoom = { zoom -> vm.setTimelineZoom(zoom) },
        playerPositionFlow = vm.playerPositionFlow,
        onOpenSpeed = { vm.openSpeedPanel() },
        onOpenTrim = { vm.openTrimPanel() },
        onOpenFx = { vm.openFxPanel() },
        onOpenFilter = { vm.openFilterPanel() },
        onOpenAdjust = { vm.openAdjustmentsPanel() },
        onSelectTextOverlay = { _, overlayId -> vm.selectTextOverlay(overlayId) },
        onSelectSticker = { vm.selectSticker(it) },
        // Reset FX clears the whole lane: FX + LUT filter + adjustments.
        onClearFx = {
            vm.selectFx(null)
            vm.selectFilter(null)
            vm.resetAdjustments()
        },
        onOpenClipTransition = { fromId, toId ->
            vm.openTransitionPicker(fromId, toId)
        },
        onSelectAudioTrack = { vm.selectAudioTrack(it) },
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
    )

    // ---- BOTTOM LINE (below the timeline) ----
    // Timeline-only tools (keyframe nav + zoom). Everything else that
    // used to live here moved to the bottom bars: Overlay / Effects /
    // Adjust to the main bottom bar, Transition to the main bottom bar
    // (nearest V1 junction to playhead), Split + Curves (Keyframe
    // Studio) to the clip bar, Move <-/-> to drag-reorder, and the
    // Adjust chips row is gone (same controls in the Adjust panel).
    BottomTimelineActionRow(
        hasKeyframeAtPlayhead = hasKeyframeAtPlayhead,
        timelineZoom = state.timelineZoom,
        onToggleKeyframe = { vm.toggleKeyframeAtPlayhead() },
        onPrevKeyframe = { vm.jumpToPrevKeyframe() },
        onNextKeyframe = { vm.jumpToNextKeyframe() },
        onZoomIn = { vm.zoomInTimeline() },
        onZoomOut = { vm.zoomOutTimeline() },
        modifier = Modifier.fillMaxWidth()
    )
}

/* ================= Phase 3 overlays ================= */

/** Edit Pack bottom-sheet overlay (20 presets). */
@Composable
fun EditPackPanelOverlay(
    state: EditorState,
    vm: EditorViewModel
) {
    if (!state.editPackPanelOpen) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent) // Pro Phase 1: locked preview stays visible
            .clickable { vm.closeEditPackPanel() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
            // The ACTUAL selected clip — never a silent fallback to the
            // first clip. With no selection the panel disables its
            // presets and says so: its header promises "applies to the
            // selected clip", so it must not apply to anything else.
            val selectedClip = state.project?.clips?.firstOrNull { it.id == state.selectedClipId }
            val clipId = selectedClip?.id
            // Stable callbacks (remembered on clipId) so the panel skips
            // recomposition on every 33ms playback tick — the old inline
            // lambdas were new instances each tick, dragging the whole
            // preset grid into the playback recomposition storm.
            val onApplySpeedRamp: (String, Float, Float, Float?) -> Unit = remember(clipId) {
                { _, start, end, fixed -> clipId?.let { vm.applyEditPackSpeed(it, start, end, fixed) } }
            }
            val onApplyFreeze: (String) -> Unit = remember(clipId) {
                { clipId?.let { vm.applyEditPackFreeze(it) } }
            }
            val onApplySplitScreen: (String, SplitLayout) -> Unit = remember(clipId) {
                { _, layout -> clipId?.let { vm.applyEditPackSplit(it, layout) } }
            }
            val onApplyZoom: (String, Boolean) -> Unit = remember(clipId) {
                { _, zoomIn -> clipId?.let { vm.applyEditPackZoom(it, zoomIn) } }
            }
            val onClose: () -> Unit = remember { { vm.closeEditPackPanel() } }
            EditPackPanel(
                selectedClipName = selectedClip?.name,
                onApplySpeedRamp = onApplySpeedRamp,
                onApplyFreeze = onApplyFreeze,
                onApplySplitScreen = onApplySplitScreen,
                onApplyZoom = onApplyZoom,
                onClose = onClose
            )
        }
    }
}

/** Adjust Pack bottom-sheet overlay (30 presets + sliders). */
@Composable
fun AdjustPackPanelOverlay(
    state: EditorState,
    vm: EditorViewModel
) {
    if (!state.adjustPackPanelOpen) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clickable { vm.closeAdjustPackPanel() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
            AdjustPackPanel(
                adjustments = state.adjustments,
                onApplyPreset = { vm.applyAdjustPackPreset(it) },
                onUpdate = { vm.updateAdjustments(it) },
                onReset = { vm.resetAdjustments() },
                onClose = { vm.closeAdjustPackPanel() }
            )
        }
    }
}

/** Color Scopes bottom-sheet overlay. */
@Composable
fun ColorScopesPanelOverlay(
    state: EditorState,
    vm: EditorViewModel
) {
    if (!state.colorScopesPanelOpen) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clickable { vm.closeColorScopesPanel() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
            ColorScopesPanel(
                previewBitmap = state.gpuFilterPreviewBitmap,
                onApplyRecovery = { vm.applyAdjustPackPreset(it) },
                onClose = { vm.closeColorScopesPanel() }
            )
        }
    }
}

/** BG Remover bottom-sheet overlay. */
@Composable
fun BgRemoverPanelOverlay(
    state: EditorState,
    vm: EditorViewModel,
    onPickCustomBackground: () -> Unit
) {
    if (!state.bgRemoverPanelOpen) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clickable { vm.closeBgRemoverPanel() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
            BgRemoverPanel(
                chromaKeySettings = state.chromaKeySettings,
                onUpdateChromaKey = { vm.updateChromaKeySettings(it) },
                onPickCustomBackground = onPickCustomBackground,
                onClose = { vm.closeBgRemoverPanel() }
            )
        }
    }
}

/** Auto Clip bottom-sheet overlay. */
@Composable
fun AutoClipPanelOverlay(
    state: EditorState,
    vm: EditorViewModel,
    onOpenMusicForMood: (String) -> Unit
) {
    if (!state.autoClipPanelOpen) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clickable { vm.closeAutoClipPanel() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
            AutoClipPanel(
                clips = state.project?.clips ?: emptyList(),
                onApplyPlan = { plan ->
                    vm.applyAutoClipPlan(plan)
                    vm.closeAutoClipPanel()
                },
                onApplyPlanWithMusic = { plan ->
                    vm.applyAutoClipPlan(plan)
                    vm.closeAutoClipPanel()
                    onOpenMusicForMood(plan.style.musicMood)
                },
                onClose = { vm.closeAutoClipPanel() }
            )
        }
    }
}

/** Music Library bottom-sheet overlay. */
@Composable
fun MusicLibraryPanelOverlay(
    state: EditorState,
    vm: EditorViewModel,
    initialMood: String?,
    onAddTrack: (String, String, Long) -> Unit
) {
    if (!state.musicLibraryPanelOpen) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clickable { vm.closeMusicLibraryPanel() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
            MusicLibraryPanel(
                onAddTrack = { title, path, dur ->
                    onAddTrack(title, path, dur)
                    vm.closeMusicLibraryPanel()
                },
                onClose = { vm.closeMusicLibraryPanel() },
                initialMood = initialMood
            )
        }
    }
}

/** History & Drafts bottom-sheet overlay. */
@Composable
fun HistoryDraftsPanelOverlay(
    state: EditorState,
    vm: EditorViewModel,
    projects: List<com.apexstudio.app.domain.model.Project>
) {
    if (!state.historyDraftsPanelOpen) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clickable { vm.closeHistoryDraftsPanel() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
            HistoryDraftsPanel(
                projects = projects,
                currentTab = state.historyDraftsTab,
                onTabChange = { vm.setHistoryDraftsTab(it) },
                lastAutoSaveMs = state.lastAutoSaveMs,
                onOpenProject = { project ->
                    vm.openSavedProject(project)
                    vm.closeHistoryDraftsPanel()
                },
                onDeleteProject = { id -> vm.deleteSavedProject(id) },
                onSaveDraft = { vm.saveDraft() },
                onClose = { vm.closeHistoryDraftsPanel() }
            )
        }
    }
}

/** Background replace bottom-sheet overlay (PR-G). */
@Composable
fun BackgroundReplacePanelOverlay(
    state: EditorState,
    vm: EditorViewModel,
    onPickBackgroundImage: () -> Unit
) {
    if (!state.backgroundPanelOpen) return
    val context = LocalContext.current
    val bgUi by vm.backgroundUi.collectAsState()
    val packs by com.apexstudio.app.data.packs.download.PackManager.packsState.collectAsState()
    val modelPackState = packs.firstOrNull {
        it.pack.id == com.apexstudio.app.data.background.BackgroundReplaceEngine.PACK_ID
    }?.state
    // Refresh the pack catalog so the panel's download state is
    // current (a stale catalog would show "Download" for an
    // already-installed model).
    LaunchedEffect(Unit) {
        com.apexstudio.app.data.packs.download.PackManager.refresh(context.applicationContext)
    }
    // Exact match only (Kilo review): falling back to the first clip
    // when nothing is selected would apply the treatment to a clip
    // the user never chose. Null → the panel says "select a clip".
    val selectedClip = state.project?.clips?.firstOrNull { it.id == state.selectedClipId }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clickable { vm.closeBackgroundPanel() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
            BackgroundReplacePanel(
                clip = selectedClip,
                ui = bgUi,
                packState = modelPackState,
                onApply = { settings ->
                    val clipId = selectedClip?.id ?: return@BackgroundReplacePanel
                    vm.applyBackgroundReplace(clipId, settings)
                },
                onRestoreOriginal = {
                    val clipId = selectedClip?.id ?: return@BackgroundReplacePanel
                    vm.restoreOriginalBackground(clipId)
                },
                onCancel = { vm.cancelBackgroundReplace() },
                onDownloadModel = { vm.downloadBackgroundModel() },
                onPickImage = onPickBackgroundImage,
                onClose = { vm.closeBackgroundPanel() }
            )
        }
    }
}
