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
    val pan: Float = 0f,
    val panKeyframes: List<Keyframe<Float>> = emptyList(),
    val ducking: Ducking? = null
) : Track {
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
    val drawingLayer: DrawingLayer? = null
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
    val text: String,
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
    val startMs: Long = 0L,
    val endMs: Long = Long.MAX_VALUE
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
    val symbolOrUri: String,
    val position: XY = XY(0.5f, 0.5f),
    val positionKeyframes: List<Keyframe<XY>> = emptyList(),
    val scale: Float = 1f,
    val scaleKeyframes: List<Keyframe<Float>> = emptyList(),
    val rotationDeg: Float = 0f,
    val rotationKeyframes: List<Keyframe<Float>> = emptyList(),
    val alpha: Float = 1f,
    val alphaKeyframes: List<Keyframe<Float>> = emptyList(),
    val startMs: Long = 0L,
    val endMs: Long = Long.MAX_VALUE
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
        val drawingLayer: DrawingLayer? = null
    ) : OverlayItem

    @Serializable
    data class Image(
        override val id: String = UUID.randomUUID().toString(),
        @Serializable(with = AndroidUriSerializer::class)
        val uri: Uri,
        val inMs: Long,
        val outMs: Long,
        override val transform: Transform = Transform(),
        override val opacity: Float = 1f,
        override val opacityKeyframes: List<Keyframe<Float>> = emptyList()
    ) : OverlayItem
}

@Serializable
data class ChromaKey(
    val tolerance: Float,
    val softness: Float,
    val spill: Float,
    @Serializable(with = ComposeColorSerializer::class)
    val color: Color
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
        val keyframes: List<Keyframe<@Serializable(with = AndroidRectFSerializer::class) RectF>> = emptyList()
    ) : Mask

    @Serializable
    data class Pen(
        @Serializable(with = AndroidPathSerializer::class)
        val path: Path,
        val strokes: List<Stroke> = emptyList(),
        val feather: Float = 0f
    ) : Mask

    @Serializable
    data class BlurFaces(val enabled: Boolean = true) : Mask

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

@Serializable
data class DrawingLayer(
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
