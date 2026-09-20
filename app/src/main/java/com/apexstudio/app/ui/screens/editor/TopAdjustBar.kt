package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.domain.model.VideoAdjustments
import com.apexstudio.app.ui.theme.ApexPalette

/**
 * Top Adjustment Bar: Elevates all 14 color, light & HDR features from the
 * bottom to the top directly below the preview player.
 * Allows instant 1-tap adjustment preview and 1-tap expansion of the full top panel.
 */
@Composable
fun TopAdjustBar(
    adjustments: VideoAdjustments,
    onOpenAdjustPanel: () -> Unit,
    onUpdate: ((VideoAdjustments) -> VideoAdjustments) -> Unit,
    onResetAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isModified = !adjustments.isDefault

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(ApexPalette.BgDeep)
            .border(0.5.dp, ApexPalette.BorderGlass, RoundedCornerShape(0.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Main Adjust Trigger (Prominently styled at top)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(ApexPalette.NeonCyan.copy(alpha = 0.25f), ApexPalette.NeonPurple.copy(alpha = 0.25f))
                    )
                )
                .border(
                    1.dp,
                    if (isModified) ApexPalette.NeonCyan else ApexPalette.NeonCyan.copy(alpha = 0.5f),
                    RoundedCornerShape(8.dp)
                )
                .clickable { onOpenAdjustPanel() }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                Icons.Default.Tune,
                contentDescription = "Adjust",
                tint = ApexPalette.NeonCyan,
                modifier = Modifier.size(13.dp)
            )
            Text(
                "ADJUST (14 FX)",
                color = ApexPalette.NeonCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
            if (isModified) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(ApexPalette.NeonAmber)
                )
            }
        }

        // Horizontal list of all individual adjust features at the top
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // HDR+
            item {
                AdjustQuickChip(
                    emoji = "✨",
                    label = "HDR+",
                    value = "%.0f%%".format(adjustments.hdr * 100),
                    isModified = adjustments.hdr > 0f,
                    onClick = {
                        val next = if (adjustments.hdr >= 0.9f) 0f else adjustments.hdr + 0.3f
                        onUpdate { it.copy(hdr = next.coerceIn(0f, 1f)) }
                    }
                )
            }
            // Brightness
            item {
                AdjustQuickChip(
                    emoji = "☀️",
                    label = "Bright",
                    value = "%+.0f".format(adjustments.brightness * 100),
                    isModified = adjustments.brightness != 0f,
                    onClick = { onOpenAdjustPanel() }
                )
            }
            // Contrast
            item {
                AdjustQuickChip(
                    emoji = "🌓",
                    label = "Contrast",
                    value = "%.0f%%".format(adjustments.contrast * 100),
                    isModified = adjustments.contrast != 1f,
                    onClick = { onOpenAdjustPanel() }
                )
            }
            // Saturation
            item {
                AdjustQuickChip(
                    emoji = "🎨",
                    label = "Color",
                    value = "%.0f%%".format(adjustments.saturation * 100),
                    isModified = adjustments.saturation != 1f,
                    onClick = { onOpenAdjustPanel() }
                )
            }
            // Warmth / Temperature
            item {
                AdjustQuickChip(
                    emoji = "🌡️",
                    label = "Warmth",
                    value = "%+.0f".format(adjustments.temperature * 100),
                    isModified = adjustments.temperature != 0f,
                    onClick = { onOpenAdjustPanel() }
                )
            }
            // Vignette
            item {
                AdjustQuickChip(
                    emoji = "🎯",
                    label = "Vignette",
                    value = "%.0f%%".format(adjustments.vignette * 100),
                    isModified = adjustments.vignette > 0f,
                    onClick = { onOpenAdjustPanel() }
                )
            }
            // Exposure
            item {
                AdjustQuickChip(
                    emoji = "📸",
                    label = "Exposure",
                    value = "%+.0f".format(adjustments.exposure * 100),
                    isModified = adjustments.exposure != 0f,
                    onClick = { onOpenAdjustPanel() }
                )
            }
            // Sharpness
            item {
                AdjustQuickChip(
                    emoji = "🔪",
                    label = "Sharpness",
                    value = "%.0f%%".format(adjustments.sharpness * 100),
                    isModified = adjustments.sharpness > 0f,
                    onClick = { onOpenAdjustPanel() }
                )
            }
            // Film Grain
            item {
                AdjustQuickChip(
                    emoji = "📽️",
                    label = "Grain",
                    value = "%.0f%%".format(adjustments.grain * 100),
                    isModified = adjustments.grain > 0f,
                    onClick = { onOpenAdjustPanel() }
                )
            }
        }

        // Reset if modified
        if (isModified) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(ApexPalette.BgElevated)
                    .clickable { onResetAll() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Reset",
                    tint = ApexPalette.NeonPink,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun AdjustQuickChip(
    emoji: String,
    label: String,
    value: String,
    isModified: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isModified) ApexPalette.NeonAmber.copy(alpha = 0.15f)
                else ApexPalette.BgElevated
            )
            .border(
                0.8.dp,
                if (isModified) ApexPalette.NeonAmber.copy(alpha = 0.6f)
                else ApexPalette.BorderGlass,
                RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(emoji, fontSize = 9.sp)
        Text(
            label,
            color = if (isModified) ApexPalette.NeonAmber else ApexPalette.TextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            value,
            color = if (isModified) ApexPalette.NeonAmber else ApexPalette.TextPrimary,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
