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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.domain.model.ChromaKeySettings
import com.apexstudio.app.ui.theme.ApexPalette

private data class ColorPreset(val label: String, val argb: Long, val displayColor: Color)
private data class BgPreset(val id: String, val label: String, val colors: List<Color>)

@Composable
fun ChromaKeyPanel(
    settings: ChromaKeySettings,
    onUpdate: (ChromaKeySettings) -> Unit,
    onPickCustomBg: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorPresets = remember {
        listOf(
            ColorPreset("Green Screen", 0xFF00E676, Color(0xFF00E676)),
            ColorPreset("Blue Screen", 0xFF2979FF, Color(0xFF2979FF)),
            ColorPreset("Magenta", 0xFFE040FB, Color(0xFFE040FB)),
            ColorPreset("Cyan", 0xFF00E5FF, Color(0xFF00E5FF)),
            ColorPreset("Custom Red", 0xFFFF1744, Color(0xFFFF1744))
        )
    }

    val bgPresets = remember {
        listOf(
            BgPreset("cyber_portal", "Cyber Portal", listOf(Color(0xFF050515), Color(0xFF00F0FF), Color(0xFFFF007F))),
            BgPreset("neon_city", "Neon Tokyo", listOf(Color(0xFF0A001A), Color(0xFF8A00FF), Color(0xFFFF4081))),
            BgPreset("virtual_studio", "Virtual Studio", listOf(Color(0xFF101420), Color(0xFF1E3A8A), Color(0xFF38BDF8))),
            BgPreset("deep_space", "Deep Space", listOf(Color(0xFF030308), Color(0xFF311042), Color(0xFF1E1B4B))),
            BgPreset("matrix_grid", "Matrix Grid", listOf(Color(0xFF001100), Color(0xFF004400), Color(0xFF00FF66))),
            BgPreset("transparent", "Cutout (Alpha)", listOf(Color(0xFF333333), Color(0xFF555555)))
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(ApexPalette.BgSurface)
            .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.35f), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.linearGradient(listOf(ApexPalette.NeonCyan, ApexPalette.NeonPurple))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Layers, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                }
                Column {
                    Text("3D ChromaKey VFX", color = ApexPalette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Real-Time Green Screen & 3D Extrusion", color = ApexPalette.TextSecondary, fontSize = 10.sp)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = { onUpdate(settings.copy(enabled = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ApexPalette.NeonCyan,
                        uncheckedTrackColor = ApexPalette.BgElevated
                    )
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(ApexPalette.BgElevated)
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = ApexPalette.TextSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Key Color Selector
        Text("1. KEY COLOR", color = ApexPalette.NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            colorPresets.forEach { preset ->
                val isSelected = settings.keyColorArgb == preset.argb
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(preset.displayColor.copy(alpha = if (isSelected) 1f else 0.45f))
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Color.White else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            onUpdate(settings.copy(enabled = true, keyColorArgb = preset.argb))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Chroma Sliders
        Text("2. MATTE PRECISION", color = ApexPalette.NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))

        ChromaSliderRow(
            label = "Similarity (Tolerance)",
            value = settings.similarity,
            valueRange = 0.05f..0.95f,
            format = { "%.0f%%".format(it * 100) },
            onValueChange = { onUpdate(settings.copy(similarity = it)) }
        )

        ChromaSliderRow(
            label = "Smoothness (Soft Edge)",
            value = settings.smoothness,
            valueRange = 0.01f..0.60f,
            format = { "%.0f%%".format(it * 100) },
            onValueChange = { onUpdate(settings.copy(smoothness = it)) }
        )

        ChromaSliderRow(
            label = "Spill Suppression",
            value = settings.spillSuppression,
            valueRange = 0f..1f,
            format = { "%.0f%%".format(it * 100) },
            onValueChange = { onUpdate(settings.copy(spillSuppression = it)) }
        )

        Spacer(Modifier.height(14.dp))

        // 3D Parallax & Depth Controls
        Text("3. 3D VFX & PARALLAX", color = ApexPalette.NeonPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))

        ChromaSliderRow(
            label = "3D Depth & Shadow",
            value = settings.depth3D,
            valueRange = 0f..1f,
            format = { "%.0f%%".format(it * 100) },
            onValueChange = { onUpdate(settings.copy(depth3D = it)) }
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                ChromaSliderRow(
                    label = "Tilt X",
                    value = settings.tiltX,
                    valueRange = -30f..30f,
                    format = { "%.0f°".format(it) },
                    onValueChange = { onUpdate(settings.copy(tiltX = it)) }
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                ChromaSliderRow(
                    label = "Tilt Y",
                    value = settings.tiltY,
                    valueRange = -30f..30f,
                    format = { "%.0f°".format(it) },
                    onValueChange = { onUpdate(settings.copy(tiltY = it)) }
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // 3D Background Replacement
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("4. VIRTUAL 3D BACKGROUND", color = ApexPalette.NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(
                "Pick Image",
                color = ApexPalette.NeonCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onPickCustomBg() }
            )
        }
        Spacer(Modifier.height(6.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(bgPresets) { bg ->
                val isSelected = settings.backgroundType == bg.id
                Box(
                    modifier = Modifier
                        .width(96.dp)
                        .height(60.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.verticalGradient(bg.colors))
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) ApexPalette.NeonCyan else Color(0xFF2A2A3C),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            onUpdate(settings.copy(enabled = true, backgroundType = bg.id))
                        }
                        .padding(6.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Text(
                        text = bg.label,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Reset Settings Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(
                onClick = {
                    onUpdate(ChromaKeySettings(enabled = true))
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ApexPalette.NeonPink),
                border = androidx.compose.foundation.BorderStroke(1.dp, ApexPalette.NeonPink.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Reset Defaults", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ChromaSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = ApexPalette.TextSecondary, fontSize = 11.sp)
            Text(format(value), color = ApexPalette.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = ApexPalette.NeonCyan,
                activeTrackColor = ApexPalette.NeonCyan,
                inactiveTrackColor = Color(0xFF2A2A3C)
            ),
            modifier = Modifier.height(28.dp)
        )
    }
}
