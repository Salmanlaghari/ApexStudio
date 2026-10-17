package com.apexstudio.app.data.engine

/**
 * Types of multimedia operations handled by ApexStudio's Hybrid Engine.
 */
enum class OperationType {
    /** Simple trim / cut operation on a video or audio clip. */
    TRIM_CUT,
    /** Fast hardware-accelerated playback and frame decoding. */
    PLAYBACK,
    /** Advanced effects, 3D LUTs, multi-stage color grading, chroma keying. */
    COMPLEX_FILTER,
    /** Pipeline-based real-time effect preview and low latency monitoring. */
    REALTIME_PREVIEW,
    /** Live streaming, RTP/RTSP, network sinks. */
    STREAMING,
    /** Extracting audio tracks from video files. */
    AUDIO_EXTRACT,
    /** Merging or concatenating multiple clips together. */
    CONCAT_MERGE,
    /** Converting container formats and transcoding codecs. */
    FORMAT_CONVERT,
    /** Video render and export. Routed based on complexity. */
    EXPORT
}

/**
 * Supported multimedia engine types in the Hybrid Engine architecture.
 */
enum class EngineType {
    /** Media3 (ExoPlayer & Transformer) — Hardware accelerated, zero-copy, fastest path. */
    MEDIA3,
    /** FFmpeg — Batch processing powerhouse for complex software filters, LUTs, chroma key, audio. */
    FFMPEG,
    /** GStreamer — Pipeline-based real-time streaming, low-latency live preview, and exotic muxing. */
    GSTREAMER
}

/**
 * Encapsulates a multimedia operation and its routing context.
 *
 * @property type The classification of the multimedia operation.
 * @property isComplex Whether the operation involves multi-pass processing, complex node graphs, or software filters.
 * @property requiresRealtime Whether the operation demands low-latency, real-time pipeline streaming.
 * @property requiresHardwareAcceleration Whether the operation prefers hardware video codecs (MediaCodec).
 * @property parameters Optional key-value metadata for the operation.
 */
data class MediaOperation(
    val type: OperationType,
    val isComplex: Boolean = false,
    val requiresRealtime: Boolean = false,
    val requiresHardwareAcceleration: Boolean = false,
    val parameters: Map<String, Any?> = emptyMap()
)

/**
 * Common contract implemented by all multimedia engines in ApexStudio.
 */
interface Engine {
    /** Unique engine identifier. */
    val engineType: EngineType

    /** Checks whether the engine is initialized and its native libraries are available. */
    fun isAvailable(): Boolean

    /** Returns the engine or underlying library version string. */
    fun getVersion(): String

    /** Returns true if this engine supports the specified operation. */
    fun supports(operation: MediaOperation): Boolean

    /**
     * Executes the multimedia command synchronously.
     *
     * @param command Command arguments or pipeline string elements.
     * @return Return code (0 indicates success).
     * @throws Exception on execution failure.
     */
    fun execute(command: Array<String>): Int
}
