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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.presentation.state.AudioStudioState
import com.apexstudio.app.ui.theme.ApexPalette

/**
 * Audio mixer bottom sheet. Renders the three canonical track rows
 * the user expects from a CapCut/Premiere-style mixer:
 *
 * 1. **Original video audio** — surfaced as a synthetic row backed by
 *    [AudioStudioState.isMuted] so the user can mute/unmute the V1
 *    audio without having to add a new track first.
 * 2. **Music tracks** — every audio track in [AudioStudioState.tracks]
 *    with kind = MUSIC.
 * 3. **Voiceover / SFX** — every track with kind = SFX.
 *
 * Each row exposes a volume fader (0..1), a mute toggle, a fade-in
 * and fade-out field in milliseconds, and a remove button. The
 * `+ Add Track` affordance seeds the user with a placeholder track
 * the model knows about; full SAF-based audio import is wired in
 * via the existing [onAddTrack] callback so a future file-picker
 * change is a drop-in.
 */
@Composable
fun AudioMixerPanel(
    state: AudioStudioState,
    muteOriginalVideo: Boolean,
    onMuteOriginal: (Boolean) -> Unit,
    onAddTrack: (name: String, uri: String, kind: AudioTrack.Kind) -> Unit,
    onRemoveTrack: (trackId: String) -> Unit,
    onVolume: (trackId: String, vol: Float) -> Unit,
    onMute: (trackId: String) -> Unit,
    onSolo: (trackId: String) -> Unit,
    onTrim: (trackId: String, startMs: Long, endMs: Long) -> Unit,
    onFadeIn: (trackId: String, ms: Long) -> Unit,
    onFadeOut: (trackId: String, ms: Long) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(ApexPalette.BgElevated)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = null,
                tint = ApexPalette.NeonCyan,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Audio Mixer",
                color = ApexPalette.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = ApexPalette.TextSecondary)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Mix V1 audio, music and SFX with per-track faders, mute and fades.",
            color = ApexPalette.TextSecondary,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(12.dp))

        // --- Original video audio row (synthetic, no track id) ---
        AudioMixerRow(
            label = "Original video audio",
            icon = if (muteOriginalVideo) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
            volume = if (muteOriginalVideo) 0f else state.volume,
            muted = muteOriginalVideo,
            solo = false,
            accent = ApexPalette.NeonPurple,
            onVolume = { onMuteOriginal(it <= 0.001f) },
            onMute = { onMuteOriginal(!muteOriginalVideo) },
            onSolo = { /* no-op for the V1 row */ },
            onRemove = null
        )

        Spacer(Modifier.height(8.dp))

        // --- Per-track rows ---
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.tracks, key = { it.id }) { track ->
                AudioTrackRow(
                    track = track,
                    onVolume = { onVolume(track.id, it) },
                    onMute = { onMute(track.id) },
                    onSolo = { onSolo(track.id) },
                    onRemove = { onRemoveTrack(track.id) },
                    onFadeIn = { onFadeIn(track.id, it) },
                    onFadeOut = { onFadeOut(track.id, it) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AddTrackButton(
                label = "+ Music",
                icon = Icons.Default.MusicNote,
                onClick = { onAddTrack("Background music", "music://placeholder", AudioTrack.Kind.MUSIC) }
            )
            AddTrackButton(
                label = "+ SFX / Voiceover",
                icon = Icons.Default.Mic,
                onClick = { onAddTrack("SFX", "sfx://placeholder", AudioTrack.Kind.SFX) }
            )
        }
    }
}

@Composable
private fun AudioMixerRow(
    label: String,
    icon: ImageVector,
    volume: Float,
    muted: Boolean,
    solo: Boolean,
    accent: Color,
    onVolume: (Float) -> Unit,
    onMute: () -> Unit,
    onSolo: () -> Unit,
    onRemove: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ApexPalette.BgBase)
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = ApexPalette.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Slider(
                value = if (muted) 0f else volume,
                onValueChange = { onVolume(it) },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = ApexPalette.BorderGlass
                )
            )
        }
        IconButton(onClick = onMute) {
            Icon(
                imageVector = if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                contentDescription = if (muted) "Unmute" else "Mute",
                tint = if (muted) ApexPalette.TextTertiary else ApexPalette.TextPrimary
            )
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove", tint = ApexPalette.TextTertiary)
            }
        }
    }
}

@Composable
private fun AudioTrackRow(
    track: AudioTrack,
    onVolume: (Float) -> Unit,
    onMute: () -> Unit,
    onSolo: () -> Unit,
    onRemove: () -> Unit,
    onFadeIn: (Long) -> Unit,
    onFadeOut: (Long) -> Unit
) {
    val accent = when {
        track.isSolo -> ApexPalette.NeonCyan
        track.isMuted -> ApexPalette.TextTertiary
        else -> ApexPalette.NeonPurple
    }
    val kindLabel = if (track.volume >= 0.99f) "SFX" else "Music"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ApexPalette.BgBase)
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        AudioMixerRow(
            label = "${track.name} • $kindLabel",
            icon = if (track.isMuted) Icons.Default.MusicOff else Icons.Default.MusicNote,
            volume = track.volume,
            muted = track.isMuted,
            solo = track.isSolo,
            accent = accent,
            onVolume = onVolume,
            onMute = onMute,
            onSolo = onSolo,
            onRemove = onRemove
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FadeField(
                label = "Fade in",
                ms = track.fadeInMs,
                accent = accent,
                onChange = onFadeIn
            )
            Spacer(Modifier.width(12.dp))
            FadeField(
                label = "Fade out",
                ms = track.fadeOutMs,
                accent = accent,
                onChange = onFadeOut
            )
        }
    }
}

@Composable
private fun FadeField(label: String, ms: Long, accent: Color, onChange: (Long) -> Unit) {
    var text by remember(ms) { mutableStateOf(ms.toString()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = ApexPalette.TextSecondary, fontSize = 11.sp, modifier = Modifier.width(64.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(ApexPalette.BgElevated)
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = text,
                onValueChange = {
                    text = it.filter { c -> c.isDigit() }.take(5)
                    val v = text.toLongOrNull() ?: 0L
                    onChange(v)
                },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = ApexPalette.TextPrimary,
                    fontSize = 12.sp
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(accent),
                modifier = Modifier.width(60.dp)
            )
        }
        Spacer(Modifier.width(4.dp))
        Text("ms", color = ApexPalette.TextTertiary, fontSize = 11.sp)
    }
}

@Composable
private fun AddTrackButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ApexPalette.NeonCyan.copy(alpha = 0.10f))
            .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = ApexPalette.NeonCyan, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = ApexPalette.NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}
