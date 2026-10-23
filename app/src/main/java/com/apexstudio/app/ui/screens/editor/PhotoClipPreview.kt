package com.apexstudio.app.ui.screens.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
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
    modifier: Modifier = Modifier,
    isCoverMode: Boolean = false,
    onImageAspectRatioChange: (Float) -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // Raw decoded photo (EXIF applied, downscaled for preview).
    var rawBitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    // Whether the load attempt for this uri has finished (success or
    // failure). While false the preview is still loading; once true
    // with rawBitmap == null the image failed to decode and the UI
    // shows a visible retry affordance — never a silent black
    // preview (a failed load used to render as an empty black box
    // with no way to recover short of re-picking the clip).
    var loadFinished by remember(uri) { mutableStateOf(false) }
    var retryNonce by remember { mutableStateOf(0) }
    // Edited bitmap (declared early so the uri effect can check it).
    var edited by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri, retryNonce) {
        val old = rawBitmap
        loadFinished = false
        val fresh = PhotoEditRenderer.loadBitmap(context, uri, maxDim = 1280)
        rawBitmap = fresh
        loadFinished = true
        if (fresh == null) {
            android.util.Log.w("PhotoClipPreview", "Image failed to load: $uri (tap retry to reload)")
        }
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
    val effectiveSettings = if (cropActive) settings.copy(
        crop = null,
        canvas = com.apexstudio.app.domain.model.PhotoCanvasSettings()
    ) else settings
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

    // Photo display rect derived from layout size — the single source of
    // truth for drawing and crop drag hit-testing. Crop editing always
    // uses Fit so the entire image and all crop handles remain reachable.
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val bmp = edited
    androidx.compose.runtime.SideEffect {
        if (bmp != null && bmp.width > 0 && bmp.height > 0) {
            onImageAspectRatioChange(bmp.width.toFloat() / bmp.height)
        }
    }
    val photoRect = remember(bmp, canvasSize, isCoverMode, cropActive) {
        bmp?.let {
            photoDisplayRect(
                IntSize(it.width, it.height), canvasSize,
                isCoverMode = isCoverMode,
                cropActive = cropActive
            )
        }
    }

    val handleRadiusPx = with(density) { 13.dp.toPx() }
    val cropRect = settings.crop ?: PhotoCropRect.full()
    val lockedAspect = PhotoEditSettings.aspectRatioForPreset(settings.cropAspectPreset)
    var dragCorner by remember { mutableStateOf<PhotoEditRenderer.CropCorner?>(null) }

    // Phase 4: Ken Burns motion preview — the real KenBurnsView
    // (Apache-2.0) pans/zooms the edited bitmap. Crop interactions stay
    // on the static canvas.
    if (loadFinished && rawBitmap == null) {
        // Visible failure state: the image could not be decoded
        // (expired grant, moved file, corrupt data). Tapping retries
        // the load instead of leaving a silent black preview.
        androidx.compose.foundation.layout.Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF2A1215))
                .clickable { retryNonce++ },
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.BrokenImage,
                    contentDescription = null,
                    tint = Color(0xFFE57373),
                    modifier = Modifier.size(32.dp)
                )
                androidx.compose.material3.Text(
                    text = "Couldn't load image",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                androidx.compose.material3.Text(
                    text = "Tap to retry",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
            }
        }
    } else if (settings.kenBurns && !cropActive && bmp != null) {
        val kbBitmap = bmp
        BoxWithConstraints(
            modifier = modifier.fillMaxSize().clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            val viewport = IntSize(
                with(density) { maxWidth.roundToPx() },
                with(density) { maxHeight.roundToPx() }
            )
            val rect = photoDisplayRect(
                IntSize(kbBitmap.width, kbBitmap.height), viewport,
                isCoverMode = isCoverMode,
                cropActive = false
            )
            if (rect != null) {
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
                    modifier = Modifier.size(
                        with(density) { rect.width.toDp() },
                        with(density) { rect.height.toDp() }
                    )
                )
            }
        }
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

/**
 * Centered photo bounds in preview pixels. Cover uses max scale (cropping
 * overflow at the viewport); Fit uses min scale (letterboxing). Crop mode
 * deliberately forces Fit regardless of the normal preview setting.
 */
internal fun photoDisplayRect(
    imageSize: IntSize,
    viewportSize: IntSize,
    isCoverMode: Boolean,
    cropActive: Boolean
): Rect? {
    if (imageSize.width <= 0 || imageSize.height <= 0 ||
        viewportSize.width <= 0 || viewportSize.height <= 0
    ) return null

    val scaleX = viewportSize.width.toFloat() / imageSize.width
    val scaleY = viewportSize.height.toFloat() / imageSize.height
    val scale = if (isCoverMode && !cropActive) maxOf(scaleX, scaleY) else minOf(scaleX, scaleY)
    val width = imageSize.width * scale
    val height = imageSize.height * scale
    val left = (viewportSize.width - width) / 2f
    val top = (viewportSize.height - height) / 2f
    return Rect(left, top, left + width, top + height)
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
