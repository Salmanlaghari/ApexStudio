package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.presentation.state.EditorState
import com.apexstudio.app.presentation.viewmodel.*
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.ui.theme.ApexPalette

/**
 * CapCut-style contextual bottom toolbar.
 *
 * The tool set follows the current selection, exactly like CapCut:
 * - Nothing selected → global tools (Edit, Audio, Text, Effects, Filters, Stickers, Lenses)
 * - Video clip selected → clip tools (Keyframe, Adjust, Replace, Speed, Animation, Delete)
 * - Audio selected → audio tools (Fade, Replace, Beats, Volume, Delete)
 *
 * A collapse chevron as the first item deselects and returns to global tools.
 */
enum class ToolbarSelectionKind { NONE, VIDEO, AUDIO, PHOTO }

fun resolveToolbarSelection(state: EditorState): ToolbarSelectionKind {
    val project = state.project ?: return ToolbarSelectionKind.NONE
    if (state.selectedAudioTrackId != null &&
        project.audioTracks.any { it.id == state.selectedAudioTrackId }
    ) {
        return ToolbarSelectionKind.AUDIO
    }
    val clip = project.clips.firstOrNull { it.id == state.selectedClipId } ?: return ToolbarSelectionKind.NONE
    return when (clip.type) {
        ClipType.VIDEO, ClipType.OVERLAY -> ToolbarSelectionKind.VIDEO
        ClipType.AUDIO, ClipType.SFX -> ToolbarSelectionKind.AUDIO
        // Still-image clips get the photo-editing tool set (PR F).
        ClipType.IMAGE -> ToolbarSelectionKind.PHOTO
    }
}

private data class CtxToolItem(
    val label: String,
    val icon: ImageVector? = null,
    val tint: Color = Color(0xFF9CA3AF),
    val onClick: () -> Unit,
    /** Text glyph rendered in place of [icon] (e.g. the mockup's bold "T" for Text). */
    val glyph: String? = null,
    /** Degrees to rotate [icon] (e.g. 90 for the mockup's horizontal Adjust sliders). */
    val iconRotationDeg: Float = 0f
) {
    init {
        require(icon != null || glyph != null) { "CtxToolItem needs an icon or a glyph" }
    }
}

/**
 * Mockup Keyframe icon: diamond (rhombus) outline, drawn as a stroked vector
 * so it pixel-matches the approved mockup (diamond, not the key glyph).
 */
private val DiamondOutlineIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "DiamondOutline",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = listOf(
            androidx.compose.ui.graphics.vector.PathNode.MoveTo(12f, 3.5f),
            androidx.compose.ui.graphics.vector.PathNode.LineTo(20.5f, 12f),
            androidx.compose.ui.graphics.vector.PathNode.LineTo(12f, 20.5f),
            androidx.compose.ui.graphics.vector.PathNode.LineTo(3.5f, 12f),
            androidx.compose.ui.graphics.vector.PathNode.Close
        ),
        stroke = androidx.compose.ui.graphics.SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ).build()
}

@Composable
fun ContextualBottomToolbar(
    selectionKind: ToolbarSelectionKind,
    // Global tools (mockup bottom tabs)
    onText: () -> Unit = {},
    onEffects: () -> Unit = {},
    onStickers: () -> Unit = {},
    // Global tools: Filters opens the GPU filter gallery directly.
    onFilters: () -> Unit = {},
    // Mockup "AR Face" tab: opens the AR face-filter panel (functionality
    // owned by a separate workstream; this only wires the tab).
    onArFace: () -> Unit = {},
    // Video-clip tools
    onAdjust: () -> Unit = {},
    onReplace: () -> Unit = {},
    onSpeed: () -> Unit = {},
    onVolume: () -> Unit = {},
    onAnimation: () -> Unit = {},
    onDelete: () -> Unit = {},
    // Audio tools
    onFade: () -> Unit = {},
    onBeats: () -> Unit = {},
    beatsActive: Boolean = false,
    beatsAnalyzing: Boolean = false,
    // Photo-clip tools (PR F) — shown when an IMAGE clip is selected.
    onPhotoCrop: () -> Unit = {},
    onPhotoAdjust: () -> Unit = {},
    onPhotoFilters: () -> Unit = {},
    onPhotoRotate: () -> Unit = {},
    // Collapse (deselect)
    onCollapse: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val items: List<CtxToolItem> = when (selectionKind) {
        ToolbarSelectionKind.NONE -> listOf(
            // Mockup bottom tabs, exact order/labels/icons: Text (active cyan),
            // Stickers, Effects, Filters, AR Face, Adjust.
            CtxToolItem("Text", glyph = "T", tint = ApexPalette.NeonCyan, onClick = onText),
            CtxToolItem("Stickers", Icons.Default.StickyNote2, onClick = onStickers),
            CtxToolItem("Effects", Icons.Default.AutoAwesome, onClick = onEffects),
            CtxToolItem("Filters", Icons.Default.FilterVintage, onClick = onFilters),
            CtxToolItem("AR Face", Icons.Default.FaceRetouchingNatural, onClick = onArFace),
            CtxToolItem("Adjust", Icons.Default.Tune, iconRotationDeg = 90f, onClick = onAdjust)
        )
        ToolbarSelectionKind.VIDEO -> listOf(
            // Keyframe tab: opens the keyframe editor (diamonds + curves)
            // so keyframes can be added/edited immediately on selection.
            // Mockup: diamond-outline icon, cyan; every tool icon+label is cyan.
            CtxToolItem("Keyframe", DiamondOutlineIcon, tint = ApexPalette.NeonCyan, onClick = onAnimation),
            CtxToolItem("Adjust", Icons.Default.Tune, tint = ApexPalette.NeonCyan, onClick = onAdjust),
            CtxToolItem("Replace", Icons.Default.Autorenew, tint = ApexPalette.NeonCyan, onClick = onReplace),
            CtxToolItem("Speed", Icons.Default.Speed, tint = ApexPalette.NeonCyan, onClick = onSpeed),
            // Second entry point into the same keyframe/animation panel.
            CtxToolItem("Animation", Icons.Default.AutoAwesome, tint = ApexPalette.NeonCyan, onClick = onAnimation),
            CtxToolItem("Delete", Icons.Default.DeleteOutline, tint = ApexPalette.NeonCyan, onClick = onDelete)
        )
        ToolbarSelectionKind.AUDIO -> listOf(
            CtxToolItem("Fade", Icons.Default.Tune, onClick = onFade),
            CtxToolItem("Replace", Icons.Default.SwapHoriz, onClick = onReplace),
            CtxToolItem(
                if (beatsAnalyzing) "Beats…" else "Beats",
                Icons.Default.GraphicEq,
                if (beatsActive) ApexPalette.NeonCyan else Color(0xFF9CA3AF),
                onBeats
            ),
            CtxToolItem("Volume", Icons.Default.VolumeUp, onClick = onVolume),
            CtxToolItem("Delete", Icons.Default.DeleteOutline, ApexPalette.NeonPink, onDelete)
        )
        // Photo clip selected → real photo-editing tools (PR F).
        ToolbarSelectionKind.PHOTO -> listOf(
            CtxToolItem("Crop", Icons.Default.Crop, ApexPalette.NeonCyan, onPhotoCrop),
            CtxToolItem("Adjust", Icons.Default.Tune, onClick = onPhotoAdjust),
            CtxToolItem("Filters", Icons.Default.AutoAwesome, onClick = onPhotoFilters),
            CtxToolItem("Rotate", Icons.Default.RotateRight, onClick = onPhotoRotate),
            CtxToolItem("Delete", Icons.Default.DeleteOutline, ApexPalette.NeonPink, onDelete)
        )
    }

    // Mockup: the toolbar sits in a dark rounded container; when a video
    // clip is selected a cyan "Video clip selected" label with a cyan
    // divider sits above it.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0C0C14))
    ) {
        if (selectionKind == ToolbarSelectionKind.VIDEO) {
            Text(
                text = "Video clip selected",
                color = ApexPalette.NeonCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 8.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ApexPalette.NeonCyan)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(ApexPalette.BgElevated)
                .height(58.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
        // CapCut-style collapse chevron when a clip/track is selected.
        if (selectionKind != ToolbarSelectionKind.NONE) {
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .fillMaxHeight()
                    .clickable(onClick = onCollapse),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Deselect",
                    tint = Color(0xFF9CA3AF),
                    modifier = Modifier.size(22.dp)
                )
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight(0.6f)
                    .background(Color(0xFF1F1F2E))
            )
        }
        items.forEach { item ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .width(64.dp)
                    .fillMaxHeight()
                    .clickable(onClick = item.onClick)
            ) {
                if (item.glyph != null) {
                    // Mockup text glyph (e.g. the bold "T" for the Text tab).
                    Text(
                        text = item.glyph,
                        color = item.tint,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.size(21.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                } else {
                    Icon(
                        imageVector = item.icon!!,
                        contentDescription = item.label,
                        tint = item.tint,
                        modifier = Modifier
                            .size(21.dp)
                            .graphicsLayer { rotationZ = item.iconRotationDeg }
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.label,
                    color = item.tint,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        }
    }
}

/**
 * Volume bottom sheet for the contextual toolbar.
 * Video clip: 0..200% per-clip gain (live preview + export).
 * Audio track: 0..100% track gain (preview + export).
 */
@Composable
fun ClipVolumeSheet(
    title: String,
    volume: Float,
    maxVolume: Float = 2f,
    onVolumeChange: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(ApexPalette.BgElevated)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = ApexPalette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = "${(volume / maxVolume * 100).toInt()}%",
                color = ApexPalette.NeonCyan,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.VolumeDown, contentDescription = null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(20.dp))
            Slider(
                value = volume,
                onValueChange = onVolumeChange,
                valueRange = 0f..maxVolume,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = ApexPalette.NeonCyan,
                    activeTrackColor = ApexPalette.NeonCyan,
                    inactiveTrackColor = Color(0xFF2A2A3C)
                )
            )
            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Done",
            color = ApexPalette.NeonCyan,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.End)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClose)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/**
 * Fade in / fade out sheet for a selected audio track (CapCut "Fade" tool).
 */
@Composable
fun AudioFadeSheet(
    track: AudioTrack,
    onFadeInChange: (Long) -> Unit,
    onFadeOutChange: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(ApexPalette.BgElevated)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text("Fade — ${track.name}", color = ApexPalette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        FadeSliderRow(
            label = "Fade in",
            valueMs = track.fadeInMs,
            maxMs = 10_000L,
            onChange = onFadeInChange
        )
        Spacer(Modifier.height(8.dp))
        FadeSliderRow(
            label = "Fade out",
            valueMs = track.fadeOutMs,
            maxMs = 10_000L,
            onChange = onFadeOutChange
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Done",
            color = ApexPalette.NeonCyan,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.End)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClose)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun FadeSliderRow(
    label: String,
    valueMs: Long,
    maxMs: Long,
    onChange: (Long) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color(0xFF9CA3AF), fontSize = 12.sp, modifier = Modifier.width(64.dp))
        Slider(
            value = valueMs.toFloat(),
            onValueChange = { onChange(it.toLong()) },
            valueRange = 0f..maxMs.toFloat(),
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = ApexPalette.NeonCyan,
                activeTrackColor = ApexPalette.NeonCyan,
                inactiveTrackColor = Color(0xFF2A2A3C)
            )
        )
        Text(
            text = "${valueMs / 1000}.${(valueMs % 1000) / 100}s",
            color = ApexPalette.NeonCyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(52.dp)
        )
    }
}

/**
 * Extracted bottom-toolbar section for EditorScreen (keeps the EditorScreen
 * composable under the JVM 64KB method limit). Always renders the
 * New contextual toolbar.
 */
@Composable
fun EditorBottomToolbarSection(
    state: EditorState,
    vm: com.apexstudio.app.presentation.viewmodel.EditorViewModel,
    mediaPicker: com.apexstudio.app.data.picker.MediaPickerHelper
) {
    val toolbarKind = resolveToolbarSelection(state)
    val selectedAudioTrack =
        state.project?.audioTracks?.firstOrNull { it.id == state.selectedAudioTrackId }
    ContextualBottomToolbar(
        selectionKind = toolbarKind,
        onText = { vm.openTextPanel() },
        onEffects = { vm.openFxPanel() },
        onStickers = { vm.openStickerPanel() },
        onArFace = { vm.openArFilterPanel() },
        onFilters = { vm.openFilterPanel() },
        onAdjust = { vm.openAdjustmentsPanel() },
        onReplace = {
            when (toolbarKind) {
                ToolbarSelectionKind.VIDEO -> {
                    val clip = state.project?.clips?.firstOrNull { it.id == state.selectedClipId }
                    if (clip != null) {
                        vm.setPendingReplaceClip(clip.id)
                        val request = if (clip.type == ClipType.OVERLAY) {
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageAndVideo
                            )
                        } else {
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.VideoOnly
                            )
                        }
                        mediaPicker.pickMultipleMedia.launch(request)
                    }
                }
                ToolbarSelectionKind.AUDIO -> {
                    val audioClip = state.project?.clips?.firstOrNull {
                        it.id == state.selectedClipId && (it.type == ClipType.AUDIO || it.type == ClipType.SFX)
                    }
                    when {
                        audioClip != null -> {
                            vm.setPendingReplaceClip(audioClip.id)
                            mediaPicker.pickAudioMedia.launch("audio/*")
                        }
                        selectedAudioTrack != null -> {
                            vm.setPendingReplaceAudioTrack(selectedAudioTrack.id)
                            mediaPicker.pickAudioMedia.launch("audio/*")
                        }
                    }
                }
                ToolbarSelectionKind.NONE -> {}
                // Photo clip → replace with another still image.
                ToolbarSelectionKind.PHOTO -> {
                    val clip = state.project?.clips?.firstOrNull { it.id == state.selectedClipId }
                    if (clip != null) {
                        vm.setPendingReplaceClip(clip.id)
                        mediaPicker.pickMultipleMedia.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    }
                }
            }
        },
        onSpeed = { vm.openSpeedPanel() },
        onVolume = { vm.openClipVolumeSheet() },
        onAnimation = { vm.setKeyframePanelOpen(true) },
        onDelete = {
            when (toolbarKind) {
                ToolbarSelectionKind.VIDEO ->
                    state.selectedClipId?.let { vm.deleteClip(it); vm.clearSelection() }
                ToolbarSelectionKind.PHOTO ->
                    state.selectedClipId?.let { vm.deleteClip(it); vm.clearSelection() }
                ToolbarSelectionKind.AUDIO -> {
                    val audioClip = state.project?.clips?.firstOrNull {
                        it.id == state.selectedClipId && (it.type == ClipType.AUDIO || it.type == ClipType.SFX)
                    }
                    when {
                        audioClip != null -> { vm.deleteClip(audioClip.id); vm.clearSelection() }
                        selectedAudioTrack != null -> {
                            vm.removeAudioTrack(selectedAudioTrack.id); vm.clearSelection()
                        }
                    }
                }
                ToolbarSelectionKind.NONE -> {}
            }
        },
        onFade = { vm.openAudioFadeSheet() },
        onBeats = {
            selectedAudioTrack?.let { vm.toggleBeatsForTrack(it.id) }
        },
        beatsActive = state.beatSourceTrackId != null && state.beatMarkersMs.isNotEmpty(),
        beatsAnalyzing = state.beatsAnalyzing,
        // Photo-editing tools (PR F): open the Edit Photo sheet on the
        // requested tab so the tool appears the moment a photo clip is
        // selected.
        onPhotoCrop = { vm.openPhotoEditPanel(com.apexstudio.app.presentation.state.PhotoEditTab.CROP) },
        onPhotoAdjust = { vm.openPhotoEditPanel(com.apexstudio.app.presentation.state.PhotoEditTab.ADJUST) },
        onPhotoFilters = { vm.openPhotoEditPanel(com.apexstudio.app.presentation.state.PhotoEditTab.FILTERS) },
        onPhotoRotate = { vm.openPhotoEditPanel(com.apexstudio.app.presentation.state.PhotoEditTab.ROTATE) },
        onCollapse = { vm.clearSelection() }
    )
}

/**
 * Volume bottom-sheet overlay (extracted from EditorScreen).
 */
@Composable
fun ClipVolumeSheetOverlay(
    state: EditorState,
    vm: com.apexstudio.app.presentation.viewmodel.EditorViewModel
) {
    if (!state.clipVolumeSheetOpen) return
    val toolbarKind = resolveToolbarSelection(state)
    val volClip = state.project?.clips?.firstOrNull { it.id == state.selectedClipId }
    val volTrack = state.project?.audioTracks?.firstOrNull { it.id == state.selectedAudioTrackId }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable { vm.closeClipVolumeSheet() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
            when {
                toolbarKind == ToolbarSelectionKind.VIDEO && volClip != null -> {
                    ClipVolumeSheet(
                        title = "Volume — ${volClip.name}",
                        volume = volClip.volume,
                        maxVolume = 2f,
                        onVolumeChange = { vm.setClipVolume(volClip.id, it) },
                        onClose = { vm.closeClipVolumeSheet() }
                    )
                }
                toolbarKind == ToolbarSelectionKind.AUDIO && volTrack != null -> {
                    ClipVolumeSheet(
                        title = "Volume — ${volTrack.name}",
                        volume = volTrack.volume,
                        maxVolume = 1f,
                        onVolumeChange = { vm.setAudioTrackVolumeFull(volTrack.id, it) },
                        onClose = { vm.closeClipVolumeSheet() }
                    )
                }
                else -> {
                    androidx.compose.runtime.LaunchedEffect(Unit) { vm.closeClipVolumeSheet() }
                }
            }
        }
    }
}

/**
 * Audio fade bottom-sheet overlay (extracted from EditorScreen).
 */
@Composable
fun AudioFadeSheetOverlay(
    state: EditorState,
    vm: com.apexstudio.app.presentation.viewmodel.EditorViewModel
) {
    if (!state.audioFadeSheetOpen) return
    val fadeTrack = state.project?.audioTracks?.firstOrNull { it.id == state.selectedAudioTrackId }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable { vm.closeAudioFadeSheet() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
            if (fadeTrack != null) {
                AudioFadeSheet(
                    track = fadeTrack,
                    onFadeInChange = { vm.setAudioTrackFadeIn(fadeTrack.id, it) },
                    onFadeOutChange = { vm.setAudioTrackFadeOut(fadeTrack.id, it) },
                    onClose = { vm.closeAudioFadeSheet() }
                )
            } else {
                androidx.compose.runtime.LaunchedEffect(Unit) { vm.closeAudioFadeSheet() }
            }
        }
    }
}
