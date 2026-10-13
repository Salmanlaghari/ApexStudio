package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.domain.model.MaskShape
import com.apexstudio.app.domain.model.VideoMask
import com.apexstudio.app.ui.theme.ApexPalette

/**
 * Mask tool bottom sheet (CapCut-style Mask).
 *
 * Shape picker (rectangle / circle / linear wipe), feather + opacity
 * sliders, invert toggle, and remove. The mask itself is positioned
 * and sized by dragging directly on the preview while this panel is
 * open ([MaskPreviewOverlay] edit mode).
 *
 * Every control here is live: [onUpdate] receives the new [VideoMask]
 * and the caller persists it.
 */
@Composable
fun MaskPanel(
    mask: VideoMask?,
    onUpdate: (VideoMask?) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    // NOTE: there is intentionally no `?: VideoMask.default()` fallback
    // here. When the mask is null (removed), the panel shows the
    // no-mask state below; every control below reads `mask` directly so
    // a slider touch can never resurrect a removed mask.

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
                    imageVector = Icons.Default.Crop,
                    contentDescription = null,
                    tint = ApexPalette.NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Mask",
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

        Spacer(Modifier.height(6.dp))
        Text(
            text = "Drag on the preview to move • drag the handle to resize",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 11.sp
        )
        Spacer(Modifier.height(14.dp))

        // Shape picker
        Text("SHAPE", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MaskShape.entries.forEach { shape ->
                val selected = mask?.shape == shape
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) ApexPalette.NeonCyan.copy(alpha = 0.18f) else Color(0xFF1C1C2E))
                        .border(
                            1.5.dp,
                            if (selected) ApexPalette.NeonCyan else Color.Transparent,
                            RoundedCornerShape(12.dp)
                        )
                        // Tapping a shape with no mask creates one; with a
                        // mask it switches shape. Either way the mask param
                        // (not a synthesized default) is the source of truth.
                        .clickable { onUpdate((mask ?: VideoMask.default()).copy(shape = shape)) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = shape.label,
                        color = if (selected) ApexPalette.NeonCyan else Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        // Sliders + invert only exist when there IS a mask. When the mask
        // was removed these controls are hidden (with a hint instead), so
        // touching a slider can never resurrect a removed mask.
        val activeMask = mask
        if (activeMask != null) {
        Spacer(Modifier.height(16.dp))

        // Feather slider
        MaskSliderRow(
            label = "Feather",
            valueLabel = "${(activeMask.feather * 100).toInt()}%",
            value = activeMask.feather,
            onValueChange = { onUpdate(activeMask.copy(feather = it)) }
        )
        Spacer(Modifier.height(10.dp))

        // Opacity slider
        MaskSliderRow(
            label = "Opacity",
            valueLabel = "${(activeMask.opacity * 100).toInt()}%",
            value = activeMask.opacity,
            onValueChange = { onUpdate(activeMask.copy(opacity = it)) }
        )
        Spacer(Modifier.height(14.dp))

        // Invert toggle
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
                Text("Invert mask", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Keep everything outside the shape",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
            }
            Switch(
                checked = activeMask.invert,
                onCheckedChange = { onUpdate(activeMask.copy(invert = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ApexPalette.NeonCyan,
                    checkedTrackColor = ApexPalette.NeonCyan.copy(alpha = 0.4f)
                )
            )
        }
        } else {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "No mask on this clip — pick a shape above to add one.",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(16.dp))

        // Remove mask
        if (mask != null) {
            Button(
                onClick = { onUpdate(null) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A1520)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = ApexPalette.NeonPink,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Remove mask", color = ApexPalette.NeonPink, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun MaskSliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(valueLabel, color = ApexPalette.NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = ApexPalette.NeonCyan,
                activeTrackColor = ApexPalette.NeonCyan
            )
        )
    }
}
