package com.apexstudio.app.data.effect

import com.apexstudio.app.domain.model.ChromaKeySettings
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromaKeyGlEffectTest {
    @Test
    fun `shader uses normalized RGB distance and persisted parameter semantics`() {
        val settings = ChromaKeySettings(
            enabled = true,
            keyColorArgb = 0xFF00FF00,
            similarity = 0.4f,
            smoothness = 0.15f,
            spillSuppression = 0.5f
        )
        assertArrayEquals(floatArrayOf(0f, 1f, 0f), ChromaKeyGlEffect.normalizedKeyColor(settings.keyColorArgb), 0f)
        assertTrue(ChromaKeyGlEffect.FRAGMENT_SHADER.contains("distanceToKey"))
        assertTrue(ChromaKeyGlEffect.FRAGMENT_SHADER.contains("uSimilarity"))
        assertTrue(ChromaKeyGlEffect.FRAGMENT_SHADER.contains("uSmoothness"))
        assertTrue(ChromaKeyGlEffect.FRAGMENT_SHADER.contains("uSpillSuppression"))
        assertTrue(ChromaKeyGlEffect.FRAGMENT_SHADER.contains("source.a * matte"))
    }

    @Test
    fun `disabled settings do not create an export effect`() {
        assertTrue(ChromaKeyGlEffect.fromSettings(ChromaKeySettings()) == null)
        assertTrue(ChromaKeyGlEffect.fromSettings(ChromaKeySettings(enabled = true)) != null)
    }
}
