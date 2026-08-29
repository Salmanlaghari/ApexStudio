package com.apexstudio.app.ui.screens.export

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apexstudio.app.domain.model.ExportQuality
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.EditorViewModelFactory
import com.apexstudio.app.ui.components.AppTopBar
import com.apexstudio.app.ui.components.BottomNavBar
import com.apexstudio.app.ui.components.BottomNavItem
import com.apexstudio.app.ui.components.GlassCard
import com.apexstudio.app.ui.components.NeonPrimaryButton
import com.apexstudio.app.ui.theme.ApexPalette
import com.apexstudio.app.util.Fps
import com.apexstudio.app.util.TimeFormat
import com.apexstudio.app.util.WaveformGenerator
import kotlinx.coroutines.delay

@Composable
fun ExportScreen(
    projectId: String,
    onBack: () -> Unit,
    vm: EditorViewModel = viewModel(factory = EditorViewModelFactory())
) {
    val export by vm.export.collectAsStateWithLifecycle()
    val s = export.settings

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .verticalScroll(rememberScrollState())
    ) {
        AppTopBar(
            title = "VideoFX & Export",
            subtitle = "Finalize & share",
            onBack = onBack
        )

        Spacer(Modifier.height(4.dp))

        // Preview
        GlassCard(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            cornerRadius = 22.dp
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.verticalGradient(
                            listOf(Color(0xFFFF9A8B), Color(0xFFFF6A88),
                                Color(0xFF6A5BE2))
                        ))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(
                                listOf(Color(0x88000000), Color.Transparent)
                            ))
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PlayArrow, null,
                            tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("01:24 / 04:15", color = Color.White, fontSize = 11.sp)
                        Spacer(Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.18f))
                        ) {
                            Icon(Icons.Default.Fullscreen, null,
                                tint = Color.White,
                                modifier = Modifier.align(Alignment.Center).size(16.dp))
                        }
                    }
                    // mini waveform
                    val samples = 80
                    val data = remember { WaveformGenerator.generate(42L, samples) }
                    Canvas(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(24.dp)
                            .padding(start = 32.dp, end = 40.dp, bottom = 32.dp)
                    ) {
                        val w = size.width
                        val h = size.height
                        val mid = h / 2
                        val step = w / samples
                        for (i in 0 until samples) {
                            val v = data[i]
                            drawLine(
                                color = Color.White.copy(alpha = 0.5f),
                                start = Offset(i * step, mid - v * h / 2),
                                end = Offset(i * step, mid + v * h / 2),
                                strokeWidth = 2f
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // progress
                Slider(
                    value = 0.32f,
                    onValueChange = {},
                    colors = SliderDefaults.colors(
                        thumbColor = ApexPalette.NeonCyan,
                        activeTrackColor = ApexPalette.NeonCyan,
                        inactiveTrackColor = ApexPalette.BgElevated
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        val fxList = vm.fx.collectAsStateWithLifecycle().value
        val transitionsList = vm.transitions.collectAsStateWithLifecycle().value

        SectionTitle("Aesthetic FX & Transitions")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(fxList) { fx ->
                FxCard(fx.label, fx.id == "chrom", fx.icon)
            }
        }

        Spacer(Modifier.height(18.dp))

        SectionTitle("Transition Selector")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(transitionsList) { t ->
                TransitionCard(t.label, t.icon)
            }
        }

        Spacer(Modifier.height(18.dp))

        // Export Settings
        GlassCard(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            cornerRadius = 20.dp
        ) {
            Column {
                Text("Export Settings",
                    color = ApexPalette.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))

                SettingsRow("Resolution") {
                    listOf("8K Ultra HD", "4K", "1080p").forEach { r ->
                        PillButton(
                            text = r,
                            selected = s.resolution == r,
                            sub = if (r == "8K Ultra HD") "7680x4320" else null,
                            onClick = { vm.updateExport { it.copy(resolution = r) } }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text("Frame Rate", color = ApexPalette.TextPrimary,
                    style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("24", color = ApexPalette.TextTertiary, fontSize = 10.sp)
                    Slider(
                        value = s.frameRate.toFloat(),
                        onValueChange = { vm.updateExport { e -> e.copy(frameRate = it.toInt()) } },
                        valueRange = Fps.MIN.toFloat()..Fps.MAX.toFloat(),
                        steps = ((Fps.MAX - Fps.MIN) / Fps.STEP) - 1,
                        colors = SliderDefaults.colors(
                            thumbColor = ApexPalette.NeonCyan,
                            activeTrackColor = ApexPalette.NeonCyan,
                            inactiveTrackColor = ApexPalette.BgElevated
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Text("60", color = ApexPalette.TextTertiary, fontSize = 10.sp)
                }
                Text("${s.frameRate} fps",
                    color = ApexPalette.NeonCyan,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.End))

                Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Bitrate & Quality",
                        color = ApexPalette.TextPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f))
                    QualityPicker(
                        current = s.quality,
                        onChange = { q -> vm.updateExport { it.copy(quality = q) } }
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Gauge
                    GaugeArc(progress = 0.78f)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Estimated Size",
                            color = ApexPalette.TextTertiary,
                            style = MaterialTheme.typography.labelSmall)
                        Text("1.8 GB / 120 Mbps",
                            color = ApexPalette.TextPrimary,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Export button
        Box(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            NeonPrimaryButton(
                text = if (export.isExporting)
                    "Exporting… ${(export.progress * 100).toInt()}%"
                else "Export Project",
                icon = Icons.Default.IosShare,
                onClick = { vm.startExport() },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (export.isExporting) {
            LaunchedEffect(Unit) {
                while (export.progress < 1f) {
                    delay(80)
                    vm.setExportProgress((export.progress + 0.01f).coerceAtMost(1f))
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        BottomNavBar(
            current = "Export",
            items = listOf(
                BottomNavItem("Home", Icons.Default.Home, false),
                BottomNavItem("Export", Icons.Default.IosShare, true)
            ),
            onSelect = { /* TODO */ }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = ApexPalette.TextPrimary,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
    )
}

@Composable
private fun FxCard(label: String, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val border = if (selected) ApexPalette.NeonCyan else Color.Transparent
    Box(
        modifier = Modifier
            .width(110.dp)
            .height(86.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(
                listOf(Color(0xFF1A0F2E), Color(0xFF0F1B2D))
            ))
            .clickable { }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.radialGradient(
                    listOf(ApexPalette.NeonPurple.copy(alpha = 0.45f), Color.Transparent)
                ))
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(34.dp)
                .clip(CircleShape)
                .background(ApexPalette.NeonCyan.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = if (selected) ApexPalette.NeonCyan else Color.White,
                modifier = Modifier.size(20.dp))
        }
        Text(
            label,
            color = if (selected) ApexPalette.NeonCyan else Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.horizontalGradient(
                    listOf(ApexPalette.NeonCyan, ApexPalette.NeonPurple)
                ))
        )
    }
}

@Composable
private fun TransitionCard(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        modifier = Modifier
            .width(86.dp)
            .height(80.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(
                listOf(Color(0xFF1A2440), Color(0xFF0F1B33))
            ))
            .clickable { },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = ApexPalette.NeonCyan, modifier = Modifier.size(28.dp))
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(ApexPalette.BgDeep.copy(alpha = 0.7f))
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = ApexPalette.TextPrimary, fontSize = 9.sp)
        }
    }
}

@Composable
private fun SettingsRow(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label, color = ApexPalette.TextPrimary,
            style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun PillButton(
    text: String,
    selected: Boolean,
    sub: String? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) ApexPalette.NeonCyan.copy(alpha = 0.18f) else ApexPalette.BgElevated
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text,
                color = if (selected) ApexPalette.NeonCyan else ApexPalette.TextPrimary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold)
            if (sub != null) {
                Text(sub, color = ApexPalette.TextTertiary, fontSize = 8.sp)
            }
        }
    }
}

@Composable
private fun QualityPicker(current: ExportQuality, onChange: (ExportQuality) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ExportQuality.values().forEach { q ->
            val sel = current == q
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (sel) ApexPalette.NeonCyan else ApexPalette.BgElevated
                    )
                    .clickable { onChange(q) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(q.label,
                    color = if (sel) ApexPalette.BgDeep else ApexPalette.TextPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun GaugeArc(progress: Float) {
    Box(
        modifier = Modifier.size(54.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 6f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = ApexPalette.BgElevated,
                startAngle = 135f, sweepAngle = 270f, useCenter = false,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(ApexPalette.NeonCyan, ApexPalette.NeonPurple, ApexPalette.NeonCyan)
                ),
                startAngle = 135f, sweepAngle = 270f * progress, useCenter = false,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
    }
}
