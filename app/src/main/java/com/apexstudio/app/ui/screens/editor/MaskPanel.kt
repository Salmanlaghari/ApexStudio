package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.domain.model.MaskKeyframe
import com.apexstudio.app.domain.model.MaskModel
import com.apexstudio.app.domain.model.MaskPreset
import com.apexstudio.app.domain.model.MaskShape
import com.apexstudio.app.domain.model.VideoMask
import com.apexstudio.app.ui.theme.ApexPalette

/**
 * Compact, mobile-first mask editor. Presets and shape thumbnails are horizontal
 * browsing strips; selecting one immediately sends a complete mask through
 * [onUpdate], so the caller's preview and export stay on the same model path.
 * Opening the sheet is read-only. No tracking or face detection is implied.
 */
@Composable
fun MaskPanel(
    mask: VideoMask?,
    onUpdate: (VideoMask?) -> Unit,
    onClose: () -> Unit,
    keyframeTimeMs: Long = 0L,
    modifier: Modifier = Modifier
) {
    var fineTuneExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(Color(0xFF141420))
            .border(1.dp, Color(0xFF2A2A3E), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .heightIn(max = 380.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Mask", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close mask", tint = Color.White.copy(alpha = 0.75f))
            }
        }

        // Recipes are deliberately first, so common one-tap looks are not
        // buried beneath sliders. Applying a recipe is an explicit replacement.
        Text("PRESETS", color = Color.White.copy(alpha = 0.58f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            MaskPreset.ALL.forEach { preset ->
                val selected = mask?.presetId == preset.id
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f) else Color(0xFF1C1C2E))
                        .border(1.dp, if (selected) MaterialTheme.colorScheme.secondary else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { onUpdate(preset.apply()) }
                        .padding(horizontal = 11.dp, vertical = 8.dp)
                ) {
                    Text(preset.label, color = if (selected) MaterialTheme.colorScheme.secondary else Color.White.copy(alpha = 0.82f), fontSize = 11.sp, maxLines = 1)
                }
            }
        }

        Spacer(Modifier.height(11.dp))
        Text("SHAPE", color = Color.White.copy(alpha = 0.58f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Horizontal mask shape strip" }
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            MaskShape.entries.forEach { shape ->
                val selected = mask?.shape == shape
                Column(
                    modifier = Modifier
                        .width(68.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color(0xFF1C1C2E))
                        .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable {
                            val next = MaskModel.selectShape(shape, mask, keyframeTimeMs)
                            onUpdate(next)
                        }
                        .semantics { contentDescription = "Mask shape ${shape.label}" }
                        .padding(horizontal = 6.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    MaskShapePreview(shape, Modifier.size(width = 34.dp, height = 26.dp))
                    Text(
                        shape.label,
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f),
                        fontSize = 9.sp,
                        maxLines = 1,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        if (mask == null) {
            Spacer(Modifier.height(9.dp))
            Text("Choose a preset or shape to add a centered mask.", color = Color.White.copy(alpha = 0.56f), fontSize = 11.sp)
        }

        Spacer(Modifier.height(9.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (mask != null) {
                val active = mask
                val stamped = active.maskKeyframes.any { it.timeMs == keyframeTimeMs }
                OutlinedButton(
                    onClick = {
                        val stamp = MaskKeyframe(
                            id = "mask-$keyframeTimeMs", timeMs = keyframeTimeMs,
                            centerX = active.centerX, centerY = active.centerY,
                            sizeX = active.sizeX, sizeY = active.sizeY,
                            rotationDeg = active.rotationDeg, feather = active.feather,
                            opacity = active.opacity, invert = active.invert
                        )
                        onUpdate(active.copy(maskKeyframes = (active.maskKeyframes.filterNot { it.timeMs == keyframeTimeMs } + stamp).sortedBy { it.timeMs }))
                    },
                    contentPadding = PaddingValues(horizontal = 11.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) { Text(if (stamped) "Update key" else "Add key", fontSize = 11.sp, maxLines = 1) }

                if (active.maskKeyframes.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { onUpdate(active.copy(maskKeyframes = emptyList())) },
                        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
                    ) { Text("Clear keys ${active.maskKeyframes.size}", fontSize = 11.sp, maxLines = 1) }
                }
                OutlinedButton(
                    onClick = { onUpdate(null) },
                    contentPadding = PaddingValues(horizontal = 11.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Remove", fontSize = 11.sp, maxLines = 1)
                }
            }
        }

        if (mask != null) {
            Spacer(Modifier.height(7.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1C1C2E))
                    .clickable { fineTuneExpanded = !fineTuneExpanded }
                    .padding(horizontal = 11.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text("Fine-tune", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(if (fineTuneExpanded) "−" else "+", color = MaterialTheme.colorScheme.primary, fontSize = 17.sp)
            }

            if (fineTuneExpanded) {
                Spacer(Modifier.height(9.dp))
                MaskSliderRow("Feather", "${(mask.feather * 100).toInt()}%", mask.feather) { onUpdate(mask.copy(feather = it, presetId = null)) }
                MaskSliderRow("Opacity", "${(mask.opacity * 100).toInt()}%", mask.opacity) { onUpdate(mask.copy(opacity = it, presetId = null)) }
                MaskSliderRow("Rotation", "${mask.rotationDeg.toInt()}°", ((mask.rotationDeg + 180f) / 360f).coerceIn(0f, 1f)) {
                    onUpdate(mask.copy(rotationDeg = it * 360f - 180f, presetId = null))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Invert mask", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("Keep everything outside the shape", color = Color.White.copy(alpha = 0.56f), fontSize = 10.sp)
                    }
                    Switch(
                        checked = mask.invert,
                        onCheckedChange = { onUpdate(mask.copy(invert = it, presetId = null)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    )
                }
            }
        }
    }
}

@Composable
private fun MaskShapePreview(shape: MaskShape, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier.semantics { contentDescription = "${shape.label} mask preview" }) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 1.8.dp.toPx())
        val color = accent
        val inset = 2.dp.toPx()
        when (shape) {
            MaskShape.RECTANGLE -> drawRect(color, Offset(inset, inset), Size(w - inset * 2, h - inset * 2), style = stroke)
            MaskShape.ELLIPSE, MaskShape.CIRCLE -> drawOval(color, Offset(inset, inset), Size(w - inset * 2, h - inset * 2), style = stroke)
            MaskShape.LINEAR -> {
                drawRect(Color.White.copy(alpha = 0.12f), Offset(inset, inset), Size(w - inset * 2, h - inset * 2))
                drawLine(color, Offset(w * 0.52f, inset), Offset(w * 0.52f, h - inset), strokeWidth = 2.dp.toPx())
                drawLine(color.copy(alpha = 0.35f), Offset(w * 0.35f, inset), Offset(w * 0.35f, h - inset), strokeWidth = 1.dp.toPx())
            }
            MaskShape.MIRROR -> {
                drawLine(color, Offset(w / 2f, inset), Offset(w / 2f, h - inset), strokeWidth = 2.dp.toPx())
                drawLine(color, Offset(inset, inset), Offset(w / 2f, h / 2f), strokeWidth = 1.5.dp.toPx())
                drawLine(color, Offset(w - inset, inset), Offset(w / 2f, h / 2f), strokeWidth = 1.5.dp.toPx())
            }
            MaskShape.BLUR_FACE -> {
                drawOval(color.copy(alpha = 0.3f), Offset(inset, inset), Size(w - inset * 2, h - inset * 2), style = stroke)
                drawCircle(color, radius = 2.dp.toPx(), center = Offset(w * 0.36f, h * 0.42f))
                drawCircle(color, radius = 2.dp.toPx(), center = Offset(w * 0.64f, h * 0.42f))
                drawLine(color, Offset(w * 0.38f, h * 0.67f), Offset(w * 0.62f, h * 0.67f), strokeWidth = 1.5.dp.toPx())
            }
            else -> {
                val points = MaskModel.outline(shape)
                if (points.isNotEmpty()) {
                    val path = Path().apply {
                        points.forEachIndexed { i, p ->
                            val x = w / 2f + p.x * (w - inset * 2) / 2f
                            val y = h / 2f + p.y * (h - inset * 2) / 2f
                            if (i == 0) moveTo(x, y) else lineTo(x, y)
                        }
                        close()
                    }
                    drawPath(path, color, style = stroke)
                } else {
                    drawLine(color, Offset(inset, h - inset), Offset(w - inset, inset), strokeWidth = 2.dp.toPx())
                }
            }
        }
    }
}

@Composable
private fun MaskSliderRow(label: String, valueLabel: String, value: Float, onValueChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(valueLabel, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            modifier = Modifier.height(38.dp),
            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
        )
    }
}
