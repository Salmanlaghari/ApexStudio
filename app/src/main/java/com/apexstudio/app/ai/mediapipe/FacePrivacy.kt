package com.apexstudio.app.ai.mediapipe

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

/** Face blur operations consume actual Face Landmarker detections; they never infer a face from image heuristics. */
object FacePrivacy {
    /**
     * Return a distinct pixelated copy with face-mesh convex hull interiors pixelated. [blockSize] is
     * in source pixels; use a larger value for stronger obfuscation. Detections are normalized to [source].
     */
    fun pixelateFaces(source: Bitmap, detections: List<FaceDetection>, blockSize: Int = 14): Bitmap {
        require(!source.isRecycled) { "Source bitmap has been recycled" }
        require(blockSize >= 2) { "blockSize must be at least 2" }
        val width = source.width
        val height = source.height
        val original = IntArray(width * height)
        source.getPixels(original, 0, width, 0, 0, width, height)
        val output = original.copyOf()
        for (face in detections) {
            val polygon = convexHull(face.landmarks.map {
                Point(it.x.coerceIn(0f, 1f) * width, it.y.coerceIn(0f, 1f) * height)
            })
            if (polygon.size < 3) continue
            val bounds = Bounds(
                max(0, polygon.minOf { it.x.toInt() } - 1),
                max(0, polygon.minOf { it.y.toInt() } - 1),
                min(width, polygon.maxOf { it.x.toInt() } + 2),
                min(height, polygon.maxOf { it.y.toInt() } + 2),
            )
            if (bounds.left >= bounds.right || bounds.top >= bounds.bottom) continue
            val startX = bounds.left / blockSize * blockSize
            val startY = bounds.top / blockSize * blockSize
            for (by in startY until bounds.bottom step blockSize) {
                for (bx in startX until bounds.right step blockSize) {
                    val sampleX = min(width - 1, bx + blockSize / 2)
                    val sampleY = min(height - 1, by + blockSize / 2)
                    val sampledColor = original[sampleY * width + sampleX]
                    val xEnd = min(width, bx + blockSize)
                    val yEnd = min(height, by + blockSize)
                    for (y in max(bounds.top, by) until yEnd) {
                        for (x in max(bounds.left, bx) until xEnd) {
                            if (contains(polygon, x + 0.5f, y + 0.5f)) output[y * width + x] = sampledColor
                        }
                    }
                }
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.setPixels(output, 0, width, 0, 0, width, height)
        }
    }

    private data class Point(val x: Float, val y: Float)
    private data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private fun convexHull(points: List<Point>): List<Point> {
        val distinct = points.distinctBy { it.x to it.y }.sortedWith(compareBy<Point> { it.x }.thenBy { it.y })
        if (distinct.size < 3) return distinct
        fun cross(o: Point, a: Point, b: Point) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        val lower = mutableListOf<Point>()
        for (point in distinct) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), point) <= 0f) lower.removeAt(lower.lastIndex)
            lower += point
        }
        val upper = mutableListOf<Point>()
        for (point in distinct.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), point) <= 0f) upper.removeAt(upper.lastIndex)
            upper += point
        }
        return lower.dropLast(1) + upper.dropLast(1)
    }

    private fun contains(polygon: List<Point>, x: Float, y: Float): Boolean {
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[j]
            if ((a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x) inside = !inside
            j = i
        }
        return inside
    }
}

/** Detection-backed face track; identity is ephemeral, local to one tracker instance. */
data class TrackedFace(val id: Long, val detection: FaceDetection)

/**
 * Associates detections from each real inference frame to prior boxes by greedy IoU matching and smooths
 * box/landmark jitter. This is temporal association, not a substitute for a detector; feed every frame's
 * MediaPipe results, including empty lists when no face was found.
 */
class FaceTrackSmoother(
    private val iouThreshold: Float = 0.2f,
    private val smoothing: Float = 0.65f,
    private val maxGapMs: Long = 700L,
) {
    private data class Track(val id: Long, var detection: FaceDetection, var lastSeenMs: Long)
    private val tracks = mutableListOf<Track>()
    private var nextId = 1L
    private var previousTimestampMs = Long.MIN_VALUE

    init {
        require(iouThreshold in 0f..1f)
        require(smoothing in 0f..1f)
        require(maxGapMs >= 0)
    }

    @Synchronized
    fun update(timestampMs: Long, detections: List<FaceDetection>): List<TrackedFace> {
        require(timestampMs >= previousTimestampMs) { "Face track timestamps must be non-decreasing" }
        previousTimestampMs = timestampMs
        tracks.removeAll { timestampMs - it.lastSeenMs > maxGapMs }
        val candidates = buildList {
            tracks.forEachIndexed { ti, track -> detections.forEachIndexed { di, detection ->
                val score = intersectionOverUnion(track.detection.box, detection.box)
                if (score >= iouThreshold) add(Triple(score, ti, di))
            } }
        }.sortedByDescending { it.first }
        val usedTracks = mutableSetOf<Int>()
        val usedDetections = mutableSetOf<Int>()
        val assignments = mutableMapOf<Int, Track>()
        for ((_, trackIndex, detectionIndex) in candidates) {
            if (trackIndex in usedTracks || detectionIndex in usedDetections) continue
            usedTracks += trackIndex
            usedDetections += detectionIndex
            val track = tracks[trackIndex]
            track.detection = interpolate(track.detection, detections[detectionIndex], smoothing)
            track.lastSeenMs = timestampMs
            assignments[detectionIndex] = track
        }
        detections.forEachIndexed { index, detection ->
            if (index !in assignments) {
                val track = Track(nextId++, detection, timestampMs)
                tracks += track
                assignments[index] = track
            }
        }
        return detections.indices.map { index -> TrackedFace(assignments.getValue(index).id, assignments.getValue(index).detection) }
    }

    @Synchronized
    fun reset() {
        tracks.clear()
        nextId = 1L
        previousTimestampMs = Long.MIN_VALUE
    }

    private fun intersectionOverUnion(a: NormalizedBox, b: NormalizedBox): Float {
        val iw = max(0f, min(a.right, b.right) - max(a.left, b.left))
        val ih = max(0f, min(a.bottom, b.bottom) - max(a.top, b.top))
        val intersection = iw * ih
        val union = boxArea(a) + boxArea(b) - intersection
        return if (union <= 0f) 0f else intersection / union
    }

    private fun boxArea(box: NormalizedBox) = max(0f, box.right - box.left) * max(0f, box.bottom - box.top)

    private fun interpolate(previous: FaceDetection, current: FaceDetection, currentWeight: Float): FaceDetection {
        val old = previous.landmarks
        val now = current.landmarks
        val points = if (old.size == now.size) old.indices.map { index ->
            val p = old[index]
            val n = now[index]
            NormalizedPoint(
                p.x * (1f - currentWeight) + n.x * currentWeight,
                p.y * (1f - currentWeight) + n.y * currentWeight,
                p.z * (1f - currentWeight) + n.z * currentWeight,
                n.visibility,
            )
        } else now
        val a = previous.box
        val b = current.box
        val box = NormalizedBox(
            a.left * (1f - currentWeight) + b.left * currentWeight,
            a.top * (1f - currentWeight) + b.top * currentWeight,
            a.right * (1f - currentWeight) + b.right * currentWeight,
            a.bottom * (1f - currentWeight) + b.bottom * currentWeight,
        )
        return FaceDetection(box, points)
    }
}
