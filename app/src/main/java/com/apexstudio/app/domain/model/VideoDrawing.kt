package com.apexstudio.app.domain.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.apexstudio.app.domain.model.timeline.BlendMode
import com.apexstudio.app.domain.model.timeline.DrawingLayer
import com.apexstudio.app.domain.model.timeline.Keyframe
import com.apexstudio.app.domain.model.timeline.Stroke
import com.apexstudio.app.domain.model.timeline.Tool
import com.apexstudio.app.domain.model.timeline.Transform
import com.apexstudio.app.domain.model.timeline.XY
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
    val opacity: Float = 1f,
    /** Typed drawing timing and animation, defaulted for old JSON. */
    val startMs: Long = 0L,
    val endMs: Long? = null,
    val alphaKeyframes: List<Keyframe<Float>> = emptyList(),
    val transform: Transform = Transform()
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
 * otherwise it is visible only over the identified source clip. Appending these
 * defaulted fields keeps pre-existing JSON projects readable.
 */
@Serializable
data class VideoDrawingLayer(
    val id: String = UUID.randomUUID().toString(),
    val clipId: String? = null,
    val strokes: List<VideoDrawingStroke> = emptyList(),
    val blendMode: BlendMode = BlendMode.NORMAL,
    val globalAlpha: Float = 1f,
    val globalAlphaKeyframes: List<Keyframe<Float>> = emptyList()
) {
    fun sanitized(): VideoDrawingLayer = copy(
        strokes = strokes.map(VideoDrawingStroke::sanitized),
        globalAlpha = (if (globalAlpha.isFinite()) globalAlpha else 1f).coerceIn(0f, 1f)
    )

    fun append(stroke: VideoDrawingStroke): VideoDrawingLayer =
        copy(strokes = strokes + stroke.sanitized())

    fun undo(): VideoDrawingLayer =
        if (strokes.isEmpty()) this else copy(strokes = strokes.dropLast(1))
}

/** Field-level legacy -> typed conversion; no legacy objects are embedded. */
fun VideoDrawingLayer.toTypedDrawingLayer(ownerClipId: String? = clipId): DrawingLayer = DrawingLayer(
    id = id,
    ownerClipId = ownerClipId,
    strokes = strokes.map { stroke ->
        Stroke(
            points = stroke.points.map { Offset(it.x, it.y) },
            color = Color(stroke.colorArgb.toInt()),
            width = stroke.width,
            tool = if (stroke.tool == VideoDrawingTool.ERASER) Tool.ERASER else Tool.PEN,
            startMs = stroke.startMs,
            endMs = stroke.endMs,
            alpha = stroke.opacity,
            alphaKeyframes = stroke.alphaKeyframes,
            transform = stroke.transform
        )
    },
    blendMode = blendMode,
    globalAlpha = globalAlpha,
    globalAlphaKeyframes = globalAlphaKeyframes
)

/** Field-level typed -> legacy conversion, preserving the supplied stable layer id. */
fun DrawingLayer.toLegacyDrawingLayer(existing: VideoDrawingLayer? = null): VideoDrawingLayer = VideoDrawingLayer(
    id = id,
    clipId = ownerClipId ?: existing?.clipId,
    strokes = strokes.map { stroke ->
        VideoDrawingStroke(
            points = stroke.points.map { VideoDrawingPoint(it.x, it.y) },
            colorArgb = stroke.color.toArgb().toLong() and 0xFFFFFFFFL,
            width = stroke.width,
            tool = if (stroke.tool == Tool.ERASER) VideoDrawingTool.ERASER else VideoDrawingTool.BRUSH,
            opacity = stroke.alpha,
            startMs = stroke.startMs,
            endMs = stroke.endMs,
            alphaKeyframes = stroke.alphaKeyframes,
            transform = stroke.transform
        )
    },
    blendMode = blendMode,
    globalAlpha = globalAlpha,
    globalAlphaKeyframes = globalAlphaKeyframes
)

/** Project-wide artwork plus marks scoped to the currently displayed source clip. */
fun visibleVideoDrawingLayers(
    layers: List<VideoDrawingLayer>,
    clipId: String?
): List<VideoDrawingLayer> = layers.filter { it.clipId == null || it.clipId == clipId }
