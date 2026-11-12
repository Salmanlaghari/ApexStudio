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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.apexstudio.app.data.background.InstalledWallpaper
import com.apexstudio.app.data.background.WallpaperCatalogRepository
import com.apexstudio.app.data.packs.download.PackManager
import com.apexstudio.app.data.packs.download.PackManifestRepo
import com.apexstudio.app.domain.model.ChromaKeySettings
import com.apexstudio.app.presentation.viewmodel.BackgroundPhase
import com.apexstudio.app.presentation.viewmodel.BackgroundUiState
import com.apexstudio.app.ui.theme.EditorPalette as ApexPalette

@Composable
fun WallpaperChromaPanel(
    settings: ChromaKeySettings,
    onUpdate: (ChromaKeySettings) -> Unit,
    wallpapers: List<InstalledWallpaper>,
    packState: PackManager.PackState?,
    packLoading: Boolean,
    packError: String?,
    packSizeBytes: Long,
    catalogItemCount: Int,
    ui: BackgroundUiState,
    isApplied: Boolean,
    targetAvailable: Boolean,
    onDownloadPack: () -> Unit,
    onRefreshPack: () -> Unit,
    onRepairPack: () -> Unit,
    onApply: (ChromaKeySettings) -> Unit,
    onRestoreOriginal: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val selectedId = WallpaperCatalogRepository.resolveLegacyId(settings.backgroundType)
    val selectedWallpaper = wallpapers.firstOrNull { it.asset.id == selectedId }
    val busy = ui.phase == BackgroundPhase.PROCESSING

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(Color(0xFF141420))
            .border(1.dp, Color(0xFF2A2A3E), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = ApexPalette.NeonCyan, modifier = Modifier.size(20.dp))
                Text("Green/Blue Screen", color = ApexPalette.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.75f))
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            "Choose a screen color and wallpaper, then Apply. The processed clip is used for both preview and export.",
            color = ApexPalette.TextPrimary.copy(alpha = 0.72f), fontSize = 12.sp
        )
        Spacer(Modifier.height(12.dp))

        if (!targetAvailable) {
            Text("Select a video clip on the timeline to use chroma key.", color = Color(0xFFFFC36B), fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1C1C2E)).padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Enable chroma key", color = ApexPalette.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Removes a selected screen color", color = ApexPalette.TextPrimary.copy(alpha = 0.62f), fontSize = 10.sp)
            }
            Switch(
                checked = settings.enabled,
                onCheckedChange = { onUpdate(settings.copy(enabled = it)) },
                enabled = targetAvailable && !busy,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ApexPalette.NeonCyan,
                    checkedTrackColor = ApexPalette.NeonCyan.copy(alpha = 0.4f)
                )
            )
        }

        if (settings.enabled) {
            Spacer(Modifier.height(12.dp))
            Text("SCREEN COLOR", color = ApexPalette.TextPrimary.copy(alpha = 0.62f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple("Green", 0xFF00E676L, Color(0xFF00E676)),
                    Triple("Blue", 0xFF0000FFL, Color(0xFF2979FF)),
                    Triple("Magenta", 0xFFFF00FFL, Color(0xFFE040FB))
                ).forEach { (label, argb, swatch) ->
                    val selected = settings.keyColorArgb == argb
                    Row(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                            .background(if (selected) swatch.copy(alpha = 0.22f) else Color(0xFF1E1E2C))
                            .border(if (selected) 1.5.dp else 1.dp, if (selected) swatch else Color(0xFF2F2F44), RoundedCornerShape(9.dp))
                            .clickable(enabled = targetAvailable && !busy) { onUpdate(settings.copy(keyColorArgb = argb)) }
                            .padding(horizontal = 5.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(11.dp).clip(CircleShape).background(swatch))
                        Spacer(Modifier.width(5.dp))
                        Text(label, color = ApexPalette.TextPrimary, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            SliderRow("Similarity", settings.similarity, { onUpdate(settings.copy(similarity = it)) }, enabled = targetAvailable && !busy, range = 0.01f..1f)
            SliderRow("Edge feather", settings.smoothness, { onUpdate(settings.copy(smoothness = it)) }, enabled = targetAvailable && !busy, range = 0.01f..0.5f)
            SliderRow("Spill suppression", settings.spillSuppression, { onUpdate(settings.copy(spillSuppression = it)) }, enabled = targetAvailable && !busy)

            Spacer(Modifier.height(6.dp))
            Text("WALLPAPERS", color = ApexPalette.TextPrimary.copy(alpha = 0.62f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            WallpaperPackState(
                packState = packState,
                packLoading = packLoading,
                packError = packError,
                packSizeBytes = packSizeBytes,
                catalogItemCount = catalogItemCount,
                installedCount = wallpapers.size,
                onDownload = onDownloadPack,
                onRefresh = onRefreshPack,
                onRepair = onRepairPack
            )
            if (wallpapers.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(wallpapers, key = { it.asset.id }) { wallpaper ->
                        val selected = wallpaper.asset.id == selectedId
                        Column(
                            modifier = Modifier.width(132.dp).clip(RoundedCornerShape(10.dp))
                                .border(if (selected) 2.dp else 1.dp, if (selected) ApexPalette.NeonCyan else Color(0xFF303044), RoundedCornerShape(10.dp))
                                .clickable(enabled = targetAvailable && !busy) {
                                    onUpdate(settings.copy(backgroundType = wallpaper.asset.id, customBackgroundUri = null))
                                }
                        ) {
                            AsyncImage(
                                model = wallpaper.uri,
                                contentDescription = wallpaper.asset.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(78.dp)
                            )
                            Column(Modifier.fillMaxWidth().background(Color(0xFF1B1B2B)).padding(horizontal = 8.dp, vertical = 6.dp)) {
                                Text(wallpaper.asset.name, color = ApexPalette.TextPrimary, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                                Text(wallpaper.asset.category, color = ApexPalette.TextPrimary.copy(alpha = 0.6f), fontSize = 9.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            if (busy) {
                LinearProgressIndicator(progress = ui.progress01.coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(5.dp))
                Text(ui.message, color = ApexPalette.TextPrimary.copy(alpha = 0.76f), fontSize = 11.sp)
            } else if (ui.phase == BackgroundPhase.FAILED) {
                Text(ui.message, color = Color(0xFFFF9B8E), fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
            } else if (ui.phase == BackgroundPhase.DONE) {
                Text(ui.message, color = Color(0xFF7FE0A2), fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
            }

            Button(
                onClick = { onApply(settings.copy(backgroundType = selectedWallpaper?.asset?.id ?: settings.backgroundType)) },
                enabled = targetAvailable && !busy && settings.enabled && selectedWallpaper != null,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ApexPalette.NeonCyan, contentColor = Color(0xFF071018))
            ) {
                Text("Apply key & wallpaper", fontWeight = FontWeight.Bold)
            }
            if (isApplied) {
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = onRestoreOriginal,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF29293B), contentColor = ApexPalette.TextPrimary)
                ) { Text("Restore original clip") }
            }
        } else if (isApplied) {
            Spacer(Modifier.height(10.dp))
            Text("A chroma-key version is applied to this clip.", color = Color(0xFF7FE0A2), fontSize = 11.sp)
            Button(onClick = onRestoreOriginal, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                Text("Restore original clip")
            }
        }
    }
}

@Composable
private fun WallpaperPackState(
    packState: PackManager.PackState?,
    packLoading: Boolean,
    packError: String?,
    packSizeBytes: Long,
    catalogItemCount: Int,
    installedCount: Int,
    onDownload: () -> Unit,
    onRefresh: () -> Unit,
    onRepair: () -> Unit
) {
    val downloadReady = packState is PackManager.PackState.NotDownloaded ||
        packState is PackManager.PackState.UpdateAvailable || packState is PackManager.PackState.Failed
    when {
        packState is PackManager.PackState.Downloading -> {
            if (packState.fraction >= 0f) LinearProgressIndicator(progress = packState.fraction, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("Downloading wallpapers ${if (packState.fraction >= 0f) "${(packState.fraction * 100).toInt()}%" else "…"}", color = ApexPalette.TextPrimary.copy(alpha = 0.72f), fontSize = 11.sp)
        }
        packState is PackManager.PackState.Failed -> {
            Text("Wallpaper download failed: ${packState.reason}", color = Color(0xFFFF9B8E), fontSize = 11.sp)
            Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) { Text("Retry download") }
        }
        packState is PackManager.PackState.Downloaded && installedCount < catalogItemCount -> {
            Text("Some files failed their integrity check; they are not selectable.", color = Color(0xFFFFC36B), fontSize = 11.sp)
            Button(onClick = onRepair, modifier = Modifier.fillMaxWidth()) { Text("Repair wallpaper pack") }
        }
        downloadReady -> {
            Text("$catalogItemCount original backgrounds · ${PackManifestRepo.formatSize(packSizeBytes)} on demand", color = ApexPalette.TextPrimary.copy(alpha = 0.7f), fontSize = 11.sp)
            Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                Text(if (packState is PackManager.PackState.UpdateAvailable) "Update wallpapers" else "Download wallpapers")
            }
        }
        packState == null -> {
            if (packLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Loading wallpaper catalog…", color = ApexPalette.TextPrimary.copy(alpha = 0.7f), fontSize = 11.sp)
                }
            } else {
                Text(packError ?: "Wallpaper catalog is unavailable.", color = Color(0xFFFFC36B), fontSize = 11.sp)
                Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Retry catalog") }
            }
        }
        else -> Text("$installedCount verified wallpapers ready", color = ApexPalette.TextPrimary.copy(alpha = 0.7f), fontSize = 11.sp)
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean,
    range: ClosedFloatingPointRange<Float> = 0f..1f
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = ApexPalette.TextPrimary.copy(alpha = 0.82f), fontSize = 11.sp)
            Text("${(value * 100).toInt()}%", color = ApexPalette.NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            enabled = enabled,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = ApexPalette.NeonCyan, activeTrackColor = ApexPalette.NeonCyan, inactiveTrackColor = Color(0xFF2A2A3E))
        )
    }
}
