package com.apexstudio.app.data.effect

import android.graphics.Bitmap
import com.apexstudio.app.data.text.TextAnimEngine
import com.apexstudio.app.data.text.TextSpriteRenderer
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.domain.model.TextOverlayKaraoke
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TextOverlayBatchRasterTest {
    @Test fun measuredWordWindowsMoveHighlightWhilePhraseGapRemainsVisibleWithoutHighlight() {
        val phrase = "Hello world"
        val base = TextOverlay.of(text = phrase).copy(
            x = .5f, y = .5f, colorArgb = 0xFFFFFFFFL,
            highlightSourceFingerprint = TextOverlayKaraoke.fingerprint(phrase),
            highlightColorArgb = 0xFFFFFF00L
        )
        val windows = listOf(
            base.copy(id = "first", startMs = 100L, endMs = 199L,
                highlightStartUtf16 = 0, highlightEndUtf16 = 5),
            base.copy(id = "gap", startMs = 200L, endMs = 299L),
            base.copy(id = "second", startMs = 300L, endMs = 399L,
                highlightStartUtf16 = 6, highlightEndUtf16 = 11)
        )
        fun render(timeMs: Long): Bitmap {
            val plan = TextOverlayFramePlanner.plan(windows, timeMs)
            return TextSpriteRenderer.render(plan.overlays, 800, 300,
                states = plan.overlays.associate { it.id to TextAnimEngine.fullState(it, timeMs, forExport = true) })
        }
        fun yellowXs(bitmap: Bitmap): List<Int> {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            return pixels.indices.filter { i ->
                val pixel = pixels[i]
                (pixel ushr 24 and 255) > 128 && (pixel ushr 16 and 255) > 180 &&
                    (pixel ushr 8 and 255) > 150 && (pixel and 255) < 100
            }.map { it % bitmap.width }
        }
        val first = render(150L)
        val gap = render(250L)
        val second = render(350L)
        val after = render(450L)
        try {
            val firstXs = yellowXs(first)
            val secondXs = yellowXs(second)
            assertTrue(firstXs.size > 5)
            assertTrue(secondXs.size > 5)
            assertTrue("second measured word highlights farther right", firstXs.average() < secondXs.average())
            assertTrue("genuine gap has no highlighted word", yellowXs(gap).isEmpty())
            assertTrue("phrase text remains visible in the gap", (0 until gap.width).any { x ->
                (0 until gap.height).any { y -> gap.getPixel(x, y) ushr 24 != 0 }
            })
            assertTrue("text leaves the active sprite outside its window", (0 until after.width).all { x ->
                (0 until after.height).all { y -> after.getPixel(x, y) ushr 24 == 0 }
            })
        } finally {
            listOf(first, gap, second, after).forEach { it.recycle() }
        }
    }
}
