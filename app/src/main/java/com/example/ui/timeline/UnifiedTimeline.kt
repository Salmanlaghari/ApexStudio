package com.example.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioTrackState
import com.example.model.ChromaKeyState
import com.example.model.EffectType
import com.example.video.VideoPresetClip
import kotlin.math.roundToInt

/**
 * Unified multi-track timeline engine.
 * Solves:
 * 1. Continuous per-frame scrubbing without coarse jumps.
 * 2. Strict synchronization across Video, FX, Text, and Audio tracks in one scroll canvas.
 * 3. Complete removal of any 'Cover' label anywhere on the tracks.
 */
@Composable
fun UnifiedTimeline(
    currentTimeMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
    activeClip: VideoPresetClip,
    chromaKey: ChromaKeyState,
    activeEffect: EffectType,
    textOverlay: String,
    audioState: AudioTrackState,
    onSeekContinuously: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val pxPerSec = with(density) { 70.dp.toPx() }
    val totalWidthDp = with(density) { ((totalDurationMs / 1000f) * pxPerSec).toDp() } + 160.dp

    val scrollState = rememberScrollState()

    // Auto-scroll timeline when playing to follow the playhead smoothly
    LaunchedEffect(currentTimeMs, isPlaying) {
        if (isPlaying) {
            val playheadPx = (currentTimeMs / 1000f) * pxPerSec
            val targetScroll = (playheadPx - 300f).coerceAtLeast(0f).toInt()
            scrollState.scrollTo(targetScroll)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF101218))
            .border(1.dp, Color(0xFF232734), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .testTag("timeline_container")
    ) {
        // Track Header labels bar (Clean icons, strictly NO 'Cover' label!)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF161922))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TrackBadge(icon = Icons.Default.Movie, label = "Video", color = Color(0xFF3B82F6))
                if (chromaKey.enabled) {
                    TrackBadge(icon = Icons.Default.VpnKey, label = "3D Keyer", color = Color(0xFF10B981))
                }
                if (activeEffect != EffectType.NONE) {
                    TrackBadge(icon = Icons.Default.GraphicEq, label = activeEffect.displayName, color = Color(0xFFEC4899))
                }
                TrackBadge(icon = Icons.Default.TextFields, label = "Text", color = Color(0xFFF59E0B))
                TrackBadge(
                    icon = if (audioState.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    label = audioState.title.take(14),
                    color = Color(0xFF8B5CF6)
                )
            }

            // Real-time timecode readout
            Text(
                text = formatTimecode(currentTimeMs),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color(0xFF00F0FF)
            )
        }

        // Horizontal scrolling container containing synchronized tracks + continuous scrub
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(185.dp)
                .pointerInput(totalDurationMs, pxPerSec) {
                    detectTapGestures { offset ->
                        // Continuous direct seek on tap
                        val totalX = offset.x + scrollState.value
                        val newTimeMs = ((totalX / pxPerSec) * 1000L).toLong().coerceIn(0L, totalDurationMs)
                        onSeekContinuously(newTimeMs)
                    }
                }
                .pointerInput(totalDurationMs, pxPerSec) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        // Continuous per-frame scrubbing calculation down to the millisecond
                        val dtMs = ((dragAmount.x / pxPerSec) * 1000f).toLong()
                        val newTimeMs = (currentTimeMs + dtMs).coerceIn(0L, totalDurationMs)
                        onSeekContinuously(newTimeMs)
                    }
                }
        ) {
            // Horizontal Track Stacks
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .width(totalWidthDp)
                    .offset { IntOffset(-scrollState.value, 0) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp, end = 60.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // 1. Time Ruler (continuous seconds and tick markers)
                    TimeRulerBar(durationMs = totalDurationMs, pxPerSec = pxPerSec)

                    // 2. Video Track (filmstrip block)
                    VideoTrackBlock(
                        clip = activeClip,
                        durationMs = totalDurationMs,
                        pxPerSec = pxPerSec
                    )

                    // 3. FX / Shader Track
                    FxTrackBlock(
                        activeEffect = activeEffect,
                        durationMs = totalDurationMs,
                        pxPerSec = pxPerSec
                    )

                    // 4. Text Overlay Track
                    TextTrackBlock(
                        text = textOverlay,
                        durationMs = totalDurationMs,
                        pxPerSec = pxPerSec
                    )

                    // 5. Audio Track (synchronized real waveform & volume)
                    AudioTrackBlock(
                        audioState = audioState,
                        durationMs = totalDurationMs,
                        pxPerSec = pxPerSec
                    )
                }
            }

            // Universal Playhead Cursor across ALL tracks
            val playheadPx = with(density) {
                ((currentTimeMs / 1000f) * pxPerSec) + 16.dp.toPx() - scrollState.value
            }

            PlayheadIndicator(
                playheadPx = playheadPx,
                modifier = Modifier.testTag("timeline_playhead")
            )
        }
    }
}

@Composable
private fun TrackBadge(icon: ImageVector, label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(12.dp))
        Text(label, fontSize = 10.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TimeRulerBar(durationMs: Long, pxPerSec: Float) {
    val totalSec = (durationMs / 1000f).toInt()
    val density = LocalDensity.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .background(Color(0xFF141720))
    ) {
        for (sec in 0..totalSec) {
            val secWidthDp = with(density) { pxPerSec.toDp() }
            Box(
                modifier = Modifier
                    .width(secWidthDp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.BottomStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Major second tick
                    Box(modifier = Modifier.width(1.5.dp).height(12.dp).background(Color(0xFF8A93A6)))
                    // Sub-ticks
                    Box(modifier = Modifier.width(1.dp).height(6.dp).background(Color(0xFF3B4152)))
                    Box(modifier = Modifier.width(1.dp).height(8.dp).background(Color(0xFF4A5266)))
                    Box(modifier = Modifier.width(1.dp).height(6.dp).background(Color(0xFF3B4152)))
                }
                Text(
                    text = "${sec}s",
                    fontSize = 9.sp,
                    color = Color(0xFFA0AEC0),
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun VideoTrackBlock(clip: VideoPresetClip, durationMs: Long, pxPerSec: Float) {
    val density = LocalDensity.current
    val trackWidthDp = with(density) { ((durationMs / 1000f) * pxPerSec).toDp() }

    Box(
        modifier = Modifier
            .width(trackWidthDp)
            .height(42.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF1E3A8A), Color(0xFF2563EB), Color(0xFF1D4ED8))
                )
            )
            .border(1.dp, Color(0xFF60A5FA), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp)
            .testTag("track_video"),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Movie, contentDescription = "Video Track", tint = Color.White, modifier = Modifier.size(16.dp))
                Text(clip.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Text("${durationMs / 1000}s HD", fontSize = 10.sp, color = Color(0xFFBFDBFE))
        }
    }
}

@Composable
private fun FxTrackBlock(activeEffect: EffectType, durationMs: Long, pxPerSec: Float) {
    val density = LocalDensity.current
    val trackWidthDp = with(density) { ((durationMs / 1000f) * pxPerSec).toDp() }
    val isEnabled = activeEffect != EffectType.NONE

    Box(
        modifier = Modifier
            .width(trackWidthDp)
            .height(28.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(
                if (isEnabled) {
                    Brush.horizontalGradient(listOf(Color(0xFF831843), Color(0xFFBE185D)))
                } else {
                    SolidColor(Color(0xFF1A1D26))
                }
            )
            .border(1.dp, if (isEnabled) Color(0xFFF472B6) else Color(0xFF2A2E3B), RoundedCornerShape(5.dp))
            .padding(horizontal = 8.dp)
            .testTag("track_fx"),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.GraphicEq, contentDescription = "FX Track", tint = if (isEnabled) Color.White else Color.Gray, modifier = Modifier.size(14.dp))
            Text(
                text = if (isEnabled) "FX: ${activeEffect.displayName}" else "No FX Active (Tap FX Tool to Add)",
                fontSize = 11.sp,
                fontWeight = if (isEnabled) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isEnabled) Color.White else Color.Gray
            )
        }
    }
}

@Composable
private fun TextTrackBlock(text: String, durationMs: Long, pxPerSec: Float) {
    val density = LocalDensity.current
    val trackWidthDp = with(density) { ((durationMs / 1000f) * pxPerSec).toDp() }
    val hasText = text.isNotBlank()

    Box(
        modifier = Modifier
            .width(trackWidthDp)
            .height(26.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(
                if (hasText) Color(0xFF92400E) else Color(0xFF181B22)
            )
            .border(1.dp, if (hasText) Color(0xFFF59E0B) else Color(0xFF262B38), RoundedCornerShape(5.dp))
            .padding(horizontal = 8.dp)
            .testTag("track_text"),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.TextFields, contentDescription = "Text Track", tint = if (hasText) Color.White else Color.Gray, modifier = Modifier.size(13.dp))
            Text(
                text = if (hasText) "Title: \"$text\"" else "Text Overlay (Tap Text to Add)",
                fontSize = 10.sp,
                color = if (hasText) Color.White else Color.Gray
            )
        }
    }
}

@Composable
private fun AudioTrackBlock(audioState: AudioTrackState, durationMs: Long, pxPerSec: Float) {
    val density = LocalDensity.current
    val trackWidthDp = with(density) { ((durationMs / 1000f) * pxPerSec).toDp() }

    Box(
        modifier = Modifier
            .width(trackWidthDp)
            .height(40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF4C1D95), Color(0xFF6D28D9), Color(0xFF5B21B6))
                )
            )
            .border(1.dp, Color(0xFFA78BFA), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp)
            .testTag("track_audio"),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    imageVector = if (audioState.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = "Audio Track",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "${audioState.title} ${(audioState.volume * 100).toInt()}%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            // Real audio waveform bars visualization
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.width(110.dp).height(24.dp)
            ) {
                val waves = audioState.waveforms.ifEmpty { List(24) { 0.4f } }
                for (peak in waves.take(24)) {
                    val barH = (peak * 22f).coerceIn(3f, 22f)
                    Box(
                        modifier = Modifier
                            .width(2.5.dp)
                            .height(barH.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(if (audioState.isMuted) Color.Gray else Color(0xFFDDD6FE))
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayheadIndicator(playheadPx: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .offset { IntOffset(playheadPx.roundToInt() - 6, 0) }
            .fillMaxHeight()
            .width(14.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        // Red / Cyan glowing cursor line
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(2.dp)
                .background(Color(0xFFFF3366))
        )
        // Playhead top drag notch
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF3366))
                .border(2.dp, Color.White, CircleShape)
        )
    }
}

private fun formatTimecode(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val millis = (timeMs % 1000) / 10
    return String.format("%02d:%02d.%02d", minutes, seconds, millis)
}
