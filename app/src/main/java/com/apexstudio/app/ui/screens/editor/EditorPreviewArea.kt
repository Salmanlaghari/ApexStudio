package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.VideoCropSettings
import com.apexstudio.app.data.effect.VideoCropGlEffect
import com.apexstudio.app.data.effect.FaceCropGlEffect
import com.apexstudio.app.data.effect.FaceCropRenderConfig
import com.apexstudio.app.data.filter.toVideoAdjustments
import com.apexstudio.app.domain.model.StickerOverlay
import com.apexstudio.app.domain.model.VideoDrawingLayer
import com.apexstudio.app.domain.model.VideoDrawingTool
import com.apexstudio.app.domain.model.VideoDrawingStroke
import com.apexstudio.app.ui.theme.EditorPalette as ApexPalette
import com.apexstudio.app.util.TimeFormat

// === 2. VIDEO PREVIEW AREA ===
internal const val DEFAULT_PREVIEW_COVER_MODE = false
internal const val LOCKED_EDITOR_PREVIEW_ASPECT = 16f / 9f

@Composable
fun VideoPreviewArea(
    exoPlayer: ExoPlayer? = null,
    chromaKeySettings: com.apexstudio.app.domain.model.ChromaKeySettings = com.apexstudio.app.domain.model.ChromaKeySettings(),
    // PiP overlay clips (V2 track). Rendered by the interactive
    // PipOverlayCanvas: tap to select, drag to move, pinch to
    // scale/rotate, with delete + resize handles on the selection.
    // [overlayPlayer] plays the bound video overlay live (muted);
    // image overlays render via Coil.
    overlayClips: List<MediaClip> = emptyList(),
    selectedOverlayClipId: String? = null,
    overlayPlayer: ExoPlayer? = null,
    overlayPlayerClipId: String? = null,
    // Per-clip probe result: is this overlay's media a video?
    // (OverlayMediaProbe — the clip type is always OVERLAY, so the
    // media itself answers. Missing entry = probe still running.)
    overlayIsVideo: Map<String, Boolean> = emptyMap(),
    onSelectOverlayClip: ((String?) -> Unit)? = null,
    onMoveOverlayClip: ((id: String, dx: Float, dy: Float, persist: Boolean) -> Unit)? = null,
    onScaleOverlayClip: ((id: String, scale: Float, persist: Boolean) -> Unit)? = null,
    onRotateOverlayClip: ((id: String, deltaDeg: Float, persist: Boolean) -> Unit)? = null,
    onOverlayGestureEnd: ((String) -> Unit)? = null,
    onRemoveOverlayClip: ((String) -> Unit)? = null,
    // Keyframe upsert for canvas gestures while the selected overlay
    // has keyframes (gesture edits the keyframe, not pip base values).
    onUpsertOverlayKeyframe: ((id: String, timeMs: Long, x: Float, y: Float, scale: Float, rotationDeg: Float, opacity: Float, persist: Boolean) -> Unit)? = null,
    isCoverMode: Boolean = DEFAULT_PREVIEW_COVER_MODE,
    adjustments: com.apexstudio.app.domain.model.VideoAdjustments = com.apexstudio.app.domain.model.VideoAdjustments(),
    activeFilterId: String? = null,
    filterIntensity: Float = 1.0f,
    gpuFilterConfig: com.apexstudio.app.data.filter.GpuFilterConfig = com.apexstudio.app.data.filter.GpuFilterConfig(),
    gpuFilterCompareMode: Boolean = false,
    gpuFilterSplitPosition: Float = 0.5f,
    lutCompareMode: Boolean = false,
    lutCompareSplitPosition: Float = 0.5f,
    activeArFilterId: String? = null,
    arFilterIntensity: Float = 0.85f,
    arFilterCustomText: String = "",
    playerError: String? = null,
    stickers: List<StickerOverlay> = emptyList(),
    activeFxId: String? = null,
    fxIntensity: Float = 0f,
    fxSpeed: Float = 1f,
    isPlaying: Boolean = false,
    animatedTransform: com.apexstudio.app.domain.model.AnimatedTransform = com.apexstudio.app.domain.model.AnimatedTransform.Identity,
    textOverlays: List<com.apexstudio.app.domain.model.TextOverlay> = emptyList(),
    selectedTextOverlayId: String? = null,
    currentTimeMs: Long = 0L,
    durationMs: Long = 0L,
    onSelectTextOverlay: ((String) -> Unit)? = null,
    onMoveTextOverlay: ((id: String, dx: Float, dy: Float) -> Unit)? = null,
    onDeleteTextOverlay: ((String) -> Unit)? = null,
    onDuplicateTextOverlay: ((String) -> Unit)? = null,
    onEditTextOverlay: ((String) -> Unit)? = null,
    onSizeScaleChange: ((String, Float) -> Unit)? = null,
    onRetryLoad: (() -> Unit)? = null,
    onTapVideo: () -> Unit = {},
    // Small filename chip (bottom-left inside the preview): the
    // selected clip/track name, moved here from the clip bar, where
    // it crowded the clip bar's actions. Null = nothing selected.
    // Hidden while playing (same rule as the clip bar).
    selectionFileLabel: String? = null,
    // Shape mask (Mask tool) on the main video clip. Rendered as a
    // scrim-with-hole overlay so the user sees the kept region;
    // [maskEditMode] enables drag-to-move/resize directly on the preview.
    mask: com.apexstudio.app.domain.model.VideoMask? = null,
    maskEditMode: Boolean = false,
    onMaskCenterLive: (x: Float, y: Float) -> Unit = { _, _ -> },
    onMaskCenterFinished: (x: Float, y: Float) -> Unit = { _, _ -> },
    onMaskSizeLive: (sx: Float, sy: Float) -> Unit = { _, _ -> },
    onMaskSizeFinished: (sx: Float, sy: Float) -> Unit = { _, _ -> },
    // CapCut-style pinch-to-zoom on the V1 video/photo layer: a
    // two-finger pinch multiplies the selected clip's keyframed scale
    // at the playhead (auto-keyframe; no-op with no keyframes yet).
    // Attached to the INNER video box (below), so gestures starting
    // on an overlay/sticker are consumed by their own canvases first
    // and never reach this handler.
    onPinchZoom: ((Float) -> Unit)? = null,
    // Per-clip saturation keyframes: factor multiplying the global
    // saturation in the preview grade (1f = global grade unchanged).
    keyframedSaturationFactor: Float = 1f,
    // Photo-clip editing (PR F). When non-null the selected clip is a
    // still image: it is rendered by PhotoClipPreview with its
    // PhotoEditSettings instead of the ExoPlayer surface.
    photoClip: MediaClip? = null,
    photoCropActive: Boolean = false,
    onPhotoCropRectChange: ((com.apexstudio.app.domain.model.PhotoCropRect) -> Unit)? = null,
    videoWidth: Int = 0,
    videoHeight: Int = 0,
    drawingLayers: List<VideoDrawingLayer> = emptyList(),
    drawingClipId: String? = null,
    drawingEnabled: Boolean = false,
    drawingTool: VideoDrawingTool = VideoDrawingTool.BRUSH,
    drawingColorArgb: Long = 0xFFFF3B82L,
    drawingBrushWidth: Float = VideoDrawingStroke.DEFAULT_WIDTH,
    onDrawingLayerChange: (VideoDrawingLayer) -> Unit = {},
    // Main-track video crop; IMAGE clips use photoClip.photoEdit.crop instead.
    videoCrop: VideoCropSettings? = null,
    faceCropConfig: FaceCropRenderConfig? = null,
    projectVideoEffectId: String? = null,
    workspaceEffects: List<com.apexstudio.app.domain.model.WorkspaceEffect> = emptyList(),
    onEffectPreviewFailure: (String) -> Unit = {},
    compositionAspectRatio: Float = LOCKED_EDITOR_PREVIEW_ASPECT,
    showFrame: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val usesViralHdrPipeline = gpuFilterConfig.activeProfileId ==
        com.apexstudio.app.data.filter.OneTapLooks.VIRAL_HDR_ID
    val gpuFilterPreset = remember(context, gpuFilterConfig.filterPresetId, usesViralHdrPipeline) {
        if (usesViralHdrPipeline && gpuFilterConfig.filterPresetId != null) {
            com.apexstudio.app.data.filter.LutFilterEngine(context).manifest.filters
                .firstOrNull { it.id == gpuFilterConfig.filterPresetId }
        } else null
    }
    val gpuVideoAdjustments = remember(gpuFilterConfig) { gpuFilterConfig.toVideoAdjustments() }
    // Fixed project-video effects run on decoded pixels with the same matrix and
    // shader as export, not the approximate legacy Compose filter overlay.
    val workspaceEffectBinding = remember(workspaceEffects) {
        workspaceEffectPreviewBinding(workspaceEffects)
    }
    LaunchedEffect(
        exoPlayer, videoCrop, faceCropConfig, projectVideoEffectId,
        workspaceEffectBinding.sourceEffects, photoClip?.id, gpuFilterConfig, gpuFilterPreset
    ) {
        val player = exoPlayer ?: return@LaunchedEffect
        try {
            if (photoClip == null) {
                val dynamicCrop = faceCropConfig?.let { FaceCropGlEffect.forPreview(it.previewConfig()) }
                val viralHdrEffects = if (usesViralHdrPipeline) {
                    buildList<androidx.media3.common.Effect> {
                        if (gpuFilterPreset != null && gpuFilterConfig.filterIntensity > 0f) {
                            add(com.apexstudio.app.data.filter.LutFilterGlEffect(
                                context, gpuFilterPreset, gpuFilterConfig.filterIntensity
                            ))
                        }
                        if (!gpuVideoAdjustments.isDefault) {
                            add(com.apexstudio.app.data.adjust.AdjustmentsGlEffect(gpuVideoAdjustments))
                        }
                    }
                } else emptyList()
                player.setVideoEffects(listOfNotNull(
                    dynamicCrop ?: VideoCropGlEffect.fromSettings(videoCrop),
                    com.apexstudio.app.data.template.ProjectVideoPresets.colorEffect(projectVideoEffectId),
                    *workspaceEffectBinding.renderEffects.toTypedArray()
                ) + viralHdrEffects)
                if (player.playbackState == androidx.media3.common.Player.STATE_IDLE && player.mediaItemCount > 0) player.prepare()
                // Replacing a video-effect chain while paused does not guarantee Media3
                // redraws the already-presented frame. Seek to the same source position
                // to invalidate that frame; never move the project playhead or auto-play.
                else if (!player.playWhenReady && player.playbackState == androidx.media3.common.Player.STATE_READY) {
                    player.seekTo(player.currentPosition.coerceAtLeast(0L))
                }
            } else player.setVideoEffects(emptyList())
        } catch (error: Exception) {
            onEffectPreviewFailure(error.message ?: "This device cannot preview this video effect.")
        }
    }
    var photoAspectRatio by remember(photoClip?.id) { mutableStateOf<Float?>(null) }
    val mediaAspectRatio = when {
        photoClip != null -> photoAspectRatio
        else -> previewContentAspect(
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            pixelAspect = exoPlayer?.videoSize?.pixelWidthHeightRatio ?: 1f,
            effectOutputAspect = faceCropConfig?.track?.outputAspectRatio
                ?: videoCropOutputAspect(videoWidth, videoHeight,
                    exoPlayer?.videoSize?.pixelWidthHeightRatio ?: 1f, videoCrop)
        )
    }
    // Photo crop handles and dynamic face output force FIT regardless of COVER.
    val contentCoverMode = if (photoClip != null) isCoverMode && !photoCropActive
        else faceCropConfig == null && isCoverMode
    val mediaContentModifier = Modifier.fillMaxSize().clipToPreviewContent(mediaAspectRatio, contentCoverMode)

    // Dynamic face output already has its exact target aspect; never zoom-crop it a second time.
    val currentResizeMode = if (faceCropConfig != null) {
        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
    } else if (isCoverMode) {
        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    } else {
        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (showFrame) Modifier.clip(RoundedCornerShape(EditorLayoutMetrics.PreviewCornerRadius)) else Modifier)
            // This is only the letterbox/frame surface around media. The
            // PlayerView/PhotoClipPreview content is not recolored or masked.
            .background(ApexPalette.BgBase)
            .then(if (showFrame) Modifier.border(1.dp, Color(0xFF1C2333),
                RoundedCornerShape(EditorLayoutMetrics.PreviewCornerRadius)) else Modifier)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTapVideo() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Video Clip Layer with Keyframe Animations (Scale, Rotation, Opacity, Position)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(onPinchZoom) {
                    // Two-finger pinch only: single taps fall through
                    // to the outer tap handler (show controls).
                    if (onPinchZoom == null) return@pointerInput
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1f) onPinchZoom(zoom)
                    }
                }
                .graphicsLayer {
                    scaleX = animatedTransform.scale
                    scaleY = animatedTransform.scale
                    rotationZ = animatedTransform.rotationDeg
                    alpha = animatedTransform.opacity.coerceIn(0f, 1f)
                    translationX = animatedTransform.translateX * size.width * 0.5f
                    translationY = animatedTransform.translateY * size.height * 0.5f
                }
        ) {
            // Photo clip (PR F): still image rendered with its edits via
            // the same CPU pipeline the export uses — WYSIWYG.
            if (photoClip != null) {
                PhotoClipPreview(
                    uri = photoClip.uri,
                    settings = photoClip.photoEdit,
                    cropActive = photoCropActive,
                    onCropChange = { onPhotoCropRectChange?.invoke(it) },
                    modifier = Modifier.fillMaxSize(),
                    isCoverMode = isCoverMode,
                    onImageAspectRatioChange = { photoAspectRatio = it }
                )
            } else if (exoPlayer != null) {
                AndroidView(
                    factory = { ctx ->
                        val pv = android.view.LayoutInflater.from(ctx)
                            .inflate(com.apexstudio.app.R.layout.view_player, null) as androidx.media3.ui.PlayerView
                        pv.apply {
                            useController = false
                            resizeMode = currentResizeMode
                            player = exoPlayer
                        }
                    },
                    update = { view ->
                        view.player = exoPlayer
                        view.resizeMode = currentResizeMode
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Video preview unavailable", color = ApexPalette.TextSecondary, fontSize = 12.sp)
                }
            }

            // Live Filter Grading & Color Adjustments Overlay (Hardware accelerated Compose canvas, 0ms lag, zero video disappearances)
            FilterPreviewOverlay(
                filterId = if (usesViralHdrPipeline) null else activeFilterId,
                intensity = if (usesViralHdrPipeline) 0f else filterIntensity,
                adjustments = adjustments,
                saturationMultiplier = keyframedSaturationFactor,
                compareMode = lutCompareMode,
                splitPosition = lutCompareSplitPosition,
                modifier = mediaContentModifier
            )

            // Live Real-Time GPUImage Filter & Stylistic Effects Overlay
            if (!usesViralHdrPipeline) {
                GpuVideoFilterOverlay(
                    config = gpuFilterConfig,
                    compareMode = gpuFilterCompareMode,
                    splitPosition = gpuFilterSplitPosition,
                    modifier = mediaContentModifier
                )
            }

            // Real-time 3D Chroma Key Live Overlay
            if (chromaKeySettings.enabled) {
                ChromaKeyPreviewOverlay(
                    settings = chromaKeySettings,
                    modifier = mediaContentModifier
                )
            }

            // Live Visual FX Overlay (VHS, Glitch, Scanlines, Grain, Light Leaks, Bloom, etc.)
            if (activeFxId != null && fxIntensity > 0f) {
                FxPreviewOverlay(
                    fxId = activeFxId,
                    intensity = fxIntensity,
                    isPlaying = isPlaying,
                    modifier = mediaContentModifier,
                    speed = fxSpeed
                )
            }

            // Live AR/AI Face & Festival Filter Overlay
            if (activeArFilterId != null && arFilterIntensity > 0f) {
                ArFaceFilterOverlay(
                    filterId = activeArFilterId,
                    intensity = arFilterIntensity,
                    customText = arFilterCustomText,
                    isPlaying = isPlaying,
                    modifier = mediaContentModifier
                )
            }

            // Shape mask (Mask tool): darken everything outside the mask
            // shape so the kept region is visible; drag to move/resize
            // while the mask panel is open.
            PreviewContentCanvas(mediaAspectRatio, contentCoverMode) {
                MaskPreviewOverlay(
                    mask = mask,
                    editMode = maskEditMode,
                    onCenterChangeLive = onMaskCenterLive,
                    onCenterChangeFinished = onMaskCenterFinished,
                    onSizeChangeLive = onMaskSizeLive,
                    onSizeChangeFinished = onMaskSizeFinished,
                    modifier = Modifier.fillMaxSize()
                )
            }

        // Drawings are constrained to the same centered FIT bounds as the
        // actual media (or the full viewport in COVER mode). Coordinates
        // therefore remain frame-normalized and never include letterbox bars.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val bounds = previewContentBounds(
                with(density) { maxWidth.toPx() },
                with(density) { maxHeight.toPx() },
                mediaAspectRatio,
                contentCoverMode
            )
            if (bounds != null) {
                val x = with(density) { bounds.left.toDp() }
                val y = with(density) { bounds.top.toDp() }
                val width = with(density) { (bounds.right - bounds.left).toDp() }
                val height = with(density) { (bounds.bottom - bounds.top).toDp() }
                val emptyLayer = remember(drawingClipId) { VideoDrawingLayer(clipId = drawingClipId) }
                val storedLayer = drawingLayers.lastOrNull { it.clipId == drawingClipId }
                val layerToEdit = storedLayer ?: emptyLayer
                Box(Modifier.offset(x, y).size(width, height)) {
                    VideoDrawingArtwork(
                        layers = if (drawingEnabled && storedLayer != null)
                            drawingLayers.filterNot { it.id == storedLayer.id }
                        else drawingLayers,
                        clipId = drawingClipId,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (drawingEnabled) {
                        VideoDrawingCanvas(
                            layer = layerToEdit,
                            onLayerChange = onDrawingLayerChange,
                            modifier = Modifier.fillMaxSize(),
                            enabled = true,
                            tool = drawingTool,
                            colorArgb = drawingColorArgb,
                            brushWidth = drawingBrushWidth
                        )
                    }
                }
            }
        }
    }

        // Playback time is in the transport row; no filename/time/keyframe HUD covers the media.
        // Keyframe values stay in their editing panel; never cover the video with a debug HUD.
        // Interactive PiP overlay canvas (video / image overlays on the V2
        // track): tap to select, drag to move, pinch to scale/rotate,
        // delete + resize handles on the selection. Replaces the old
        // fixed bottom-end box, which could not be edited at all.
        if (overlayClips.isNotEmpty()) {
            PreviewContentCanvas(compositionAspectRatio, isCoverMode = false) {
                PipOverlayCanvas(
                    overlayClips = overlayClips,
                    selectedOverlayClipId = selectedOverlayClipId,
                    currentTimeMs = currentTimeMs,
                    overlayPlayer = overlayPlayer,
                    overlayPlayerClipId = overlayPlayerClipId,
                    overlayIsVideo = overlayIsVideo,
                    onSelectOverlayClip = { onSelectOverlayClip?.invoke(it) },
                    onMoveOverlayClip = { id, dx, dy, persist ->
                        onMoveOverlayClip?.invoke(id, dx, dy, persist)
                    },
                    onScaleOverlayClip = { id, scale, persist ->
                        onScaleOverlayClip?.invoke(id, scale, persist)
                    },
                    onRotateOverlayClip = { id, deltaDeg, persist ->
                        onRotateOverlayClip?.invoke(id, deltaDeg, persist)
                    },
                    onOverlayGestureEnd = { onOverlayGestureEnd?.invoke(it) },
                    onRemoveOverlayClip = { onRemoveOverlayClip?.invoke(it) },
                    onUpsertOverlayKeyframe = onUpsertOverlayKeyframe,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (playerError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Video Error",
                        tint = ApexPalette.NeonPink,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "Video Load Error",
                        color = ApexPalette.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = playerError,
                        color = ApexPalette.TextSecondary,
                        fontSize = 11.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    if (onRetryLoad != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ApexPalette.NeonCyan.copy(alpha = 0.2f))
                                .border(1.dp, ApexPalette.NeonCyan, RoundedCornerShape(8.dp))
                                .clickable { onRetryLoad() }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Reload Sample Video",
                                color = ApexPalette.NeonCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}



internal fun displayedVideoAspect(width: Int, height: Int, pixelAspect: Float): Float? =
    (width.toFloat() * pixelAspect / height.toFloat())
        .takeIf { width > 0 && height > 0 && pixelAspect > 0f && it.isFinite() }

/**
 * Aspect ratio of pixels actually presented by the preview. A dynamic face crop
 * changes Media3's effect output size while Player.videoSize can still describe
 * the decoded source; use the effect's fixed output aspect for overlay clipping.
 */
internal fun previewContentAspect(
    videoWidth: Int,
    videoHeight: Int,
    pixelAspect: Float,
    effectOutputAspect: Float? = null
): Float? = effectOutputAspect?.takeIf { it.isFinite() && it > 0f }
    ?: displayedVideoAspect(videoWidth, videoHeight, pixelAspect)

/** Aspect of the frame emitted by the manual crop shader, not its uncropped input. */
internal fun videoCropOutputAspect(
    videoWidth: Int,
    videoHeight: Int,
    pixelAspect: Float,
    crop: VideoCropSettings?
): Float? {
    val sourceAspect = displayedVideoAspect(videoWidth, videoHeight, pixelAspect) ?: return null
    if (crop == null || crop.isFullFrame) return null
    val safeCrop = crop.sanitized()
    return (sourceAspect * safeCrop.width / safeCrop.height)
        .takeIf { it.isFinite() && it > 0f }
}

internal data class PreviewContentBounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** Centered media frame for FIT; COVER fills the viewport because the player zoom-crops it. */
internal fun previewContentBounds(
    viewportWidth: Float,
    viewportHeight: Float,
    mediaAspectRatio: Float?,
    isCoverMode: Boolean
): PreviewContentBounds? {
    if (viewportWidth <= 0f || viewportHeight <= 0f) return null
    val aspect = mediaAspectRatio?.takeIf { it.isFinite() && it > 0f } ?: return null
    if (isCoverMode) return PreviewContentBounds(0f, 0f, viewportWidth, viewportHeight)
    val viewportAspect = viewportWidth / viewportHeight
    return if (viewportAspect > aspect) {
        val width = viewportHeight * aspect
        val left = (viewportWidth - width) / 2f
        PreviewContentBounds(left, 0f, left + width, viewportHeight)
    } else {
        val height = viewportWidth / aspect
        val top = (viewportHeight - height) / 2f
        PreviewContentBounds(0f, top, viewportWidth, top + height)
    }
}

/** Output-frame aspect selected in export settings; the editor chrome stays 16:9. */
internal fun previewCompositionAspect(aspectRatio: String): Float = when (aspectRatio) {
    "9:16" -> 9f / 16f
    "1:1" -> 1f
    else -> 16f / 9f
}

/**
 * Constrains frame-normalized interactive overlays to the pixels occupied by
 * the source/output frame. FIT letterboxes are excluded; COVER remains full
 * viewport because the player zoom-crops the source to fill it.
 */
@Composable
internal fun PreviewContentCanvas(
    mediaAspectRatio: Float?,
    isCoverMode: Boolean,
    modifier: Modifier = Modifier,
    frameModifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    BoxWithConstraints(modifier = modifier) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val bounds = previewContentBounds(
            with(density) { maxWidth.toPx() },
            with(density) { maxHeight.toPx() },
            mediaAspectRatio,
            isCoverMode
        )
        if (bounds == null) {
            Box(Modifier.fillMaxSize().then(frameModifier), content = content)
        } else {
            val x = with(density) { bounds.left.toDp() }
            val y = with(density) { bounds.top.toDp() }
            val width = with(density) { (bounds.right - bounds.left).toDp() }
            val height = with(density) { (bounds.bottom - bounds.top).toDp() }
            Box(Modifier.offset(x, y).size(width, height).then(frameModifier), content = content)
        }
    }
}

private fun Modifier.clipToPreviewContent(mediaAspectRatio: Float?, isCoverMode: Boolean): Modifier =
    drawWithContent {
        val bounds = previewContentBounds(size.width, size.height, mediaAspectRatio, isCoverMode)
        if (bounds == null) {
            drawContent()
        } else {
            clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom) { this@drawWithContent.drawContent() }
        }
    }
