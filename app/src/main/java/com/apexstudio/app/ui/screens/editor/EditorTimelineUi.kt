package com.apexstudio.app.ui.screens.editor

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.media.ThumbnailExtractor
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.ui.theme.ApexPalette

// === 4. CAPCUT-STYLE TIMELINE TRACK AREA ===

/**
 * Filmstrip thumbnails for one V1 clip, keyed by second (0, 1, 2, ...).
 * Still-image clips contribute the photo itself (MediaMetadataRetriever
 * cannot pull frames from a JPEG); video clips get per-second frames.
 * [ThumbnailExtractor.extractPerSecondFrames] falls back to generated
 * frames, so the map is never empty for a valid clip.
 */
private suspend fun extractClipThumbnails(
    context: android.content.Context,
    clip: com.apexstudio.app.domain.model.MediaClip
): Map<Int, Bitmap> {
    if (clip.type == ClipType.IMAGE) {
        val photo = com.apexstudio.app.data.photoedit.PhotoEditRenderer.loadBitmap(
            context, clip.uri, maxDim = 240
        )
        return if (photo != null) mapOf(0 to photo) else emptyMap()
    }
    val trimStart = clip.trimStartMs
    val trimEnd =
        if (clip.trimEndMs > trimStart) clip.trimEndMs else clip.durationMs.coerceAtLeast(1000L)
    return ThumbnailExtractor.extractPerSecondFrames(
        context = context,
        uri = clip.uri,
        trimStartMs = trimStart,
        trimEndMs = trimEnd,
        frameWidthPx = 120,
        frameHeightPx = 80,
        maxSeconds = 60
    )
}

@Composable
fun TimelineTrackArea(
    state: com.apexstudio.app.presentation.state.EditorState,
    onScrub: (Long) -> Unit = {},
    onToggleSnapToBeat: () -> Unit = {},
    onToggleMagneticSnapping: () -> Unit = {},
    onToggleRippleEdit: () -> Unit = {},
    onSelectClip: (String?) -> Unit = {},
    onSelectFx: (String) -> Unit = {},
    onSelectFilter: (String) -> Unit = {},
    onAddMedia: (ClipType) -> Unit = {},
    onSplitClip: (clipId: String, atMs: Long) -> Unit = { _, _ -> },
    onDuplicateClip: (clipId: String) -> Unit = {},
    onDeleteClip: (clipId: String) -> Unit = {},
    onDeleteClips: (Set<String>) -> Unit = {},
    onMoveClipLeft: (clipId: String) -> Unit = {},
    onMoveClipRight: (clipId: String) -> Unit = {},
    onReorderClips: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onShiftClipOffset: (clipId: String, deltaMs: Long) -> Unit = { _, _ -> },
    onSetClipOffset: (clipId: String, offsetMs: Long) -> Unit = { _, _ -> },
    onTrimClip: (clipId: String, startMs: Long, endMs: Long) -> Unit = { _, _, _ -> },
    onToggleKeyframeAtPlayhead: (clipId: String) -> Unit = {},
    onMoveKeyframe: (clipId: String, keyframeId: String, newTimeMs: Long) -> Unit = { _, _, _ -> },
    onToggleTextKeyframeAtPlayhead: (clipId: String, overlayId: String) -> Unit = { _, _ -> },
    onMoveTextKeyframe: (clipId: String, overlayId: String, keyframeId: String, newTimeMs: Long) -> Unit = { _, _, _, _ -> },
    onZoomIn: () -> Unit = {},
    onZoomOut: () -> Unit = {},
    onResetZoom: () -> Unit = {},
    onSetZoom: (Float) -> Unit = {},
    onOpenSpeed: () -> Unit = {},
    onOpenAudio: () -> Unit = {},
    onOpenTrim: () -> Unit = {},
    onOpenText: () -> Unit = {},
    onOpenStickers: () -> Unit = {},
    onOpenFx: () -> Unit = {},
    onOpenVoice: () -> Unit = {},
    onOpenAnimation: () -> Unit = {},
    onOpenTransition: () -> Unit = {},
    onOpenClipTransition: (fromClipId: String, toClipId: String) -> Unit = { _, _ -> },
    onOpenChromaKey: () -> Unit = {},
    onOpenArFilters: () -> Unit = {},
    onOpenRoyaltyMusic: () -> Unit = {},
    onSelectAudioTrack: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clips = state.project?.clips ?: emptyList()
    val activeClip = clips.firstOrNull { it.id == state.selectedClipId } ?: clips.firstOrNull()
    val durationMs = state.durationMs.coerceAtLeast(1000L)
    val playheadMs = state.playerPositionMs.coerceIn(0L, durationMs)

    var selectedLayer by remember { mutableStateOf(SelectedLayerType.NONE) }
    val timelineScrollState = rememberScrollState()
    val zoomFactor = state.timelineZoom.coerceIn(0.5f, 10.0f)
    val durationSec = (durationMs / 1000L).coerceAtLeast(1L).toInt()
    val baseSecondWidthDp = 52.dp
    val secondWidthDp = (baseSecondWidthDp * zoomFactor).coerceIn(26.dp, 240.dp)

    // V1 lane clips: main video + photo (IMAGE) clips share the filmstrip
    // lane. Photo clips previously rendered nowhere, leaving the V1 track
    // empty even with a project loaded.
    val v1Clips = remember(clips) {
        clips.filter { it.type == ClipType.VIDEO || it.type == ClipType.IMAGE }
    }

    // Per-clip, per-second filmstrip thumbnails. Extracted once per clip
    // (keyed by id/uri/trim) so every V1 clip shows its own frames instead
    // of every clip reusing the active clip's frames.
    val thumbnailsByClip = remember { mutableStateMapOf<String, Map<Int, Bitmap>>() }
    v1Clips.forEach { clip ->
        LaunchedEffect(clip.id, clip.uri, clip.trimStartMs, clip.trimEndMs) {
            try {
                thumbnailsByClip[clip.id] = extractClipThumbnails(context, clip)
            } catch (e: Exception) {
                Log.w("TimelineTrackArea", "Thumbnail extraction failed for ${clip.id}: ${e.message}")
            }
        }
    }
    // Drop thumbnails for clips that no longer exist.
    LaunchedEffect(v1Clips.map { it.id }) {
        thumbnailsByClip.keys.retainAll(v1Clips.map { it.id }.toSet())
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF08080E))
            .padding(top = 2.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Multi-Layer Video Timeline Component: Horizontal Scrolling, Multi-Layer Tracks (Video, Audio, Overlay), Drag-and-Drop Reordering
        com.apexstudio.app.ui.components.VideoTimeline(
            state = state,
            onScrub = onScrub,
            onSelectClip = { clipId ->
                selectedLayer = SelectedLayerType.VIDEO_V1
                onSelectClip(clipId)
            },
            onReorderClips = onReorderClips,
            onAddMedia = { type -> onAddMedia(type) },
            onSplitClip = onSplitClip,
            onDuplicateClip = onDuplicateClip,
            onDeleteClip = onDeleteClip,
            onZoomChange = { newZoom ->
                onSetZoom(newZoom)
            },
            onOpenTransition = { fromId, toId ->
                onOpenClipTransition(fromId, toId)
            },
            thumbnailsByClip = thumbnailsByClip,
            onTrimClip = onTrimClip,
            onMoveClipOffset = onSetClipOffset,
            onDeleteClips = onDeleteClips,
            onToggleKeyframeAtPlayhead = onToggleKeyframeAtPlayhead,
            onMoveKeyframe = onMoveKeyframe,
            onToggleTextKeyframeAtPlayhead = onToggleTextKeyframeAtPlayhead,
            onMoveTextKeyframe = onMoveTextKeyframe,
            onSelectAudioTrack = { trackId ->
                selectedLayer = SelectedLayerType.AUDIO_A1
                onSelectAudioTrack(trackId)
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        Spacer(Modifier.height(4.dp))

        // --- 3. CONTEXTUAL QUICK ACTIONS (adapt on layer tap) ---
        // Note: the mockup's cyan divider + "Video clip selected" label and
        // the clip toolbar live in ContextualToolbar (EditorBottomToolbarSection,
        // rendered directly below); they are intentionally not duplicated here.
        if (selectedLayer != SelectedLayerType.NONE) {
            // Layer-Specific Contextual Action Cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .padding(top = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (selectedLayer) {
                    SelectedLayerType.VIDEO_V1 -> {
                        QuickActionSquareCard(
                            icon = Icons.Default.ContentCut,
                            label = "Split",
                            onClick = { activeClip?.let { onSplitClip(it.id, playheadMs) } }
                        )
                        QuickActionSquareCard(
                            icon = Icons.Default.Tune,
                            label = "Trim",
                            onClick = onOpenTrim
                        )
                        QuickActionSquareCard(
                            icon = Icons.Default.Speed,
                            label = "Speed",
                            onClick = onOpenSpeed
                        )
                        QuickActionSquareCard(
                            icon = Icons.Default.VolumeUp,
                            label = "Volume",
                            onClick = onOpenAudio
                        )
                        QuickActionSquareCard(
                            icon = Icons.Default.AutoAwesome,
                            label = "Effects",
                            onClick = onOpenFx
                        )
                        QuickActionSquareCard(
                            icon = Icons.Default.Layers,
                            label = "Animation",
                            onClick = onOpenAnimation
                        )
                        QuickActionSquareCard(
                            icon = Icons.Default.ContentCopy,
                            label = "Duplicate",
                            onClick = { activeClip?.let { onDuplicateClip(it.id) } }
                        )
                        QuickActionSquareCard(
                            icon = Icons.Default.DeleteOutline,
                            label = "Delete",
                            tint = ApexPalette.NeonPink,
                            onClick = { activeClip?.let { onDeleteClip(it.id) } }
                        )
                    }

                    SelectedLayerType.OVERLAY_V2 -> {
                        val ovClip = state.project?.clips?.firstOrNull { it.type == ClipType.OVERLAY }
                        if (ovClip != null) {
                            QuickActionSquareCard(
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                label = "Push -1s",
                                tint = Color(0xFF00E5FF),
                                onClick = { onShiftClipOffset(ovClip.id, -1000L) }
                            )
                            QuickActionSquareCard(
                                icon = Icons.AutoMirrored.Filled.ArrowForward,
                                label = "Push +1s",
                                tint = Color(0xFF00E5FF),
                                onClick = { onShiftClipOffset(ovClip.id, 1000L) }
                            )
                            QuickActionSquareCard(
                                icon = Icons.Default.NearMe,
                                label = "To Playhead",
                                tint = Color(0xFFFFD700),
                                onClick = { onSetClipOffset(ovClip.id, playheadMs) }
                            )
                            QuickActionSquareCard(
                                icon = Icons.Default.SwapVert,
                                label = "Swap V1",
                                tint = Color(0xFF38BDF8),
                                onClick = { onMoveClipLeft(ovClip.id) }
                            )
                        }
                        QuickActionSquareCard(
                            icon = Icons.Default.ContentCut,
                            label = "Split",
                            onClick = { activeClip?.let { onSplitClip(it.id, playheadMs) } }
                        )
                        QuickActionSquareCard(
                            icon = Icons.Default.Layers,
                            label = "3D Chroma",
                            tint = Color(0xFF00E5FF),
                            onClick = onOpenChromaKey
                        )
                        QuickActionSquareCard(
                            icon = Icons.Default.AutoAwesome,
                            label = "Animation",
                            onClick = onOpenAnimation
                        )
                        QuickActionSquareCard(
                            icon = Icons.Default.AddPhotoAlternate,
                            label = "Replace",
                            onClick = { onAddMedia(ClipType.OVERLAY) }
                        )
                        QuickActionSquareCard(
                            icon = Icons.Default.DeleteOutline,
                            label = "Delete",
                            tint = ApexPalette.NeonPink,
                            onClick = { activeClip?.let { onDeleteClip(it.id) } }
                        )
                    }

                    SelectedLayerType.TEXT_TXT -> {
                        QuickActionSquareCard(
                            icon = Icons.Default.TextFields,
                            label = "Edit Text",
                            tint = Color(0xFFA855F7),
                            onClick = onOpenText
                        )
                        QuickActionSquareCard(
                            icon = Icons.Default.Palette,
                            label = "Styles",
                            tint = Color(0xFFA855F7),
                            onClick = onOpenText
                        )
                        QuickActionSquareCard(
                            icon = Icons.Default.EmojiEmotions,
                            label = "Stickers",
                            tint = Color(0xFFEC4899),
                            onClick = onOpenStickers
                        )
                        QuickActionSquareCard(
                            icon = Icons.Default.ContentCopy,
                            label = "Duplicate",
                            onClick = { activeClip?.let { onDuplicateClip(it.id) } }
                        )
                        QuickActionSquareCard(
                            icon = Icons.Default.DeleteOutline,
                            label = "Delete",
                            tint = ApexPalette.NeonPink,
                            onClick = { activeClip?.let { onDeleteClip(it.id) } }
                        )
                    }

                    SelectedLayerType.FX_LAYER -> {
                        QuickActionSquareCard(
                            icon = Icons.Default.AutoAwesome,
                            label = "Visual FX",
                            tint = Color(0xFFF59E0B),
                            onClick = onOpenFx
                        )
                        QuickActionSquareCard(
                            icon = Icons.Default.FaceRetouchingNatural,
                            label = "AR Face",
                            tint = Color(0xFF10B981),
                            onClick = onOpenArFilters
                        )
                        QuickActionSquareCard(
                            icon = Icons.Default.Refresh,
                            label = "Reset FX",
                            onClick = { onSelectFx("") }
                        )
                    }

                    SelectedLayerType.AUDIO_A1 -> {
                        QuickActionSquareCard(
                            icon = Icons.Default.LibraryMusic,
                            label = "Audio Mix",
                            tint = Color(0xFF10B981),
                            onClick = onOpenAudio
                        )
                        QuickActionSquareCard(
                            icon = Icons.Default.MusicNote,
                            label = "Royalty Music",
                            tint = Color(0xFF34D399),
                            onClick = onOpenRoyaltyMusic
                        )
                        QuickActionSquareCard(
                            icon = Icons.Default.VolumeUp,
                            label = "Volume",
                            onClick = onOpenAudio
                        )
                        QuickActionSquareCard(
                            icon = Icons.Default.Mic,
                            label = "Record",
                            onClick = onOpenVoice
                        )
                        QuickActionSquareCard(
                            icon = Icons.Default.DeleteOutline,
                            label = "Delete",
                            tint = ApexPalette.NeonPink,
                            onClick = { activeClip?.let { onDeleteClip(it.id) } }
                        )
                    }

                    SelectedLayerType.NONE -> {}
                }
                }
        } else {
            // Default CapCut quick action toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickActionSquareCard(
                    icon = Icons.Default.ContentCut,
                    label = "Split",
                    onClick = { activeClip?.let { onSplitClip(it.id, playheadMs) } }
                )

                QuickActionSquareCard(
                    icon = Icons.Default.Tune,
                    label = "Trim",
                    onClick = onOpenTrim
                )

                QuickActionSquareCard(
                    icon = Icons.Default.Speed,
                    label = "Speed",
                    onClick = onOpenSpeed
                )

                QuickActionSquareCard(
                    icon = Icons.Default.VolumeUp,
                    label = "Volume",
                    onClick = onOpenAudio
                )

                QuickActionSquareCard(
                    icon = Icons.Default.Layers,
                    label = "Animation",
                    onClick = onOpenAnimation
                )

                QuickActionSquareCard(
                    icon = Icons.Default.DeleteOutline,
                    label = "Delete",
                    tint = ApexPalette.NeonPink,
                    onClick = { activeClip?.let { onDeleteClip(it.id) } }
                )
            }
        }
    }
}

@Composable
fun TimelineQuickActionBar(
    hasKeyframeAtPlayhead: Boolean,
    timelineZoom: Float,
    canSplit: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onToggleKeyframe: () -> Unit,
    onPrevKeyframe: () -> Unit,
    onNextKeyframe: () -> Unit,
    onSplit: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onOpenKeyframes: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0F0F1A))
            .border(1.dp, Color(0xFF232336), RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Keyframe jump & toggle group
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Jump Prev Keyframe
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1A1A28))
                    .clickable(onClick = onPrevKeyframe),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FastRewind,
                    contentDescription = "Prev Keyframe",
                    tint = ApexPalette.NeonCyan,
                    modifier = Modifier.size(15.dp)
                )
            }

            // Keyframe Diamond Add/Remove
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (hasKeyframeAtPlayhead) ApexPalette.NeonCyan.copy(alpha = 0.25f) else Color(0xFF222234))
                    .border(
                        1.dp,
                        if (hasKeyframeAtPlayhead) ApexPalette.NeonCyan else Color(0xFF383852),
                        RoundedCornerShape(6.dp)
                    )
                    .clickable(onClick = onToggleKeyframe)
                    .padding(horizontal = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .graphicsLayer { rotationZ = 45f }
                            .background(if (hasKeyframeAtPlayhead) ApexPalette.NeonCyan else Color.White)
                    )
                    Text(
                        text = if (hasKeyframeAtPlayhead) "- KF" else "+ KF",
                        color = if (hasKeyframeAtPlayhead) ApexPalette.NeonCyan else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Jump Next Keyframe
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1A1A28))
                    .clickable(onClick = onNextKeyframe),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = "Next Keyframe",
                    tint = ApexPalette.NeonCyan,
                    modifier = Modifier.size(15.dp)
                )
            }

            // Open Keyframe Studio Curves
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1E2235))
                    .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                    .clickable(onClick = onOpenKeyframes)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Animation,
                        contentDescription = "Keyframe Studio",
                        tint = ApexPalette.NeonCyan,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Curves",
                        color = ApexPalette.NeonCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Center: Split & Reorder buttons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Split button
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (canSplit) Color(0xFF242438) else Color(0xFF141420))
                    .border(1.dp, if (canSplit) Color(0xFF42425E) else Color.Transparent, RoundedCornerShape(6.dp))
                    .clickable(enabled = canSplit, onClick = onSplit)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCut,
                        contentDescription = "Split",
                        tint = if (canSplit) Color.White else Color(0xFF555566),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Split",
                        color = if (canSplit) Color.White else Color(0xFF555566),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (canMoveLeft) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1A1A28))
                        .clickable(onClick = onMoveLeft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Move Clip Left",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            if (canMoveRight) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1A1A28))
                        .clickable(onClick = onMoveRight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Move Clip Right",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // Right: Timeline Zoom Controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF1A1A28))
                    .clickable(onClick = onZoomOut),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomOut,
                    contentDescription = "Zoom Out",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }

            Text(
                text = String.format(java.util.Locale.US, "%.1fx", timelineZoom),
                color = Color(0xFF94A3B8),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 3.dp)
            )

            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF1A1A28))
                    .clickable(onClick = onZoomIn),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomIn,
                    contentDescription = "Zoom In",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickActionSquareCard(
    icon: ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 54.dp, height = 50.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF141420))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                color = tint,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun TrackSidebarCell(
    badge: String,
    label: String,
    icon: ImageVector,
    tint: Color,
    height: androidx.compose.ui.unit.Dp,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 2.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) tint.copy(alpha = 0.25f) else Color(0xFF141420))
            .border(
                width = if (isSelected) 1.5.dp else 0.5.dp,
                color = if (isSelected) tint else tint.copy(alpha = 0.45f),
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isSelected) tint else tint.copy(alpha = 0.25f))
                        .padding(horizontal = 2.dp, vertical = 0.5.dp)
                ) {
                    Text(
                        text = badge,
                        color = if (isSelected) Color.Black else tint,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isSelected) tint else tint.copy(alpha = 0.9f),
                    modifier = Modifier.size(11.dp)
                )
            }
            Text(
                text = label,
                color = if (isSelected) tint else Color.White.copy(alpha = 0.85f),
                fontSize = 7.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}
