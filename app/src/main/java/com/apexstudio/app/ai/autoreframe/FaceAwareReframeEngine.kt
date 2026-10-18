package com.apexstudio.app.ai.autoreframe

import com.apexstudio.app.ai.mediapipe.FaceDetection
import kotlin.math.max

/** Normalized source crop rectangle, top-left origin, right/bottom exclusive. */
data class NormalizedCrop(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val centerX get() = (left + right) * 0.5f
    val centerY get() = (top + bottom) * 0.5f
}

data class ReframeFrame(val timestampMs: Long, val crop: NormalizedCrop, val faceFound: Boolean)

/**
 * Geometry/temporal layer for face-aware reframe. Detection is never faked: callers supply per-frame
 * MediaPipe detections, and missing detections are explicitly marked while using a centered crop.
 * Pixel rendering/encoding remains the editor's responsibility.
 */
object FaceAwareReframeEngine {
    fun cropFor(
        sourceWidth: Int,
        sourceHeight: Int,
        outputAspectRatio: Float,
        faces: List<FaceDetection>,
        margin: Float = 0.35f,
        previous: NormalizedCrop? = null,
        smoothing: Float = 0.35f
    ): ReframeFrame {
        require(sourceWidth > 0 && sourceHeight > 0 && outputAspectRatio.isFinite() && outputAspectRatio > 0f)
        require(margin.isFinite() && margin in 0f..2f && smoothing.isFinite() && smoothing in 0f..1f)
        val aspect = sourceWidth.toFloat() / sourceHeight
        val (cropW, cropH) = if (aspect >= outputAspectRatio) outputAspectRatio / aspect to 1f
            else 1f to aspect / outputAspectRatio
        val chosen = faces.asSequence()
            .filter { it.box.left.isFinite() && it.box.top.isFinite() && it.box.right.isFinite() && it.box.bottom.isFinite() && it.box.right > it.box.left && it.box.bottom > it.box.top }
            .maxByOrNull { (it.box.right - it.box.left) * (it.box.bottom - it.box.top) }
        val found = chosen != null
        val targetX = chosen?.box?.centerX?.coerceIn(0f, 1f) ?: 0.5f
        val targetY = chosen?.box?.centerY?.coerceIn(0f, 1f) ?: 0.5f
        val smoothedX = if (previous == null) targetX else previous.centerX + (targetX - previous.centerX) * smoothing
        val smoothedY = if (previous == null) targetY else previous.centerY + (targetY - previous.centerY) * smoothing
        // Keep the detected face within the target crop with a tunable safety margin when possible.
        val box = chosen?.box
        val xMargin = if (box == null) 0f else (box.right - box.left) * margin
        val yMargin = if (box == null) 0f else (box.bottom - box.top) * margin
        val minX = if (box == null) cropW / 2f else box.right + xMargin - cropW / 2f
        val maxX = if (box == null) 1f - cropW / 2f else box.left - xMargin + cropW / 2f
        val minY = if (box == null) cropH / 2f else box.bottom + yMargin - cropH / 2f
        val maxY = if (box == null) 1f - cropH / 2f else box.top - yMargin + cropH / 2f
        val centerX = clampCenter(smoothedX, minX, maxX, cropW)
        val centerY = clampCenter(smoothedY, minY, maxY, cropH)
        return ReframeFrame(0L, NormalizedCrop(centerX - cropW / 2f, centerY - cropH / 2f,
            centerX + cropW / 2f, centerY + cropH / 2f), found)
    }

    /** Reframe a timestamped sample stream; an empty face list means no face was detected for that sample. */
    fun track(
        sourceWidth: Int,
        sourceHeight: Int,
        outputAspectRatio: Float,
        samples: List<Pair<Long, List<FaceDetection>>>,
        margin: Float = 0.35f,
        smoothing: Float = 0.35f
    ): List<ReframeFrame> {
        require(samples.zipWithNext().all { it.first.first <= it.second.first }) { "Sample timestamps must be monotonic." }
        var prev: NormalizedCrop? = null
        var previousFaceCenter: Pair<Float, Float>? = null
        return samples.map { (time, faces) ->
            val validFaces = faces.filter(::hasUsableBox)
            // Largest face initializes tracking; nearest-center matching reduces identity jumps when
            // two people cross or one face becomes temporarily smaller in frame.
            val selected = if (previousFaceCenter == null) validFaces.maxByOrNull { faceArea(it) }
            else validFaces.minByOrNull { face ->
                val dx = face.box.centerX - previousFaceCenter!!.first
                val dy = face.box.centerY - previousFaceCenter!!.second
                dx * dx + dy * dy
            }
            if (selected != null) previousFaceCenter = selected.box.centerX to selected.box.centerY
            val frame = cropFor(sourceWidth, sourceHeight, outputAspectRatio, selected?.let(::listOf).orEmpty(), margin, prev, smoothing)
            prev = frame.crop
            frame.copy(timestampMs = time)
        }
    }

    private fun hasUsableBox(face: FaceDetection): Boolean {
        val box = face.box
        if (!box.left.isFinite() || !box.top.isFinite() || !box.right.isFinite() || !box.bottom.isFinite()) return false
        val width = box.right - box.left
        val height = box.bottom - box.top
        return width > 0f && height > 0f && (width * height).isFinite()
    }

    private fun faceArea(face: FaceDetection) =
        (face.box.right - face.box.left) * (face.box.bottom - face.box.top)

    private fun clampCenter(value: Float, lower: Float, upper: Float, size: Float): Float {
        val minCenter = size / 2f
        val maxCenter = 1f - minCenter
        if (lower <= upper) {
            val lo = max(minCenter, lower).coerceAtMost(maxCenter)
            val hi = upper.coerceAtLeast(minCenter).coerceAtMost(maxCenter)
            if (lo <= hi) return value.coerceIn(lo, hi)
        }
        // Face does not fit inside the fixed aspect crop; prioritize keeping its center visible.
        return value.coerceIn(minCenter, maxCenter)
    }
}
