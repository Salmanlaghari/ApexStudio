package com.apexstudio.app.ui.screens.editor

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.SystemClock
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    val lifecycleOwner = LocalLifecycleOwner.current
    var isRecording by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var recordingTimeMs by remember { mutableLongStateOf(0L) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val clock = remember { RecordingClock() }
    val cleanupHolder = remember { RecordingCleanupHolder() }
    val lifecycleGuard = remember { RecordingLifecycleGuard() }
    var hasRecordPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val amplitudeHistory = remember { mutableStateListOf<Float>() }

    val startRecording: () -> Unit = start@{
        if (isRecording || recorder != null) return@start
        errorMessage = null
        var createdRecorder: MediaRecorder? = null
        var createdFile: File? = null
        try {
            val dir = File(context.filesDir, "VoiceRecordings")
            check(dir.exists() || dir.mkdirs()) { "Could not create the recordings folder" }
            val file = File(dir, "VoiceOver_${System.currentTimeMillis()}.m4a")
            createdFile = file

            val mr = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            createdRecorder = mr
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioSamplingRate(44100)
            mr.setAudioEncodingBitRate(128000)
            mr.setOutputFile(file.absolutePath)
            mr.prepare()
            mr.start()

            recorder = mr
            outputFile = file
            cleanupHolder.recorder = mr
            cleanupHolder.file = file
            cleanupHolder.keepFile = false
            clock.start(SystemClock.elapsedRealtime())
            recordingTimeMs = 0L
            amplitudeHistory.clear()
            isPaused = false
            isRecording = true
        } catch (e: Exception) {
            Log.e("VoiceRecorderPanel", "Failed to start recording", e)
            try { createdRecorder?.release() } catch (_: Exception) {}
            createdFile?.delete()
            cleanupHolder.recorder = null
            cleanupHolder.file = null
            recorder = null
            outputFile = null
            isRecording = false
            isPaused = false
            errorMessage = "Could not start recording. Check microphone access and try again."
        }
    }

    val latestStartRecording = rememberUpdatedState(startRecording)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasRecordPermission = granted
        if (granted && lifecycleGuard.canStart(lifecycleOwner.lifecycle.currentState)) latestStartRecording.value()
        else if (granted) errorMessage = "Microphone allowed. Return to the editor and tap mic to start."
        else errorMessage = "Microphone permission was not granted."
    }

    fun requestOrStartRecording() {
        if (!lifecycleGuard.canStart(lifecycleOwner.lifecycle.currentState)) return
        hasRecordPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasRecordPermission) startRecording()
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun cancelRecording() {
        val activeRecorder = recorder
        recorder = null
        cleanupHolder.recorder = null
        try {
            // Releasing without stop intentionally abandons an unfinished file.
            activeRecorder?.release()
        } catch (e: Exception) {
            Log.w("VoiceRecorderPanel", "Recorder release during cancel failed", e)
        }
        outputFile?.delete()
        cleanupHolder.file?.delete()
        cleanupHolder.file = null
        cleanupHolder.keepFile = false
        outputFile = null
        clock.reset()
        recordingTimeMs = 0L
        isRecording = false
        isPaused = false
        onClose()
    }

    fun stopAndSave() {
        val activeRecorder = recorder ?: return
        val durationAtStop = clock.elapsedMs(SystemClock.elapsedRealtime())
        var stopSucceeded = false
        try {
            activeRecorder.stop()
            stopSucceeded = true
        } catch (e: RuntimeException) {
            // MediaRecorder.stop() throws for very short/empty recordings. Such
            // output is not a valid audio asset and must never reach the editor.
            Log.w("VoiceRecorderPanel", "Recording could not be finalized", e)
        } finally {
            try { activeRecorder.release() } catch (e: Exception) {
                Log.w("VoiceRecorderPanel", "Recorder release failed", e)
            }
            recorder = null
            cleanupHolder.recorder = null
        }

        clock.stop(SystemClock.elapsedRealtime())
        recordingTimeMs = durationAtStop
        isRecording = false
        isPaused = false

        val file = outputFile
        val keep = VoiceRecordingSavePolicy.shouldKeep(
            stopSucceeded = stopSucceeded,
            durationMs = durationAtStop,
            fileBytes = file?.takeIf { it.exists() }?.length() ?: 0L
        )
        if (keep && file != null) {
            try {
                onAddRecording(file.absolutePath, durationAtStop, file.name)
            } catch (e: Exception) {
                Log.e("VoiceRecorderPanel", "Could not add the recording to the project", e)
                file.delete()
                cleanupHolder.file = null
                outputFile = null
                errorMessage = "Could not add this recording to the project. Please try again."
                return
            }
            cleanupHolder.keepFile = true
            onClose()
        } else {
            file?.delete()
            cleanupHolder.file?.delete()
            cleanupHolder.file = null
            outputFile = null
            errorMessage = if (stopSucceeded) {
                "Recording was too short to save. Try recording for a little longer."
            } else {
                "Recording could not be saved. Please try again."
            }
        }
    }

    fun pauseRecording() {
        try {
            val activeRecorder = recorder ?: return
            activeRecorder.pause()
            clock.pause(SystemClock.elapsedRealtime())
            isPaused = true
        } catch (e: Exception) {
            Log.e("VoiceRecorderPanel", "Pause failed", e)
            errorMessage = "Could not pause recording."
        }
    }

    fun resumeRecording() {
        try {
            if (!lifecycleGuard.canStart(lifecycleOwner.lifecycle.currentState)) return
            val activeRecorder = recorder ?: return
            activeRecorder.resume()
            clock.resume(SystemClock.elapsedRealtime())
            isPaused = false
            errorMessage = null
        } catch (e: Exception) {
            Log.e("VoiceRecorderPanel", "Resume failed", e)
            errorMessage = "Could not resume recording."
        }
    }

    // Leaving the foreground must never leave an invisible microphone capture running.
    // Resume is always explicit; returning from another app does not restart the microphone.
    val pauseForBackground = rememberUpdatedState<() -> Unit> {
        if (isRecording && !isPaused) {
            pauseRecording()
            if (!isPaused) {
                // A recorder that cannot pause is abandoned rather than allowed to capture.
                try { recorder?.release() } catch (_: Exception) {}
                recorder = null
                cleanupHolder.recorder = null
                outputFile?.delete()
                outputFile = null
                cleanupHolder.file = null
                clock.reset()
                recordingTimeMs = 0L
                isRecording = false
                errorMessage = "Recording stopped because the app left the foreground. Please try again."
            } else {
                recordingTimeMs = clock.elapsedMs(SystemClock.elapsedRealtime())
                errorMessage = "Recording paused when you left the editor. Tap resume to continue."
            }
        }
    }
    DisposableEffect(lifecycleOwner) {
        lifecycleGuard.attach()
        val observer = LifecycleEventObserver { _, event ->
            lifecycleGuard.onEvent(event) { pauseForBackground.value() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleGuard.dispose()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Any dismissal / configuration disposal abandons an unfinished file.
    DisposableEffect(Unit) {
        onDispose {
            try {
                cleanupHolder.recorder?.release()
            } catch (e: Exception) {
                Log.w("VoiceRecorderPanel", "Recorder cleanup failed", e)
            } finally {
                cleanupHolder.recorder = null
                if (!cleanupHolder.keepFile) cleanupHolder.file?.delete()
                cleanupHolder.file = null
            }
        }
    }

    // The clock uses elapsedRealtime(), excluding paused intervals and wall-clock changes.
    LaunchedEffect(isRecording, isPaused) {
        if (isRecording && !isPaused) {
            while (isActive && isRecording && !isPaused) {
                recordingTimeMs = clock.elapsedMs(SystemClock.elapsedRealtime())
                val maxAmp = try { recorder?.maxAmplitude ?: 0 } catch (_: Exception) { 0 }
                val normAmp = (maxAmp / 32767f).coerceIn(0.05f, 1f)
                amplitudeHistory.add(normAmp)
                if (amplitudeHistory.size > 50) amplitudeHistory.removeAt(0)
                delay(80)
            }
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
                    .clickable { cancelRecording() },
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
                        .clickable { requestOrStartRecording() },
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
            errorMessage ?: if (!hasRecordPermission) "Microphone permission required to record"
            else if (!isRecording) "Tap mic to start voice recording"
            else if (isPaused) "Recording paused"
            else "Recording voice over...",
            color = if (errorMessage != null) ApexPalette.NeonPink else ApexPalette.TextSecondary,
            fontSize = 11.sp
        )
    }
}

/** Monotonic elapsed recording time, with paused intervals excluded. */
internal class RecordingClock {
    private var accumulatedMs = 0L
    private var segmentStartedAtMs: Long? = null

    fun start(nowMs: Long) {
        accumulatedMs = 0L
        segmentStartedAtMs = nowMs
    }

    fun pause(nowMs: Long) {
        val startedAt = segmentStartedAtMs ?: return
        accumulatedMs += (nowMs - startedAt).coerceAtLeast(0L)
        segmentStartedAtMs = null
    }

    fun resume(nowMs: Long) {
        if (segmentStartedAtMs == null) segmentStartedAtMs = nowMs
    }

    fun elapsedMs(nowMs: Long): Long = accumulatedMs +
        (segmentStartedAtMs?.let { (nowMs - it).coerceAtLeast(0L) } ?: 0L)

    fun stop(nowMs: Long): Long {
        val elapsed = elapsedMs(nowMs)
        accumulatedMs = elapsed
        segmentStartedAtMs = null
        return elapsed
    }

    fun reset() {
        accumulatedMs = 0L
        segmentStartedAtMs = null
    }
}

/** Only finalized, non-empty recordings of usable duration may be added. */
internal object VoiceRecordingSavePolicy {
    const val MIN_SAVE_DURATION_MS = 1_000L

    fun shouldKeep(stopSucceeded: Boolean, durationMs: Long, fileBytes: Long): Boolean =
        stopSucceeded && durationMs >= MIN_SAVE_DURATION_MS && fileBytes > 0L
}

/** Also guards delayed permission callbacks after a panel has been dismissed. */
internal class RecordingLifecycleGuard {
    private var attached = false
    fun attach() { attached = true }
    fun dispose() { attached = false }
    fun canStart(state: Lifecycle.State): Boolean = attached && state.isAtLeast(Lifecycle.State.RESUMED)
    fun onEvent(event: Lifecycle.Event, pause: () -> Unit) {
        if (attached && event in setOf(Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY)) pause()
    }
}

private class RecordingCleanupHolder {
    var recorder: MediaRecorder? = null
    var file: File? = null
    var keepFile: Boolean = false
}
