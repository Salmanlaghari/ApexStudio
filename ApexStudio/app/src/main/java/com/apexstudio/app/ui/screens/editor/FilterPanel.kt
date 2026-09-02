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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.ImageBitmap
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
    thumbnails: Map<String?, androidx.compose.ui.graphics.ImageBitmap> = emptyMap(),
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
                    filterId = null,
                    selected = activeFilterId == null,
                    thumbnail = thumbnails[null],
                    onClick = { onFilterSelected(null) }
                )
            }
            items(presets) { preset ->
                FilterChip(
                    label = preset.name,
                    filterId = preset.id,
                    selected = activeFilterId == preset.id,
                    thumbnail = thumbnails[preset.id],
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
    filterId: String?,
    selected: Boolean,
    thumbnail: androidx.compose.ui.graphics.ImageBitmap? = null,
    onClick: () -> Unit
) {
    val colors = filterPreviewColors(filterId)
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
                .then(
                    if (thumbnail != null) Modifier.background(Color.Transparent)
                    else Modifier.background(Brush.linearGradient(colors))
                )
                .border(
                    1.5.dp,
                    if (selected) ApexPalette.NeonCyan else ApexPalette.BorderGlass,
                    RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            // Show real thumbnail if available, otherwise gradient fallback
            if (thumbnail != null) {
                androidx.compose.foundation.Image(
                    bitmap = thumbnail,
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(10.dp))
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clip(RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
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

/**
 * Returns a two-color gradient that visually represents each filter's
 * colour character. "Original" gets a neutral gradient; each LUT
 * preset maps to a signature look based on its name or category.
 */
private fun filterPreviewColors(filterId: String?): List<Color> {
    return when (filterId) {
        // Cinematic
        "teal_orange" -> listOf(Color(0xFF0D4F6B), Color(0xFFD4760A))
        "hollywood" -> listOf(Color(0xFF8B6914), Color(0xFFD4A03C))
        "moody_blockbuster" -> listOf(Color(0xFF1A1A2E), Color(0xFF4A3F6B))
        "matrix_green" -> listOf(Color(0xFF003300), Color(0xFF00CC00))
        "film_noir_cinema" -> listOf(Color(0xFF0A0A0A), Color(0xFF333333))
        "blockbuster_warm" -> listOf(Color(0xFF6B3A0A), Color(0xFFD4960A))
        "epic_dawn" -> listOf(Color(0xFF1A0A33), Color(0xFFD46A0A))
        "cinema_teal" -> listOf(Color(0xFF0A4A5A), Color(0xFF0A8A8A))
        "thriller_blue" -> listOf(Color(0xFF0A1A4A), Color(0xFF3A6ABA))
        "romance_warm" -> listOf(Color(0xFF6B2A2A), Color(0xFFD48A7A))
        // Retro & Film
        "kodak_35mm" -> listOf(Color(0xFF8B7355), Color(0xFFC4A882))
        "fuji_chrome" -> listOf(Color(0xFF2A5A3A), Color(0xFF7ACA5A))
        "vintage_sepia" -> listOf(Color(0xFF7A5A2A), Color(0xFFC4A06A))
        "eighties_grain" -> listOf(Color(0xFF6A4A2A), Color(0xFFBA9A5A))
        "polaroid_fade" -> listOf(Color(0xFF8A7A6A), Color(0xFFDAC8B8))
        "super_8" -> listOf(Color(0xFF5A4A2A), Color(0xFFAA9A5A))
        "film_warm" -> listOf(Color(0xFF7A5A3A), Color(0xFFCA9A6A))
        "film_cool" -> listOf(Color(0xFF2A3A5A), Color(0xFF7A9ABA))
        "disposable_camera" -> listOf(Color(0xFF8A6A4A), Color(0xFFDAC09A))
        "vhs_warm" -> listOf(Color(0xFF6A3A2A), Color(0xFFBA7A5A))
        // Cyberpunk & Neon
        "neon_purple" -> listOf(Color(0xFF3A0A5A), Color(0xFFAA2AFA))
        "cyan_glow" -> listOf(Color(0xFF0A3A5A), Color(0xFF0ACAEA))
        "midnight_dark" -> listOf(Color(0xFF0A0A2A), Color(0xFF2A2A6A))
        "neon_contrast" -> listOf(Color(0xFF1A0A3A), Color(0xFFFA2ACA))
        "synthwave_pink" -> listOf(Color(0xFF5A0A4A), Color(0xFFFA5ACA))
        "synthwave_blue" -> listOf(Color(0xFF0A1A5A), Color(0xFF5A8AFA))
        "laser_grid" -> listOf(Color(0xFF0A2A0A), Color(0xFF2AFA2A))
        "chrome_metal" -> listOf(Color(0xFF3A3A4A), Color(0xFFAAAAAA))
        "neon_green" -> listOf(Color(0xFF0A3A0A), Color(0xFF2ACA2A))
        "ultraviolet" -> listOf(Color(0xFF2A0A5A), Color(0xFF8A2AFA))
        // Portrait & Beauty
        "soft_skin_glow" -> listOf(Color(0xFF8A5A4A), Color(0xFFDABA9A))
        "natural_warmth" -> listOf(Color(0xFF6A5A3A), Color(0xFFCAA87A))
        "pastel_tone" -> listOf(Color(0xFFCA9ABA), Color(0xFFFADAFA))
        "studio_glow" -> listOf(Color(0xFF7A6A5A), Color(0xFFBAB0A0))
        "fresh_face" -> listOf(Color(0xFF8A7A6A), Color(0xFFDACABA))
        "peachy_glow" -> listOf(Color(0xFFBA7A5A), Color(0xFFFABA9A))
        "porcelain" -> listOf(Color(0xFFAAAAAA), Color(0xFFF0F0F0))
        "rose_gold" -> listOf(Color(0xFFBA6A5A), Color(0xFFFAAA9A))
        "clean_white" -> listOf(Color(0xFF9A9A9A), Color(0xFFFAFAFA))
        // B&W & Monochromatic
        "noir_classic" -> listOf(Color(0xFF0A0A0A), Color(0xFF3A3A3A))
        "high_contrast_charcoal" -> listOf(Color(0xFF1A1A1A), Color(0xFF5A5A5A))
        "silver_oxide" -> listOf(Color(0xFF4A4A5A), Color(0xFFAAAAAA))
        "rich_black" -> listOf(Color(0xFF0A0A0A), Color(0xFF2A2A2A))
        "film_bw_warm" -> listOf(Color(0xFF2A2A1A), Color(0xFF7A7A5A))
        "film_bw_cool" -> listOf(Color(0xFF1A1A2A), Color(0xFF5A5A8A))
        "ink_wash" -> listOf(Color(0xFF0A0A1A), Color(0xFF4A4A7A))
        "graphite" -> listOf(Color(0xFF2A2A2A), Color(0xFF6A6A6A))
        "classic_mono" -> listOf(Color(0xFF1A1A1A), Color(0xFF6A6A6A))
        "high_key_mono" -> listOf(Color(0xFF5A5A5A), Color(0xFFCACACA))
        // Urban & Moody
        "cold_city" -> listOf(Color(0xFF1A2A4A), Color(0xFF4A6A9A))
        "street_blue" -> listOf(Color(0xFF0A2A5A), Color(0xFF3A7ACA))
        "muted_tones" -> listOf(Color(0xFF4A4A3A), Color(0xFF8A8A6A))
        "industrial" -> listOf(Color(0xFF3A3A3A), Color(0xFF7A7A7A))
        "rainy_window" -> listOf(Color(0xFF1A2A3A), Color(0xFF5A7A9A))
        "night_street" -> listOf(Color(0xFF0A0A2A), Color(0xFF2A3A6A))
        "urban_shadow" -> listOf(Color(0xFF1A1A2A), Color(0xFF4A4A6A))
        "concrete_gray" -> listOf(Color(0xFF4A4A4A), Color(0xFF8A8A8A))
        "subway_light" -> listOf(Color(0xFF3A3A2A), Color(0xFF8A8A6A))
        "rooftop_dusk" -> listOf(Color(0xFF2A1A3A), Color(0xFF7A4A9A))
        // Food & Landscape
        "vibrant_punch" -> listOf(Color(0xFFBA3A2A), Color(0xFFFA8A2A))
        "forest_green" -> listOf(Color(0xFF0A3A1A), Color(0xFF2A8A3A))
        "sunset_gold" -> listOf(Color(0xFF8A4A0A), Color(0xFFDA9A2A))
        "ocean_blue" -> listOf(Color(0xFF0A2A5A), Color(0xFF3A8ADA))
        "tropical_punch" -> listOf(Color(0xFFDA3A5A), Color(0xFFFAAA2A))
        "fresh_garden" -> listOf(Color(0xFF2A5A2A), Color(0xFF7ACA5A))
        "golden_hour" -> listOf(Color(0xFF8A6A1A), Color(0xFFDA9A4A))
        "blue_hour" -> listOf(Color(0xFF1A2A5A), Color(0xFF4A6ABA))
        "mountain_air" -> listOf(Color(0xFF3A5A6A), Color(0xFF8ABABA))
        "desert_sand" -> listOf(Color(0xFF7A5A2A), Color(0xFFCABA7A))
        // Original / no filter
        null -> listOf(
            ApexPalette.NeonPurple.copy(alpha = 0.5f),
            ApexPalette.NeonCyan.copy(alpha = 0.5f)
        )
        // Fallback
        else -> listOf(
            ApexPalette.NeonPurple.copy(alpha = 0.5f),
            ApexPalette.NeonCyan.copy(alpha = 0.5f)
        )
    }
}
