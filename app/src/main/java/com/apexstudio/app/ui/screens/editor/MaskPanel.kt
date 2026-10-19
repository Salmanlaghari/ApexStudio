package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
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
import com.apexstudio.app.domain.model.MaskKeyframe
import com.apexstudio.app.domain.model.MaskPreset
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
    // Clip-relative playhead, the stamp point for the KEYFRAME control:
    // add bakes the current geometry at exactly this position so the
    // mask animates in preview and on export (resolveMaskAt).
    keyframeTimeMs: Long = 0L,
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
            .heightIn(max = 380.dp)
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
        // Compact horizontal shape strip: labels remain readable and the panel
        // no longer grows into several rows on a phone. The strip scrolls when
        // the available width is narrow; every shape remains selectable.
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MaskShape.entries.forEach { shape ->
                val selected = mask?.shape == shape
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) ApexPalette.NeonCyan.copy(alpha = 0.18f) else Color(0xFF1C1C2E))
                        .border(1.5.dp, if (selected) ApexPalette.NeonCyan else Color.Transparent, RoundedCornerShape(12.dp))
                        .clickable { onUpdate((mask ?: VideoMask.default()).copy(shape = shape)) }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = shape.label,
                        color = if (selected) ApexPalette.NeonCyan else Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
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
        Spacer(Modifier.height(14.dp))

        // Rotation: the shape spins about its own centre, independent of
        // feather/opacity so a nudge on one slider can never move another.
        MaskSliderRow(
            label = "Rotation",
            valueLabel = "${activeMask.rotationDeg.toInt()}°",
            value = ((activeMask.rotationDeg + 180f) / 360f).coerceIn(0f, 1f),
            onValueChange = { onUpdate(activeMask.copy(rotationDeg = it.coerceIn(0f, 1f) * 360f - 180f)) }
        )
        Spacer(Modifier.height(14.dp))

        // Presets ship a COMPLETE look - shape, extent, feather, rotation
        // and inversion together - so one tap reproduces it rather than
        // editing five settings by hand. Choosing one overwrites every
        // geometry field (documented on MaskPreset.apply), which is what
        // users expect from a recipe.
        Text(
            "PRESETS",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MaskPreset.ALL.forEach { preset ->
                val active = activeMask.presetId == preset.id
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) ApexPalette.NeonPink.copy(alpha = 0.18f) else Color(0xFF1C1C2E))
                        .border(1.dp, if (active) ApexPalette.NeonPink else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { onUpdate(preset.apply().copy(presetId = preset.id)) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = preset.label,
                        color = if (active) ApexPalette.NeonPink else Color.White.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        // KEYFRAME ANIMATION: the eighth control in the task's list.
        // "Add mask key" freezes the CURRENT geometry at the playhead, so
        // the mask can be moved / resized / rotated / faded over time;
        // resolveMaskAt() interpolates between stamps on preview and on
        // export. Stamps already existing at this exact time are replaced
        // (move a handle and re-press = adjust that key, not a second one).
        val keyframes = activeMask.maskKeyframes
        val stamped = keyframes.any { it.timeMs == keyframeTimeMs }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    val stamp = MaskKeyframe(
                        id = "mask-$keyframeTimeMs",
                        timeMs = keyframeTimeMs,
                        centerX = activeMask.centerX,
                        centerY = activeMask.centerY,
                        sizeX = activeMask.sizeX,
                        sizeY = activeMask.sizeY,
                        rotationDeg = activeMask.rotationDeg,
                        feather = activeMask.feather,
                        opacity = activeMask.opacity,
                        invert = activeMask.invert
                    )
                    onUpdate(
                        activeMask.copy(
                            maskKeyframes = (keyframes.filterNot { it.timeMs == keyframeTimeMs } + stamp)
                                .sortedBy { it.timeMs }
                        )
                    )
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF103640)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = if (stamped) "Update mask key" else "Add mask key",
                    color = ApexPalette.NeonCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (keyframes.isNotEmpty()) {
                Button(
                    onClick = { onUpdate(activeMask.copy(maskKeyframes = emptyList())) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A1520)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Clear keys (${keyframes.size})",
                        color = ApexPalette.NeonPink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
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
