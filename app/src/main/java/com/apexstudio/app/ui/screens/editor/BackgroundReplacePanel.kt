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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.packs.download.PackManager
import com.apexstudio.app.domain.model.BackgroundReplaceMode
import com.apexstudio.app.domain.model.BackgroundReplaceSettings
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.presentation.viewmodel.BackgroundPhase
import com.apexstudio.app.presentation.viewmodel.BackgroundUiState
import com.apexstudio.app.ui.components.HsvColorPicker
import com.apexstudio.app.ui.theme.EditorPalette as ApexPalette

/**
 * Background replace panel (PR-G): per-clip MediaPipe person
 * segmentation — Original / Blur / Colour / Image.
 *
 * Nothing here is a dead button: every state explains itself.
 * - Model not downloaded → download prompt (244 KB, one-time).
 * - Processing → progress bar + Cancel.
 * - Failed → the real error message + Apply to retry.
 * - Applied → "Applied: …" badge + Restore original.
 *
 * Preview/export parity: Apply pre-renders ONE processed video
 * file; the preview plays it and the export exports it, so what
 * you see is exactly what you get.
 */
private enum class BgUiMode { ORIGINAL, BLUR, COLOR, IMAGE }

private val COLOR_PRESETS = listOf(
    0xFF000000L to "Black",
    0xFFFFFFFFL to "White",
    0xFF00E676L to "Green",
    0xFF2196F3L to "Blue",
    0xFFE53935L to "Red",
    0xFFFFB300L to "Amber"
)

@Composable
fun BackgroundReplacePanel(
    clip: MediaClip?,
    ui: BackgroundUiState,
    packState: PackManager.PackState?,
    onApply: (BackgroundReplaceSettings) -> Unit,
    onRestoreOriginal: () -> Unit,
    onCancel: () -> Unit,
    onDownloadModel: () -> Unit,
    onPickImage: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val applied = clip?.backgroundReplace
    var mode by remember(clip?.id, applied) {
        mutableStateOf(
            when (applied?.mode) {
                BackgroundReplaceMode.COLOR -> BgUiMode.COLOR
                BackgroundReplaceMode.IMAGE -> BgUiMode.IMAGE
                else -> BgUiMode.BLUR
            }
        )
    }
    var blur by remember(clip?.id, applied) {
        mutableFloatStateOf(((applied?.blurStrength ?: 0.5f) * 100f))
    }
    var colorArgb by remember(clip?.id, applied) {
        mutableStateOf(applied?.colorArgb ?: 0xFF000000L)
    }
    val imageUri = ui.imageUri ?: applied?.imageUri
    val isProcessing = ui.phase == BackgroundPhase.PROCESSING
    val modelDownloadedHint = packState is PackManager.PackState.Downloaded ||
        packState is PackManager.PackState.UpdateAvailable
    val showApply = !isProcessing &&
        (ui.phase != BackgroundPhase.NEED_DOWNLOAD || modelDownloadedHint)
    val canApply = when (mode) {
        BgUiMode.ORIGINAL -> applied != null
        BgUiMode.IMAGE -> !imageUri.isNullOrBlank()
        else -> true
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        ApexPalette.BgSurface.copy(alpha = 0.98f),
                        ApexPalette.BgBase.copy(alpha = 0.98f)
                    )
                )
            )
            .border(
                1.dp,
                Brush.horizontalGradient(
                    listOf(ApexPalette.NeonCyan.copy(alpha = 0.4f), ApexPalette.NeonAmber.copy(alpha = 0.4f))
                ),
                RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(ApexPalette.NeonCyan.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🧍", fontSize = 14.sp)
                }
                Column {
                    Text(
                        "Background",
                        color = ApexPalette.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "MediaPipe person segmentation • on-device",
                        color = ApexPalette.TextTertiary,
                        fontSize = 9.sp
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(ApexPalette.BgElevated)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = ApexPalette.TextSecondary, modifier = Modifier.size(15.dp))
            }
        }

        Spacer(Modifier.height(10.dp))

        if (clip == null) {
            Text("No clip selected.", color = ApexPalette.TextSecondary, fontSize = 12.sp)
            return@Column
        }
        if (clip.type != ClipType.VIDEO) {
            Text(
                "Background replace works on video clips — select a video clip first.",
                color = ApexPalette.TextSecondary, fontSize = 12.sp
            )
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Applied badge
            if (applied != null) {
                val label = when (applied.mode) {
                    BackgroundReplaceMode.BLUR -> "Blur ${(applied.blurStrength * 100).toInt()}%"
                    BackgroundReplaceMode.COLOR -> "Colour"
                    BackgroundReplaceMode.IMAGE -> "Image"
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0F2A1A))
                        .border(1.dp, Color(0xFF2D5A2D), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        "✅ Applied: $label — preview and export show the same processed video.",
                        color = Color(0xFF88CC88),
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            // Mode chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BgUiMode.entries.forEach { m ->
                    val label = when (m) {
                        BgUiMode.ORIGINAL -> "Original"
                        BgUiMode.BLUR -> "🫧 Blur"
                        BgUiMode.COLOR -> "🎨 Colour"
                        BgUiMode.IMAGE -> "🖼️ Image"
                    }
                    ModeChip(
                        label = label,
                        selected = mode == m,
                        onClick = { mode = m }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            when (mode) {
                BgUiMode.ORIGINAL -> {
                    Text(
                        if (applied != null) {
                            "Restore the clip's untouched original background."
                        } else {
                            "This clip already shows its original background."
                        },
                        color = ApexPalette.TextSecondary, fontSize = 12.sp
                    )
                }
                BgUiMode.BLUR -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Blur strength", color = ApexPalette.TextSecondary, fontSize = 11.sp)
                        Text("${blur.toInt()}%", color = ApexPalette.NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = blur,
                        onValueChange = { blur = it },
                        valueRange = 0f..100f,
                        enabled = !isProcessing,
                        colors = SliderDefaults.colors(
                            thumbColor = ApexPalette.NeonCyan,
                            activeTrackColor = ApexPalette.NeonCyan
                        )
                    )
                    Text(
                        "The person stays sharp; everything behind them blurs.",
                        color = ApexPalette.TextTertiary, fontSize = 10.sp
                    )
                }
                BgUiMode.COLOR -> {
                    // Preset swatches
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        COLOR_PRESETS.forEach { (argb, _) ->
                            val c = Color(argb.toInt())
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(
                                        2.dp,
                                        if (colorArgb == argb) ApexPalette.NeonCyan else ApexPalette.TextPrimary.copy(alpha = 0.25f),
                                        CircleShape
                                    )
                                    .clickable { colorArgb = argb },
                                contentAlignment = Alignment.Center
                            ) {}
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Custom colour", color = ApexPalette.TextSecondary, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    HsvColorPicker(
                        initialColor = Color(colorArgb.toInt()),
                        // toArgb() is the idiomatic ARGB int; mask to
                        // unsigned Long and force opaque alpha.
                        onColorChange = { colorArgb = it.toArgb().toLong() and 0xFFFFFFFFL or 0xFF000000L }
                    )
                }
                BgUiMode.IMAGE -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ApexPalette.BgElevated)
                            .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .clickable(enabled = !isProcessing) { onPickImage() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (imageUri == null) "Pick a background image" else "Change background image",
                            color = ApexPalette.NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    if (imageUri != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            imageUri.substringAfterLast('/').take(48),
                            color = ApexPalette.TextSecondary, fontSize = 10.sp
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "The image is cover-cropped to the video frame.",
                        color = ApexPalette.TextTertiary, fontSize = 10.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Status / progress
            when (ui.phase) {
                BackgroundPhase.NEED_DOWNLOAD -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF2A1A0F))
                            .border(1.dp, Color(0xFF5A3A1D), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                "The person-segmentation model (~244 KB, one-time download) is needed for background replace.",
                                color = Color(0xFFFFC27D), fontSize = 11.sp
                            )
                            if (packState is PackManager.PackState.Downloading) {
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { packState.fraction.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = ApexPalette.NeonAmber
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Downloading ${(packState.fraction * 100).toInt()}%…",
                                    color = ApexPalette.TextSecondary, fontSize = 10.sp
                                )
                            }
                            if (packState is PackManager.PackState.Failed) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Download failed: ${packState.reason}",
                                    color = ApexPalette.NeonPink, fontSize = 10.sp
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onDownloadModel,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ApexPalette.NeonAmber),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            if (packState is PackManager.PackState.Downloading) "Downloading…" else "Download model (244 KB)",
                            color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    if (modelDownloadedHint) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Model downloaded — tap Apply below.",
                            color = Color(0xFF88CC88), fontSize = 11.sp
                        )
                    }
                }
                BackgroundPhase.PROCESSING -> {
                    LinearProgressIndicator(
                        progress = { ui.progress01.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = ApexPalette.NeonCyan
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            ui.message.ifBlank { "Processing ${(ui.progress01 * 100).toInt()}%…" },
                            color = ApexPalette.TextSecondary, fontSize = 11.sp
                        )
                        Text(
                            "Cancel",
                            color = ApexPalette.NeonPink, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onCancel() }
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Processing runs on-device and may take a while for long clips — you can cancel anytime.",
                        color = ApexPalette.TextTertiary, fontSize = 10.sp
                    )
                }
                BackgroundPhase.DONE -> {
                    if (ui.message.isNotBlank()) {
                        Text(ui.message, color = Color(0xFF88CC88), fontSize = 11.sp)
                    }
                }
                BackgroundPhase.FAILED -> {
                    if (ui.message.isNotBlank()) {
                        Text(ui.message, color = ApexPalette.NeonPink, fontSize = 11.sp)
                    }
                }
                BackgroundPhase.IDLE -> {
                    if (ui.message.isNotBlank()) {
                        Text(ui.message, color = ApexPalette.TextSecondary, fontSize = 11.sp)
                    }
                }
            }

            // Apply / Restore
            if (showApply) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (mode == BgUiMode.ORIGINAL) {
                            onRestoreOriginal()
                        } else {
                            onApply(
                                BackgroundReplaceSettings(
                                    mode = when (mode) {
                                        BgUiMode.BLUR -> BackgroundReplaceMode.BLUR
                                        BgUiMode.COLOR -> BackgroundReplaceMode.COLOR
                                        BgUiMode.IMAGE -> BackgroundReplaceMode.IMAGE
                                        BgUiMode.ORIGINAL -> BackgroundReplaceMode.BLUR
                                    },
                                    blurStrength = (blur / 100f).coerceIn(0f, 1f),
                                    colorArgb = colorArgb,
                                    imageUri = imageUri,
                                    sourceUri = clip.backgroundReplace?.sourceUri ?: clip.uri
                                )
                            )
                        }
                    },
                    enabled = canApply,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ApexPalette.NeonCyan),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        if (mode == BgUiMode.ORIGINAL) "Restore original" else "Apply background",
                        color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold
                    )
                }
                if (mode == BgUiMode.IMAGE && imageUri.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Pick a background image to enable Apply.",
                        color = ApexPalette.TextTertiary, fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) ApexPalette.NeonCyan.copy(alpha = 0.2f) else ApexPalette.BgElevated)
            .border(1.dp, if (selected) ApexPalette.NeonCyan else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            color = if (selected) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
