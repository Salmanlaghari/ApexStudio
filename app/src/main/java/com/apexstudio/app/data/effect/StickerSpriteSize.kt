package com.apexstudio.app.data.effect

/** PNG and animated sticker textures retain the output aspect, including portrait video. */
internal object StickerSpriteSize {
    fun forAspect(aspect: Float): Pair<Int, Int> = TextOverlaySpriteSize.forAspect(
        aspect.takeIf { it.isFinite() && it > 0f } ?: 1f
    )
}
