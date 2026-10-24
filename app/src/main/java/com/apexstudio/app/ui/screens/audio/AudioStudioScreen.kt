package com.apexstudio.app.ui.screens.audio

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apexstudio.app.presentation.viewmodel.*
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.EditorViewModelFactory
import com.apexstudio.app.ui.components.RealAudioWaveform
import com.apexstudio.app.ui.components.AppTopBar
import com.apexstudio.app.ui.components.GlassCard
import com.apexstudio.app.ui.theme.ApexPalette
import com.apexstudio.app.util.WaveformGenerator
import com.apexstudio.app.data.audio.TrackWaveformCache
import com.apexstudio.app.domain.model.AudioTrack
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
    val projectAudioTracks = audioState.project?.audioTracks.orEmpty()

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
                    if (projectAudioTracks.isEmpty()) {
                        Text(
                            "No audio tracks yet. Add music, voiceover, or sound effects to this project.",
                            color = ApexPalette.TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else projectAudioTracks.forEachIndexed { idx, track ->
                        WaveformTrackRow(
                            track = track,
                            samples = rememberTrackWaveform(track),
                            progress = if (audioState.durationMs > 0L) {
                                (audioState.currentTimeMs.toFloat() / audioState.durationMs).coerceIn(0f, 1f)
                            } else 0f,
                            color = trackAccent(track),
                            onMute = { vm.toggleAudioTrackMute(track.id) },
                            onSolo = { vm.toggleAudioTrackSolo(track.id) }
                        )
                        if (idx < projectAudioTracks.lastIndex) Spacer(Modifier.height(6.dp))
                    }
                }
            }

            SectionLabel("MIXER CONSOLE")
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (projectAudioTracks.isEmpty()) {
                        Text("Track controls appear here after audio is added.", color = ApexPalette.TextSecondary, fontSize = 10.sp)
                    } else projectAudioTracks.forEach { track ->
                        AudioTrackMixerRow(
                            track = track,
                            accent = trackAccent(track),
                            onVolumeChange = { vm.setTrackVolume(track.id, it) },
                            onPanChange = { vm.setAudioTrackPan(track.id, it) },
                            onMute = { vm.toggleAudioTrackMute(track.id) },
                            onSolo = { vm.toggleAudioTrackSolo(track.id) }
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

            SectionLabel("VOICE CHANGER + EFFECTS")
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    LabeledSlider(
                        label = "Pitch",
                        valueLabel = "${state.pitchSemitones.toInt()} st",
                        value = state.pitchSemitones,
                        valueRange = -12f..12f,
                        steps = 24,
                        onValueChange = { vm.setPitch(it) }
                    )
                    Spacer(Modifier.height(8.dp))
                    EffectToggleRow(
                        label = "Reverb",
                        enabled = state.reverbEnabled,
                        onToggle = { vm.enableReverb(!state.reverbEnabled, state.reverbPreset) }
                    )
                    if (state.reverbEnabled) {
                        Spacer(Modifier.height(4.dp))
                        ReverbPresetRow(
                            selected = state.reverbPreset.toInt(),
                            onSelect = { vm.enableReverb(true, it.toShort()) }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    EffectToggleRow(
                        label = "Echo",
                        enabled = state.echoEnabled,
                        onToggle = { vm.enableEcho(!state.echoEnabled) }
                    )
                    Spacer(Modifier.height(8.dp))
                    EffectToggleRow(
                        label = "Bass Boost",
                        enabled = state.bassBoostEnabled,
                        onToggle = { vm.enableBassBoost(!state.bassBoostEnabled, state.bassBoostStrength) }
                    )
                    if (state.bassBoostEnabled) {
                        Spacer(Modifier.height(4.dp))
                        LabeledSlider(
                            label = "Strength",
                            valueLabel = "${(state.bassBoostStrength.toInt() / 10)}%",
                            value = state.bassBoostStrength.toFloat(),
                            valueRange = 0f..1000f,
                            steps = 20,
                            onValueChange = { vm.enableBassBoost(true, it.toInt().toShort()) }
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
private fun rememberTrackWaveform(track: AudioTrack): FloatArray {
    val context = LocalContext.current
    var samples by remember(track.id, track.uri, track.trimStartMs, track.trimEndMs) {
        mutableStateOf(
            TrackWaveformCache.peek(track.uri, track.trimStartMs, track.trimEndMs) ?: FloatArray(0)
        )
    }
    LaunchedEffect(track.id, track.uri, track.trimStartMs, track.trimEndMs) {
        TrackWaveformCache.get(context, track.uri, track.trimStartMs, track.trimEndMs)?.let {
            samples = it
        }
    }
    return samples
}

private fun trackAccent(track: AudioTrack): Color = when (track.kind) {
    AudioTrack.Kind.ORIGINAL_VIDEO -> ApexPalette.NeonCyan
    AudioTrack.Kind.MUSIC -> ApexPalette.NeonEmerald
    AudioTrack.Kind.SFX -> ApexPalette.NeonPurple
}

@Composable
private fun WaveformTrackRow(
    track: AudioTrack,
    samples: FloatArray,
    progress: Float,
    color: Color,
    onMute: () -> Unit,
    onSolo: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(68.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(ApexPalette.BgElevated)
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(4.dp))
                .padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                track.name,
                color = color,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 9.sp,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(34.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ApexPalette.BgBase)
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(4.dp))
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            if (samples.isNotEmpty()) {
                RealAudioWaveform(
                    samples = samples,
                    modifier = Modifier.fillMaxSize(),
                    color = if (track.isMuted) ApexPalette.TextTertiary else color,
                    progress = progress
                )
            } else {
                Text("Waveform unavailable", color = ApexPalette.TextTertiary, fontSize = 8.sp)
            }
        }
        Spacer(Modifier.width(4.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (track.isMuted) ApexPalette.NeonPink.copy(alpha = 0.18f) else ApexPalette.BgElevated)
                    .border(1.dp, if (track.isMuted) ApexPalette.NeonPink else ApexPalette.BorderGlass, RoundedCornerShape(3.dp))
                    .clickable { onMute() }
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text("M", color = if (track.isMuted) ApexPalette.NeonPink else ApexPalette.TextSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (track.isSolo) ApexPalette.NeonCyan.copy(alpha = 0.18f) else ApexPalette.BgElevated)
                    .border(1.dp, if (track.isSolo) ApexPalette.NeonCyan else ApexPalette.BorderGlass, RoundedCornerShape(3.dp))
                    .clickable { onSolo() }
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text("S", color = if (track.isSolo) ApexPalette.NeonCyan else ApexPalette.TextSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AudioTrackMixerRow(
    track: AudioTrack,
    accent: Color,
    onVolumeChange: (Float) -> Unit,
    onPanChange: (Float) -> Unit,
    onMute: () -> Unit,
    onSolo: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(ApexPalette.BgBase).padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(track.name, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
            Text("${track.kind}", color = ApexPalette.TextTertiary, fontSize = 8.sp)
            Spacer(Modifier.width(6.dp))
            Box(Modifier.clip(RoundedCornerShape(3.dp)).background(if (track.isMuted) ApexPalette.NeonPink.copy(alpha = 0.2f) else ApexPalette.BgElevated).clickable { onMute() }.padding(horizontal = 6.dp, vertical = 3.dp)) {
                Text("M", color = if (track.isMuted) ApexPalette.NeonPink else ApexPalette.TextSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(4.dp))
            Box(Modifier.clip(RoundedCornerShape(3.dp)).background(if (track.isSolo) ApexPalette.NeonCyan.copy(alpha = 0.2f) else ApexPalette.BgElevated).clickable { onSolo() }.padding(horizontal = 6.dp, vertical = 3.dp)) {
                Text("S", color = if (track.isSolo) ApexPalette.NeonCyan else ApexPalette.TextSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("VOL", color = ApexPalette.TextTertiary, fontSize = 8.sp, modifier = Modifier.width(28.dp))
            Slider(
                value = track.volume.coerceIn(0f, 1f),
                onValueChange = onVolumeChange,
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f).height(28.dp),
                colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
            )
            Text("${(track.volume.coerceIn(0f, 1f) * 100).toInt()}%", color = accent, fontSize = 8.sp, modifier = Modifier.width(32.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PAN", color = ApexPalette.TextTertiary, fontSize = 8.sp, modifier = Modifier.width(28.dp))
            Slider(
                value = track.pan.coerceIn(-1f, 1f),
                onValueChange = onPanChange,
                valueRange = -1f..1f,
                modifier = Modifier.weight(1f).height(28.dp),
                colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
            )
            val panLabel = when {
                track.pan < -0.03f -> "L${(track.pan * -100).toInt()}"
                track.pan > 0.03f -> "R${(track.pan * 100).toInt()}"
                else -> "C"
            }
            Text(panLabel, color = accent, fontSize = 8.sp, modifier = Modifier.width(32.dp))
        }
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
// ====================================================================
// Phase E: voice-changer + audio effects composables.
// ====================================================================

@Composable
private fun LabeledSlider(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = ApexPalette.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(valueLabel, color = ApexPalette.NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}

@Composable
private fun EffectToggleRow(
    label: String,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ApexPalette.BgGlass)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = if (enabled) ApexPalette.NeonCyan else ApexPalette.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        androidx.compose.material3.Switch(
            checked = enabled,
            onCheckedChange = { onToggle() }
        )
    }
}

@Composable
private fun ReverbPresetRow(selected: Int, onSelect: (Int) -> Unit) {
    val presets = listOf(
        "None" to 0,
        "Small Room" to 1,
        "Medium Hall" to 2,
        "Large Hall" to 3,
        "Plate" to 4
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(presets) { (name, presetId) ->
            val isSelected = presetId == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) ApexPalette.NeonCyan.copy(alpha = 0.2f) else ApexPalette.BgGlass)
                    .border(
                        1.dp,
                        if (isSelected) ApexPalette.NeonCyan else ApexPalette.BorderGlass,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelect(presetId) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    name,
                    color = if (isSelected) ApexPalette.NeonCyan else ApexPalette.TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
