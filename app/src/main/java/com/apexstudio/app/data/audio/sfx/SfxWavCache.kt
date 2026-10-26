package com.apexstudio.app.data.audio.sfx

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.roundToInt

/** Writes and reuses generated WAV files in the disposable application cache. */
object SfxWavCache {
    @Synchronized
    fun getOrCreate(
        context: Context,
        effectId: String,
        durationMs: Long,
        intensity: Float = SfxLibrary.DEFAULT_INTENSITY
    ): File {
        val safeIntensity = intensity.coerceIn(0f, 1f)
        val effect = SfxLibrary.find(effectId)
            ?: throw IllegalArgumentException("Unknown sound effect: $effectId")
        require(durationMs in 1L..SfxSynth.MAX_DURATION_MS) { "Unsupported effect duration" }
        val key = "v1_${effect.id}_${durationMs}_${safeIntensity.toBits().toUInt().toString(16)}"
        val directory = File(context.cacheDir, "sfx").apply { mkdirs() }
        val destination = File(directory, "$key.wav")
        val expectedFrames = ((durationMs.toDouble() * SfxSynth.SAMPLE_RATE) / 1_000.0).roundToInt().coerceAtLeast(1)
        val expectedBytes = SfxSynth.WAV_HEADER_BYTES + expectedFrames * 2
        if (destination.isFile && destination.length() == expectedBytes.toLong() && isWaveFile(destination)) {
            return destination
        }

        val samples = SfxSynth.render(effect.id, durationMs, safeIntensity)
        val temporary = File(directory, "$key.wav.part")
        if (temporary.exists()) temporary.delete()
        FileOutputStream(temporary).use { output ->
            writeHeader(output, samples.size * 2)
            val block = ByteArray(8 * 1024)
            var offset = 0
            while (offset < samples.size) {
                val count = minOf(samples.size - offset, block.size / 2)
                for (index in 0 until count) {
                    val sample = samples[offset + index].toInt()
                    block[index * 2] = sample.toByte()
                    block[index * 2 + 1] = (sample shr 8).toByte()
                }
                output.write(block, 0, count * 2)
                offset += count
            }
            output.fd.sync()
        }
        if (destination.exists() && !destination.delete()) {
            temporary.delete()
            throw IllegalStateException("Could not replace cached sound effect")
        }
        if (!temporary.renameTo(destination)) {
            temporary.copyTo(destination, overwrite = true)
            temporary.delete()
        }
        return destination
    }

    private fun writeHeader(output: FileOutputStream, dataBytes: Int) {
        output.write("RIFF".toByteArray(Charsets.US_ASCII))
        writeIntLe(output, 36 + dataBytes)
        output.write("WAVE".toByteArray(Charsets.US_ASCII))
        output.write("fmt ".toByteArray(Charsets.US_ASCII))
        writeIntLe(output, 16)
        writeShortLe(output, 1)
        writeShortLe(output, 1)
        writeIntLe(output, SfxSynth.SAMPLE_RATE)
        writeIntLe(output, SfxSynth.SAMPLE_RATE * 2)
        writeShortLe(output, 2)
        writeShortLe(output, 16)
        output.write("data".toByteArray(Charsets.US_ASCII))
        writeIntLe(output, dataBytes)
    }

    private fun writeIntLe(output: FileOutputStream, value: Int) {
        output.write(value and 0xff)
        output.write((value ushr 8) and 0xff)
        output.write((value ushr 16) and 0xff)
        output.write((value ushr 24) and 0xff)
    }

    private fun writeShortLe(output: FileOutputStream, value: Int) {
        output.write(value and 0xff)
        output.write((value ushr 8) and 0xff)
    }

    private fun isWaveFile(file: File): Boolean = try {
        RandomAccessFile(file, "r").use { wav ->
            val header = ByteArray(12)
            wav.readFully(header)
            String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(header, 8, 4, Charsets.US_ASCII) == "WAVE"
        }
    } catch (_: Exception) {
        false
    }
}
