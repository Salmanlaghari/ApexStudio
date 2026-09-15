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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.domain.model.ChromaKeySettings
import com.apexstudio.app.ui.theme.ApexPalette

@Composable
fun ChromaKeyPanel(
    settings: ChromaKeySettings,
    onUpdate: (ChromaKeySettings) -> Unit,
    onPickCustomBg: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(Color(0xFF141420))
            .border(1.dp, Color(0xFF2A2A3E), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = ApexPalette.NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "3D Chroma Key & Virtual Background",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Enable Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1C1C2E))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Chroma Key Enabled", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Remove green/blue background with 3D projection", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
            }
            Switch(
                checked = settings.enabled,
                onCheckedChange = { onUpdate(settings.copy(enabled = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ApexPalette.NeonCyan,
                    checkedTrackColor = ApexPalette.NeonCyan.copy(alpha = 0.4f)
                )
            )
        }

        if (settings.enabled) {
            Spacer(Modifier.height(14.dp))

            // Key Color Preset Row
            Text("KEY COLOR", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val colorOptions = listOf(
                    Triple("Green Screen", 0xFF00FF00L, Color(0xFF00E676)),
                    Triple("Blue Screen", 0xFF0000FFL, Color(0xFF2979FF)),
                    Triple("Magenta Screen", 0xFFFF00FFL, Color(0xFFE040FB))
                )
                colorOptions.forEach { (label, argb, color) ->
                    val isSelected = settings.keyColorArgb == argb
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) color.copy(alpha = 0.25f) else Color(0xFF1E1E2C))
                            .border(if (isSelected) 1.5.dp else 1.dp, if (isSelected) color else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable { onUpdate(settings.copy(keyColorArgb = argb)) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color))
                            Text(label, color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Sliders: Similarity
            SliderRow(
                label = "Similarity / Tolerance",
                value = settings.similarity,
                onValueChange = { onUpdate(settings.copy(similarity = it)) }
            )

            // Sliders: Smoothness
            SliderRow(
                label = "Smoothness / Edge Feather",
                value = settings.smoothness,
                onValueChange = { onUpdate(settings.copy(smoothness = it)) }
            )

            // Sliders: Spill Suppression
            SliderRow(
                label = "Spill Suppression",
                value = settings.spillSuppression,
                onValueChange = { onUpdate(settings.copy(spillSuppression = it)) }
            )

            // 3D Depth & Tilt
            SliderRow(
                label = "3D Virtual Depth",
                value = settings.depth3D,
                onValueChange = { onUpdate(settings.copy(depth3D = it)) }
            )

            Spacer(Modifier.height(12.dp))

            // Virtual Background Selection
            Text("VIRTUAL BACKGROUND", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            val bgTypes = listOf(
                "studio_neon" to "Cyber Studio",
                "sci_fi" to "Neon Grid",
                "blur_bokeh" to "Bokeh Blur",
                "cinema_gradient" to "Cinema",
                "custom" to "Custom Media"
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(bgTypes) { (type, label) ->
                    val isSelected = settings.backgroundType == type
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) ApexPalette.NeonCyan.copy(alpha = 0.2f) else Color(0xFF1E1E2E))
                            .border(if (isSelected) 1.5.dp else 1.dp, if (isSelected) ApexPalette.NeonCyan else Color(0xFF2F2F44), RoundedCornerShape(8.dp))
                            .clickable {
                                if (type == "custom") {
                                    onPickCustomBg()
                                    onUpdate(settings.copy(backgroundType = "custom"))
                                } else {
                                    onUpdate(settings.copy(backgroundType = type))
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (type == "custom") {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = if (isSelected) ApexPalette.NeonCyan else Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = label,
                                color = if (isSelected) ApexPalette.NeonCyan else Color.White,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
            Text("${(value * 100).toInt()}%", color = ApexPalette.NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = ApexPalette.NeonCyan,
                activeTrackColor = ApexPalette.NeonCyan,
                inactiveTrackColor = Color(0xFF2A2A3E)
            )
        )
    }
}
