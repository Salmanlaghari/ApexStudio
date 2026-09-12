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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.effects.ColorGradingProcessor
import com.example.model.AdjustmentValues
import com.example.model.ColorPreset

/**
 * Color Grading & Adjust Panel:
 * Fulfills Requirement 6:
 * 1. 10+ Curated Color Presets (Teal & Orange, Cyberpunk, Moody Noir, Golden Hour, etc.)
 * 2. Full 12-factor Color Grading Controls (Brightness, Contrast, Saturation, Hue, Exposure,
 *    Highlights, Shadows, Temperature, Tint, Vignette, Sharpness, Fade).
 * 3. Before / After comparison & Reset.
 */
@Composable
fun AdjustPanel(
    adjustments: AdjustmentValues,
    onAdjustmentsChange: (AdjustmentValues) -> Unit,
    isCompareMode: Boolean,
    onToggleCompare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Presets Header & Compare Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Color Grading Studio", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("12-factor color science & 12 cinema presets", fontSize = 11.sp, color = Color(0xFF94A3B8))
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Before / After Compare Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onToggleCompare() }
                        .background(if (isCompareMode) Color(0xFFE11D48) else Color(0xFF1E2430))
                        .border(1.dp, if (isCompareMode) Color(0xFFFDA4AF) else Color(0xFF333D4F), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("adjust_compare_button")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Compare, contentDescription = "Compare", tint = Color.White, modifier = Modifier.size(14.dp))
                        Text(
                            text = if (isCompareMode) "Showing Original" else "Compare",
                            fontSize = 10.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Reset adjustments
                IconButton(
                    onClick = { onAdjustmentsChange(AdjustmentValues()) },
                    modifier = Modifier.testTag("adjust_reset_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset Color Adjustments", tint = Color.Gray)
                }
            }
        }

        // Curated Presets Bar (12 Presets)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Cinema Color Presets", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFE2E8F0))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("adjust_presets_row")
            ) {
                items(ColorGradingProcessor.PRESETS) { preset ->
                    val isSelected = adjustments == preset.values

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onAdjustmentsChange(preset.values) }
                            .background(if (isSelected) Color(0xFF2563EB) else Color(0xFF1A1D26))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Color(0xFF60A5FA) else Color(0xFF2F3545),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("preset_${preset.id}")
                    ) {
                        Column {
                            Text(
                                text = preset.name,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = Color.White
                            )
                            Text(
                                text = preset.description.take(24) + "...",
                                fontSize = 9.sp,
                                color = if (isSelected) Color(0xFFDBEAFE) else Color.Gray
                            )
                        }
                    }
                }
            }
        }

        // Full 12-factor Color Grading Sliders
        Text("Manual Color Grading Controls", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFE2E8F0))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 1. Brightness
            SliderControl(
                label = "Brightness",
                value = adjustments.brightness,
                valueRange = -100f..100f,
                displayValue = "${adjustments.brightness.toInt()}",
                onValueChange = { onAdjustmentsChange(adjustments.copy(brightness = it)) },
                testTag = "adjust_brightness_slider"
            )

            // 2. Contrast
            SliderControl(
                label = "Contrast",
                value = adjustments.contrast,
                valueRange = -100f..100f,
                displayValue = "${adjustments.contrast.toInt()}",
                onValueChange = { onAdjustmentsChange(adjustments.copy(contrast = it)) },
                testTag = "adjust_contrast_slider"
            )

            // 3. Saturation
            SliderControl(
                label = "Saturation",
                value = adjustments.saturation,
                valueRange = -100f..100f,
                displayValue = "${adjustments.saturation.toInt()}",
                onValueChange = { onAdjustmentsChange(adjustments.copy(saturation = it)) },
                testTag = "adjust_saturation_slider"
            )

            // 4. Hue Rotation
            SliderControl(
                label = "Hue Rotation Angle",
                value = adjustments.hue,
                valueRange = -180f..180f,
                displayValue = "${adjustments.hue.toInt()}°",
                onValueChange = { onAdjustmentsChange(adjustments.copy(hue = it)) },
                testTag = "adjust_hue_slider"
            )

            // 5. Exposure
            SliderControl(
                label = "Exposure",
                value = adjustments.exposure,
                valueRange = -100f..100f,
                displayValue = "${adjustments.exposure.toInt()}",
                onValueChange = { onAdjustmentsChange(adjustments.copy(exposure = it)) },
                testTag = "adjust_exposure_slider"
            )

            // 6. Highlights
            SliderControl(
                label = "Highlights",
                value = adjustments.highlights,
                valueRange = -100f..100f,
                displayValue = "${adjustments.highlights.toInt()}",
                onValueChange = { onAdjustmentsChange(adjustments.copy(highlights = it)) },
                testTag = "adjust_highlights_slider"
            )

            // 7. Shadows
            SliderControl(
                label = "Shadows",
                value = adjustments.shadows,
                valueRange = -100f..100f,
                displayValue = "${adjustments.shadows.toInt()}",
                onValueChange = { onAdjustmentsChange(adjustments.copy(shadows = it)) },
                testTag = "adjust_shadows_slider"
            )

            // 8. Temperature
            SliderControl(
                label = "Temperature (Cool ↔ Warm)",
                value = adjustments.temperature,
                valueRange = -100f..100f,
                displayValue = if (adjustments.temperature < 0) "${adjustments.temperature.toInt()} Cool" else "+${adjustments.temperature.toInt()} Warm",
                onValueChange = { onAdjustmentsChange(adjustments.copy(temperature = it)) },
                testTag = "adjust_temp_slider"
            )

            // 9. Tint
            SliderControl(
                label = "Tint (Green ↔ Magenta)",
                value = adjustments.tint,
                valueRange = -100f..100f,
                displayValue = if (adjustments.tint < 0) "${adjustments.tint.toInt()} Green" else "+${adjustments.tint.toInt()} Magenta",
                onValueChange = { onAdjustmentsChange(adjustments.copy(tint = it)) },
                testTag = "adjust_tint_slider"
            )

            // 10. Vignette
            SliderControl(
                label = "Vignette Darkening",
                value = adjustments.vignette,
                valueRange = 0f..100f,
                displayValue = "${adjustments.vignette.toInt()}%",
                onValueChange = { onAdjustmentsChange(adjustments.copy(vignette = it)) },
                testTag = "adjust_vignette_slider"
            )

            // 11. Sharpness
            SliderControl(
                label = "Sharpness / Clarity",
                value = adjustments.sharpness,
                valueRange = -100f..100f,
                displayValue = "${adjustments.sharpness.toInt()}",
                onValueChange = { onAdjustmentsChange(adjustments.copy(sharpness = it)) },
                testTag = "adjust_sharpness_slider"
            )

            // 12. Fade
            SliderControl(
                label = "Fade (Lift Black Floor)",
                value = adjustments.fade,
                valueRange = 0f..100f,
                displayValue = "${adjustments.fade.toInt()}%",
                onValueChange = { onAdjustmentsChange(adjustments.copy(fade = it)) },
                testTag = "adjust_fade_slider"
            )
        }
    }
}
