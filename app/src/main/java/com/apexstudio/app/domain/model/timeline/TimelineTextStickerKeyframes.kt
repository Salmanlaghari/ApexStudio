package com.apexstudio.app.domain.model.timeline

/** Every animated text property receives a pin at the sampled playhead value. */
fun TextItem.addAnimationKeyframeAt(timeMs: Long): TextItem = copy(
    positionKeyframes = positionKeyframes.upsertKeyframe(Keyframe(timeMs, positionAt(timeMs), curveAt(timeMs, positionKeyframes))),
    scaleKeyframes = scaleKeyframes.upsertKeyframe(Keyframe(timeMs, scaleAt(timeMs), curveAt(timeMs, scaleKeyframes))),
    rotationKeyframes = rotationKeyframes.upsertKeyframe(Keyframe(timeMs, rotationAt(timeMs), curveAt(timeMs, rotationKeyframes))),
    colorKeyframes = colorKeyframes.upsertKeyframe(Keyframe(timeMs, colorAt(timeMs), curveAt(timeMs, colorKeyframes))),
    alphaKeyframes = alphaKeyframes.upsertKeyframe(Keyframe(timeMs, alphaAt(timeMs), curveAt(timeMs, alphaKeyframes)))
)

fun TextItem.removeAnimationKeyframeAt(timeMs: Long): TextItem = copy(
    positionKeyframes = positionKeyframes.removeKeyframeAt(timeMs),
    scaleKeyframes = scaleKeyframes.removeKeyframeAt(timeMs),
    rotationKeyframes = rotationKeyframes.removeKeyframeAt(timeMs),
    colorKeyframes = colorKeyframes.removeKeyframeAt(timeMs),
    alphaKeyframes = alphaKeyframes.removeKeyframeAt(timeMs)
)

fun TextItem.moveAnimationKeyframe(fromMs: Long, toMs: Long): TextItem = copy(
    positionKeyframes = positionKeyframes.moveKeyframe(fromMs, toMs),
    scaleKeyframes = scaleKeyframes.moveKeyframe(fromMs, toMs),
    rotationKeyframes = rotationKeyframes.moveKeyframe(fromMs, toMs),
    colorKeyframes = colorKeyframes.moveKeyframe(fromMs, toMs),
    alphaKeyframes = alphaKeyframes.moveKeyframe(fromMs, toMs)
)

fun TextItem.setAnimationCurveAt(timeMs: Long, curve: Curve): TextItem = copy(
    positionKeyframes = positionKeyframes.setCurveAt(timeMs, curve),
    scaleKeyframes = scaleKeyframes.setCurveAt(timeMs, curve),
    rotationKeyframes = rotationKeyframes.setCurveAt(timeMs, curve),
    colorKeyframes = colorKeyframes.setCurveAt(timeMs, curve),
    alphaKeyframes = alphaKeyframes.setCurveAt(timeMs, curve)
)

fun TextItem.animationKeyframeTimes(): Set<Long> = buildSet {
    addAll(positionKeyframes.map(Keyframe<XY>::timeMs))
    addAll(scaleKeyframes.map(Keyframe<Float>::timeMs))
    addAll(rotationKeyframes.map(Keyframe<Float>::timeMs))
    addAll(colorKeyframes.map(Keyframe<androidx.compose.ui.graphics.Color>::timeMs))
    addAll(alphaKeyframes.map(Keyframe<Float>::timeMs))
}

/** Every animated sticker property receives a pin at the sampled playhead value. */
fun StickerItem.addAnimationKeyframeAt(timeMs: Long): StickerItem = copy(
    positionKeyframes = positionKeyframes.upsertKeyframe(Keyframe(timeMs, positionAt(timeMs), curveAt(timeMs, positionKeyframes))),
    scaleKeyframes = scaleKeyframes.upsertKeyframe(Keyframe(timeMs, scaleAt(timeMs), curveAt(timeMs, scaleKeyframes))),
    rotationKeyframes = rotationKeyframes.upsertKeyframe(Keyframe(timeMs, rotationAt(timeMs), curveAt(timeMs, rotationKeyframes))),
    alphaKeyframes = alphaKeyframes.upsertKeyframe(Keyframe(timeMs, alphaAt(timeMs), curveAt(timeMs, alphaKeyframes)))
)

fun StickerItem.removeAnimationKeyframeAt(timeMs: Long): StickerItem = copy(
    positionKeyframes = positionKeyframes.removeKeyframeAt(timeMs),
    scaleKeyframes = scaleKeyframes.removeKeyframeAt(timeMs),
    rotationKeyframes = rotationKeyframes.removeKeyframeAt(timeMs),
    alphaKeyframes = alphaKeyframes.removeKeyframeAt(timeMs)
)

fun StickerItem.moveAnimationKeyframe(fromMs: Long, toMs: Long): StickerItem = copy(
    positionKeyframes = positionKeyframes.moveKeyframe(fromMs, toMs),
    scaleKeyframes = scaleKeyframes.moveKeyframe(fromMs, toMs),
    rotationKeyframes = rotationKeyframes.moveKeyframe(fromMs, toMs),
    alphaKeyframes = alphaKeyframes.moveKeyframe(fromMs, toMs)
)

fun StickerItem.setAnimationCurveAt(timeMs: Long, curve: Curve): StickerItem = copy(
    positionKeyframes = positionKeyframes.setCurveAt(timeMs, curve),
    scaleKeyframes = scaleKeyframes.setCurveAt(timeMs, curve),
    rotationKeyframes = rotationKeyframes.setCurveAt(timeMs, curve),
    alphaKeyframes = alphaKeyframes.setCurveAt(timeMs, curve)
)

fun StickerItem.animationKeyframeTimes(): Set<Long> = buildSet {
    addAll(positionKeyframes.map(Keyframe<XY>::timeMs))
    addAll(scaleKeyframes.map(Keyframe<Float>::timeMs))
    addAll(rotationKeyframes.map(Keyframe<Float>::timeMs))
    addAll(alphaKeyframes.map(Keyframe<Float>::timeMs))
}

private fun <T> curveAt(timeMs: Long, keys: List<Keyframe<T>>): Curve =
    keys.lastOrNull { it.timeMs <= timeMs }?.curve
        ?: keys.firstOrNull()?.curve
        ?: Curve.SMOOTH

private fun <T> List<Keyframe<T>>.moveKeyframe(fromMs: Long, toMs: Long): List<Keyframe<T>> {
    if (fromMs == toMs) return this
    val moving = firstOrNull { it.timeMs == fromMs } ?: return this
    return filterNot { it.timeMs == fromMs || it.timeMs == toMs }
        .plus(moving.copy(timeMs = toMs))
        .sortedBy(Keyframe<T>::timeMs)
}

private fun <T> List<Keyframe<T>>.setCurveAt(timeMs: Long, curve: Curve): List<Keyframe<T>> =
    map { if (it.timeMs == timeMs) it.copy(curve = curve) else it }
