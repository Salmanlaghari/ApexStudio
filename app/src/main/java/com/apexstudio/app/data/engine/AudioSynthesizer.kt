package com.apexstudio.app.data.engine

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * Procedural multi-harmonic audio synthesis engine.
 * Generates lossless 16-bit 44.1kHz PCM WAV tracks for royalty-free music and sound design.
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

    /**
     * Procedural audio synthesizer creating 44.1 kHz 16-bit PCM royalty-free WAV tracks:
     * "neon_horizon", "cyber_pulse", "sunset_drive", "ambient_chill".
     */
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

    /**
     * Generates normalized peak waveform points from an audio file.
     * Streams the file in fixed-size chunks to avoid OOM on large audio files.
     */
    fun extractWaveformPoints(audioFile: File, pointCount: Int = 64): List<Float> {
        if (!audioFile.exists() || audioFile.length() < 100) {
            return List(pointCount) { 0.2f }
        }

        try {
            val fileLength = audioFile.length()
            val dataLength = fileLength - 44L
            val sampleCount = dataLength / 2L
            if (sampleCount <= 0L) return List(pointCount) { 0.3f }

            val step = (sampleCount / pointCount).coerceAtLeast(1L)
            val maxVals = IntArray(pointCount)

            FileInputStream(audioFile).use { fis ->
                // Skip 44-byte WAV header safely
                val header = ByteArray(44)
                var headerRead = 0
                while (headerRead < 44) {
                    val r = fis.read(header, headerRead, 44 - headerRead)
                    if (r == -1) break
                    headerRead += r
                }

                val buffer = ByteArray(4096)
                var currentSampleIndex = 0L
                var bytesRead: Int
                var leftoverByte: Int? = null

                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    var offset = 0

                    if (leftoverByte != null && bytesRead > 0) {
                        val b0 = leftoverByte
                        val b1 = buffer[0].toInt()
                        val sample = (b0 or (b1 shl 8)).toShort().toInt()
                        val absVal = Math.abs(sample)
                        val p = (currentSampleIndex / step).toInt()
                        if (p in 0 until pointCount && absVal > maxVals[p]) {
                            maxVals[p] = absVal
                        }
                        currentSampleIndex++
                        offset = 1
                        leftoverByte = null
                    }

                    while (offset + 1 < bytesRead) {
                        val p = (currentSampleIndex / step).toInt()
                        if (p >= pointCount) break

                        val b0 = buffer[offset].toInt() and 0xFF
                        val b1 = buffer[offset + 1].toInt()
                        val sample = (b0 or (b1 shl 8)).toShort().toInt()
                        val absVal = Math.abs(sample)
                        if (absVal > maxVals[p]) {
                            maxVals[p] = absVal
                        }

                        currentSampleIndex++
                        offset += 2
                    }

                    if (offset < bytesRead) {
                        leftoverByte = buffer[offset].toInt() and 0xFF
                    }

                    if ((currentSampleIndex / step) >= pointCount) {
                        break
                    }
                }
            }

            return maxVals.map { maxVal ->
                (maxVal / 32767f).coerceIn(0.05f, 1.0f)
            }
        } catch (e: Exception) {
            return List(pointCount) { 0.35f }
        }
    }
}
