package com.apexstudio.app.ui.screens.editor

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.presentation.state.EditorState
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.ui.theme.ApexPalette

/**
 * CapCut-style contextual bottom toolbar.
 *
 * The tool set follows the current selection, exactly like CapCut:
 * - Nothing selected → global tools (Edit, Audio, Text, Effects, Stickers)
 * - Video clip selected → clip tools (Adjust, Replace, Speed, Volume, Animation, Delete)
 * - Audio selected → audio tools (Fade, Replace, Beats, Volume, Delete)
 *
 * A collapse chevron as the first item deselects and returns to global tools.
 */
enum class ToolbarSelectionKind { NONE, VIDEO, AUDIO }

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
    }
}

private data class CtxToolItem(
    val label: String,
    val icon: ImageVector,
    val tint: Color = Color(0xFF9CA3AF),
    val onClick: () -> Unit
)

@Composable
fun ContextualBottomToolbar(
    selectionKind: ToolbarSelectionKind,
    // Global tools
    onEdit: () -> Unit = {},
    onAudio: () -> Unit = {},
    onText: () -> Unit = {},
    onEffects: () -> Unit = {},
    onStickers: () -> Unit = {},
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
    // Collapse (deselect)
    onCollapse: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val items: List<CtxToolItem> = when (selectionKind) {
        ToolbarSelectionKind.NONE -> listOf(
            CtxToolItem("Edit", Icons.Default.ContentCut, ApexPalette.NeonCyan, onEdit),
            CtxToolItem("Audio", Icons.Default.MusicNote, onClick = onAudio),
            CtxToolItem("Text", Icons.Default.TextFields, onClick = onText),
            CtxToolItem("Effects", Icons.Default.AutoAwesome, onClick = onEffects),
            CtxToolItem("Stickers", Icons.Default.EmojiEmotions, onClick = onStickers)
        )
        ToolbarSelectionKind.VIDEO -> listOf(
            CtxToolItem("Adjust", Icons.Default.Tune, onClick = onAdjust),
            CtxToolItem("Replace", Icons.Default.SwapHoriz, onClick = onReplace),
            CtxToolItem("Speed", Icons.Default.Speed, onClick = onSpeed),
            CtxToolItem("Volume", Icons.Default.VolumeUp, onClick = onVolume),
            // Keyframe tab: opens the keyframe editor (diamonds + curves)
            // so keyframes can be added/edited immediately on selection.
            CtxToolItem("Keyframe", Icons.Default.Diamond, onClick = onAnimation),
            CtxToolItem("Delete", Icons.Default.DeleteOutline, ApexPalette.NeonPink, onDelete)
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
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(Color(0xFF0C0C14))
            .border(width = 1.dp, color = Color(0xFF1F1F2E))
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
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = item.tint,
                    modifier = Modifier.size(21.dp)
                )
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
