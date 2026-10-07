package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.presentation.viewmodel.MediaClipPipScale
import com.apexstudio.app.ui.theme.ApexPalette
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

/**
 * Aspect (width / height) of the PiP overlay box. The export GL effect
 * ([com.apexstudio.app.data.effect.PipOverlayGlEffect]) composites the
 * overlay into a 16:9 box, so the preview must use the same box or
 * WYSIWYG breaks.
 */
const val PIP_BOX_ASPECT = 16f / 9f

/**
 * Interactive PiP overlay canvas, rendered inside the preview frame.
 *
 * Every overlay clip (video / image on the V2 track) that is active at
 * [currentTimeMs] is drawn at its persisted transform
 * (pipX / pipY / pipScale / pipRotationDeg / pipOpacity). Tap selects,
 * drag moves, pinch scales + rotates, and the selected overlay shows
 * CapCut-style handles: delete (top-end) and resize (bottom-end) —
 * the same interaction quality as the sticker canvas.
 *
 * Video overlays play live through [overlayPlayer] (bound to
 * [overlayPlayerClipId] by the screen); image overlays render via Coil.
 * Taps that miss every overlay are NOT consumed, so the preview's own
 * tap handler (screen controls) keeps working.
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
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val canvasWpx = with(density) { maxWidth.toPx() }
        val canvasHpx = with(density) { maxHeight.toPx() }
        if (canvasWpx < 1f || canvasHpx < 1f) return@BoxWithConstraints

        val activeClips = overlayClips.filter { it.isOverlayActiveAt(currentTimeMs) }
        val selectedId = selectedOverlayClipId

        Box(
            modifier = Modifier
                .fillMaxSize()
                // Tap = hit-test overlays topmost-first. A hit selects
                // (tap again deselects); a miss deselects but consumes
                // nothing so the preview's onTapVideo still fires.
                .pointerInput(activeClips, selectedId) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val hit = activeClips.asReversed().firstOrNull { clip ->
                            pipHitTest(clip, down.position, canvasWpx, canvasHpx)
                        }
                        if (hit != null) {
                            // Consume only the up: the down must stay
                            // unconsumed so the item's own transform
                            // detector can start a drag from it. A consumed
                            // up still suppresses the preview's onTapVideo.
                            val up = waitForUpOrCancellation()
                            up?.consume()
                            if (up != null) {
                                onSelectOverlayClip(
                                    if (hit.id == selectedId) null else hit.id
                                )
                            }
                        }
                    }
                }
        ) {
            for (clip in activeClips) {
                key(clip.id) {
                    PipOverlayItem(
                        clip = clip,
                        isSelected = clip.id == selectedId,
                        canvasWpx = canvasWpx,
                        canvasHpx = canvasHpx,
                        overlayPlayer = overlayPlayer,
                        overlayPlayerClipId = overlayPlayerClipId,
                        onMoveOverlayClip = onMoveOverlayClip,
                        onScaleOverlayClip = onScaleOverlayClip,
                        onRotateOverlayClip = onRotateOverlayClip,
                        onOverlayGestureEnd = onOverlayGestureEnd,
                        onRemoveOverlayClip = onRemoveOverlayClip
                    )
                }
            }
        }
    }
}

/** Generous finger hit test against the (possibly rotated) overlay box. */
private fun pipHitTest(
    clip: MediaClip,
    pos: Offset,
    canvasWpx: Float,
    canvasHpx: Float
): Boolean {
    val (boxW, boxH) = pipBoxSizePx(clip, canvasWpx)
    val cx = clip.pipX.coerceIn(0f, 1f) * canvasWpx
    val cy = clip.pipY.coerceIn(0f, 1f) * canvasHpx
    // Inverse-rotate the tap into the overlay's local frame
    // (rotationZ is clockwise-positive in screen coords).
    val rad = Math.toRadians(clip.pipRotationDeg.toDouble())
    val c = cos(rad).toFloat()
    val s = sin(rad).toFloat()
    val dx = pos.x - cx
    val dy = pos.y - cy
    val lx = dx * c + dy * s
    val ly = -dx * s + dy * c
    val slop = 16f
    return kotlin.math.abs(lx) <= boxW / 2f + slop &&
            kotlin.math.abs(ly) <= boxH / 2f + slop
}

private fun pipBoxSizePx(clip: MediaClip, canvasWpx: Float): Pair<Float, Float> {
    val w = canvasWpx *
            clip.pipScale.coerceIn(MediaClipPipScale.MIN, MediaClipPipScale.MAX)
    return w to (w / PIP_BOX_ASPECT)
}

@Composable
private fun PipOverlayItem(
    clip: MediaClip,
    isSelected: Boolean,
    canvasWpx: Float,
    canvasHpx: Float,
    overlayPlayer: ExoPlayer?,
    overlayPlayerClipId: String?,
    onMoveOverlayClip: (id: String, dx: Float, dy: Float, persist: Boolean) -> Unit,
    onScaleOverlayClip: (id: String, scale: Float, persist: Boolean) -> Unit,
    onRotateOverlayClip: (id: String, deltaDeg: Float, persist: Boolean) -> Unit,
    onOverlayGestureEnd: (id: String) -> Unit,
    onRemoveOverlayClip: (String) -> Unit
) {
    val density = LocalDensity.current
    val latest by rememberUpdatedState(clip)

    val (boxWpx, boxHpx) = pipBoxSizePx(clip, canvasWpx)
    val cxPx = clip.pipX.coerceIn(0f, 1f) * canvasWpx
    val cyPx = clip.pipY.coerceIn(0f, 1f) * canvasHpx

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
                rotationZ = clip.pipRotationDeg
                alpha = clip.pipOpacity.coerceIn(0f, 1f)
            }
            .pointerInput(clip.id) {
                // detectTransformGestures has no end callback: re-arm in a
                // loop and persist once per completed gesture.
                while (true) {
                    var gestureActive = false
                    try {
                        detectTransformGestures { _, pan, zoom, rotation ->
                            gestureActive = true
                            val c = latest
                            if (pan != Offset.Zero) {
                                onMoveOverlayClip(
                                    c.id,
                                    pan.x / canvasWpx,
                                    pan.y / canvasHpx,
                                    false
                                )
                            }
                            if (zoom != 1f) {
                                onScaleOverlayClip(
                                    c.id,
                                    (c.pipScale * zoom).coerceIn(
                                        MediaClipPipScale.MIN,
                                        MediaClipPipScale.MAX
                                    ),
                                    false
                                )
                            }
                            if (rotation != 0f) {
                                // Same convention as the sticker canvas:
                                // detectTransformGestures reports rotation in
                                // the same clockwise-positive sense as
                                // graphicsLayer rotationZ.
                                onRotateOverlayClip(c.id, rotation, false)
                            }
                        }
                    } finally {
                        if (gestureActive) {
                            onOverlayGestureEnd(latest.id)
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
        when {
            // Live video overlay through the dedicated muted player.
            clip.type == ClipType.VIDEO && overlayPlayer != null &&
                    overlayPlayerClipId == clip.id -> {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                            player = overlayPlayer
                        }
                    },
                    update = { view -> view.player = overlayPlayer },
                    modifier = Modifier.fillMaxSize()
                )
            }
            clip.type == ClipType.VIDEO -> {
                // Additional simultaneous video overlay (the shared player
                // is bound to the first one): static placeholder.
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
            else -> {
                AsyncImage(
                    model = clip.uri,
                    contentDescription = "Overlay image",
                    // Stretch to the 16:9 box — matches the export GL
                    // effect, which maps the full texture onto the box.
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

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
            // Delete handle (top-end).
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 10.dp, y = (-10).dp)
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
            // Resize handle (bottom-end): drag diagonally to scale.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 12.dp, y = 12.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(ApexPalette.NeonCyan)
                    .pointerInput(clip.id) {
                        var startScale = 1f
                        var acc = 0f
                        var baseSide = 1f
                        detectDragGestures(
                            onDragStart = {
                                startScale = latest.pipScale
                                acc = 0f
                                baseSide = (canvasWpx * startScale).coerceAtLeast(1f)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                acc += (dragAmount.x + dragAmount.y)
                                val factor = 1f + acc / baseSide
                                onScaleOverlayClip(
                                    latest.id,
                                    (startScale * factor).coerceIn(
                                        MediaClipPipScale.MIN,
                                        MediaClipPipScale.MAX
                                    ),
                                    false
                                )
                            },
                            onDragEnd = { onOverlayGestureEnd(latest.id) },
                            onDragCancel = { onOverlayGestureEnd(latest.id) }
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
