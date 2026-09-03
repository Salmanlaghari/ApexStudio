package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.fx.FxPreset
import com.apexstudio.app.ui.theme.ApexPalette

/**
 * Bottom-sheet FX picker: "Original" + every real-time FX preset,
 * each tile carrying a distinct gradient + glyph, plus an intensity
 * slider that fades the chosen effect from subtle to full. Selecting
 * a preset pushes a [com.apexstudio.app.data.fx.FxGlEffect] into the
 * live preview effect chain (and later into the export).
 */
@Composable
fun FxPanel(
    activeFxId: String?,
    intensity: Float,
    onFxSelected: (String?) -> Unit,
    onIntensityChange: (Float) -> Unit,
    onKeyframesClick: (() -> Unit)? = null,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(ApexPalette.BgSurface)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "FX",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Default.Close,
                contentDescription = "Close FX",
                tint = ApexPalette.NeonCyan,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { onClose() }
                    .padding(4.dp)
            )
        }
        Spacer(Modifier.height(10.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FxChip(
                    label = "Original",
                    icon = null,
                    colors = listOf(ApexPalette.BgElevated, ApexPalette.BgDeep),
                    selected = activeFxId == null,
                    onClick = { onFxSelected(null) }
                )
            }
            items(FxPreset.values().toList()) { preset ->
                FxChip(
                    label = preset.label,
                    icon = iconFor(preset),
                    colors = colorsFor(preset),
                    selected = activeFxId == preset.id,
                    onClick = { onFxSelected(preset.id) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "Intensity",
            color = ApexPalette.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Slider(
            value = intensity,
            onValueChange = onIntensityChange,
            enabled = activeFxId != null,
            colors = SliderDefaults.colors(
                thumbColor = ApexPalette.NeonCyan,
                activeTrackColor = ApexPalette.NeonCyan,
                inactiveTrackColor = ApexPalette.BgElevated
            )
        )

        if (onKeyframesClick != null) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ApexPalette.BgElevated.copy(alpha = 0.6f))
                    .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(10.dp))
                    .clickable { onKeyframesClick() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Timeline, null,
                    tint = ApexPalette.NeonCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Keyframe animation (scale / rotate / position)",
                    color = ApexPalette.NeonCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun FxChip(
    label: String,
    icon: ImageVector?,
    colors: List<Color>,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) ApexPalette.NeonCyan.copy(alpha = 0.12f)
                else Color.Transparent
            )
            .border(
                1.5.dp,
                if (selected) ApexPalette.NeonCyan else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(colors))
                .border(1.dp, ApexPalette.BorderGlass.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Icon(
                    icon, null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = if (selected) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

private fun iconFor(preset: FxPreset): ImageVector = when (preset) {
    FxPreset.VIGNETTE -> Icons.Default.CenterFocusWeak
    FxPreset.FILM_GRAIN -> Icons.Default.Grain
    FxPreset.VHS -> Icons.Default.Texture
    FxPreset.GLITCH -> Icons.Default.Bolt
    FxPreset.PIXELATE -> Icons.Default.GridOn
    FxPreset.CHROMATIC -> Icons.Default.Layers
    FxPreset.SCANLINES -> Icons.Default.BlurOn
    FxPreset.SOFT_BLUR -> Icons.Default.BlurOn
}

private fun colorsFor(preset: FxPreset): List<Color> = when (preset) {
    FxPreset.VIGNETTE -> listOf(Color(0xFF3A3A3A), Color(0xFF0B0B0B))
    FxPreset.FILM_GRAIN -> listOf(Color(0xFF6E6E6E), Color(0xFF222222))
    FxPreset.VHS -> listOf(Color(0xFF0D3B66), Color(0xFF04101F))
    FxPreset.GLITCH -> listOf(Color(0xFF3F0E8E), Color(0xFF00E5FF))
    FxPreset.PIXELATE -> listOf(Color(0xFF2B8A3E), Color(0xFF0C2A12))
    FxPreset.CHROMATIC -> listOf(Color(0xFF8E0E0E), Color(0xFF0E3B8E))
    FxPreset.SCANLINES -> listOf(Color(0xFF1A1A2E), Color(0xFF0E0E1A))
    FxPreset.SOFT_BLUR -> listOf(Color(0xFF8E6E3F), Color(0xFF2E1F0C))
}
