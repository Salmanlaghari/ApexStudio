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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.packs.PackLoader
import com.apexstudio.app.domain.model.ChromaKeySettings
import com.apexstudio.app.ui.theme.ApexPalette

/**
 * Phase 3: Background Remover panel — 13 configs from
 * `assets/packs/bg-remover/config.json`.
 *
 * - Engine: ML Kit selfie segmentation (on-device, free). The segmenter
 *   runs on the preview frame; the mask preview is honest about being
 *   a preview — export uses the same pipeline.
 * - Backgrounds: blur light/heavy, solid colors, gradients, custom image.
 * - Chroma-key mode reuses the existing [ChromaKeySettings] engine.
 * - Edge feather slider maps to chroma smoothness / mask feather.
 *
 * Every control applies a real setting — no dead buttons.
 */

private fun parseColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    Color.Black
}

private fun bgPreviewBrush(option: PackLoader.BgOption): Brush = when (option.type) {
    "blur" -> Brush.verticalGradient(listOf(Color(0xFF444444), Color(0xFF222222)))
    "gradient" -> Brush.verticalGradient(listOf(Color(0xFF666666), Color(0xFF333333)))
    "image" -> Brush.verticalGradient(
        listOf(ApexPalette.NeonCyan.copy(alpha = 0.3f), ApexPalette.NeonAmber.copy(alpha = 0.3f))
    )
    else -> Brush.verticalGradient(listOf(parseColor(option.color), parseColor(option.color)))
}

private fun emojiForBg(option: PackLoader.BgOption): String = when (option.type) {
    "blur" -> "🫧"
    "gradient" -> "🌅"
    "image" -> "🖼️"
    else -> when (option.id) {
        "bg_green" -> "🟩"
        "bg_white" -> "⬜"
        "bg_black" -> "⬛"
        else -> "🎨"
    }
}

@Composable
fun BgRemoverPanel(
    chromaKeySettings: ChromaKeySettings,
    onUpdateChromaKey: (ChromaKeySettings) -> Unit,
    onPickCustomBackground: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pack = remember { PackLoader.loadBgRemover(context) }
    var enabled by remember(chromaKeySettings.enabled) { mutableStateOf(chromaKeySettings.enabled) }
    var selectedBgId by remember {
        mutableStateOf(
            when {
                chromaKeySettings.customBackgroundUri != null -> "bg_custom"
                else -> "bg_blur_light"
            }
        )
    }
    var feather by remember { mutableFloatStateOf(chromaKeySettings.smoothness * 100f) }
    var chromaMode by remember { mutableStateOf(false) }

    fun apply() {
        val selected = pack.backgrounds.firstOrNull { it.id == selectedBgId }
        onUpdateChromaKey(
            chromaKeySettings.copy(
                enabled = enabled,
                smoothness = (feather / 100f).coerceIn(0f, 1f),
                backgroundType = when {
                    !enabled -> chromaKeySettings.backgroundType
                    chromaMode -> "chroma"
                    selected?.type == "blur" -> "blur_${selected.radius}"
                    selected?.type == "solid" -> "solid_${selected.color}"
                    selected?.type == "gradient" -> "gradient_custom"
                    selected?.type == "image" -> "custom"
                    else -> "blur_15"
                }
            )
        )
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
                    Text("🪄", fontSize = 14.sp)
                }
                Column {
                    Text(
                        "Background Remover",
                        color = ApexPalette.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "ML Kit selfie segmentation • on-device",
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Enable switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Remove background", color = ApexPalette.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        apply()
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = ApexPalette.NeonCyan)
                )
            }

            Spacer(Modifier.height(8.dp))

            // Mode: AI segmentation vs chroma key
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip(
                    label = "🤖 AI Segment",
                    selected = !chromaMode,
                    onClick = { chromaMode = false; apply() }
                )
                ModeChip(
                    label = "🟩 Chroma Key",
                    selected = chromaMode,
                    onClick = { chromaMode = true; apply() }
                )
            }

            Spacer(Modifier.height(12.dp))

            if (!chromaMode) {
                Text("BACKGROUND", color = ApexPalette.NeonCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                val bgs = pack.backgrounds.ifEmpty {
                    listOf(
                        PackLoader.BgOption("bg_blur_light", "Blur Light", "blur", radius = 15),
                        PackLoader.BgOption("bg_blur_heavy", "Blur Heavy", "blur", radius = 40),
                        PackLoader.BgOption("bg_white", "White", "solid", "#FFFFFF"),
                        PackLoader.BgOption("bg_black", "Black", "solid", "#000000")
                    )
                }
                bgs.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { bg ->
                            val selected = selectedBgId == bg.id
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(bgPreviewBrush(bg))
                                        .border(
                                            2.dp,
                                            if (selected) ApexPalette.NeonCyan else Color.Transparent,
                                            RoundedCornerShape(14.dp)
                                        )
                                        .clickable {
                                            selectedBgId = bg.id
                                            if (bg.id == "bg_custom") onPickCustomBackground()
                                            apply()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(emojiForBg(bg), fontSize = 22.sp)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    bg.name,
                                    color = if (selected) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
                                    fontSize = 9.sp,
                                    maxLines = 1
                                )
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // Edge feather
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Edge feather", color = ApexPalette.TextSecondary, fontSize = 11.sp)
                    Text("${feather.toInt()}%", color = ApexPalette.NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = feather,
                    onValueChange = { feather = it },
                    onValueChangeFinished = { apply() },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = ApexPalette.NeonCyan,
                        activeTrackColor = ApexPalette.NeonCyan
                    )
                )

                // Edge refinement presets from pack
                if (pack.edge_refinement.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("EDGE REFINEMENT", color = ApexPalette.NeonCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pack.edge_refinement.forEach { er ->
                            ModeChip(
                                label = er.name,
                                selected = false,
                                onClick = {
                                    feather = er.feather.toFloat() * 10f
                                    apply()
                                }
                            )
                        }
                    }
                }
            } else {
                Text(
                    "Chroma key uses the existing keyer — pick a key color in the Chroma panel.",
                    color = ApexPalette.TextTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0F2A1A))
                    .border(1.dp, Color(0xFF2D5A2D), RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Text(
                    "✅ 100% on-device (ML Kit) — no internet, no uploads.",
                    color = Color(0xFF88CC88),
                    fontSize = 10.sp
                )
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
