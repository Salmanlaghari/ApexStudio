package com.apexstudio.app.ui.screens.audio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.apexstudio.app.ui.components.AppTopBar
import com.apexstudio.app.ui.components.AudioWaveform
import com.apexstudio.app.ui.components.GlassCard
import com.apexstudio.app.ui.theme.ApexPalette
import com.apexstudio.app.util.WaveformGenerator
import kotlin.math.abs

@Composable
fun AudioStudioScreen(
    projectId: String,
    onBack: () -> Unit,
    vm: EditorViewModel = viewModel()
) {
    val state by vm.audio.collectAsStateWithLifecycle()
    val editor by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .verticalScroll(rememberScrollState())
    ) {
        AppTopBar(
            title = "Shot 4",
            subtitle = "Audio Studio",
            onBack = onBack,
            onExport = {}
        )

        Spacer(Modifier.height(4.dp))

        GlassCard(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            cornerRadius = 22.dp
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("01:24:18", color = ApexPalette.NeonCyan,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    IconButton(Icons.Default.SkipPrevious)
                    IconButton(Icons.Default.FastRewind)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(
                                listOf(ApexPalette.NeonCyan, ApexPalette.NeonPurple)
                            ))
                            .clickable { vm.togglePlay() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (editor.isPlaying) Icons.Default.Pause
                            else Icons.Default.PlayArrow,
                            null, tint = ApexPalette.BgDeep, modifier = Modifier.size(24.dp))
                    }
                    IconButton(Icons.Default.FastForward)
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ApexPalette.BgElevated)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Column {
                            Text("Beat Sync", color = ApexPalette.TextSecondary, fontSize = 9.sp)
                            Text("Active: ${state.bpm} BPM", color = ApexPalette.NeonCyan, fontSize = 9.sp,
                                fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ApexPalette.BgElevated)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.4f)
                            .background(Brush.horizontalGradient(
                                listOf(ApexPalette.NeonCyan, ApexPalette.NeonPurple)
                            ))
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        state.tracks.forEachIndexed { idx, track ->
            TrackCard(track, idx + 1, vm)
            Spacer(Modifier.height(10.dp))
        }

        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GlassCard(
                modifier = Modifier.weight(1f),
                cornerRadius = 18.dp
            ) {
                Column {
                    Text("Audio Mixer Console",
                        color = ApexPalette.TextPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MixerChannel("Main", 0.75f, ApexPalette.NeonCyan)
                        MixerChannel("Vocal", 0.75f, ApexPalette.NeonPurple)
                        MixerChannel("Beat", 0.6f, ApexPalette.NeonPink)
                        MixerChannel("SFX", 0.5f, ApexPalette.NeonCyan)
                    }
                }
            }
            GlassCard(
                modifier = Modifier.weight(1f),
                cornerRadius = 18.dp
            ) {
                Column {
                    Text("Real-time EQ Visualizer",
                        color = ApexPalette.TextPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                    ) {
                        EqVisualizer()
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()) {
                        EqKnob("Low", "80Hz")
                        EqKnob("Mid", "1kHz")
                        EqKnob("High", "5kHz")
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GlassCard(
                modifier = Modifier.weight(1.4f),
                cornerRadius = 18.dp
            ) {
                Column {
                    Text("Sound FX Library",
                        color = ApexPalette.TextPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("Impact 01", "Riser 04", "Transition", "Ambience", "Whoosh")) { name ->
                            FxChip(name)
                        }
                    }
                }
            }
            GlassCard(
                modifier = Modifier.weight(1f),
                cornerRadius = 18.dp
            ) {
                Column {
                    Text("AI Voice Enhance",
                        color = ApexPalette.TextPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Toggle", color = ApexPalette.TextSecondary, fontSize = 11.sp)
                        Spacer(Modifier.weight(1f))
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
                    Text("Clarity", color = ApexPalette.TextSecondary, fontSize = 10.sp)
                    Slider(
                        value = state.clarity,
                        onValueChange = { vm.setClarity(it) },
                        colors = SliderDefaults.colors(
                            thumbColor = ApexPalette.NeonCyan,
                            activeTrackColor = ApexPalette.NeonCyan
                        )
                    )
                    Text("Reduce Noise", color = ApexPalette.TextSecondary, fontSize = 10.sp)
                    Slider(
                        value = state.reduceNoise,
                        onValueChange = { vm.setReduceNoise(it) },
                        colors = SliderDefaults.colors(
                            thumbColor = ApexPalette.NeonPurple,
                            activeTrackColor = ApexPalette.NeonPurple
                        )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(
                                listOf(ApexPalette.NeonCyan, ApexPalette.NeonPurple)
                            ))
                            .clickable { vm.toggleAiVoice() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Enhance", color = ApexPalette.BgDeep,
                            fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun IconButton(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(ApexPalette.BgElevated)
            .clickable { },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = ApexPalette.TextPrimary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun TrackCard(track: AudioTrack, idx: Int, vm: EditorViewModel) {
    GlassCard(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        cornerRadius = 18.dp
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ApexPalette.BgElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Track $idx", color = ApexPalette.TextTertiary, fontSize = 8.sp)
                        Text(
                            track.id.take(1).uppercase() + track.id.drop(1).take(2),
                            color = ApexPalette.NeonCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(track.name,
                        color = ApexPalette.TextPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                    Text("${(track.volume * 100).toInt()}%",
                        color = ApexPalette.NeonCyan, fontSize = 10.sp)
                }
                Icon(Icons.Default.VolumeOff, null,
                    tint = if (track.isMuted) ApexPalette.Danger else ApexPalette.TextSecondary,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { vm.toggleMute(track.id) })
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (track.isSolo) ApexPalette.NeonCyan.copy(alpha = 0.18f)
                            else ApexPalette.BgElevated
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Solo", color = if (track.isSolo) ApexPalette.NeonCyan
                        else ApexPalette.TextSecondary, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ApexPalette.BgBase)
            ) {
                AudioWaveform(
                    seed = track.id.hashCode().toLong(),
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    color = when (track.id) {
                        "a1" -> ApexPalette.NeonCyan
                        "a2" -> ApexPalette.NeonPurple
                        else -> ApexPalette.NeonPink
                    },
                    secondaryColor = Color.Gray,
                    progress = 0.4f
                )
            }
        }
    }
}

@Composable
private fun MixerChannel(name: String, vol: Float, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, color = ApexPalette.TextPrimary, fontSize = 9.sp,
            fontWeight = FontWeight.Bold)
        Text("Panning", color = ApexPalette.TextTertiary, fontSize = 7.sp)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(14.dp)
                .height(60.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ApexPalette.BgBase)
        ) {
            val barH = 60 * vol
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(barH.dp)
                    .background(Brush.verticalGradient(
                        listOf(color, color.copy(alpha = 0.3f))
                    ))
            )
        }
        Spacer(Modifier.height(2.dp))
        Text("${(vol * 100).toInt()}%", color = color, fontSize = 9.sp)
    }
}

@Composable
private fun EqKnob(name: String, freq: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(ApexPalette.BgElevated, ApexPalette.BgBase))
                )
                .border(2.dp, ApexPalette.NeonCyan, CircleShape)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
                    .size(width = 3.dp, height = 8.dp)
                    .background(ApexPalette.NeonCyan)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(name, color = ApexPalette.TextPrimary, fontSize = 9.sp)
        Text(freq, color = ApexPalette.TextTertiary, fontSize = 8.sp)
    }
}

@Composable
private fun EqVisualizer() {
    val samples = 64
    val data = remember { WaveformGenerator.generate(99L, samples, 1.2f) }
    Canvas(modifier = Modifier.fillMaxSize()) {
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
                        ApexPalette.NeonCyan, ApexPalette.NeonPurple,
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
private fun FxChip(name: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(70.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(ApexPalette.BgBase)
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(
                    listOf(ApexPalette.NeonCyan.copy(alpha = 0.5f), Color.Transparent)
                )),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.GraphicEq, null,
                tint = ApexPalette.NeonCyan, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(name, color = ApexPalette.TextPrimary, fontSize = 8.sp)
    }
}
