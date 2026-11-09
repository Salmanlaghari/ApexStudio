package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RangeSlider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.presentation.state.AudioStudioState
import com.apexstudio.app.ui.theme.EditorPalette as ApexPalette

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
    onSplit: (trackId: String) -> Unit = {},
    onFadeIn: (trackId: String, ms: Long) -> Unit,
    onFadeOut: (trackId: String, ms: Long) -> Unit,
    onImportLocalAudio: () -> Unit = {},
    onOpenRoyaltyFreeMusic: () -> Unit = {},
    onClose: () -> Unit,
    /** Phase 4: player audio session for the live visualizer (0 = none yet). */
    audioSessionId: Int = 0,
    /** Timeline playhead (state mirror) — volume faders show the
     * effective volume AT THE PLAYHEAD when volume keyframes exist. */
    playheadMs: Long = 0L,
    /** Manual first volume keyframe at the playhead (CapCut: the
     * first keyframe is always manual; later fader moves auto-upsert). */
    onAddVolumeKeyframe: (trackId: String) -> Unit = {},
    /** Delete one volume keyframe pin. */
    onRemoveVolumeKeyframe: (trackId: String, keyframeId: String) -> Unit = { _, _ -> },
    /** Seek the playhead (tap a volume-keyframe chip). */
    onSeekTo: (Long) -> Unit = {},
    /** Audio Intelligence: normalize using an explicitly labelled LUFS-like estimate. */
    onNormalizeTrack: (trackId: String) -> Unit = {},
    /** Audio Intelligence: duck music under the selected voice/SFX track. */
    onAutoDuckMusic: (musicTrackId: String, voiceTrackId: String) -> Unit = { _, _ -> },
    /** Audio Intelligence: choose a source window matching the project duration. */
    onFitMusicToProject: (trackId: String) -> Unit = {},
    /** Audio Intelligence: remove detected silence while retaining source ranges. */
    onRemoveSilence: (trackId: String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(ApexPalette.BgElevated)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("audio-mixer-panel-root")
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
        Spacer(Modifier.height(6.dp))

        // Keep the title and close action pinned. Every feature group is a
        // horizontal card, so the editor preview is not displaced by a tall
        // vertical list on portrait phones.
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .testTag("audio-mixer-panel-cards"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item(key = "quick-controls") {
                Column(
                    modifier = Modifier
                        .width(292.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ApexPalette.BgBase)
                        .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AddTrackButton(
                        label = "Local Audio",
                        icon = Icons.Default.FolderOpen,
                        accent = ApexPalette.NeonCyan,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onImportLocalAudio
                    )
                    AddTrackButton(
                        label = "Free Royalty Music",
                        icon = Icons.Default.GraphicEq,
                        accent = ApexPalette.NeonPurple,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onOpenRoyaltyFreeMusic
                    )
                    AudioMixerRow(
                        label = "Original video audio",
                        icon = if (muteOriginalVideo) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        volume = if (muteOriginalVideo) 0f else state.volume,
                        muted = muteOriginalVideo,
                        solo = false,
                        accent = ApexPalette.NeonPurple,
                        onVolume = { onMuteOriginal(it <= 0.001f) },
                        onMute = { onMuteOriginal(!muteOriginalVideo) },
                        onSolo = {},
                        onRemove = null,
                        showVolume = false,
                        showSolo = false
                    )
                    Text(
                        text = "Original gain is unavailable here; mute is supported.",
                        color = ApexPalette.TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
            item(key = "visualizer") {
                Column(
                    modifier = Modifier
                        .width(292.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ApexPalette.BgBase)
                        .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    MixerVisualizer(audioSessionId = audioSessionId, modifier = Modifier.fillMaxWidth())
                }
            }
            items(state.tracks, key = { it.id }) { track ->
                AudioTrackRow(
                    track = track,
                    playheadMs = playheadMs,
                    onVolume = { onVolume(track.id, it) },
                    onMute = { onMute(track.id) },
                    onSolo = { onSolo(track.id) },
                    onRemove = { onRemoveTrack(track.id) },
                    onFadeIn = { onFadeIn(track.id, it) },
                    onFadeOut = { onFadeOut(track.id, it) },
                    onAddVolumeKeyframe = { onAddVolumeKeyframe(track.id) },
                    onRemoveVolumeKeyframe = { kfId -> onRemoveVolumeKeyframe(track.id, kfId) },
                    onSeekTo = onSeekTo,
                    voiceTrackId = state.tracks.firstOrNull { it.kind == AudioTrack.Kind.SFX }?.id,
                    onNormalizeTrack = onNormalizeTrack,
                    onAutoDuckMusic = onAutoDuckMusic,
                    onFitMusicToProject = onFitMusicToProject,
                    onRemoveSilence = onRemoveSilence
                )
            }
            items(state.tracks, key = { "trim-${it.id}" }) { track ->
                AudioSourceTrimCard(
                    track = track,
                    playheadMs = playheadMs,
                    onTrim = { startMs, endMs -> onTrim(track.id, startMs, endMs) },
                    onSplit = { onSplit(track.id) }
                )
            }
        }
    }
}

/** Valid, source-relative retained bounds; null means the source is too short to trim safely. */
internal data class AudioSourceTrimBounds(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = endMs - startMs
}

internal fun normalizedAudioSourceTrimBounds(
    sourceDurationMs: Long,
    startMs: Long,
    endMs: Long,
    minimumDurationMs: Long = 100L
): AudioSourceTrimBounds? {
    if (minimumDurationMs <= 0L || sourceDurationMs < minimumDurationMs) return null
    val start = startMs.coerceIn(0L, sourceDurationMs - minimumDurationMs)
    val end = endMs.coerceIn(start + minimumDurationMs, sourceDurationMs)
    return AudioSourceTrimBounds(start, end)
}

/** The playhead is project time; splitting also requires at least 100 ms on each retained side. */
internal fun canSplitAudioTrackAtPlayhead(track: AudioTrack, playheadMs: Long): Boolean {
    val sourceEnd = track.sourceEndMs
    val span = track.trimEndMs - track.trimStartMs
    if (sourceEnd <= 0L || track.trimStartMs < 0L || track.trimEndMs > sourceEnd ||
        track.startTimeMs < 0L || span < 200L || playheadMs < track.startTimeMs
    ) return false
    val elapsed = playheadMs - track.startTimeMs
    return elapsed in 100L..(span - 100L)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioSourceTrimCard(
    track: AudioTrack,
    playheadMs: Long,
    onTrim: (Long, Long) -> Unit,
    onSplit: () -> Unit
) {
    val sourceDuration = track.sourceEndMs
    val currentBounds = normalizedAudioSourceTrimBounds(sourceDuration, track.trimStartMs, track.trimEndMs)
    var range by remember(track.id, sourceDuration, track.trimStartMs, track.trimEndMs) {
        mutableStateOf(
            currentBounds?.let { it.startMs.toFloat()..it.endMs.toFloat() } ?: (0f..0f)
        )
    }
    val accent = ApexPalette.NeonCyan
    Column(
        modifier = Modifier
            .width(292.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ApexPalette.BgBase)
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text("Source trim · ${track.name}", color = ApexPalette.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (currentBounds == null) {
            Text("Source duration: ${sourceDuration.coerceAtLeast(0L)} ms · Too short to trim safely",
                color = ApexPalette.TextSecondary, fontSize = 11.sp)
        } else {
            Text(
                "Source ${range.start.toLong()}–${range.endInclusive.toLong()} / ${sourceDuration} ms · Retained ${range.endInclusive.toLong() - range.start.toLong()} ms" +
                    if (track.sourceDurationMs <= 0L) " (known legacy boundary)" else "",
                color = ApexPalette.TextSecondary,
                fontSize = 9.sp
            )
            val retainedDuration = (range.endInclusive - range.start).toLong().coerceAtLeast(0L)
            val projectEnd = if (track.startTimeMs > Long.MAX_VALUE - retainedDuration) Long.MAX_VALUE
                else track.startTimeMs + retainedDuration
            Text(
                "Project ${track.startTimeMs}–$projectEnd ms · Playhead $playheadMs ms (project)",
                color = ApexPalette.TextSecondary,
                fontSize = 9.sp
            )
            RangeSlider(
                value = range,
                onValueChange = { candidate ->
                    if (candidate.endInclusive - candidate.start >= 100f) range = candidate
                },
                valueRange = 0f..sourceDuration.toFloat(),
                onValueChangeFinished = {
                    normalizedAudioSourceTrimBounds(sourceDuration, range.start.toLong(), range.endInclusive.toLong())
                        ?.let { onTrim(it.startMs, it.endMs) }
                },
                modifier = Modifier.testTag("audio-track-trim-range-${track.id}")
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (track.sourceDurationMs > 0L) "Reset source" else "Reset known bounds",
                    color = accent,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onTrim(0L, sourceDuration) }
                        .testTag("audio-track-trim-reset-${track.id}")
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
                Button(
                    onClick = onSplit,
                    enabled = canSplitAudioTrackAtPlayhead(track, playheadMs),
                    modifier = Modifier.testTag("audio-track-split-${track.id}")
                ) { Text("Split at playhead", fontSize = 11.sp) }
            }
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
    onRemove: (() -> Unit)?,
    showVolume: Boolean = true,
    showSolo: Boolean = true
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
            if (showVolume) {
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
        }
        if (showSolo) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (solo) accent.copy(alpha = 0.2f) else Color.Transparent)
                    .clickable(onClick = onSolo)
                    .testTag("audio-track-solo")
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Solo",
                    color = if (solo) accent else ApexPalette.TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
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
    playheadMs: Long,
    onVolume: (Float) -> Unit,
    onMute: () -> Unit,
    onSolo: () -> Unit,
    onRemove: () -> Unit,
    onFadeIn: (Long) -> Unit,
    onFadeOut: (Long) -> Unit,
    onAddVolumeKeyframe: () -> Unit,
    onRemoveVolumeKeyframe: (String) -> Unit,
    onSeekTo: (Long) -> Unit,
    voiceTrackId: String?,
    onNormalizeTrack: (String) -> Unit,
    onAutoDuckMusic: (String, String) -> Unit,
    onFitMusicToProject: (String) -> Unit,
    onRemoveSilence: (String) -> Unit
) {
    val accent = when {
        track.isSolo -> ApexPalette.NeonCyan
        track.isMuted -> ApexPalette.TextTertiary
        else -> ApexPalette.NeonPurple
    }
    val kindLabel = audioTrackKindLabel(track.kind)
    Column(
        modifier = Modifier
            .width(292.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ApexPalette.BgBase)
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        AudioMixerRow(
            label = "${track.name} • $kindLabel",
            icon = if (track.isMuted) Icons.Default.MusicOff else Icons.Default.MusicNote,
            // Fader shows the effective volume AT THE PLAYHEAD when
            // volume keyframes exist (falls back to the scalar
            // volume); dragging it auto-upserts at the playhead.
            volume = track.volumeAt(playheadMs),
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
        Spacer(Modifier.height(6.dp))
        // Volume keyframes (CapCut-style volume animation): "+ Vol
        // KF" pins the current effective volume at the playhead
        // (first keyframe is manual); once keyframes exist, the
        // fader above auto-upserts at the playhead. Tap a chip to
        // seek to that pin; tap its ✕ to delete the pin.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(ApexPalette.NeonCyan.copy(alpha = 0.15f))
                    .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .clickable { onAddVolumeKeyframe() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "+ Vol KF",
                    color = ApexPalette.NeonCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${track.volumeKeyframes.size} volume keyframe(s)",
                color = ApexPalette.TextSecondary,
                fontSize = 11.sp
            )
        }
        if (track.volumeKeyframes.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                track.volumeKeyframes.forEach { kf ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .clickable { onSeekTo(kf.timeMs) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${formatVolumeKfTime(kf.timeMs)} · ${(kf.volume * 100).toInt()}%",
                            color = ApexPalette.TextPrimary,
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "✕",
                            color = ApexPalette.TextTertiary,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clickable { onRemoveVolumeKeyframe(kf.id) }
                                .padding(2.dp)
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AudioActionButton("Normalize · -16 LUFS-like", "audio-track-normalize-${track.id}") {
                onNormalizeTrack(track.id)
            }
            if (track.kind == AudioTrack.Kind.MUSIC && voiceTrackId != null) {
                AudioActionButton("Auto-duck", "audio-track-duck-${track.id}") {
                    onAutoDuckMusic(track.id, voiceTrackId)
                }
                AudioActionButton("Fit to project", "audio-track-fit-${track.id}") {
                    onFitMusicToProject(track.id)
                }
            }
            AudioActionButton("Remove silence", "audio-track-remove-silence-${track.id}") {
                onRemoveSilence(track.id)
            }
        }
        Text(
            text = "Normalize is a deterministic gated loudness estimate, not a broadcast LUFS meter.",
            color = ApexPalette.TextTertiary,
            fontSize = 9.sp
        )
    }
}

@Composable
private fun AudioActionButton(label: String, tag: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.testTag(tag),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(label, fontSize = 10.sp, maxLines = 1)
    }
}

/** Track identity is persisted in [AudioTrack.kind], not inferred from its current fader value. */
internal fun audioTrackKindLabel(kind: AudioTrack.Kind): String = when (kind) {
    AudioTrack.Kind.ORIGINAL_VIDEO -> "Original video"
    AudioTrack.Kind.MUSIC -> "Music"
    AudioTrack.Kind.SFX -> "Voiceover / SFX"
}

private fun formatVolumeKfTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    return String.format(java.util.Locale.US, "%d:%02d", totalSec / 60, totalSec % 60)
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
private fun AddTrackButton(
    label: String,
    icon: ImageVector,
    accent: Color = ApexPalette.NeonCyan,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = ApexPalette.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
