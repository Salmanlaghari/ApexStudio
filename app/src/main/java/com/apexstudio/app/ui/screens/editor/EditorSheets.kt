package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.ui.theme.ApexPalette
import kotlinx.coroutines.isActive

@Composable
fun SpeedControlSheet(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    onClose: () -> Unit
) {
    androidx.compose.material3.Surface(
        color = Color(0xFF14141E),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = ApexPalette.NeonCyan)
                    Text("Speed Control", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                androidx.compose.material3.IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            // Speed Presets Row
            val presets = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 4.0f)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                presets.forEach { speed ->
                    val isSelected = kotlin.math.abs(currentSpeed - speed) < 0.05f
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) ApexPalette.NeonCyan else Color(0xFF222232))
                            .clickable { onSpeedChange(speed) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${speed}x",
                            color = if (isSelected) Color.Black else Color.White,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // Slider
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Custom Speed", color = Color(0xFF9CA3AF), fontSize = 12.sp)
                    Text(String.format(java.util.Locale.US, "%.2fx", currentSpeed), color = ApexPalette.NeonCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                androidx.compose.material3.Slider(
                    value = currentSpeed,
                    onValueChange = { onSpeedChange(it) },
                    valueRange = 0.25f..8.0f,
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = ApexPalette.NeonCyan,
                        activeTrackColor = ApexPalette.NeonCyan,
                        inactiveTrackColor = Color(0xFF2A2A3C)
                    )
                )
            }
        }
    }
}

// Add Media Menu Sheet
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun AddMediaMenuSheet(
    onPickVideo: () -> Unit,
    onPickOverlay: () -> Unit,
    onPickAudio: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ApexPalette.BgElevated,
        scrimColor = Color.Black.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Add to project",
                color = ApexPalette.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            AddMediaRow(
                icon = Icons.Default.VideoLibrary,
                title = "Video clip",
                subtitle = "Add to the V1 timeline lane",
                tint = ApexPalette.NeonCyan,
                onClick = {
                    onPickVideo()
                    onDismiss()
                }
            )
            AddMediaRow(
                icon = Icons.Default.GraphicEq,
                title = "Audio",
                subtitle = "Add to the A1 audio lane",
                tint = ApexPalette.NeonEmerald,
                onClick = {
                    onPickAudio()
                    onDismiss()
                }
            )
            AddMediaRow(
                icon = Icons.Default.Layers,
                title = "Overlay clip",
                subtitle = "Add as a picture-in-picture overlay (V2)",
                tint = ApexPalette.NeonPurple,
                onClick = {
                    onPickOverlay()
                    onDismiss()
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AddMediaRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = ApexPalette.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = ApexPalette.TextSecondary,
                fontSize = 11.sp
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = ApexPalette.TextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}
