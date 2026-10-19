package com.apexstudio.app.data.effect

/** Dimensions for a full-frame text sprite, with the longest edge capped. */
internal object TextOverlaySpriteSize {
    private const val LONG_EDGE = 1600

    fun forAspect(aspect: Float): Pair<Int, Int> {
        val safeAspect = aspect.coerceIn(0.2f, 5f)
        return if (safeAspect >= 1f) {
            LONG_EDGE to (LONG_EDGE / safeAspect).toInt().coerceAtLeast(1)
        } else {
            (LONG_EDGE * safeAspect).toInt().coerceAtLeast(1) to LONG_EDGE
        }
    }
}
