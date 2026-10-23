package com.apexstudio.app.data.text

import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.domain.model.TextOverlayKaraoke
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TextSpriteRendererKaraokeTest {
    @Test fun activeWordUsesHighlightColorOverFullPhraseAndGradient() {
        val phrase = "Hello world"
        val overlay = TextOverlay.of(text = phrase).copy(
            x = .5f, y = .5f, colorArgb = 0xFFFFFFFFL,
            gradientStartArgb = 0xFF0044FFL, gradientEndArgb = 0xFFAA00FFL,
            highlightStartUtf16 = 6, highlightEndUtf16 = 11,
            highlightSourceFingerprint = TextOverlayKaraoke.fingerprint(phrase),
            highlightColorArgb = 0xFFFFFF00L,
        )
        val bitmap = TextSpriteRenderer.render(listOf(overlay), 800, 300)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val yellowPixels = pixels.count { pixel ->
            val a = pixel ushr 24 and 0xff
            val r = pixel ushr 16 and 0xff
            val g = pixel ushr 8 and 0xff
            val b = pixel and 0xff
            a > 128 && r > 180 && g > 150 && b < 100
        }
        assertTrue("highlighted glyphs should contain yellow pixels, found $yellowPixels", yellowPixels > 5)
        bitmap.recycle()
    }
}
