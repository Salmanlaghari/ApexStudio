package com.apexstudio.app.data.export

import java.nio.ByteBuffer

/** Only actual input/output work resets the inactivity deadline, never an empty poll. */
internal data class CodecPumpStep(
    val progressed: Boolean = false,
    val outputEnded: Boolean = false
)

/** Temporary empty output after input EOS is not output EOS. */
internal fun pumpCodecUntilOutputEos(
    watchdog: ExportCodecWatchdog = ExportCodecWatchdog(),
    step: () -> CodecPumpStep
) {
    while (true) {
        val result = step()
        if (result.outputEnded) return
        if (result.progressed) watchdog.progressed()
        watchdog.throwIfTimedOut()
    }
}

/** Copy exactly BufferInfo's byte window without changing the codec-owned buffer. */
internal fun copyEncodedOutput(buffer: ByteBuffer?, offset: Int, size: Int): ByteArray {
    require(offset >= 0 && size >= 0) { "Invalid encoded output range" }
    if (size == 0) return ByteArray(0)
    requireNotNull(buffer) { "Missing encoded output buffer" }
    require(offset <= buffer.capacity() && size <= buffer.capacity() - offset) {
        "Encoded output range exceeds codec buffer"
    }
    val window = buffer.duplicate()
    window.clear()
    window.position(offset)
    window.limit(offset + size)
    return ByteArray(size).also { window.get(it) }
}
