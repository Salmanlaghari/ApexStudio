package com.apexstudio.app.domain.model
import kotlinx.serialization.Serializable

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Normalised point in SHAPE space for polygon masks: x to the right,
 * y DOWN (matching Compose canvas coordinates), zero at the shape
 * centre, -1..1 on the inscribed envelope of the shape.
 *
 * This is deliberately NOT in
 * frame space — callers multiply by the mask's half-extent (aspect
 * corrected) so the preview path and the export shader build the SAME
 * polygon from the SAME numbers, which is what makes WYSIWYG hold.
 */
@Serializable
data class MaskPoint(val x: Float, val y: Float)

/**
 * Geometry + policy for the 10 top-level mask modes (Bug 4).
 *
 * Three of the ten modes are true polygons (heart, star, triangle) and
 * one is user-drawn (pen). Rather than duplicating that maths in the
 * Canvas preview (`MaskPreviewOverlay.shapePath`) and in the Media3
 * fragment shader (`MaskGlEffect`), both sides ask THIS object for one
 * closed list of points: the preview strokes it into a [androidx.compose.ui.graphics.Path],
 * and the export copies it into the `uPoly` uniform as floats.
 *
 * Polygons close themselves: the last point equals the first, so both
 * consumers can walk consecutive pairs without a wrap-around case.
 */
object MaskModel {

    /**
     * Maximum polygon length, including the duplicated closing point.
     * The export uniform is `vec2 uPoly[32]`, so all shapes must land
     * within this; peripheral shapes are resampled to fit.
     */
    const val MAX_POINTS = 32

    /** Samples used to approximate the heart's parametric curve. */
    private const val HEART_SAMPLES = 31

    /**
     * Closed polygon for [shape] in shape space, or an empty list for
     * modes that are not polygonal (rectangle, ellipse, circle,
     * horizontal, mirror, blur-faces) — those are evaluated analytically
     * as distance fields on both sides.
     *
     * `center` / `size` / `rotationRad` are expressed in shape space
     * relative to the mask centre and are used only by the freeform
     * [MaskShape.DRAW] points, which the caller stores already placed.
     * The generated shapes ignore them: the preview and the shader both
     * transform them by the mask's own centre/extent, single-source.
     */
    fun outline(shape: MaskShape, drawPoints: List<MaskPoint> = emptyList()): List<MaskPoint> =
        when (shape) {
            MaskShape.HEART -> heartOutline()
            MaskShape.STAR -> starOutline()
            MaskShape.TRIANGLE -> triangleOutline()
            MaskShape.DRAW -> drawOutline(drawPoints)
            MaskShape.RECTANGLE,
            MaskShape.ELLIPSE,
            MaskShape.CIRCLE,
            MaskShape.LINEAR,
            MaskShape.MIRROR,
            MaskShape.BLUR_FACE -> emptyList()
        }

    /**
     * Heart from its classic parametric curve
     * `x = 16 sin^3 t`, `y = 13 cos t - 5 cos 2t - 2 cos 3t - cos 4t`.
     *
     * In maths coordinates y is up and this traces the lobes at TOP; in
     * screen space y is down, hence the negation. It is then normalised
     * by the real bounds (±16 wide, 13-1-(-17) range -> /17) so the
     * silhouette lands inside the -1..1 envelope and scales correctly
     * with `uSize`.
     */
    fun heartOutline(): List<MaskPoint> {
        val points = ArrayList<MaskPoint>(HEART_SAMPLES + 1)
        for (i in 0..HEART_SAMPLES) {
            val t = (2.0 * PI * i / HEART_SAMPLES).toFloat()
            val s = sin(t)
            val xNorm = 16f * s * s * s
            val yNorm = 13f * cos(t) - 5f * cos(2f * t) - 2f * cos(3f * t) - cos(4f * t)
            points += MaskPoint((xNorm / 16f).coerceIn(-1f, 1f), (-yNorm / 17f).coerceIn(-1f, 1f))
        }
        // The final sample is mathematically the first point, but Float
        // trig leaves a tiny rounding difference. Replace it exactly
        // instead of appending a 33rd point past the shader uniform.
        points[points.lastIndex] = points.first()
        return points
    }

    /** Five-pointed star: five outer tips at unit radius, five valleys offset. */
    fun starOutline(innerRadius: Float = 0.5f): List<MaskPoint> {
        val points = ArrayList<MaskPoint>(MAX_POINTS + 1)
        for (i in 0 until 10) {
            // Tip at 12 o'clock, alternating outer/inner every 36 degrees,
            // screen space y is down.
            // 36 degrees per step: ten vertices (5 tips + 5 valleys) must close a
            // full turn, so the step is PI/5 - half of it at the tip of a
            // star sample, and the tip is pinned at 12 oclock.
            val angle = (-PI.toFloat() / 2f) + (i * (PI / 5f)).toFloat()
            val radius = if (i % 2 == 0) 1f else innerRadius
            points += MaskPoint(radius * cos(angle), radius * sin(angle))
        }
        val first = points.first()
        if (points.last() != first) points += first
        return points
    }

    /** Equilateral triangle, apex up (screen-space y grows downward). */
    fun triangleOutline(): List<MaskPoint> {
        val points = ArrayList<MaskPoint>(4)
        points += MaskPoint(0f, -1f)
        points += MaskPoint(0.866f, 0.5f)
        points += MaskPoint(-0.866f, 0.5f)
        val first = points.first()
        if (points.last() != first) points += first
        return points
    }

    /**
     * Freeform pen region: already-placed normalised points, clamped to
     * [MAX_POINTS] and closed. Fewer than 3 distinct points cannot bound
     * an area, so this returns an empty list and both consumers fall
     * back to "no polygon" instead of rendering a degenerate sliver.
     */
    fun drawOutline(drawPoints: List<MaskPoint>): List<MaskPoint> {
        val distinct = drawPoints
            .filter { it.x.isFinite() && it.y.isFinite() }
            .distinct()
            .take(MAX_POINTS - 1)
        if (distinct.size < 3) return emptyList()
        return distinct + distinct.first()
    }

    /** True for the modes whose region is a polygon on both sides. */
    fun MaskShape.isPolygonal(): Boolean = outline(this).isNotEmpty() || this == MaskShape.DRAW
}

/**
 * One-tap mask recipe (Bug 4 control 8 of feather/invert/opacity/
 * rotation/position/size/keyframes/presets).
 *
 * `apply` produces a COMPLETE [VideoMask], never a partial patch: a
 * preset is meant to feel like switching modes, so it resets feather,
 * rotation and inversion to the values the recipe is drawn for rather
 * than inheriting whatever the previous shape left behind.
 *
 * All geometry is normalised to the frame (0..1 centre, 0..1 full
 * extent) so a preset means the same thing at 720p and at 4K.
 */
data class MaskPreset(
    val id: String,
    val label: String,
    val shape: MaskShape,
    val sizeX: Float,
    val sizeY: Float,
    val feather: Float,
    val rotationDeg: Float = 0f,
    val invert: Boolean = false,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f
) {
    /** Build the mask this preset describes (opacity always full). */
    fun apply(): VideoMask = VideoMask(
        shape = shape,
        centerX = centerX,
        centerY = centerY,
        sizeX = sizeX,
        sizeY = sizeY,
        feather = feather.coerceIn(0f, 1f),
        invert = invert,
        opacity = 1f,
        enabled = true,
        rotationDeg = rotationDeg.coerceIn(-180f, 180f),
        presetId = id
    )

    companion object {
        /** The shipped recipe list; order is what the picker shows. */
        val ALL: List<MaskPreset> = listOf(
            MaskPreset("portrait", "Portrait", MaskShape.CIRCLE, 0.5f, 0.5f, 0.35f),
            MaskPreset("oval", "Oval", MaskShape.ELLIPSE, 0.55f, 0.75f, 0.3f),
            MaskPreset("cinema", "Cinema Bars", MaskShape.RECTANGLE, 1f, 0.45f, 0.12f, invert = true),
            MaskPreset("widescreen", "Wide Screen", MaskShape.RECTANGLE, 0.7f, 0.7f, 0.2f),
            MaskPreset("horizontal-wipe", "Half Frame", MaskShape.LINEAR, 0.5f, 1f, 0.4f),
            MaskPreset("valentine", "Valentine", MaskShape.HEART, 0.7f, 0.7f, 0.25f, rotationDeg = 0f),
            MaskPreset("spotlight", "Spotlight", MaskShape.STAR, 0.65f, 0.65f, 0.3f, rotationDeg = 0f),
            MaskPreset("corner-frame", "Corner Frame", MaskShape.TRIANGLE, 0.5f, 0.5f, 0.18f, centerX = 0.3f, centerY = 0.7f),
            MaskPreset("soft-everywhere", "Everything", MaskShape.RECTANGLE, 1f, 1f, 0.5f)
        )

        fun byId(id: String?): MaskPreset? = ALL.firstOrNull { it.id == id }
    }
}

/**
 * One keyframe of a mask's flow — Bug 4 control "keyframe animation".
 *
 * Keyframes are clip-relative milliseconds, matching `Keyframe` for
 * ordinary transforms so the timeline's diamond strip and the
 * `KeyframePanel` can drive both. Every field carries the mask property
 * it animates, which keeps interpolation total: there is no field that
 * can silently stop being animatable because it was left out of a pair.
 */
@Serializable
data class MaskKeyframe(
    val id: String,
    val timeMs: Long,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val sizeX: Float = 0.6f,
    val sizeY: Float = 0.6f,
    val rotationDeg: Float = 0f,
    val feather: Float = 0.15f,
    val opacity: Float = 1f,
    val invert: Boolean = false
)

/**
 * Resolve this mask at [localMs] (clip-relative milliseconds).
 *
 * This is the kinetic half of Bug 4's "keyframe animation" control: the
 * preview evaluates it every frame at the playhead and the export
 * evaluates it each frame of that clip's span, so both render the same
 * moving mask. With no keyframes the mask is returned untouched, which
 * keeps the call free for the common static case.
 *
 * Interpolation is LINEAR and total: every float is a plain lerp, and
 * [invert] — a boolean — crosses at the midpoint of its bracket, so no
 * property can be left behind by a mismatched pair.
 * Outside the track it clamps to the first / last keyframe, the same
 * contract [KeyframeTrack.interpolateAt] uses for normal transforms.
 */
fun VideoMask.resolveMaskAt(localMs: Long): VideoMask {
    val frames = maskKeyframes.orEmpty()
    if (frames.isEmpty()) return this
    if (frames.size == 1) return withKeyframe(frames.first())
    val sorted = frames.sortedBy { it.timeMs }
    val first = sorted.first()
    val last = sorted.last()
    if (localMs <= first.timeMs) return withKeyframe(first)
    if (localMs >= last.timeMs) return withKeyframe(last)
    val lower = sorted.last { it.timeMs <= localMs }
    val upper = sorted.first { it.timeMs > lower.timeMs }
    if (upper.timeMs == lower.timeMs) return withKeyframe(upper)
    val span = (upper.timeMs - lower.timeMs).toFloat().coerceAtLeast(1f)
    val t = ((localMs - lower.timeMs).toFloat() / span).coerceIn(0f, 1f)
    fun lerp(a: Float, b: Float): Float = a + (b - a) * t
    return copy(
        centerX = lerp(lower.centerX, upper.centerX).coerceIn(0f, 1f),
        centerY = lerp(lower.centerY, upper.centerY).coerceIn(0f, 1f),
        sizeX = lerp(lower.sizeX, upper.sizeX).coerceIn(0f, 1f),
        sizeY = lerp(lower.sizeY, upper.sizeY).coerceIn(0f, 1f),
        rotationDeg = lerp(lower.rotationDeg, upper.rotationDeg),
        feather = lerp(lower.feather, upper.feather).coerceIn(0f, 1f),
        opacity = lerp(lower.opacity, upper.opacity).coerceIn(0f, 1f),
        // boolean -> crosses at the midpoint so the flicker is frame
        // exact rather than an instant jump at an arbitrary side.
        invert = (if (lower.invert) 1f else 0f) + t *
            ((if (upper.invert) 1f else 0f) - (if (lower.invert) 1f else 0f)) >= 0.5f
    )
}

/** Mask rebuilt with every property taken verbatim from [frame]. */
fun VideoMask.withKeyframe(frame: MaskKeyframe): VideoMask = copy(
    centerX = frame.centerX.coerceIn(0f, 1f),
    centerY = frame.centerY.coerceIn(0f, 1f),
    sizeX = frame.sizeX.coerceIn(0f, 1f),
    sizeY = frame.sizeY.coerceIn(0f, 1f),
    rotationDeg = frame.rotationDeg,
    feather = frame.feather.coerceIn(0f, 1f),
    opacity = frame.opacity.coerceIn(0f, 1f),
    invert = frame.invert
)
