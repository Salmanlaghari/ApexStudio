package com.apexstudio.app.ai

import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/** Thread-safe cancellation token for CPU/inference loops that are not coroutine-aware. */
class AiCancellationToken {
    private val cancelled = AtomicBoolean(false)
    fun cancel() { cancelled.set(true) }
    fun isCancelled(): Boolean = cancelled.get()
    fun throwIfCancelled() { if (isCancelled()) throw CancellationException("AI operation cancelled") }
}

data class AiProgress(val feature: AiFeatureId, val fraction: Float, val phase: String) {
    init { require(fraction.isFinite() && fraction in 0f..1f) }
}

class AiModelUnavailableException(message: String) : IllegalStateException(message)
