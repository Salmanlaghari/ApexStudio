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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.apexstudio.app.domain.model.VideoAdjustments
import com.apexstudio.app.ui.theme.ApexPalette

private data class AdjustItem(
    val id: String,
    val label: String,
    val iconEmoji: String,
    val valueRange: ClosedFloatingPointRange<Float>,
    val step: Float,
    val getValue: (VideoAdjustments) -> Float,
    val updateValue: (VideoAdjustments, Float) -> VideoAdjustments,
    val formatValue: (Float) -> String = { "%.0f".format(it * 100) }
)

private val ADJUST_ITEMS = listOf(
    AdjustItem("hdr", "HDR+", "✨", 0f..1f, 0.05f, { it.hdr }, { a, v -> a.copy(hdr = v.coerceIn(0f, 1f)) }, { "%.0f%%".format(it * 100) }),
    AdjustItem("brightness", "Brightness", "☀️", -1f..1f, 0.05f, { it.brightness }, { a, v -> a.copy(brightness = v.coerceIn(-1f, 1f)) }, { "%+.0f".format(it * 100) }),
    AdjustItem("brilliance", "Brilliance", "🌟", -1f..1f, 0.05f, { it.brilliance }, { a, v -> a.copy(brilliance = v.coerceIn(-1f, 1f)) }, { "%+.0f".format(it * 100) }),
    AdjustItem("contrast", "Contrast", "🌓", 0f..2f, 0.05f, { it.contrast }, { a, v -> a.copy(contrast = v.coerceIn(0f, 2f)) }, { "%.0f%%".format(it * 100) }),
    AdjustItem("saturation", "Saturation", "🎨", 0f..2f, 0.05f, { it.saturation }, { a, v -> a.copy(saturation = v.coerceIn(0f, 2f)) }, { "%.0f%%".format(it * 100) }),
    AdjustItem("exposure", "Exposure", "📸", -1f..1f, 0.05f, { it.exposure }, { a, v -> a.copy(exposure = v.coerceIn(-1f, 1f)) }, { "%+.0f".format(it * 100) }),
    AdjustItem("highlights", "Highlights", "🔆", -1f..1f, 0.05f, { it.highlights }, { a, v -> a.copy(highlights = v.coerceIn(-1f, 1f)) }, { "%+.0f".format(it * 100) }),
    AdjustItem("shadows", "Shadows", "👥", -1f..1f, 0.05f, { it.shadows }, { a, v -> a.copy(shadows = v.coerceIn(-1f, 1f)) }, { "%+.0f".format(it * 100) }),
    AdjustItem("temp", "Warmth", "🌡️", -1f..1f, 0.05f, { it.temperature }, { a, v -> a.copy(temperature = v.coerceIn(-1f, 1f)) }, { "%+.0f".format(it * 100) }),
    AdjustItem("tint", "Tint", "🌸", -1f..1f, 0.05f, { it.tint }, { a, v -> a.copy(tint = v.coerceIn(-1f, 1f)) }, { "%+.0f".format(it * 100) }),
    AdjustItem("sharpness", "Sharpness", "🔪", 0f..1f, 0.05f, { it.sharpness }, { a, v -> a.copy(sharpness = v.coerceIn(0f, 1f)) }, { "%.0f%%".format(it * 100) }),
    AdjustItem("fade", "Fade", "🌫️", 0f..1f, 0.05f, { it.fade }, { a, v -> a.copy(fade = v.coerceIn(0f, 1f)) }, { "%.0f%%".format(it * 100) }),
    AdjustItem("vignette", "Vignette", "🎯", 0f..1f, 0.05f, { it.vignette }, { a, v -> a.copy(vignette = v.coerceIn(0f, 1f)) }, { "%.0f%%".format(it * 100) }),
    AdjustItem("grain", "Film Grain", "📽️", 0f..1f, 0.05f, { it.grain }, { a, v -> a.copy(grain = v.coerceIn(0f, 1f)) }, { "%.0f%%".format(it * 100) })
)

@Composable
fun AdjustPanel(
    adjustments: VideoAdjustments,
    onUpdate: ((VideoAdjustments) -> VideoAdjustments) -> Unit,
    onReset: () -> Unit,
    onResetAll: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedAdjustId by remember { mutableStateOf("hdr") }
    val activeItem = ADJUST_ITEMS.firstOrNull { it.id == selectedAdjustId } ?: ADJUST_ITEMS.first()
    val currentValue = activeItem.getValue(adjustments)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp, topStart = 16.dp, topEnd = 16.dp))
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
                RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp, topStart = 16.dp, topEnd = 16.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // TOP HEADER: Title + Active status + Reset All + Close
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
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        tint = ApexPalette.NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Pro Color & Light Adjust",
                            color = ApexPalette.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (!adjustments.isDefault) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(ApexPalette.NeonCyan.copy(alpha = 0.2f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text("ACTIVE", color = ApexPalette.NeonCyan, fontSize = 8.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    Text(
                        "14 Professional Parameters • Real-time Hardware GL",
                        color = ApexPalette.TextTertiary,
                        fontSize = 9.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ApexPalette.BgElevated)
                        .border(0.5.dp, ApexPalette.NeonPink.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .clickable { onResetAll() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = ApexPalette.NeonPink, modifier = Modifier.size(12.dp))
                        Text("Reset All", color = ApexPalette.NeonPink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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

        // MOVED TO TOP: 14 Adjustment Feature Categories Strip
        // Placed at the very top of the controls so the user sees all options immediately!
        Text(
            "SELECT PARAMETER (14 FEATURES)",
            color = ApexPalette.TextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
        Spacer(Modifier.height(4.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(ADJUST_ITEMS) { item ->
                val isSelected = item.id == selectedAdjustId
                val valForDisplay = item.getValue(adjustments)
                val isModified = when (item.id) {
                    "contrast", "saturation" -> valForDisplay != 1f
                    else -> valForDisplay != 0f
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) ApexPalette.NeonCyan.copy(alpha = 0.22f)
                            else ApexPalette.BgElevated
                        )
                        .border(
                            1.5.dp,
                            if (isSelected) ApexPalette.NeonCyan
                            else if (isModified) ApexPalette.NeonAmber
                            else ApexPalette.BorderGlass,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { selectedAdjustId = item.id }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(item.iconEmoji, fontSize = 12.sp)
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                item.label,
                                color = if (isSelected) ApexPalette.NeonCyan else ApexPalette.TextPrimary,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                            )
                            Text(
                                item.formatValue(valForDisplay),
                                color = if (isModified) ApexPalette.NeonAmber else ApexPalette.TextTertiary,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Selected Adjustment Slider + Stepper Controls + Current Value
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ApexPalette.BgBase)
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(activeItem.iconEmoji, fontSize = 14.sp)
                    Text(
                        activeItem.label,
                        color = ApexPalette.NeonCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Reset single parameter button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ApexPalette.BgElevated)
                            .clickable {
                                val defaultVal = when (activeItem.id) {
                                    "contrast", "saturation" -> 1f
                                    else -> 0f
                                }
                                onUpdate { current -> activeItem.updateValue(current, defaultVal) }
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Reset", color = ApexPalette.TextSecondary, fontSize = 9.sp)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ApexPalette.NeonCyan.copy(alpha = 0.15f))
                            .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            activeItem.formatValue(currentValue),
                            color = ApexPalette.NeonCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Slider with Minus & Plus buttons for precision
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Minus button
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(ApexPalette.BgElevated)
                        .border(1.dp, ApexPalette.BorderGlass, CircleShape)
                        .clickable {
                            val nextVal = (currentValue - activeItem.step).coerceIn(activeItem.valueRange)
                            onUpdate { current -> activeItem.updateValue(current, nextVal) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("−", color = ApexPalette.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Slider(
                    value = currentValue,
                    onValueChange = { newValue ->
                        onUpdate { current -> activeItem.updateValue(current, newValue) }
                    },
                    valueRange = activeItem.valueRange,
                    colors = SliderDefaults.colors(
                        thumbColor = ApexPalette.NeonCyan,
                        activeTrackColor = ApexPalette.NeonCyan,
                        inactiveTrackColor = ApexPalette.BgElevated
                    ),
                    modifier = Modifier.weight(1f)
                )

                // Plus button
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(ApexPalette.BgElevated)
                        .border(1.dp, ApexPalette.BorderGlass, CircleShape)
                        .clickable {
                            val nextVal = (currentValue + activeItem.step).coerceIn(activeItem.valueRange)
                            onUpdate { current -> activeItem.updateValue(current, nextVal) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = ApexPalette.TextPrimary, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Quick Preset Color Grade Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val presets = listOf(
                "✨ Auto HDR" to {
                    onUpdate {
                        it.copy(hdr = 0.65f, contrast = 1.15f, saturation = 1.12f, highlights = -0.1f, shadows = 0.15f)
                    }
                },
                "🌅 Warm Sun" to {
                    onUpdate {
                        it.copy(temperature = 0.35f, brilliance = 0.15f, highlights = -0.1f, vignette = 0.2f)
                    }
                },
                "❄️ Cool Punch" to {
                    onUpdate {
                        it.copy(temperature = -0.25f, contrast = 1.2f, saturation = 1.1f, sharpness = 0.25f)
                    }
                },
                "🎞️ Vintage Film" to {
                    onUpdate {
                        it.copy(fade = 0.35f, grain = 0.4f, temperature = 0.15f, contrast = 0.95f)
                    }
                }
            )

            presets.forEach { (name, action) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ApexPalette.BgElevated)
                        .border(0.5.dp, ApexPalette.BorderGlass, RoundedCornerShape(8.dp))
                        .clickable { action() }
                        .padding(vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        name,
                        color = ApexPalette.TextPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
