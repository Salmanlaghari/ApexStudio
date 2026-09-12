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
import androidx.compose.runtime.*
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

        var activeCategory by remember { mutableStateOf("All") }

        // Category pills
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(FxPreset.categories()) { cat ->
                val selected = cat == activeCategory
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
                        .clickable { activeCategory = cat }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        cat,
                        color = if (selected) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        val displayedPresets = remember(activeCategory) {
            if (activeCategory == "All") FxPreset.values().toList()
            else FxPreset.values().filter { it.category == activeCategory }
        }

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
            items(displayedPresets) { preset ->
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
    FxPreset.VIGNETTE, FxPreset.BLOOM, FxPreset.ZOOM_BLUR, FxPreset.LENS_FLARE,
    FxPreset.LIGHT_LEAK, FxPreset.ANAMORPHIC, FxPreset.BOKEH_OVERLAY, FxPreset.SUN_BEAM -> Icons.Default.CenterFocusWeak

    FxPreset.FILM_GRAIN, FxPreset.HALATION, FxPreset.SEPIA_GRAIN,
    FxPreset.DUST_SCRATCHES, FxPreset.FILM_DAMAGE -> Icons.Default.Grain

    FxPreset.VHS, FxPreset.SCANLINES, FxPreset.CRT_PHOSPHOR,
    FxPreset.INTERLACED, FxPreset.HALFTONE, FxPreset.OIL_PAINT,
    FxPreset.EMBOSS_RELIEF -> Icons.Default.Texture

    FxPreset.GLITCH, FxPreset.STROBE, FxPreset.BAD_TV,
    FxPreset.RGB_JITTER, FxPreset.DATAMOSH, FxPreset.SPARKLE,
    FxPreset.EDGE_NEON -> Icons.Default.Bolt

    FxPreset.PIXELATE, FxPreset.PIXEL_SORT, FxPreset.DIGITAL_DROP -> Icons.Default.GridOn

    FxPreset.CHROMATIC, FxPreset.PRISM, FxPreset.BLEACH_BYPASS,
    FxPreset.TECHNICOLOR_STRIP, FxPreset.CROSS_PROCESS, FxPreset.SOLARIZE,
    FxPreset.POSTERIZE, FxPreset.THERMAL_VISION, FxPreset.NIGHT_VISION,
    FxPreset.INVERT_FX, FxPreset.KALEIDOSCOPE,
    FxPreset.CHROMAKEY_3D -> Icons.Default.Layers

    FxPreset.SOFT_BLUR, FxPreset.GLOW_DIFFUSE, FxPreset.RADIAL_BLUR,
    FxPreset.TILT_SHIFT, FxPreset.SPIN_BLUR -> Icons.Default.BlurOn

    FxPreset.SHAKE, FxPreset.MOTION_STREAK, FxPreset.GHOSTING,
    FxPreset.CAMERA_WOBBLE, FxPreset.WHIP_PAN_FX,
    FxPreset.SKETCH_LINES -> Icons.Default.Timeline
}

private fun colorsFor(preset: FxPreset): List<Color> = when (preset) {
    FxPreset.VIGNETTE -> listOf(Color(0xFF3A3A3A), Color(0xFF0B0B0B))
    FxPreset.BLOOM -> listOf(Color(0xFFFFD54F), Color(0xFFFF6F00))
    FxPreset.PRISM -> listOf(Color(0xFF00E5FF), Color(0xFFFF007F))
    FxPreset.HALATION -> listOf(Color(0xFFFF7043), Color(0xFFBF360C))
    FxPreset.LENS_FLARE -> listOf(Color(0xFFFFCA28), Color(0xFF00B0FF))
    FxPreset.LIGHT_LEAK -> listOf(Color(0xFFFF6E40), Color(0xFFFFD180))
    FxPreset.ANAMORPHIC -> listOf(Color(0xFF00B0FF), Color(0xFF1DE9B6))
    FxPreset.BOKEH_OVERLAY -> listOf(Color(0xFFBA68C8), Color(0xFFFF80AB))
    FxPreset.SPARKLE -> listOf(Color(0xFFFFF59D), Color(0xFFF48FB1))
    FxPreset.SUN_BEAM -> listOf(Color(0xFFFFD54F), Color(0xFFFFECB3))
    FxPreset.GLOW_DIFFUSE -> listOf(Color(0xFF80DEEA), Color(0xFFE0F7FA))

    FxPreset.GLITCH -> listOf(Color(0xFF3F0E8E), Color(0xFF00E5FF))
    FxPreset.CHROMATIC -> listOf(Color(0xFF8E0E0E), Color(0xFF0E3B8E))
    FxPreset.PIXELATE -> listOf(Color(0xFF2B8A3E), Color(0xFF0C2A12))
    FxPreset.BAD_TV -> listOf(Color(0xFF37474F), Color(0xFF78909C))
    FxPreset.PIXEL_SORT -> listOf(Color(0xFF4A148C), Color(0xFF880E4F))
    FxPreset.DATAMOSH -> listOf(Color(0xFF00E676), Color(0xFFE040FB))
    FxPreset.RGB_JITTER -> listOf(Color(0xFFFF1744), Color(0xFF00E5FF))
    FxPreset.CRT_PHOSPHOR -> listOf(Color(0xFF2E7D32), Color(0xFF1565C0))
    FxPreset.INTERLACED -> listOf(Color(0xFF455A64), Color(0xFF263238))
    FxPreset.DIGITAL_DROP -> listOf(Color(0xFFC51162), Color(0xFFFFAB00))

    FxPreset.FILM_GRAIN -> listOf(Color(0xFF6E6E6E), Color(0xFF222222))
    FxPreset.VHS -> listOf(Color(0xFF0D3B66), Color(0xFF04101F))
    FxPreset.SCANLINES -> listOf(Color(0xFF1A1A2E), Color(0xFF0E0E1A))
    FxPreset.STROBE -> listOf(Color(0xFFFFFFFF), Color(0xFF424242))
    FxPreset.SEPIA_GRAIN -> listOf(Color(0xFF8D6E63), Color(0xFF3E2723))
    FxPreset.DUST_SCRATCHES -> listOf(Color(0xFF5D4037), Color(0xFF212121))
    FxPreset.FILM_DAMAGE -> listOf(Color(0xFFE65100), Color(0xFFFFB74D))
    FxPreset.BLEACH_BYPASS -> listOf(Color(0xFF9E9E9E), Color(0xFF424242))
    FxPreset.TECHNICOLOR_STRIP -> listOf(Color(0xFFD50000), Color(0xFF00C853))
    FxPreset.CROSS_PROCESS -> listOf(Color(0xFFFFAB00), Color(0xFF00B0FF))
    FxPreset.SOLARIZE -> listOf(Color(0xFF7C4DFF), Color(0xFFFF5252))

    FxPreset.SOFT_BLUR -> listOf(Color(0xFF8E6E3F), Color(0xFF2E1F0C))
    FxPreset.ZOOM_BLUR -> listOf(Color(0xFF7C4DFF), Color(0xFF304FFE))
    FxPreset.SHAKE -> listOf(Color(0xFFE53935), Color(0xFF8E0000))
    FxPreset.RADIAL_BLUR -> listOf(Color(0xFF2979FF), Color(0xFF651FFF))
    FxPreset.TILT_SHIFT -> listOf(Color(0xFF00B8D4), Color(0xFF004D40))
    FxPreset.MOTION_STREAK -> listOf(Color(0xFFFF9100), Color(0xFFFF3D00))
    FxPreset.GHOSTING -> listOf(Color(0xFF78909C), Color(0xFF37474F))
    FxPreset.SPIN_BLUR -> listOf(Color(0xFF651FFF), Color(0xFFD500F9))
    FxPreset.CAMERA_WOBBLE -> listOf(Color(0xFF00897B), Color(0xFF004D40))
    FxPreset.WHIP_PAN_FX -> listOf(Color(0xFFFF6D00), Color(0xFFFFD600))

    FxPreset.HALFTONE -> listOf(Color(0xFF212121), Color(0xFFEEEEEE))
    FxPreset.SKETCH_LINES -> listOf(Color(0xFFB0BEC5), Color(0xFF37474F))
    FxPreset.POSTERIZE -> listOf(Color(0xFFFF007F), Color(0xFF00E5FF))
    FxPreset.EDGE_NEON -> listOf(Color(0xFF00E5FF), Color(0xFFFF0055))
    FxPreset.THERMAL_VISION -> listOf(Color(0xFF304FFE), Color(0xFFFF1744))
    FxPreset.NIGHT_VISION -> listOf(Color(0xFF00E676), Color(0xFF1B5E20))
    FxPreset.OIL_PAINT -> listOf(Color(0xFFE65100), Color(0xFF0091EA))
    FxPreset.EMBOSS_RELIEF -> listOf(Color(0xFF90A4AE), Color(0xFF455A64))
    FxPreset.INVERT_FX -> listOf(Color(0xFF000000), Color(0xFFFFFFFF))
    FxPreset.KALEIDOSCOPE -> listOf(Color(0xFFAA00FF), Color(0xFF00B0FF))
    FxPreset.CHROMAKEY_3D -> listOf(Color(0xFF00E676), Color(0xFF00E5FF))
}
