package com.apexstudio.app.ui.screens.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import com.flaviofaria.kenburnsview.KenBurnsView
import com.flaviofaria.kenburnsview.RandomTransitionGenerator
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.apexstudio.app.data.filter.LutBitmapCache
import com.apexstudio.app.data.filter.LutFilterEngine
import com.apexstudio.app.data.filter.LutTexture
import com.apexstudio.app.data.photoedit.PhotoEditRenderer
import com.apexstudio.app.domain.model.PhotoCropRect
import com.apexstudio.app.domain.model.PhotoEditSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Live preview for an IMAGE timeline clip with its photo edits applied.
 *
 * Renders the photo through the exact same CPU pipeline the export
 * uses ([PhotoEditRenderer.applyEdits]: rotate/flip → crop → sharpen →
 * adjustments → real-LUT filter), so what the user sees is what bakes
 * into the exported MP4.
 *
 * When [cropActive] is true the crop itself is *not* baked (the full
 * rotated photo is shown) and a dim + corner-handle overlay lets the
 * user drag the crop window directly on the preview.
 */
@Composable
fun PhotoClipPreview(
    uri: String,
    settings: PhotoEditSettings,
    cropActive: Boolean,
    onCropChange: (PhotoCropRect) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // Raw decoded photo (EXIF applied, downscaled for preview).
    var rawBitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    // Edited bitmap (declared early so the uri effect can check it).
    var edited by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        val old = rawBitmap
        val fresh = PhotoEditRenderer.loadBitmap(context, uri, maxDim = 1280)
        rawBitmap = fresh
        // Recycle the superseded raw only if the pipeline isn't still
        // reading it (it isn't — this effect runs before the pipeline
        // effect sees the new value) and it isn't the displayed frame.
        if (old != null && old != fresh && old != edited) {
            try { if (!old.isRecycled) old.recycle() } catch (_: Exception) {}
        }
    }

    // Real LUT texture for the selected filter (loaded once per filter).
    var lutTexture by remember { mutableStateOf<LutTexture?>(null) }
    var lutFilterId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(settings.filterId) {
        val id = settings.filterId
        lutFilterId = id
        lutTexture = if (id == null) {
            null
        } else {
            val preset = LutFilterEngine(context).manifest.presetById(id)
            preset?.let { LutBitmapCache.getOrLoad(context, it) }
        }
    }

    // In crop mode the crop is withheld so the handles operate on the
    // full (rotated) photo.
    val effectiveSettings = if (cropActive) settings.copy(crop = null) else settings
    val activeLut = lutTexture.takeIf { lutFilterId == settings.filterId && settings.filterId != null }
    LaunchedEffect(rawBitmap, effectiveSettings, activeLut) {
        val src = rawBitmap
        if (src == null) {
            val oldEdited = edited
            edited = null
            try { if (oldEdited != null && !oldEdited.isRecycled) oldEdited.recycle() } catch (_: Exception) {}
        } else {
            val next = withContext(Dispatchers.Default) {
                try {
                    PhotoEditRenderer.applyEdits(src, effectiveSettings, activeLut)
                } catch (e: Exception) {
                    null
                }
            }
            // Re-read current raw: if uri changed mid-processing, src is stale.
            val currentRaw = rawBitmap
            if (currentRaw == src) {
                val oldEdited = edited
                edited = next
                // Recycle the previous edited frame if it isn't the src
                // (applyEdits may return src unchanged when no edits apply)
                // and isn't the new frame.
                try {
                    if (oldEdited != null && oldEdited != next && oldEdited != src &&
                        !oldEdited.isRecycled
                    ) oldEdited.recycle()
                } catch (_: Exception) {}
            } else {
                // Stale: drop the result. Never recycle src here — it may be
                // the bitmap the Canvas is still drawing (edited == src when
                // no edits were applied). The uri-change effect above owns
                // recycling superseded raws.
                try { if (next != null && next != src && !next.isRecycled) next.recycle() } catch (_: Exception) {}
            }
        }
    }

    // Photo display rect (Fit, centred) derived from layout size — the
    // single source of truth for both drawing and drag hit-testing.
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val bmp = edited
    val photoRect: Rect? = remember(bmp, canvasSize) {
        if (bmp == null || canvasSize == IntSize.Zero) {
            null
        } else {
            val scale = minOf(
                canvasSize.width.toFloat() / bmp.width,
                canvasSize.height.toFloat() / bmp.height
            )
            val dw = bmp.width * scale
            val dh = bmp.height * scale
            val dx = (canvasSize.width - dw) / 2f
            val dy = (canvasSize.height - dh) / 2f
            Rect(dx, dy, dx + dw, dy + dh)
        }
    }

    val handleRadiusPx = with(density) { 13.dp.toPx() }
    val cropRect = settings.crop ?: PhotoCropRect.full()
    val lockedAspect = PhotoEditSettings.aspectRatioForPreset(settings.cropAspectPreset)
    var dragCorner by remember { mutableStateOf<PhotoEditRenderer.CropCorner?>(null) }

    // Phase 4: Ken Burns motion preview — the real KenBurnsView
    // (Apache-2.0) pans/zooms the edited bitmap. Crop interactions stay
    // on the static canvas.
    if (settings.kenBurns && !cropActive && bmp != null) {
        val kbBitmap = bmp
        AndroidView(
            factory = { ctx ->
                KenBurnsView(ctx).apply {
                    setTransitionGenerator(
                        RandomTransitionGenerator(
                            8000L,
                            android.view.animation.AccelerateDecelerateInterpolator()
                        )
                    )
                }
            },
            update = { view -> view.setImageBitmap(kbBitmap) },
            modifier = modifier.fillMaxSize()
        )
    } else {
        Canvas(
            modifier = modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(cropActive, photoRect) {
                    if (!cropActive) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragCorner = hitTestCorner(offset, cropRect, photoRect, handleRadiusPx * 1.6f)
                        },
                        onDragEnd = { dragCorner = null },
                        onDragCancel = { dragCorner = null },
                        onDrag = { change, _ ->
                            val corner = dragCorner ?: return@detectDragGestures
                            val rect = photoRect ?: return@detectDragGestures
                            val nx = ((change.position.x - rect.left) / rect.width).coerceIn(-0.2f, 1.2f)
                            val ny = ((change.position.y - rect.top) / rect.height).coerceIn(-0.2f, 1.2f)
                            onCropChange(
                                PhotoEditRenderer.moveCorner(cropRect, corner, nx, ny, lockedAspect)
                            )
                            change.consume()
                        }
                    )
                }
        ) {
            val bitmap = bmp ?: return@Canvas
            val rect = photoRect ?: return@Canvas

            drawImage(
                image = bitmap.asImageBitmap(),
                dstOffset = IntOffset(rect.left.toInt(), rect.top.toInt()),
                dstSize = IntSize(rect.width.toInt(), rect.height.toInt())
            )

            if (cropActive) {
                // Crop window in display coords.
                val cl = rect.left + cropRect.left * rect.width
                val ct = rect.top + cropRect.top * rect.height
                val cr = rect.left + cropRect.right * rect.width
                val cb = rect.top + cropRect.bottom * rect.height
                val dim = Color.Black.copy(alpha = 0.55f)
                // Dim everything outside the crop window.
                drawRect(dim, Offset(0f, 0f), Size(size.width, ct))
                drawRect(dim, Offset(0f, cb), Size(size.width, size.height - cb))
                drawRect(dim, Offset(0f, ct), Size(cl, cb - ct))
                drawRect(dim, Offset(cr, ct), Size(size.width - cr, cb - ct))
                // Window border + rule-of-thirds grid.
                drawRect(
                    Color.White, Offset(cl, ct), Size(cr - cl, cb - ct),
                    style = Stroke(width = 2.dp.toPx())
                )
                val gw = (cr - cl) / 3f
                val gh = (cb - ct) / 3f
                for (i in 1..2) {
                    drawLine(
                        Color.White.copy(alpha = 0.45f),
                        Offset(cl + gw * i, ct), Offset(cl + gw * i, cb),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        Color.White.copy(alpha = 0.45f),
                        Offset(cl, ct + gh * i), Offset(cr, ct + gh * i),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                // Corner handles.
                val active = dragCorner
                listOf(
                    PhotoEditRenderer.CropCorner.TOP_LEFT to Offset(cl, ct),
                    PhotoEditRenderer.CropCorner.TOP_RIGHT to Offset(cr, ct),
                    PhotoEditRenderer.CropCorner.BOTTOM_LEFT to Offset(cl, cb),
                    PhotoEditRenderer.CropCorner.BOTTOM_RIGHT to Offset(cr, cb)
                ).forEach { (corner, pos) ->
                    val highlight = active == corner
                    drawRoundRect(
                        color = if (highlight) Color(0xFF00F0FF) else Color.White,
                        topLeft = Offset(pos.x - handleRadiusPx, pos.y - handleRadiusPx),
                        size = Size(handleRadiusPx * 2f, handleRadiusPx * 2f),
                        cornerRadius = CornerRadius(handleRadiusPx * 0.45f)
                    )
                }
            }
        }
    }
}

private fun hitTestCorner(
    offset: Offset,
    crop: PhotoCropRect,
    photoRect: Rect?,
    slop: Float
): PhotoEditRenderer.CropCorner? {
    val rect = photoRect ?: return null
    val points = listOf(
        PhotoEditRenderer.CropCorner.TOP_LEFT to
                Offset(rect.left + crop.left * rect.width, rect.top + crop.top * rect.height),
        PhotoEditRenderer.CropCorner.TOP_RIGHT to
                Offset(rect.left + crop.right * rect.width, rect.top + crop.top * rect.height),
        PhotoEditRenderer.CropCorner.BOTTOM_LEFT to
                Offset(rect.left + crop.left * rect.width, rect.top + crop.bottom * rect.height),
        PhotoEditRenderer.CropCorner.BOTTOM_RIGHT to
                Offset(rect.left + crop.right * rect.width, rect.top + crop.bottom * rect.height)
    )
    return points.minByOrNull { (_, p) -> (p - offset).getDistance() }
        ?.takeIf { (_, p) -> (p - offset).getDistance() <= slop }
        ?.first
}
