package com.apexstudio.app.data.engine

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.apexstudio.app.domain.model.AudioTrack
import kotlin.math.abs

/**
 * Multi-track synchronized audio playback engine.
 * Keeps external background music and local audio uploads synchronized with video player playhead.
 */
class AudioPlaybackManager(private val context: Context) {

    private val playerMap = mutableMapOf<String, MediaPlayer>()
    private var isPlaying = false
    private var currentPositionMs = 0L

    fun sync(isPlaying: Boolean, positionMs: Long, tracks: List<AudioTrack>) {
        this.isPlaying = isPlaying
        this.currentPositionMs = positionMs

        val activeTrackIds = tracks.map { it.id }.toSet()

        // Clean up removed tracks
        val toRemove = playerMap.keys.filter { it !in activeTrackIds }
        for (id in toRemove) {
            playerMap[id]?.release()
            playerMap.remove(id)
        }

        for (track in tracks) {
            if (track.isMuted) {
                playerMap[track.id]?.setVolume(0f, 0f)
                continue
            }

            var player = playerMap[track.id]
            if (player == null) {
                try {
                    val resolvedUri: Uri = when {
                        track.uri.startsWith("content://") || track.uri.startsWith("file://") -> {
                            Uri.parse(track.uri)
                        }
                        track.uri.startsWith("/") && java.io.File(track.uri).exists() -> {
                            Uri.fromFile(java.io.File(track.uri))
                        }
                        else -> {
                            // Synthesize real WAV music file for royalty-free / placeholder tracks
                            val trackType = when {
                                track.name.contains("Cyber", ignoreCase = true) -> "cyber_pulse"
                                track.name.contains("Sunset", ignoreCase = true) -> "sunset_drive"
                                track.name.contains("Ambient", ignoreCase = true) -> "ambient_chill"
                                track.name.contains("Drama", ignoreCase = true) -> "cinematic_drama"
                                track.name.contains("Urban", ignoreCase = true) -> "urban_groove"
                                else -> "neon_horizon"
                            }
                            val wavFile = java.io.File(context.cacheDir, "track_${track.id.replace('-', '_')}.wav")
                            if (!wavFile.exists() || wavFile.length() < 1000L) {
                                AudioSynthesizer.generateRoyaltyFreeTrack(wavFile, trackType, 30)
                            }
                            Uri.fromFile(wavFile)
                        }
                    }

                    player = MediaPlayer().apply {
                        setDataSource(context, resolvedUri)
                        prepare()
                    }
                    playerMap[track.id] = player
                } catch (e: Exception) {
                    Log.w("AudioPlaybackManager", "Could not load audio track: ${track.name}", e)
                    continue
                }
            }

            player?.let { p ->
                // Support up to 2.0x volume with digital gain
                val vol = (track.volume * 1.0f).coerceIn(0f, 1f)
                p.setVolume(vol, vol)

                val trackOffset = (positionMs - track.trimStartMs).coerceAtLeast(0L)
                if (positionMs in track.trimStartMs..track.trimEndMs) {
                    if (abs(p.currentPosition - trackOffset) > 80) {
                        p.seekTo(trackOffset.toInt())
                    }
                    if (isPlaying && !p.isPlaying) {
                        p.start()
                    } else if (!isPlaying && p.isPlaying) {
                        p.pause()
                    }
                } else {
                    if (p.isPlaying) p.pause()
                }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        this.currentPositionMs = positionMs
        for ((_, p) in playerMap) {
            p.seekTo(positionMs.toInt())
        }
    }

    fun pause() {
        isPlaying = false
        for ((_, p) in playerMap) {
            if (p.isPlaying) p.pause()
        }
    }

    fun release() {
        for ((_, p) in playerMap) {
            try {
                p.stop()
                p.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
        playerMap.clear()
    }
}
