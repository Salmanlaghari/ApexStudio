package com.apexstudio.app.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

/** A point in the video frame, stored in normalized 0..1 coordinates. */
@Serializable
data class VideoDrawingPoint(
    val x: Float,
    val y: Float
) {
    fun sanitized(): VideoDrawingPoint = VideoDrawingPoint(
        x = (if (x.isFinite()) x else 0f).coerceIn(0f, 1f),
        y = (if (y.isFinite()) y else 0f).coerceIn(0f, 1f)
    )
}

/** The selected drawing instrument. Eraser removes artwork, never video pixels. */
@Serializable
enum class VideoDrawingTool { BRUSH, ERASER }

/** One editable freehand mark. Width is a fraction of the video frame's shorter edge. */
@Serializable
data class VideoDrawingStroke(
    val points: List<VideoDrawingPoint> = emptyList(),
    /** ARGB color. Ignored by eraser strokes. */
    val colorArgb: Long = 0xFFFF3B82L,
    /** Normalized width, clamped during rendering to protect old/malformed projects. */
    val width: Float = 0.008f,
    val tool: VideoDrawingTool = VideoDrawingTool.BRUSH,
    val opacity: Float = 1f
) {
    fun sanitized(): VideoDrawingStroke = copy(
        points = points.map(VideoDrawingPoint::sanitized),
        width = (if (width.isFinite()) width else DEFAULT_WIDTH).coerceIn(MIN_WIDTH, MAX_WIDTH),
        opacity = (if (opacity.isFinite()) opacity else 1f).coerceIn(0f, 1f)
    )

    companion object {
        const val DEFAULT_WIDTH = 0.008f
        const val MIN_WIDTH = 0.001f
        const val MAX_WIDTH = 0.12f
    }
}

/**
 * Project-persisted paint layer. A null clipId means the drawing spans the project;
 * otherwise it is visible only over the identified source clip. Appending this
 * defaulted project field keeps pre-existing JSON projects readable.
 */
@Serializable
data class VideoDrawingLayer(
    val id: String = UUID.randomUUID().toString(),
    val clipId: String? = null,
    val strokes: List<VideoDrawingStroke> = emptyList()
) {
    fun sanitized(): VideoDrawingLayer = copy(strokes = strokes.map(VideoDrawingStroke::sanitized))

    fun append(stroke: VideoDrawingStroke): VideoDrawingLayer =
        copy(strokes = strokes + stroke.sanitized())

    fun undo(): VideoDrawingLayer =
        if (strokes.isEmpty()) this else copy(strokes = strokes.dropLast(1))
}

/** Project-wide artwork plus marks scoped to the currently displayed source clip. */
fun visibleVideoDrawingLayers(
    layers: List<VideoDrawingLayer>,
    clipId: String?
): List<VideoDrawingLayer> = layers.filter { it.clipId == null || it.clipId == clipId }
