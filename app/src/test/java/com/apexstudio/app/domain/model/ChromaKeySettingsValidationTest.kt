package com.apexstudio.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromaKeySettingsValidationTest {
    @Test
    fun `key color accepts unsigned and signed 32-bit ARGB values`() {
        val unsigned = ChromaKeySettings(keyColorArgb = 0xFF00FF00L)
        val legacySigned = ChromaKeySettings(keyColorArgb = 0xFF00FF00.toInt().toLong())

        assertTrue(unsigned.hasValidParameters())
        assertTrue(legacySigned.hasValidParameters())
        assertEquals(0xFF00FF00L, legacySigned.sanitized().keyColorArgb)
    }

    @Test
    fun `out-of-range key color fails validation and sanitizes to visible green`() {
        val invalid = ChromaKeySettings(keyColorArgb = 0x1_0000_0000L)

        assertFalse(invalid.hasValidParameters())
        assertEquals(0xFF00E676L, invalid.sanitized().keyColorArgb)
    }
}
