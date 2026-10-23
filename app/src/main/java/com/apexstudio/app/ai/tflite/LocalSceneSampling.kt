package com.apexstudio.app.ai.tflite

/** Bounded source-time sampling. The trim's out-point is exclusive, including on exact intervals. */
object LocalSceneSampling {
    fun timestamps(startMs: Long, endMs: Long, intervalMs: Long, maximumSamples: Int): List<Long> {
        require(startMs >= 0L && endMs > startMs) { "Invalid source trim range" }
        require(intervalMs > 0L && maximumSamples in 2..10_000)
        val span = endMs - startMs - 1L
        val intervals = span / intervalMs
        // Add the final retained millisecond when it is not already on the interval grid.
        val regularCount = intervals + 1L
        val hasTail = span % intervalMs != 0L
        if (regularCount <= maximumSamples && (!hasTail || regularCount < maximumSamples)) {
            return buildList {
                repeat(regularCount.toInt()) { add(startMs + it.toLong() * intervalMs) }
                if (hasTail) add(endMs - 1L)
            }
        }
        // Spread capped samples over the entire retained source, instead of dropping the tail.
        // Divide before multiplying so even Long.MAX_VALUE source ranges cannot overflow.
        val divisor = maximumSamples - 1L
        return List(maximumSamples) { index ->
            val i = index.toLong()
            startMs + (span / divisor) * i + ((span % divisor) * i) / divisor
        }
    }
}
