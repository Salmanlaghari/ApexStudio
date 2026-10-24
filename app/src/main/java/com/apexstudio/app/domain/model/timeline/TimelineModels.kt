package com.apexstudio.app.domain.model.timeline

import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import java.util.UUID

/** One shared timeline container; each lane is represented by exactly one [Track] subtype. */
@Serializable
data class Timeline(val tracks: List<Track> = emptyList()) {
    /** Next lane number for a newly-created track, including an overlay lane. */
    fun nextLane(): Int = (tracks.maxOfOrNull(Track::lane) ?: -1) + 1

    fun append(track: Track): Timeline {
        require(tracks.none { it.id == track.id }) { "Track ids must be unique" }
        require(tracks.none { it.lane == track.lane }) { "Each track must own a unique lane" }
        return copy(tracks = tracks + track)
    }
}

@Serializable
sealed interface Track {
    val id: String
    val lane: Int
}

@Serializable
data class VideoTrack(
    override val id: String = UUID.randomUUID().toString(),
    override val lane: Int,
    val clips: List<VideoClip> = emptyList()
) : Track

@Serializable
data class AudioTrack(
    override val id: String = UUID.randomUUID().toString(),
    override val lane: Int,
    val clips: List<AudioClip> = emptyList(),
    /** Track identity and mixer state; source placement belongs to [AudioClip]. */
    val name: String = "",
    val volume: Float = 0.75f,
    val isMuted: Boolean = false,
    val isSolo: Boolean = false,
    val volumeKeyframes: List<Keyframe<Float>> = emptyList(),
    val pan: Float = 0f,
    val panKeyframes: List<Keyframe<Float>> = emptyList(),
    val kind: AudioKind = AudioKind.MUSIC,
    val ducking: Ducking? = null
) : Track {
    @Serializable
    enum class AudioKind { ORIGINAL_VIDEO, MUSIC, SFX }

    fun volumeAt(timeMs: Long): Float = sample(volumeKeyframes, volume, timeMs, FloatType)
    fun panAt(timeMs: Long): Float = sample(panKeyframes, pan, timeMs, FloatType)
}

@Serializable
data class TextTrack(
    override val id: String = UUID.randomUUID().toString(),
    override val lane: Int,
    val items: List<TextItem> = emptyList()
) : Track

@Serializable
data class StickerTrack(
    override val id: String = UUID.randomUUID().toString(),
    override val lane: Int,
    val items: List<StickerItem> = emptyList()
) : Track

@Serializable
data class OverlayTrack(
    override val id: String = UUID.randomUUID().toString(),
    override val lane: Int,
    val items: List<OverlayItem> = emptyList(),
    val isVisible: Boolean = true,
    val isLocked: Boolean = false,
    val opacity: Float = 1f,
    val opacityKeyframes: List<Keyframe<Float>> = emptyList(),
    val blendMode: BlendMode = BlendMode.NORMAL
) : Track {
    fun opacityAt(timeMs: Long): Float = sample(opacityKeyframes, opacity, timeMs, FloatType)
}

@Serializable
data class VideoClip(
    val id: String = UUID.randomUUID().toString(),
    @Serializable(with = AndroidUriSerializer::class)
    val uri: Uri,
    /** Clip start and end on the project timeline, in milliseconds. */
    val inMs: Long,
    val outMs: Long,
    /** Source-media trim offset, in milliseconds. */
    val trimStartMs: Long = 0L,
    val speed: Float = 1f,
    /** Legacy-compatible source identity and timing metadata. */
    val sourceName: String = "",
    val sourceDurationMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val thumbnail: String? = null,
    val groupId: String? = null,
    val speedCurve: com.apexstudio.app.domain.model.SpeedCurve = com.apexstudio.app.domain.model.SpeedCurve.LINEAR,
    val rampStartSpeed: Float = 1f,
    val rampEndSpeed: Float = 1f,
    val speedPoints: List<com.apexstudio.app.domain.model.SpeedPoint> = emptyList(),
    /** Main-track source kind; IMAGE clips still use this shared visual shape. */
    val mediaType: com.apexstudio.app.domain.model.ClipType = com.apexstudio.app.domain.model.ClipType.VIDEO,
    val transform: Transform = Transform(),
    val opacity: Float = 1f,
    val opacityKeyframes: List<Keyframe<Float>> = emptyList(),
    /** GPUImage filter key and its animated intensity. */
    val filter: String? = null,
    val filterIntensity: Float = 1f,
    val filterKeyframes: List<Keyframe<Float>> = emptyList(),
    val effectIntensity: Float = 1f,
    val effectIntensityKeyframes: List<Keyframe<Float>> = emptyList(),
    val lutId: String? = null,
    val lutIntensity: Float = 1f,
    val lutKeyframes: List<Keyframe<Float>> = emptyList(),
    val saturation: Float = 1f,
    val saturationKeyframes: List<Keyframe<Float>> = emptyList(),
    val mask: Mask? = null,
    val chromaKey: ChromaKey? = null,
    val blendMode: BlendMode = BlendMode.NORMAL,
    val volume: Float = 1f,
    val volumeKeyframes: List<Keyframe<Float>> = emptyList(),
    val pan: Float = 0f,
    val panKeyframes: List<Keyframe<Float>> = emptyList(),
    val drawingLayer: DrawingLayer? = null,
    val denoiseEnabled: Boolean = false,
    val denoiseStrength: com.apexstudio.app.domain.model.DenoiseStrength = com.apexstudio.app.domain.model.DenoiseStrength.MEDIUM,
    val backgroundReplace: com.apexstudio.app.domain.model.BackgroundReplaceSettings? = null,
    val gpuFilterConfig: com.apexstudio.app.domain.model.GpuFilterConfig = com.apexstudio.app.domain.model.GpuFilterConfig(),
    val projectVideoEffectId: String? = null,
    val videoCrop: com.apexstudio.app.domain.model.VideoCropSettings = com.apexstudio.app.domain.model.VideoCropSettings(),
    val faceCropTrack: com.apexstudio.app.domain.model.FaceCropTrack? = null,
    val pipX: Float = 0.85f,
    val pipY: Float = 0.85f,
    val pipScale: Float = 0.25f,
    val pipRotationDeg: Float = 0f,
    val pipOpacity: Float = 1f,
    val overlayAspect: Float = 16f / 9f,
    val overlayBlend: BlendMode = BlendMode.NORMAL,
    val overlayBorderWidth: Float = 0f,
    val overlayBorderColor: Int = 0xFFFFFFFF.toInt(),
    val overlayShadow: Float = 0f,
    val overlayFlipH: Boolean = false,
    val overlayFlipV: Boolean = false,
    val overlayVolume: Float = 0f,
    val photoEdit: com.apexstudio.app.domain.model.PhotoEditSettings = com.apexstudio.app.domain.model.PhotoEditSettings(),
    val workspaceEffects: List<com.apexstudio.app.domain.model.WorkspaceEffect> = emptyList(),
    val videoFitMode: com.apexstudio.app.domain.model.VideoFitMode = com.apexstudio.app.domain.model.VideoFitMode.FIT

) {
    fun opacityAt(timeMs: Long): Float = sample(opacityKeyframes, opacity, timeMs, FloatType)
    fun filterIntensityAt(timeMs: Long): Float = sample(filterKeyframes, filterIntensity, timeMs, FloatType)
    fun effectIntensityAt(timeMs: Long): Float = sample(effectIntensityKeyframes, effectIntensity, timeMs, FloatType)
    fun lutIntensityAt(timeMs: Long): Float = sample(lutKeyframes, lutIntensity, timeMs, FloatType)
    fun saturationAt(timeMs: Long): Float = sample(saturationKeyframes, saturation, timeMs, FloatType)
    fun volumeAt(timeMs: Long): Float = sample(volumeKeyframes, volume, timeMs, FloatType)
    fun panAt(timeMs: Long): Float = sample(panKeyframes, pan, timeMs, FloatType)
}

/** Base transform values are fallbacks; all animated changes live in the keyframe lists. */
@Serializable
data class Transform(
    val position: XY = XY(0f, 0f),
    val scale: Float = 1f,
    val rotationDeg: Float = 0f,
    val positionKeyframes: List<Keyframe<XY>> = emptyList(),
    val scaleKeyframes: List<Keyframe<Float>> = emptyList(),
    val rotationKeyframes: List<Keyframe<Float>> = emptyList()
) {
    fun positionAt(timeMs: Long): XY = sample(positionKeyframes, position, timeMs, XYType)
    fun scaleAt(timeMs: Long): Float = sample(scaleKeyframes, scale, timeMs, FloatType)
    fun rotationAt(timeMs: Long): Float = sample(rotationKeyframes, rotationDeg, timeMs, FloatType)
}

@Serializable
data class AudioClip(
    val id: String = UUID.randomUUID().toString(),
    @Serializable(with = AndroidUriSerializer::class)
    val uri: Uri,
    val inMs: Long,
    val outMs: Long,
    val trimStartMs: Long = 0L,
    /** Source boundary and envelope values; placement is [inMs]/[outMs]. */
    val trimEndMs: Long = 0L,
    val sourceDurationMs: Long = 0L,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    val volume: Float = 1f,
    val volumeKeyframes: List<Keyframe<Float>> = emptyList(),
    val pan: Float = 0f,
    val panKeyframes: List<Keyframe<Float>> = emptyList()
) {
    fun volumeAt(timeMs: Long): Float = sample(volumeKeyframes, volume, timeMs, FloatType)
    fun panAt(timeMs: Long): Float = sample(panKeyframes, pan, timeMs, FloatType)
}

@Serializable
data class Ducking(
    val amount: Float = 0.5f,
    val attackMs: Long = 120L,
    val releaseMs: Long = 320L
)

@Serializable
data class TextItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "Text",
    val position: XY = XY(0.5f, 0.5f),
    val positionKeyframes: List<Keyframe<XY>> = emptyList(),
    val scale: Float = 1f,
    val scaleKeyframes: List<Keyframe<Float>> = emptyList(),
    val rotationDeg: Float = 0f,
    val rotationKeyframes: List<Keyframe<Float>> = emptyList(),
    @Serializable(with = ComposeColorSerializer::class)
    val color: Color = Color.White,
    val colorKeyframes: List<Keyframe<@Serializable(with = ComposeColorSerializer::class) Color>> = emptyList(),
    val alpha: Float = 1f,
    val alphaKeyframes: List<Keyframe<Float>> = emptyList(),
    val bgArgb: Long? = null,
    val strokeColorArgb: Long? = null,
    val shadowColorArgb: Long? = null,
    val fontFamily: String = "sans",
    val isBold: Boolean = true,
    val isItalic: Boolean = false,
    val presetId: String? = null,
    val animationType: String = "NONE",
    val animationDurationMs: Long = 800L,
    val outroAnimationType: String = "NONE",
    val outroAnimationDurationMs: Long = 600L,
    val textAlign: String = "CENTER",
    val letterSpacingEm: Float = 0f,
    val gradientStartArgb: Long? = null,
    val gradientEndArgb: Long? = null,
    val keyframes: List<Keyframe<Transform>> = emptyList(),
    val mask: Mask? = null,
    val maskKeyframes: List<com.apexstudio.app.domain.model.TextMaskKeyframe> = emptyList(),
    val highlightStartUtf16: Int? = null,
    val highlightEndUtf16: Int? = null,
    val highlightSourceFingerprint: String? = null,
    val highlightColorArgb: Long = 0xFFFFFF00L,
    val animationWindowStartMs: Long? = null,
    val animationWindowEndMs: Long? = null,
    val projectTextLayerId: String? = null,
    val rangeEndExclusive: Boolean = false,
    val startMs: Long = 0L,
    val endMs: Long = Long.MAX_VALUE,
    /** True when this item is a compatibility projection of a legacy TextOverlay. */
    val legacyBacked: Boolean = false
) {
    fun positionAt(timeMs: Long): XY = sample(positionKeyframes, position, timeMs, XYType)
    fun scaleAt(timeMs: Long): Float = sample(scaleKeyframes, scale, timeMs, FloatType)
    fun rotationAt(timeMs: Long): Float = sample(rotationKeyframes, rotationDeg, timeMs, FloatType)
    fun colorAt(timeMs: Long): Color = sample(colorKeyframes, color, timeMs, ColorType)
    fun alphaAt(timeMs: Long): Float = sample(alphaKeyframes, alpha, timeMs, FloatType)
}

@Serializable
data class StickerItem(
    val id: String = UUID.randomUUID().toString(),
    val symbolOrUri: String = "🔥",
    val category: String = "Emoji",
    val name: String = "Sticker",
    val position: XY = XY(0.5f, 0.5f),
    val positionKeyframes: List<Keyframe<XY>> = emptyList(),
    val scale: Float = 1f,
    val scaleKeyframes: List<Keyframe<Float>> = emptyList(),
    val rotationDeg: Float = 0f,
    val rotationKeyframes: List<Keyframe<Float>> = emptyList(),
    val alpha: Float = 1f,
    val alphaKeyframes: List<Keyframe<Float>> = emptyList(),
    val keyframes: List<Keyframe<Transform>> = emptyList(),
    val assetPath: String? = null,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
    val cutoutShape: String = "NONE",
    val startMs: Long = 0L,
    val endMs: Long = Long.MAX_VALUE,
    /** True when this item is a compatibility projection of a legacy StickerOverlay. */
    val legacyBacked: Boolean = false
) {
    fun positionAt(timeMs: Long): XY = sample(positionKeyframes, position, timeMs, XYType)
    fun scaleAt(timeMs: Long): Float = sample(scaleKeyframes, scale, timeMs, FloatType)
    fun rotationAt(timeMs: Long): Float = sample(rotationKeyframes, rotationDeg, timeMs, FloatType)
    fun alphaAt(timeMs: Long): Float = sample(alphaKeyframes, alpha, timeMs, FloatType)
}

/** Visual overlays deliberately carry no audio fields. */
@Serializable
sealed interface OverlayItem {
    val id: String
    val transform: Transform
    val opacity: Float
    val opacityKeyframes: List<Keyframe<Float>>

    @Serializable
    data class Video(
        override val id: String = UUID.randomUUID().toString(),
        @Serializable(with = AndroidUriSerializer::class)
        val uri: Uri,
        val inMs: Long,
        val outMs: Long,
        val trimStartMs: Long = 0L,
        val speed: Float = 1f,
        val sourceName: String = "",
        val sourceDurationMs: Long = 0L,
        val trimEndMs: Long = 0L,
        val thumbnail: String? = null,
        val groupId: String? = null,
        val speedCurve: com.apexstudio.app.domain.model.SpeedCurve = com.apexstudio.app.domain.model.SpeedCurve.LINEAR,
        val rampStartSpeed: Float = 1f,
        val rampEndSpeed: Float = 1f,
        val speedPoints: List<com.apexstudio.app.domain.model.SpeedPoint> = emptyList(),
        override val transform: Transform = Transform(),
        override val opacity: Float = 1f,
        override val opacityKeyframes: List<Keyframe<Float>> = emptyList(),
        val filter: String? = null,
        val filterIntensity: Float = 1f,
        val filterKeyframes: List<Keyframe<Float>> = emptyList(),
        val effectIntensity: Float = 1f,
        val effectIntensityKeyframes: List<Keyframe<Float>> = emptyList(),
        val lutId: String? = null,
        val lutIntensity: Float = 1f,
        val lutKeyframes: List<Keyframe<Float>> = emptyList(),
        val saturation: Float = 1f,
        val saturationKeyframes: List<Keyframe<Float>> = emptyList(),
        val mask: Mask? = null,
        val chromaKey: ChromaKey? = null,
        val blendMode: BlendMode = BlendMode.NORMAL,
        val drawingLayer: DrawingLayer? = null,
            val denoiseEnabled: Boolean = false,
            val denoiseStrength: com.apexstudio.app.domain.model.DenoiseStrength = com.apexstudio.app.domain.model.DenoiseStrength.MEDIUM,
            val backgroundReplace: com.apexstudio.app.domain.model.BackgroundReplaceSettings? = null,
            val gpuFilterConfig: com.apexstudio.app.domain.model.GpuFilterConfig = com.apexstudio.app.domain.model.GpuFilterConfig(),
            val projectVideoEffectId: String? = null,
            val videoCrop: com.apexstudio.app.domain.model.VideoCropSettings = com.apexstudio.app.domain.model.VideoCropSettings(),
            val faceCropTrack: com.apexstudio.app.domain.model.FaceCropTrack? = null,
            val pipX: Float = 0.85f,
            val pipY: Float = 0.85f,
            val pipScale: Float = 0.25f,
            val pipRotationDeg: Float = 0f,
            val pipOpacity: Float = 1f,
            val overlayAspect: Float = 16f / 9f,
            val overlayBlend: BlendMode = BlendMode.NORMAL,
            val overlayBorderWidth: Float = 0f,
            val overlayBorderColor: Int = 0xFFFFFFFF.toInt(),
            val overlayShadow: Float = 0f,
            val overlayFlipH: Boolean = false,
            val overlayFlipV: Boolean = false,
            val overlayVolume: Float = 0f,
            val photoEdit: com.apexstudio.app.domain.model.PhotoEditSettings = com.apexstudio.app.domain.model.PhotoEditSettings(),
            val workspaceEffects: List<com.apexstudio.app.domain.model.WorkspaceEffect> = emptyList(),
            val videoFitMode: com.apexstudio.app.domain.model.VideoFitMode = com.apexstudio.app.domain.model.VideoFitMode.FIT

    ) : OverlayItem

    @Serializable
    data class Image(
        override val id: String = UUID.randomUUID().toString(),
        @Serializable(with = AndroidUriSerializer::class)
        val uri: Uri,
        val inMs: Long,
        val outMs: Long,
        val sourceName: String = "",
        val sourceDurationMs: Long = 0L,
        val trimStartMs: Long = 0L,
        val trimEndMs: Long = 0L,
        val thumbnail: String? = null,
        val groupId: String? = null,
        override val transform: Transform = Transform(),
        override val opacity: Float = 1f,
        override val opacityKeyframes: List<Keyframe<Float>> = emptyList(),
        val filter: String? = null,
        val filterIntensity: Float = 1f,
        val filterKeyframes: List<Keyframe<Float>> = emptyList(),
        val effectIntensity: Float = 1f,
        val effectIntensityKeyframes: List<Keyframe<Float>> = emptyList(),
        val lutId: String? = null,
        val lutIntensity: Float = 1f,
        val lutKeyframes: List<Keyframe<Float>> = emptyList(),
        val saturation: Float = 1f,
        val saturationKeyframes: List<Keyframe<Float>> = emptyList(),
        val mask: Mask? = null,
        val chromaKey: ChromaKey? = null,
        val blendMode: BlendMode = BlendMode.NORMAL,
        val drawingLayer: DrawingLayer? = null,
            val denoiseEnabled: Boolean = false,
            val denoiseStrength: com.apexstudio.app.domain.model.DenoiseStrength = com.apexstudio.app.domain.model.DenoiseStrength.MEDIUM,
            val backgroundReplace: com.apexstudio.app.domain.model.BackgroundReplaceSettings? = null,
            val gpuFilterConfig: com.apexstudio.app.domain.model.GpuFilterConfig = com.apexstudio.app.domain.model.GpuFilterConfig(),
            val projectVideoEffectId: String? = null,
            val videoCrop: com.apexstudio.app.domain.model.VideoCropSettings = com.apexstudio.app.domain.model.VideoCropSettings(),
            val faceCropTrack: com.apexstudio.app.domain.model.FaceCropTrack? = null,
            val pipX: Float = 0.85f,
            val pipY: Float = 0.85f,
            val pipScale: Float = 0.25f,
            val pipRotationDeg: Float = 0f,
            val pipOpacity: Float = 1f,
            val overlayAspect: Float = 16f / 9f,
            val overlayBlend: BlendMode = BlendMode.NORMAL,
            val overlayBorderWidth: Float = 0f,
            val overlayBorderColor: Int = 0xFFFFFFFF.toInt(),
            val overlayShadow: Float = 0f,
            val overlayFlipH: Boolean = false,
            val overlayFlipV: Boolean = false,
            val overlayVolume: Float = 0f,
            val photoEdit: com.apexstudio.app.domain.model.PhotoEditSettings = com.apexstudio.app.domain.model.PhotoEditSettings(),
            val workspaceEffects: List<com.apexstudio.app.domain.model.WorkspaceEffect> = emptyList(),
            val videoFitMode: com.apexstudio.app.domain.model.VideoFitMode = com.apexstudio.app.domain.model.VideoFitMode.FIT

    ) : OverlayItem {
        fun filterIntensityAt(timeMs: Long): Float = sample(filterKeyframes, filterIntensity, timeMs, FloatType)
        fun effectIntensityAt(timeMs: Long): Float = sample(effectIntensityKeyframes, effectIntensity, timeMs, FloatType)
        fun lutIntensityAt(timeMs: Long): Float = sample(lutKeyframes, lutIntensity, timeMs, FloatType)
        fun saturationAt(timeMs: Long): Float = sample(saturationKeyframes, saturation, timeMs, FloatType)
    }
}

@Serializable
data class ChromaKey(
    val tolerance: Float,
    val softness: Float,
    val spill: Float,
    @Serializable(with = ComposeColorSerializer::class)
    val color: Color,
    val enabled: Boolean = true,
    val depth3D: Float = 0.35f,
    val tiltX: Float = 0f,
    val tiltY: Float = 0f,
    val backgroundType: String = "cyber_portal",
    val customBackgroundUri: String? = null
)

@Serializable
sealed interface Mask {
    @Serializable
    data class Shape(
        val kind: Kind,
        @Serializable(with = AndroidRectFSerializer::class)
        val rect: RectF,
        val feather: Float = 0f,
        val inverted: Boolean = false,
        val keyframes: List<Keyframe<@Serializable(with = AndroidRectFSerializer::class) RectF>> = emptyList(),
        val enabled: Boolean = true,
        val opacity: Float = 1f,
        val rotationDeg: Float = 0f,
        val presetId: String? = null,
        val maskKeyframes: List<MaskKeyframe> = emptyList()
    ) : Mask

    @Serializable
    data class Pen(
        @Serializable(with = AndroidPathSerializer::class)
        val path: Path = Path(),
        val points: List<XY> = emptyList(),
        val strokes: List<Stroke> = emptyList(),
        val feather: Float = 0f,
        val inverted: Boolean = false,
        val enabled: Boolean = true,
        val opacity: Float = 1f,
        val rotationDeg: Float = 0f,
        val presetId: String? = null,
        val maskKeyframes: List<MaskKeyframe> = emptyList()
    ) : Mask

    @Serializable
    data class BlurFaces(
        val enabled: Boolean = true,
        val centerX: Float = 0.5f,
        val centerY: Float = 0.5f,
        val sizeX: Float = 0.6f,
        val sizeY: Float = 0.6f,
        val feather: Float = 0.15f,
        val inverted: Boolean = false,
        val opacity: Float = 1f,
        val rotationDeg: Float = 0f,
        val presetId: String? = null,
        val maskKeyframes: List<MaskKeyframe> = emptyList()
    ) : Mask

    @Serializable
    enum class Kind {
        CIRCLE,
        RECTANGLE,
        HEART,
        STAR,
        TRIANGLE,
        ELLIPSE,
        HORIZONTAL,
        MIRROR
    }
}

/** Typed, field-level mask animation keyframe; it deliberately does not retain VideoMask. */
@Serializable
data class MaskKeyframe(
    val id: String,
    val timeMs: Long,
    @Serializable(with = AndroidRectFSerializer::class)
    val rect: RectF,
    val rotationDeg: Float = 0f,
    val feather: Float = 0f,
    val opacity: Float = 1f,
    val inverted: Boolean = false,
    val curve: Curve = Curve.LINEAR
)

@Serializable
data class DrawingLayer(
    val id: String = UUID.randomUUID().toString(),
    val ownerClipId: String? = null,
    val strokes: List<Stroke> = emptyList(),
    val blendMode: BlendMode = BlendMode.NORMAL,
    val globalAlpha: Float = 1f,
    val globalAlphaKeyframes: List<Keyframe<Float>> = emptyList()
) {
    fun alphaAt(timeMs: Long): Float = sample(globalAlphaKeyframes, globalAlpha, timeMs, FloatType)
}

@Serializable
data class Stroke(
    @Serializable(with = ComposeOffsetListSerializer::class)
    val points: List<Offset>,
    @Serializable(with = ComposeColorSerializer::class)
    val color: Color,
    val width: Float,
    val tool: Tool,
    val startMs: Long,
    val endMs: Long? = null,
    val alpha: Float = 1f,
    val alphaKeyframes: List<Keyframe<Float>> = emptyList(),
    val transform: Transform = Transform()
) {
    fun alphaAt(timeMs: Long): Float = sample(alphaKeyframes, alpha, timeMs, FloatType)
}

@Serializable
enum class Tool {
    PEN,
    ERASER
}

private fun <T> sample(keys: List<Keyframe<T>>, fallback: T, timeMs: Long, type: Type<T>): T =
    if (keys.isEmpty()) fallback else Interpolator.valueAt(keys, timeMs, type)

/** Replace the key at [timeMs] and preserve chronological ordering. */
fun <T> List<Keyframe<T>>.upsertKeyframe(keyframe: Keyframe<T>): List<Keyframe<T>> =
    filterNot { it.timeMs == keyframe.timeMs }
        .plus(keyframe)
        .sortedBy(Keyframe<T>::timeMs)

/** Remove only the keyframe at [timeMs]; neighbouring keys are preserved. */
fun <T> List<Keyframe<T>>.removeKeyframeAt(timeMs: Long): List<Keyframe<T>> =
    filterNot { it.timeMs == timeMs }
