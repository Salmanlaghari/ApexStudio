package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexstudio.app.data.audio.sfx.SfxCategory
import com.apexstudio.app.data.audio.sfx.SfxEffect
import com.apexstudio.app.data.audio.sfx.SfxLibrary
import com.apexstudio.app.data.audio.sfx.SfxLibraryController
import com.apexstudio.app.ui.theme.EditorPalette as ApexPalette

@Composable
fun SfxLibraryPanel(
    onAddEffect: (SfxEffect, String, Long, Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val controller = remember { SfxLibraryController(context.applicationContext) }
    val uiState by controller.state.collectAsStateWithLifecycle()
    var selectedCategory by remember { mutableStateOf<SfxCategory?>(null) }
    var intensity by remember { mutableFloatStateOf(SfxLibrary.DEFAULT_INTENSITY) }

    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(ApexPalette.BgSurface.copy(alpha = 0.98f), ApexPalette.BgBase.copy(alpha = 0.98f))
                )
            )
            .border(
                1.dp,
                Brush.horizontalGradient(
                    listOf(ApexPalette.NeonPurple.copy(alpha = 0.5f), ApexPalette.NeonCyan.copy(alpha = 0.4f))
                ),
                RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape)
                        .background(ApexPalette.NeonPurple.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = ApexPalette.NeonPurple, modifier = Modifier.size(16.dp))
                }
                Column {
                    Text("Sound Effects", color = ApexPalette.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Procedural • built in • no downloads", color = ApexPalette.TextTertiary, fontSize = 9.sp)
                }
            }
            Box(
                modifier = Modifier.size(28.dp).clip(CircleShape).background(ApexPalette.BgElevated)
                    .clickable { controller.stopPreview(); onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = ApexPalette.TextSecondary, modifier = Modifier.size(15.dp))
            }
        }

        Spacer(Modifier.height(9.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                CategoryChip(label = "All", selected = selectedCategory == null) { selectedCategory = null }
            }
            items(SfxCategory.entries, key = { it.name }) { category ->
                CategoryChip(
                    label = category.displayName,
                    selected = selectedCategory == category
                ) { selectedCategory = category }
            }
        }

        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Intensity", color = ApexPalette.TextSecondary, fontSize = 10.sp)
            Slider(
                value = intensity,
                onValueChange = { intensity = it },
                valueRange = 0.2f..1f,
                modifier = Modifier.weight(1f).height(34.dp),
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = ApexPalette.NeonPurple,
                    activeTrackColor = ApexPalette.NeonPurple
                )
            )
            Text("${(intensity * 100).toInt()}%", color = ApexPalette.TextTertiary, fontSize = 10.sp)
        }

        Spacer(Modifier.height(5.dp))
        val effects = remember(selectedCategory) {
            SfxLibrary.effects.filter { selectedCategory == null || it.category == selectedCategory }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(265.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(effects, key = { it.id }) { effect ->
                SfxEffectRow(
                    effect = effect,
                    isPreviewing = uiState.previewingId == effect.id,
                    isRendering = effect.id in uiState.renderingIds || effect.id in uiState.addingIds,
                    onPreview = { controller.togglePreview(effect, intensity) },
                    onAdd = {
                        controller.renderAndAdd(effect, intensity, onAdded = onAddEffect)
                    }
                )
            }
        }
        uiState.error?.let { error ->
            Spacer(Modifier.height(5.dp))
            Text(error, color = ApexPalette.NeonPink, fontSize = 11.sp)
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) ApexPalette.NeonPurple.copy(alpha = 0.2f) else ApexPalette.BgElevated)
            .border(1.dp, if (selected) ApexPalette.NeonPurple else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (selected) ApexPalette.NeonPurple else ApexPalette.TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun SfxEffectRow(
    effect: SfxEffect,
    isPreviewing: Boolean,
    isRendering: Boolean,
    onPreview: () -> Unit,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(ApexPalette.BgElevated).clickable { onPreview() }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(ApexPalette.NeonPurple.copy(alpha = 0.3f), ApexPalette.NeonCyan.copy(alpha = 0.2f)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = ApexPalette.NeonPurple, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(effect.displayName, color = ApexPalette.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${effect.category.displayName} • ${durationLabel(effect.defaultDurationMs)}", color = ApexPalette.TextTertiary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape)
                .background(ApexPalette.NeonPurple.copy(alpha = 0.14f)).clickable { onPreview() },
            contentAlignment = Alignment.Center
        ) {
            if (isRendering) CircularProgressIndicator(color = ApexPalette.NeonPurple, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            else Icon(
                if (isPreviewing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPreviewing) "Stop preview ${effect.displayName}" else "Preview ${effect.displayName}",
                tint = ApexPalette.NeonPurple, modifier = Modifier.size(19.dp)
            )
        }
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape)
                .background(ApexPalette.NeonCyan.copy(alpha = 0.16f))
                .clickable(enabled = !isRendering) { onAdd() },
            contentAlignment = Alignment.Center
        ) {
            if (isRendering) CircularProgressIndicator(color = ApexPalette.NeonCyan, modifier = Modifier.size(17.dp), strokeWidth = 2.dp)
            else Icon(Icons.Default.Add, contentDescription = "Add ${effect.displayName} to timeline at playhead", tint = ApexPalette.NeonCyan, modifier = Modifier.size(20.dp))
        }
    }
}

private fun durationLabel(durationMs: Long): String =
    if (durationMs >= 1_000L) "${"%.1f".format(durationMs / 1_000f)} s" else "$durationMs ms"
