package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.StickerOverlay
import com.apexstudio.app.ui.theme.ApexPalette
import com.apexstudio.app.util.TimeFormat

// === 2. VIDEO PREVIEW AREA ===
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
    onSelectOverlayClip: ((String?) -> Unit)? = null,
    onMoveOverlayClip: ((id: String, dx: Float, dy: Float, persist: Boolean) -> Unit)? = null,
    onScaleOverlayClip: ((id: String, scale: Float, persist: Boolean) -> Unit)? = null,
    onRotateOverlayClip: ((id: String, deltaDeg: Float, persist: Boolean) -> Unit)? = null,
    onOverlayGestureEnd: ((String) -> Unit)? = null,
    onRemoveOverlayClip: ((String) -> Unit)? = null,
    isCoverMode: Boolean = true,
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
    // Photo-clip editing (PR F). When non-null the selected clip is a
    // still image: it is rendered by PhotoClipPreview with its
    // PhotoEditSettings instead of the ExoPlayer surface.
    photoClip: MediaClip? = null,
    photoCropActive: Boolean = false,
    onPhotoCropRectChange: ((com.apexstudio.app.domain.model.PhotoCropRect) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val currentResizeMode = if (isCoverMode) {
        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    } else {
        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(ApexPalette.BgBase)
            .border(1.dp, Color(0xFF1C2333), RoundedCornerShape(24.dp))
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
                    modifier = Modifier.fillMaxSize()
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
                // Placeholder video frame thumbnail render
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFF1A1A2E), Color(0xFF16213E), Color(0xFF0F3460))
                        )
                    )
                }
            }

            // Live Filter Grading & Color Adjustments Overlay (Hardware accelerated Compose canvas, 0ms lag, zero video disappearances)
            FilterPreviewOverlay(
                filterId = activeFilterId,
                intensity = filterIntensity,
                adjustments = adjustments,
                compareMode = lutCompareMode,
                splitPosition = lutCompareSplitPosition,
                modifier = Modifier.fillMaxSize()
            )

            // Live Real-Time GPUImage Filter & Stylistic Effects Overlay
            GpuVideoFilterOverlay(
                config = gpuFilterConfig,
                compareMode = gpuFilterCompareMode,
                splitPosition = gpuFilterSplitPosition,
                modifier = Modifier.fillMaxSize()
            )

            // Real-time 3D Chroma Key Live Overlay
            if (chromaKeySettings.enabled) {
                ChromaKeyPreviewOverlay(
                    settings = chromaKeySettings,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Live Visual FX Overlay (VHS, Glitch, Scanlines, Grain, Light Leaks, Bloom, etc.)
            if (activeFxId != null && fxIntensity > 0f) {
                FxPreviewOverlay(
                    fxId = activeFxId,
                    intensity = fxIntensity,
                    isPlaying = isPlaying,
                    modifier = Modifier.fillMaxSize(),
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
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Mockup: "REC • 00:20 / 00:25" time pill, top-left inside the preview.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF3B30))
                )
                Text(
                    text = "REC • ${TimeFormat.msToShort(currentTimeMs)} / ${TimeFormat.msToShort(durationMs)}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }

        // Mockup: "HDR" badge, bottom-right inside the preview.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "HDR",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Keyframe HUD Badge when active transform is non-identity
        if (animatedTransform != com.apexstudio.app.domain.model.AnimatedTransform.Identity) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .graphicsLayer { rotationZ = 45f }
                            .background(ApexPalette.NeonCyan)
                    )
                    Text(
                        text = "KF: ${String.format(java.util.Locale.US, "%.2fx", animatedTransform.scale)} | ${animatedTransform.rotationDeg.toInt()}° | ${(animatedTransform.opacity * 100).toInt()}%",
                        color = ApexPalette.NeonCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
        }

        // Interactive PiP overlay canvas (video / image overlays on the V2
        // track): tap to select, drag to move, pinch to scale/rotate,
        // delete + resize handles on the selection. Replaces the old
        // fixed bottom-end box, which could not be edited at all.
        if (overlayClips.isNotEmpty()) {
            PipOverlayCanvas(
                overlayClips = overlayClips,
                selectedOverlayClipId = selectedOverlayClipId,
                currentTimeMs = currentTimeMs,
                overlayPlayer = overlayPlayer,
                overlayPlayerClipId = overlayPlayerClipId,
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
                modifier = Modifier.fillMaxSize()
            )
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

