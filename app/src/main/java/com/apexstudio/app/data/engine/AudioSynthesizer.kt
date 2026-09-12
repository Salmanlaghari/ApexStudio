package com.apexstudio.app.data.engine

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * Procedural audio synthesizer creating 44.1 kHz 16-bit PCM royalty-free WAV tracks:
 * "Neon Horizon", "Cybernetic Pulse", "Sunset Drive", "Ambient Chill".
 */
object AudioSynthesizer {

    fun generateRoyaltyFreeTrack(
        outputFile: File,
        trackId: String,
        durationSeconds: Int = 15
    ): File {
        val sampleRate = 44100
        val totalSamples = sampleRate * durationSeconds
        val pcm = ShortArray(totalSamples)

        when (trackId) {
            "cyber_pulse" -> {
                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate
                    val bass = sin(2 * PI * 55.0 * t) // 55 Hz A1
                    val sub = sin(2 * PI * 110.0 * t)
                    val env = (t % 0.5) / 0.5 // pulsating beat
                    pcm[i] = ((bass * 0.6 + sub * 0.4) * (1.0 - env * 0.4) * 16000).toInt().toShort()
                }
            }
            "sunset_drive" -> {
                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate
                    val chord = sin(2 * PI * 220.0 * t) + sin(2 * PI * 277.18 * t) + sin(2 * PI * 329.63 * t)
                    val lfo = (sin(2 * PI * 0.25 * t) + 1.0) * 0.5
                    pcm[i] = (chord * 0.33 * lfo * 14000).toInt().toShort()
                }
            }
            "ambient_chill" -> {
                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate
                    val wave = sin(2 * PI * 174.61 * t) * 0.5 + sin(2 * PI * 261.63 * t) * 0.5 // F & C
                    pcm[i] = (wave * 12000).toInt().toShort()
                }
            }
            else -> { // "neon_horizon"
                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate
                    val noteFreq = when (((t / 0.5).toInt()) % 4) {
                        0 -> 130.81 // C3
                        1 -> 164.81 // E3
                        2 -> 196.00 // G3
                        else -> 246.94 // B3
                    }
                    val synth = sin(2 * PI * noteFreq * t)
                    pcm[i] = (synth * 15000).toInt().toShort()
                }
            }
        }

        writeWav(outputFile, pcm, sampleRate)
        return outputFile
    }

    private fun writeWav(file: File, pcm: ShortArray, sampleRate: Int) {
        val totalAudioLen = pcm.size * 2L
        val totalDataLen = totalAudioLen + 36
        val channels = 1
        val byteRate = sampleRate * channels * 2L

        val header = ByteArray(44)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray())
        buf.putInt(totalDataLen.toInt())
        buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray())
        buf.putInt(16) // Subchunk1Size
        buf.putShort(1) // PCM
        buf.putShort(channels.toShort())
        buf.putInt(sampleRate)
        buf.putInt(byteRate.toInt())
        buf.putShort((channels * 2).toShort())
        buf.putShort(16) // BitsPerSample
        buf.put("data".toByteArray())
        buf.putInt(totalAudioLen.toInt())

        FileOutputStream(file).use { fos ->
            fos.write(header)
            val byteBuf = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (s in pcm) byteBuf.putShort(s)
            fos.write(byteBuf.array())
        }
    }
}
