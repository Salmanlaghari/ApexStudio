package com.apexstudio.app.data.effect

import org.junit.Assert.*
import org.junit.Test

class StickerSpriteSizeTest {
    @Test fun portraitUsesPortraitTextureInsteadOfInvertedLandscapeSprite() {
        assertEquals(900 to 1600, StickerSpriteSize.forAspect(9f / 16f))
        assertEquals(1200 to 1600, StickerSpriteSize.forAspect(3f / 4f))
    }
    @Test fun landscapeAndSquareStayBoundedAndKeepAspect() {
        assertEquals(1600 to 900, StickerSpriteSize.forAspect(16f / 9f))
        assertEquals(1600 to 1600, StickerSpriteSize.forAspect(1f))
        for (aspect in listOf(0.01f, 0.2f, 0.5f, 1f, 5f, 50f)) {
            val (width, height) = StickerSpriteSize.forAspect(aspect)
            assertTrue(width in 1..1600)
            assertTrue(height in 1..1600)
        }
    }
    @Test fun malformedAspectFallsBackToFiniteSquare() {
        for (aspect in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 0f)) {
            assertEquals(1600 to 1600, StickerSpriteSize.forAspect(aspect))
        }
    }
}
