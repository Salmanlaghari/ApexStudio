package com.apexstudio.app.data.export

/** Runs resource setup and releases the resource exactly once if setup fails. */
internal inline fun <T> setupWithCleanup(
    resource: T,
    setup: (T) -> Unit,
    cleanup: (T) -> Unit
): T {
    try {
        setup(resource)
        return resource
    } catch (failure: Throwable) {
        try {
            cleanup(resource)
        } catch (cleanupFailure: Throwable) {
            failure.addSuppressed(cleanupFailure)
        }
        throw failure
    }
}

/** Monotonic no-progress timeout for codec input/output loops. */
internal class ExportCodecWatchdog(
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    private var lastProgressMs = nowMs()

    init {
        require(timeoutMs > 0L) { "Codec no-progress timeout must be positive" }
    }

    fun progressed() {
        lastProgressMs = nowMs()
    }

    fun remainingMs(): Long = (timeoutMs - (nowMs() - lastProgressMs).coerceAtLeast(0L))
        .coerceAtLeast(0L)

    fun throwIfTimedOut() {
        check(remainingMs() > 0L) {
            "MediaCodec made no progress for ${timeoutMs}ms"
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 10_000L
    }
}
