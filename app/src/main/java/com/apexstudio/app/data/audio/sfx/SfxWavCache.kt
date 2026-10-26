package com.apexstudio.app.data.audio.sfx

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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
        val expectedFrames = ((durationMs.toDouble() * SfxSynth.SAMPLE_RATE) / 1_000.0)
            .roundToInt()
            .coerceAtLeast(1)
        val expectedDataBytes = expectedFrames * 2
        val expectedBytes = SfxSynth.WAV_HEADER_BYTES + expectedDataBytes
        if (destination.isFile && destination.length() == expectedBytes.toLong() &&
            isWaveFile(destination, expectedDataBytes)
        ) {
            return destination
        }

        val samples = SfxSynth.render(effect.id, durationMs, safeIntensity)
        val temporary = File.createTempFile("${key}_", ".part", directory)
        try {
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
            replaceAtomically(temporary, destination)
        } finally {
            if (temporary.exists()) temporary.delete()
        }
        return destination
    }

    private fun replaceAtomically(temporary: File, destination: File) {
        try {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            // Both files are in the app cache on the same filesystem, so the fallback is a move, not a copy.
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
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

    private fun isWaveFile(file: File, expectedDataBytes: Int): Boolean {
        if (file.length() != (SfxSynth.WAV_HEADER_BYTES + expectedDataBytes).toLong()) return false
        return try {
            val header = ByteArray(SfxSynth.WAV_HEADER_BYTES)
            RandomAccessFile(file, "r").use { it.readFully(header) }

            fun u16(offset: Int): Int =
                (header[offset].toInt() and 0xff) or ((header[offset + 1].toInt() and 0xff) shl 8)

            fun u32(offset: Int): Long =
                (header[offset].toLong() and 0xffL) or
                    ((header[offset + 1].toLong() and 0xffL) shl 8) or
                    ((header[offset + 2].toLong() and 0xffL) shl 16) or
                    ((header[offset + 3].toLong() and 0xffL) shl 24)

            String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                u32(4) == 36L + expectedDataBytes &&
                String(header, 8, 4, Charsets.US_ASCII) == "WAVE" &&
                String(header, 12, 4, Charsets.US_ASCII) == "fmt " &&
                u32(16) == 16L &&
                u16(20) == 1 && // PCM
                u16(22) == 1 && // Mono
                u32(24) == SfxSynth.SAMPLE_RATE.toLong() &&
                u32(28) == (SfxSynth.SAMPLE_RATE * 2).toLong() &&
                u16(32) == 2 &&
                u16(34) == 16 &&
                String(header, 36, 4, Charsets.US_ASCII) == "data" &&
                u32(40) == expectedDataBytes.toLong()
        } catch (_: Exception) {
            false
        }
    }
}
