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
import com.apexstudio.app.ui.components.GlassCard
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
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp)
    ) {
        // === Mixer Console ===
        SectionLabel("MIXER CONSOLE")
        Spacer(Modifier.height(8.dp))
        GlassCard(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            cornerRadius = 20.dp
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Main Output",
                        color = ApexPalette.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "128 BPM",
                        color = ApexPalette.NeonCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MixerChannel("Main", 0.8f, -0.2f, ApexPalette.NeonCyan)
                    MixerChannel("Vocal", 0.7f, 0.3f, ApexPalette.NeonPurple)
                    MixerChannel("Beat", 0.65f, 0f, ApexPalette.NeonPink)
                    MixerChannel("SFX", 0.5f, -0.4f, ApexPalette.NeonCyan)
                    MixerChannel("Amb", 0.4f, 0.5f, ApexPalette.NeonPurple)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // === EQ Visualizer ===
        SectionLabel("REAL-TIME EQ VISUALIZER")
        Spacer(Modifier.height(8.dp))
        GlassCard(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            cornerRadius = 20.dp
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                EqVisualizer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    EqKnob("Low", "80Hz", 0.3f)
                    EqKnob("Mid", "1kHz", 0.6f)
                    EqKnob("High", "5kHz", 0.75f)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // === AI Voice Enhancement ===
        SectionLabel("AI VOICE ENHANCE")
        Spacer(Modifier.height(8.dp))
        GlassCard(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            cornerRadius = 20.dp
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Neural Voice Isolation",
                            color = ApexPalette.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Reduce noise & enhance clarity",
                            color = ApexPalette.TextSecondary,
                            fontSize = 10.sp
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
                Spacer(Modifier.height(10.dp))
                Text(
                    "Clarity",
                    color = ApexPalette.TextSecondary,
                    fontSize = 10.sp
                )
                Slider(
                    value = state.clarity,
                    onValueChange = { vm.setClarity(it) },
                    colors = SliderDefaults.colors(
                        thumbColor = ApexPalette.NeonCyan,
                        activeTrackColor = ApexPalette.NeonCyan,
                        inactiveTrackColor = ApexPalette.BgElevated
                    )
                )
                Text(
                    "Reduce Noise",
                    color = ApexPalette.TextSecondary,
                    fontSize = 10.sp
                )
                Slider(
                    value = state.reduceNoise,
                    onValueChange = { vm.setReduceNoise(it) },
                    colors = SliderDefaults.colors(
                        thumbColor = ApexPalette.NeonPurple,
                        activeTrackColor = ApexPalette.NeonPurple,
                        inactiveTrackColor = ApexPalette.BgElevated
                    )
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(ApexPalette.NeonCyan, ApexPalette.NeonPurple)
                            )
                        )
                        .clickable { vm.toggleAiVoice() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Enhance Voice",
                        color = ApexPalette.BgDeep,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // === SFX Library ===
        SectionLabel("SOUND FX LIBRARY")
        Spacer(Modifier.height(8.dp))
        GlassCard(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            cornerRadius = 20.dp
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                state.tracks.forEach { track ->
                    SfxLibraryRow(track)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = ApexPalette.TextTertiary,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
    )
}

@Composable
private fun MixerChannel(
    name: String,
    volume: Float,
    pan: Float,
    accent: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(54.dp)
    ) {
        Text(
            name,
            color = ApexPalette.TextPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "Panning",
            color = ApexPalette.TextTertiary,
            fontSize = 7.sp
        )
        Spacer(Modifier.height(4.dp))
        // Pan knob
        PanKnob(pan = pan, accent = accent)
        Spacer(Modifier.height(6.dp))
        // Vertical fader
        VerticalFader(volume = volume, accent = accent)
        Spacer(Modifier.height(4.dp))
        Text(
            "${(volume * 100).toInt()}%",
            color = accent,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PanKnob(pan: Float, accent: Color) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(ApexPalette.BgElevated)
            .border(1.dp, accent.copy(alpha = 0.5f), CircleShape)
    ) {
        // Indicator
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = size.minDimension / 2f * 0.7f
            val angle = (pan * 135f) - 90f
            val rad = Math.toRadians(angle.toDouble())
            val x = cx + (r * kotlin.math.cos(rad)).toFloat()
            val y = cy + (r * kotlin.math.sin(rad)).toFloat()
            drawLine(
                color = accent,
                start = Offset(cx, cy),
                end = Offset(x, y),
                strokeWidth = 2f
            )
            drawCircle(color = Color.White, radius = 2.5f, center = Offset(x, y))
        }
    }
}

@Composable
private fun VerticalFader(volume: Float, accent: Color) {
    val trackHeight = 96.dp
    Box(
        modifier = Modifier
            .width(28.dp)
            .height(trackHeight)
            .clip(RoundedCornerShape(6.dp))
            .background(ApexPalette.BgBase)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.BottomCenter
    ) {
        val fillHeight = trackHeight * volume
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(fillHeight)
                .background(
                    Brush.verticalGradient(
                        listOf(accent, accent.copy(alpha = 0.3f))
                    )
                )
        )
        // Cap
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -(trackHeight * volume - 8.dp))
                .size(width = 30.dp, height = 18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(ApexPalette.BgElevated, ApexPalette.BgSurface)
                    )
                )
                .border(1.dp, accent, RoundedCornerShape(4.dp))
        )
    }
}

@Composable
private fun EqVisualizer(modifier: Modifier = Modifier) {
    val samples = 64
    val data = remember { WaveformGenerator.generate(99L, samples, 1.2f) }
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val barW = w / samples * 0.7f
        val step = w / samples
        val mid = h / 2
        for (i in 0 until samples) {
            val v = data[i]
            val barH = abs(v) * h * 0.9f
            val x = i * step
            drawLine(
                brush = Brush.verticalGradient(
                    listOf(
                        ApexPalette.NeonCyan.copy(alpha = 0.4f),
                        ApexPalette.NeonCyan,
                        ApexPalette.NeonPurple,
                        ApexPalette.NeonCyan,
                        ApexPalette.NeonCyan.copy(alpha = 0.4f)
                    )
                ),
                start = Offset(x, mid - barH / 2),
                end = Offset(x, mid + barH / 2),
                strokeWidth = barW
            )
        }
    }
}

@Composable
private fun EqKnob(name: String, freq: String, value: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(ApexPalette.BgElevated, ApexPalette.BgBase))
                )
                .border(2.dp, ApexPalette.NeonCyan, CircleShape)
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
                    color = ApexPalette.NeonCyan,
                    start = Offset(cx, cy),
                    end = Offset(x, y),
                    strokeWidth = 3f
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(name, color = ApexPalette.TextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(freq, color = ApexPalette.TextTertiary, fontSize = 8.sp)
    }
}

@Composable
private fun SfxLibraryRow(track: AudioTrack) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ApexPalette.BgElevated)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(ApexPalette.BgBase),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.MusicNote,
                null,
                tint = when (track.id) {
                    "a1" -> ApexPalette.NeonCyan
                    "a2" -> ApexPalette.NeonPurple
                    else -> ApexPalette.NeonPink
                },
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.name,
                color = ApexPalette.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "${(track.volume * 100).toInt()}% • ${track.id.uppercase()}",
                color = ApexPalette.TextTertiary,
                fontSize = 9.sp
            )
        }
        Icon(
            Icons.Default.PlayArrow,
            null,
            tint = ApexPalette.NeonCyan,
            modifier = Modifier
                .size(20.dp)
                .clickable { }
        )
    }
}
