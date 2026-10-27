package com.apexstudio.app.ui.screens.editor

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.apexstudio.app.data.captions.CaptionStyles
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
import com.apexstudio.app.ui.theme.EditorPalette as ApexPalette
import com.apexstudio.app.presentation.viewmodel.generateAutoCaptions
import com.apexstudio.app.presentation.viewmodel.clearAutoCaptions
import com.apexstudio.app.presentation.viewmodel.resetCaptionUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import com.apexstudio.app.domain.model.timeline.sharedKeyframeTrackFor
import com.apexstudio.app.domain.model.timeline.sharedVideoClip
import com.apexstudio.app.domain.model.timeline.animationTransformAt
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
            val audioToTrim = state.project?.audioTracks?.firstOrNull { it.id == state.selectedAudioTrackId }
            if (audioToTrim != null) {
                AudioTrimPanel(audioToTrim, vm, onClose = { vm.closeTrimPanel() })
                return@Box
            }
            val clipToTrim = state.project?.clips?.firstOrNull { it.id == state.selectedClipId }
            val sourcePlayhead = clipToTrim?.let {
                com.apexstudio.app.domain.model.ClipTimeMapping.sourceAtProjectTime(
                    state.project?.clips.orEmpty(), it, state.playerPositionMs
                )
            } ?: 0L
            val seekSource: (Long) -> Unit = { sourceMs ->
                val clips = vm.state.value.project?.clips.orEmpty()
                val target = clips.firstOrNull { it.id == clipToTrim?.id }
                if (target != null) {
                    vm.seekTo(com.apexstudio.app.domain.model.ClipTimeMapping.projectAtSourceTime(clips, target, sourceMs))
                    // Only the V1 player may be directly sought here. Overlay
                    // playback follows project time through its own binding.
                    if (target.trackIndex == 0 && target.type == ClipType.VIDEO) exoPlayer?.seekTo(sourceMs)
                }
            }
            TrimPanel(
                clip = clipToTrim,
                currentPlayheadMs = sourcePlayhead,
                isTransformerTrimming = state.isTransformerTrimming,
                transformerTrimProgress = state.transformerTrimProgress,
                transformerTrimMessage = state.transformerTrimMessage,
                transformerTrimError = state.transformerTrimError,
                onTrimChange = { start, end ->
                    clipToTrim?.let {
                        vm.trimClip(it.id, start, end)
                        seekSource(start)
                    }
                },
                onSetStartAtPlayhead = {
                    clipToTrim?.let {
                        vm.setTrimStartAtPlayhead(it.id)
                        seekSource(sourcePlayhead)
                    }
                },
                onSetEndAtPlayhead = {
                    clipToTrim?.let {
                        vm.setTrimEndAtPlayhead(it.id)
                        seekSource(sourcePlayhead)
                    }
                },
                onResetTrim = { clipToTrim?.let { vm.resetTrim(it.id) } },
                onPreviewTrimmed = {
                    clipToTrim?.let {
                        seekSource(it.trimStartMs)
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
                onClose = { vm.closeGpuFilterPanel() },
                onApplyOneTapLook = { vm.applyGpuFilterProfilePreset(it) }
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
            .statusBarsPadding()
            .navigationBarsPadding()
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
    // Request the Play-delivered feature from the base app; the editor stays
    // alive underneath and the feature Activity returns here on Back.
    com.apexstudio.app.camerakit.CameraKitDeliveryRoute(onBack = { vm.closeLensesPanel() })
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
    val activeOverlayId = state.selectedTextOverlayId
    val selectedTextClip = state.project?.clips?.firstOrNull { owner -> owner.id == state.selectedClipId && owner.textOverlays.any { it.id == activeOverlayId } }
        ?: state.project?.clips?.firstOrNull { owner -> owner.textOverlays.any { it.id == activeOverlayId } }
        ?: state.project?.clips?.firstOrNull { it.id == state.selectedClipId }
        ?: state.project?.clips?.firstOrNull()
    val rangeOwnerClipId = activeOverlayId?.let { vm.textOverlayProjectOwnerClipId(it) }
    val textClip = rangeOwnerClipId?.let { ownerId -> state.project?.clips?.firstOrNull { it.id == ownerId } }
        ?: selectedTextClip
    val textOverlays = textClip?.textOverlays ?: emptyList()
    val activeRange = activeOverlayId?.let { vm.textOverlayProjectRange(it) }
    val textTimeline = com.apexstudio.app.data.export.ProjectCompositionTimeline.build(state.project?.clips.orEmpty())
    val panelOverlays = textOverlays.map { overlay ->
        if (overlay.id == activeOverlayId && activeRange != null)
            overlay.copy(startMs = activeRange.startMs, endMs = activeRange.endMs) else overlay
    }
    val captionUiState by vm.captionUi.collectAsState()
    val captionFillerReview by vm.captionFillerReview.collectAsState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent) // Pro Phase 1: locked preview stays visible
            .clickable { vm.closeTextPanel() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
            TextPanel(
                overlays = panelOverlays,
                initialTab = if (state.textPanelStartsOnCaptions) TextPanelTab.CAPTIONS else TextPanelTab.EDIT,
                selectedId = activeOverlayId,
                timelineDurationMs = textTimeline.durationMs,
                playheadMs = state.playerPositionMs,
                clipEnds = textTimeline.entries.map { it.clip.name to it.timelineEndMs },
                onRangeChange = if (activeOverlayId != null && activeRange != null) ({ start, end ->
                    vm.updateTextOverlayProjectRange(activeOverlayId, start, end, ownerClipId = textClip?.id)
                }) else null,
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
                onApplyDesignMotion = { preset ->
                    if (textClip != null && activeOverlayId != null) {
                        vm.applyTextDesignMotion(textClip.id, activeOverlayId, preset)
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
                        vm.generateAutoCaptions(it.id)
                    }
                },
                onClearCaptions = { textClip?.let { vm.clearAutoCaptions(it.id) } },
                onCaptionLanguageChange = { vm.setCaptionLanguage(it) },
                onCaptionTimingChange = { vm.setCaptionTimingMode(it) },
                captionStyleId = textOverlays.firstOrNull { it.presetId == "caption_auto" }?.captionStyleId
                    ?: CaptionStyles.CLASSIC_ID,
                onCaptionStyleChange = { styleId -> textClip?.let { vm.setCaptionStyle(it.id, styleId) } },
                onCancelCaptions = { vm.cancelAutoCaptions() },
                hasAutoCaptions = textOverlays.any { it.presetId == "caption_auto" },
                fillerReviewState = captionFillerReview,
                onReviewFillers = { vm.openCaptionFillerReview(it) },
                onFillerSelected = { id, selected -> vm.setCaptionFillerSelected(id, selected) },
                onApplyFillerRemoval = { vm.applyCaptionFillerRemoval() },
                onCloseFillerReview = { vm.closeCaptionFillerReview() }
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
    onImportLocalAudio: () -> Unit,
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
                onImportLocalAudio = onImportLocalAudio,
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
                onTrim = { trackId, start, end ->
                    audioState.tracks.firstOrNull { it.id == trackId }?.let { track ->
                        vm.trimAudioTrackPlacement(
                            trackId,
                            start,
                            end,
                            AudioTrackPlacement.timelineStartAfterHeadTrim(track, start)
                        )
                    }
                },
                onSplit = { trackId -> vm.splitAudioTrack(trackId, vm.playerPositionFlow.value) },
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
                onSeekTo = { targetMs -> vm.seekTo(targetMs) },
                onNormalizeTrack = { trackId -> vm.normalizeTrackLoudness(trackId) },
                onAutoDuckMusic = { musicTrackId, voiceTrackId ->
                    vm.autoDuckMusicUnderVoice(musicTrackId, voiceTrackId)
                },
                onFitMusicToProject = { trackId -> vm.fitMusicToProjectLength(trackId) },
                onRemoveSilence = { trackId -> vm.removeSilenceFromAudioTrack(trackId) }
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
    onImportLocalAudio: () -> Unit
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
                        onImportLocalAudio()
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
    val selectedSticker = state.project?.let { project ->
        (project.stickers + project.clips.flatMap { it.stickers })
            .firstOrNull { it.id == state.selectedStickerId }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent) // Pro Phase 1: locked preview stays visible
            .clickable { vm.closeStickerPanel() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
            StickerPanel(
                onAddSticker = { assetPath, cat, name -> vm.addStickerAsset(assetPath = assetPath, category = cat, name = name) },
                onClose = { vm.closeStickerPanel() },
                selectedSticker = selectedSticker,
                totalTimelineMs = state.durationMs,
                onUpdateSticker = { updated -> vm.updateStickerProperties(updated.id) { updated } },
                onDuplicate = { id -> vm.duplicateSticker(id) },
                onDelete = { id -> vm.removeSticker(id) }
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
            .statusBarsPadding()
            .navigationBarsPadding()
            .background(Color.Transparent)
            .clickable { vm.closeMaskPanel() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
            MaskPanel(
                mask = selectedClip?.mask,
                keyframeTimeMs = com.apexstudio.app.domain.model.ClipTimeMapping.outputAtProjectTime(
                    state.project?.clips.orEmpty(), selectedClip, state.playerPositionMs
                ).coerceAtLeast(0L),
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
            .statusBarsPadding()
            .navigationBarsPadding()
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
    val sharedClip = selectedClip?.let { state.project?.sharedVideoClip(it.id) }
    val track = selectedClip?.let { clip ->
        state.project?.sharedKeyframeTrackFor(clip.id, clip.keyframes)
    } ?: com.apexstudio.app.domain.model.KeyframeTrack()
    val sharedPlayheadValue = sharedClip?.animationTransformAt(state.playerPositionMs)
    val initialPropertyFilter = state.keyframePropertyFilter?.let {
        runCatching { KeyframePropertyFilter.valueOf(it) }.getOrNull()
    }
    var showManualPanel by remember(state.keyframePropertyFilter) { mutableStateOf(initialPropertyFilter != null) }
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
                    clipDurationMs = state.durationMs,
                    canAdd = selectedClip != null,
                    onAdd = { atMs ->
                        selectedClip?.let {
                            val sampled = state.project?.sharedVideoClip(it.id)?.animationTransformAt(atMs)
                                ?: track.interpolateAt(atMs)
                            vm.addKeyframe(it.id, atMs, sampled)
                        }
                    },
                    onPaste = { pin, atMs -> selectedClip?.let { vm.pasteKeyframe(it.id, pin, atMs) } },
                    atPlayheadTransform = sharedPlayheadValue,
                    selectedKeyframeTimeMs = state.keyframeSelectedTimeMs,
                    openCurveEditorOnOpen = state.keyframeCurveEditorOpen,
                    onCurveEditorDismiss = { vm.dismissKeyframeCurveEditor() },
                    onUpdate = { kf ->
                        selectedClip?.let {
                            vm.updateKeyframe(it.id, kf.id) { _ -> kf }
                        }
                    },
                    onPreviewCurve = { kf ->
                        selectedClip?.let { vm.previewKeyframeCurve(it.id, kf) }
                    },
                    onCommitCurve = { original, edited ->
                        selectedClip?.let { vm.commitKeyframeCurve(it.id, original, edited) }
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
                    initialPropertyFilter = initialPropertyFilter ?: KeyframePropertyFilter.ALL,
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
                    onClose = {
                        if (state.keyframePropertyFilter != null) vm.setKeyframePanelOpen(false)
                        else showManualPanel = false
                    }
                )
            } else {
                KeyframePresetCards(
                    onApplyPreset = { preset ->
                        vm.applyAnimationPreset(preset)
                    },
                    canApply = selectedClip != null,
                    onApplyConfiguredPreset = { preset, mode, duration, intensity ->
                        vm.applyConfiguredAnimationPreset(preset, mode, duration, intensity)
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
    onImportLocalAudio: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!show) return
    val libraryState by vm.state.collectAsStateWithLifecycle()
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().clickable(onClick = onDismiss), contentAlignment = Alignment.BottomCenter) {
    Box(Modifier.fillMaxWidth().clickable(enabled = false) {}) {
    ProjectMediaLibraryPanel(
        project = libraryState.project,
        onImportMedia = { media ->
            vm.setPendingAddAsOverlay(false)
            vm.setPendingAddAsAudio(false)
            vm.setPendingAddToLayer(null)
            val audio = media.filter { it.type == ClipType.AUDIO || it.type == ClipType.SFX }
            audio.forEach { item ->
                vm.addAudioTrack(name = item.name, uri = item.uri,
                    kind = if (item.type == ClipType.SFX) AudioTrack.Kind.SFX else AudioTrack.Kind.MUSIC,
                    sourceDurationMs = item.durationMs, startTimeMs = vm.livePlayheadMs())
            }
            val visual = media.filterNot { it.type == ClipType.AUDIO || it.type == ClipType.SFX }
            if (visual.isNotEmpty()) {
                vm.setPendingAddAsOverlay(visual.all { it.type == ClipType.OVERLAY })
                vm.onMediaPicked(visual, replace = false)
            }
        },
        onBrowseVideo = {
            vm.setPendingAddAsOverlay(false)
            vm.setPendingAddAsAudio(false)
            vm.setPendingAddToLayer(null)
            onDismiss()
            mediaPicker.pickMultipleMedia.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
        },
        onBrowsePhotos = {
            vm.setPendingAddAsOverlay(false)
            vm.setPendingAddAsAudio(false)
            vm.setPendingAddToLayer(null)
            onDismiss()
            mediaPicker.pickMultipleMedia.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        },
        onBrowseOverlay = {
            vm.setPendingAddAsOverlay(true)
            vm.setPendingAddAsAudio(false)
            onDismiss()
            mediaPicker.pickMultipleMedia.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
            )
        },
        onBrowseAudio = {
            vm.setPendingAddAsOverlay(false)
            vm.setPendingAddAsAudio(false)
            onDismiss()
            onImportLocalAudio()
        },
        onClose = onDismiss
    )
    }
    }
}

/**
 * Main video preview area. ColumnScope extension so the preview can consume
 * the remaining editor height, or the whole safe window area in fullscreen.
 */
@Composable
fun androidx.compose.foundation.layout.ColumnScope.EditorPreviewSection(
    state: EditorState,
    vm: EditorViewModel,
    exoPlayer: ExoPlayer?,
    isCoverMode: Boolean,
    isTimelineFocusMode: Boolean = false,
    isFullscreenPreview: Boolean = false,
    onFullscreenToggle: () -> Unit = {},
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
    overlayIsVideo: Map<String, Boolean> = emptyMap(),
    drawingEnabled: Boolean = false,
    drawingTool: com.apexstudio.app.domain.model.VideoDrawingTool = com.apexstudio.app.domain.model.VideoDrawingTool.BRUSH,
    drawingColorArgb: Long = 0xFFFF3B82L,
    drawingBrushWidth: Float = com.apexstudio.app.domain.model.VideoDrawingStroke.DEFAULT_WIDTH,
    fitAvailableHeight: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportState by vm.export.collectAsStateWithLifecycle()
    val compositionAspectRatio = previewCompositionAspect(exportState.settings.aspectRatio)
    val mainPreviewClip = timelinePreviewClip(state.project?.clips.orEmpty(), state.playerPositionMs)
    val sourceVideoWidth = state.videoWidth.takeIf { it > 0 }
        ?: exoPlayer?.videoSize?.width?.takeIf { it > 0 }
        ?: 0
    val sourceVideoHeight = state.videoHeight.takeIf { it > 0 }
        ?: exoPlayer?.videoSize?.height?.takeIf { it > 0 }
        ?: 0
    val sourcePixelAspect = exoPlayer?.videoSize?.pixelWidthHeightRatio
        ?.takeIf { it.isFinite() && it > 0f }
        ?: 1f
    val sourceVideoAspectRatio = mainPreviewClip?.takeIf { it.type == ClipType.VIDEO }?.let { clip ->
        previewContentAspect(
            videoWidth = sourceVideoWidth,
            videoHeight = sourceVideoHeight,
            pixelAspect = sourcePixelAspect,
            effectOutputAspect = clip.faceCropTrack?.outputAspectRatio
                ?: videoCropOutputAspect(
                    sourceVideoWidth, sourceVideoHeight, sourcePixelAspect, clip.videoCrop
                )
        )
    }
    val previewStageAspectRatio = editorPreviewStageAspectRatio(sourceVideoAspectRatio, compositionAspectRatio)
    val portraitScale = portraitPreviewScale(sourceVideoAspectRatio)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            // Keep the preview/player subtree composed in focus mode while
            // yielding essentially all available height to the timeline.
            .then(when {
                isFullscreenPreview || fitAvailableHeight -> Modifier.weight(1f)
                else -> Modifier.weight(editorPreviewLayoutWeight(isTimelineFocusMode))
            })
            .background(ApexPalette.BgBase)
            .semantics { contentDescription = "Editor preview stage" },
        contentAlignment = Alignment.TopCenter
    ) {
        val stageSize = if (isFullscreenPreview) PreviewStageSize(maxWidth.value, maxHeight.value)
            else fittedPreviewStageSize(maxWidth.value, maxHeight.value, previewStageAspectRatio)
                ?: PreviewStageSize(maxWidth.value, maxHeight.value)
        Box(
            Modifier
                .size(stageSize.width.dp, stageSize.height.dp)
                .clip(RoundedCornerShape(EditorLayoutMetrics.PreviewCornerRadius))
                .background(Color.Black)
                .then(if (isFullscreenPreview || isTimelineFocusMode) Modifier else
                    Modifier.border(
                        1.dp,
                        androidx.compose.ui.graphics.Brush.linearGradient(listOf(
                            Color(0xFF3B6978), Color(0xFF202C42), Color(0xFF60517C)
                        )),
                        RoundedCornerShape(EditorLayoutMetrics.PreviewCornerRadius)
                    )),
            contentAlignment = Alignment.TopCenter
        ) {
        // Keep player, overlays, and gesture coordinates on the same source-ratio canvas.
        PreviewContentCanvas(
            mediaAspectRatio = previewStageAspectRatio,
            isCoverMode = false,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = portraitScale
                scaleY = portraitScale
            },
            frameModifier = Modifier.background(Color.Black)
        ) {
        // Selecting a V2+ overlay must not make its media/keyframes the
        // background preview source; PipOverlayCanvas owns that layer.
        val selectedClip = resolveEditorSelectionClip(
            state.project?.clips.orEmpty(), state.selectedClipId
        )
        val animatedTransform = remember(mainPreviewClip, state.playerPositionMs) {
            mainPreviewClip?.keyframes?.interpolateAt(state.playerPositionMs)
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
            activeOverlayVideoClip?.trimStartMs, activeOverlayVideoClip?.trimEndMs,
            activeOverlayVideoClip?.speedPoints, activeOverlayVideoClip?.timelineOffsetMs
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
            val relOutput = (state.playerPositionMs - clip.timelineOffsetMs).coerceAtLeast(0L)
            player.playbackParameters = androidx.media3.common.PlaybackParameters(
                clip.overlaySpeedAtOutputMs(relOutput)
            )
            player.seekTo(clip.overlaySourceOffsetMs(relOutput))
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
        LaunchedEffect(
            overlayPlayer, state.playerPositionMs, activeOverlayVideoClip?.id,
            activeOverlayVideoClip?.speedPoints, state.isPlaying
        ) {
            val player = overlayPlayer ?: return@LaunchedEffect
            val clip = activeOverlayVideoClip ?: return@LaunchedEffect
            if (player.playbackState != androidx.media3.common.Player.STATE_READY) return@LaunchedEffect
            val relOutput = (state.playerPositionMs - clip.timelineOffsetMs).coerceAtLeast(0L)
            val relSource = clip.overlaySourceOffsetMs(relOutput)
            val desiredSpeed = clip.overlaySpeedAtOutputMs(relOutput)
            if (kotlin.math.abs(player.playbackParameters.speed - desiredSpeed) > 0.02f) {
                player.playbackParameters = androidx.media3.common.PlaybackParameters(desiredSpeed)
            }
            val deadband = if (state.isPlaying) 450L else 120L
            if (kotlin.math.abs(player.currentPosition - relSource) > deadband) {
                player.seekTo(relSource)
            }
        }

        val stickers = (state.project?.stickers ?: emptyList()) + (selectedClip?.stickers ?: emptyList())
        val textOwners = previewTextOwners(mainPreviewClip, overlayClips, state.playerPositionMs)
        val faceCropConfig = com.apexstudio.app.data.effect.FaceCropRenderConfig.forMainVideo(mainPreviewClip)
        val workspaceEffectIntensityProvider = remember(state.project, mainPreviewClip?.id) {
            val clip = mainPreviewClip
            val typed = clip?.let { state.project?.sharedVideoClip(it.id) }
                ?.takeIf { it.effectIntensityKeyframes.isNotEmpty() }
            if (clip == null || typed == null) null else { presentationTimeUs: Long ->
                val projectTimeMs = com.apexstudio.app.domain.model.ClipTimeMapping.projectAtSourceTime(
                    state.project?.clips.orEmpty(), clip, presentationTimeUs / 1_000L
                )
                typed.effectIntensityAt(projectTimeMs)
            }
        }

        // Layer names belong on their timeline clips, not in a HUD over media.

        VideoPreviewArea(
            selectionFileLabel = null,
            showFrame = false,
            exoPlayer = exoPlayer,
            videoWidth = sourceVideoWidth,
            videoHeight = sourceVideoHeight,
            drawingLayers = state.project?.videoDrawings.orEmpty(),
            drawingClipId = mainPreviewClip?.id,
            drawingEnabled = drawingEnabled,
            drawingTool = drawingTool,
            drawingColorArgb = drawingColorArgb,
            drawingBrushWidth = drawingBrushWidth,
            onDrawingLayerChange = vm::updateVideoDrawingLayer,
            videoCrop = mainPreviewClip?.videoCrop,
            faceCropConfig = faceCropConfig,
            projectVideoEffectId = mainPreviewClip?.takeIf { it.type == ClipType.VIDEO }?.projectVideoEffectId,
            workspaceEffects = mainPreviewClip?.workspaceEffects.orEmpty(),
            workspaceEffectIntensityProvider = workspaceEffectIntensityProvider,
            onEffectPreviewFailure = { vm.setPlayerError(it); vm.setPlaying(false) },
            compositionAspectRatio = previewStageAspectRatio,
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
            gpuFilterConfig = mainPreviewClip?.gpuFilterConfig ?: com.apexstudio.app.data.filter.GpuFilterConfig(),
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
            currentTimeMs = mainPreviewClip?.let { clip ->
                com.apexstudio.app.domain.model.ClipTimeMapping.outputAtProjectTime(
                    state.project?.clips.orEmpty(), clip, state.playerPositionMs
                ).coerceAtLeast(0L)
            } ?: 0L,
            durationMs = state.durationMs,
            // Shape mask (Mask tool) on the selected main clip, with
            // drag-to-move/resize while the mask panel is open.
            // Overlay (V2 PiP) clips preview their mask as a hard clip on
            // the PiP box itself, so the main-frame scrim is restricted to
            // non-overlay clips.
            mask = mainPreviewClip?.mask,
            maskEditMode = state.maskPanelOpen && currentSelectedClip?.id == mainPreviewClip?.id,
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
            photoClip = mainPreviewClip?.takeIf { it.type == ClipType.IMAGE },
            photoCropActive = state.photoEditPanelOpen &&
                    state.photoEditTab == com.apexstudio.app.presentation.state.PhotoEditTab.CROP,
            onPhotoCropRectChange = { rect ->
                mainPreviewClip?.id?.let { vm.setPhotoCrop(it, rect) }
            },
            onTapVideo = {
                vm.togglePlay()
            },
            onRetryLoad = {
                exoPlayer?.let { player ->
                    vm.setPlayerError(null)
                    try {
                        val clip = mainPreviewClip ?: error("No project video is available")
                        val sourceUri = MediaUriResolver.resolvePlayableUri(context, clip.uri)
                        player.setMediaItem(MediaItem.Builder().setUri(sourceUri).setMediaId(clip.id).build())
                        player.prepare()
                        player.seekTo(com.apexstudio.app.domain.model.ClipTimeMapping.sourceAtProjectTime(
                            state.project?.clips.orEmpty(), clip, vm.playerPositionFlow.value))
                        player.playWhenReady = state.isPlaying
                    } catch (e: Exception) {
                        vm.setPlayerError("Error reloading video: ${e.message}")
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // All editing actions live in the horizontally scrolling bottom dock.
        // Floating vertical rails intercepted PiP drag/resize touches and covered
        // the source picture on narrow phones. Keep the preview gesture-only.

        // Screen-Level Wording & Object Overlays (Positioned relative to Screen, NOT clipped by video view)
        PreviewContentCanvas(
            mediaAspectRatio = previewStageAspectRatio,
            isCoverMode = false,
            modifier = Modifier.fillMaxSize()
        ) {
            // Main and PiP titles retain their own clip-relative clocks. Selecting
            // PiP must not replace the main video's template text layer.
            val wordingOwners: List<MediaClip?> = textOwners.ifEmpty { listOf(null) }
            wordingOwners.forEachIndexed { index, textOwner ->
            ScreenWordingObjects(
                stickers = if (index == 0) stickers else emptyList(),
                textOverlays = textOwner?.textOverlays.orEmpty(),
                coverText = if (index == 0) state.coverText else "",
                coverTextStyle = state.coverTextStyle,
                selectedTextOverlayId = state.selectedTextOverlayId,
                currentTimeMs = state.currentTimeMs,
                captionTimeMs = textOwner?.let { clip ->
                    com.apexstudio.app.domain.model.ClipTimeMapping.outputAtProjectTime(
                        state.project?.clips.orEmpty(), clip, state.currentTimeMs)
                } ?: state.currentTimeMs,
                isPlaying = state.isPlaying,
                onSelectTextOverlay = { id -> vm.selectTextOverlay(id) },
                onMoveTextOverlay = { id, dx, dy ->
                    val clipId = textOwner?.id
                    if (clipId != null) {
                        // Existing normalized overlay positions are preserved. Gesture
                        // deltas now use the source/output composition box as their unit.
                        val ov = textOwner?.textOverlays?.firstOrNull { it.id == id }
                        vm.moveTextOverlayAtPlayhead(
                            clipId, id,
                            (ov?.x ?: 0.5f) + dx, (ov?.y ?: 0.5f) + dy
                        )
                    }
                },
                onDeleteTextOverlay = { id -> textOwner?.let { vm.removeTextOverlay(it.id, id) } },
                onDuplicateTextOverlay = { id -> textOwner?.let { vm.duplicateTextOverlay(it.id, id) } },
                onEditTextOverlay = { id ->
                    vm.selectTextOverlay(id)
                    vm.openTextPanel()
                },
                onSizeScaleChange = { id, scale -> textOwner?.let { vm.setTextOverlaySize(it.id, id, scale) } },
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
            }
            SignaturePreviewOverlay(
                signature = state.project?.signature,
                modifier = Modifier.fillMaxSize()
            )
        }

        val activeTrayStickers = stickers.filter { it.isActiveAt(state.playerPositionMs) }
        var stickerTrayDismissed by remember(state.project?.id, state.selectedStickerId, stickers.map { it.id }) {
            mutableStateOf(false)
        }
        if (!isFullscreenPreview && !stickerTrayDismissed) {
            ProjectStickerQuickTray(
                stickers = activeTrayStickers,
                selectedStickerId = state.selectedStickerId,
                onSelectSticker = { vm.selectSticker(it) },
                onClose = { stickerTrayDismissed = true },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp)
            )
        }

        if (isFullscreenPreview) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.78f))
                    .clickable(onClick = onFullscreenToggle, role = Role.Button)
                    .semantics { contentDescription = "Exit fullscreen preview" }
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Text(
                    text = "Exit fullscreen",
                    color = ApexPalette.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        }
        }
    }
}

/**
* Transport row + timeline track area.
 * ColumnScope extension so the weight(1f) layout behaves exactly as before
 * extraction.
 */
@Composable
fun androidx.compose.foundation.layout.ColumnScope.EditorTransportSection(
    state: EditorState,
    vm: EditorViewModel,
    seekPlayerAndState: (Long) -> Unit,
    onOpenAddMediaMenu: (ClipType) -> Unit,
    isTimelineFocusMode: Boolean = false,
    onTimelineFocusModeChange: (Boolean) -> Unit = {},
    compactLandscape: Boolean = false
) {
    val context = LocalContext.current
    // Compact transport frees vertical room for the layered timeline.
    // Existing edit and playback handlers remain the source of truth.
    val kfClip = selectedKeyframeVideo(state)
    val hasKeyframeAtPlayhead =
        kfClip?.keyframes?.keyframes?.any { kotlin.math.abs(it.timeMs - state.playerPositionMs) <= 150L } == true

    if (!compactLandscape) {
    PlaybackControlBar(
        isPlaying = state.isPlaying,
        canUndo = state.canUndo,
        canRedo = state.canRedo,
        hasKeyframeAtPlayhead = hasKeyframeAtPlayhead,
        canEditKeyframe = kfClip != null,
        onTogglePlay = { vm.togglePlay() },
        onUndo = { vm.undo() },
        onRedo = { vm.redo() },
        onToggleKeyframe = {
            kfClip?.let { vm.toggleKeyframeAtPlayheadFor(it.id) }
        },
        onFullscreenToggle = { vm.toggleFullscreenPreview() },
        currentTimeMs = state.playerPositionMs,
        durationMs = state.durationMs,
        canNavigate = state.durationMs > 0L,
        onPrevious = {
            seekPlayerAndState(editorClipBoundary(state.project?.clips.orEmpty(), vm.playerPositionFlow.value, state.durationMs, false))
        },
        onNext = {
            seekPlayerAndState(editorClipBoundary(state.project?.clips.orEmpty(), vm.playerPositionFlow.value, state.durationMs, true))
        },
        showInlineKeyframe = false
    )
    }

    // No persistent property strip above the timeline. Keyframe/Animation stay
    // in the lower selected-clip tools; the timeline header has one contextual
    // keyframe action, without duplicating the full property inspector.
    CenteredTimelineWorkspace(
        layoutWeight = timelineTrackLayoutWeight(isTimelineFocusMode)
    ) {
    Row(Modifier.fillMaxWidth().height(if (compactLandscape) 44.dp else 26.dp), verticalAlignment = Alignment.CenterVertically) {
        if (compactLandscape) {
            LandscapeTimelineControls(state, vm, seekPlayerAndState, Modifier.weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.height(26.dp).clickable(role = Role.Button) { onTimelineFocusModeChange(!isTimelineFocusMode) }
                .semantics {
                contentDescription = if (isTimelineFocusMode) "Restore preview and normal timeline layout" else "Expand timeline to full height"
                }.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (isTimelineFocusMode) "Show preview" else "Timeline", fontSize = 11.sp, color = ApexPalette.TextSecondary)
            Icon(if (isTimelineFocusMode) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null,
                tint = ApexPalette.TextSecondary, modifier = Modifier.padding(start = 2.dp).size(14.dp))
        }
    }

    // Reference mockup: thin horizontal scrub line above the timeline —
    // red played portion on a dark track. Purely a position mirror.
    run {
        val totalMs = state.durationMs.coerceAtLeast(1L)
        val frac = (state.playerPositionMs.toFloat() / totalMs).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Color(0xFF2A2A2A))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(frac)
                    .height(2.dp)
                    .background(Color(0xFFFF3B30))
            )
        }
    }


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
        onSelectClip = { id ->
            vm.selectClip(id)
            val clips = state.project?.clips.orEmpty()
            val clip = clips.firstOrNull { it.id == id && it.trackIndex == 0 && (it.type == ClipType.VIDEO || it.type == ClipType.IMAGE) }
            if (clip != null && timelinePreviewClip(clips, vm.playerPositionFlow.value)?.id != clip.id) {
                seekPlayerAndState(com.apexstudio.app.domain.model.SpeedCurveMath.timelineStartMsForClip(clips, clip))
            }
        },
        // Tapping empty timeline space clears the selection (hides the
        // clip action bar again).
        onDeselect = { vm.clearSelection() },
        onAddMedia = onOpenAddMediaMenu,
        onSplitClip = { clipId, atMs -> vm.splitClip(clipId, atMs) },
        onDuplicateClip = { clipId -> vm.duplicateClip(clipId) },
        onDeleteClip = { clipId -> vm.deleteClip(clipId) },
        onDeleteClips = { ids -> vm.deleteClips(ids) },
        onDuplicateSelectedClips = { vm.duplicateSelectedClips() },
        onGroupSelectedClips = { vm.groupSelectedClips() },
        onAutoCutSelected = {
            if (!vm.autoCutSelectedClips()) {
                android.widget.Toast.makeText(
                    context,
                    "AutoCut couldn't fit the selected clips to beats after the current sequence.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        },
        onApplySpeedToSelectedClips = { speed -> vm.applySpeedToSelectedClips(speed) },
        onApplyFilterToSelectedClips = { config -> vm.applyFilterToSelectedClips(config) },
        onApplyLutToSelectedClips = { profileId, intensity -> vm.applyLutToSelectedClips(profileId, intensity) },
        onToggleMultiSelect = { vm.toggleMultiSelectMode() },
        onToggleSelectedClip = { id -> vm.toggleSelectedClip(id) },
        onClearMultiSelection = { vm.clearMultiSelection() },
        onReorderClips = { orderedIds -> vm.reorderMainVideoClips(orderedIds) },
        onSetClipOffset = { clipId, offsetMs -> vm.setClipTimelineOffset(clipId, offsetMs) },
        onTrimClip = { clipId, startMs, endMs -> vm.trimClip(clipId, startMs, endMs) },
        onToggleKeyframeAtPlayhead = { clipId -> vm.toggleKeyframeAtPlayheadFor(clipId) },
        onMoveKeyframe = { clipId, kfId, newTimeMs -> vm.moveKeyframe(clipId, kfId, newTimeMs) },
        onSelectKeyframe = { clipId, timeMs ->
            seekPlayerAndState(timeMs)
            vm.openKeyframeCurveEditor(clipId, timeMs)
        },
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
        onSelectTextOverlay = { ownerId, overlayId ->
            vm.selectClip(ownerId)
            vm.selectTextOverlay(overlayId)
            vm.textOverlayProjectRange(overlayId)?.let { range ->
                if (state.playerPositionMs !in range.startMs until range.endMs) seekPlayerAndState(range.startMs)
            }
        },
        onSetTextOverlayRange = { ownerId, overlayId, start, end ->
            vm.updateTextOverlayProjectRange(overlayId, start, end, ownerClipId = ownerId)
        },
        // Editing-assist modes. The logic already lived in
        // EditorAudioController but only landscape exposed one of the three;
        // the timeline overflow menu now reaches all of them.
        onToggleMagneticSnapping = { vm.toggleMagneticSnapping() },
        onToggleSnapToBeat = { vm.toggleSnapToBeat() },
        onToggleRippleEdit = { vm.toggleRippleEdit() },
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
        onMoveAudioTrack = { id, start -> vm.moveAudioTrack(id, start) },
        onTrimAudioTrack = { id, sourceStart, sourceEnd, timelineStart ->
            vm.trimAudioTrackPlacement(id, sourceStart, sourceEnd, timelineStart)
        },
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
    )
    }

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

/** Built-in procedural SFX browser bottom-sheet overlay. */
@Composable
fun SfxLibraryPanelOverlay(
    state: EditorState,
    vm: EditorViewModel,
    onAddEffect: (com.apexstudio.app.data.audio.sfx.SfxEffect, String, Long, Float) -> Unit
) {
    if (!state.sfxLibraryPanelOpen) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clickable { vm.closeSfxLibraryPanel() },
        contentAlignment = Alignment.BottomCenter
    ) {
        // Consume taps inside the sheet so they do not reach the dismissing backdrop.
        Box(modifier = Modifier.fillMaxWidth().clickable { }) {
            SfxLibraryPanel(
                onAddEffect = { effect, path, durationMs, intensity ->
                    onAddEffect(effect, path, durationMs, intensity)
                    vm.closeSfxLibraryPanel()
                },
                onClose = { vm.closeSfxLibraryPanel() }
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

/** Drawing-tool sheet host; state and persistence remain owned by EditorViewModel. */
@Composable
fun VideoDrawingToolsOverlay(
    isOpen: Boolean,
    tool: com.apexstudio.app.domain.model.VideoDrawingTool,
    colorArgb: Long,
    brushWidth: Float,
    strokeCount: Int,
    onToolChange: (com.apexstudio.app.domain.model.VideoDrawingTool) -> Unit,
    onColorChange: (Long) -> Unit,
    onBrushWidthChange: (Float) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit
) {
    if (!isOpen) return
    VideoDrawingControlsSheet(
        tool = tool,
        colorArgb = colorArgb,
        brushWidth = brushWidth,
        strokeCount = strokeCount,
        onToolChange = onToolChange,
        onColorChange = onColorChange,
        onBrushWidthChange = onBrushWidthChange,
        onUndo = onUndo,
        onClear = onClear,
        onClose = onClose
    )
}
