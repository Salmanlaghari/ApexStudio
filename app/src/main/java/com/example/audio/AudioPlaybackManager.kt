package com.example.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.util.Log
import com.example.model.AudioTrackState
import java.io.File
import java.io.FileOutputStream
import kotlin.math.log10

/**
 * Manages genuine Android MediaPlayer audio playback, per-track volume,
 * real-time fade-in / fade-out curves, hardware muting, trimming, and local file import.
 */
class AudioPlaybackManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var isPrepared = false
    private var pendingPlayFromMs: Long? = null
    private var currentTrackState: AudioTrackState = AudioTrackState()

    fun loadTrack(trackState: AudioTrackState) {
        currentTrackState = trackState
        release()

        val file = trackState.audioFile
        if (file == null || !file.exists()) {
            return
        }

        try {
            isPrepared = false
            pendingPlayFromMs = null
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                try {
                    loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                        enabled = true
                    }
                } catch (e: Exception) {
                    Log.w("AudioPlaybackManager", "LoudnessEnhancer unavailable", e)
                }
                setOnPreparedListener {
                    isPrepared = true
                    applyVolumeAndFade(0L)
                    val pending = pendingPlayFromMs
                    if (pending != null) {
                        pendingPlayFromMs = null
                        play(pending)
                    }
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("AudioPlaybackManager", "Error preparing MediaPlayer for ${file.name}", e)
        }
    }

    fun play(fromMs: Long) {
        val player = mediaPlayer ?: return
        if (!isPrepared) {
            pendingPlayFromMs = fromMs
            return
        }
        try {
            val seekTarget = (fromMs + currentTrackState.trimStartMs).toInt().coerceIn(0, player.duration)
            player.seekTo(seekTarget)
            player.start()
            applyVolumeAndFade(fromMs)
        } catch (e: Exception) {
            Log.e("AudioPlaybackManager", "Error playing audio", e)
        }
    }

    fun pause() {
        pendingPlayFromMs = null
        try {
            if (isPrepared && mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (e: Exception) {
            Log.e("AudioPlaybackManager", "Error pausing audio", e)
        }
    }

    fun seekTo(timelineTimeMs: Long) {
        val player = mediaPlayer ?: return
        if (!isPrepared) return
        try {
            val targetMs = (timelineTimeMs + currentTrackState.trimStartMs).toInt().coerceIn(0, player.duration)
            player.seekTo(targetMs)
            applyVolumeAndFade(timelineTimeMs)
        } catch (e: Exception) {
            Log.e("AudioPlaybackManager", "Error seeking audio", e)
        }
    }

    /**
     * Dynamically calculates and applies volume considering:
     * 1. Hardware mute state
     * 2. Per-track volume slider (0.0 to 2.0)
     * 3. Fade-in attenuation curve
     * 4. Fade-out attenuation curve
     * 5. Digital gain (software amplification via LoudnessEnhancer) for > 1.0 volume.
     */
    fun applyVolumeAndFade(timelineTimeMs: Long) {
        val player = mediaPlayer ?: return
        if (!isPrepared) return

        if (currentTrackState.isMuted) {
            player.setVolume(0f, 0f)
            try {
                loudnessEnhancer?.setTargetGain(0)
            } catch (e: Exception) {
                // Ignore
            }
            return
        }

        var multiplier = 1.0f
        val fadeInMs = (currentTrackState.fadeInSec * 1000f).toLong()
        val fadeOutMs = (currentTrackState.fadeOutSec * 1000f).toLong()
        val effectiveDur = currentTrackState.effectiveDurationMs

        // Fade in region
        if (fadeInMs > 0 && timelineTimeMs < fadeInMs) {
            multiplier *= (timelineTimeMs.toFloat() / fadeInMs).coerceIn(0f, 1f)
        }

        // Fade out region
        if (fadeOutMs > 0 && effectiveDur > fadeOutMs && timelineTimeMs > (effectiveDur - fadeOutMs)) {
            val remaining = (effectiveDur - timelineTimeMs).coerceAtLeast(0L)
            multiplier *= (remaining.toFloat() / fadeOutMs).coerceIn(0f, 1f)
        }

        val totalVol = (currentTrackState.volume * multiplier).coerceIn(0f, 2.0f)
        if (totalVol <= 1.0f) {
            player.setVolume(totalVol, totalVol)
            try {
                loudnessEnhancer?.setTargetGain(0)
            } catch (e: Exception) {
                // Ignore
            }
        } else {
            // MediaPlayer setVolume maxes out at 1.0f. Apply digital gain (mB) for 100%-200% range
            player.setVolume(1.0f, 1.0f)
            try {
                // 20 * log10(gain) dB, converted to millibels (* 100). For 2.0f: ~602 mB
                val gainMb = (2000.0 * log10(totalVol.toDouble())).toInt().coerceIn(0, 1000)
                loudnessEnhancer?.setTargetGain(gainMb)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun updateTrackConfig(newState: AudioTrackState, currentTimelineMs: Long) {
        val fileChanged = newState.audioFile != currentTrackState.audioFile
        currentTrackState = newState
        if (fileChanged) {
            loadTrack(newState)
        } else {
            applyVolumeAndFade(currentTimelineMs)
        }
    }

    fun release() {
        try {
            isPrepared = false
            pendingPlayFromMs = null
            try {
                loudnessEnhancer?.release()
            } catch (e: Exception) {
                // Ignore
            }
            loudnessEnhancer = null
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            // Ignore
        }
    }

    /**
     * Genuine local audio file import handler.
     * Copies content URI stream to internal storage, reads real duration, and generates waveform.
     */
    fun importLocalAudioFile(uri: Uri): AudioTrackState? {
        return try {
            val contentResolver = context.contentResolver
            val importedDir = File(context.filesDir, "imported_audio").apply { mkdirs() }
            val fileName = "imported_${System.currentTimeMillis()}.mp3"
            val targetFile = File(importedDir, fileName)

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            var durationMs = 15_000L
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(targetFile.absolutePath)
                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                if (durStr != null) {
                    durationMs = durStr.toLong()
                }
                retriever.release()
            } catch (e: Exception) {
                // Fallback default duration if retriever fails on certain formats
            }

            val waveforms = AudioSynthesizer.extractWaveformPoints(targetFile, 64)

            AudioTrackState(
                title = "Local: ${targetFile.nameWithoutExtension.take(16)}",
                audioFile = targetFile,
                durationMs = durationMs,
                trimStartMs = 0L,
                trimEndMs = durationMs,
                waveforms = waveforms,
                isRoyaltyFree = false
            )
        } catch (e: Exception) {
            Log.e("AudioPlaybackManager", "Failed to import local audio", e)
            null
        }
    }
}
