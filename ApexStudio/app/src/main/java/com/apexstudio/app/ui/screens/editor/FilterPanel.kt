package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.filter.FilterManifest
import com.apexstudio.app.data.filter.FilterPreset
import com.apexstudio.app.data.filter.LutFilterEngine
import com.apexstudio.app.ui.theme.ApexPalette
import androidx.compose.ui.platform.LocalContext

/**
 * Bottom-sheet style filter panel: 7 category chips at the top, a
 * horizontal list of filter chips per category below, and an
 * intensity slider at the bottom. Shows "Original" as the first
 * chip in every category so the user can always snap back to the
 * unfiltered video.
 */
@Composable
fun FilterPanel(
    manifest: FilterManifest,
    activeFilterId: String?,
    intensity: Float,
    activeCategory: String,
    onCategoryChange: (String) -> Unit,
    onFilterSelected: (String?) -> Unit,
    onIntensityChange: (Float) -> Unit,
    onClose: () -> Unit
) {
    val cat = manifest.categoryById(activeCategory) ?: manifest.categories.firstOrNull()
    val presets = cat?.filters ?: emptyList()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(ApexPalette.BgSurface)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Filters",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Default.Close,
                contentDescription = "Close filters",
                tint = ApexPalette.NeonCyan,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { onClose() }
                    .padding(4.dp)
            )
        }
        Spacer(Modifier.height(10.dp))

        // Category chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            manifest.categories.forEach { c ->
                val selected = c.id == activeCategory
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected) ApexPalette.NeonCyan.copy(alpha = 0.2f)
                            else ApexPalette.BgElevated
                        )
                        .border(
                            1.dp,
                            if (selected) ApexPalette.NeonCyan else ApexPalette.BorderGlass,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onCategoryChange(c.id) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        c.name,
                        color = if (selected) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Filter chips row (Original + each preset in the active category)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    label = "Original",
                    selected = activeFilterId == null,
                    onClick = { onFilterSelected(null) }
                )
            }
            items(presets) { preset ->
                FilterChip(
                    label = preset.name,
                    selected = activeFilterId == preset.id,
                    onClick = { onFilterSelected(preset.id) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Intensity slider — disabled when no filter is active
        Text(
            "Intensity",
            color = ApexPalette.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Slider(
            value = intensity,
            onValueChange = onIntensityChange,
            valueRange = 0f..1f,
            enabled = activeFilterId != null,
            colors = SliderDefaults.colors(
                thumbColor = ApexPalette.NeonCyan,
                activeTrackColor = ApexPalette.NeonCyan,
                inactiveTrackColor = ApexPalette.BorderGlass
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "${(intensity * 100).toInt()}%",
            color = if (activeFilterId == null) ApexPalette.TextTertiary else ApexPalette.NeonCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.End)
        )
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(74.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        listOf(ApexPalette.NeonPurple.copy(alpha = 0.5f), ApexPalette.NeonCyan.copy(alpha = 0.5f))
                    )
                )
                .border(
                    1.5.dp,
                    if (selected) ApexPalette.NeonCyan else ApexPalette.BorderGlass,
                    RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
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
