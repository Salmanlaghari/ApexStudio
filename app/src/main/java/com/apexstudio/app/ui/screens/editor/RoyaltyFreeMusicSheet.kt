package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.engine.AudioSynthesizer
import com.apexstudio.app.ui.theme.ApexPalette
import java.io.File

data class RoyaltyFreeSong(
    val id: String,
    val title: String,
    val artist: String,
    val genre: String,
    val bpm: Int,
    val durationSeconds: Int,
    val description: String
)

val ROYALTY_FREE_LIBRARY = listOf(
    RoyaltyFreeSong("cyber_pulse", "Cybernetic Pulse", "Apex Soundworks", "Synthwave", 120, 30, "High-energy retro synth arpeggios & deep 808 bass"),
    RoyaltyFreeSong("neon_horizon", "Neon Horizon", "Future Drift", "Cyberpunk", 110, 30, "Atmospheric cyber-noir pads with driving mid-tempo beat"),
    RoyaltyFreeSong("sunset_drive", "Sunset Drive", "LoFi Chill Collective", "Lo-Fi", 85, 30, "Warm mellow chords, gentle vinyl and soothing vibes"),
    RoyaltyFreeSong("ambient_chill", "Ambient Chill", "Zenith", "Ambient", 70, 30, "Deep relaxing harmonic chords for vlog & cinematic visuals"),
    RoyaltyFreeSong("cinematic_drama", "Cinematic Drama", "Orchestral Noir", "Cinematic", 90, 30, "Building orchestral suspense brass & dramatic swells"),
    RoyaltyFreeSong("urban_groove", "Urban Groove", "Metro Beats", "Hip-Hop", 95, 30, "Crisp hi-hats, punchy kick and sliding 808 sub")
)

@Composable
fun RoyaltyFreeMusicSheet(
    onSelectSong: (title: String, filePath: String, durationMs: Long) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var previewingSongId by remember { mutableStateOf<String?>(null) }
    var previewPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                previewPlayer?.stop()
                previewPlayer?.release()
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 540.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(ApexPalette.BgElevated)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = ApexPalette.NeonCyan,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Royalty-Free Music Library",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "100% Free for commercial & personal video projects",
                    color = Color(0xFF9CA3AF),
                    fontSize = 11.sp
                )
            }
            IconButton(onClick = {
                try { previewPlayer?.stop(); previewPlayer?.release() } catch (_: Exception) {}
                onClose()
            }) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ROYALTY_FREE_LIBRARY, key = { it.id }) { song ->
                val isPlayingThis = previewingSongId == song.id

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isPlayingThis) Color(0xFF1F1F35) else Color(0xFF141420))
                        .border(
                            1.dp,
                            if (isPlayingThis) ApexPalette.NeonCyan else Color(0xFF262638),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play/Preview Button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (isPlayingThis) ApexPalette.NeonCyan
                                else Color(0xFF28283E)
                            )
                            .clickable {
                                try {
                                    previewPlayer?.stop()
                                    previewPlayer?.release()
                                    previewPlayer = null
                                } catch (_: Exception) {}

                                if (isPlayingThis) {
                                    previewingSongId = null
                                } else {
                                    val file = File(context.cacheDir, "preview_${song.id}.wav")
                                    if (!file.exists() || file.length() < 1000L) {
                                        AudioSynthesizer.generateRoyaltyFreeTrack(file, song.id, song.durationSeconds)
                                    }
                                    try {
                                        val mp = android.media.MediaPlayer().apply {
                                            setDataSource(file.absolutePath)
                                            prepare()
                                            start()
                                        }
                                        previewPlayer = mp
                                        previewingSongId = song.id
                                        mp.setOnCompletionListener {
                                            previewingSongId = null
                                        }
                                    } catch (e: Exception) {
                                        previewingSongId = null
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlayingThis) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = "Preview",
                            tint = if (isPlayingThis) Color.Black else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = song.title,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF2E2452))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${song.bpm} BPM",
                                    color = Color(0xFFA78BFA),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = "${song.genre} • ${song.durationSeconds}s",
                            color = Color(0xFF9CA3AF),
                            fontSize = 11.sp
                        )
                        Text(
                            text = song.description,
                            color = Color(0xFF6B7280),
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // Add to Timeline Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF00E5FF), Color(0xFF3B82F6))
                                )
                            )
                            .clickable {
                                try {
                                    previewPlayer?.stop()
                                    previewPlayer?.release()
                                } catch (_: Exception) {}

                                val destFile = File(context.cacheDir, "rf_${song.id}_${System.currentTimeMillis()}.wav")
                                AudioSynthesizer.generateRoyaltyFreeTrack(destFile, song.id, song.durationSeconds)
                                onSelectSong(song.title, destFile.absolutePath, song.durationSeconds * 1000L)
                                onClose()
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Use",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
