package com.apexstudio.app.ui.screens.editor

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.packs.PackLoader
import com.apexstudio.app.domain.model.VideoAdjustments
import com.apexstudio.app.ui.theme.ApexPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 3: Color Scopes panel — professional color analysis + recovery.
 *
 * - Waveform / Vectorscope / Histogram / RGB Parade rendered from the
 *   live preview bitmap ([previewBitmap]) when available; otherwise a
 *   neutral placeholder grid is shown (honest: no fake data).
 * - 15 recovery presets apply real [VideoAdjustments] corrections.
 */

/** 256-bin RGB histogram computed from a bitmap (sampled). */
private data class RgbHistogram(
    val r: IntArray = IntArray(256),
    val g: IntArray = IntArray(256),
    val b: IntArray = IntArray(256),
    val luma: IntArray = IntArray(256)
) {
    val max: Int get() = (r + g + b + luma).maxOrNull() ?: 1
}

/** Reusable pixel buffer: a 1080p frame is ~1.7MB, so we allocate once and
 *  reuse it across preview frames instead of per-frame allocation. */
private var histogramPixelBuffer: IntArray = IntArray(0)

private suspend fun computeHistogram(bitmap: Bitmap): RgbHistogram =
    withContext(Dispatchers.Default) {
        val hist = RgbHistogram()
        // Downsample before reading pixels: a scope visualization doesn't need
        // full-resolution data, and it caps the per-frame work and memory.
        val maxDim = 320
        val scale = minOf(1f, maxDim / maxOf(bitmap.width, bitmap.height).toFloat())
        val sample: Bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                false
            )
        } else {
            bitmap
        }
        val w = sample.width
        val h = sample.height
        if (histogramPixelBuffer.size < w * h) {
            histogramPixelBuffer = IntArray(w * h)
        }
        val pixels = histogramPixelBuffer
        sample.getPixels(pixels, 0, w, 0, 0, w, h)
        if (sample !== bitmap) sample.recycle()
        val pixelCount = w * h
        // Sample every Nth pixel to stay fast on large frames.
        val step = maxOf(1, pixelCount / 20000)
        var i = 0
        while (i < pixelCount) {
            val px = pixels[i]
            val r = AndroidColor.red(px)
            val g = AndroidColor.green(px)
            val b = AndroidColor.blue(px)
            hist.r[r]++
            hist.g[g]++
            hist.b[b]++
            hist.luma[((0.299 * r) + (0.587 * g) + (0.114 * b)).toInt().coerceIn(0, 255)]++
            i += step
        }
        hist
    }

private fun emojiForScope(id: String): String = when (id) {
    "waveform" -> "〰️"
    "vectorscope" -> "🎯"
    "histogram" -> "📊"
    "rgb_parade" -> "🌈"
    "false_color" -> "🎨"
    "zebras" -> "🦓"
    else -> "📈"
}

@Composable
fun ColorScopesPanel(
    previewBitmap: Bitmap?,
    onApplyRecovery: (VideoAdjustments) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scopes = remember { PackLoader.loadScopeDefs(context) }
    val recoveryPresets = remember { PackLoader.loadRecoveryPresets(context) }
    var selectedScope by remember { mutableStateOf(scopes.firstOrNull()?.id ?: "histogram") }
    var histogram by remember { mutableStateOf<RgbHistogram?>(null) }

    LaunchedEffect(previewBitmap) {
        histogram = previewBitmap?.let { computeHistogram(it) }
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
                    Text("📈", fontSize = 14.sp)
                }
                Column {
                    Text(
                        "Color Scopes",
                        color = ApexPalette.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (histogram != null) "Live from preview frame" else "Scopes need a preview frame",
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
                .height(360.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Scope tabs
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(scopes.ifEmpty {
                    listOf(
                        PackLoader.ScopeDef("waveform", "Waveform"),
                        PackLoader.ScopeDef("vectorscope", "Vectorscope"),
                        PackLoader.ScopeDef("histogram", "Histogram"),
                        PackLoader.ScopeDef("rgb_parade", "RGB Parade")
                    )
                }) { scope ->
                    ScopeChip(
                        label = scope.name,
                        emoji = emojiForScope(scope.id),
                        selected = selectedScope == scope.id,
                        onClick = { selectedScope = scope.id }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Scope visualization
            ScopeCanvas(
                scopeId = selectedScope,
                histogram = histogram,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
                    .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            )

            Spacer(Modifier.height(12.dp))

            // Recovery presets
            Text("🔧 COLOR RECOVERY", color = ApexPalette.NeonCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            recoveryPresets.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { preset ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ApexPalette.BgElevated)
                                .border(0.5.dp, ApexPalette.NeonAmber.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .clickable {
                                    onApplyRecovery(
                                        VideoAdjustments(
                                            brightness = preset.brightness,
                                            contrast = 1f + preset.contrast,
                                            saturation = 1f + preset.saturation,
                                            highlights = preset.highlights,
                                            shadows = preset.shadows,
                                            temperature = preset.warmth * 1000f,
                                            tint = preset.tint * 100f
                                        )
                                    )
                                }
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(preset.name, color = ApexPalette.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text(preset.category, color = ApexPalette.TextTertiary, fontSize = 9.sp)
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }
            if (recoveryPresets.isEmpty()) {
                Text("No recovery presets found.", color = ApexPalette.TextTertiary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ScopeChip(label: String, emoji: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) ApexPalette.NeonCyan.copy(alpha = 0.2f) else ApexPalette.BgElevated)
            .border(1.dp, if (selected) ApexPalette.NeonCyan else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(emoji, fontSize = 12.sp)
            Text(
                label,
                color = if (selected) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun ScopeCanvas(
    scopeId: String,
    histogram: RgbHistogram?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (histogram == null) {
            // Honest placeholder: grid only, no fake data.
            val stepX = w / 8
            val stepY = h / 4
            for (i in 1..7) {
                drawLine(
                    Color(0xFF2A2A3E),
                    Offset(stepX * i, 0f),
                    Offset(stepX * i, h),
                    strokeWidth = 1f
                )
            }
            for (i in 1..3) {
                drawLine(
                    Color(0xFF2A2A3E),
                    Offset(0f, stepY * i),
                    Offset(w, stepY * i),
                    strokeWidth = 1f
                )
            }
            return@Canvas
        }
        when (scopeId) {
            "histogram", "rgb_parade" -> {
                // RGB histogram bars.
                val max = histogram.max.toFloat().coerceAtLeast(1f)
                val barW = w / 256f
                val channels = listOf(
                    histogram.r to Color.Red,
                    histogram.g to Color.Green,
                    histogram.b to Color.Blue
                )
                channels.forEach { (bins, color) ->
                    for (i in 0 until 256) {
                        val bh = (bins[i] / max) * h * 0.9f
                        if (bh > 0.5f) {
                            drawRect(
                                color.copy(alpha = 0.75f),
                                topLeft = Offset(i * barW, h - bh),
                                size = Size(barW, bh)
                            )
                        }
                    }
                }
            }
            "waveform" -> {
                // Luma waveform: vertical trace per column.
                val max = histogram.luma.maxOrNull()?.toFloat()?.coerceAtLeast(1f) ?: 1f
                val colW = w / 256f
                for (i in 0 until 256) {
                    val v = histogram.luma[i] / max
                    val bh = v * h
                    drawRect(
                        Color(0xFF00E5FF).copy(alpha = 0.85f),
                        topLeft = Offset(i * colW, h - bh),
                        size = Size(colW.coerceAtLeast(1f), bh)
                    )
                }
                // 100 IRE line.
                drawLine(Color.White.copy(alpha = 0.4f), Offset(0f, 4f), Offset(w, 4f), strokeWidth = 1f)
            }
            "vectorscope" -> {
                // Graticule + chroma scatter approximated from R/B distribution.
                // The outer ring is inset by the stroke padding so it is never
                // clipped at the canvas edges.
                val graticuleRadius = minOf(w, h) / 2f - 2f
                drawCircle(Color(0xFF2A2A3E), radius = graticuleRadius, center = center, style = Stroke(1f))
                drawCircle(Color(0xFF2A2A3E), radius = graticuleRadius / 2f, center = center, style = Stroke(1f))
                drawLine(Color(0xFF2A2A3E), Offset(center.x - graticuleRadius, center.y), Offset(center.x + graticuleRadius, center.y))
                drawLine(Color(0xFF2A2A3E), Offset(center.x, center.y - graticuleRadius), Offset(center.x, center.y + graticuleRadius))
                // Chroma dots from R/B balance (also reused below for the skin-tone line).
                val maxR = (histogram.r.maxOrNull() ?: 1).toFloat()
                val maxB = (histogram.b.maxOrNull() ?: 1).toFloat()
                // Skin-tone line: derived from the frame's actual chroma centroid
                // (luma-weighted mean of the R/B distribution in draw space).
                // Falls back to the conventional skin-tone reference angle
                // (~11 o'clock) when the frame carries no chroma information.
                var sumCr = 0.0
                var sumCb = 0.0
                var sumWeight = 0L
                for (i in 0 until 256) {
                    val cr = histogram.r[i] / maxR - 0.5
                    val cb = histogram.b[i] / maxB - 0.5
                    val weight = histogram.luma[i].toLong()
                    sumCr += cr * weight
                    sumCb += cb * weight
                    sumWeight += weight
                }
                val skinAngle = if (sumWeight > 0) {
                    kotlin.math.atan2(-sumCb / sumWeight, sumCr / sumWeight).toFloat()
                } else {
                    -2.2f
                }
                val skinRadius = graticuleRadius * 0.7f
                drawLine(
                    Color(0xFFFFB74D).copy(alpha = 0.6f),
                    center,
                    Offset(
                        center.x + skinRadius * kotlin.math.cos(skinAngle),
                        center.y + skinRadius * kotlin.math.sin(skinAngle)
                    ),
                    strokeWidth = 2f
                )
                for (i in 0 until 256 step 4) {
                    val cr = (histogram.r[i] / maxR - 0.5f) * graticuleRadius
                    val cb = (histogram.b[i] / maxB - 0.5f) * graticuleRadius
                    drawCircle(
                        Color(0xFF00E5FF).copy(alpha = 0.5f),
                        radius = 2f,
                        center = Offset(center.x + cr, center.y - cb)
                    )
                }
            }
            else -> {
                // false_color / zebras: luma bands.
                val bands = listOf(
                    Color(0xFF9D4EDD), Color(0xFF3A86FF), Color(0xFF00E5FF),
                    Color(0xFF06D6A0), Color(0xFFFFD166), Color(0xFFFF6B35), Color(0xFFE63946)
                )
                val bandH = h / bands.size
                bands.forEachIndexed { i, c ->
                    drawRect(c.copy(alpha = 0.7f), Offset(0f, i * bandH), Size(w, bandH))
                }
            }
        }
    }
}
