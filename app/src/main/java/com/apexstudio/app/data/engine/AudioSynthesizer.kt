package com.apexstudio.app.data.engine

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * Procedural multi-harmonic audio synthesis engine.
 * Generates lossless 16-bit 44.1kHz stereo PCM WAV tracks for royalty-free music and sound design.
 */
object AudioSynthesizer {

    data class TrackSpec(
        val title: String,
        val genre: String,
        val bpm: Int,
        val baseFreqHz: Double,
        val durationMs: Long
    )

    fun synthesizeWav(file: File, spec: TrackSpec) {
        val sampleRate = 44100
        val durationSeconds = (spec.durationMs / 1000).toInt().coerceAtLeast(3)
        val totalSamples = sampleRate * durationSeconds
        val numChannels = 2
        val bitsPerSample = 16
        val byteRate = sampleRate * numChannels * bitsPerSample / 8
        val subChunk2Size = totalSamples * numChannels * (bitsPerSample / 8)
        val chunkSize = 36 + subChunk2Size

        FileOutputStream(file).use { fos ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(chunkSize)
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16)
            header.putShort(1.toShort())
            header.putShort(numChannels.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort((numChannels * bitsPerSample / 8).toShort())
            header.putShort(bitsPerSample.toShort())
            header.put("data".toByteArray())
            header.putInt(subChunk2Size)
            fos.write(header.array())

            val buffer = ByteArray(4096)
            var bufferIdx = 0
            val beatInterval = (60.0 / spec.bpm) * sampleRate

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                val beatPhase = (i % beatInterval) / beatInterval
                val kickVol = if (beatPhase < 0.12) (1.0 - beatPhase / 0.12) * 0.4 else 0.0

                // Chord notes progression based on time
                val step = ((t / 2.0).toInt()) % 4
                val chordRatio = when (step) {
                    0 -> 1.0
                    1 -> 1.25 // Major third
                    2 -> 1.5  // Perfect fifth
                    else -> 1.33 // Perfect fourth
                }
                val f1 = spec.baseFreqHz * chordRatio
                val f2 = f1 * 1.5
                val f3 = f1 * 2.0

                val synthVoice = sin(2.0 * PI * f1 * t) * 0.35 +
                        sin(2.0 * PI * f2 * t) * 0.20 +
                        sin(2.0 * PI * f3 * t) * 0.12 +
                        (if (beatPhase < 0.06) sin(2.0 * PI * 65.0 * beatPhase) * kickVol else 0.0)

                val env = when {
                    t < 0.5 -> t / 0.5
                    t > durationSeconds - 1.0 -> (durationSeconds - t) / 1.0
                    else -> 1.0
                }.coerceIn(0.0, 1.0)

                val sample = (synthVoice * env * 0.8 * Short.MAX_VALUE).toInt().coerceIn(
                    Short.MIN_VALUE.toInt(),
                    Short.MAX_VALUE.toInt()
                ).toShort()

                // Left & Right channels
                buffer[bufferIdx++] = (sample.toInt() and 0xFF).toByte()
                buffer[bufferIdx++] = ((sample.toInt() shr 8) and 0xFF).toByte()
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
