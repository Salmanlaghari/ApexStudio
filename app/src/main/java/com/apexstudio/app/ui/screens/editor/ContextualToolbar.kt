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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
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
import com.apexstudio.app.ui.theme.EditorPalette as ApexPalette

/**
 * CapCut-style contextual bottom toolbar.
 *
 * Selected-item actions appear in a scrollable row above the persistent global
 * dock (Media, Audio, Text, Overlay, Stickers, FX, Filters, More). Tapping More
 * expands a secondary scrollable row without hiding the global dock.
 *
 * A fixed collapse chevron deselects the current item.
 */
enum class ToolbarSelectionKind { NONE, VIDEO, AUDIO, PHOTO, TEXT, STICKER }

/** Approved, always-visible global tool order. Kept explicit for UI regression tests. */
internal val CONTEXTUAL_PRIMARY_TOOL_ORDER = listOf(
    "Media", "Audio", "Text", "Overlay", "Stickers", "FX", "Filters", "More"
)

/** Screenshot-priority actions shown first in the selected-video scroll row. */
internal val CONTEXTUAL_VIDEO_PRIORITY_TOOL_ORDER = listOf(
    "Split", "Trim", "Speed", "Volume", "Keyframe", "Animation"
)

/** Tools exposed by tapping More; clip-specific actions remain in the contextual clip row. */
internal val CONTEXTUAL_MORE_TOOL_ORDER = listOf(
    "Music", "SFX", "Enhance", "Color", "Templates", "Canvas",
    "Challenge", "Subtitles", "Next", "Draw", "AI", "AR Face",
    "Chroma Key", "Mask", "Add Image", "Add Video", "Transition", "Edit Pack",
    "Scopes", "BG Remove", "Auto Clip", "Device Music", "Audio Mix", "Voiceover", "History", "Signature", "Info"
)

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
    val tint: Color = Color(0xFF8C9AAF),
    val onClick: () -> Unit,
    val contentDescription: String? = null,
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
    onText: () -> Unit,
    onDraw: () -> Unit = {},
    onEffects: () -> Unit,
    onStickers: () -> Unit,
    // Global tools: AR Face opens the native AR face-filter card carousel
    // (tap a card → effect applies, no camera preview opens); Lenses opens
    // the Snap Camera Kit live-camera lens browser; Filters opens
    // the GPU filter gallery directly.
    onArFace: () -> Unit,
    onLenses: () -> Unit,
    onFilters: () -> Unit,
    // Global tools: Overlay opens the system photo/video picker and
    // adds the chosen media as an overlay (PiP) clip at the playhead.
    // P4: two explicit overlay add actions instead of one combined
    // chooser — image and video are separate buttons, same PiP route.
    onAddOverlayImage: () -> Unit,
    onAddOverlayVideo: () -> Unit,
    // Video-clip tools
    onAdjust: () -> Unit,
    onEnhance: () -> Unit = onAdjust,
    onReplace: () -> Unit,
    onSpeed: () -> Unit,
    onVolume: () -> Unit,
    onAnimation: () -> Unit,
    onDelete: () -> Unit,
    // PR-G: per-clip Background replace (MediaPipe person segmentation).
    onBackground: () -> Unit,
    // Audio tools
    onFade: () -> Unit,
    onBeats: () -> Unit,
    beatsActive: Boolean = false,
    beatsAnalyzing: Boolean = false,
    // Photo-clip tools (PR F) — shown when an IMAGE clip is selected.
    onPhotoCrop: () -> Unit,
    onPhotoAdjust: () -> Unit,
    onPhotoFilters: () -> Unit,
    onPhotoRotate: () -> Unit,
    // Phase 3 tools
    onMusic: () -> Unit,
    onSfxLibrary: () -> Unit = {},
    onAutoClip: () -> Unit,
    onHistory: () -> Unit,
    onSignature: () -> Unit = {},
    onEditPack: () -> Unit,
    onScopes: () -> Unit,
    onBgRemover: () -> Unit,
    // Collapse (deselect)
    onCollapse: () -> Unit,
    // Merged clip-bar actions (previously the timeline quick-actions row
    // inside TimelineTrackArea — merged here so there is exactly one
    // clip bar, with no duplicated Split / Speed / Animation entries).
    onSplit: () -> Unit,
    onTrim: () -> Unit,
    onMask: () -> Unit,
    onDuplicate: () -> Unit,
    // Keyframe: toggles a keyframe at the playhead. Distinct from
    // onAnimation (opens the keyframe/animation panel) — the old bar had
    // both Keyframe and Animation opening the same panel.
    onToggleKeyframe: () -> Unit,
    // Overlay properties: opens the overlay (PiP) properties panel
    // (opacity / blend / mask / border / shadow / flip / volume /
    // track / keyframes). Only shown when the selected clip is an
    // overlay — see [isOverlaySelected].
    onOverlayProps: () -> Unit,
    // True when the selected clip is an overlay (PiP) clip. The VIDEO
    // clip bar then also shows the "Properties" entry.
    isOverlaySelected: Boolean = false,
    // AUDIO can resolve from either an audio track or a legacy audio clip.
    // Track-only actions are unavailable for the latter.
    isLegacyAudioClipSelected: Boolean = false,
    // Retained for source compatibility; selection tools now follow the
    // historical selected-row behavior regardless of the former pager default.
    @Suppress("UNUSED_PARAMETER") showSelectionToolsInitially: Boolean = true,
    // Audio-bar actions (previously the AUDIO_A1 quick-actions row).
    onAudioMix: () -> Unit,
    onRecord: () -> Unit,
    onRoyaltyMusic: () -> Unit,
    // Text / sticker-bar actions (previously the TEXT_TXT / STICKER_STK
    // quick-actions rows).
    onEditText: () -> Unit,
    onToggleTextKeyframe: () -> Unit,
    onDeleteText: () -> Unit,
    onDeleteSticker: () -> Unit,
    // While playing, only the main tool bar shows (clip bar hides).
    isPlaying: Boolean = false,
    // Transition (main bar): opens the V1 junction nearest the
    // playhead. Moved here from the old timeline tool row — the
    // function must stay reachable after the duplicate row is gone.
    onTransition: () -> Unit,
    // Overlay-layer hide/lock (VIDEO clip bar, overlay selected):
    // relocated from the removed left sidebar pills. The selected
    // overlay clip's layer (trackIndex 1..9) is toggled.
    onToggleLayerVisibility: () -> Unit,
    onToggleLayerLock: () -> Unit,
    isLayerHidden: Boolean = false,
    isLayerLocked: Boolean = false,
    // Audio-track mute (AUDIO bar): relocated from the removed left
    // sidebar pills. Toggles the selected track's isMuted.
    onToggleTrackMute: () -> Unit,
    isTrackMuted: Boolean = false,
    onToggleTrackSolo: () -> Unit = {},
    isTrackSolo: Boolean = false,
    modifier: Modifier = Modifier,
    // New top-level entry points. Defaults preserve existing callers until
    // EditorScreen supplies its app-specific panel / media-picker routes.
    onCrop: () -> Unit = {},
    onAi: () -> Unit = {},
    onDeviceAudio: () -> Unit = {},
    onCamera: () -> Unit = {},
    onChromaKey: () -> Unit = {},
    // Subtitles opens the existing speech-recognition/captions panel. Kept
    // optional for source compatibility with standalone toolbar callers.
    onCaptions: () -> Unit = onText,
    onTemplates: () -> Unit = onEditPack,
    onChallenge: () -> Unit = onText,
    onNext: () -> Unit = {},
    // Opens the editor's normal media picker (adds primary media, not a PiP overlay).
    onMedia: () -> Unit = {},
    onOverlay: (() -> Unit)? = null,
    onColorWorkspace: () -> Unit = onAdjust,
    onBeforeToolAction: () -> Unit = {},
    onReferenceTrim: () -> Unit = onTrim,
    referenceToolMode: Boolean = false,
    referenceActiveTool: String? = null
) {
    var showOverlayChoices by remember { mutableStateOf(false) }
    var showToolbarNotes by remember { mutableStateOf(false) }
    var showMoreTools by remember { mutableStateOf(false) }
    LaunchedEffect(referenceToolMode) {
        if (referenceToolMode) showMoreTools = false
    }
    val hasSelection = selectionKind != ToolbarSelectionKind.NONE
    val mainItems: List<CtxToolItem> = listOf(
        CtxToolItem("Media", Icons.Outlined.VideoLibrary, tint = ApexPalette.TextPrimary, onClick = onMedia),
        CtxToolItem("Audio", Icons.Outlined.MusicNote, tint = ApexPalette.TextPrimary, onClick = onDeviceAudio),
        CtxToolItem("Text", Icons.Outlined.Title, tint = ApexPalette.TextPrimary, onClick = onText),
        CtxToolItem(
            "Overlay", Icons.Outlined.Collections, tint = ApexPalette.TextPrimary,
            onClick = { if (onOverlay != null) onOverlay() else showOverlayChoices = true }
        ),
        CtxToolItem("Stickers", Icons.Outlined.SentimentSatisfiedAlt, tint = ApexPalette.TextPrimary, onClick = onStickers),
        CtxToolItem("FX", Icons.Outlined.AutoAwesome, tint = ApexPalette.TextPrimary, onClick = onEffects),
        CtxToolItem("Filters", Icons.Outlined.FilterNone, tint = ApexPalette.TextPrimary, onClick = onFilters),
        CtxToolItem(
            "More", Icons.Outlined.MoreHoriz,
            tint = ApexPalette.TextPrimary,
            onClick = { showMoreTools = !showMoreTools }
        )
    )
    val referenceItems: List<CtxToolItem> = listOf(
        CtxToolItem("Trim", Icons.Default.ContentCut, tint = ApexPalette.NeonPurple, onClick = onReferenceTrim),
        CtxToolItem("Filters", Icons.Default.FilterVintage, tint = ApexPalette.NeonPurple, onClick = onFilters),
        CtxToolItem("Effects", Icons.Default.AutoAwesome, tint = ApexPalette.TextSecondary, onClick = onEffects),
        CtxToolItem("Text", Icons.Outlined.Title, tint = ApexPalette.TextSecondary, onClick = onText),
        CtxToolItem("Draw", Icons.Default.Brush, tint = ApexPalette.TextSecondary, onClick = onDraw)
    )

    // Supported tools formerly shown in the main scrolling row. Keep each wired
    // to its existing route; nothing is represented by a placeholder action.
    val secondaryItems: List<CtxToolItem> = listOf(
            CtxToolItem("Music", Icons.Default.MusicNote, onClick = onMusic),
            CtxToolItem("SFX", Icons.Default.GraphicEq, tint = ApexPalette.NeonPurple, onClick = onSfxLibrary),
            CtxToolItem("Enhance", Icons.Default.Tune, onClick = onEnhance),
            CtxToolItem("Color", Icons.Default.Palette, onClick = onColorWorkspace),
            CtxToolItem("Templates", Icons.Default.Collections, onClick = onTemplates),
            CtxToolItem("Canvas", Icons.Default.Crop, tint = ApexPalette.NeonCyan, onClick = onCrop),
            CtxToolItem("Challenge", Icons.Default.LocalFireDepartment, onClick = onChallenge),
            CtxToolItem("Subtitles", Icons.Default.Subtitles, onClick = onCaptions),
            CtxToolItem("Next", Icons.Default.Share, onClick = onNext),

            // Additional supported tools remain available after the quick set.
            CtxToolItem("Draw", Icons.Default.Brush, tint = ApexPalette.NeonPink, onClick = onDraw),
            CtxToolItem("AI", Icons.Default.AutoAwesome, tint = ApexPalette.NeonPink, onClick = onAi),
            CtxToolItem("AR Face", Icons.Default.FaceRetouchingNatural, onClick = onArFace),
            CtxToolItem("Chroma Key", Icons.Default.Colorize, onClick = onChromaKey),
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
            // Phase 3: pack panels + Auto Clip + Music + History.
            CtxToolItem("Edit Pack", Icons.Default.ContentCut, onClick = onEditPack),
            CtxToolItem("Scopes", Icons.Default.Insights, onClick = onScopes),
            CtxToolItem("BG Remove", Icons.Default.AutoFixHigh, onClick = onBgRemover),
            CtxToolItem("Auto Clip", Icons.Default.Bolt, tint = ApexPalette.NeonPink, onClick = onAutoClip),
            CtxToolItem("Device Music", Icons.Default.LibraryMusic, onClick = onDeviceAudio),
            CtxToolItem("Audio Mix", Icons.Default.GraphicEq, onClick = onAudioMix),
            CtxToolItem("Voiceover", Icons.Default.Mic, onClick = onRecord),
            CtxToolItem("History", Icons.Default.History, onClick = onHistory),
            CtxToolItem("Signature", Icons.Default.Verified, onClick = onSignature),
            CtxToolItem("Info", Icons.Outlined.Info, onClick = { showToolbarNotes = true }, contentDescription = "Editing and export notes")
    )
    check(mainItems.map { it.label } == CONTEXTUAL_PRIMARY_TOOL_ORDER)
    check(secondaryItems.map { it.label } == CONTEXTUAL_MORE_TOOL_ORDER)

    if (showOverlayChoices) {
        AlertDialog(
            onDismissRequest = { showOverlayChoices = false },
            title = { Text("Add overlay") },
            text = { Text("Choose an image or video to place over your project.") },
            confirmButton = {
                Text(
                    "Image",
                    modifier = Modifier.clickable {
                        showOverlayChoices = false
                        onAddOverlayImage()
                    }.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            },
            dismissButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Video",
                        modifier = Modifier.clickable {
                            showOverlayChoices = false
                            onAddOverlayVideo()
                        }.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                    Text(
                        "Cancel",
                        modifier = Modifier.clickable { showOverlayChoices = false }
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                    )
                }
            }
        )
    }

    // Clip action bar: the merged union of the old contextual bar and the
    // old timeline quick-actions row — one entry per action, no duplicates.
    val clipItems: List<CtxToolItem> = when (selectionKind) {
        ToolbarSelectionKind.VIDEO -> buildList {
            add(CtxToolItem("Split", Icons.Default.ContentCut, tint = ApexPalette.NeonCyan, onClick = onSplit))
            add(CtxToolItem("Trim", Icons.Default.Tune, tint = ApexPalette.NeonCyan, onClick = onTrim))
            // PiP playback reads speedPoints, whereas the generic Speed panel
            // writes speedMultiplier. Hide the misleading control for overlays.
            if (!isOverlaySelected) {
                add(CtxToolItem("Speed", Icons.Default.Speed, tint = ApexPalette.NeonCyan, onClick = onSpeed))
            }
            add(CtxToolItem(
                "Volume", Icons.Default.VolumeUp, tint = ApexPalette.NeonCyan,
                onClick = if (isOverlaySelected) onOverlayProps else onVolume
            ))
            // Keyframe toggles a keyframe at the playhead (distinct action);
            // Animation opens the keyframe/animation panel.
            add(CtxToolItem("Keyframe", DiamondOutlineIcon, tint = ApexPalette.NeonCyan, onClick = onToggleKeyframe))
            add(CtxToolItem("Animation", Icons.Default.Layers, tint = ApexPalette.NeonCyan, onClick = onAnimation))
            // Phase 2: Filters on video clips too — filters were unreachable
            // when a video clip was selected (main use case).
            add(CtxToolItem("Filters", Icons.Default.FilterVintage, tint = ApexPalette.NeonCyan, onClick = onFilters))
            add(CtxToolItem("Crop", Icons.Default.Crop, tint = ApexPalette.NeonCyan, onClick = onCrop))
            add(CtxToolItem("Adjust", Icons.Default.Tune, tint = ApexPalette.NeonCyan, onClick = onAdjust))
            add(CtxToolItem("Effects", Icons.Default.AutoAwesome, tint = ApexPalette.NeonCyan, onClick = onEffects))
            add(CtxToolItem("Mask", Icons.Default.Crop, tint = ApexPalette.NeonCyan, onClick = onMask))
            // PR-G: per-clip Background replace (MediaPipe person
            // segmentation) — blur / colour / image behind the person.
            add(CtxToolItem("Background", Icons.Default.Person, tint = ApexPalette.NeonCyan, onClick = onBackground))
            add(CtxToolItem("Replace", Icons.Default.Autorenew, tint = ApexPalette.NeonCyan, onClick = onReplace))
            add(CtxToolItem("Duplicate", Icons.Default.ContentCopy, tint = ApexPalette.NeonCyan, onClick = onDuplicate))
            add(CtxToolItem("Delete", Icons.Default.DeleteOutline, tint = ApexPalette.NeonPink, onClick = onDelete))
            // Overlay selected: the properties panel (opacity / blend /
            // mask / border / shadow / flip / volume / track /
            // keyframes) is the overlay's main editing surface — plus
            // Hide/Show and Lock/Unlock, relocated from the removed
            // left sidebar pills (they act on the clip's layer).
            if (isOverlaySelected) {
                add(CtxToolItem(
                    "Properties", Icons.Default.Tune,
                    tint = ApexPalette.NeonCyan, onClick = onOverlayProps
                ))
                add(CtxToolItem(
                    if (isLayerHidden) "Show" else "Hide",
                    if (isLayerHidden) Icons.Default.LayersClear else Icons.Default.Layers,
                    tint = ApexPalette.NeonCyan, onClick = onToggleLayerVisibility
                ))
                add(CtxToolItem(
                    if (isLayerLocked) "Unlock" else "Lock",
                    if (isLayerLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                    tint = ApexPalette.NeonCyan, onClick = onToggleLayerLock
                ))
            }
        }
        ToolbarSelectionKind.AUDIO -> buildList {
            add(CtxToolItem("Split", Icons.Default.ContentCut, tint = ApexPalette.NeonCyan, onClick = onSplit))
            add(CtxToolItem("Trim", Icons.Default.Tune, tint = ApexPalette.NeonCyan, onClick = onTrim))
            if (!isLegacyAudioClipSelected) add(CtxToolItem("Fade", Icons.Default.Tune, onClick = onFade))
            add(CtxToolItem("Replace", Icons.Default.SwapHoriz, onClick = onReplace))
            if (!isLegacyAudioClipSelected) {
                add(CtxToolItem(
                    if (beatsAnalyzing) "Beats…" else "Beats",
                    Icons.Default.GraphicEq,
                    if (beatsActive) ApexPalette.NeonCyan else Color(0xFF9CA3AF),
                    onBeats
                ))
                add(CtxToolItem("Volume", Icons.Default.VolumeUp, onClick = onVolume))
            }
            add(CtxToolItem("Audio Mix", Icons.Default.LibraryMusic, onClick = onAudioMix))
            add(CtxToolItem("Royalty Music", Icons.Default.MusicNote, onClick = onRoyaltyMusic))
            add(CtxToolItem("Sound FX", Icons.Default.GraphicEq, tint = ApexPalette.NeonPurple, onClick = onSfxLibrary))
            add(CtxToolItem("Device Music", Icons.Default.LibraryMusic, onClick = onDeviceAudio))
            add(CtxToolItem("Record", Icons.Default.Mic, onClick = onRecord))
            if (!isLegacyAudioClipSelected) {
                // Mute/Unmute affects project audio tracks only.
                add(CtxToolItem(
                    if (isTrackMuted) "Unmute" else "Mute",
                    if (isTrackMuted) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    onClick = onToggleTrackMute
                ))
                add(CtxToolItem(
                    if (isTrackSolo) "Unsolo" else "Solo",
                    Icons.Default.GraphicEq,
                    if (isTrackSolo) ApexPalette.NeonCyan else Color(0xFF9CA3AF),
                    onClick = onToggleTrackSolo
                ))
            }
            add(CtxToolItem("Delete", Icons.Default.DeleteOutline, ApexPalette.NeonPink, onDelete))
        }
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

    val selectionNotice = when {
        selectionKind == ToolbarSelectionKind.AUDIO && isLegacyAudioClipSelected ->
            "Fade, Beats, Mute, and Volume are available for project audio tracks only."
        selectionKind == ToolbarSelectionKind.VIDEO && isOverlaySelected ->
            "Overlay speed editing is not available yet; use Properties for overlay volume."
        else -> null
    }

    // Restore the historical two-level dock: selected-item actions sit above
    // the persistent eight-tool global dock. More temporarily adds a scrollable
    // secondary row without replacing the global tools or introducing tabs.
    fun routed(item: CtxToolItem) = item.copy(onClick = {
        onBeforeToolAction()
        item.onClick()
    })
    val showClipBar = hasSelection && !isPlaying && !referenceToolMode
    val clipPageItems = clipItems.map(::routed)
    val toolsItems = (if (referenceToolMode) referenceItems else mainItems).map(::routed)
    val moreItems = secondaryItems.map(::routed)

    if (showToolbarNotes) {
        AlertDialog(
            onDismissRequest = { showToolbarNotes = false },
            title = { Text("Editing and export notes") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selectionNotice != null) Text(selectionNotice)
                    Text("Whole-project export supports sequential main-track video. PiP, project stickers, transitions and other unsupported edits stop that route rather than being dropped. Selected-clip export is a different scope; review its limits on the export screen.")
                }
            },
            confirmButton = {
                Text("Got it", modifier = Modifier.clickable { showToolbarNotes = false }.padding(horizontal = 16.dp, vertical = 10.dp))
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        AnimatedVisibility(
            visible = showClipBar,
            enter = fadeIn(animationSpec = tween(200)) + slideInVertically(animationSpec = tween(200)) { it },
            exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(animationSpec = tween(200)) { it }
        ) {
            ToolbarBarRow(
                items = clipPageItems,
                surfaceColor = Color(0xFF151F2D),
                modifier = Modifier.testTag("editor_clip_toolbar"),
                itemWidth = 58.dp,
                compact = selectionKind == ToolbarSelectionKind.VIDEO,
                fitVisibleCount = if (selectionKind == ToolbarSelectionKind.VIDEO) 6 else null,
                leadingContent = {
                    Box(
                        modifier = Modifier.width(44.dp).fillMaxHeight().clickable {
                            onBeforeToolAction()
                            onCollapse()
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Deselect", tint = ApexPalette.TextSecondary, modifier = Modifier.size(22.dp))
                    }
                    Box(Modifier.width(1.dp).fillMaxHeight(0.6f).background(Color(0xFF273447)))
                }
            )
        }
        AnimatedVisibility(
            visible = showMoreTools,
            enter = fadeIn(animationSpec = tween(180)) + slideInVertically(animationSpec = tween(180)) { it },
            exit = fadeOut(animationSpec = tween(180)) + slideOutVertically(animationSpec = tween(180)) { it }
        ) {
            ToolbarBarRow(
                items = moreItems,
                surfaceColor = Color(0xFF111A28),
                modifier = Modifier.testTag("editor_more_toolbar"),
                itemWidth = 58.dp
            )
        }
        ToolbarBarRow(
            items = toolsItems,
            surfaceColor = Color(0xFF101722),
            modifier = Modifier.testTag("editor_global_toolbar"),
            itemWidth = 38.dp,
            compact = true,
            selectedLabel = referenceActiveTool ?: when (selectionKind) {
                ToolbarSelectionKind.TEXT -> "Text"
                ToolbarSelectionKind.STICKER -> "Stickers"
                ToolbarSelectionKind.AUDIO -> "Audio"
                else -> null
            }
        )
    }
}

/**
 * One horizontally scrollable toolbar bar with reachable, full-size tool targets.
 * [leadingContent] (collapse chevron + selection chip) stays fixed while
 * the tool items scroll.
 */
@Composable
private fun ToolbarBarRow(
    items: List<CtxToolItem>,
    surfaceColor: Color,
    modifier: Modifier = Modifier,
    leadingContent: @Composable (RowScope.() -> Unit)? = null,
    itemWidth: androidx.compose.ui.unit.Dp = 66.dp,
    compact: Boolean = false,
    fitVisibleCount: Int? = null,
    selectedLabel: String? = null,
    barHeight: androidx.compose.ui.unit.Dp = 46.dp
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
            .background(Color.Black)
            .padding(horizontal = 4.dp)
    ) {
        val leadingWidth = if (leadingContent != null) 45.dp else 0.dp
        val targetWidth = when {
            fitVisibleCount != null -> ((maxWidth - leadingWidth).coerceAtLeast(0.dp) /
                fitVisibleCount.coerceAtLeast(1).toFloat()).coerceAtMost(itemWidth)
            compact -> (maxWidth / items.size.coerceAtLeast(1)).coerceAtLeast(itemWidth)
            else -> itemWidth
        }
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
                    items.forEach { item ->
                        val itemTint = if (item.label == selectedLabel) ApexPalette.NeonPurple else item.tint
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .width(targetWidth)
                                .fillMaxHeight()
                                .padding(horizontal = if (compact) 1.dp else 0.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (item.label == selectedLabel) ApexPalette.NeonPurple.copy(alpha = 0.20f) else Color.Transparent)
                                .border(if (item.label == selectedLabel) 1.dp else 0.dp,
                                    if (item.label == selectedLabel) ApexPalette.NeonPurple.copy(alpha = 0.5f) else Color.Transparent,
                                    RoundedCornerShape(9.dp))
                                .clickable(onClick = item.onClick)
                        ) {
                            if (item.glyph != null) {
                                // Mockup text glyph (e.g. the bold "T" for the Text tab).
                                Text(
                                    text = item.glyph,
                                    color = itemTint,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.size(20.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            } else {
                                Icon(
                                    imageVector = item.icon!!,
                                    contentDescription = item.contentDescription ?: item.label,
                                    tint = itemTint,
                                    modifier = Modifier
                                        .size(if (compact) 21.dp else 19.dp)
                                        .graphicsLayer { rotationZ = item.iconRotationDeg }
                                )
                            }
                            Spacer(Modifier.height(if (compact) 2.dp else 1.dp))
                            Text(
                                text = item.label,
                                color = itemTint,
                                fontSize = if (compact) 9.sp else 10.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
                // Edge fades (no touch handling — gestures pass
                // through to the scrollable Row below).
                if (!compact) Box(
                    Modifier.align(Alignment.CenterStart)
                        .width(20.dp).fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Black, Color.Transparent)
                            )
                        )
                )
                if (!compact) Box(
                    Modifier.align(Alignment.CenterEnd)
                        .width(20.dp).fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.Black)
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

internal fun prepareMainMediaPickerLaunch(vm: com.apexstudio.app.presentation.viewmodel.EditorViewModel) {
    vm.clearPendingReplace()
    vm.setPendingAddAsOverlay(false)
    vm.setPendingAddAsAudio(false)
    vm.setPendingAddToLayer(null)
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
    mediaPicker: com.apexstudio.app.data.picker.MediaPickerHelper,
    onAi: () -> Unit = {},
    onDraw: () -> Unit = {},
    onToggleKeyframe: () -> Unit = { vm.toggleKeyframeAtPlayhead() },
    onDeviceAudio: () -> Unit = {
        vm.setPendingAddAsOverlay(false)
        vm.setPendingAddAsAudio(true)
        mediaPicker.pickAudioMedia.launch("audio/*")
    },
    onCrop: () -> Unit = { vm.setCropMode(true) },
    onChromaKey: () -> Unit = { vm.openChromaKeyPanel() },
    // Optional override for hosts that own a dedicated effects workspace;
    // the default remains the real in-app FX panel route.
    onEffectsWorkspace: () -> Unit = { vm.openFxPanel() },
    onQuickEnhance: () -> Unit = { vm.openAdjustmentsPanel() },
    onQuickTemplates: () -> Unit = { vm.openTransmissionTemplatesPanel() },
    onQuickFilters: () -> Unit = { vm.openFilterPanel() },
    onChallenge: () -> Unit = { vm.openTextPanel() },
    onNext: () -> Unit = {},
    onColorWorkspace: () -> Unit = { vm.openColorGradingLutPanel() },
    onSignature: () -> Unit = { vm.openSignaturePanel() },
    onBeforeToolAction: () -> Unit = {},
    // Main-timeline media picker; EditorScreen may override when it owns the route.
    onMedia: () -> Unit = {
        prepareMainMediaPickerLaunch(vm)
        mediaPicker.pickMultipleMedia.launch(
            androidx.activity.result.PickVisualMediaRequest(
                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageAndVideo
            )
        )
    },
    onOverlay: (() -> Unit)? = null,
    referenceToolMode: Boolean = false,
    referenceActiveTool: String? = null,
    onReferenceTrim: () -> Unit = { vm.openTrimPanel() }
) {
    val context = androidx.compose.ui.platform.LocalContext.current
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
    val isLegacyAudioClipSelected = selectedClip?.type == ClipType.AUDIO || selectedClip?.type == ClipType.SFX
    val selectedOverlayLayer =
        selectedClip?.takeIf { it.type == ClipType.OVERLAY }?.trackIndex
            ?.takeIf { it in 1..9 }
    ContextualBottomToolbar(
        selectionKind = toolbarKind,
        isLegacyAudioClipSelected = isLegacyAudioClipSelected,
        showSelectionToolsInitially = false,
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
            } else {
                android.widget.Toast.makeText(context, "Transitions need two main-track clips. Whole-project transition export is not supported yet.", android.widget.Toast.LENGTH_LONG).show()
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
        onToggleTrackSolo = {
            selectedAudioTrack?.let { vm.toggleAudioTrackSolo(it.id) }
        },
        isTrackSolo = selectedAudioTrack?.isSolo == true,
        onText = { vm.openTextPanel() },
        onDraw = onDraw,
        onEffects = onEffectsWorkspace,
        onStickers = { vm.openStickerPanel() },
        onArFace = { vm.openArFilterPanel() },
        onLenses = {}, // Legacy API only: the editor no longer exposes Camera Kit.
        onFilters = onQuickFilters,
        onCrop = onCrop,
        onAi = onAi,
        onDeviceAudio = onDeviceAudio,
        onChromaKey = onChromaKey,
        onCaptions = { vm.openCaptionsPanel() },
        onTemplates = onQuickTemplates,
        onChallenge = onChallenge,
        onNext = onNext,
        onMedia = onMedia,
        onOverlay = onOverlay,
        onColorWorkspace = onColorWorkspace,
        onBeforeToolAction = onBeforeToolAction,
        onReferenceTrim = onReferenceTrim,
        referenceToolMode = referenceToolMode,
        referenceActiveTool = referenceActiveTool,
        onAdjust = { vm.openAdjustmentsPanel() },
        onEnhance = onQuickEnhance,
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
        onSfxLibrary = { vm.openSfxLibraryPanel() },
        onAutoClip = { vm.openAutoClipPanel() },
        onHistory = { vm.openHistoryDraftsPanel() },
        onSignature = onSignature,
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
        onAnimation = { vm.openKeyframeAnimationPanel() },
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
            if (selectedAudioTrack != null) vm.splitAudioTrack(selectedAudioTrack.id, vm.playerPositionFlow.value)
            else state.selectedClipId?.let { vm.splitClip(it, vm.playerPositionFlow.value) }
        },
        onTrim = { vm.openTrimPanel() },
        onMask = { vm.openMaskPanelTopLevel() },
        onDuplicate = { state.selectedClipId?.let { vm.duplicateClip(it) } },
        onToggleKeyframe = onToggleKeyframe,
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
