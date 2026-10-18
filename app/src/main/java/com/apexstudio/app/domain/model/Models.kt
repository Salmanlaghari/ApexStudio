package com.apexstudio.app.domain.model

import androidx.annotation.FloatRange
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable
import kotlin.math.pow

/** Normalized top-left-origin crop window persisted with a video clip. */
@Serializable
data class VideoCropSettings(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
    /** One of the ids in VideoCropPreset; "free" is the default. */
    val presetId: String = "free"
) {
    val width: Float get() = (right - left).coerceAtLeast(0.01f)
    val height: Float get() = (bottom - top).coerceAtLeast(0.01f)
    val isFullFrame: Boolean
        get() = left <= 0f && top <= 0f && right >= 1f && bottom >= 1f

    fun sanitized(): VideoCropSettings {
        // NaN survives coerceIn and would poison GL transforms, overlay bounds and hit testing.
        // Restore malformed geometry as an untouched frame, not an invisible layer.
        if (!left.isFinite() || !top.isFinite() || !right.isFinite() || !bottom.isFinite()) {
            return VideoCropSettings()
        }
        val l = left.coerceIn(0f, 0.99f)
        val t = top.coerceIn(0f, 0.99f)
        val r = right.coerceIn(l + 0.01f, 1f)
        val b = bottom.coerceIn(t + 0.01f, 1f)
        return copy(left = l, top = t, right = r, bottom = b)
    }
}

@Serializable
data class MediaClip(
    val id: String,
    val name: String,
    val uri: String,
    val durationMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long,
    val thumbnail: String? = null,
    val trackIndex: Int = 0,
    val type: ClipType = ClipType.VIDEO,
    val timelineOffsetMs: Long = 0L,
    // Speed ramping: speedMultiplier is the per-clip playback rate
    // (0.25 slow-mo → 8 fast-forward). The user picks one of the
    // SpeedPreset values OR a custom value via the speed panel.
    // speedCurve models acceleration across the clip — LINEAR keeps
    // a constant rate, EASE_IN/OUT ramps the rate, RAMP allows
    // start/end keyframe anchors via [rampStartSpeed, rampEndSpeed].
    val speedMultiplier: Float = 1f,
    val speedCurve: SpeedCurve = SpeedCurve.LINEAR,
    val rampStartSpeed: Float = 1f,
    val rampEndSpeed: Float = 1f,
    // Variable speed curve (PR-E): per-clip speed-ramping graph.
    // Empty = no variable curve — the clip plays at [speedMultiplier]
    // (constant). Non-empty = a list of (timeFraction, speed) control
    // points, timeFraction in 0..1 across the TRIMMED clip span, speed
    // in 0.1..10. The engine interpolates with monotone cubic (PCHIP)
    // so the curve never overshoots between points. Backwards
    // compatible: older project JSONs without this field deserialise
    // as empty (constant speed).
    val speedPoints: List<SpeedPoint> = emptyList(),
    // Per-clip audio gain (0 = silent, 1 = original, up to 2 = boosted).
    // Applied to the ExoPlayer preview of this clip and multiplied into
    // the export audio volume, so the Volume tool is real in both.
    val volume: Float = 1f,
    // Clean Voice (voice noise cleanup): per-clip spectral noise
    // suppression for the clip's embedded audio (hiss / fan hum / room
    // tone). Applied via the same DenoiseAudioProcessor in the preview
    // (DenoiseRenderersFactory audio sink) and the export (Transformer
    // Effects), so preview == export. Backwards-compatible: older project JSONs
    // without these fields deserialise as off.
    // NOTE: the engine is classical spectral gating, NOT RNNoise — see
    // SpectralDenoiser's KDoc for the honest reasoning.
    val denoiseEnabled: Boolean = false,
    val denoiseStrength: DenoiseStrength = DenoiseStrength.MEDIUM,
    // Background replace (PR-G): per-clip MediaPipe person
    // segmentation. Null = original background (no processing).
    // When set, [uri] points at the processed video file (same
    // duration / resolution as the original) and
    // [backgroundReplace.sourceUri] points at the untouched
    // original, so "Original" restores it and re-applying a
    // different treatment always starts from the original —
    // never from an already-processed file. Preview and export
    // both read [uri], so they show the identical pixels by
    // construction. Backwards-compatible: older project JSONs
    // without this field deserialise as null (original).
    val backgroundReplace: BackgroundReplaceSettings? = null,
    // Animated transform track — empty by default. When populated
    // by the Keyframe panel, the GL effect applies the interpolated
    // translate / scale / rotation / opacity on every preview frame
    // and bakes the same into the exported video.
    val keyframes: KeyframeTrack = KeyframeTrack(),
    // Caption / title overlays attached to this clip. Each overlay
    // carries its own text, style, and a normalised (0..1) anchor
    // inside the video frame so the preview (Compose layer) and the
    // export (TextOverlayGlEffect) render the text at exactly the
    // same relative position and size.
    val textOverlays: List<TextOverlay> = emptyList(),
    val stickers: List<StickerOverlay> = emptyList(),
    val gpuFilterConfig: com.apexstudio.app.data.filter.GpuFilterConfig = com.apexstudio.app.data.filter.GpuFilterConfig(),
    // Per-clip normalized video crop. Absent in older project JSON; the
    // full-frame default preserves their prior preview/export behavior.
    val videoCrop: VideoCropSettings = VideoCropSettings(),
    // PiP overlay transform — only meaningful when type == ClipType.OVERLAY.
    // The live preview (PipOverlayCanvas) and the export (PipOverlayGlEffect)
    // resolve these against the same frame geometry, so what you drag on
    // screen is exactly what bakes into the MP4:
    // - pipX / pipY: normalised centre of the overlay (0..1; x from the
    //   left, y from the top). Defaults put the box in the classic
    //   bottom-end PiP corner.
    // - pipScale: overlay width as a fraction of the frame width
    //   (0.05..0.9). The box keeps a 16:9 aspect, matching the export.
    // - pipRotationDeg: clockwise rotation in degrees.
    // - pipOpacity: 0..1 alpha.
    // Default values preserve the pre-editing bottom-end PiP look, and
    // keep older saved projects (missing fields) deserialising unchanged.
    val pipX: Float = 0.85f,
    val pipY: Float = 0.85f,
    val pipScale: Float = 0.25f,
    val pipRotationDeg: Float = 0f,
    val pipOpacity: Float = 1f,
    // Overlay (PiP) properties-panel fields — only meaningful when
    // type == ClipType.OVERLAY. All normalised / resolution-independent
    // so preview and export resolve the same numbers:
    // - overlayAspect: intrinsic media aspect (width / height) of the
    //   overlay source. The preview box and the export PiP box both
    //   derive their height from this (pipScale is the width fraction),
    //   so a portrait photo is never stretched into the legacy 16:9
    //   box. 16:9 default preserves older projects.
    // - overlayBlend: how the overlay composites over V1 (Normal /
    //   Screen / Multiply / Overlay), implemented in the preview
    //   (saveLayer blendMode) and the export shader.
    // - overlayBorderWidth: border thickness as a fraction of the
    //   overlay box width (0 = no border). overlayBorderColor: ARGB.
    // - overlayShadow: soft drop-shadow strength 0..1 (0 = none).
    // - overlayFlipH / overlayFlipV: mirror the overlay.
    // - overlayVolume: video-overlay audio gain 0..2 (preview
    //   overlayPlayer volume). Default 0 keeps older projects muted
    //   (previous behaviour); new video overlays are created at 1.
    // Backwards-compatible: older project JSONs without these fields
    // deserialise as the defaults above.
    val overlayAspect: Float = 16f / 9f,
    val overlayBlend: OverlayBlend = OverlayBlend.NORMAL,
    val overlayBorderWidth: Float = 0f,
    val overlayBorderColor: Int = 0xFFFFFFFF.toInt(),
    val overlayShadow: Float = 0f,
    val overlayFlipH: Boolean = false,
    val overlayFlipV: Boolean = false,
    val overlayVolume: Float = 0f,
    // Photo-editing tools for IMAGE clips (crop / adjust / LUT filter /
    // rotate+flip). Stored per-clip so every photo keeps its own edits;
    // the preview renderer and the export path both read this single
    // source of truth. Default = untouched photo. Backwards-compatible:
    // older project JSONs without this field deserialise as default.
    val photoEdit: PhotoEditSettings = PhotoEditSettings(),
    // Shape mask (CapCut-style Mask tool). Null = no mask. When set,
    // the preview darkens everything outside the mask shape and the
    // export bakes the same mask via MaskGlEffect (V1) or
    // PipOverlayGlEffect (V2 overlay). Backwards-compatible: older
    // project JSONs without this field deserialise as null.
    val mask: VideoMask? = null
) {
    /**
     * True when this overlay clip (type == [ClipType.OVERLAY]) should be
     * visible at [timeMs] on the timeline: from its [timelineOffsetMs]
     * for exactly its trimmed duration. The preview canvas and the
     * export visibility check both use this single helper so they can
     * never disagree.
     */
    fun isOverlayActiveAt(timeMs: Long): Boolean {
        if (type != ClipType.OVERLAY) return false
        val trimmedMs = (trimEndMs - trimStartMs).coerceAtLeast(0L)
        val endMs = if (trimEndMs == Long.MAX_VALUE) Long.MAX_VALUE
        else timelineOffsetMs + trimmedMs
        return timeMs in timelineOffsetMs until endMs
    }

    /** True when this clip has a variable speed curve (2+ points). */
    fun hasSpeedCurve(): Boolean = speedPoints.size >= 2

    /**
     * The clip's length on the timeline (output time). Without a speed
     * curve this is just the trimmed span. With a curve it is the
     * integral of 1/speed over the trimmed span — a 0.5x clip plays
     * twice as long, a 2x clip half as long. Deliberately does NOT
     * account for the constant [speedMultiplier]: the timeline never
     * has (pre-PR-E behaviour), and changing that would alter every
     * existing project with a non-1x constant speed.
     */
    fun effectiveDurationMs(): Long =
        SpeedCurveMath.effectiveDurationMs(trimEndMs - trimStartMs, speedPoints)
}

/**
 * Normalised crop rectangle for a photo clip (all values 0..1 in the
 * *displayed* (post-rotation) image space).
 *
 * Null on [PhotoEditSettings.crop] means "full frame" — the rect is
 * only stored once the user actually crops.
 */
@Serializable
data class PhotoCropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    fun width(): Float = (right - left).coerceAtLeast(0.01f)
    fun height(): Float = (bottom - top).coerceAtLeast(0.01f)
    fun aspect(): Float = width() / height().coerceAtLeast(0.01f)

    fun isFullFrame(): Boolean =
        left <= 0f && top <= 0f && right >= 1f && bottom >= 1f

    companion object {
        fun full() = PhotoCropRect(0f, 0f, 1f, 1f)

        /**
         * Largest centred rect of [targetAspect] (width / height) that
         * fits inside the unit square — used when the user picks an
         * aspect preset (1:1, 4:5, 16:9, 9:16, 3:2).
         */
        fun centeredForAspect(targetAspect: Float): PhotoCropRect {
            val aspect = targetAspect.coerceAtLeast(0.01f)
            return if (aspect >= 1f) {
                // Wider than tall: full width, centred vertically.
                val h = (1f / aspect).coerceIn(0.01f, 1f)
                val t = (1f - h) / 2f
                PhotoCropRect(0f, t, 1f, t + h)
            } else {
                // Taller than wide: full height, centred horizontally.
                val w = aspect.coerceIn(0.01f, 1f)
                val l = (1f - w) / 2f
                PhotoCropRect(l, 0f, l + w, 1f)
            }
        }
    }
}

/**
 * All photo-editing state for one IMAGE [MediaClip].
 *
 * Render order (identical in the live preview and the export so WYSIWYG
 * holds): decode (+EXIF) → rotate/flip → crop → sharpen → colour
 * adjustments → LUT filter.
 */
@Serializable
data class PhotoEditSettings(
    /** Null = full frame. */
    val crop: PhotoCropRect? = null,
    /** Aspect preset id: "free", "1:1", "4:5", "16:9", "9:16", "3:2". */
    val cropAspectPreset: String? = null,
    /** Brightness / contrast / saturation / warmth / sharpness. */
    val adjustments: VideoAdjustments = VideoAdjustments(),
    /** LUT preset id from the app's filter manifest; null = none. */
    val filterId: String? = null,
    val filterIntensity: Float = 1f,
    /** Quarter-turns clockwise (normalised to 0..3). */
    val rotationSteps: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    /**
     * Phase 4: Ken Burns pan/zoom animation (flavioarfaria/KenBurnsView,
     * Apache-2.0). When true the photo gently zooms/pans in preview and
     * the export bakes the motion into the still-video.
     */
    val kenBurns: Boolean = false
) {
    val normalizedRotationSteps: Int
        get() = ((rotationSteps % 4) + 4) % 4

    val isDefault: Boolean
        get() = (crop == null || crop.isFullFrame()) &&
                adjustments.isDefault &&
                filterId == null &&
                normalizedRotationSteps == 0 &&
                !flipHorizontal &&
                !flipVertical

    companion object {
        /** Aspect presets offered by the Crop tool (id → width/height). Null ratio = free. */
        val ASPECT_PRESETS: List<Pair<String, Float?>> = listOf(
            "free" to null,
            "1:1" to 1f,
            "4:5" to 0.8f,
            "16:9" to (16f / 9f),
            "9:16" to (9f / 16f),
            "3:2" to 1.5f
        )

        fun aspectRatioForPreset(presetId: String?): Float? =
            ASPECT_PRESETS.firstOrNull { it.first == presetId }?.second
    }
}

/**
 * A text overlay (caption / title) rendered on top of a video clip.
 *
 * Position and font scale are *normalised* to the video frame:
 * [x], [y] are the centre of the text as fractions of the frame
 * width / height (0..1), and [sizeScale] multiplies a base font
 * size of ~7% of the frame height. Because both the editor preview
 * and the export GL effect resolve these against the same frame
 * geometry, what you drag on screen is exactly what bakes into the
 * MP4.
 *
 * Colours are stored as 0xAARRGGBB longs so the JSON project file
 * stays readable; null [bgArgb] means no pill behind the text.
 */
/**
 * One keyframe on a text overlay's [VideoMask]: pins the mask centre
 * (normalised frame coords) at an absolute timeline position. The
 * preview ([AnimatedTextOverlayView]) and the export
 * ([TextSpriteRenderer] via [TextOverlayGlEffect]) both interpolate
 * the centre linearly between neighbouring mask keyframes and clamp
 * to the endpoints outside them — so a travelling wipe (the Text
 * Reveal template) previews exactly as it bakes.
 */
@Serializable
data class TextMaskKeyframe(
    val id: String,
    val timeMs: Long,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f
) {
    companion object {
        fun at(timeMs: Long, centerX: Float, centerY: Float, id: String = java.util.UUID.randomUUID().toString()) =
            TextMaskKeyframe(id = id, timeMs = timeMs, centerX = centerX, centerY = centerY)
    }
}

@Serializable
data class TextOverlay(
    val id: String,
    val text: String = "Text",
    // Normalised centre (0..1) inside the video frame.
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val sizeScale: Float = 1f,
    val colorArgb: Long = 0xFFFFFFFFL,
    val bgArgb: Long? = null,
    val strokeColorArgb: Long? = null,
    val shadowColorArgb: Long? = null,
    val fontFamily: String = "sans",
    val isItalic: Boolean = false,
    val isBold: Boolean = true,
    val presetId: String? = null,
    // Active window on the clip's timeline (ms). Defaults to the
    // whole clip.
    val startMs: Long = 0L,
    val endMs: Long = Long.MAX_VALUE,
    val animationType: String = "NONE",
    val animationDurationMs: Long = 800L,
    // Exit animation timed at the END of the overlay's window
    // (FADE_OUT, SLIDE_DOWN, SHRINK, or NONE).
    val outroAnimationType: String = "NONE",
    val outroAnimationDurationMs: Long = 600L,
    val rotationDeg: Float = 0f,
    val opacity: Float = 1f,
    // Text block alignment relative to the (x, y) anchor:
    // CENTER keeps the classic behaviour; LEFT pins the block's left
    // edge at the anchor, RIGHT pins the right edge.
    val textAlign: String = "CENTER",
    // Extra letter spacing in em units (-0.2 .. 1.0).
    val letterSpacingEm: Float = 0f,
    // Vertical gradient fill: both non-null to enable.
    val gradientStartArgb: Long? = null,
    val gradientEndArgb: Long? = null,
    val keyframes: KeyframeTrack = KeyframeTrack(),
    // Shape mask (same [VideoMask] as video clips). Null = no mask.
    // When set, only the masked region of the text is visible — the
    // preview clips the text and the export bakes the same clip via
    // the sprite renderer. Backwards-compatible: older project JSONs
    // without this field deserialise as null.
    val mask: VideoMask? = null,
    // Keyframes animating the mask centre over time (travelling wipe).
    // Empty = the mask stays at its static [mask] centre.
    val maskKeyframes: List<TextMaskKeyframe> = emptyList(),
    // Optional static karaoke span. The source fingerprint prevents stale offsets
    // from highlighting unrelated text after a manual caption edit.
    val highlightStartUtf16: Int? = null,
    val highlightEndUtf16: Int? = null,
    val highlightSourceFingerprint: String? = null,
    val highlightColorArgb: Long = 0xFFFFFF00L
) {
    fun isActiveAt(timeMs: Long): Boolean = timeMs in startMs..endMs

    /**
     * The mask to render at [timeMs]: the static [mask] with its
     * centre replaced by the interpolated mask-keyframe centre.
     * Null when no mask is set. Keyframes interpolate linearly and
     * clamp to the endpoints outside their range.
     */
    fun effectiveMaskAt(timeMs: Long): VideoMask? {
        val base = mask ?: return null
        if (maskKeyframes.isEmpty()) return base
        val sorted = maskKeyframes.sortedBy { it.timeMs }
        val before = sorted.lastOrNull { it.timeMs <= timeMs }
        val after = sorted.firstOrNull { it.timeMs >= timeMs }
        if (before == null) return base.copy(centerX = sorted.first().centerX, centerY = sorted.first().centerY)
        if (after == null) return base.copy(centerX = sorted.last().centerX, centerY = sorted.last().centerY)
        if (before.id == after.id) return base.copy(centerX = before.centerX, centerY = before.centerY)
        val span = (after.timeMs - before.timeMs).coerceAtLeast(1L)
        val t = ((timeMs - before.timeMs).toFloat() / span.toFloat()).coerceIn(0f, 1f)
        return base.copy(
            centerX = before.centerX + (after.centerX - before.centerX) * t,
            centerY = before.centerY + (after.centerY - before.centerY) * t
        )
    }

    companion object {
        fun of(
            id: String = java.util.UUID.randomUUID().toString(),
            text: String = "Text",
            x: Float = 0.5f,
            y: Float = 0.35f,
            sizeScale: Float = 1f,
            colorArgb: Long = 0xFFFFFFFFL,
            bgArgb: Long? = null,
            strokeColorArgb: Long? = null,
            shadowColorArgb: Long? = null,
            fontFamily: String = "sans",
            isItalic: Boolean = false,
            isBold: Boolean = true,
            presetId: String? = null,
            animationType: String = "NONE",
            animationDurationMs: Long = 800L,
            outroAnimationType: String = "NONE",
            outroAnimationDurationMs: Long = 600L,
            rotationDeg: Float = 0f,
            opacity: Float = 1f,
            textAlign: String = "CENTER",
            letterSpacingEm: Float = 0f,
            gradientStartArgb: Long? = null,
            gradientEndArgb: Long? = null,
            keyframes: KeyframeTrack = KeyframeTrack(),
            startMs: Long = 0L,
            endMs: Long = Long.MAX_VALUE,
            mask: VideoMask? = null,
            maskKeyframes: List<TextMaskKeyframe> = emptyList()
        ): TextOverlay = TextOverlay(
            id = id, text = text, x = x, y = y, sizeScale = sizeScale,
            colorArgb = colorArgb, bgArgb = bgArgb, strokeColorArgb = strokeColorArgb,
            shadowColorArgb = shadowColorArgb, fontFamily = fontFamily,
            isItalic = isItalic, isBold = isBold, presetId = presetId,
            animationType = animationType, animationDurationMs = animationDurationMs,
            outroAnimationType = outroAnimationType,
            outroAnimationDurationMs = outroAnimationDurationMs,
            rotationDeg = rotationDeg, opacity = opacity,
            textAlign = textAlign, letterSpacingEm = letterSpacingEm,
            gradientStartArgb = gradientStartArgb, gradientEndArgb = gradientEndArgb,
            keyframes = keyframes, startMs = startMs, endMs = endMs,
            mask = mask, maskKeyframes = maskKeyframes
        )
    }
}

enum class ClipType { VIDEO, OVERLAY, AUDIO, SFX, IMAGE }

/**
 * Blend mode for an overlay (PiP) clip over the V1 frame.
 * Implemented identically in the preview (saveLayer blendMode) and
 * the export ([PipOverlayGlEffect] fragment shader), so WYSIWYG holds.
 */
@Serializable
enum class OverlayBlend {
    NORMAL,
    SCREEN,
    MULTIPLY,
    OVERLAY;

    val label: String get() = when (this) {
        NORMAL -> "Normal"
        SCREEN -> "Screen"
        MULTIPLY -> "Multiply"
        OVERLAY -> "Overlay"
    }

    /** ID consumed by the export shader's uPipBlend (do NOT use ordinal). */
    val glId: Int get() = when (this) {
        NORMAL -> 0
        SCREEN -> 1
        MULTIPLY -> 2
        OVERLAY -> 3
    }
}

/**
 * Shape of a [VideoMask].
 *
 * Ten top-level mask modes (Bug 4). The three original values are kept
 * FIRST and unchanged so previously saved projects deserialise exactly
 * as before, and each keeps its numeric order so the export shader's
 * `uShape` ids 0/1/2 stay stable:
 *
 *   0 rect | 1 ellipse | 2 linear(horizontal wipe) | 3 circle |
 *   4 heart | 5 star | 6 triangle | 7 mirror | 8 pen | 9 blur faces
 *
 * `LINEAR` is the Horizontal mode; its legacy enum name is deliberate
 * project-format compatibility — [label] is what the UI shows.
 */
@Serializable
enum class MaskShape {
    RECTANGLE,
    ELLIPSE,
    /** Horizontal gradient wipe: the left side is kept, feathered edge. */
    LINEAR,
    /** True circle: width and height are forced equal, unlike [ELLIPSE]. */
    CIRCLE,
    /** Heart silhouette, classic Valentine-style cut-out. */
    HEART,
    /** Five-pointed star. */
    STAR,
    /** Upward-pointing triangle. */
    TRIANGLE,
    /** Vertical mirror: the frame folds about the mask's centre line. */
    MIRROR,
    /** Freeform pen: the region the user draws by hand. */
    DRAW,
    /** Softens a circular region over detected faces. */
    BLUR_FACE;

    val label: String get() = when (this) {
        RECTANGLE -> "Rectangle"
        ELLIPSE -> "Ellipse"
        LINEAR -> "Horizontal"
        CIRCLE -> "Circle"
        HEART -> "Heart"
        STAR -> "Star"
        TRIANGLE -> "Triangle"
        MIRROR -> "Mirror"
        DRAW -> "Pen"
        BLUR_FACE -> "Blur Faces"
    }

    /**
     * Value passed to the export shader's `uShape` uniform — the index
     * in this declaration order. Agrees with the enum ordinal today, and
     * `MaskShapeTest` pins it so a reordering cannot silently remap an
     * already-exported mask.
     */
    val shaderId: Float get() = ordinal.toFloat()
}

/**
 * A shape mask applied to a video clip (CapCut-style Mask tool).
 *
 * All geometry is normalised to the clip's own video frame (0..1):
 * [centerX]/[centerY] is the mask centre, [sizeX]/[sizeY] the full
 * width/height as fractions of the frame. [feather] (0..1) softens
 * the mask edge, [invert] keeps everything OUTSIDE the shape, and
 * [opacity] fades the whole masked result.
 *
 * The preview ([MaskPreviewOverlay]) and the export ([MaskGlEffect]
 * / [PipOverlayGlEffect]) resolve the same numbers against the frame
 * geometry, so what the user drags on screen is exactly what bakes
 * into the MP4.
 */
@Serializable
data class VideoMask(
    val shape: MaskShape = MaskShape.ELLIPSE,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val sizeX: Float = 0.6f,
    val sizeY: Float = 0.6f,
    val feather: Float = 0.15f,
    val invert: Boolean = false,
    val opacity: Float = 1f,
    val enabled: Boolean = true,
    // Bug 4 control (rotation): clockwise rotation of the mask geometry,
    // in degrees. The preview rotates the shape path and the export
    // shader rotates the same point set, so both agree.
    val rotationDeg: Float = 0f,
    // Bug 4 control (presets): id of the MaskPreset last applied, so the
    // picker can highlight the active recipe. Null = hand-tuned mask.
    val presetId: String? = null,
    // Bug 4 control (keyframe animation): clip-relative ms keyframes
    // animating centre / size / rotation / feather / opacity / invert.
    // Empty = the static values above are used unchanged.
    val maskKeyframes: List<MaskKeyframe> = emptyList(),
    // Bug 4 control (draw/pen): normalised shape-space points of the
    // hand-drawn region for MaskShape.DRAW, in draw order. Under 3 points
    // is not an area - both consumers treat it as "no region" until the
    // user has closed a shape. Empty for every other mode.
    val drawPoints: List<MaskPoint> = emptyList()
) {
    fun withCenter(x: Float, y: Float) = copy(
        centerX = x.coerceIn(0f, 1f),
        centerY = y.coerceIn(0f, 1f)
    )

    fun withSize(sx: Float, sy: Float) = copy(
        sizeX = sx.coerceIn(0.05f, 1f),
        sizeY = sy.coerceIn(0.05f, 1f)
    )

    companion object {
        fun default(shape: MaskShape = MaskShape.ELLIPSE) = VideoMask(shape = shape)
    }
}

@Serializable
enum class SpeedCurve {
    LINEAR,        // constant speed across the clip
    EASE_IN,       // accelerate from start speed → end speed
    EASE_OUT,      // decelerate from start speed → end speed
    EASE_IN_OUT,   // smooth accel + decel
    RAMP           // keyframe-driven (start + end)
}

/**
 * Clean Voice strength (per-clip voice noise cleanup). Maps to the
 * spectral-gating parameters in [SpectralDenoiser]: LIGHT is gentle
 * (keeps almost everything, removes only clear hiss), MEDIUM is the
 * balanced default, STRONG suppresses aggressively (may thin very
 * quiet speech — that is the documented trade-off of a classical
 * suppressor). This is NOT an RNNoise strength — see SpectralDenoiser's
 * KDoc for why this feature uses classical spectral gating instead of
 * RNNoise.
 */
/**
 * Background replace mode (PR-G): what replaces the background
 * behind the segmented person. ORIGINAL means "no processing" —
 * it is represented by `MediaClip.backgroundReplace == null`,
 * never by a settings object with this mode.
 */
@Serializable
enum class BackgroundReplaceMode {
    /** Blur the background (strength = [BackgroundReplaceSettings.blurStrength]). */
    BLUR,
    /** Solid colour ([BackgroundReplaceSettings.colorArgb]). */
    COLOR,
    /** Another image, cover-cropped to the frame ([BackgroundReplaceSettings.imageUri]). */
    IMAGE
}

/**
 * Per-clip background-replace settings (PR-G, MediaPipe person
 * segmentation). Persisted on [MediaClip.backgroundReplace].
 *
 * - [sourceUri] is the untouched original clip. The processed
 *   file lives at [MediaClip.uri]. Keeping the original here
 *   (instead of deriving it) is what makes "Original" a lossless
 *   restore and re-apply idempotent.
 * - [blurStrength] 0..1 maps to the blur divisor in
 *   [com.apexstudio.app.data.background.BackgroundReplaceMath]
 *   (0 = light blur, 1 = strongest).
 * - [colorArgb] is 0xAARRGGBB; only read in COLOR mode.
 * - [imageUri] is the picked replacement image; only read in IMAGE mode.
 *
 * Use [sanitized] before processing: it clamps every knob so the
 * engine never sees an out-of-range value.
 */
@Serializable
data class BackgroundReplaceSettings(
    val mode: BackgroundReplaceMode = BackgroundReplaceMode.BLUR,
    val blurStrength: Float = 0.5f,
    val colorArgb: Long = 0xFF000000L,
    val imageUri: String? = null,
    val sourceUri: String = ""
) {
    /** Clamped copy: blur in 0..1, colour alpha forced opaque. */
    fun sanitized(): BackgroundReplaceSettings = copy(
        blurStrength = blurStrength.coerceIn(0f, 1f),
        // A transparent "solid colour" would punch a hole to black in
        // the encoder; force opaque so COLOR always means a real colour.
        colorArgb = colorArgb or 0xFF000000L
    )

    /**
     * Stable cache key for the processed output file: identical
     * settings + identical source map to the same file, so a
     * re-apply with unchanged settings reuses the file instead of
     * re-processing. Any knob change yields a different key.
     */
    fun cacheKey(): String = listOf(
        sourceUri,
        mode.name,
        blurStrength.toString(),
        colorArgb.toString(),
        imageUri ?: ""
    ).joinToString("|")

    companion object {
        /** Default first-use settings: medium blur. */
        fun defaultFor(sourceUri: String) = BackgroundReplaceSettings(
            mode = BackgroundReplaceMode.BLUR,
            blurStrength = 0.5f,
            sourceUri = sourceUri
        )
    }
}

@Serializable
enum class DenoiseStrength {
    LIGHT,
    MEDIUM,
    STRONG
}

/**
 * One control point of a variable speed curve ([MediaClip.speedPoints]).
 * [timeFraction] is 0..1 across the TRIMMED clip span (0 = first frame,
 * 1 = last frame). [speed] is the playback rate at that point (0.1..10).
 * [sharp] marks an abrupt segment: when true, the segment ENDING at
 * this point interpolates linearly (a straight, near-vertical drop /
 * rise, CapCut velocity-edit style) instead of the default smooth
 * PCHIP curve. Lets one curve mix smooth Bezier-like ramps with
 * sharp cuts and rapid fast/slow flicker (alternating sharp points).
 */
@Serializable
data class SpeedPoint(
    val timeFraction: Float,
    val speed: Float,
    val sharp: Boolean = false
)

enum class SpeedPreset(val label: String, val multiplier: Float) {
    QUARTER("0.25x", 0.25f),
    HALF("0.5x", 0.5f),
    NORMAL("1x", 1f),
    DOUBLE("2x", 2f),
    QUAD("4x", 4f),
    FAST("8x", 8f);

    companion object {
        fun nearest(value: Float): SpeedPreset =
            values().minBy { kotlin.math.abs(it.multiplier - value) }
    }
}

@Serializable
data class Project(
    val id: String,
    val name: String,
    val durationMs: Long,
    val resolution: String = "4K",
    val fps: Int = 60,
    val clips: List<MediaClip> = emptyList(),
    val audioTracks: List<AudioTrack> = emptyList(),
    /**
     * Last transmission template the user applied to this project.
     * Pure metadata — when the editor opens the project it preloads
     * this template's LUT + FX + intensity so the user starts with
     * the look they had last time. Null means "no preset applied"
     * (the LUT / FX panels are still empty until the user picks one).
     *
     * Default-null + a default-value field means the JSON project
     * file stays backwards-compatible: older saved projects without
     * this field deserialise as null and behave exactly as before.
     */
    val lastTransmissionTemplateId: String? = null,
    // Transition hint chosen by the Transmission panel. Project-level
    // for now (per-clip transitions are a follow-up feature); null
    // means "no transition preset was picked". Persisted alongside
    // lastTransmissionTemplateId so the choice survives an app
    // restart.
    val lastTransitionType: String? = null,
    val lastTransitionDurationMs: Long = 500L,
    val transitions: List<ClipTransition> = emptyList(),
    val stickers: List<StickerOverlay> = emptyList(),
    val coverFrameMs: Long? = null,
    val coverCustomUri: String? = null,
    /**
     * Auto-detected source video aspect ratio (width / height).
     * Set automatically when the first video clip is added; null means
     * "not detected yet" (falls back to 16:9). Used to set the preview
     * frame and export output ratio without manual user setup.
     */
    val videoAspectRatio: Float? = null,
    /**
     * Phase 3: last time this project was saved/edited (epoch ms).
     * Drives the History tab ordering. Defaults to 0 for projects
     * saved before this field existed (they sort last).
     */
    val lastEditedMs: Long = 0L,
    /**
     * Phase 3: user-visible draft label, e.g. "Draft" vs "Project".
     * Pure metadata for the History/Drafts UI.
     */
    val isDraft: Boolean = false
)

/**
 * One volume keyframe on an [AudioTrack]: an absolute volume value
 * pinned at a timeline-absolute [timeMs]. The track's effective
 * volume interpolates linearly between adjacent pins and clamps to
 * the first/last pin outside the pinned range. Empty list = the
 * scalar [AudioTrack.volume] applies (backwards compatible with
 * projects saved before volume keyframes existed).
 */
@Serializable
data class VolumeKeyframe(
    val id: String,
    val timeMs: Long,
    val volume: Float
)

@Serializable
data class AudioTrack(
    val id: String,
    val name: String,
    val uri: String,
    val volume: Float = 0.75f,
    val isMuted: Boolean = false,
    val isSolo: Boolean = false,
    /**
     * CapCut-style volume animation: per-point volume keyframes over
     * timeline time (distinct from fade in/out, which are envelope
     * ramps at the track edges). Empty = scalar [volume] applies.
     */
    val volumeKeyframes: List<VolumeKeyframe> = emptyList(),
    // Per-track trim + fade (all in ms, relative to the source media).
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    /** Timeline placement in project time; independent of source trim. */
    val startTimeMs: Long = 0L,
    /** Original source length; 0 means legacy data only knows trimEndMs. */
    val sourceDurationMs: Long = 0L,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    /**
     * Persisted track kind: drives the timeline's A1 (music) / A2 (voice)
     * lane split and the mixer fader labels ("Video", "Music",
     * "Voiceover / SFX"). Defaults to MUSIC so pre-existing saved projects
     * decode unchanged. Voice recordings are stored as SFX.
     */
    val kind: Kind = Kind.MUSIC
) {
    /**
     * The kind of audio track, used by the mixer UI to label faders
     * ("Video", "Music", "Voiceover / SFX") and to pick a default
     * colour strip. SFX tracks default to a louder volume than music.
     */
    @Serializable
    enum class Kind { ORIGINAL_VIDEO, MUSIC, SFX }

    /** Full source boundary, falling back to the pre-field trim end. */
    val sourceEndMs: Long
        get() = sourceDurationMs.takeIf { it > 0L } ?: trimEndMs

    /** Per-track volume multiplier in 0..1, with mute applied. */
    fun effectiveVolume(): Float = if (isMuted) 0f else volume.coerceIn(0f, 1f)

    /**
     * Interpolated absolute volume at timeline [timeMs] from the
     * volume keyframes; falls back to the scalar [volume] when no
     * volume keyframes exist.
     */
    fun volumeAt(timeMs: Long): Float =
        interpolateVolumeKeyframes(volumeKeyframes.sortedBy { it.timeMs }, volume, timeMs)

    /** [volumeAt] with mute applied (0 when muted). */
    fun effectiveVolumeAt(timeMs: Long): Float =
        if (isMuted) 0f else volumeAt(timeMs).coerceIn(0f, 1f)

    companion object {
        /**
         * Pure interpolation over a pre-sorted volume-keyframe list.
         * The export mixer calls this per sample with a list sorted
         * once per track (sorting per sample would allocate on the
         * audio hot path). [fallback] applies when [sorted] is empty.
         */
        fun interpolateVolumeKeyframes(
            sorted: List<VolumeKeyframe>,
            fallback: Float,
            timeMs: Long
        ): Float {
            if (sorted.isEmpty()) return fallback
            if (timeMs <= sorted.first().timeMs) return sorted.first().volume
            if (timeMs >= sorted.last().timeMs) return sorted.last().volume
            for (i in 0 until sorted.size - 1) {
                val a = sorted[i]
                val b = sorted[i + 1]
                if (timeMs in a.timeMs..b.timeMs) {
                    val span = (b.timeMs - a.timeMs).coerceAtLeast(1L)
                    val frac = (timeMs - a.timeMs).toFloat() / span.toFloat()
                    return a.volume + (b.volume - a.volume) * frac
                }
            }
            return fallback
        }
    }
}

/**
 * One keyframe on a clip. Holds the four animated transform
 * properties every editor supports (translateX, translateY, scale,
 * rotation, opacity) at a single point on the clip's local timeline.
 *
 * `timeMs` is the absolute timeline position (in ms) at which the
 * keyframe's value is "pinned". The [KeyframeTrack] interpolates
 * between adjacent keyframes using the curve shape declared on the
 * later of the two.
 */
@Serializable
data class Keyframe(
    val id: String,
    val timeMs: Long,
    val translateX: Float = 0f,
    val translateY: Float = 0f,
    val scale: Float = 1f,
    val rotationDeg: Float = 0f,
    val opacity: Float = 1f,
    val volume: Float = 1f,
    val effectIntensity: Float = 1f,
    val filterIntensity: Float = 1f,
    /**
     * Per-clip saturation factor pinned by this keyframe (multiplies
     * the project-global saturation; 1f = global grade unchanged,
     * 0f = fully black-and-white). Null = this keyframe does not pin
     * saturation (old keyframes, and overlay/text/sticker keyframes
     * which never touch saturation).
     *
     * Saturation interpolates only over the subsequence of keyframes
     * with a non-null value — see [KeyframeTrack.interpolateAt].
     * Null vs 1f is NOT an inconsistency: null means "no pin" (the
     * keyframe is invisible to the ramp), while 1f is an explicit
     * pin at the current effective factor. New keyframes created via
     * `addKeyframe` / upsert pin the current effective saturation,
     * so a scale-only keyframe preserves the ramp instead of
     * leaving a hole that would clamp backwards.
     */
    val saturation: Float? = null,
    val curve: KeyframeCurve = KeyframeCurve.LINEAR
) {
    companion object {
        fun identity(timeMs: Long, id: String = java.util.UUID.randomUUID().toString()) =
            Keyframe(id = id, timeMs = timeMs)
    }
}

/**
 * A single keyframeable parameter on a clip, for CapCut-style
 * auto-keyframing: changing [KeyframeParam] at a playhead position
 * with no keyframe nearby auto-generates one there (see
 * `upsertKeyframeParam`). First keyframe stays manual.
 */
enum class KeyframeParam {
    TRANSLATE_X,
    TRANSLATE_Y,
    SCALE,
    ROTATION,
    OPACITY,
    VOLUME,
    EFFECT_INTENSITY,
    FILTER_INTENSITY,
    SATURATION
}

@Serializable
enum class KeyframeCurve {
    /**
     * LINEAR is a straight line; the others are cheap analytic
     * approximations good enough for editor previews — a real CapCut
     * implementation would back these with an ML spline, but the math
     * here is the same `easeOutCubic` / `easeInOutQuad` family every
     * editor uses.
     *
     * SPRING / OVERSHOOT give the buttery CapCut/TikTok feel: springy
     * entrances with a soft overshoot settle, like CapCut's "Spring"
     * and "Bounce" combo animations.
     */
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT,
    BEZIER,
    HOLD,
    /** Damped spring oscillation that settles at 1.0 — CapCut "Spring" feel. */
    SPRING,
    /** Ease-out with a single overshoot past 1.0 — CapCut "Pop/Bounce" feel. */
    OVERSHOOT,
}

/**
 * Full animated-transform track attached to a single clip. The
 * track stores the keyframes sorted by time and exposes
 * [interpolateAt] which returns the (translate, scale, rotation,
 * opacity, volume, effectIntensity, filterIntensity) tuple to render at the given ms.
 */
@Serializable
data class KeyframeTrack(
    val keyframes: List<Keyframe> = emptyList()
) {
    fun isEmpty(): Boolean = keyframes.isEmpty()

    fun sorted(): KeyframeTrack = copy(keyframes = keyframes.sortedBy { it.timeMs })

    /**
     * Return the animated transform at [timeMs] (interpolated from
     * the surrounding keyframes). When no keyframes exist, returns
     * the identity transform (translate 0, scale 1, rotation 0,
     * opacity 1). When [timeMs] is before the first or after the
     * last keyframe, clamps to that endpoint.
     */
    fun interpolateAt(timeMs: Long): AnimatedTransform {
        if (keyframes.isEmpty()) return AnimatedTransform.Identity
        val sorted = keyframes.sortedBy { it.timeMs }
        val base = if (timeMs <= sorted.first().timeMs) {
            val k = sorted.first()
            AnimatedTransform(
                translateX = k.translateX,
                translateY = k.translateY,
                scale = k.scale,
                rotationDeg = k.rotationDeg,
                opacity = k.opacity,
                volume = k.volume,
                effectIntensity = k.effectIntensity,
                filterIntensity = k.filterIntensity
            )
        } else if (timeMs >= sorted.last().timeMs) {
            val k = sorted.last()
            AnimatedTransform(
                translateX = k.translateX,
                translateY = k.translateY,
                scale = k.scale,
                rotationDeg = k.rotationDeg,
                opacity = k.opacity,
                volume = k.volume,
                effectIntensity = k.effectIntensity,
                filterIntensity = k.filterIntensity
            )
        } else {
            var pair: AnimatedTransform? = null
            for (i in 0 until sorted.size - 1) {
                val a = sorted[i]
                val b = sorted[i + 1]
                if (timeMs in a.timeMs..b.timeMs) {
                    pair = interpolatePair(a, b, timeMs)
                    break
                }
            }
            pair ?: AnimatedTransform.Identity
        }
        // Saturation interpolates over its own pinned subsequence
        // (keyframes with a non-null saturation), not over the
        // bracketing pair — a scale-only keyframe must not reset the
        // saturation ramp. Null = nothing pins saturation.
        return base.copy(saturation = interpolateSaturation(timeMs))
    }

    /**
     * Interpolated saturation factor at [timeMs] over the subsequence
     * of keyframes with a non-null [Keyframe.saturation], using the
     * later pin's curve. Null when no keyframe pins saturation.
     *
     * Boundary semantics (CapCut per-parameter behaviour):
     * - BEFORE the first pin the clip keeps its BASE saturation
     *   (factor 1f): a saturation pin never retroactively changes
     *   earlier times. (Clamping to the first pin instead would make
     *   a lone pin at t=2500 desaturate the whole clip from t=0.)
     * - AFTER the last pin the value HOLDS at the last pin.
     * - New keyframes pin the current effective saturation, so a
     *   scale-only keyframe preserves — never resets — the ramp.
     */
    private fun interpolateSaturation(timeMs: Long): Float? {
        val pinned = keyframes.filter { it.saturation != null }.sortedBy { it.timeMs }
        if (pinned.isEmpty()) return null
        if (timeMs < pinned.first().timeMs) return 1f
        if (timeMs >= pinned.last().timeMs) return pinned.last().saturation
        for (i in 0 until pinned.size - 1) {
            val a = pinned[i]
            val b = pinned[i + 1]
            if (timeMs in a.timeMs..b.timeMs) {
                val span = (b.timeMs - a.timeMs).coerceAtLeast(1L)
                val raw = ((timeMs - a.timeMs).toDouble() / span.toDouble()).coerceIn(0.0, 1.0)
                val eased = ease(raw, b.curve)
                return lerp(a.saturation!!, b.saturation!!, eased)
            }
        }
        return null
    }

    /** True when at least one keyframe pins a saturation value. */
    fun hasSaturationPins(): Boolean = keyframes.any { it.saturation != null }

    /**
     * Effective saturation factor at [timeMs]: the interpolated
     * keyframed factor, or 1f (no change) when nothing pins
     * saturation. The factor MULTIPLIES the project-global
     * saturation — 1f leaves the global grade untouched, 0f is
     * fully black-and-white.
     */
    fun saturationFactorAt(timeMs: Long): Float =
        interpolateAt(timeMs).saturation ?: 1f

    private fun interpolatePair(a: Keyframe, b: Keyframe, t: Long): AnimatedTransform {
        val span = (b.timeMs - a.timeMs).coerceAtLeast(1L)
        val raw = ((t - a.timeMs).toDouble() / span.toDouble()).coerceIn(0.0, 1.0)
        val eased = ease(raw, b.curve)
        return AnimatedTransform(
            translateX = lerp(a.translateX, b.translateX, eased),
            translateY = lerp(a.translateY, b.translateY, eased),
            scale = lerp(a.scale, b.scale, eased),
            rotationDeg = lerp(a.rotationDeg, b.rotationDeg, eased),
            opacity = lerp(a.opacity, b.opacity, eased),
            volume = lerp(a.volume, b.volume, eased),
            effectIntensity = lerp(a.effectIntensity, b.effectIntensity, eased),
            filterIntensity = lerp(a.filterIntensity, b.filterIntensity, eased)
        )
    }

    private fun ease(t: Double, curve: KeyframeCurve): Double = when (curve) {
        KeyframeCurve.LINEAR -> t
        KeyframeCurve.EASE_IN -> t * t
        KeyframeCurve.EASE_OUT -> 1.0 - (1.0 - t) * (1.0 - t)
        KeyframeCurve.EASE_IN_OUT -> if (t < 0.5) 2 * t * t else 1 - 2 * (1 - t) * (1 - t)
        KeyframeCurve.BEZIER -> t * t * (3.0 - 2.0 * t) // Smoothstep cubic Bezier easing
        KeyframeCurve.HOLD -> 0.0 // first keyframe value until the next one
        // CapCut-style easeOutBack: overshoots past 1.0 then settles — buttery pop feel.
        KeyframeCurve.OVERSHOOT -> {
            val c1 = 1.70158
            val c3 = c1 + 1.0
            1.0 + c3 * (t - 1.0).pow(3.0) + c1 * (t - 1.0).pow(2.0)
        }
        // Damped spring: oscillates and settles at 1.0 — CapCut "Spring" entrance feel.
        KeyframeCurve.SPRING -> {
            if (t <= 0.0) 0.0 else if (t >= 1.0) 1.0
            else 1.0 - kotlin.math.exp(-6.0 * t) * kotlin.math.cos(11.0 * t)
        }
    }

    private fun lerp(a: Float, b: Float, t: Double): Float =
        (a + (b - a) * t.toFloat())
}

/** Snapshot of a keyframe track evaluated at a single time point. */
data class AnimatedTransform(
    val translateX: Float,
    val translateY: Float,
    val scale: Float,
    val rotationDeg: Float,
    val opacity: Float,
    val volume: Float = 1f,
    val effectIntensity: Float = 1f,
    val filterIntensity: Float = 1f,
    /** Interpolated saturation factor, or null when nothing pins it (factor 1f). */
    val saturation: Float? = null
) {
    companion object {
        val Identity = AnimatedTransform(0f, 0f, 1f, 0f, 1f, 1f, 1f, 1f)
    }
}

data class ToolItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val color: Color
)

data class LutPreset(
    val id: String,
    val name: String,
    val thumbnail: String
)

data class ExportSettings(
    val resolution: String = "8K Ultra HD",
    val frameRate: Int = 60,
    val quality: ExportQuality = ExportQuality.HIGH,
    val estimatedSizeGb: Float = 1.8f,
    // Pro Phase 1: aspect ratio + bitrate controls
    val aspectRatio: String = "16:9", // "16:9" | "9:16" | "1:1"
    val bitrateMbps: Int = 0 // 0 = auto (resolution-based default)
)

enum class ExportQuality(val label: String) {
    HIGH("High"), MEDIUM("Med"), LOW("Low")
}

@Serializable
data class VideoAdjustments(
    val hdr: Float = 0f,
    val brilliance: Float = 0f,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val exposure: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val sharpness: Float = 0f,
    val fade: Float = 0f,
    val vignette: Float = 0f,
    val grain: Float = 0f,
    // Video Adjustments v2 (photo-editor parity):
    // lightness/whites/blacks/clarity are -1..1, hue is degrees -180..180,
    // denoise is 0..1. All default to "no change" so old saved
    // adjustments keep working (backward compatible).
    @FloatRange(from = -1.0, to = 1.0)
    val lightness: Float = 0f,
    @FloatRange(from = -1.0, to = 1.0)
    val whites: Float = 0f,
    @FloatRange(from = -1.0, to = 1.0)
    val blacks: Float = 0f,
    @FloatRange(from = -180.0, to = 180.0)
    val hue: Float = 0f,
    @FloatRange(from = -1.0, to = 1.0)
    val clarity: Float = 0f,
    @FloatRange(from = 0.0, to = 1.0)
    val denoise: Float = 0f,
    @FloatRange(from = -1.0, to = 1.0)
    val vibrance: Float = 0f
) {
    val isDefault: Boolean
        get() = hdr == 0f &&
                brilliance == 0f &&
                brightness == 0f &&
                contrast == 1f &&
                saturation == 1f &&
                exposure == 0f &&
                highlights == 0f &&
                shadows == 0f &&
                temperature == 0f &&
                tint == 0f &&
                sharpness == 0f &&
                fade == 0f &&
                vignette == 0f &&
                grain == 0f &&
                lightness == 0f &&
                whites == 0f &&
                blacks == 0f &&
                hue == 0f &&
                clarity == 0f &&
                denoise == 0f &&
                vibrance == 0f

    /**
     * True when any adjustment that can't be expressed as a color matrix is
     * active. The live preview (EditorScreen) and the export path
     * (ExportEngine) use this to decide whether the GL shader stage is
     * needed; the matrix stage handles the rest, so nothing double-applies.
     */
    val hasNonMatrixAdjustments: Boolean
        get() = highlights != 0f || shadows != 0f ||
                sharpness != 0f || fade != 0f || vignette != 0f || grain != 0f ||
                lightness != 0f || whites != 0f || blacks != 0f || hue != 0f ||
                vibrance != 0f || clarity != 0f || denoise != 0f
}

/**
 * Minimum sticker crop-rect edge as a fraction of the source bitmap.
 * Shared by the interactive crop editor ([com.apexstudio.app.ui.screens.editor.StickerCanvas])
 * and [StickerOverlay.sanitizedCrop] so the preview and the export can
 * never disagree on the smallest allowed crop.
 */
const val STICKER_CROP_MIN_FRACTION = 0.05f

@Serializable
data class StickerOverlay(
    val id: String = java.util.UUID.randomUUID().toString(),
    val symbolOrUri: String = "🔥",
    val category: String = "Emoji",
    val name: String = "Sticker",
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val sizeScale: Float = 1f,
    val rotationDeg: Float = 0f,
    val opacity: Float = 1f,
    val startMs: Long = 0L,
    val endMs: Long = Long.MAX_VALUE,
    val keyframes: KeyframeTrack = KeyframeTrack(),
    /**
     * Bundled sticker from `assets/stickers/` (e.g. "love/red-heart.webp"),
     * loaded via [com.apexstudio.app.data.stickers.StickerPack]. Null means
     * this is a legacy emoji sticker rendered from [symbolOrUri] as text.
     * Default-null keeps old saved projects deserialising unchanged.
     */
    val assetPath: String? = null,
    // Rect crop of the source bitmap, as fractions (0..1) of the bitmap.
    // (0,0,1,1) = no crop. Applied by the preview Canvas and the export
    // GL sprite renderer, so WYSIWYG holds in both places.
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
    /**
     * Shape mask applied on top of the crop: "NONE" (default), "RECT",
     * "OVAL". Only meaningful for PNG stickers.
     */
    val cutoutShape: String = "NONE"
) {
    fun isActiveAt(timeMs: Long): Boolean = timeMs in startMs..endMs

    /**
     * Keyframe-interpolated transform at [timeMs] (translate / scale /
     * rotation / opacity offsets relative to the sticker's base
     * [x]/[y]/[sizeScale]/[rotationDeg]/[opacity]). Identity when the
     * sticker has no keyframes. Preview ([StickerCanvas]) and export
     * ([StickerGlEffect]) both evaluate this single helper so a
     * keyframed sticker moves identically on screen and in the MP4.
     */
    fun effectiveTransformAt(timeMs: Long): AnimatedTransform =
        keyframes.interpolateAt(timeMs)

    /** True for bundled PNG stickers (asset library), false for legacy emoji stickers. */
    fun isPngSticker(): Boolean = !assetPath.isNullOrBlank()

    /** Crop fractions sanitised into a valid rect (left < right, top < bottom, 0..1). */
    fun sanitizedCrop(): FloatArray {
        val min = STICKER_CROP_MIN_FRACTION
        val l = cropLeft.coerceIn(0f, 1f - min)
        val t = cropTop.coerceIn(0f, 1f - min)
        val r = cropRight.coerceIn(min, 1f)
        val b = cropBottom.coerceIn(min, 1f)
        return floatArrayOf(
            minOf(l, r - min),
            minOf(t, b - min),
            maxOf(r, l + min),
            maxOf(b, t + min)
        )
    }
}
