package com.apexstudio.app.data.audio

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

data class RoyaltyTrack(
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val genre: String,
    val bpm: Int,
    val baseFreq: Double
)

object FreeRoyaltyMusic {

    val CATALOG = listOf(
        RoyaltyTrack("track_cinematic", "Cinematic Horizon", "Apex Studio", 16000L, "Cinematic", 110, 220.0),
        RoyaltyTrack("track_cyberpunk", "Cyberpunk Pulse", "Apex Studio", 16000L, "Synthwave", 128, 146.8),
        RoyaltyTrack("track_lofi", "Lo-Fi Midnight Study", "Apex Studio", 16000L, "Lo-Fi Chill", 85, 174.6),
        RoyaltyTrack("track_acoustic", "Acoustic Summer Breeze", "Apex Studio", 16000L, "Acoustic", 100, 261.6),
        RoyaltyTrack("track_phonk", "Phonk Drift Tokyo", "Apex Studio", 16000L, "Phonk", 140, 110.0),
        RoyaltyTrack("track_ambient", "Ambient Astral Glow", "Apex Studio", 18000L, "Ambient", 75, 196.0),
        RoyaltyTrack("track_tropical", "Tropical Sunset Vibes", "Apex Studio", 16000L, "EDM", 124, 293.6),
        RoyaltyTrack("track_corporate", "Epic Uplift Energy", "Apex Studio", 16000L, "Pop", 120, 329.6),
        RoyaltyTrack("track_rock", "Neon Rock Anthem", "Apex Studio", 16000L, "Rock", 135, 164.8),
        RoyaltyTrack("track_meditation", "Zen Deep Resonance", "Apex Studio", 20000L, "Meditation", 60, 130.8),
        RoyaltyTrack("track_lofi_vintage", "Vintage Cassette Lounge", "Apex Studio", 16000L, "Lo-Fi Vintage", 90, 185.0),
        RoyaltyTrack("track_anime_sparkle", "Shibuya Kawaii Bloom", "Apex Studio", 16000L, "J-Pop", 132, 293.6),
        RoyaltyTrack("track_drill_trap", "Dark Phantom Drill", "Apex Studio", 16000L, "UK Drill", 142, 116.5),
        RoyaltyTrack("track_retro_80s", "Miami Synth Highway 84", "Apex Studio", 16000L, "Retrowave", 115, 220.0),
        RoyaltyTrack("track_pop_energy", "Solar Flare Electro Pop", "Apex Studio", 16000L, "Electropop", 126, 277.2),
        RoyaltyTrack("track_rush_electro", "Hyper Velocity Overdrive", "Apex Studio", 16000L, "Electro Rush", 138, 155.5),
        RoyaltyTrack("track_slowmo_epic", "Timeless Gravity Swell", "Apex Studio", 20000L, "Orchestral Slow", 65, 130.8),
        RoyaltyTrack("track_acoustic_warm", "Sunset Campfire Ballad", "Apex Studio", 16000L, "Folk Acoustic", 95, 246.9),
        RoyaltyTrack("track_synthwave_sunset", "Neon Boulevard Sunset", "Apex Studio", 16000L, "Synthwave", 125, 164.8),
        RoyaltyTrack("track_orchestral_epic", "Triumph of Titans", "Apex Studio", 18000L, "Trailer Cinematic", 108, 196.0),
        RoyaltyTrack("track_matrix_cyber", "Cybernetic Neural Net", "Apex Studio", 16000L, "Cyber Tech", 134, 138.6),
        RoyaltyTrack("track_lofi_rain", "Raindrops on Tokyo Glass", "Apex Studio", 18000L, "Lo-Fi Rain", 80, 164.8),
        RoyaltyTrack("track_urban_hiphop", "Brooklyn Drip 808", "Apex Studio", 16000L, "Boom Bap", 98, 123.4),
        RoyaltyTrack("track_chillhop_cozy", "Cozy Sunday Morning", "Apex Studio", 16000L, "Chillhop", 82, 196.0),
        RoyaltyTrack("track_action_rock", "Overdrive Adrenaline", "Apex Studio", 16000L, "Action Rock", 136, 146.8),
        RoyaltyTrack("track_vintage_jazz", "Late Night Speakeasy", "Apex Studio", 16000L, "Vintage Jazz", 92, 220.0),
        RoyaltyTrack("track_phonk_drift_fast", "Midnight Phonk Drift", "Apex Studio", 16000L, "Drift Phonk", 145, 103.8),
        RoyaltyTrack("track_fashion_house", "Paris Runway Vogue", "Apex Studio", 16000L, "Deep House", 122, 261.6),
        RoyaltyTrack("track_dark_ambient", "Abyssal Shadow Echo", "Apex Studio", 18000L, "Dark Ambient", 70, 110.0),
        RoyaltyTrack("track_ethnic_deep", "Sahara Nomadic Echoes", "Apex Studio", 18000L, "World Cinematic", 105, 174.6),
        RoyaltyTrack("track_techno_pulse", "Berlin Underground Pulse", "Apex Studio", 16000L, "Dark Techno", 130, 130.8),
        RoyaltyTrack("track_indie_pop", "Golden Hour Memories", "Apex Studio", 16000L, "Indie Pop", 112, 293.6),
        RoyaltyTrack("track_zen_meditation", "Himalayan Wind Bells", "Apex Studio", 20000L, "Zen Ambient", 60, 146.8),
        RoyaltyTrack("track_cinema_trailer", "Hero Rising Requiem", "Apex Studio", 18000L, "Hollywood Trailer", 116, 174.6),
        RoyaltyTrack("track_edm_rave", "Neon Laser Festival Rave", "Apex Studio", 16000L, "EDM Festival", 150, 164.8)
    )

    fun getTrackFile(context: Context, track: RoyaltyTrack): File {
        val audioFile = File(context.cacheDir, "royalty_${track.id}.wav")
        if (audioFile.exists() && audioFile.length() > 5000) {
            return audioFile
        }
        generateMusicalWav(audioFile, track)
        return audioFile
    }

    private fun generateMusicalWav(outputFile: File, track: RoyaltyTrack) {
        val sampleRate = 44100
        val durationSeconds = (track.durationMs / 1000).toInt().coerceAtLeast(8)
        val totalSamples = sampleRate * durationSeconds
        val numChannels = 2
        val bitsPerSample = 16
        val byteRate = sampleRate * numChannels * bitsPerSample / 8

        FileOutputStream(outputFile).use { fos ->
            // Write standard WAV Header
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            val subChunk2Size = totalSamples * numChannels * (bitsPerSample / 8)
            val chunkSize = 36 + subChunk2Size

            header.put("RIFF".toByteArray())
            header.putInt(chunkSize)
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16) // Subchunk1Size (16 for PCM)
            header.putShort(1.toShort()) // AudioFormat (1 for PCM)
            header.putShort(numChannels.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort((numChannels * bitsPerSample / 8).toShort())
            header.putShort(bitsPerSample.toShort())
            header.put("data".toByteArray())
            header.putInt(subChunk2Size)

            fos.write(header.array())

            // Generate musical audio (rich multi-harmonic synth chords and gentle beat)
            val buffer = ByteArray(4096)
            var bufferIdx = 0
            val baseFreq = track.baseFreq
            val beatInterval = (60.0 / track.bpm) * sampleRate

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                val beatPhase = (i % beatInterval) / beatInterval
                val beatVolume = if (beatPhase < 0.15) (1.0 - beatPhase / 0.15) * 0.4 else 0.0

                // 3 harmonic chord voices
                val chordNote = when (((i / (sampleRate * 2)) % 4)) {
                    0 -> 1.0
                    1 -> 1.25 // Major third
                    2 -> 1.5  // Fifth
                    else -> 1.33 // Fourth
                }
                val f1 = baseFreq * chordNote
                val f2 = f1 * 1.5
                val f3 = f1 * 2.0

                val melody = sin(2.0 * PI * f1 * t) * 0.35 +
                        sin(2.0 * PI * f2 * t) * 0.20 +
                        sin(2.0 * PI * f3 * t) * 0.10 +
                        (if (beatPhase < 0.08) (sin(2.0 * PI * 80.0 * beatPhase) * beatVolume) else 0.0)

                // Fade in / out
                val envelope = when {
                    t < 1.0 -> t
                    t > durationSeconds - 1.5 -> (durationSeconds - t) / 1.5
                    else -> 1.0
                }.coerceIn(0.0, 1.0)

                val sample = (melody * envelope * 0.8 * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

                // Left channel
                buffer[bufferIdx++] = (sample.toInt() and 0xFF).toByte()
                buffer[bufferIdx++] = ((sample.toInt() shr 8) and 0xFF).toByte()

                // Right channel
                buffer[bufferIdx++] = (sample.toInt() and 0xFF).toByte()
                buffer[bufferIdx++] = ((sample.toInt() shr 8) and 0xFF).toByte()

                if (bufferIdx >= buffer.size) {
                    fos.write(buffer, 0, bufferIdx)
                    bufferIdx = 0
                }
            }

            if (bufferIdx > 0) {
                fos.write(buffer, 0, bufferIdx)
            }
        }
    }
}
