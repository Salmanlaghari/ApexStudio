package com.example.audio

import android.content.Context
import com.example.model.RoyaltyFreeTrack
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * Procedural synthesizer that generates real 44.1kHz 16-bit PCM WAV audio files
 * for the royalty-free library, ensuring 100% offline playback with real music through MediaPlayer.
 */
object AudioSynthesizer {

    val TRACKS = listOf(
        RoyaltyFreeTrack(
            id = "rf_neon_horizon",
            title = "Neon Horizon",
            genre = "Synthwave / Cyberpunk",
            bpm = 120,
            durationMs = 12_000L,
            description = "Driving analog bassline with pulsing 80s synthesizer arpeggios"
        ),
        RoyaltyFreeTrack(
            id = "rf_sunset_chill",
            title = "Sunset Chill",
            genre = "Lo-Fi Beats",
            bpm = 85,
            durationMs = 12_000L,
            description = "Mellow electric piano chords with relaxed rhythm and warm tones"
        ),
        RoyaltyFreeTrack(
            id = "rf_cyber_drive",
            title = "Cybernetic Drive",
            genre = "Techno / Electro",
            bpm = 130,
            durationMs = 10_000L,
            description = "High-energy punchy kick drum and acid synth sweeps"
        ),
        RoyaltyFreeTrack(
            id = "rf_cinematic_dawn",
            title = "Cinematic Dawn",
            genre = "Soundtrack / Ambient",
            bpm = 70,
            durationMs = 14_000L,
            description = "Ethereal swelling strings and majestic harmonic pads"
        ),
        RoyaltyFreeTrack(
            id = "rf_urban_groove",
            title = "Urban Groove",
            genre = "Boom-Bap / Hip-Hop",
            bpm = 95,
            durationMs = 12_000L,
            description = "Crisp snare snaps, deep 808 bass slides and jazzy chords"
        ),
        RoyaltyFreeTrack(
            id = "rf_acoustic_breeze",
            title = "Acoustic Breeze",
            genre = "Folk / Indie",
            bpm = 110,
            durationMs = 12_000L,
            description = "Gentle fingerpicked guitar melody and light percussion"
        )
    )

    fun ensureTrackGenerated(context: Context, track: RoyaltyFreeTrack): File {
        val audioDir = File(context.filesDir, "royalty_free").apply { mkdirs() }
        val targetFile = File(audioDir, "${track.id}.wav")

        if (targetFile.exists() && targetFile.length() > 44) {
            return targetFile
        }

        generateWavFile(targetFile, track)
        return targetFile
    }

    private fun generateWavFile(file: File, track: RoyaltyFreeTrack) {
        val sampleRate = 44100
        val durationSec = (track.durationMs / 1000f).coerceIn(4f, 15f)
        val numSamples = (sampleRate * durationSec).toInt()

        val sampleBuffer = ShortArray(numSamples)
        val bpm = track.bpm
        val beatInterval = (sampleRate * 60f / bpm).toInt()

        // Generate musical audio based on track archetype
        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val beatIndex = (i / beatInterval)
            val beatFraction = (i % beatInterval).toFloat() / beatInterval

            val sampleVal: Double = when (track.id) {
                "rf_neon_horizon" -> {
                    // Synthwave: 120 bpm bass octaves + arp notes
                    val notes = doubleArrayOf(110.0, 110.0, 146.83, 164.81, 130.81, 146.83)
                    val noteFreq = notes[beatIndex % notes.size]
                    val bass = sin(2.0 * PI * noteFreq * t) * (1.0 - beatFraction * 0.7)
                    val arpFreq = noteFreq * 3.0
                    val arp = sin(2.0 * PI * arpFreq * t) * 0.35
                    (bass * 0.6 + arp * 0.4)
                }
                "rf_sunset_chill" -> {
                    // Lo-Fi: Rhodes electric piano chords + soft sub
                    val chordRoot = if ((beatIndex / 4) % 2 == 0) 220.0 else 196.0
                    val chord = sin(2.0 * PI * chordRoot * t) +
                            sin(2.0 * PI * (chordRoot * 1.25) * t) * 0.7 +
                            sin(2.0 * PI * (chordRoot * 1.5) * t) * 0.5
                    val sub = sin(2.0 * PI * (chordRoot * 0.5) * t) * 0.4
                    (chord * 0.25 + sub * 0.35)
                }
                "rf_cyber_drive" -> {
                    // Techno: Punchy 4/4 kick + acid modulation
                    val kickDecay = Math.exp(-beatFraction * 8.0)
                    val kickFreq = 50.0 + 120.0 * kickDecay
                    val kick = sin(2.0 * PI * kickFreq * t) * kickDecay
                    val acidMod = sin(2.0 * PI * 1.5 * t) * 200.0 + 350.0
                    val acid = (sin(2.0 * PI * acidMod * t) > 0.0)
                    val acidVal = if (acid) 0.25 else -0.25
                    (kick * 0.65 + acidVal * 0.35)
                }
                "rf_cinematic_dawn" -> {
                    // Ambient: Sustained swelling chords
                    val root = 130.81 // C3
                    val swell = (sin(2.0 * PI * 0.15 * t) * 0.3 + 0.7)
                    val pad = sin(2.0 * PI * root * t) * 0.4 +
                            sin(2.0 * PI * (root * 1.498) * t) * 0.3 +
                            sin(2.0 * PI * (root * 1.887) * t) * 0.25 +
                            sin(2.0 * PI * (root * 2.0) * t) * 0.2
                    (pad * swell * 0.5)
                }
                "rf_urban_groove" -> {
                    // Hip-Hop: 808 sub + snare pulse
                    val isSnare = (beatIndex % 2 == 1) && (beatFraction < 0.25)
                    val snare = if (isSnare) (Math.random() - 0.5) * (1.0 - beatFraction * 4.0) else 0.0
                    val subBass = sin(2.0 * PI * 55.0 * t) * 0.5
                    (subBass * 0.6 + snare * 0.4)
                }
                else -> {
                    // Acoustic arpeggios
                    val chordRoots = doubleArrayOf(261.63, 293.66, 329.63, 349.23)
                    val f = chordRoots[beatIndex % chordRoots.size]
                    sin(2.0 * PI * f * t) * (1.0 - beatFraction * 0.5) * 0.5
                }
            }

            sampleBuffer[i] = (sampleVal.coerceIn(-1.0, 1.0) * 30000.0).toInt().toShort()
        }

        FileOutputStream(file).use { fos ->
            writeWavHeader(fos, numSamples, sampleRate)
            val byteBuffer = ByteBuffer.allocate(numSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (sample in sampleBuffer) {
                byteBuffer.putShort(sample)
            }
            fos.write(byteBuffer.array())
        }
    }

    private fun writeWavHeader(out: FileOutputStream, totalAudioLen: Int, sampleRate: Int) {
        val totalDataLen = totalAudioLen * 2 + 36
        val byteRate = sampleRate * 2
        val header = ByteArray(44)

        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte() // 'fmt ' chunk
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 4 bytes: size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // format = 1 (PCM)
        header[21] = 0
        header[22] = 1 // channels = 1 (mono)
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = 2 // block align
        header[33] = 0
        header[34] = 16 // bits per sample
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        val pcmDataLen = totalAudioLen * 2
        header[40] = (pcmDataLen and 0xff).toByte()
        header[41] = ((pcmDataLen shr 8) and 0xff).toByte()
        header[42] = ((pcmDataLen shr 16) and 0xff).toByte()
        header[43] = ((pcmDataLen shr 24) and 0xff).toByte()

        out.write(header, 0, 44)
    }

    /**
     * Generates normalized peak waveform points (60 points) from an audio file.
     */
    fun extractWaveformPoints(audioFile: File, pointCount: Int = 64): List<Float> {
        if (!audioFile.exists() || audioFile.length() < 100) {
            return List(pointCount) { 0.2f }
        }

        try {
            val bytes = audioFile.readBytes()
            val sampleCount = (bytes.size - 44) / 2
            if (sampleCount <= 0) return List(pointCount) { 0.3f }

            val step = sampleCount / pointCount
            val result = ArrayList<Float>(pointCount)

            for (p in 0 until pointCount) {
                val startSample = p * step
                var maxVal = 0
                for (s in 0 until step) {
                    val byteIdx = 44 + (startSample + s) * 2
                    if (byteIdx + 1 < bytes.size) {
                        val sample = (bytes[byteIdx].toInt() and 0xFF) or (bytes[byteIdx + 1].toInt() shl 8)
                        val absVal = Math.abs(sample.toShort().toInt())
                        if (absVal > maxVal) maxVal = absVal
                    }
                }
                val norm = (maxVal / 32767f).coerceIn(0.05f, 1.0f)
                result.add(norm)
            }
            return result
        } catch (e: Exception) {
            return List(pointCount) { 0.35f }
        }
    }
}
