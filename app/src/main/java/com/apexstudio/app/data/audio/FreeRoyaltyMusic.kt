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
        RoyaltyTrack("track_cinematic", "Cinematic Horizon", "Apex Studio", 12000L, "Cinematic", 110, 220.0),
        RoyaltyTrack("track_cyberpunk", "Cyberpunk Pulse", "Apex Studio", 15000L, "Synthwave", 128, 146.8),
        RoyaltyTrack("track_lofi", "Lo-Fi Midnight Study", "Apex Studio", 14000L, "Lo-Fi Chill", 85, 174.6),
        RoyaltyTrack("track_acoustic", "Acoustic Summer Breeze", "Apex Studio", 12000L, "Acoustic", 100, 261.6),
        RoyaltyTrack("track_phonk", "Phonk Drift Tokyo", "Apex Studio", 10000L, "Phonk", 140, 110.0),
        RoyaltyTrack("track_ambient", "Ambient Astral Glow", "Apex Studio", 16000L, "Ambient", 75, 196.0),
        RoyaltyTrack("track_tropical", "Tropical Sunset Vibes", "Apex Studio", 13000L, "EDM", 124, 293.6),
        RoyaltyTrack("track_corporate", "Epic Uplift Energy", "Apex Studio", 11000L, "Pop", 120, 329.6),
        RoyaltyTrack("track_rock", "Neon Rock Anthem", "Apex Studio", 10000L, "Rock", 135, 164.8),
        RoyaltyTrack("track_meditation", "Zen Deep Resonance", "Apex Studio", 18000L, "Meditation", 60, 130.8)
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
