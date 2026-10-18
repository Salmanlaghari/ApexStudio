package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.effect.VideoCropPreset
import com.apexstudio.app.ui.theme.ApexPalette

/**
 * Reusable video crop chooser. Each option maps to a deterministic,
 * center-anchored normalized crop window in [VideoCropPreset.centeredWindow].
 * The host owns preview/export state and persists the returned preset/window.
 */
@Composable
fun CropPresetPanel(
    activePresetId: String = "free",
    onPresetSelected: (VideoCropPreset) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .background(ApexPalette.BgSurface)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Crop, contentDescription = null, tint = ApexPalette.NeonCyan)
            Text(
                "Crop & frame",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            )
            CropAction("Reset", Icons.Default.RestartAlt) { onPresetSelected(VideoCropPreset.FREE) }
            CropAction("Close", Icons.Default.Close, onClose)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VideoCropPreset.all.forEach { preset ->
                val selected = preset.id == activePresetId
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) ApexPalette.NeonCyan.copy(alpha = 0.16f) else ApexPalette.BgElevated)
                        .border(
                            1.dp,
                            if (selected) ApexPalette.NeonCyan else ApexPalette.BorderGlass,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onPresetSelected(preset) }
                        .padding(horizontal = 11.dp, vertical = 8.dp)
                ) {
                    Text(
                        preset.label,
                        color = if (selected) ApexPalette.NeonCyan else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (preset == VideoCropPreset.FREE) "Custom" else "Frame",
                        color = ApexPalette.TextTertiary,
                        fontSize = 9.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Preset crops are centered and saved per clip. Drag-to-adjust handles remain a host-screen integration.",
            color = ApexPalette.TextTertiary,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun CropAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 5.dp)
    ) {
        Icon(icon, contentDescription = label, tint = ApexPalette.TextSecondary)
        Text(label, color = ApexPalette.TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(start = 2.dp))
    }
}
