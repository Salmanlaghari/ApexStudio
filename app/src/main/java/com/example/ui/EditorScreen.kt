package com.example.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.EditorTab
import com.example.model.EffectType
import com.example.ui.panels.AdjustPanel
import com.example.ui.panels.AudioPanel
import com.example.ui.panels.ChromaKeyPanel
import com.example.ui.panels.EffectsPanel
import com.example.ui.timeline.UnifiedTimeline
import com.example.video.VideoPresetClip

@Composable
fun EditorScreen(
    viewModel: EditorViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val currentTimeMs by viewModel.currentTimeMs.collectAsState()
    val totalDurationMs by viewModel.totalDurationMs.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val activeClip by viewModel.activeClip.collectAsState()
    val chromaKey by viewModel.chromaKeyState.collectAsState()
    val activeEffect by viewModel.activeEffect.collectAsState()
    val effectIntensity by viewModel.effectIntensity.collectAsState()
    val adjustments by viewModel.adjustments.collectAsState()
    val isCompareMode by viewModel.isCompareMode.collectAsState()
    val textOverlay by viewModel.textOverlay.collectAsState()
    val audioState by viewModel.audioState.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()
    val previewingTrackId by viewModel.previewingTrackId.collectAsState()
    val renderedFrame by viewModel.renderedFrame.collectAsState()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090A0F)),
        containerColor = Color(0xFF090A0F)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 1. Top Header Bar
            EditorHeaderBar(
                activeClip = activeClip,
                onSelectClip = { viewModel.setClip(it) },
                onExport = {
                    Toast.makeText(context, "Exporting 1080p Master Video...", Toast.LENGTH_SHORT).show()
                }
            )

            // 2. Video Preview Monitor
            VideoPreviewMonitor(
                renderedFrame = renderedFrame,
                activeEffect = activeEffect,
                isChromaEnabled = chromaKey.enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )

            // 3. Transport Controls Bar (Play/Pause, Seek, Timecode)
            PlaybackTransportBar(
                isPlaying = isPlaying,
                currentTimeMs = currentTimeMs,
                totalDurationMs = totalDurationMs,
                onTogglePlay = { viewModel.togglePlayPause() },
                onSeekToStart = { viewModel.seekContinuously(0L) },
                onStepForward = { viewModel.seekContinuously(currentTimeMs + 1000L) },
                onStepBackward = { viewModel.seekContinuously(currentTimeMs - 1000L) }
            )

            // 4. Unified Synchronized Multi-Track Timeline (Continuous scrub, in-sync audio & video, NO Cover label)
            UnifiedTimeline(
                currentTimeMs = currentTimeMs,
                totalDurationMs = totalDurationMs,
                isPlaying = isPlaying,
                activeClip = activeClip,
                chromaKey = chromaKey,
                activeEffect = activeEffect,
                textOverlay = textOverlay,
                audioState = audioState,
                onSeekContinuously = { viewModel.seekContinuously(it) },
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // 5. Tool Selection Tabs
            EditorToolTabs(
                selectedTab = activeTab,
                onTabSelect = { viewModel.setTab(it) }
            )

            // 6. Active Tool Panels
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF10131B))
            ) {
                when (activeTab) {
                    EditorTab.CHROMA_KEY -> {
                        ChromaKeyPanel(
                            chromaKeyState = chromaKey,
                            onChromaKeyChange = { viewModel.setChromaKeyState(it) }
                        )
                    }
                    EditorTab.EFFECTS -> {
                        EffectsPanel(
                            activeEffect = activeEffect,
                            effectIntensity = effectIntensity,
                            onEffectSelect = { viewModel.setActiveEffect(it) },
                            onIntensityChange = { viewModel.setEffectIntensity(it) }
                        )
                    }
                    EditorTab.ADJUST -> {
                        AdjustPanel(
                            adjustments = adjustments,
                            onAdjustmentsChange = { viewModel.setAdjustments(it) },
                            isCompareMode = isCompareMode,
                            onToggleCompare = { viewModel.toggleCompareMode() }
                        )
                    }
                    EditorTab.AUDIO -> {
                        AudioPanel(
                            audioState = audioState,
                            onAudioStateChange = { viewModel.setAudioState(it) },
                            onSelectRoyaltyFree = { viewModel.loadRoyaltyFreeTrack(it) },
                            onImportLocalAudio = { viewModel.importLocalAudio(it) },
                            onPreviewTrack = { viewModel.previewRoyaltyFreeTrack(it) },
                            previewingTrackId = previewingTrackId
                        )
                    }
                    EditorTab.TIMELINE -> {
                        // Quick overview panel
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Timeline Inspector", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                            Text("Tracks are locked in sync with audio frequency. Drag playhead for smooth sub-frame scrubbing.", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorHeaderBar(
    activeClip: VideoPresetClip,
    onSelectClip: (VideoPresetClip) -> Unit,
    onExport: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF00F0FF), Color(0xFFFF007F)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Movie, contentDescription = "Logo", tint = Color.Black, modifier = Modifier.size(18.dp))
            }
            Text(
                text = "ApexStudio",
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
        }

        // Clip Selector Chips
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            VideoPresetClip.values().forEach { clip ->
                val isSelected = activeClip == clip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSelectClip(clip) }
                        .background(if (isSelected) Color(0xFF1E3A8A) else Color(0xFF161922))
                        .border(1.dp, if (isSelected) Color(0xFF3B82F6) else Color(0xFF262C3A), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (clip == VideoPresetClip.GREEN_SCREEN_DANCER) "Green Screen" else clip.title.take(8),
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color(0xFF93C5FD) else Color(0xFF94A3B8)
                    )
                }
            }
        }

        // Export Action Button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onExport() }
                .background(Color(0xFF00F0FF))
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("export_button")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Download, contentDescription = "Export Video", tint = Color.Black, modifier = Modifier.size(14.dp))
                Text("Export", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
    }
}

@Composable
private fun VideoPreviewMonitor(
    renderedFrame: Bitmap?,
    activeEffect: EffectType,
    isChromaEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black)
            .border(1.5.dp, Color(0xFF242A38), RoundedCornerShape(10.dp))
            .testTag("preview_monitor"),
        contentAlignment = Alignment.Center
    ) {
        if (renderedFrame != null) {
            Image(
                bitmap = renderedFrame.asImageBitmap(),
                contentDescription = "Live Video Frame Preview",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        // Status Badges
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("1080p 60FPS", fontSize = 9.sp, color = Color(0xFF00F0FF), fontWeight = FontWeight.Bold)
            }

            if (isChromaEnabled) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF10B981).copy(alpha = 0.8f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("3D Keyer Active", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            if (activeEffect != EffectType.NONE) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFE11D48).copy(alpha = 0.85f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("FX: ${activeEffect.displayName}", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PlaybackTransportBar(
    isPlaying: Boolean,
    currentTimeMs: Long,
    totalDurationMs: Long,
    onTogglePlay: () -> Unit,
    onSeekToStart: () -> Unit,
    onStepForward: () -> Unit,
    onStepBackward: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Timecode Readout
        Text(
            text = "${formatTime(currentTimeMs)} / ${formatTime(totalDurationMs)}",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFCBD5E1)
        )

        // Transport Controls (min 48dp touch targets)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onSeekToStart,
                modifier = Modifier.size(42.dp).testTag("seek_start_button")
            ) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Rewind to Start", tint = Color.White)
            }

            IconButton(
                onClick = onStepBackward,
                modifier = Modifier.size(42.dp).testTag("step_back_button")
            ) {
                Icon(Icons.Default.FastRewind, contentDescription = "Step -1s", tint = Color.White)
            }

            // Central Play/Pause button
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00F0FF))
                    .clickable { onTogglePlay() }
                    .testTag("play_pause_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(26.dp)
                )
            }

            IconButton(
                onClick = onStepForward,
                modifier = Modifier.size(42.dp).testTag("step_forward_button")
            ) {
                Icon(Icons.Default.FastForward, contentDescription = "Step +1s", tint = Color.White)
            }
        }
    }
}

@Composable
private fun EditorToolTabs(
    selectedTab: EditorTab,
    onTabSelect: (EditorTab) -> Unit
) {
    val tabs = listOf(
        Pair(EditorTab.CHROMA_KEY, Icons.Default.VpnKey),
        Pair(EditorTab.EFFECTS, Icons.Default.GraphicEq),
        Pair(EditorTab.ADJUST, Icons.Default.Palette),
        Pair(EditorTab.AUDIO, Icons.Default.Audiotrack)
    )

    TabRow(
        selectedTabIndex = tabs.indexOfFirst { it.first == selectedTab }.coerceAtLeast(0),
        containerColor = Color(0xFF141722),
        contentColor = Color(0xFF00F0FF),
        indicator = { tabPositions ->
            val idx = tabs.indexOfFirst { it.first == selectedTab }.coerceAtLeast(0)
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(tabPositions[idx]),
                color = Color(0xFF00F0FF),
                height = 3.dp
            )
        },
        modifier = Modifier.height(44.dp)
    ) {
        tabs.forEach { (tab, icon) ->
            val isSelected = selectedTab == tab
            Tab(
                selected = isSelected,
                onClick = { onTabSelect(tab) },
                modifier = Modifier.testTag("tab_${tab.name.lowercase()}"),
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(icon, contentDescription = tab.title, modifier = Modifier.size(15.dp), tint = if (isSelected) Color(0xFF00F0FF) else Color.Gray)
                        Text(
                            text = tab.title,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else Color.Gray
                        )
                    }
                }
            )
        }
    }
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
