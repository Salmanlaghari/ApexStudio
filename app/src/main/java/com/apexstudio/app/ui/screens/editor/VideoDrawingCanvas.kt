package com.apexstudio.app.ui.screens.editor

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import com.apexstudio.app.domain.model.VideoDrawingLayer
import com.apexstudio.app.domain.model.VideoDrawingPoint
import com.apexstudio.app.domain.model.VideoDrawingStroke
import com.apexstudio.app.domain.model.VideoDrawingTool
import com.apexstudio.app.domain.model.visibleVideoDrawingLayers
import kotlin.math.min

/**
 * Raster renderer shared by preview/export integrations. The returned bitmap is
 * transparent; eraser strokes clear prior paint in this layer rather than the video.
 * Callers should cache the result until the drawing list or output dimensions change.
 */
object VideoDrawingRenderer {
    fun renderBitmap(
        width: Int,
        height: Int,
        layers: List<VideoDrawingLayer>,
        clipId: String? = null
    ): Bitmap {
        require(width > 0 && height > 0) { "Drawing bitmap dimensions must be positive" }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        val frameMin = min(width, height).toFloat()
        visibleVideoDrawingLayers(layers, clipId).asSequence()
            .flatMap { it.strokes.asSequence() }.forEach { raw ->
                val stroke = raw.sanitized()
                if (stroke.points.isEmpty()) return@forEach
                val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                    style = AndroidPaint.Style.STROKE
                    strokeCap = AndroidPaint.Cap.ROUND
                    strokeJoin = AndroidPaint.Join.ROUND
                    strokeWidth = stroke.width * frameMin
                    if (stroke.tool == VideoDrawingTool.ERASER) {
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                    } else {
                        color = stroke.colorArgb.toInt()
                        alpha = (alpha * stroke.opacity).toInt().coerceIn(0, 255)
                    }
                }
                val path = AndroidPath()
                val first = stroke.points.first().sanitized()
                path.moveTo(first.x * width, first.y * height)
                stroke.points.drop(1).forEach { point ->
                    val p = point.sanitized()
                    path.lineTo(p.x * width, p.y * height)
                }
                if (stroke.points.size == 1) {
                    paint.style = AndroidPaint.Style.FILL
                    canvas.drawCircle(first.x * width, first.y * height, paint.strokeWidth / 2f, paint)
                } else {
                    canvas.drawPath(path, paint)
                }
                paint.xfermode = null
            }
        return bitmap
    }
}

/**
 * Transparent on-video drawing surface. Coordinates are normalized against the
 * displayed video frame. A parent should place it exactly over the video image,
 * disable it while ordinary preview gestures are active, and persist the emitted
 * [VideoDrawingLayer] (e.g. in Project.videoDrawings).
 */
@Composable
fun VideoDrawingCanvas(
    layer: VideoDrawingLayer,
    onLayerChange: (VideoDrawingLayer) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tool: VideoDrawingTool = VideoDrawingTool.BRUSH,
    colorArgb: Long = 0xFFFF3B82L,
    brushWidth: Float = VideoDrawingStroke.DEFAULT_WIDTH,
    opacity: Float = 1f
) {
    var draft by remember(layer.id) { mutableStateOf<List<VideoDrawingPoint>>(emptyList()) }
    val latestLayer = rememberUpdatedState(layer)
    val latestOnLayerChange = rememberUpdatedState(onLayerChange)
    val normalizedWidth = (if (brushWidth.isFinite()) brushWidth else VideoDrawingStroke.DEFAULT_WIDTH)
        .coerceIn(VideoDrawingStroke.MIN_WIDTH, VideoDrawingStroke.MAX_WIDTH)
    val commit: (List<VideoDrawingPoint>) -> Unit = { points ->
        if (points.isNotEmpty()) {
            latestOnLayerChange.value(
                latestLayer.value.append(VideoDrawingStroke(points, colorArgb, normalizedWidth, tool, opacity))
            )
        }
        draft = emptyList()
    }

    Canvas(
        modifier = modifier
            // Offscreen composition is essential: BlendMode.Clear removes paint
            // from this transparent artwork buffer, never the underlying video.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .pointerInput(enabled, layer.id, normalizedWidth, tool, colorArgb, opacity) {
                if (enabled) detectTapGestures { position ->
                    commit(listOf(position.toNormalizedPoint(size.width.toFloat(), size.height.toFloat())))
                }
            }
            .pointerInput(enabled, layer.id, normalizedWidth, tool, colorArgb, opacity) {
                if (enabled) detectDragGestures(
                    onDragStart = { position ->
                        draft = listOf(position.toNormalizedPoint(size.width.toFloat(), size.height.toFloat()))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val p = change.position.toNormalizedPoint(size.width.toFloat(), size.height.toFloat())
                        if (draft.lastOrNull() != p) draft = draft + p
                    },
                    onDragEnd = { commit(draft) },
                    onDragCancel = { draft = emptyList() }
                )
            }
    ) {
        drawDrawingLayers(listOf(layer), size.width, size.height)
        if (draft.isNotEmpty()) {
            drawOneStroke(
                VideoDrawingStroke(draft, colorArgb, normalizedWidth, tool, opacity),
                size.width,
                size.height
            )
        }
    }
}

private fun Offset.toNormalizedPoint(width: Float, height: Float): VideoDrawingPoint = VideoDrawingPoint(
    (x / width.coerceAtLeast(1f)).coerceIn(0f, 1f),
    (y / height.coerceAtLeast(1f)).coerceIn(0f, 1f)
)

/** Draw all layers in order; clears only the transparent art surface. */
@Composable
fun VideoDrawingArtwork(
    layers: List<VideoDrawingLayer>,
    clipId: String? = null,
    modifier: Modifier = Modifier
) {
    Canvas(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val visible = visibleVideoDrawingLayers(layers, clipId)
        drawDrawingLayers(visible, size.width, size.height)
    }
}

private fun DrawScope.drawDrawingLayers(layers: List<VideoDrawingLayer>, width: Float, height: Float) {
    layers.forEach { layer -> layer.strokes.forEach { drawOneStroke(it, width, height) } }
}

private fun DrawScope.drawOneStroke(stroke: VideoDrawingStroke, width: Float, height: Float) {
    val clean = stroke.sanitized()
    if (clean.points.isEmpty()) return
    val points = clean.points.map { Offset(it.x * width, it.y * height) }
    val brushPx = clean.width * min(width, height)
    val blend = if (clean.tool == VideoDrawingTool.ERASER) BlendMode.Clear else BlendMode.SrcOver
    val color = Color(clean.colorArgb.toInt()).copy(alpha = clean.opacity)
    if (points.size == 1) {
        drawCircle(color, radius = brushPx / 2f, center = points.first(), blendMode = blend)
    } else {
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = brushPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
            blendMode = blend
        )
    }
}
