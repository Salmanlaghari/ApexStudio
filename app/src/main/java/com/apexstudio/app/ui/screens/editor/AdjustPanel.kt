package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.domain.model.VideoAdjustments
import com.apexstudio.app.ui.theme.ApexPalette

private data class AdjustItem(
    val id: String,
    val label: String,
    val valueRange: ClosedFloatingPointRange<Float>,
    val getValue: (VideoAdjustments) -> Float,
    val updateValue: (VideoAdjustments, Float) -> VideoAdjustments,
    val formatValue: (Float) -> String = { "%.0f".format(it * 100) }
)

private val ADJUST_ITEMS = listOf(
    AdjustItem("brightness", "Brightness", -1f..1f, { it.brightness }, { a, v -> a.copy(brightness = v) }),
    AdjustItem("contrast", "Contrast", 0f..2f, { it.contrast }, { a, v -> a.copy(contrast = v) }, { "%.0f".format((it - 1f) * 100) }),
    AdjustItem("saturation", "Saturation", 0f..2f, { it.saturation }, { a, v -> a.copy(saturation = v) }, { "%.0f".format((it - 1f) * 100) }),
    AdjustItem("exposure", "Exposure", -1f..1f, { it.exposure }, { a, v -> a.copy(exposure = v) }),
    AdjustItem("highlights", "Highlights", -1f..1f, { it.highlights }, { a, v -> a.copy(highlights = v) }),
    AdjustItem("shadows", "Shadows", -1f..1f, { it.shadows }, { a, v -> a.copy(shadows = v) }),
    AdjustItem("temp", "Temperature", -1f..1f, { it.temperature }, { a, v -> a.copy(temperature = v) }),
    AdjustItem("tint", "Tint", -1f..1f, { it.tint }, { a, v -> a.copy(tint = v) }),
    AdjustItem("sharpness", "Sharpness", 0f..1f, { it.sharpness }, { a, v -> a.copy(sharpness = v) }, { "%.0f".format(it * 100) }),
    AdjustItem("fade", "Fade", 0f..1f, { it.fade }, { a, v -> a.copy(fade = v) }, { "%.0f".format(it * 100) }),
    AdjustItem("vignette", "Vignette", 0f..1f, { it.vignette }, { a, v -> a.copy(vignette = v) }, { "%.0f".format(it * 100) }),
    AdjustItem("grain", "Grain", 0f..1f, { it.grain }, { a, v -> a.copy(grain = v) }, { "%.0f".format(it * 100) })
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
    var selectedAdjustId by remember { mutableStateOf("brightness") }
    val activeItem = ADJUST_ITEMS.firstOrNull { it.id == selectedAdjustId } ?: ADJUST_ITEMS.first()
    val currentValue = activeItem.getValue(adjustments)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(ApexPalette.BgSurface)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Adjustments",
                    color = ApexPalette.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                if (!adjustments.isDefault) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ApexPalette.NeonCyan.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("ACTIVE", color = ApexPalette.NeonCyan, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ApexPalette.BgElevated)
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
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(ApexPalette.BgElevated)
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = ApexPalette.TextSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Selected Adjustment Slider + Value
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ApexPalette.BgBase)
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    activeItem.label,
                    color = ApexPalette.NeonCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    activeItem.formatValue(currentValue),
                    color = ApexPalette.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black
                )
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
                )
            )
        }

        Spacer(Modifier.height(10.dp))

        // Adjustment Categories Strip
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ADJUST_ITEMS) { item ->
                val isSelected = item.id == selectedAdjustId
                val valForDisplay = item.getValue(adjustments)
                val isModified = item.id in listOf("brightness", "exposure", "highlights", "shadows", "temp", "tint", "sharpness", "fade", "vignette", "grain") && valForDisplay != 0f ||
                        item.id in listOf("contrast", "saturation") && valForDisplay != 1f

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) ApexPalette.NeonCyan.copy(alpha = 0.2f)
                            else ApexPalette.BgElevated
                        )
                        .border(
                            1.dp,
                            if (isSelected) ApexPalette.NeonCyan
                            else if (isModified) ApexPalette.NeonAmber
                            else ApexPalette.BorderGlass,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { selectedAdjustId = item.id }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            item.label,
                            color = if (isSelected) ApexPalette.NeonCyan else ApexPalette.TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            item.formatValue(valForDisplay),
                            color = if (isModified) ApexPalette.NeonAmber else ApexPalette.TextTertiary,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}
