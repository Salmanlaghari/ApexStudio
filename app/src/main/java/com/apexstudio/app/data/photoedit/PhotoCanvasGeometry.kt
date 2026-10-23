package com.apexstudio.app.data.photoedit

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Pure geometry shared by bitmap canvas rendering and host-side tests. */
internal data class PhotoCanvasGeometry(
    val width: Int,
    val height: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    companion object {
        fun calculate(sourceWidth: Int, sourceHeight: Int, aspect: Float, fill: Boolean): PhotoCanvasGeometry {
            require(sourceWidth > 0 && sourceHeight > 0)
            require(aspect.isFinite() && aspect > 0f)
            // Never allocate a canvas with a side larger than the decoded image's
            // longest side. Preview and export keep their existing memory budgets.
            val longest = max(sourceWidth, sourceHeight)
            val width = if (aspect >= 1f) longest else (longest * aspect).roundToInt().coerceAtLeast(1)
            val height = if (aspect >= 1f) (longest / aspect).roundToInt().coerceAtLeast(1) else longest
            val sx = width.toFloat() / sourceWidth
            val sy = height.toFloat() / sourceHeight
            val scale = if (fill) max(sx, sy) else min(sx, sy)
            val drawnWidth = sourceWidth * scale
            val drawnHeight = sourceHeight * scale
            val left = (width - drawnWidth) / 2f
            val top = (height - drawnHeight) / 2f
            return PhotoCanvasGeometry(width, height, left, top, left + drawnWidth, top + drawnHeight)
        }
    }
}
