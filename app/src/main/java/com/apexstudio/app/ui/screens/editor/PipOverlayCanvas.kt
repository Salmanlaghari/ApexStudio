package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.data.effect.VideoCropGlEffect
import com.apexstudio.app.domain.model.OverlayBlend
import com.apexstudio.app.domain.model.croppedOverlayAspect
import com.apexstudio.app.presentation.viewmodel.MediaClipPipScale
import com.apexstudio.app.ui.theme.ApexPalette
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

/**
 * Aspect (width / height) of the PiP overlay box, used ONLY as the legacy
 * fallback. Real overlays use their own [MediaClip.overlayAspect]
 * (intrinsic media aspect), so a portrait photo is never stretched into
 * a 16:9 box. The export GL effect uses the same per-clip aspect.
 */
const val PIP_BOX_ASPECT = 16f / 9f

/** Use the same TextureView-backed player surface as the main preview. */
internal fun createOverlayPlayerView(
    context: android.content.Context,
    player: ExoPlayer?
): PlayerView = (android.view.LayoutInflater.from(context)
    .inflate(com.apexstudio.app.R.layout.view_player, null) as PlayerView).apply {
    useController = false
    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
    this.player = player
}

/**
 * Effective (resolved) overlay transform at a moment in time.
 *
 * - No keyframes: the persisted PiP values (pipX / pipY / pipScale /
 *   pipRotationDeg / pipOpacity).
 * - Keyframes present: the interpolated keyframe values, which for
 *   overlays are ABSOLUTE overlay transforms (translateX = pipX,
 *   translateY = pipY, scale = pipScale, ...). Preview and export
 *   resolve the same numbers, so WYSIWYG holds.
 */
private data class EffOverlayTransform(
    val x: Float,
    val y: Float,
    val scale: Float,
    val rotationDeg: Float,
    val opacity: Float
)

private fun effectiveOverlayTransform(clip: MediaClip, timeMs: Long): EffOverlayTransform {
    if (clip.keyframes.keyframes.isEmpty()) {
        return EffOverlayTransform(
            clip.pipX, clip.pipY, clip.pipScale, clip.pipRotationDeg, clip.pipOpacity
        )
    }
    val t = clip.keyframes.interpolateAt(timeMs)
    return EffOverlayTransform(
        x = t.translateX.coerceIn(0f, 1f),
        y = t.translateY.coerceIn(0f, 1f),
        scale = t.scale.coerceIn(MediaClipPipScale.MIN, MediaClipPipScale.MAX),
        rotationDeg = t.rotationDeg,
        opacity = t.opacity.coerceIn(0f, 1f)
    )
}

/**
 * Interactive PiP overlay canvas, rendered inside the preview frame.
 *
 * Every overlay clip (video / image on the V2..V10 tracks) that is active
 * at [currentTimeMs] is drawn at its effective transform (keyframe-aware).
 * Tap selects, drag moves, pinch scales + rotates, and the selected
 * overlay shows CapCut-style handles: delete (top-end), resize
 * (bottom-end) and rotate (top-centre, drag around the box). Video
 * overlays play live through [overlayPlayer] (bound to
 * [overlayPlayerClipId] by the screen); image overlays render via Coil.
 * Taps that miss every overlay are NOT consumed, so the preview's own
 * tap handler (screen controls) keeps working.
 *
 * Media kind comes from [overlayIsVideo] (OverlayMediaProbe) — the clip
 * type is always OVERLAY, so the media itself answers video-vs-image.
 * Missing entry = probe still running → a loading placeholder, never a
 * black box presented as content.
 */
@Composable
fun PipOverlayCanvas(
    overlayClips: List<MediaClip>,
    selectedOverlayClipId: String?,
    currentTimeMs: Long,
    overlayPlayer: ExoPlayer?,
    overlayPlayerClipId: String?,
    onSelectOverlayClip: (String?) -> Unit,
    onMoveOverlayClip: (id: String, dx: Float, dy: Float, persist: Boolean) -> Unit,
    onScaleOverlayClip: (id: String, scale: Float, persist: Boolean) -> Unit,
    onRotateOverlayClip: (id: String, deltaDeg: Float, persist: Boolean) -> Unit,
    onOverlayGestureEnd: (id: String) -> Unit,
    onRemoveOverlayClip: (String) -> Unit,
    modifier: Modifier = Modifier,
    overlayIsVideo: Map<String, Boolean> = emptyMap(),
    // Upsert a keyframe at [timeMs] with the given ABSOLUTE overlay
    // transform. Used by gestures when the clip has keyframes (the
    // gesture edits the keyframe, not the pip base values). Null =
    // keyframe editing unsupported (falls back to base setters).
    onUpsertOverlayKeyframe: ((id: String, timeMs: Long, x: Float, y: Float, scale: Float, rotationDeg: Float, opacity: Float, persist: Boolean) -> Unit)? = null
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val canvasWpx = with(density) { maxWidth.toPx() }
        val canvasHpx = with(density) { maxHeight.toPx() }
        if (canvasWpx < 1f || canvasHpx < 1f) return@BoxWithConstraints

        val activeClips = overlayClips.filter { it.isOverlayActiveAt(currentTimeMs) }
        val selectedId = selectedOverlayClipId
        val playerClip = activeClips.firstOrNull { it.id == overlayPlayerClipId }
        LaunchedEffect(overlayPlayer, playerClip?.id, playerClip?.videoCrop) {
            overlayPlayer?.setVideoEffects(
                listOfNotNull(VideoCropGlEffect.fromSettings(playerClip?.videoCrop))
            )
        }

        // Do not put pointerInput on this full-size parent: Compose hit-tests
        // only the top sibling, so even an unconsumed miss would shield the V1
        // preview. Each overlay item owns its own tap/transform recognition.
        Box(modifier = Modifier.fillMaxSize()) {
            for (clip in activeClips) {
                key(clip.id) {
                    PipOverlayItem(
                        clip = clip,
                        isSelected = clip.id == selectedId,
                        canvasWpx = canvasWpx,
                        canvasHpx = canvasHpx,
                        currentTimeMs = currentTimeMs,
                        isVideo = overlayIsVideo[clip.id],
                        overlayPlayer = overlayPlayer,
                        overlayPlayerClipId = overlayPlayerClipId,
                        onMoveOverlayClip = onMoveOverlayClip,
                        onScaleOverlayClip = onScaleOverlayClip,
                        onRotateOverlayClip = onRotateOverlayClip,
                        onSelectOverlayClip = onSelectOverlayClip,
                        onOverlayGestureEnd = onOverlayGestureEnd,
                        onRemoveOverlayClip = onRemoveOverlayClip,
                        onUpsertOverlayKeyframe = onUpsertOverlayKeyframe
                    )
                }
            }
        }
    }
}

private fun pipBoxSizePx(clip: MediaClip, canvasWpx: Float, scale: Float): Pair<Float, Float> {
    val w = canvasWpx * scale.coerceIn(MediaClipPipScale.MIN, MediaClipPipScale.MAX)
    // Preserve the selected crop's aspect, not the original uncropped source. Otherwise a square
    // crop of a landscape overlay is stretched back into a landscape box. Export uses this too.
    val aspect = croppedOverlayAspect(clip.overlayAspect, clip.videoCrop)
    return w to (w / aspect)
}

/** Handle rectangles match the control layout, expressed in item-local pixels. */
private fun pipHandleLocalHitTest(
    pos: Offset,
    boxW: Float,
    boxH: Float,
    density: Float
): Boolean {
    fun inside(left: Float, top: Float, right: Float, bottom: Float) =
        pos.x in left..right && pos.y in top..bottom

    return inside(boxW - 32f * density, 6f * density, boxW - 6f * density, 32f * density) ||
            inside((boxW - 28f * density) / 2f, 6f * density,
                (boxW + 28f * density) / 2f, 34f * density) ||
            inside(boxW - 38f * density, boxH - 38f * density,
                boxW - 6f * density, boxH - 6f * density)
}

private fun OverlayBlend.toComposeBlendMode(): BlendMode = when (this) {
    OverlayBlend.NORMAL -> BlendMode.SrcOver
    OverlayBlend.SCREEN -> BlendMode.Screen
    OverlayBlend.MULTIPLY -> BlendMode.Multiply
    OverlayBlend.OVERLAY -> BlendMode.Overlay
    OverlayBlend.SOFT_LIGHT -> BlendMode.Softlight
}

@Composable
private fun PipOverlayItem(
    clip: MediaClip,
    isSelected: Boolean,
    canvasWpx: Float,
    canvasHpx: Float,
    currentTimeMs: Long,
    isVideo: Boolean?,
    overlayPlayer: ExoPlayer?,
    overlayPlayerClipId: String?,
    onMoveOverlayClip: (id: String, dx: Float, dy: Float, persist: Boolean) -> Unit,
    onScaleOverlayClip: (id: String, scale: Float, persist: Boolean) -> Unit,
    onRotateOverlayClip: (id: String, deltaDeg: Float, persist: Boolean) -> Unit,
    onSelectOverlayClip: (String?) -> Unit,
    onOverlayGestureEnd: (id: String) -> Unit,
    onRemoveOverlayClip: (String) -> Unit,
    onUpsertOverlayKeyframe: ((id: String, timeMs: Long, x: Float, y: Float, scale: Float, rotationDeg: Float, opacity: Float, persist: Boolean) -> Unit)?
) {
    val density = LocalDensity.current
    val latest by rememberUpdatedState(clip)
    val latestTimeMs by rememberUpdatedState(currentTimeMs)
    val latestIsSelected by rememberUpdatedState(isSelected)
    val latestOnSelect by rememberUpdatedState(onSelectOverlayClip)
    val latestOnMove by rememberUpdatedState(onMoveOverlayClip)
    val latestOnScale by rememberUpdatedState(onScaleOverlayClip)
    val latestOnRotate by rememberUpdatedState(onRotateOverlayClip)
    val latestOnGestureEnd by rememberUpdatedState(onOverlayGestureEnd)
    val latestOnUpsertKeyframe by rememberUpdatedState(onUpsertOverlayKeyframe)
    val eff = effectiveOverlayTransform(clip, currentTimeMs)
    // NOTE: the gesture helpers below run in long-lived pointerInput
    // coroutines — they must NOT capture hasKeyframes from composition
    // (it goes stale when keyframes are added/removed). They read
    // latest.keyframes fresh on every call.

    // Gesture routing: with keyframes, a drag edits the keyframe at the
    // playhead (upsert) instead of the pip base values — otherwise the
    // drag would be invisible while keyframes are active. The upsert
    // snaps to the nearest existing keyframe within 1s so dragging
    // during playback does not spray a keyframe per frame.
    fun upsertTimeMs(): Long {
        val t = latestTimeMs
        val nearest = latest.keyframes.keyframes.minByOrNull {
            kotlin.math.abs(it.timeMs - t)
        }
        return if (nearest != null && kotlin.math.abs(nearest.timeMs - t) <= 1000L) {
            nearest.timeMs
        } else t
    }
    fun moveBy(dx: Float, dy: Float, persist: Boolean) {
        val c = latest
        if (latest.keyframes.keyframes.isNotEmpty() && latestOnUpsertKeyframe != null) {
            val e = effectiveOverlayTransform(c, latestTimeMs)
            latestOnUpsertKeyframe?.invoke(
                c.id, upsertTimeMs(),
                (e.x + dx).coerceIn(0f, 1f), (e.y + dy).coerceIn(0f, 1f),
                e.scale, e.rotationDeg, e.opacity, persist
            )
        } else {
            latestOnMove(c.id, dx, dy, persist)
        }
    }
    fun scaleTo(scale: Float, persist: Boolean) {
        val c = latest
        if (latest.keyframes.keyframes.isNotEmpty() && latestOnUpsertKeyframe != null) {
            val e = effectiveOverlayTransform(c, latestTimeMs)
            latestOnUpsertKeyframe?.invoke(
                c.id, upsertTimeMs(),
                e.x, e.y,
                scale.coerceIn(MediaClipPipScale.MIN, MediaClipPipScale.MAX),
                e.rotationDeg, e.opacity, persist
            )
        } else {
            latestOnScale(c.id, scale, persist)
        }
    }
    fun rotateBy(deltaDeg: Float, persist: Boolean) {
        val c = latest
        if (latest.keyframes.keyframes.isNotEmpty() && latestOnUpsertKeyframe != null) {
            val e = effectiveOverlayTransform(c, latestTimeMs)
            latestOnUpsertKeyframe?.invoke(
                c.id, upsertTimeMs(),
                e.x, e.y, e.scale, e.rotationDeg + deltaDeg, e.opacity, persist
            )
        } else {
            latestOnRotate(c.id, deltaDeg, persist)
        }
    }

    val (boxWpx, boxHpx) = pipBoxSizePx(clip, canvasWpx, eff.scale)
    val cxPx = eff.x.coerceIn(0f, 1f) * canvasWpx
    val cyPx = eff.y.coerceIn(0f, 1f) * canvasHpx
    val borderPx = clip.overlayBorderWidth.coerceIn(0f, 0.05f) * boxWpx
    val shadowAlpha = clip.overlayShadow.coerceIn(0f, 1f) * 0.45f

    Box(
        modifier = Modifier
            .size(
                width = with(density) { boxWpx.toDp() },
                height = with(density) { boxHpx.toDp() }
            )
            .offset {
                IntOffset(
                    (cxPx - boxWpx / 2f).roundToInt(),
                    (cyPx - boxHpx / 2f).roundToInt()
                )
            }
                .graphicsLayer {
                rotationZ = eff.rotationDeg
                alpha = eff.opacity.coerceIn(0f, 1f)
            }
            .testTag("pip-overlay-item-${clip.id}")
            // Preview orientation/window resizing changes normalized drag coordinates.
            // Restart only for viewport changes, not each live clip transform update.
            .pointerInput(clip.id, canvasWpx, canvasHpx, density) {
                // detectTransformGestures never returns after a pointer-up;
                // wrapping it in finally only saved on disposal. Track each
                // gesture explicitly so a completed drag/pinch saves once and
                // the next interaction gets its own undo entry.
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial
                    )
                    val startedOnHandle = latestIsSelected &&
                            pipHandleLocalHitTest(
                                down.position, size.width.toFloat(), size.height.toFloat(), density.density
                            )
                    var gestureActive = false
                    var pastTouchSlop = false
                    var accumulatedZoom = 1f
                    var accumulatedRotation = 0f
                    var accumulatedPan = Offset.Zero
                    try {
                        do {
                            // Observe/claim before descendants in the Main
                            // pass. In particular, a video overlay's native
                            // PlayerView can consume AndroidView touch events;
                            // reading only Main made selected video PiPs inert.
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val cancelled = event.changes.any { it.isConsumed }
                            val up = event.changes.firstOrNull {
                                it.id == down.id && !it.pressed
                            }
                            if (!cancelled && up != null && event.changes.none { it.pressed } &&
                                !gestureActive && !startedOnHandle
                            ) {
                                // Item-local taps select/deselect; consuming
                                // this up prevents the preview's screen-control
                                // tap from firing. Misses never enter this node.
                                up.consume()
                                latestOnSelect(
                                    if (latestIsSelected) null else latest.id
                                )
                            }
                            if (!cancelled && !startedOnHandle) {
                                val pan = event.calculatePan()
                                val zoom = event.calculateZoom()
                                val rotation = event.calculateRotation()
                                if (!pastTouchSlop) {
                                    accumulatedZoom *= zoom
                                    accumulatedRotation += rotation
                                    accumulatedPan += pan
                                    val radius = event.calculateCentroidSize(useCurrent = false)
                                    val zoomMotion = kotlin.math.abs(1f - accumulatedZoom) * radius
                                    val rotationMotion = kotlin.math.abs(accumulatedRotation) *
                                        kotlin.math.PI.toFloat() * radius / 180f
                                    pastTouchSlop = maxOf(
                                        zoomMotion, rotationMotion, accumulatedPan.getDistance()
                                    ) > viewConfiguration.touchSlop
                                }
                                if (pastTouchSlop) {
                                    if (!gestureActive) {
                                        gestureActive = true
                                        // Select when the gesture becomes an
                                        // edit, before move callbacks reach the
                                        // controller.
                                        if (!latestIsSelected) latestOnSelect(latest.id)
                                    }
                                    if (pan != Offset.Zero) {
                                        moveBy(pan.x / canvasWpx, pan.y / canvasHpx, false)
                                    }
                                    if (zoom != 1f) {
                                        val e = effectiveOverlayTransform(latest, latestTimeMs)
                                        scaleTo(e.scale * zoom, false)
                                    }
                                    if (rotation != 0f) rotateBy(rotation, false)
                                    event.changes.forEach {
                                        if (it.positionChanged()) it.consume()
                                    }
                                }
                            }
                        } while (!cancelled && event.changes.any { it.pressed })
                    } finally {
                        if (gestureActive) {
                            latestOnGestureEnd(latest.id)
                        }
                    }
                }
            }
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) Color(0xFF00F0FF)
                else Color(0xFF00F0FF).copy(alpha = 0.55f),
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        // Soft drop shadow (properties panel): a blurred dark rect
        // behind the box. Drawn with a framework BlurMaskFilter so it
        // works on every API level (Modifier.blur needs API 31+).
        if (shadowAlpha > 0.01f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(
                        (shadowAlpha * 255).roundToInt().coerceIn(0, 255), 0, 0, 0
                    )
                    maskFilter = android.graphics.BlurMaskFilter(
                        (boxWpx * 0.06f).coerceAtLeast(1f),
                        android.graphics.BlurMaskFilter.Blur.NORMAL
                    )
                }
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawRect(
                        boxWpx * 0.04f, boxHpx * 0.05f,
                        boxWpx * 0.98f, boxHpx * 0.99f,
                        paint
                    )
                }
            }
        }
        // Shape mask (Mask tool) on the overlay: feathered clip with
        // per-pixel opacity, WYSIWYG with the export shader
        // (PipOverlayGlEffect composites maskFactor * opacity).
        val overlayMask = clip.mask?.takeIf { it.enabled && it.opacity > 0.001f }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (overlayMask != null) Modifier.videoMaskFeatheredClip(overlayMask)
                    else Modifier
                )
                // Blend mode (properties panel): the content is drawn
                // into an offscreen layer whose Paint carries the
                // blend mode, so the whole overlay composites with
                // Normal / Screen / Multiply / Overlay against V1.
                // Note: blend has no visible effect on the AndroidView
                // (PlayerView) video path — platform limitation
                // (AndroidView is not drawn through the Compose draw
                // phase).
                .drawWithContent {
                    drawIntoCanvas { canvas ->
                        val paint = Paint().apply {
                            blendMode = clip.overlayBlend.toComposeBlendMode()
                        }
                        canvas.saveLayer(
                            Rect(Offset.Zero, size), paint
                        )
                        drawContent()
                        canvas.restore()
                    }
                }
                .graphicsLayer {
                    // Mirror the content (properties panel flip).
                    // Applied here, not on the outer box, so the
                    // handles keep their positions.
                    scaleX = if (clip.overlayFlipH) -1f else 1f
                    scaleY = if (clip.overlayFlipV) -1f else 1f
                }
                // Border (properties panel): drawn inside the box so
                // it never changes the box layout.
                .then(
                    if (borderPx > 0.5f) Modifier.border(
                        width = with(density) { borderPx.toDp() },
                        color = Color(clip.overlayBorderColor)
                    ) else Modifier
                )
        ) {
            when {
                // Live video overlay through the dedicated player.
                isVideo == true && overlayPlayer != null &&
                        overlayPlayerClipId == clip.id -> {
                    AndroidView(
                        // A default PlayerView creates a SurfaceView, which
                        // is outside Compose's transformed/clipped layers and
                        // obscures overlay gestures. The shared layout uses a
                        // TextureView, matching the main preview renderer.
                        factory = { ctx -> createOverlayPlayerView(ctx, overlayPlayer) },
                        update = { view -> view.player = overlayPlayer },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                isVideo == true -> {
                    // Video overlay whose turn for the shared player has
                    // not come yet (another video overlay is bound):
                    // loading placeholder, never a black box presented
                    // as content.
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Movie,
                            contentDescription = "Video overlay",
                            tint = Color(0xFF00F0FF).copy(alpha = 0.7f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                isVideo == null -> {
                    // Probe still running: neutral placeholder while the
                    // media kind resolves.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF10131A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Movie,
                            contentDescription = "Loading overlay",
                            tint = Color(0xFF00F0FF).copy(alpha = 0.4f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                else -> {
                    // Image overlay: Coil decodes the still. A failed
                    // load must NEVER render as a silent black box (the
                    // box background is black, so a failed AsyncImage
                    // looked exactly like the #146 black-box bug). Show
                    // a visible "couldn't load" placeholder with the
                    // clip name so the user knows the media — not the
                    // editor — failed, and can re-pick it.
                    var loadError by remember(clip.id, clip.uri) { mutableStateOf(false) }
                    // Retry counter: a transient I/O failure (slow
                    // storage, grant timing) must not latch a
                    // permanent error — tapping the placeholder
                    // retries the load with a fresh Coil request.
                    var loadAttempts by remember(clip.id, clip.uri) { mutableStateOf(0) }
                    if (loadError) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF2A1215))
                                .clickable {
                                    loadError = false
                                    loadAttempts++
                                }
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.BrokenImage,
                                contentDescription = null,
                                tint = Color(0xFFE57373),
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Couldn't load media",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = clip.name,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 8.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Tap to retry",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 8.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        key(loadAttempts) {
                            AsyncImage(
                                model = clip.uri,
                                contentDescription = "Overlay image",
                                // Stretch to the box — matches the export
                                // GL effect EXACTLY: PipOverlayGlEffect's
                                // fragment shader maps the full texture
                                // onto the box (pipUv = local/half * 0.5
                                // + 0.5, full [0,1] range, no
                                // letterboxing), and the box is sized
                                // w x w/overlayAspect in BOTH preview
                                // (pipBoxSizePx) and export
                                // (computeGeometry: pipH = pipW /
                                // aspect). FillBounds == export mapping.
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier.fillMaxSize().graphicsLayer {
                                    val crop = clip.videoCrop.sanitized()
                                    scaleX = 1f / crop.width
                                    scaleY = 1f / crop.height
                                    translationX = (0.5f - (crop.left + crop.right) * 0.5f) * size.width * scaleX
                                    translationY = (0.5f - (crop.top + crop.bottom) * 0.5f) * size.height * scaleY
                                },
                                onError = {
                                    android.util.Log.w(
                                        "PipOverlayCanvas",
                                        "Overlay image failed to load (attempt $loadAttempts): ${clip.uri}"
                                    )
                                    loadError = true
                                },
                                onSuccess = { loadError = false }
                            )
                        }
                    }
                }
            }
        } // inner mask-clip box

        if (isSelected) {
            // Dashed selection border (rotates with the overlay, CapCut-style).
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    color = Color(0xFF00E5FF),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                    )
                )
            }
            // "PIP" tag (top-start).
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "PIP OVERLAY",
                    color = Color(0xFF00F0FF),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            // Delete handle (top-end, inside the box: the outer box is
            // clipped, so a handle offset outside it would be cut off).
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-6).dp, y = 6.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE53935))
                    .clickable { onRemoveOverlayClip(clip.id) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove overlay",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
            // Rotate handle (top-centre, inside the box): drag around
            // the box centre to rotate. Angle is measured from the
            // touch position relative to the box centre. Kept inside
            // the box because the outer box clips its children.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 6.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(ApexPalette.NeonCyan)
                    .pointerInput(clip.id, canvasWpx, canvasHpx, density) {
                        var prevAngle: Float? = null
                        val handleHalfPx = with(density) { 14.dp.toPx() }
                        // Box centre in handle-local coords, computed
                        // FRESH on every event: the pointerInput block
                        // outlives recompositions, so a captured boxHpx
                        // would go stale after a scale change.
                        fun cyPx(): Float {
                            val effScale = effectiveOverlayTransform(
                                latest, latestTimeMs
                            ).scale
                            val h = pipBoxSizePx(latest, canvasWpx, effScale).second
                            return h / 2f - with(density) { 6.dp.toPx() }
                        }
                        detectDragGestures(
                            onDragStart = { down ->
                                prevAngle = atan2(
                                    down.y - cyPx(), down.x - handleHalfPx
                                ) * (180f / Math.PI.toFloat())
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val angle = atan2(
                                    change.position.y - cyPx(),
                                    change.position.x - handleHalfPx
                                ) * (180f / Math.PI.toFloat())
                                val prev = prevAngle
                                prevAngle = angle
                                if (prev != null) {
                                    var delta = angle - prev
                                    // Wrap to (-180, 180].
                                    while (delta <= -180f) delta += 360f
                                    while (delta > 180f) delta -= 360f
                                    if (delta != 0f) rotateBy(delta, false)
                                }
                            },
                            onDragEnd = { latestOnGestureEnd(latest.id) },
                            onDragCancel = { latestOnGestureEnd(latest.id) }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.RotateRight,
                    contentDescription = "Rotate overlay",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            // Resize handle (bottom-end, inside the box): drag
            // diagonally to scale. Kept inside because the outer box
            // clips its children.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-6).dp, y = (-6).dp)
                    .size(32.dp)
                    .testTag("pip-overlay-resize-${clip.id}")
                    .clip(CircleShape)
                    .background(ApexPalette.NeonCyan)
                    .pointerInput(clip.id, canvasWpx, canvasHpx, density) {
                        var startScale = 1f
                        var acc = 0f
                        var baseSide = 1f
                        detectDragGestures(
                            onDragStart = {
                                startScale = effectiveOverlayTransform(
                                    latest, latestTimeMs
                                ).scale
                                acc = 0f
                                baseSide = (canvasWpx * startScale).coerceAtLeast(1f)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                acc += (dragAmount.x + dragAmount.y)
                                val factor = 1f + acc / baseSide
                                scaleTo(
                                    (startScale * factor).coerceIn(
                                        MediaClipPipScale.MIN,
                                        MediaClipPipScale.MAX
                                    ),
                                    false
                                )
                            },
                            onDragEnd = { latestOnGestureEnd(latest.id) },
                            onDragCancel = { latestOnGestureEnd(latest.id) }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.OpenInFull,
                    contentDescription = "Resize overlay",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
