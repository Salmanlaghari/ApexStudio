package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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

// === 2. VIDEO PREVIEW AREA ===
@Composable
fun VideoPreviewArea(
    exoPlayer: ExoPlayer? = null,
    chromaKeySettings: com.apexstudio.app.domain.model.ChromaKeySettings = com.apexstudio.app.domain.model.ChromaKeySettings(),
    overlayClip: MediaClip? = null,
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
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF12121A))
            .border(1.dp, Color(0xFF1F1F2E), RoundedCornerShape(16.dp))
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

        // Keyframe HUD Badge when active transform is non-identity
        if (animatedTransform != com.apexstudio.app.domain.model.AnimatedTransform.Identity) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
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

        // Picture-in-Picture Overlay Media Preview (Video / Image Overlay)
        if (overlayClip != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 12.dp)
                    .size(130.dp, 75.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
                    .border(1.5.dp, Color(0xFF00F0FF), RoundedCornerShape(8.dp))
            ) {
                coil.compose.AsyncImage(
                    model = overlayClip.uri,
                    contentDescription = "Overlay Media",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(3.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "PIP OVERLAY",
                        color = Color(0xFF00F0FF),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
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

