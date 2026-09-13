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
            if (player == null && track.uri.isNotEmpty()) {
                try {
                    player = MediaPlayer().apply {
                        setDataSource(context, Uri.parse(track.uri))
                        prepare()
                    }
                    playerMap[track.id] = player
                } catch (e: Exception) {
                    Log.w("AudioPlaybackManager", "Could not load audio track: ${track.name}", e)
                    continue
                }
            }

            player?.let { p ->
                val vol = track.volume.coerceIn(0f, 1f)
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
