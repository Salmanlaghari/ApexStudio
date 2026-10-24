package com.apexstudio.app.domain.model.timeline

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/** Values sampled from the shared clip model at one project-time point. */
data class VideoAnimationValues(
    val position: XY,
    val scale: Float,
    val rotationDeg: Float,
    val opacity: Float,
    val volume: Float,
    val pan: Float,
    val filterIntensity: Float,
    val effectIntensity: Float,
    val lutIntensity: Float,
    val saturation: Float
)

/** Timeline properties that have an explicit typed keyframe list on [VideoClip]. */
enum class VideoAnimationProperty {
    POSITION_X,
    POSITION_Y,
    SCALE,
    ROTATION,
    OPACITY,
    VOLUME,
    PAN,
    FILTER_INTENSITY,
    EFFECT_INTENSITY,
    LUT_INTENSITY,
    SATURATION
}

fun VideoClip.animationAt(timeMs: Long): VideoAnimationValues = VideoAnimationValues(
    position = transform.positionAt(timeMs),
    scale = transform.scaleAt(timeMs),
    rotationDeg = transform.rotationAt(timeMs),
    opacity = opacityAt(timeMs),
    volume = volumeAt(timeMs),
    pan = panAt(timeMs),
    filterIntensity = filterIntensityAt(timeMs),
    effectIntensity = effectIntensityAt(timeMs),
    lutIntensity = lutIntensityAt(timeMs),
    saturation = saturationAt(timeMs)
)

/** Unique keyframe times across all animatable video-clip properties, in ascending order. */
fun VideoClip.animationKeyframeTimes(): List<Long> = buildSet {
    addAll(transform.positionKeyframes.map(Keyframe<XY>::timeMs))
    addAll(transform.scaleKeyframes.map(Keyframe<Float>::timeMs))
    addAll(transform.rotationKeyframes.map(Keyframe<Float>::timeMs))
    addAll(opacityKeyframes.map(Keyframe<Float>::timeMs))
    addAll(volumeKeyframes.map(Keyframe<Float>::timeMs))
    addAll(panKeyframes.map(Keyframe<Float>::timeMs))
    addAll(filterKeyframes.map(Keyframe<Float>::timeMs))
    addAll(effectIntensityKeyframes.map(Keyframe<Float>::timeMs))
    addAll(lutKeyframes.map(Keyframe<Float>::timeMs))
    addAll(saturationKeyframes.map(Keyframe<Float>::timeMs))
    mask.keyframeTimesTo(this)
    drawingLayer?.keyframeTimesTo(this)
}.sorted()

/** Pin the sampled value of every video property at [timeMs], keeping each property's own list typed. */
fun VideoClip.addAnimationKeyframe(timeMs: Long, curve: Curve = Curve.SMOOTH): VideoClip {
    val value = animationAt(timeMs)
    return copy(
        transform = transform.copy(
            positionKeyframes = transform.positionKeyframes.upsertKeyframe(Keyframe(timeMs, value.position, curve)),
            scaleKeyframes = transform.scaleKeyframes.upsertKeyframe(Keyframe(timeMs, value.scale, curve)),
            rotationKeyframes = transform.rotationKeyframes.upsertKeyframe(Keyframe(timeMs, value.rotationDeg, curve))
        ),
        opacityKeyframes = opacityKeyframes.upsertKeyframe(Keyframe(timeMs, value.opacity, curve)),
        volumeKeyframes = volumeKeyframes.upsertKeyframe(Keyframe(timeMs, value.volume, curve)),
        panKeyframes = panKeyframes.upsertKeyframe(Keyframe(timeMs, value.pan, curve)),
        filterKeyframes = filterKeyframes.upsertKeyframe(Keyframe(timeMs, value.filterIntensity, curve)),
        effectIntensityKeyframes = effectIntensityKeyframes.upsertKeyframe(Keyframe(timeMs, value.effectIntensity, curve)),
        lutKeyframes = lutKeyframes.upsertKeyframe(Keyframe(timeMs, value.lutIntensity, curve)),
        saturationKeyframes = saturationKeyframes.upsertKeyframe(Keyframe(timeMs, value.saturation, curve)),
        mask = mask.addAnimationKeyframe(timeMs, curve),
        drawingLayer = drawingLayer?.addAnimationKeyframe(timeMs, curve)
    )
}

private fun Mask?.addAnimationKeyframe(timeMs: Long, curve: Curve): Mask? = when (this) {
    is Mask.Shape -> {
        val sampledRect = if (keyframes.isEmpty()) rect else Interpolator.valueAt(keyframes, timeMs, RectFType)
        copy(keyframes = keyframes.upsertKeyframe(Keyframe(timeMs, sampledRect, curve)))
    }
    else -> this
}

private fun DrawingLayer.addAnimationKeyframe(timeMs: Long, curve: Curve): DrawingLayer = copy(
    globalAlphaKeyframes = globalAlphaKeyframes.upsertKeyframe(Keyframe(timeMs, alphaAt(timeMs), curve)),
    strokes = strokes.map { stroke ->
        if (timeMs < stroke.startMs || (stroke.endMs != null && timeMs > stroke.endMs)) stroke
        else stroke.copy(
            alphaKeyframes = stroke.alphaKeyframes.upsertKeyframe(Keyframe(timeMs, stroke.alphaAt(timeMs), curve)),
            transform = stroke.transform.copy(
                positionKeyframes = stroke.transform.positionKeyframes.upsertKeyframe(
                    Keyframe(timeMs, stroke.transform.positionAt(timeMs), curve)
                ),
                scaleKeyframes = stroke.transform.scaleKeyframes.upsertKeyframe(
                    Keyframe(timeMs, stroke.transform.scaleAt(timeMs), curve)
                ),
                rotationKeyframes = stroke.transform.rotationKeyframes.upsertKeyframe(
                    Keyframe(timeMs, stroke.transform.rotationAt(timeMs), curve)
                )
            )
        )
    }
)

/** Remove only keys at [timeMs] from every property; keys on either side remain unchanged. */
fun VideoClip.removeAnimationKeyframeAt(timeMs: Long): VideoClip = copy(
    transform = transform.copy(
        positionKeyframes = transform.positionKeyframes.removeKeyframeAt(timeMs),
        scaleKeyframes = transform.scaleKeyframes.removeKeyframeAt(timeMs),
        rotationKeyframes = transform.rotationKeyframes.removeKeyframeAt(timeMs)
    ),
    opacityKeyframes = opacityKeyframes.removeKeyframeAt(timeMs),
    volumeKeyframes = volumeKeyframes.removeKeyframeAt(timeMs),
    panKeyframes = panKeyframes.removeKeyframeAt(timeMs),
    filterKeyframes = filterKeyframes.removeKeyframeAt(timeMs),
    effectIntensityKeyframes = effectIntensityKeyframes.removeKeyframeAt(timeMs),
    lutKeyframes = lutKeyframes.removeKeyframeAt(timeMs),
    saturationKeyframes = saturationKeyframes.removeKeyframeAt(timeMs),
    mask = mask.removeKeyframeAt(timeMs),
    drawingLayer = drawingLayer?.removeKeyframeAt(timeMs)
)

/** Move a marker and all property keys at that time as one operation. */
fun VideoClip.moveAnimationKeyframe(fromMs: Long, toMs: Long): VideoClip {
    if (fromMs == toMs || fromMs !in animationKeyframeTimes()) return this
    return copy(
        transform = transform.copy(
            positionKeyframes = transform.positionKeyframes.moveKeyframeAt(fromMs, toMs),
            scaleKeyframes = transform.scaleKeyframes.moveKeyframeAt(fromMs, toMs),
            rotationKeyframes = transform.rotationKeyframes.moveKeyframeAt(fromMs, toMs)
        ),
        opacityKeyframes = opacityKeyframes.moveKeyframeAt(fromMs, toMs),
        volumeKeyframes = volumeKeyframes.moveKeyframeAt(fromMs, toMs),
        panKeyframes = panKeyframes.moveKeyframeAt(fromMs, toMs),
        filterKeyframes = filterKeyframes.moveKeyframeAt(fromMs, toMs),
        effectIntensityKeyframes = effectIntensityKeyframes.moveKeyframeAt(fromMs, toMs),
        lutKeyframes = lutKeyframes.moveKeyframeAt(fromMs, toMs),
        saturationKeyframes = saturationKeyframes.moveKeyframeAt(fromMs, toMs),
        mask = mask.moveKeyframeAt(fromMs, toMs),
        drawingLayer = drawingLayer?.moveKeyframeAt(fromMs, toMs)
    )
}

/** Duplicate all typed keys at [fromMs] onto [toMs], replacing only exact-time collisions. */
fun VideoClip.copyAnimationKeyframe(fromMs: Long, toMs: Long): VideoClip {
    if (fromMs == toMs || fromMs !in animationKeyframeTimes()) return this
    return copy(
        transform = transform.copy(
            positionKeyframes = transform.positionKeyframes.copyKeyframeAt(fromMs, toMs),
            scaleKeyframes = transform.scaleKeyframes.copyKeyframeAt(fromMs, toMs),
            rotationKeyframes = transform.rotationKeyframes.copyKeyframeAt(fromMs, toMs)
        ),
        opacityKeyframes = opacityKeyframes.copyKeyframeAt(fromMs, toMs),
        volumeKeyframes = volumeKeyframes.copyKeyframeAt(fromMs, toMs),
        panKeyframes = panKeyframes.copyKeyframeAt(fromMs, toMs),
        filterKeyframes = filterKeyframes.copyKeyframeAt(fromMs, toMs),
        effectIntensityKeyframes = effectIntensityKeyframes.copyKeyframeAt(fromMs, toMs),
        lutKeyframes = lutKeyframes.copyKeyframeAt(fromMs, toMs),
        saturationKeyframes = saturationKeyframes.copyKeyframeAt(fromMs, toMs),
        mask = mask.copyKeyframeAt(fromMs, toMs),
        drawingLayer = drawingLayer?.copyKeyframeAt(fromMs, toMs)
    )
}

/** Set the selected segment curve for every property pinned by the marker at [timeMs]. */
fun VideoClip.setAnimationCurveAt(timeMs: Long, curve: Curve): VideoClip = copy(
    transform = transform.copy(
        positionKeyframes = transform.positionKeyframes.withCurveAt(timeMs, curve),
        scaleKeyframes = transform.scaleKeyframes.withCurveAt(timeMs, curve),
        rotationKeyframes = transform.rotationKeyframes.withCurveAt(timeMs, curve)
    ),
    opacityKeyframes = opacityKeyframes.withCurveAt(timeMs, curve),
    volumeKeyframes = volumeKeyframes.withCurveAt(timeMs, curve),
    panKeyframes = panKeyframes.withCurveAt(timeMs, curve),
    filterKeyframes = filterKeyframes.withCurveAt(timeMs, curve),
    effectIntensityKeyframes = effectIntensityKeyframes.withCurveAt(timeMs, curve),
    lutKeyframes = lutKeyframes.withCurveAt(timeMs, curve),
    saturationKeyframes = saturationKeyframes.withCurveAt(timeMs, curve),
    mask = mask.withCurveAt(timeMs, curve),
    drawingLayer = drawingLayer?.withCurveAt(timeMs, curve)
)

/** Upsert one property at [timeMs] without changing other property tracks. */
fun VideoClip.upsertAnimationValue(
    property: VideoAnimationProperty,
    timeMs: Long,
    value: Float,
    curve: Curve = curveAt(timeMs)
): VideoClip {
    require(value.isFinite()) { "Animated values must be finite" }
    val current = animationAt(timeMs)
    return when (property) {
        VideoAnimationProperty.POSITION_X -> transform.positionKeyframes.upsertKeyframe(
            Keyframe(timeMs, current.position.copy(x = value), curve)
        ).let { copy(transform = transform.copy(positionKeyframes = it)) }
        VideoAnimationProperty.POSITION_Y -> transform.positionKeyframes.upsertKeyframe(
            Keyframe(timeMs, current.position.copy(y = value), curve)
        ).let { copy(transform = transform.copy(positionKeyframes = it)) }
        VideoAnimationProperty.SCALE -> transform.scaleKeyframes.upsertKeyframe(Keyframe(timeMs, value, curve))
            .let { copy(transform = transform.copy(scaleKeyframes = it)) }
        VideoAnimationProperty.ROTATION -> transform.rotationKeyframes.upsertKeyframe(Keyframe(timeMs, value, curve))
            .let { copy(transform = transform.copy(rotationKeyframes = it)) }
        VideoAnimationProperty.OPACITY -> copy(opacityKeyframes = opacityKeyframes.upsertKeyframe(Keyframe(timeMs, value, curve)))
        VideoAnimationProperty.VOLUME -> copy(volumeKeyframes = volumeKeyframes.upsertKeyframe(Keyframe(timeMs, value, curve)))
        VideoAnimationProperty.PAN -> copy(panKeyframes = panKeyframes.upsertKeyframe(Keyframe(timeMs, value, curve)))
        VideoAnimationProperty.FILTER_INTENSITY -> copy(filterKeyframes = filterKeyframes.upsertKeyframe(Keyframe(timeMs, value, curve)))
        VideoAnimationProperty.EFFECT_INTENSITY -> copy(effectIntensityKeyframes = effectIntensityKeyframes.upsertKeyframe(Keyframe(timeMs, value, curve)))
        VideoAnimationProperty.LUT_INTENSITY -> copy(lutKeyframes = lutKeyframes.upsertKeyframe(Keyframe(timeMs, value, curve)))
        VideoAnimationProperty.SATURATION -> copy(saturationKeyframes = saturationKeyframes.upsertKeyframe(Keyframe(timeMs, value, curve)))
    }
}

/** Curve on the first property key at [timeMs], or the shared default when the marker is absent. */
fun VideoClip.curveAt(timeMs: Long): Curve = sequenceOf(
    transform.positionKeyframes.firstOrNull { it.timeMs == timeMs }?.curve,
    transform.scaleKeyframes.firstOrNull { it.timeMs == timeMs }?.curve,
    transform.rotationKeyframes.firstOrNull { it.timeMs == timeMs }?.curve,
    opacityKeyframes.firstOrNull { it.timeMs == timeMs }?.curve,
    volumeKeyframes.firstOrNull { it.timeMs == timeMs }?.curve,
    panKeyframes.firstOrNull { it.timeMs == timeMs }?.curve,
    filterKeyframes.firstOrNull { it.timeMs == timeMs }?.curve,
    effectIntensityKeyframes.firstOrNull { it.timeMs == timeMs }?.curve,
    lutKeyframes.firstOrNull { it.timeMs == timeMs }?.curve,
    saturationKeyframes.firstOrNull { it.timeMs == timeMs }?.curve
).filterNotNull().firstOrNull() ?: Curve.SMOOTH

private fun <T> List<Keyframe<T>>.withCurveAt(timeMs: Long, curve: Curve): List<Keyframe<T>> =
    map { if (it.timeMs == timeMs) it.copy(curve = curve) else it }

private fun <T> List<Keyframe<T>>.moveKeyframeAt(fromMs: Long, toMs: Long): List<Keyframe<T>> {
    val moving = firstOrNull { it.timeMs == fromMs } ?: return this
    return filterNot { it.timeMs == fromMs || it.timeMs == toMs }
        .plus(moving.copy(timeMs = toMs))
        .sortedBy(Keyframe<T>::timeMs)
}

/** All timeline boundaries and typed keyframes, optionally including a quarter-note beat grid. */
fun Timeline.collectAllTimecodes(bpm: Int? = null): Set<Long> {
    val result = sortedSetOf<Long>()
    tracks.forEach { track ->
        when (track) {
            is VideoTrack -> track.clips.forEach { clip -> clip.collectTimecodesTo(result) }
            is AudioTrack -> {
                track.clips.forEach { clip ->
                    result.addIfNonNegative(clip.inMs)
                    result.addIfNonNegative(clip.outMs)
                    result.addKeys(clip.volumeKeyframes)
                    result.addKeys(clip.panKeyframes)
            }
                result.addKeys(track.panKeyframes)
            }
            is TextTrack -> track.items.forEach { item ->
                result.addIfNonNegative(item.startMs)
                result.addIfNonNegative(item.endMs)
                result.addKeys(item.positionKeyframes)
                result.addKeys(item.scaleKeyframes)
                result.addKeys(item.colorKeyframes)
                result.addKeys(item.alphaKeyframes)
            }
            is StickerTrack -> track.items.forEach { item ->
                result.addIfNonNegative(item.startMs)
                result.addIfNonNegative(item.endMs)
                result.addKeys(item.scaleKeyframes)
                result.addKeys(item.rotationKeyframes)
                result.addKeys(item.alphaKeyframes)
            }
            is OverlayTrack -> {
                track.items.forEach { item -> item.collectTimecodesTo(result) }
                result.addKeys(track.opacityKeyframes)
            }
        }
    }
    val safeBpm = bpm?.takeIf { it > 0 }
    if (safeBpm != null) {
        val end = result.maxOrNull() ?: 0L
        val beatMs = 60_000.0 / safeBpm.toDouble()
        val beatCount = floor(end.toDouble() / beatMs).toLong().coerceAtMost(1_000_000L)
        for (beat in 0L..beatCount) result.add((beat.toDouble() * beatMs).roundToLong())
    }
    return result
}

/** Snap to the nearest candidate only when it falls inside the zoom-scaled pixel threshold. */
fun snapTimeMs(
    dragMs: Long,
    snapTimes: Iterable<Long>,
    thresholdPx: Float,
    msPerPx: Float
): Long {
    if (!thresholdPx.isFinite() || !msPerPx.isFinite() || thresholdPx <= 0f || msPerPx <= 0f) return dragMs
    val thresholdMs = thresholdPx.toDouble() * msPerPx.toDouble()
    val nearest = snapTimes.minByOrNull { abs(it.toDouble() - dragMs.toDouble()) } ?: return dragMs
    return if (abs(nearest.toDouble() - dragMs.toDouble()) <= thresholdMs) nearest else dragMs
}

private fun VideoClip.collectTimecodesTo(result: MutableSet<Long>) {
    result.addIfNonNegative(inMs)
    result.addIfNonNegative(outMs)
    result.addKeys(transform.positionKeyframes)
    result.addKeys(transform.scaleKeyframes)
    result.addKeys(transform.rotationKeyframes)
    result.addKeys(opacityKeyframes)
    result.addKeys(volumeKeyframes)
    result.addKeys(panKeyframes)
    result.addKeys(filterKeyframes)
    result.addKeys(effectIntensityKeyframes)
    result.addKeys(lutKeyframes)
    result.addKeys(saturationKeyframes)
    mask.keyframeTimesTo(result)
    drawingLayer?.keyframeTimesTo(result)
}

private fun OverlayItem.collectTimecodesTo(result: MutableSet<Long>) {
    when (this) {
        is OverlayItem.Video -> {
            result.addIfNonNegative(inMs)
            result.addIfNonNegative(outMs)
            result.addKeys(transform.positionKeyframes)
            result.addKeys(transform.scaleKeyframes)
            result.addKeys(transform.rotationKeyframes)
            result.addKeys(opacityKeyframes)
            result.addKeys(filterKeyframes)
            result.addKeys(effectIntensityKeyframes)
            result.addKeys(lutKeyframes)
            result.addKeys(saturationKeyframes)
            mask.keyframeTimesTo(result)
            drawingLayer?.keyframeTimesTo(result)
        }
        is OverlayItem.Image -> {
            result.addIfNonNegative(inMs)
            result.addIfNonNegative(outMs)
            result.addKeys(transform.positionKeyframes)
            result.addKeys(transform.scaleKeyframes)
            result.addKeys(transform.rotationKeyframes)
            result.addKeys(opacityKeyframes)
        }
    }
}

private fun Mask?.keyframeTimesTo(result: MutableSet<Long>) {
    when (this) {
        is Mask.Shape -> result.addKeys(keyframes)
        else -> Unit
    }
}

private fun DrawingLayer.keyframeTimesTo(result: MutableSet<Long>) {
    result.addKeys(globalAlphaKeyframes)
    strokes.forEach { stroke ->
        result.addIfNonNegative(stroke.startMs)
        stroke.endMs?.let(result::addIfNonNegative)
        result.addKeys(stroke.alphaKeyframes)
        result.addKeys(stroke.transform.positionKeyframes)
        result.addKeys(stroke.transform.scaleKeyframes)
        result.addKeys(stroke.transform.rotationKeyframes)
    }
}

private fun Mask?.removeKeyframeAt(timeMs: Long): Mask? = when (this) {
    is Mask.Shape -> copy(keyframes = keyframes.removeKeyframeAt(timeMs))
    else -> this
}

private fun Mask?.moveKeyframeAt(fromMs: Long, toMs: Long): Mask? = when (this) {
    is Mask.Shape -> copy(keyframes = keyframes.moveKeyframeAt(fromMs, toMs))
    else -> this
}

private fun DrawingLayer.removeKeyframeAt(timeMs: Long): DrawingLayer = copy(
    globalAlphaKeyframes = globalAlphaKeyframes.removeKeyframeAt(timeMs),
    strokes = strokes.map { stroke ->
        stroke.copy(
            alphaKeyframes = stroke.alphaKeyframes.removeKeyframeAt(timeMs),
            transform = stroke.transform.copy(
                positionKeyframes = stroke.transform.positionKeyframes.removeKeyframeAt(timeMs),
                scaleKeyframes = stroke.transform.scaleKeyframes.removeKeyframeAt(timeMs),
                rotationKeyframes = stroke.transform.rotationKeyframes.removeKeyframeAt(timeMs)
            )
        )
    }
)

private fun DrawingLayer.moveKeyframeAt(fromMs: Long, toMs: Long): DrawingLayer = copy(
    globalAlphaKeyframes = globalAlphaKeyframes.moveKeyframeAt(fromMs, toMs),
    strokes = strokes.map { stroke ->
        stroke.copy(
            alphaKeyframes = stroke.alphaKeyframes.moveKeyframeAt(fromMs, toMs),
            transform = stroke.transform.copy(
                positionKeyframes = stroke.transform.positionKeyframes.moveKeyframeAt(fromMs, toMs),
                scaleKeyframes = stroke.transform.scaleKeyframes.moveKeyframeAt(fromMs, toMs),
                rotationKeyframes = stroke.transform.rotationKeyframes.moveKeyframeAt(fromMs, toMs)
            )
        )
    }
)

private fun Mask?.copyKeyframeAt(fromMs: Long, toMs: Long): Mask? = when (this) {
    is Mask.Shape -> copy(keyframes = keyframes.copyKeyframeAt(fromMs, toMs))
    else -> this
}

private fun DrawingLayer.copyKeyframeAt(fromMs: Long, toMs: Long): DrawingLayer = copy(
    globalAlphaKeyframes = globalAlphaKeyframes.copyKeyframeAt(fromMs, toMs),
    strokes = strokes.map { stroke ->
        stroke.copy(
            alphaKeyframes = stroke.alphaKeyframes.copyKeyframeAt(fromMs, toMs),
            transform = stroke.transform.copy(
                positionKeyframes = stroke.transform.positionKeyframes.copyKeyframeAt(fromMs, toMs),
                scaleKeyframes = stroke.transform.scaleKeyframes.copyKeyframeAt(fromMs, toMs),
                rotationKeyframes = stroke.transform.rotationKeyframes.copyKeyframeAt(fromMs, toMs)
            )
        )
    }
)

private fun Mask?.withCurveAt(timeMs: Long, curve: Curve): Mask? = when (this) {
    is Mask.Shape -> copy(keyframes = keyframes.withCurveAt(timeMs, curve))
    else -> this
}

private fun DrawingLayer.withCurveAt(timeMs: Long, curve: Curve): DrawingLayer = copy(
    globalAlphaKeyframes = globalAlphaKeyframes.withCurveAt(timeMs, curve),
    strokes = strokes.map { stroke ->
        stroke.copy(
            alphaKeyframes = stroke.alphaKeyframes.withCurveAt(timeMs, curve),
            transform = stroke.transform.copy(
                positionKeyframes = stroke.transform.positionKeyframes.withCurveAt(timeMs, curve),
                scaleKeyframes = stroke.transform.scaleKeyframes.withCurveAt(timeMs, curve),
                rotationKeyframes = stroke.transform.rotationKeyframes.withCurveAt(timeMs, curve)
            )
        )
    }
)

private fun MutableSet<Long>.addIfNonNegative(timeMs: Long) {
    if (timeMs >= 0L && timeMs < Long.MAX_VALUE) add(timeMs)
}

private fun <T> List<Keyframe<T>>.copyKeyframeAt(fromMs: Long, toMs: Long): List<Keyframe<T>> {
    val source = firstOrNull { it.timeMs == fromMs } ?: return this
    return upsertKeyframe(source.copy(timeMs = toMs))
}

private fun <T> MutableSet<Long>.addKeys(keys: List<Keyframe<T>>) {
    keys.forEach { addIfNonNegative(it.timeMs) }
}
