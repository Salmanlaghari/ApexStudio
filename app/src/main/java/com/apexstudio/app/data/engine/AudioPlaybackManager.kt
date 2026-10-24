package com.apexstudio.app.data.engine

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.apexstudio.app.domain.model.AudioTrack
import kotlin.math.abs

/** Converts a project playhead position to the corresponding source-media position. */
internal object AudioPlaybackPosition {
    fun sourcePositionMs(track: AudioTrack, timelinePositionMs: Long): Long? {
        val timelineStartMs = track.startTimeMs.coerceAtLeast(0L)
        val sourceStartMs = track.trimStartMs.coerceAtLeast(0L)
        val sourceEndMs = minOf(track.trimEndMs, track.sourceEndMs).coerceAtLeast(0L)
        // Compare ordered, non-negative endpoints before subtraction to avoid malformed-data overflow.
        if (sourceEndMs <= sourceStartMs || timelinePositionMs < timelineStartMs) return null
        val sourceLengthMs = sourceEndMs - sourceStartMs

        val elapsedMs = timelinePositionMs - timelineStartMs
        if (elapsedMs >= sourceLengthMs) return null
        return sourceStartMs + elapsedMs
    }
}

/** Pure gain calculation for the MediaPlayer preview path. */
internal object AudioPlaybackMix {
    fun isEnabled(track: AudioTrack, tracks: List<AudioTrack>): Boolean =
        !track.isMuted && (tracks.none { it.isSolo } || track.isSolo)

    fun volumeAt(track: AudioTrack, timelinePositionMs: Long, tracks: List<AudioTrack>): Float {
        if (!isEnabled(track, tracks)) return 0f
        if (AudioPlaybackPosition.sourcePositionMs(track, timelinePositionMs) == null) return 0f

        val timelineStartMs = track.startTimeMs.coerceAtLeast(0L)
        val sourceStartMs = track.trimStartMs.coerceAtLeast(0L)
        val sourceEndMs = minOf(track.trimEndMs, track.sourceEndMs).coerceAtLeast(0L)
        val playDurationMs = sourceEndMs - sourceStartMs
        val elapsedMs = timelinePositionMs - timelineStartMs
        val remainingMs = playDurationMs - elapsedMs

        var envelope = 1f
        if (track.fadeInMs > 0L && elapsedMs < track.fadeInMs) {
            envelope = minOf(envelope, elapsedMs.toDouble().div(track.fadeInMs).toFloat())
        }
        if (track.fadeOutMs > 0L && remainingMs < track.fadeOutMs) {
            envelope = minOf(envelope, remainingMs.coerceAtLeast(0L).toDouble().div(track.fadeOutMs).toFloat())
        }

        // Keyframes are absolute project-timeline values; edge fades are local
        // to the placed, trimmed playback window and multiply that volume.
        val keyedVolume = track.volumeAt(timelinePositionMs)
        val cleanVolume = if (keyedVolume.isFinite()) keyedVolume.coerceIn(0f, 1f) else 0f
        return (cleanVolume * envelope).coerceIn(0f, 1f)
    }

    /** Linear left/right gains preserve the center mix level and match export pan behavior. */
    fun stereoGainsAt(track: AudioTrack, timelinePositionMs: Long): Pair<Float, Float> {
        val pan = track.panAt(timelinePositionMs).takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f
        return (1f - pan.coerceAtLeast(0f)) to (1f + pan.coerceAtMost(0f))
    }
}

/**
 * Multi-track synchronized audio playback engine.
 * Keeps external background music and local audio uploads synchronized with video player playhead.
 */
class AudioPlaybackManager(private val context: Context) {

    private val playerMap = mutableMapOf<String, MediaPlayer>()
    private var isPlaying = false
    private var currentPositionMs = 0L
    private var lastTracks: List<AudioTrack> = emptyList()

    fun sync(isPlaying: Boolean, positionMs: Long, tracks: List<AudioTrack>) {
        this.isPlaying = isPlaying
        this.currentPositionMs = positionMs
        lastTracks = tracks

        val activeTrackIds = tracks.map { it.id }.toSet()

        // Clean up removed tracks
        val toRemove = playerMap.keys.filter { it !in activeTrackIds }
        for (id in toRemove) {
            playerMap[id]?.release()
            playerMap.remove(id)
        }

        for (track in tracks) {
            val volume = AudioPlaybackMix.volumeAt(track, positionMs, tracks)
            val enabled = AudioPlaybackMix.isEnabled(track, tracks)
            val sourcePositionMs = AudioPlaybackPosition.sourcePositionMs(track, positionMs)
            var player = playerMap[track.id]
            if (player == null) {
                // Do not prepare tracks suppressed by mute/solo. Other tracks
                // stay ready across zero-gain/off-range spans so sync can start
                // them at the correct source sample on their first active tick.
                if (!enabled) continue
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
                // Sync runs at ~250ms cadence, so preview envelope changes
                // follow that cadence; export remains sample-accurate.
                val (panLeft, panRight) = AudioPlaybackMix.stereoGainsAt(track, positionMs)
                p.setVolume(volume * panLeft, volume * panRight)

                if (sourcePositionMs != null && enabled) {
                    if (abs(p.currentPosition - sourcePositionMs) > 80) {
                        p.seekTo(sourcePositionMs, MediaPlayer.SEEK_CLOSEST)
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
        val tracksById = lastTracks.associateBy { it.id }
        for ((id, player) in playerMap) {
            val track = tracksById[id] ?: continue
            val volume = AudioPlaybackMix.volumeAt(track, positionMs, lastTracks)
            val enabled = AudioPlaybackMix.isEnabled(track, lastTracks)
            val (panLeft, panRight) = AudioPlaybackMix.stereoGainsAt(track, positionMs)
            player.setVolume(volume * panLeft, volume * panRight)
            val sourcePositionMs = AudioPlaybackPosition.sourcePositionMs(track, positionMs)
            if (sourcePositionMs == null || !enabled) {
                if (player.isPlaying) player.pause()
            } else {
                player.seekTo(sourcePositionMs, MediaPlayer.SEEK_CLOSEST)
            }
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
        lastTracks = emptyList()
    }
}
