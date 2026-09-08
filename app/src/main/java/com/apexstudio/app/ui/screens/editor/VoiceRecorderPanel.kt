package com.apexstudio.app.ui.screens.editor

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.apexstudio.app.ui.theme.ApexPalette
import com.apexstudio.app.util.TimeFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File

@Composable
fun VoiceRecorderPanel(
    onAddRecording: (uri: String, durationMs: Long, name: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var recordingTimeMs by remember { mutableLongStateOf(0L) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    var hasRecordPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasRecordPermission = granted
    }

    val amplitudeHistory = remember { mutableStateListOf<Float>() }

    // Clean up recorder on dispose
    DisposableEffect(Unit) {
        onDispose {
            try {
                recorder?.stop()
                recorder?.release()
            } catch (_: Exception) {}
        }
    }

    // Timer & amplitude polling loop
    LaunchedEffect(isRecording, isPaused) {
        if (isRecording && !isPaused) {
            val start = System.currentTimeMillis() - recordingTimeMs
            while (isActive && isRecording && !isPaused) {
                recordingTimeMs = System.currentTimeMillis() - start
                val maxAmp = try { recorder?.maxAmplitude ?: 0 } catch (_: Exception) { 0 }
                val normAmp = (maxAmp / 32767f).coerceIn(0.05f, 1f)
                amplitudeHistory.add(normAmp)
                if (amplitudeHistory.size > 50) amplitudeHistory.removeAt(0)
                delay(80)
            }
        }
    }

    fun startRecording() {
        if (!hasRecordPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        try {
            val dir = File(context.getExternalFilesDir(null), "VoiceRecordings")
            dir.mkdirs()
            val file = File(dir, "VoiceOver_${System.currentTimeMillis()}.m4a")
            outputFile = file

            val mr = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioSamplingRate(44100)
            mr.setAudioEncodingBitRate(128000)
            mr.setOutputFile(file.absolutePath)
            mr.prepare()
            mr.start()
            recorder = mr
            isRecording = true
            isPaused = false
        } catch (e: Exception) {
            Log.e("VoiceRecorderPanel", "Failed to start recording", e)
            isRecording = false
        }
    }

    fun pauseRecording() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                recorder?.pause()
            }
            isPaused = true
        } catch (e: Exception) {
            Log.e("VoiceRecorderPanel", "Pause failed", e)
        }
    }

    fun resumeRecording() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                recorder?.resume()
            }
            isPaused = false
        } catch (e: Exception) {
            Log.e("VoiceRecorderPanel", "Resume failed", e)
        }
    }

    fun stopAndSave() {
        try {
            recorder?.stop()
            recorder?.release()
            recorder = null
            isRecording = false
            outputFile?.let { f ->
                if (f.exists() && f.length() > 0) {
                    onAddRecording(f.absolutePath, recordingTimeMs, f.name)
                }
            }
            onClose()
        } catch (e: Exception) {
            Log.e("VoiceRecorderPanel", "Stop failed", e)
            onClose()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(ApexPalette.BgSurface)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Voice Over Studio",
                color = ApexPalette.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(ApexPalette.BgElevated)
                    .clickable {
                        recorder?.release()
                        onClose()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = ApexPalette.TextSecondary, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(Modifier.height(14.dp))

        // Timer
        Text(
            TimeFormat.msToTimecode(recordingTimeMs, includeFrames = true),
            color = if (isRecording && !isPaused) ApexPalette.NeonPink else ApexPalette.NeonCyan,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black
        )

        Spacer(Modifier.height(10.dp))

        // Dynamic Waveform Meter
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ApexPalette.BgBase)
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(10.dp))
                .padding(6.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val samples = amplitudeHistory.toList()
                if (samples.isNotEmpty()) {
                    val step = size.width / 50f
                    val mid = size.height / 2f
                    for (i in samples.indices) {
                        val h = samples[i] * size.height * 0.9f
                        val x = i * step
                        drawLine(
                            color = ApexPalette.NeonPink,
                            start = Offset(x, mid - h / 2f),
                            end = Offset(x, mid + h / 2f),
                            strokeWidth = step * 0.6f
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isRecording) {
                // Record Start Button
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(ApexPalette.NeonPink, Color(0xFF8B0000))
                            )
                        )
                        .clickable { startRecording() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Start Recording", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            } else {
                // Pause / Resume
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(ApexPalette.BgElevated)
                        .border(1.dp, ApexPalette.NeonCyan, CircleShape)
                        .clickable {
                            if (isPaused) resumeRecording() else pauseRecording()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (isPaused) "Resume" else "Pause",
                        tint = ApexPalette.NeonCyan,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Stop & Save
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(ApexPalette.NeonEmerald)
                        .clickable { stopAndSave() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Save Recording", tint = ApexPalette.BgDeep, modifier = Modifier.size(32.dp))
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            if (!hasRecordPermission) "Microphone permission required to record"
            else if (!isRecording) "Tap mic to start voice recording"
            else if (isPaused) "Recording paused"
            else "Recording voice over...",
            color = ApexPalette.TextSecondary,
            fontSize = 11.sp
        )
    }
}
