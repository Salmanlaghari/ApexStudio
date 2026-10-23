package com.apexstudio.app.data.text

import android.graphics.Color
import com.apexstudio.app.domain.model.TextOverlay
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TextDesignMotionRenderTest {
    @Test fun `all 120 combos rasterize nonempty premultiplied sprites through production renderer`() {
        TextDesignMotionPack.presets.forEach { preset ->
            val overlay = preset.applyTo(TextOverlay(id = "title", text = "Creator title"))
            val state = TextAnimEngine.compute(overlay, 320L, forExport = true)
            val bitmap = TextSpriteRenderer.render(listOf(overlay), 384, 216, states = mapOf(overlay.id to state))
            try {
                assertTrue("${preset.id} keeps Android premultiplied alpha", bitmap.isPremultiplied)
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                assertTrue("${preset.id} draws real text pixels", pixels.any { Color.alpha(it) > 0 })
            } finally { bitmap.recycle() }
        }
    }
}
