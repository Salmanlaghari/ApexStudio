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
import androidx.compose.foundation.layout.padding
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
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.*
import com.apexstudio.app.ui.theme.ApexPalette
import com.apexstudio.app.presentation.viewmodel.captionUi
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
                captionUiState = captionUiState,
                onAutoCaptions = {
                    textClip?.let {
                        vm.resetCaptionUi()
                        vm.generateAutoCaptions(it.id)
                    }
                },
                onClearCaptions = { textClip?.let { vm.clearAutoCaptions(it.id) } },
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
                onVolume = { trackId, vol -> vm.setAudioTrackVolume(trackId, vol) },
                onMute = { trackId -> vm.toggleAudioTrackMute(trackId) },
                onSolo = { trackId -> vm.toggleAudioTrackSolo(trackId) },
                onTrim = { trackId, start, end -> vm.setAudioTrackTrim(trackId, start, end) },
                onFadeIn = { trackId, ms -> vm.setAudioTrackFadeIn(trackId, ms) },
                onFadeOut = { trackId, ms -> vm.setAudioTrackFadeOut(trackId, ms) },
                onClose = { vm.closeAudioMixer() },
                audioSessionId = audioSessionId
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

    if (state.royaltyMusicDialogOpen) {
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
    val selectedClip = state.project?.clips?.firstOrNull { it.id == state.selectedClipId }
        ?: state.project?.clips?.firstOrNull()
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
    overlayPlayer: ExoPlayer? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        val selectedClip = state.project?.clips?.firstOrNull { it.id == state.selectedClipId }
            ?: state.project?.clips?.firstOrNull()
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
        // The video overlay clip the shared muted player is bound to:
        // the first VIDEO overlay active at the playhead.
        val activeOverlayVideoClip = overlayClips.firstOrNull {
            it.type == ClipType.VIDEO && it.isOverlayActiveAt(state.playerPositionMs)
        }

        // Bind the overlay player to the active video overlay clip
        // (trimmed range, looping, muted). Re-binds when the active
        // clip changes (including entering/leaving its window).
        LaunchedEffect(overlayPlayer, activeOverlayVideoClip?.id) {
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
            player.volume = 0f
            player.prepare()
            player.pause()
            val rel = (state.playerPositionMs - clip.timelineOffsetMs).coerceAtLeast(0L)
            player.seekTo(rel)
            if (state.isPlaying) player.play()
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

        VideoPreviewArea(
            exoPlayer = exoPlayer,
            chromaKeySettings = state.chromaKeySettings,
            overlayClips = overlayClips,
            selectedOverlayClipId = state.selectedOverlayClipId,
            overlayPlayer = overlayPlayer,
            overlayPlayerClipId = activeOverlayVideoClip?.id,
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
            currentTimeMs = state.playerPositionMs,
            durationMs = state.durationMs,
            // Photo clip (PR F): rendered by PhotoClipPreview with
            // its edits; null for video clips.
            photoClip = currentSelectedClip?.takeIf { it.type == ClipType.IMAGE },
            photoCropActive = state.photoEditPanelOpen &&
                    state.photoEditTab == com.apexstudio.app.presentation.state.PhotoEditTab.CROP,
            onPhotoCropRectChange = { rect ->
                currentSelectedClip?.id?.let { vm.setPhotoCrop(it, rect) }
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
                    vm.updateTextOverlay(clipId, id) { it.copy(x = it.x + dx, y = it.y + dy) }
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

    // Elevate all 14 Adjust features to the top right below the video player preview
    TopAdjustBar(
        adjustments = state.adjustments,
        onOpenAdjustPanel = { vm.openAdjustmentsPanel() },
        onUpdate = { vm.updateAdjustments(it) },
        onResetAll = { vm.resetAllAdjustments() },
        modifier = Modifier.fillMaxWidth()
    )

    val activeClip = state.project?.clips?.firstOrNull { it.id == state.selectedClipId } ?: state.project?.clips?.firstOrNull()
    val clipList = state.project?.clips ?: emptyList()
    val currentClipIdx = clipList.indexOfFirst { it.id == activeClip?.id }

    TimelineQuickActionBar(
        hasKeyframeAtPlayhead = hasKeyframeAtPlayhead,
        timelineZoom = state.timelineZoom,
        canSplit = activeClip != null,
        canMoveLeft = currentClipIdx > 0,
        canMoveRight = currentClipIdx in 0 until (clipList.size - 1),
        onToggleKeyframe = { vm.toggleKeyframeAtPlayhead() },
        onPrevKeyframe = { vm.jumpToPrevKeyframe() },
        onNextKeyframe = { vm.jumpToNextKeyframe() },
        onSplit = {
            activeClip?.let { vm.splitClip(it.id, state.playerPositionMs) }
        },
        onMoveLeft = {
            activeClip?.let { vm.moveClipLeft(it.id) }
        },
        onMoveRight = {
            activeClip?.let { vm.moveClipRight(it.id) }
        },
        onZoomIn = { vm.zoomInTimeline() },
        onZoomOut = { vm.zoomOutTimeline() },
        onOpenKeyframes = { vm.setKeyframePanelOpen(true) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )

    TimelineTrackArea(
        state = state,
        onScrub = { targetMs ->
            val snapped = vm.findMagneticSnapPoint(targetMs)
            seekPlayerAndState(snapped)
        },
        onToggleSnapToBeat = { vm.toggleSnapToBeat() },
        onToggleMagneticSnapping = { vm.toggleMagneticSnapping() },
        onToggleRippleEdit = { vm.toggleRippleEdit() },
        onSelectClip = { vm.selectClip(it) },
        onSelectFx = { fxId -> vm.selectFx(fxId, intensity = 0.85f) },
        onSelectFilter = { filterId -> vm.selectFilter(filterId, intensity = 0.85f) },
        onAddMedia = onOpenAddMediaMenu,
        onSplitClip = { clipId, atMs -> vm.splitClip(clipId, atMs) },
        onDuplicateClip = { clipId -> vm.duplicateClip(clipId) },
        onDeleteClip = { clipId -> vm.deleteClip(clipId) },
        onDeleteClips = { ids -> vm.deleteClips(ids) },
        onMoveClipLeft = { clipId -> vm.moveClipLeft(clipId) },
        onMoveClipRight = { clipId -> vm.moveClipRight(clipId) },
        onReorderClips = { fromIndex, toIndex -> vm.reorderClips(fromIndex, toIndex) },
        onShiftClipOffset = { clipId, deltaMs -> vm.shiftClipTimelineOffset(clipId, deltaMs) },
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
        onZoomIn = { vm.zoomInTimeline() },
        onZoomOut = { vm.zoomOutTimeline() },
        onResetZoom = { vm.setTimelineZoom(1.0f) },
        onSetZoom = { zoom -> vm.setTimelineZoom(zoom) },
        // Multi-layer video tracks (V1..V10) + 60fps playhead.
        onToggleLayerVisibility = { layer -> vm.toggleVideoLayerVisibility(layer) },
        onToggleLayerLock = { layer -> vm.toggleVideoLayerLock(layer) },
        onDeleteLayer = { layer -> vm.deleteVideoLayer(layer) },
        onAddLayer = { vm.addEmptyVideoLayer() },
        onMoveClipToLayer = { clipId, layer -> vm.moveClipToVideoLayer(clipId, layer) },
        onAddClipToLayer = { layer ->
            vm.setPendingAddToLayer(layer)
            onOpenAddMediaMenu(ClipType.OVERLAY)
        },
        playerPositionFlow = vm.playerPositionFlow,
        onOpenSpeed = { vm.openSpeedPanel() },
        onOpenAudio = { vm.openAudioMixer() },
        onOpenTrim = { vm.openTrimPanel() },
        onOpenText = { vm.openTextPanel() },
        onOpenStickers = { vm.openStickerPanel() },
        onOpenFx = { vm.openFxPanel() },
        onOpenVoice = { vm.openVoiceRecorder() },
        onOpenAnimation = { vm.setKeyframePanelOpen(true) },
        onOpenTransition = {
            val clips = state.project?.clips ?: emptyList()
            if (clips.size >= 2) {
                val selIndex = clips.indexOfFirst { it.id == state.selectedClipId }
                val fromIdx = if (selIndex in 0 until clips.size - 1) selIndex else 0
                vm.openTransitionPicker(clips[fromIdx].id, clips[fromIdx + 1].id)
            } else {
                vm.openTransmissionTemplatesPanel()
            }
        },
        onOpenClipTransition = { fromId, toId ->
            vm.openTransitionPicker(fromId, toId)
        },
        onOpenChromaKey = { vm.openChromaKeyPanel() },
        onOpenArFilters = { vm.openArFilterPanel() },
        onOpenRoyaltyMusic = { vm.openRoyaltyMusicDialog() },
        onSelectAudioTrack = { vm.selectAudioTrack(it) },
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
    )
}
