package com.apexstudio.app.data.engine

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig

/**
 * Foundation engine for FFmpeg operations in ApexStudio.
 *
 * Implements [Engine] to serve as the batch processing powerhouse for:
 * - Complex software filtergraphs (3D LUTs, curves, multi-layer compositing)
 * - Chroma keying / green screen removal
 * - Audio extraction, format conversion, concat demuxing
 * - Heavy or multi-pass exports where Media3 cannot handle the codecs/filters
 *
 * Fixed in PR #156 & updated in PR #2 (Hybrid Router integration).
 */
class FFmpegEngine : Engine {

    override val engineType: EngineType = EngineType.FFMPEG

    /**
     * Returns true if FFmpeg supports the requested operation.
     */
    override fun supports(operation: MediaOperation): Boolean {
        return when (operation.type) {
            OperationType.COMPLEX_FILTER -> true
            OperationType.AUDIO_EXTRACT -> true
            OperationType.CONCAT_MERGE -> true
            OperationType.FORMAT_CONVERT -> true
            OperationType.EXPORT -> operation.isComplex
            OperationType.TRIM_CUT -> true // Supported as fallback when re-encoding is needed
            OperationType.PLAYBACK -> false // FFmpeg is batch CLI, not interactive player
            OperationType.REALTIME_PREVIEW -> false
            OperationType.STREAMING -> true
        }
    }

    /**
     * Executes an FFmpeg command synchronously.
     *
     * @param command the FFmpeg command line arguments (e.g., `arrayOf("-i", input, output)`)
     * @return the FFmpeg return code. `0` indicates success.
     * @throws Exception if the native FFmpeg library fails to load or execute.
     */
    override fun execute(command: Array<String>): Int {
        // Let exceptions propagate — callers need the real error, not a fake -1.
        val session = FFmpegKit.executeWithArguments(command)
        return session?.returnCode?.value
            ?: throw IllegalStateException("FFmpegKit.executeWithArguments returned a null session")
    }

    /**
     * Returns the version string of the underlying ffmpeg-kit library.
     *
     * @return version string (e.g., "8.1.7").
     * @throws Exception if the native FFmpeg library is missing or broken.
     */
    override fun getVersion(): String {
        // Let exceptions propagate — hiding them as "unknown" masks real failures.
        // FFmpegKit.getVersion() removed in 8.x — use FFmpegKitConfig.getVersion().
        return FFmpegKitConfig.getVersion()
    }

    /**
     * Checks whether FFmpeg is supported on the current device.
     *
     * Calls [getVersion] — if it returns a non-empty string without throwing,
     * the native library is present and functional.
     *
     * @return `true` if FFmpeg is available, `false` if the native lib is missing/broken.
     */
    override fun isAvailable(): Boolean {
        return try {
            getVersion().isNotEmpty()
        } catch (e: Throwable) {
            // Catch Throwable — native lib loading can throw UnsatisfiedLinkError (an Error, not Exception).
            false
        }
    }
}
