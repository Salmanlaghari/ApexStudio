package com.apexstudio.app.data.stickers

import android.graphics.Bitmap
import com.apexstudio.app.domain.model.StickerOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Geometry unit tests for the sticker canvas <-> export contract.
 *
 * [StickerSpriteRenderer.dstRect] / [srcRect] are the single source of
 * truth shared by the preview Canvas ([StickerCanvas]) and the export
 * GL sprite ([com.apexstudio.app.data.effect.StickerGlEffect]); these
 * tests pin the math so preview and export cannot drift apart.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StickerGeometryTest {

    private fun sticker(
        x: Float = 0.5f,
        y: Float = 0.5f,
        sizeScale: Float = 1f,
        cropLeft: Float = 0f,
        cropTop: Float = 0f,
        cropRight: Float = 1f,
        cropBottom: Float = 1f
    ) = StickerOverlay(
        symbolOrUri = "",
        assetPath = "stickers/love/red-heart.png",
        x = x,
        y = y,
        sizeScale = sizeScale,
        cropLeft = cropLeft,
        cropTop = cropTop,
        cropRight = cropRight,
        cropBottom = cropBottom
    )

    @Test
    fun `dstRect centres the sticker box on the normalised position`() {
        val dst = StickerSpriteRenderer.dstRect(sticker(), width = 1080, height = 1920)
        assertEquals(540f, dst.centerX(), 0.5f)
        assertEquals(960f, dst.centerY(), 0.5f)
        val expectedSide = 1920f * StickerSpriteRenderer.BASE_STICKER_FRACTION
        assertEquals(expectedSide, dst.width(), 0.5f)
        assertEquals(expectedSide, dst.height(), 0.5f)
    }

    @Test
    fun `dstRect scales linearly with sizeScale`() {
        val small = StickerSpriteRenderer.dstRect(sticker(sizeScale = 0.5f), 1080, 1920)
        val big = StickerSpriteRenderer.dstRect(sticker(sizeScale = 2f), 1080, 1920)
        assertEquals(small.width() * 4f, big.width(), 0.5f)
    }

    @Test
    fun `srcRect maps crop fractions onto bitmap pixels`() {
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val src = StickerSpriteRenderer.srcRect(
            bitmap,
            sticker(cropLeft = 0.25f, cropTop = 0.25f, cropRight = 0.75f, cropBottom = 0.75f)
        )
        assertEquals(128, src.left)
        assertEquals(128, src.top)
        assertEquals(384, src.right)
        assertEquals(384, src.bottom)
    }

    @Test
    fun `sanitizedCrop repairs inverted and out-of-range rects`() {
        val broken = StickerOverlay(
            cropLeft = 0.9f, cropTop = 0.9f, cropRight = 0.1f, cropBottom = 0.1f
        ).sanitizedCrop()
        assertTrue("left < right after sanitise", broken[0] < broken[2])
        assertTrue("top < bottom after sanitise", broken[1] < broken[3])
        broken.forEach { assertTrue("crop fraction in 0..1: $it", it in 0f..1f) }
    }

    @Test
    fun `sanitizedCrop keeps a valid rect untouched`() {
        val ok = sticker(cropLeft = 0.2f, cropTop = 0.3f, cropRight = 0.8f, cropBottom = 0.9f)
            .sanitizedCrop()
        assertEquals(0.2f, ok[0], 0.001f)
        assertEquals(0.3f, ok[1], 0.001f)
        assertEquals(0.8f, ok[2], 0.001f)
        assertEquals(0.9f, ok[3], 0.001f)
    }

    @Test
    fun `isPngSticker distinguishes bundled pngs from emoji stickers`() {
        assertTrue(sticker().isPngSticker())
        assertTrue(!StickerOverlay(symbolOrUri = "🔥").isPngSticker())
        assertTrue(!StickerOverlay(assetPath = "").isPngSticker())
    }
}
