package com.apexstudio.app.ai.mediapipe

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.hypot

/** Named index connections from the MediaPipe 33-landmark pose and 21-landmark hand conventions. */
object LandmarkConnections {
    val POSE: List<Pair<Int, Int>> = listOf(
        0 to 1, 1 to 2, 2 to 3, 3 to 7, 0 to 4, 4 to 5, 5 to 6, 6 to 8,
        9 to 10, 11 to 12, 11 to 13, 13 to 15, 15 to 17, 15 to 19, 15 to 21, 17 to 19,
        12 to 14, 14 to 16, 16 to 18, 16 to 20, 16 to 22, 18 to 20,
        11 to 23, 12 to 24, 23 to 24, 23 to 25, 25 to 27, 27 to 29, 29 to 31, 27 to 31,
        24 to 26, 26 to 28, 28 to 30, 30 to 32, 28 to 32,
    )
    val HAND: List<Pair<Int, Int>> = listOf(
        0 to 1, 1 to 2, 2 to 3, 3 to 4,
        0 to 5, 5 to 6, 6 to 7, 7 to 8,
        5 to 9, 9 to 10, 10 to 11, 11 to 12,
        9 to 13, 13 to 14, 14 to 15, 15 to 16,
        13 to 17, 17 to 18, 18 to 19, 19 to 20,
        0 to 17,
    )
}

/** Draws landmark overlays to the caller's canvas. Coordinates use the input bitmap's normalized space. */
object LandmarkOverlayRenderer {
    fun drawPose(canvas: Canvas, width: Int, height: Int, pose: PoseDetection, color: Int, lineWidthPx: Float = 3f) {
        draw(canvas, width, height, pose.landmarks, LandmarkConnections.POSE, color, lineWidthPx)
    }

    fun drawHand(canvas: Canvas, width: Int, height: Int, hand: HandDetection, color: Int, lineWidthPx: Float = 3f) {
        draw(canvas, width, height, hand.landmarks, LandmarkConnections.HAND, color, lineWidthPx)
    }

    private fun draw(canvas: Canvas, width: Int, height: Int, points: List<NormalizedPoint>,
                     edges: List<Pair<Int, Int>>, color: Int, lineWidthPx: Float) {
        require(width > 0 && height > 0)
        require(lineWidthPx > 0f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = lineWidthPx
            strokeCap = Paint.Cap.ROUND
        }
        for ((from, to) in edges) {
            if (from !in points.indices || to !in points.indices) continue
            val a = points[from]
            val b = points[to]
            if ((a.visibility ?: 1f) < 0.35f || (b.visibility ?: 1f) < 0.35f) continue
            canvas.drawLine(a.x * width, a.y * height, b.x * width, b.y * height, paint)
        }
        paint.style = Paint.Style.FILL
        val radius = lineWidthPx * 1.35f
        points.forEach { point ->
            if ((point.visibility ?: 1f) >= 0.35f) canvas.drawCircle(point.x * width, point.y * height, radius, paint)
        }
    }
}

/**
 * Deterministic geometric gesture primitives over the 21 landmark coordinates returned by Hand Landmarker.
 * These are pose-derived cues, not an additional learned gesture classifier; uncertain geometry is UNKNOWN.
 */
object GesturePrimitives {
    enum class Gesture { OPEN_PALM, FIST, POINTING, PINCH, UNKNOWN }

    fun classify(hand: HandDetection): Gesture {
        if (hand.landmarks.size < 21) return Gesture.UNKNOWN
        val thumb = isFingerExtended(hand, Finger.THUMB)
        val index = isFingerExtended(hand, Finger.INDEX)
        val middle = isFingerExtended(hand, Finger.MIDDLE)
        val ring = isFingerExtended(hand, Finger.RING)
        val little = isFingerExtended(hand, Finger.LITTLE)
        val extended = listOf(thumb, index, middle, ring, little)
        val wrist = hand.landmarks[0]
        val thumbTip = hand.landmarks[4]
        val indexTip = hand.landmarks[8]
        val pinchDistance = distance(thumbTip, indexTip)
        val palmScale = maxOf(distance(wrist, hand.landmarks[9]), 1e-4f)
        if (pinchDistance / palmScale < 0.28f && !middle && !ring && !little) return Gesture.PINCH
        if (extended.all { it }) return Gesture.OPEN_PALM
        if (extended.none { it }) return Gesture.FIST
        if (index && !middle && !ring && !little) return Gesture.POINTING
        return Gesture.UNKNOWN
    }

    fun isFingerExtended(hand: HandDetection, finger: Finger): Boolean {
        if (hand.landmarks.size < 21) return false
        val tipIndex = when (finger) { Finger.THUMB -> 4; Finger.INDEX -> 8; Finger.MIDDLE -> 12; Finger.RING -> 16; Finger.LITTLE -> 20 }
        val pipIndex = when (finger) { Finger.THUMB -> 3; Finger.INDEX -> 6; Finger.MIDDLE -> 10; Finger.RING -> 14; Finger.LITTLE -> 18 }
        val wrist = hand.landmarks[0]
        val palmScale = maxOf(distance(wrist, hand.landmarks[9]), 1e-4f)
        // Relative Euclidean distances support rotated hands and handedness without assuming screen-up is distal.
        return distance(wrist, hand.landmarks[tipIndex]) > distance(wrist, hand.landmarks[pipIndex]) * 1.12f &&
            distance(wrist, hand.landmarks[tipIndex]) > palmScale * 0.72f
    }

    enum class Finger { THUMB, INDEX, MIDDLE, RING, LITTLE }

    private fun distance(a: NormalizedPoint, b: NormalizedPoint): Float = hypot(a.x - b.x, a.y - b.y)
}
