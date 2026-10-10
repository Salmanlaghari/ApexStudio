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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.apexstudio.app.domain.model.VideoAdjustments
import com.apexstudio.app.ui.theme.ApexPalette

/**
 * Phase 3: Adjust Pack panel — 30 brightness/adjust presets + manual sliders.
 *
 * Presets map pack deltas onto [VideoAdjustments]:
 * - brightness/highlights/shadows/warmth/tint: direct deltas
 * - contrast/saturation: pack stores deltas around 0, model uses 1.0 baseline
 *
 * Tapping a preset calls [onApplyPreset]; manual sliders call [onUpdate]
 * exactly like the existing AdjustPanel.
 */

data class AdjustPresetUi(
    val id: String,
    val name: String,
    val category: String,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val highlights: Float,
    val shadows: Float,
    val warmth: Float,
    val tint: Float
)

private fun PackLoader.AdjustPreset.toUi() = AdjustPresetUi(
    id = id, name = name, category = category,
    brightness = brightness, contrast = contrast, saturation = saturation,
    highlights = highlights, shadows = shadows, warmth = warmth, tint = tint
)

/** Applies a pack preset on top of current adjustments. */
fun AdjustPresetUi.toAdjustments(base: VideoAdjustments = VideoAdjustments()): VideoAdjustments =
    base.copy(
        brightness = (base.brightness + brightness).coerceIn(-1f, 1f),
        contrast = (base.contrast + contrast).coerceIn(0.2f, 3f),
        saturation = (base.saturation + saturation).coerceIn(0f, 3f),
        highlights = (base.highlights + highlights).coerceIn(-1f, 1f),
        shadows = (base.shadows + shadows).coerceIn(-1f, 1f),
        temperature = (base.temperature + warmth * 1000f).coerceIn(-3000f, 3000f),
        tint = (base.tint + tint * 100f).coerceIn(-100f, 100f)
    )

private fun emojiForCategory(category: String): String = when {
    category.contains("Airy", ignoreCase = true) -> "☀️"
    category.contains("Moody", ignoreCase = true) -> "🌙"
    category.contains("Contrast", ignoreCase = true) -> "💥"
    category.contains("Soft", ignoreCase = true) -> "🌸"
    category.contains("HDR", ignoreCase = true) -> "🌈"
    else -> "🎚️"
}

@Composable
fun AdjustPackPanel(
    adjustments: VideoAdjustments,
    onApplyPreset: (VideoAdjustments) -> Unit,
    onUpdate: ((VideoAdjustments) -> VideoAdjustments) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val presets = remember {
        PackLoader.loadAdjustPresets(context).map { it.toUi() }
    }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    val categories = remember(presets) { presets.map { it.category }.distinct() }
    val visible = remember(presets, selectedCategory) {
        if (selectedCategory == null) presets else presets.filter { it.category == selectedCategory }
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
                    Text("💡", fontSize = 14.sp)
                }
                Column {
                    Text(
                        "Brightness Pack",
                        color = ApexPalette.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${presets.size} presets + manual controls",
                        color = ApexPalette.TextTertiary,
                        fontSize = 9.sp
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ApexPalette.BgElevated)
                        .clickable { onReset() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = ApexPalette.NeonPink, modifier = Modifier.size(12.dp))
                        Text("Reset", color = ApexPalette.NeonPink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
        }

        Spacer(Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Category chips
            Text("PRESETS", color = ApexPalette.NeonCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    PresetChip(label = "All", selected = selectedCategory == null, onClick = { selectedCategory = null })
                }
                items(categories) { cat ->
                    PresetChip(label = cat, selected = selectedCategory == cat, onClick = { selectedCategory = cat })
                }
            }
            Spacer(Modifier.height(8.dp))

            // Preset cards
            visible.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { preset ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ApexPalette.BgElevated)
                                .border(0.5.dp, ApexPalette.NeonCyan.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                .clickable { onApplyPreset(preset.toAdjustments(adjustments)) }
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(emojiForCategory(preset.category), fontSize = 20.sp)
                                Column {
                                    Text(preset.name, color = ApexPalette.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    Text(preset.category, color = ApexPalette.TextTertiary, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }

            // Manual sliders
            Spacer(Modifier.height(4.dp))
            Text("MANUAL", color = ApexPalette.NeonCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            ManualSlider(
                label = "Brightness",
                value = adjustments.brightness,
                range = -1f..1f,
                onChange = { v -> onUpdate { adj -> adj.copy(brightness = v.coerceIn(-1f, 1f)) } }
            )
            ManualSlider(
                label = "Contrast",
                value = adjustments.contrast,
                range = 0.2f..3f,
                onChange = { v -> onUpdate { adj -> adj.copy(contrast = v.coerceIn(0.2f, 3f)) } }
            )
            ManualSlider(
                label = "Saturation",
                value = adjustments.saturation,
                range = 0f..3f,
                onChange = { v -> onUpdate { adj -> adj.copy(saturation = v.coerceIn(0f, 3f)) } }
            )
            ManualSlider(
                label = "Warmth",
                value = adjustments.temperature / 1000f,
                range = -3f..3f,
                onChange = { v -> onUpdate { adj -> adj.copy(temperature = (v * 1000f).coerceIn(-3000f, 3000f)) } }
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PresetChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            color = if (selected) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun ManualSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = ApexPalette.TextSecondary, fontSize = 11.sp)
            Text("%.2f".format(value), color = ApexPalette.NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = ApexPalette.NeonCyan,
                activeTrackColor = ApexPalette.NeonCyan
            )
        )
    }
}
