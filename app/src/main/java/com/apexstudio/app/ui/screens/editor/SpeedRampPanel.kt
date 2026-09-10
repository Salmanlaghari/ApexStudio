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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.domain.model.SpeedPreset
import com.apexstudio.app.ui.theme.ApexPalette

/**
 * Bottom-sheet style panel for variable playback speed (slow-mo /
 * time-lapse). Pops up when the user taps the Speed button in the
 * editor's horizontal tool bar.
 *
 * - Six preset chips for the canonical rates (0.25x .. 8x) the model
 *   advertises. Picking one calls [onSelectPreset] which both updates
 *   the selected clip's speed and pushes the new rate to ExoPlayer
 *   so the preview immediately re-clocks.
 * - A continuous slider underneath covers every value in 0.25x..8x
 *   for users who want a non-standard rate. The slider's current
 *   value is shown next to the label so they can read it back.
 * - The panel surfaces the currently selected clip's existing rate
 *   so the user can see what's already set before they make a change.
 */
@Composable
fun SpeedRampPanel(
    selectedClipId: String?,
    currentSpeed: Float,
    activeClipSpeed: Float,
    onSelectPreset: (SpeedPreset) -> Unit,
    onCustomSpeed: (Float) -> Unit,
    onClose: () -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf(activeClipSpeed.coerceIn(0.25f, 8f)) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(ApexPalette.BgElevated)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(16.dp)
    ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Speed,
                contentDescription = null,
                tint = ApexPalette.NeonCyan,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Speed Ramping",
                color = ApexPalette.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = ApexPalette.TextSecondary)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (selectedClipId == null)
                "No clip selected — slider sets the preview rate"
            else
                "Active clip rate: ${formatRate(activeClipSpeed)}  •  Preview rate: ${formatRate(currentSpeed)}",
            color = ApexPalette.TextSecondary,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(12.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SpeedPreset.values().toList()) { preset ->
                val selected = kotlin.math.abs(activeClipSpeed - preset.multiplier) < 0.01f
                SpeedPresetChip(
                    label = preset.label,
                    selected = selected,
                    onClick = {
                        sliderValue = preset.multiplier
                        onSelectPreset(preset)
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "Custom rate: ${formatRate(sliderValue)}",
            color = ApexPalette.TextPrimary,
            fontSize = 13.sp
        )
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onCustomSpeed(sliderValue) },
            valueRange = 0.25f..8f,
            colors = SliderDefaults.colors(
                thumbColor = ApexPalette.NeonCyan,
                activeTrackColor = ApexPalette.NeonCyan,
                inactiveTrackColor = ApexPalette.BorderGlass
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("0.25x", color = ApexPalette.TextTertiary, fontSize = 11.sp)
            Text("1x", color = ApexPalette.TextTertiary, fontSize = 11.sp)
            Text("4x", color = ApexPalette.TextTertiary, fontSize = 11.sp)
            Text("8x", color = ApexPalette.TextTertiary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SpeedPresetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) ApexPalette.NeonCyan.copy(alpha = 0.18f) else ApexPalette.BgBase
    val border = if (selected) ApexPalette.NeonCyan else ApexPalette.BorderGlass
    val fg = if (selected) ApexPalette.NeonCyan else ApexPalette.TextPrimary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(label, color = fg, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        }
    }
}

private fun formatRate(v: Float): String {
    val rounded = (v * 100f).toInt() / 100f
    return if (rounded == rounded.toInt().toFloat()) "${rounded.toInt()}x" else "${rounded}x"
}
