package com.apexstudio.app.data.export

/**
 * Orders independently rendered layers for a single exported clip.
 * Main-video effects (crop, rotation, mask, grading, transitions and keyframes)
 * run first, then PiP overlays, then foreground captions/stickers.
 */
internal fun <T> composeVideoEffectLayers(
    mainVideoEffects: List<T>,
    pipOverlayEffects: List<T>,
    foregroundEffects: List<T>
): List<T> = buildList(
    mainVideoEffects.size + pipOverlayEffects.size + foregroundEffects.size
) {
    addAll(mainVideoEffects)
    addAll(pipOverlayEffects)
    addAll(foregroundEffects)
}
