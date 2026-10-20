package com.apexstudio.app.data.export

/**
 * Orders independently rendered layers for a single exported clip.
 * Main-video effects (crop, rotation, mask, grading, transitions and keyframes)
 * run first, then frame-anchored artwork, PiP overlays and foreground captions/stickers.
 */
internal fun <T> composeVideoEffectLayers(
    mainVideoEffects: List<T>,
    pipOverlayEffects: List<T>,
    foregroundEffects: List<T>,
    mainArtworkEffects: List<T> = emptyList()
): List<T> = buildList(
    mainVideoEffects.size + mainArtworkEffects.size + pipOverlayEffects.size + foregroundEffects.size
) {
    addAll(mainVideoEffects)
    addAll(mainArtworkEffects)
    addAll(pipOverlayEffects)
    addAll(foregroundEffects)
}
