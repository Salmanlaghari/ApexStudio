package com.apexstudio.app.data.engine

import android.util.Log

/**
 * Result returned after routing and executing an operation across the hybrid engines.
 *
 * @property engineUsed The engine that successfully executed the operation.
 * @property returnCode The process/session exit code (0 for success).
 * @property isSuccess Whether execution finished successfully.
 * @property fallbackOccurred Whether a fallback engine was used after primary failure.
 * @property attemptedEngines Ordered list of engines that were attempted.
 * @property errorMessage Error description if execution failed.
 */
data class ExecutionResult(
    val engineUsed: EngineType,
    val returnCode: Int,
    val isSuccess: Boolean,
    val fallbackOccurred: Boolean = false,
    val attemptedEngines: List<EngineType> = emptyList(),
    val errorMessage: String? = null
)

/**
 * Diagnostic record of a routing decision.
 *
 * @property operation The requested multimedia operation.
 * @property selectedEngine The primary engine chosen by routing rules.
 * @property fallbackEngines Ordered list of fallback engines if primary fails.
 * @property rationale Human-readable reason for the routing choice.
 * @property timestamp Epoch millis when the decision was made.
 */
data class RoutingDecision(
    val operation: MediaOperation,
    val selectedEngine: EngineType,
    val fallbackEngines: List<EngineType>,
    val rationale: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Hybrid multimedia router for ApexStudio (PR #2).
 *
 * Dynamically selects the optimal processing engine based on operation characteristics:
 * - Simple trim / cut / playback -> [Media3Engine] (fastest, hardware accelerated)
 * - Complex filters (LUTs, color grading, chroma key) -> [FFmpegEngine] (powerful)
 * - Real-time effects / live preview / streaming -> [GStreamerEngine] (pipeline-based)
 * - Export -> Evaluated by complexity (Simple -> Media3, Complex -> FFmpeg)
 *
 * Features automatic fallback: if the primary engine is unavailable or fails execution,
 * the router seamlessly delegates to candidate fallbacks while logging every decision.
 */
class HybridRouter(
    private val engines: Map<EngineType, Engine> = defaultEngines()
) {

    private val routingHistory = mutableListOf<RoutingDecision>()

    companion object {
        private const val TAG = "HybridRouter"

        /**
         * Creates default instances of all three hybrid engines.
         */
        fun defaultEngines(): Map<EngineType, Engine> = mapOf(
            EngineType.MEDIA3 to Media3Engine(),
            EngineType.FFMPEG to FFmpegEngine(),
            EngineType.GSTREAMER to GStreamerEngine()
        )
    }

    /**
     * Returns an engine by type, or null if not registered.
     */
    fun getEngine(type: EngineType): Engine? = engines[type]

    /**
     * Returns the list of currently available engine types on this device/environment.
     */
    fun getAvailableEngines(): List<EngineType> {
        return engines.filter { it.value.isAvailable() }.map { it.key }
    }

    /**
     * Returns an immutable copy of the routing decision history for diagnostics.
     */
    fun getRoutingHistory(): List<RoutingDecision> = synchronized(routingHistory) {
        routingHistory.toList()
    }

    /**
     * Determines the primary engine and fallback candidates for the requested [MediaOperation].
     *
     * Routing Rules:
     * 1. Real-time / live preview / streaming -> Prefer [GStreamerEngine], fallback to [FFmpegEngine] or [Media3Engine]
     * 2. Complex filters (LUTs, chroma key, color grading) -> Prefer [FFmpegEngine], fallback to [GStreamerEngine] or [Media3Engine]
     * 3. Simple trim / cut / playback -> Prefer [Media3Engine] (hardware fast path), fallback to [FFmpegEngine]
     * 4. Export -> If complex -> Prefer [FFmpegEngine]; If simple -> Prefer [Media3Engine]
     * 5. Format conversion / audio extraction / concat -> Prefer [FFmpegEngine], fallback to [Media3Engine]
     *
     * @param operation The media operation to route.
     * @return [RoutingDecision] containing selected engine, fallbacks, and rationale.
     */
    fun planRoute(operation: MediaOperation): RoutingDecision {
        val (primary, fallbacks, rationale) = when {
            // Real-time effects, live preview, streaming
            operation.requiresRealtime ||
            operation.type == OperationType.REALTIME_PREVIEW ||
            operation.type == OperationType.STREAMING -> {
                Triple(
                    EngineType.GSTREAMER,
                    listOf(EngineType.FFMPEG, EngineType.MEDIA3),
                    "Real-time pipeline requested: routed to GStreamer"
                )
            }

            // Complex filters (LUTs, color grading, chroma key)
            operation.type == OperationType.COMPLEX_FILTER -> {
                Triple(
                    EngineType.FFMPEG,
                    listOf(EngineType.GSTREAMER, EngineType.MEDIA3),
                    "Complex filter/chroma/LUT requested: routed to FFmpeg"
                )
            }

            // Export: choose based on complexity
            operation.type == OperationType.EXPORT -> {
                if (operation.isComplex) {
                    Triple(
                        EngineType.FFMPEG,
                        listOf(EngineType.MEDIA3),
                        "Complex multi-layer export requested: routed to FFmpeg"
                    )
                } else {
                    Triple(
                        EngineType.MEDIA3,
                        listOf(EngineType.FFMPEG),
                        "Simple fast-path export requested: routed to Media3 hardware encoder"
                    )
                }
            }

            // Simple trim, cut, playback
            operation.type == OperationType.TRIM_CUT ||
            operation.type == OperationType.PLAYBACK -> {
                Triple(
                    EngineType.MEDIA3,
                    listOf(EngineType.FFMPEG),
                    "Low-overhead playback/trim requested: routed to Media3 fast path"
                )
            }

            // Audio extraction, concat, convert
            operation.type == OperationType.AUDIO_EXTRACT ||
            operation.type == OperationType.CONCAT_MERGE ||
            operation.type == OperationType.FORMAT_CONVERT -> {
                Triple(
                    EngineType.FFMPEG,
                    listOf(EngineType.MEDIA3),
                    "Demuxing/transcoding/concat operation: routed to FFmpeg"
                )
            }

            else -> {
                Triple(
                    EngineType.MEDIA3,
                    listOf(EngineType.FFMPEG),
                    "Default operation fallback: routed to Media3"
                )
            }
        }

        val decision = RoutingDecision(
            operation = operation,
            selectedEngine = primary,
            fallbackEngines = fallbacks,
            rationale = rationale
        )

        synchronized(routingHistory) {
            routingHistory.add(decision)
        }

        try {
            Log.d(TAG, "Routing ${operation.type} -> $primary (Fallbacks: $fallbacks) [$rationale]")
        } catch (_: Throwable) {
            println("[$TAG] Routing ${operation.type} -> $primary: $rationale")
        }

        return decision
    }

    /**
     * Returns the ordered list of engines to attempt for this operation.
     */
    fun getExecutionCandidates(operation: MediaOperation): List<Engine> {
        val decision = planRoute(operation)
        val candidateTypes = listOf(decision.selectedEngine) + decision.fallbackEngines
        return candidateTypes.mapNotNull { engines[it] }
    }

    /**
     * Executes the given operation using the best available engine with automatic fallback.
     *
     * @param operation The multimedia operation to execute.
     * @param command The argument list or pipeline command.
     * @return [ExecutionResult] containing the winning engine, status, and fallback history.
     */
    fun execute(operation: MediaOperation, command: Array<String>): ExecutionResult {
        val candidates = getExecutionCandidates(operation)
        if (candidates.isEmpty()) {
            return ExecutionResult(
                engineUsed = EngineType.MEDIA3,
                returnCode = -1,
                isSuccess = false,
                errorMessage = "No registered engines capable of handling operation ${operation.type}"
            )
        }

        val attempted = mutableListOf<EngineType>()
        var lastError: String? = null

        for (i in candidates.indices) {
            val candidate = candidates[i]
            attempted.add(candidate.engineType)

            // Check if engine supports the operation
            if (!candidate.supports(operation)) {
                continue
            }

            try {
                val code = candidate.execute(command)
                if (code == 0) {
                    val fallbackOccurred = i > 0
                    return ExecutionResult(
                        engineUsed = candidate.engineType,
                        returnCode = 0,
                        isSuccess = true,
                        fallbackOccurred = fallbackOccurred,
                        attemptedEngines = attempted
                    )
                } else {
                    lastError = "Engine ${candidate.engineType} returned non-zero code $code"
                    try {
                        Log.w(TAG, "Engine ${candidate.engineType} failed with code $code; trying fallback...")
                    } catch (_: Throwable) {
                        println("[$TAG] Engine ${candidate.engineType} failed with code $code; trying fallback...")
                    }
                }
            } catch (t: Throwable) {
                lastError = "${candidate.engineType} threw ${t::class.java.simpleName}: ${t.message}"
                try {
                    Log.w(TAG, "Engine ${candidate.engineType} execution exception: ${t.message}; trying fallback...")
                } catch (_: Throwable) {
                    println("[$TAG] Engine ${candidate.engineType} threw exception: ${t.message}")
                }
            }
        }

        return ExecutionResult(
            engineUsed = attempted.lastOrNull() ?: EngineType.MEDIA3,
            returnCode = -1,
            isSuccess = false,
            fallbackOccurred = attempted.size > 1,
            attemptedEngines = attempted,
            errorMessage = lastError ?: "All candidate engines failed for operation ${operation.type}"
        )
    }
}
