package com.apexstudio.app.ui.screens.audio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.EditorViewModelFactory
import com.apexstudio.app.ui.components.AudioWaveform
import com.apexstudio.app.ui.components.AppTopBar
import com.apexstudio.app.ui.components.GlassCard
import com.apexstudio.app.ui.theme.ApexPalette
import com.apexstudio.app.util.WaveformGenerator
import kotlin.math.abs

@Composable
fun AudioStudioScreen(
    projectId: String,
    onBack: () -> Unit,
    onExport: () -> Unit,
    vm: EditorViewModel = viewModel(
        key = projectId,
        factory = EditorViewModelFactory(projectId = projectId)
    )
) {
    val state by vm.audio.collectAsStateWithLifecycle()
    val audioState by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ApexPalette.BgBase)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        AppTopBar(
            title = "Audio",
            subtitle = "ApexStudio • Audio Studio",
            onBack = onBack,
            onExport = onExport
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionLabel("MULTI-TRACK TIMELINE")
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    listOf(
                        Triple("Vocals", 7L, ApexPalette.NeonEmerald),
                        Triple("Beats", 13L, ApexPalette.NeonCyan),
                        Triple("SFX", 23L, ApexPalette.NeonPurple),
                        Triple("Music", 31L, ApexPalette.NeonPink)
                    ).forEachIndexed { idx, (name, seed, color) ->
                        WaveformTrackRow(
                            name = name,
                            seed = seed,
                            color = color,
                            state = state,
                            onMute = { vm.toggleMuteEngine() },
                            onSolo = { vm.toggleSoloEngine() }
                        )
                        if (idx < 3) Spacer(Modifier.height(6.dp))
                    }
                }
            }

            SectionLabel("MIXER CONSOLE")
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MixerChannel("Main", state.volume, 0f, ApexPalette.NeonCyan,
                            onVolumeChange = { vm.setVolume(it) },
                            onMute = { vm.toggleMuteEngine() },
                            onSolo = { vm.toggleSoloEngine() },
                            isMuted = state.isMuted, isSolo = state.isSolo
                        )
                        MixerChannel("Vocal", state.volume * 0.8f, 0.3f, ApexPalette.NeonPurple,
                            onVolumeChange = { vm.setVolume(it) },
                            onMute = { vm.toggleMuteEngine() },
                            onSolo = { vm.toggleSoloEngine() },
                            isMuted = state.isMuted, isSolo = state.isSolo
                        )
                        MixerChannel("Beat", state.volume * 0.6f, 0f, ApexPalette.NeonEmerald,
                            onVolumeChange = { vm.setVolume(it) },
                            onMute = { vm.toggleMuteEngine() },
                            onSolo = { vm.toggleSoloEngine() },
                            isMuted = state.isMuted, isSolo = state.isSolo
                        )
                        MixerChannel("SFX", state.volume * 0.5f, -0.4f, ApexPalette.NeonPink,
                            onVolumeChange = { vm.setVolume(it) },
                            onMute = { vm.toggleMuteEngine() },
                            onSolo = { vm.toggleSoloEngine() },
                            isMuted = state.isMuted, isSolo = state.isSolo
                        )
                    }
                }
            }

            SectionLabel("REAL-TIME EQ VISUALIZER")
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    EqVisualizer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(70.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        EqKnob("Low", "80Hz", state.lowEQ.toFloat() / 1000f) { vm.setLowEQ(it.toInt().toShort()) }
                        EqKnob("Mid", "1kHz", state.midEQ.toFloat() / 1000f) { vm.setMidEQ(it.toInt().toShort()) }
                        EqKnob("High", "5kHz", state.highEQ.toFloat() / 1000f) { vm.setMidEQ(it.toInt().toShort()) }
                    }
                }
            }

            SectionLabel("AI VOICE ENHANCEMENT")
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
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
                                modifier = Modifier.size(16.dp)
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
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Clarity",
                        color = ApexPalette.TextSecondary,
                        fontSize = 9.sp
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
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(ApexPalette.NeonCyan, ApexPalette.NeonPurple)
                                )
                            )
                            .clickable { vm.toggleAiVoice() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Apply Enhancement",
                            color = ApexPalette.BgDeep,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            SectionLabel("SOUND FX LIBRARY")
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    for (name in listOf("Riser", "Transition", "Ambience", "Whoosh", "Impact")) {
                        FxLibraryRow(name)
                        Spacer(Modifier.height(6.dp))
                    }
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
        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
    )
}

@Composable
private fun WaveformTrackRow(
    name: String,
    seed: Long,
    color: Color,
    state: com.apexstudio.app.presentation.state.AudioStudioState,
    onMute: () -> Unit,
    onSolo: () -> Unit
) {
    val isMuted = state.isMuted && seed == 7L
    val isSolo = state.isSolo
    val width = 600
    val density = androidx.compose.ui.platform.LocalDensity.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(60.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(ApexPalette.BgElevated)
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(4.dp))
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                name,
                color = color,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 9.sp,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(3.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(34.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ApexPalette.BgBase)
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(4.dp))
                .padding(2.dp)
        ) {
            AudioWaveform(
                seed = seed,
                modifier = Modifier.fillMaxSize(),
                color = if (isMuted) ApexPalette.TextTertiary else color,
                secondaryColor = color.copy(alpha = 0.3f),
                progress = 0.4f,
                samples = 100
            )
        }
        Spacer(Modifier.width(3.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (isMuted) ApexPalette.NeonPink.copy(alpha = 0.18f)
                        else ApexPalette.BgElevated
                    )
                    .border(
                        1.dp,
                        if (isMuted) ApexPalette.NeonPink else ApexPalette.BorderGlass,
                        RoundedCornerShape(3.dp)
                    )
                    .clickable { onMute() }
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    "M",
                    color = if (isMuted) ApexPalette.NeonPink else ApexPalette.TextSecondary,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (isSolo) ApexPalette.NeonCyan.copy(alpha = 0.18f)
                        else ApexPalette.BgElevated
                    )
                    .border(
                        1.dp,
                        if (isSolo) ApexPalette.NeonCyan else ApexPalette.BorderGlass,
                        RoundedCornerShape(3.dp)
                    )
                    .clickable { onSolo() }
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    "S",
                    color = if (isSolo) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MixerChannel(
    name: String,
    volume: Float,
    pan: Float,
    accent: Color,
    onVolumeChange: (Float) -> Unit,
    onMute: () -> Unit,
    onSolo: () -> Unit,
    isMuted: Boolean,
    isSolo: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(60.dp)
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
        PanKnob(pan = pan, accent = accent)
        Spacer(Modifier.height(6.dp))
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
    val trackHeight = 80.dp
    val capOffset = -(trackHeight * (1f - volume))
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
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(y = capOffset)
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(ApexPalette.BgElevated, ApexPalette.BgSurface)
                    )
                )
                .border(1.dp, accent, RoundedCornerShape(3.dp))
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
private fun EqKnob(name: String, freq: String, value: Float, onValueChange: (Float) -> Unit) {
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
private fun FxLibraryRow(name: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ApexPalette.BgElevated)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
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
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                color = ApexPalette.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "FX Library",
                color = ApexPalette.TextTertiary,
                fontSize = 8.sp
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