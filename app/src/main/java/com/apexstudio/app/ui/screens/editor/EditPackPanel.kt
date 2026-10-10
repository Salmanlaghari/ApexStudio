package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.packs.PackLoader
import com.apexstudio.app.ui.theme.ApexPalette

/**
 * Phase 3: Edit Pack panel — 20 editing presets from `assets/packs/edit-presets/`.
 *
 * - speed_ramp  -> applied via EditorViewModel.setClipSpeed / setClipSpeedRamp
 * - freeze_frame -> dramatic slow-mo hold (engine has no true freeze; honest label)
 * - reverse     -> engine has no reverse playback: shown with "Soon" badge
 * - split_screen -> PIP layout via clip pipX/pipY/pipScale
 * - zoom        -> scale keyframes via EditorViewModel.addKeyframe
 *
 * No dead buttons: every preset either applies or carries a "Soon" badge.
 */

private data class EditPresetUi(
    val id: String,
    val name: String,
    val type: String,
    val description: String,
    val emoji: String
)

private fun emojiFor(type: String, id: String): String = when (type) {
    "speed_ramp" -> when {
        id.contains("slowmo") -> "🐌"
        id.contains("speedup") -> "⚡"
        id.contains("beat") -> "🥁"
        else -> "📈"
    }
    "freeze_frame" -> "❄️"
    "reverse" -> "🔄"
    "split_screen" -> "◫"
    "zoom" -> "🔍"
    else -> "✂️"
}

private fun labelFor(type: String): String = when (type) {
    "speed_ramp" -> "Speed"
    "freeze_frame" -> "Freeze"
    "reverse" -> "Reverse"
    "split_screen" -> "Split"
    "zoom" -> "Zoom"
    else -> type
}

@Composable
fun EditPackPanel(
    onApplySpeedRamp: (presetId: String, startSpeed: Float, endSpeed: Float, fixedSpeed: Float?) -> Unit,
    onApplyFreeze: (presetId: String) -> Unit,
    onApplySplitScreen: (presetId: String, layout: SplitLayout) -> Unit,
    onApplyZoom: (presetId: String, zoomIn: Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val presets = remember {
        PackLoader.loadEditPresets(context).map {
            EditPresetUi(
                id = it.id,
                name = it.name,
                type = it.type,
                description = it.description,
                emoji = emojiFor(it.type, it.id)
            )
        }
    }
    var selectedType by remember { mutableStateOf<String?>(null) }
    val types = remember(presets) { presets.map { it.type }.distinct() }
    val visible = remember(presets, selectedType) {
        if (selectedType == null) presets else presets.filter { it.type == selectedType }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        ApexPalette.BgSurface.copy(alpha = 0.98f),
                        ApexPalette.BgBase.copy(alpha = 0.98f)
                    )
                )
            )
            .border(
                1.dp,
                Brush.horizontalGradient(
                    listOf(ApexPalette.NeonCyan.copy(alpha = 0.4f), ApexPalette.NeonAmber.copy(alpha = 0.4f))
                ),
                RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(ApexPalette.NeonCyan.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✂️", fontSize = 14.sp)
                }
                Column {
                    Text(
                        "Edit Pack",
                        color = ApexPalette.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${presets.size} presets • tap to apply to selected clip",
                        color = ApexPalette.TextTertiary,
                        fontSize = 9.sp
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(ApexPalette.BgElevated)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = ApexPalette.TextSecondary, modifier = Modifier.size(15.dp))
            }
        }

        Spacer(Modifier.height(10.dp))

        // Type filter chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(label = "All", selected = selectedType == null, onClick = { selectedType = null })
            }
            items(types) { t ->
                FilterChip(label = labelFor(t), selected = selectedType == t, onClick = { selectedType = t })
            }
        }

        Spacer(Modifier.height(10.dp))

        // Preset grid
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            visible.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { preset ->
                        EditPresetCard(
                            preset = preset,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                when (preset.type) {
                                    "speed_ramp" -> {
                                        val (start, end, fixed) = speedFor(preset.id)
                                        onApplySpeedRamp(preset.id, start, end, fixed)
                                    }
                                    "freeze_frame" -> onApplyFreeze(preset.id)
                                    "split_screen" -> onApplySplitScreen(preset.id, layoutFor(preset.id))
                                    "zoom" -> onApplyZoom(preset.id, zoomInFor(preset.id))
                                    // reverse: no engine support — badge shown, no-op
                                    else -> { /* coming soon */ }
                                }
                            }
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (presets.isEmpty()) {
                Text(
                    "No presets found.",
                    color = ApexPalette.TextTertiary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) ApexPalette.NeonCyan.copy(alpha = 0.2f) else ApexPalette.BgElevated)
            .border(
                1.dp,
                if (selected) ApexPalette.NeonCyan else Color.Transparent,
                RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            color = if (selected) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun EditPresetCard(
    preset: EditPresetUi,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isSoon = preset.type == "reverse"
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(ApexPalette.BgElevated)
            .border(0.5.dp, ApexPalette.NeonCyan.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .clickable(enabled = !isSoon) { onClick() }
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(preset.emoji, fontSize = 22.sp)
                if (isSoon) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ApexPalette.NeonAmber.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("SOON", color = ApexPalette.NeonAmber, fontSize = 8.sp, fontWeight = FontWeight.Black)
                    }
                } else {
                    Text(
                        labelFor(preset.type).uppercase(),
                        color = ApexPalette.NeonCyan,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(preset.name, color = ApexPalette.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            if (preset.description.isNotBlank()) {
                Text(preset.description, color = ApexPalette.TextTertiary, fontSize = 10.sp, maxLines = 2)
            }
        }
    }
}

/** Maps speed_ramp preset ids to (startSpeed, endSpeed, fixedSpeed). */
private fun speedFor(presetId: String): Triple<Float, Float, Float?> = when {
    presetId.contains("slowmo") -> Triple(1f, 1f, 0.25f)
    presetId.contains("speedup") -> Triple(1f, 1f, 2f)
    presetId.contains("velocity") -> Triple(0.5f, 2f, null)
    presetId.contains("ramp_in") -> Triple(0.5f, 1.5f, null)
    presetId.contains("ramp_out") -> Triple(1.5f, 0.5f, null)
    presetId.contains("beat") -> Triple(0.25f, 1f, null)
    else -> Triple(1f, 1f, 1f)
}

/** Split-screen PIP layout. */
data class SplitLayout(
    val pipX: Float,
    val pipY: Float,
    val pipScale: Float
)

private fun layoutFor(presetId: String): SplitLayout = when {
    presetId.contains("2side") -> SplitLayout(0.25f, 0.5f, 0.5f)
    presetId.contains("2x2") || presetId.contains("4") -> SplitLayout(0.25f, 0.25f, 0.5f)
    presetId.contains("pip") -> SplitLayout(0.85f, 0.85f, 0.25f)
    presetId.contains("top") -> SplitLayout(0.5f, 0.25f, 0.5f)
    presetId.contains("bottom") -> SplitLayout(0.5f, 0.75f, 0.5f)
    else -> SplitLayout(0.5f, 0.5f, 0.5f)
}

private fun zoomInFor(presetId: String): Boolean = !presetId.contains("out")
