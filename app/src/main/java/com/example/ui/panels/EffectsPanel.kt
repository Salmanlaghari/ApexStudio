package com.example.ui.panels

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.EffectType

/**
 * FX / Effects Panel:
 * Direct, distinct real shader implementations for each effect:
 * - RGB Jitter (chromatic aberration channel separation)
 * - Pixel Sort (horizontal brightness threshold sort)
 * - Datamosh (macroblock motion vector corruption)
 * - Bleach Bypass (photographic silver retention)
 * - VHS Glitch (scanlines & tracking tears)
 * - Thermal (false color infrared gradient)
 * - Halftone Comic (Sobel edges & dot matrix screen)
 * - Vintage 8mm (analog noise, film scratches & sepia)
 * - Zoom Blur (radial directional expansion)
 * - Cyberpunk Glow (high-pass neon bloom)
 */
@Composable
fun EffectsPanel(
    activeEffect: EffectType,
    effectIntensity: Float,
    onEffectSelect: (EffectType) -> Unit,
    onIntensityChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Active effect header & intensity
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "VFX Engine: ${activeEffect.displayName}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = activeEffect.description,
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    maxLines = 1
                )
            }

            if (activeEffect != EffectType.NONE) {
                Text(
                    text = "${(effectIntensity * 100).toInt()}% Intensity",
                    fontSize = 12.sp,
                    color = Color(0xFFF43F5E),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (activeEffect != EffectType.NONE) {
            SliderControl(
                label = "Effect Intensity",
                value = effectIntensity,
                valueRange = 0.1f..1.0f,
                displayValue = "${(effectIntensity * 100).toInt()}%",
                onValueChange = onIntensityChange,
                testTag = "fx_intensity_slider"
            )
        }

        // Grid of distinct effects
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(190.dp)
        ) {
            items(EffectType.values()) { effect ->
                val isSelected = activeEffect == effect
                val badgeBrush = when (effect) {
                    EffectType.RGB_JITTER -> Brush.horizontalGradient(listOf(Color(0xFFFF0055), Color(0xFF00F0FF)))
                    EffectType.PIXEL_SORT -> Brush.horizontalGradient(listOf(Color(0xFF8B5CF6), Color(0xFF3B82F6)))
                    EffectType.DATAMOSH -> Brush.horizontalGradient(listOf(Color(0xFF10B981), Color(0xFFF59E0B)))
                    EffectType.BLEACH_BYPASS -> Brush.horizontalGradient(listOf(Color(0xFF64748B), Color(0xFFCBD5E1)))
                    EffectType.VHS_GLITCH -> Brush.horizontalGradient(listOf(Color(0xFFEF4444), Color(0xFF8B5CF6)))
                    EffectType.THERMAL -> Brush.horizontalGradient(listOf(Color(0xFF7C3AED), Color(0xFFF97316)))
                    EffectType.HALFTONE -> Brush.horizontalGradient(listOf(Color(0xFF1E293B), Color(0xFFF8FAFC)))
                    EffectType.VINTAGE_8MM -> Brush.horizontalGradient(listOf(Color(0xFF78350F), Color(0xFFD97706)))
                    EffectType.ZOOM_BLUR -> Brush.horizontalGradient(listOf(Color(0xFF0284C7), Color(0xFF06B6D4)))
                    EffectType.CYBERPUNK_GLOW -> Brush.horizontalGradient(listOf(Color(0xFFEC4899), Color(0xFF06B6D4)))
                    EffectType.NONE -> Brush.horizontalGradient(listOf(Color(0xFF1E222D), Color(0xFF262C38)))
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onEffectSelect(effect) }
                        .background(if (isSelected) Color(0xFF202636) else Color(0xFF141720))
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Color(0xFFF43F5E) else Color(0xFF2B3242),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(10.dp)
                        .testTag("fx_card_${effect.name.lowercase()}"),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(badgeBrush)
                        )
                        Column {
                            Text(
                                text = effect.displayName,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                            )
                        }
                    }
                }
            }
        }
    }
}
