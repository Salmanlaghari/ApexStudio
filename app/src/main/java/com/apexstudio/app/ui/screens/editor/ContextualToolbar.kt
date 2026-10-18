package com.apexstudio.app.ui.screens.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.apexstudio.app.domain.model.DenoiseStrength
import com.apexstudio.app.ui.theme.ApexPalette

/**
 * CapCut-style contextual bottom toolbar.
 *
 * The tool set follows the current selection, exactly like CapCut:
 * - Nothing selected → global tools (Text, Stickers, Effects, Filters, AR Face, Adjust)
 * - Video clip selected → clip tools (Keyframe, Adjust, Replace, Speed, Animation, Delete)
 * - Audio selected → audio tools (Fade, Replace, Beats, Volume, Delete)
 *
 * A collapse chevron as the first item deselects and returns to global tools.
 */
enum class ToolbarSelectionKind { NONE, VIDEO, AUDIO, PHOTO, TEXT, STICKER }

fun resolveToolbarSelection(state: EditorState): ToolbarSelectionKind {
    val project = state.project ?: return ToolbarSelectionKind.NONE
    // Text / sticker selections are the most specific: a text overlay or
    // sticker sits on top of a clip, and the clip stays selected too, so
    // these win over the clip's own kind.
    if (state.selectedTextOverlayId != null) return ToolbarSelectionKind.TEXT
    if (state.selectedStickerId != null) return ToolbarSelectionKind.STICKER
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
    // Global tools
    onText: () -> Unit = {},
    onEffects: () -> Unit = {},
    onStickers: () -> Unit = {},
    // Global tools: AR Face opens the native AR face-filter card carousel
    // (tap a card → effect applies, no camera preview opens); Lenses opens
    // the Snap Camera Kit live-camera lens browser; Filters opens
    // the GPU filter gallery directly.
    onArFace: () -> Unit = {},
    onLenses: () -> Unit = {},
    onFilters: () -> Unit = {},
    // Global tools: Overlay opens the system photo/video picker and
    // adds the chosen media as an overlay (PiP) clip at the playhead.
    onOverlay: () -> Unit = {},
    // P4: two explicit overlay add actions instead of one combined
    // chooser — image and video are separate buttons, same PiP route.
    onAddOverlayImage: () -> Unit = {},
    onAddOverlayVideo: () -> Unit = {},
    // Video-clip tools
    onAdjust: () -> Unit = {},
    onReplace: () -> Unit = {},
    onSpeed: () -> Unit = {},
    onVolume: () -> Unit = {},
    onAnimation: () -> Unit = {},
    onDelete: () -> Unit = {},
    // PR-G: per-clip Background replace (MediaPipe person segmentation).
    onBackground: () -> Unit = {},
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
    // Phase 3 tools
    onMusic: () -> Unit = {},
    onAutoClip: () -> Unit = {},
    onHistory: () -> Unit = {},
    onEditPack: () -> Unit = {},
    onScopes: () -> Unit = {},
    onBgRemover: () -> Unit = {},
    // Collapse (deselect)
    onCollapse: () -> Unit = {},
    // Merged clip-bar actions (previously the timeline quick-actions row
    // inside TimelineTrackArea — merged here so there is exactly one
    // clip bar, with no duplicated Split / Speed / Animation entries).
    onSplit: () -> Unit = {},
    onTrim: () -> Unit = {},
    onMask: () -> Unit = {},
    onDuplicate: () -> Unit = {},
    // Keyframe: toggles a keyframe at the playhead. Distinct from
    // onAnimation (opens the keyframe/animation panel) — the old bar had
    // both Keyframe and Animation opening the same panel.
    onToggleKeyframe: () -> Unit = {},
    // Overlay properties: opens the overlay (PiP) properties panel
    // (opacity / blend / mask / border / shadow / flip / volume /
    // track / keyframes). Only shown when the selected clip is an
    // overlay — see [isOverlaySelected].
    onOverlayProps: () -> Unit = {},
    // True when the selected clip is an overlay (PiP) clip. The VIDEO
    // clip bar then also shows the "Properties" entry.
    isOverlaySelected: Boolean = false,
    // Audio-bar actions (previously the AUDIO_A1 quick-actions row).
    onAudioMix: () -> Unit = {},
    onRecord: () -> Unit = {},
    onRoyaltyMusic: () -> Unit = {},
    // Text / sticker-bar actions (previously the TEXT_TXT / STICKER_STK
    // quick-actions rows).
    onEditText: () -> Unit = {},
    onToggleTextKeyframe: () -> Unit = {},
    onDeleteText: () -> Unit = {},
    onDeleteSticker: () -> Unit = {},
    // While playing, only the main tool bar shows (clip bar hides).
    isPlaying: Boolean = false,
    // Transition (main bar): opens the V1 junction nearest the
    // playhead. Moved here from the old timeline tool row — the
    // function must stay reachable after the duplicate row is gone.
    onTransition: () -> Unit = {},
    // Overlay-layer hide/lock (VIDEO clip bar, overlay selected):
    // relocated from the removed left sidebar pills. The selected
    // overlay clip's layer (trackIndex 1..9) is toggled.
    onToggleLayerVisibility: () -> Unit = {},
    onToggleLayerLock: () -> Unit = {},
    isLayerHidden: Boolean = false,
    isLayerLocked: Boolean = false,
    // Audio-track mute (AUDIO bar): relocated from the removed left
    // sidebar pills. Toggles the selected track's isMuted.
    onToggleTrackMute: () -> Unit = {},
    isTrackMuted: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Main tool bar: always visible (also the only bar when nothing is
    // selected, or while playing).
    val mainItems: List<CtxToolItem> = listOf(
            // Mockup bottom tabs, exact order/labels/icons: Text (active cyan),
            // Stickers, Effects, Filters, AR Face, Adjust.
            CtxToolItem("Text", glyph = "T", tint = ApexPalette.NeonCyan, onClick = onText),
            CtxToolItem("Stickers", Icons.Default.StickyNote2, onClick = onStickers),
            CtxToolItem("Effects", Icons.Default.AutoAwesome, onClick = onEffects),
            CtxToolItem("Filters", Icons.Default.FilterVintage, onClick = onFilters),
            CtxToolItem("AR Face", Icons.Default.FaceRetouchingNatural, onClick = onArFace),
            CtxToolItem("Lenses", Icons.Default.Face, onClick = onLenses),
            // Bug 4: Mask is a TOP-LEVEL tool, reachable straight from the
            // main bottom bar rather than buried under the selected-clip
            // bar. With nothing selected, openMaskPanelTopLevel() targets
            // the first video layer so the tool is never a dead button.
            CtxToolItem("Mask", Icons.Default.Crop, tint = ApexPalette.NeonCyan, onClick = onMask),
            // Overlay: system photo/video picker → PiP clip at the
            // playhead (images 5s, videos source length).
            // P4: separate buttons for PNG/JPEG overlays and video
            // overlays (both land on the V2..V9 PiP layers).
            CtxToolItem("Add Image", Icons.Default.Image, onClick = onAddOverlayImage),
            CtxToolItem("Add Video", Icons.Default.VideoLibrary, onClick = onAddOverlayVideo),
            // Transition: nearest V1 junction to the playhead
            // (relocated from the timeline tool row).
            CtxToolItem("Transition", Icons.Default.Transform, onClick = onTransition),
            CtxToolItem("Adjust", Icons.Default.Tune, iconRotationDeg = 90f, onClick = onAdjust),
            // Phase 3: pack panels + Auto Clip + Music + History.
            CtxToolItem("Edit Pack", Icons.Default.ContentCut, onClick = onEditPack),
            CtxToolItem("Scopes", Icons.Default.Insights, onClick = onScopes),
            CtxToolItem("BG Remove", Icons.Default.AutoFixHigh, onClick = onBgRemover),
            CtxToolItem("Auto Clip", Icons.Default.Bolt, tint = ApexPalette.NeonPink, onClick = onAutoClip),
            CtxToolItem("Music", Icons.Default.MusicNote, onClick = onMusic),
            CtxToolItem("History", Icons.Default.History, onClick = onHistory)
    )

    // Clip action bar: the merged union of the old contextual bar and the
    // old timeline quick-actions row — one entry per action, no duplicates.
    val clipItems: List<CtxToolItem> = when (selectionKind) {
        ToolbarSelectionKind.VIDEO -> listOf(
            CtxToolItem("Split", Icons.Default.ContentCut, tint = ApexPalette.NeonCyan, onClick = onSplit),
            CtxToolItem("Trim", Icons.Default.Tune, tint = ApexPalette.NeonCyan, onClick = onTrim),
            CtxToolItem("Speed", Icons.Default.Speed, tint = ApexPalette.NeonCyan, onClick = onSpeed),
            CtxToolItem("Volume", Icons.Default.VolumeUp, tint = ApexPalette.NeonCyan, onClick = onVolume),
            // Keyframe toggles a keyframe at the playhead (distinct action);
            // Animation opens the keyframe/animation panel.
            CtxToolItem("Keyframe", DiamondOutlineIcon, tint = ApexPalette.NeonCyan, onClick = onToggleKeyframe),
            CtxToolItem("Animation", Icons.Default.Layers, tint = ApexPalette.NeonCyan, onClick = onAnimation),
            // Phase 2: Filters on video clips too — filters were unreachable
            // when a video clip was selected (main use case).
            CtxToolItem("Filters", Icons.Default.FilterVintage, tint = ApexPalette.NeonCyan, onClick = onFilters),
            CtxToolItem("Adjust", Icons.Default.Tune, tint = ApexPalette.NeonCyan, onClick = onAdjust),
            CtxToolItem("Effects", Icons.Default.AutoAwesome, tint = ApexPalette.NeonCyan, onClick = onEffects),
            CtxToolItem("Mask", Icons.Default.Crop, tint = ApexPalette.NeonCyan, onClick = onMask),
            // PR-G: per-clip Background replace (MediaPipe person
            // segmentation) — blur / colour / image behind the person.
            CtxToolItem("Background", Icons.Default.Person, tint = ApexPalette.NeonCyan, onClick = onBackground),
            CtxToolItem("Replace", Icons.Default.Autorenew, tint = ApexPalette.NeonCyan, onClick = onReplace),
            CtxToolItem("Duplicate", Icons.Default.ContentCopy, tint = ApexPalette.NeonCyan, onClick = onDuplicate),
            CtxToolItem("Delete", Icons.Default.DeleteOutline, tint = ApexPalette.NeonPink, onClick = onDelete)
        ).let { items ->
            // Overlay selected: the properties panel (opacity / blend /
            // mask / border / shadow / flip / volume / track /
            // keyframes) is the overlay's main editing surface — plus
            // Hide/Show and Lock/Unlock, relocated from the removed
            // left sidebar pills (they act on the clip's layer).
            if (isOverlaySelected) {
                items + CtxToolItem(
                    "Properties", Icons.Default.Tune,
                    tint = ApexPalette.NeonCyan, onClick = onOverlayProps
                ) + CtxToolItem(
                    if (isLayerHidden) "Show" else "Hide",
                    if (isLayerHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    tint = ApexPalette.NeonCyan, onClick = onToggleLayerVisibility
                ) + CtxToolItem(
                    if (isLayerLocked) "Unlock" else "Lock",
                    if (isLayerLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                    tint = ApexPalette.NeonCyan, onClick = onToggleLayerLock
                )
            } else items
        }
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
            CtxToolItem("Audio Mix", Icons.Default.LibraryMusic, onClick = onAudioMix),
            CtxToolItem("Royalty Music", Icons.Default.MusicNote, onClick = onRoyaltyMusic),
            CtxToolItem("Record", Icons.Default.Mic, onClick = onRecord),
            // Mute/Unmute for the selected track, relocated from the
            // removed left sidebar pills.
            CtxToolItem(
                if (isTrackMuted) "Unmute" else "Mute",
                if (isTrackMuted) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                onClick = onToggleTrackMute
            ),
            CtxToolItem("Delete", Icons.Default.DeleteOutline, ApexPalette.NeonPink, onDelete)
        )
        // Photo clip selected → real photo-editing tools (PR F).
        ToolbarSelectionKind.PHOTO -> listOf(
            CtxToolItem("Crop", Icons.Default.Crop, ApexPalette.NeonCyan, onPhotoCrop),
            CtxToolItem("Split", Icons.Default.ContentCut, ApexPalette.NeonCyan, onSplit),
            CtxToolItem("Trim", Icons.Default.Edit, ApexPalette.NeonCyan, onTrim),
            CtxToolItem("Duplicate", Icons.Default.ContentCopy, ApexPalette.NeonCyan, onDuplicate),
            CtxToolItem("Adjust", Icons.Default.Tune, onClick = onPhotoAdjust),
            CtxToolItem("Filters", Icons.Default.AutoAwesome, onClick = onPhotoFilters),
            CtxToolItem("Rotate", Icons.Default.RotateRight, onClick = onPhotoRotate),
            CtxToolItem("Delete", Icons.Default.DeleteOutline, ApexPalette.NeonPink, onDelete)
        )
        // Text overlay selected → text tools (previously TEXT_TXT
        // quick-actions row).
        ToolbarSelectionKind.TEXT -> listOf(
            CtxToolItem("Edit Text", Icons.Default.TextFields, tint = Color(0xFFA855F7), onClick = onEditText),
            CtxToolItem("Styles", Icons.Default.Palette, tint = Color(0xFFA855F7), onClick = onEditText),
            CtxToolItem("Keyframe", DiamondOutlineIcon, tint = Color(0xFFFBBF24), onClick = onToggleTextKeyframe),
            CtxToolItem("Delete", Icons.Default.DeleteOutline, tint = ApexPalette.NeonPink, onClick = onDeleteText)
        )
        // Sticker selected → sticker tools (previously STICKER_STK
        // quick-actions row).
        ToolbarSelectionKind.STICKER -> listOf(
            CtxToolItem("Edit", Icons.Default.EmojiEmotions, tint = Color(0xFFEC4899), onClick = onStickers),
            CtxToolItem("Delete", Icons.Default.DeleteOutline, tint = ApexPalette.NeonPink, onClick = onDeleteSticker)
        )
        ToolbarSelectionKind.NONE -> emptyList()
    }

    // Two stacked bars:
    // - Clip action bar (merged, one entry per action): only while a
    //   clip/overlay/track is selected AND the editor is not playing.
    //   Tapping a clip reveals it with a ~200ms slide/fade; tapping empty
    //   timeline space (which clears the selection) or starting playback
    //   hides it again. The old "Video clip selected" label row is gone —
    //   the selection name shows as a small chip in the preview instead.
    // - Main tool bar: always visible.
    // The freed height (old quick-actions row + label row) goes to the
    // timeline: this toolbar never adds padding/spacers of its own.
    val showClipBar = selectionKind != ToolbarSelectionKind.NONE && !isPlaying
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0C0C14))
    ) {
        AnimatedVisibility(
            visible = showClipBar,
            enter = fadeIn(animationSpec = tween(200)) +
                slideInVertically(animationSpec = tween(200)) { it },
            exit = fadeOut(animationSpec = tween(200)) +
                slideOutVertically(animationSpec = tween(200)) { it }
        ) {
            ToolbarBarRow(
                items = clipItems,
                leadingContent = {
                    // CapCut-style collapse chevron: deselects, hiding
                    // this bar again.
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
                    // No filename chip here: it crowded the clip
                    // bar's actions. The selected clip/track name now
                    // shows as a small chip in the preview (bottom-left),
                    // where it crowds nothing.
                }
            )
        }
        ToolbarBarRow(items = mainItems)
    }
}

/**
 * One horizontally scrollable toolbar bar (58dp, rounded, dark).
 * [leadingContent] (collapse chevron + selection chip) stays fixed while
 * the tool items scroll.
 */
@Composable
private fun ToolbarBarRow(
    items: List<CtxToolItem>,
    modifier: Modifier = Modifier,
    leadingContent: @Composable (RowScope.() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(ApexPalette.BgElevated)
            .height(58.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingContent != null) {
                leadingContent()
            }
            // Scrollable items: scrolling content padding + edge
            // fades so the first/last tool is never half-cut at rest.
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.width(12.dp))
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
                    Spacer(Modifier.width(12.dp))
                }
                // Edge fades (no touch handling — gestures pass
                // through to the scrollable Row below).
                Box(
                    Modifier.align(Alignment.CenterStart)
                        .width(20.dp).fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                listOf(ApexPalette.BgElevated, Color.Transparent)
                            )
                        )
                )
                Box(
                    Modifier.align(Alignment.CenterEnd)
                        .width(20.dp).fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, ApexPalette.BgElevated)
                            )
                        )
                )
            }
        }
    }
}

/**
 * Volume bottom sheet for the contextual toolbar.
 * Video clip: 0..200% per-clip gain (live preview + export).
 * Audio track: 0..100% track gain (preview + export).
 *
 * Video clips also get the Clean Voice section (null [denoiseEnabled]
 * hides it — used for audio tracks, whose MediaPlayer-based playback
 * cannot host a Media3 AudioProcessor).
 */
@Composable
fun ClipVolumeSheet(
    title: String,
    volume: Float,
    maxVolume: Float = 2f,
    onVolumeChange: (Float) -> Unit,
    onClose: () -> Unit,
    denoiseEnabled: Boolean? = null,
    denoiseStrength: DenoiseStrength = DenoiseStrength.MEDIUM,
    onDenoiseChange: ((Boolean, DenoiseStrength) -> Unit)? = null,
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
        // Clean Voice (video clips only): one-tap voice noise cleanup.
        // Same processing in preview and export; toggle is instant
        // (real-time processor, no offline job to cancel).
        if (denoiseEnabled != null && onDenoiseChange != null) {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Clean Voice",
                        color = ApexPalette.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Removes hiss & hum from this clip's voice",
                        color = Color(0xFF9CA3AF),
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = denoiseEnabled,
                    onCheckedChange = { onDenoiseChange(it, denoiseStrength) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ApexPalette.NeonCyan,
                        uncheckedThumbColor = Color(0xFF9CA3AF),
                        uncheckedTrackColor = Color(0xFF2A2A3C)
                    )
                )
            }
            if (denoiseEnabled) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Plain for-loop (not forEach) so Modifier.weight
                    // still sees the RowScope receiver.
                    for ((strength, label) in listOf(
                        DenoiseStrength.LIGHT to "Light",
                        DenoiseStrength.MEDIUM to "Medium",
                        DenoiseStrength.STRONG to "Strong"
                    )) {
                        val selected = strength == denoiseStrength
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) ApexPalette.NeonCyan.copy(alpha = 0.2f)
                                    else Color(0xFF2A2A3C)
                                )
                                .clickable {
                                    // Chips only render while denoise is
                                    // on (see the if above), so this keeps
                                    // the current enabled state — tapping
                                    // a strength never toggles on its own.
                                    onDenoiseChange(denoiseEnabled, strength)
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (selected) ApexPalette.NeonCyan else Color(0xFF9CA3AF),
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Strong removes more noise but can thin very quiet speech.",
                    color = Color(0xFF9CA3AF),
                    fontSize = 11.sp
                )
            }
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
    // Owner clip of the selected text overlay (for text keyframe/delete).
    val selectedTextOwnerClip = state.project?.clips?.firstOrNull { clip ->
        clip.textOverlays.any { it.id == state.selectedTextOverlayId }
    }
    // Overlay-layer hide/lock for the clip bar (relocated from the
    // removed left sidebar pills). Only when an overlay clip on
    // layer 1..9 is selected; V1 is the base layer and is never
    // hidden/locked.
    val selectedClip =
        state.project?.clips?.firstOrNull { it.id == state.selectedClipId }
    val selectedOverlayLayer =
        selectedClip?.takeIf { it.type == ClipType.OVERLAY }?.trackIndex
            ?.takeIf { it in 1..9 }
    ContextualBottomToolbar(
        selectionKind = toolbarKind,
        isPlaying = state.isPlaying,
        // Transition: nearest V1 junction to the playhead (relocated
        // from the removed timeline tool row).
        onTransition = {
            val v1Clips = state.project?.clips?.filter {
                (it.type == ClipType.VIDEO || it.type == ClipType.IMAGE) && it.trackIndex == 0
            } ?: emptyList()
            if (v1Clips.size >= 2) {
                var acc = 0L
                var best: Pair<String, String>? = null
                var bestDist = Long.MAX_VALUE
                for (i in 0 until v1Clips.size - 1) {
                    acc += v1Clips[i].effectiveDurationMs()
                    val d = kotlin.math.abs(acc - state.playerPositionMs)
                    if (d < bestDist) {
                        bestDist = d
                        best = v1Clips[i].id to v1Clips[i + 1].id
                    }
                }
                best?.let { (fromId, toId) -> vm.openTransitionPicker(fromId, toId) }
            }
        },
        onToggleLayerVisibility = {
            selectedOverlayLayer?.let { vm.toggleVideoLayerVisibility(it) }
        },
        onToggleLayerLock = {
            selectedOverlayLayer?.let { vm.toggleVideoLayerLock(it) }
        },
        isLayerHidden = selectedOverlayLayer?.let { it in state.hiddenVideoLayers } == true,
        isLayerLocked = selectedOverlayLayer?.let { it in state.lockedVideoLayers } == true,
        onToggleTrackMute = {
            selectedAudioTrack?.let { vm.toggleAudioTrackMute(it.id) }
        },
        isTrackMuted = selectedAudioTrack?.isMuted == true,
        onText = { vm.openTextPanel() },
        onEffects = { vm.openFxPanel() },
        onStickers = { vm.openStickerPanel() },
        onArFace = { vm.openArFilterPanel() },
        onLenses = { vm.openLensesPanel() },
        onFilters = { vm.openFilterPanel() },
        onAdjust = { vm.openAdjustmentsPanel() },
        // Overlay: system photo/video picker → overlay (PiP) clip at
        // the playhead. Same picker path as the V2-track "+" button.
        // P4: same overlay (PiP) route and pending flags as before —
        // only the MIME filter differs per button.
        onAddOverlayImage = {
            vm.setPendingAddToLayer(null)
            vm.setPendingAddAsOverlay(true)
            vm.setPendingAddAsAudio(false)
            mediaPicker.pickMultipleMedia.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                )
            )
        },
        onAddOverlayVideo = {
            vm.setPendingAddToLayer(null)
            vm.setPendingAddAsOverlay(true)
            vm.setPendingAddAsAudio(false)
            mediaPicker.pickMultipleMedia.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.VideoOnly
                )
            )
        },
        // Overlay properties panel (only shown in the clip bar when
        // the selected clip is an overlay — see isOverlaySelected).
        onOverlayProps = { vm.openOverlayPropsPanel() },
        isOverlaySelected = state.project?.clips?.firstOrNull {
            it.id == state.selectedClipId
        }?.type == ClipType.OVERLAY,
        // Phase 3 tools.
        onMusic = { vm.openMusicLibraryPanel() },
        onAutoClip = { vm.openAutoClipPanel() },
        onHistory = { vm.openHistoryDraftsPanel() },
        onEditPack = { vm.openEditPackPanel() },
        onScopes = { vm.openColorScopesPanel() },
        onBgRemover = { vm.openBgRemoverPanel() },
        // PR-G: per-clip Background replace panel (selected video clip).
        onBackground = { vm.openBackgroundPanel() },
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
                // Text / sticker bars have no Replace action.
                ToolbarSelectionKind.TEXT -> {}
                ToolbarSelectionKind.STICKER -> {}
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
                // Text / sticker Delete buttons use onDeleteText /
                // onDeleteSticker (separate params), not this handler.
                ToolbarSelectionKind.TEXT -> {}
                ToolbarSelectionKind.STICKER -> {}
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
        onCollapse = { vm.clearSelection() },
        // Merged clip-bar actions (previously the timeline quick-actions row).
        onSplit = {
            state.selectedClipId?.let { vm.splitClip(it, state.playerPositionMs) }
        },
        onTrim = { vm.openTrimPanel() },
        onMask = { vm.openMaskPanelTopLevel() },
        onDuplicate = { state.selectedClipId?.let { vm.duplicateClip(it) } },
        onToggleKeyframe = { vm.toggleKeyframeAtPlayhead() },
        onAudioMix = { vm.openAudioMixer() },
        onRecord = { vm.openVoiceRecorder() },
        onRoyaltyMusic = { vm.openRoyaltyMusicDialog() },
        onEditText = { vm.openTextPanel() },
        onToggleTextKeyframe = {
            val overlayId = state.selectedTextOverlayId
            if (overlayId != null && selectedTextOwnerClip != null) {
                vm.toggleTextKeyframeAtPlayhead(selectedTextOwnerClip.id, overlayId)
            }
        },
        onDeleteText = {
            val overlayId = state.selectedTextOverlayId
            if (overlayId != null && selectedTextOwnerClip != null) {
                vm.removeTextOverlay(selectedTextOwnerClip.id, overlayId)
            }
        },
        onDeleteSticker = { state.selectedStickerId?.let { vm.removeSticker(it) } }
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
                        onClose = { vm.closeClipVolumeSheet() },
                        denoiseEnabled = volClip.denoiseEnabled,
                        denoiseStrength = volClip.denoiseStrength,
                        onDenoiseChange = { enabled, strength ->
                            vm.setClipDenoise(volClip.id, enabled, strength)
                        }
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
