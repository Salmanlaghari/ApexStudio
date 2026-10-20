package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.apexstudio.app.domain.model.VideoDrawingStroke
import com.apexstudio.app.domain.model.VideoDrawingTool
import com.apexstudio.app.ui.theme.EditorPalette as ApexPalette
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
                    Text("Speed Control", color = ApexPalette.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                            color = if (isSelected) Color.Black else ApexPalette.TextPrimary,
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

/** Controls for painting directly over the preview. All edits are supplied by the caller. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun VideoDrawingControlsSheet(
    tool: VideoDrawingTool,
    colorArgb: Long,
    brushWidth: Float,
    strokeCount: Int,
    onToolChange: (VideoDrawingTool) -> Unit,
    onColorChange: (Long) -> Unit,
    onBrushWidthChange: (Float) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = ApexPalette.BgElevated,
        scrimColor = Color.Black.copy(alpha = 0.35f)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Draw on video", color = ApexPalette.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("Marks are saved with the project", color = ApexPalette.TextSecondary, fontSize = 11.sp)
                }
                androidx.compose.material3.IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close drawing tools", tint = ApexPalette.TextPrimary)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DrawingModeChip(Modifier.weight(1f), "Brush", tool == VideoDrawingTool.BRUSH, ApexPalette.NeonCyan) { onToolChange(VideoDrawingTool.BRUSH) }
                DrawingModeChip(Modifier.weight(1f), "Eraser", tool == VideoDrawingTool.ERASER, ApexPalette.NeonPink) { onToolChange(VideoDrawingTool.ERASER) }
            }
            Text("COLOR", color = ApexPalette.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf(0xFFFF3B82L, 0xFF19D3F3L, 0xFFFFD54FL, 0xFF73E6A6L, 0xFFFFFFFFL, 0xFF8C6CFFL).forEach { argb ->
                    val color = Color(argb.toInt())
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(50)).background(color)
                            .border(if (colorArgb == argb) 3.dp else 1.dp, if (colorArgb == argb) Color.White else ApexPalette.TextPrimary.copy(alpha = 0.35f), RoundedCornerShape(50))
                            .clickable(enabled = tool == VideoDrawingTool.BRUSH) { onColorChange(argb) }
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Stroke width", color = ApexPalette.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("${(brushWidth * 1000).toInt()}‰", color = ApexPalette.NeonCyan, fontSize = 12.sp)
            }
            androidx.compose.material3.Slider(
                value = brushWidth.coerceIn(VideoDrawingStroke.MIN_WIDTH, 0.06f),
                onValueChange = onBrushWidthChange,
                valueRange = VideoDrawingStroke.MIN_WIDTH..0.06f,
                colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = ApexPalette.NeonCyan, activeTrackColor = ApexPalette.NeonCyan)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DrawingModeChip(Modifier.weight(1f), "Undo  •  $strokeCount", strokeCount > 0, ApexPalette.NeonCyan, enabled = strokeCount > 0, onClick = onUndo)
                DrawingModeChip(Modifier.weight(1f), "Clear all", false, ApexPalette.Danger, enabled = strokeCount > 0, onClick = onClear)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DrawingModeChip(
    modifier: Modifier,
    label: String,
    selected: Boolean,
    tint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier.clip(RoundedCornerShape(9.dp))
            .background(if (selected) tint.copy(alpha = 0.2f) else ApexPalette.BgSurface)
            .border(1.dp, if (selected) tint else ApexPalette.BorderGlass, RoundedCornerShape(9.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (enabled) tint else ApexPalette.TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
