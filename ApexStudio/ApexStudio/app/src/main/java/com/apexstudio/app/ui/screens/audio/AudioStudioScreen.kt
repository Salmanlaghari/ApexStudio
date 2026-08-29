package com.apexstudio.app.ui.screens.audio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.EditorViewModelFactory
import com.apexstudio.app.ui.components.AudioWaveform
import com.apexstudio.app.ui.components.GlassCard
import com.apexstudio.app.ui.components.ScreenTopBar
import com.apexstudio.app.ui.theme.ApexPalette
import com.apexstudio.app.util.WaveformGenerator
import kotlin.math.abs

@Composable
fun AudioStudioScreen(
    projectId: String,
    onBack: () -> Unit,
    vm: EditorViewModel = viewModel(factory = EditorViewModelFactory())
) {
    val state by vm.audio.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ApexPalette.BgBase)
    ) {
        ScreenTopBar(
            title = "AUDIOLAB MIXER",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Multi-track Waveform Timeline (horizontal cards)
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    listOf(
                        Triple("1. VOCALS", 7L to "03:12", ApexPalette.NeonEmerald),
                        Triple("2. MUSIC", 13L to "04:30", ApexPalette.NeonEmerald),
                        Triple("3. BEAT", 23L to "01:58", ApexPalette.NeonEmerald)
                    ).forEachIndexed { idx, (name, info, color) ->
                        MultiTrackRow(
                            name = name,
                            duration = info.second,
                            seed = info.first,
                            color = color,
                            isActive = idx == 0
                        )
                        if (idx < 2) Spacer(Modifier.height(4.dp))
                    }
                }
            }

            // Mixer Console (horizontal faders)
            SectionLabel("MIXER CONSOLE")
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MixerChannel("1. VOCALS", 0.8f, 0f, -12, ApexPalette.NeonEmerald)
                        MixerChannel("2. MUSIC", 0.7f, 0f, -18, ApexPalette.NeonEmerald)
                        MixerChannel("3. BEAT", 0.65f, 0f, -6, ApexPalette.NeonEmerald)
                        MixerChannel("4. SYNTH", 0.5f, 0f, -20, ApexPalette.NeonEmerald)
                        MixerChannel("5. DRUMS", 0.5f, 0f, -45, ApexPalette.NeonEmerald)
                        MixerChannel("MASTER", 0.9f, 0f, 0, ApexPalette.NeonEmerald)
                    }
                }
            }

            // Real-Time EQ Visualizer
            SectionLabel("SPECTRUM ANALYZER")
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    EqVisualizer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        EqKnob("Low", "80Hz", 0.3f)
                        EqKnob("Mid", "1kHz", 0.6f)
                        EqKnob("High", "5kHz", 0.75f)
                    }
                }
            }

            // AI Voice Enhancement
            SectionLabel("AI VOICE ENHANCEMENT")
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(ApexPalette.NeonCyan, ApexPalette.NeonPurple)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.GraphicEq,
                            null,
                            tint = ApexPalette.BgDeep,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "AI Noise Reduction",
                            color = ApexPalette.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Reduce noise & enhance clarity",
                            color = ApexPalette.TextSecondary,
                            fontSize = 9.sp
                        )
                    }
                    Switch(
                        checked = state.aiVoiceEnhance,
                        onCheckedChange = { vm.toggleAiVoice() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ApexPalette.NeonCyan,
                            checkedTrackColor = ApexPalette.NeonCyan.copy(alpha = 0.3f)
                        )
                    )
                }
            }

            // SFX Library (horizontal scrollable)
            SectionLabel("SOUND FX LIBRARY")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("Riser", "Transition", "Ambience", "Whoosh", "Impact", "Drop")) { name ->
                    FxCard(name)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = ApexPalette.TextTertiary,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun MultiTrackRow(
    name: String,
    duration: String,
    seed: Long,
    color: Color,
    isActive: Boolean
) {
    val border = if (isActive) color else ApexPalette.BorderGlass
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ApexPalette.BgBase)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .padding(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.width(72.dp)) {
                Text(
                    name,
                    color = color,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp
                )
                Text(
                    if (isActive) "Active" else "Muted",
                    color = if (isActive) color else ApexPalette.TextTertiary,
                    fontSize = 8.sp
                )
                Text(
                    duration,
                    color = ApexPalette.TextTertiary,
                    fontSize = 8.sp
                )
            }
            Spacer(Modifier.width(6.dp))
            AudioWaveform(
                seed = seed,
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp),
                color = color,
                secondaryColor = color.copy(alpha = 0.3f),
                progress = 0.5f,
                samples = 120
            )
            Spacer(Modifier.width(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(ApexPalette.BgElevated)
                        .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(3.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("R", color = ApexPalette.TextSecondary, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(ApexPalette.BgElevated)
                        .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(3.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("S", color = ApexPalette.TextSecondary, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(ApexPalette.BgElevated)
                        .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(3.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("M", color = ApexPalette.TextSecondary, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MixerChannel(
    name: String,
    volume: Float,
    pan: Float,
    db: Int,
    accent: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(46.dp)
    ) {
        Text(
            name,
            color = ApexPalette.TextPrimary,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Spacer(Modifier.height(3.dp))
        // Volume meter
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(ApexPalette.BgBase)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(volume)
                    .background(
                        Brush.horizontalGradient(
                            listOf(accent, accent.copy(alpha = 0.5f))
                        )
                    )
            )
        }
        Spacer(Modifier.height(3.dp))
        // Pan knob
        PanKnob(pan = pan, accent = accent)
        Spacer(Modifier.height(3.dp))
        Text("L < > R", color = ApexPalette.TextTertiary, fontSize = 6.sp)
        Spacer(Modifier.height(3.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ApexPalette.BgElevated)
                    .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("M", color = ApexPalette.TextSecondary, fontSize = 6.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ApexPalette.BgElevated)
                    .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("S", color = ApexPalette.TextSecondary, fontSize = 6.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(4.dp))
        // Fader
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ApexPalette.BgBase)
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(4.dp))
        ) {
            // dB scale
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .padding(vertical = 2.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("0", "-10", "-20", "-30", "-40", "-60").forEach {
                    Text(it, color = ApexPalette.TextTertiary, fontSize = 5.sp)
                }
            }
            // Fader fill
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height((80 * volume).dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(accent, accent.copy(alpha = 0.3f))
                        )
                    )
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "${if (db > 0) "+" else ""}${db}dB",
            color = accent,
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PanKnob(pan: Float, accent: Color) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(ApexPalette.BgElevated)
            .border(1.dp, accent.copy(alpha = 0.5f), CircleShape)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = size.minDimension / 2f * 0.6f
            val angle = (pan * 135f) - 90f
            val rad = Math.toRadians(angle.toDouble())
            val x = cx + (r * kotlin.math.cos(rad)).toFloat()
            val y = cy + (r * kotlin.math.sin(rad)).toFloat()
            drawLine(
                color = accent,
                start = Offset(cx, cy),
                end = Offset(x, y),
                strokeWidth = 1.5f
            )
        }
    }
}

@Composable
private fun EqVisualizer(modifier: Modifier = Modifier) {
    val samples = 64
    val data = remember { WaveformGenerator.generate(99L, samples, 1.2f) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(ApexPalette.BgBase)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(6.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val barW = w / samples * 0.6f
            val step = w / samples
            val mid = h / 2
            // Grid
            for (i in 1..3) {
                val x = w * i / 4
                drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 1f
                )
            }
            for (i in 0 until samples) {
                val v = data[i]
                val barH = abs(v) * h * 0.9f
                val x = i * step
                drawLine(
                    brush = Brush.verticalGradient(
                        listOf(
                            ApexPalette.NeonEmerald.copy(alpha = 0.5f),
                            ApexPalette.NeonEmerald,
                            ApexPalette.NeonCyan
                        )
                    ),
                    start = Offset(x, mid - barH / 2),
                    end = Offset(x, mid + barH / 2),
                    strokeWidth = barW
                )
            }
        }
    }
}

@Composable
private fun EqKnob(name: String, freq: String, value: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(ApexPalette.BgElevated, ApexPalette.BgBase))
                )
                .border(2.dp, ApexPalette.NeonEmerald, CircleShape)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = size.minDimension / 2f * 0.7f
                val angle = (value * 270f) - 135f
                val rad = Math.toRadians(angle.toDouble())
                val x = cx + (r * kotlin.math.cos(rad)).toFloat()
                val y = cy + (r * kotlin.math.sin(rad)).toFloat()
                drawLine(
                    color = ApexPalette.NeonEmerald,
                    start = Offset(cx, cy),
                    end = Offset(x, y),
                    strokeWidth = 3f
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(name, color = ApexPalette.TextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(freq, color = ApexPalette.TextTertiary, fontSize = 8.sp)
    }
}

@Composable
private fun FxCard(name: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(ApexPalette.BgSurface)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(10.dp))
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(ApexPalette.NeonEmerald.copy(alpha = 0.4f), Color.Transparent)
                    )
                )
                .border(1.dp, ApexPalette.NeonEmerald.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.MusicNote,
                null,
                tint = ApexPalette.NeonEmerald,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            name,
            color = ApexPalette.TextPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
