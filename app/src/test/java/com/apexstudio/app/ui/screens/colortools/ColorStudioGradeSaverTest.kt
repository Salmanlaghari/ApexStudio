package com.apexstudio.app.ui.screens.colortools

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaverScope
import com.apexstudio.app.data.color.ColorStudioGrade
import com.apexstudio.app.data.color.ColorWheelVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ColorStudioGradeSaverTest {
    private val scope = object : SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }

    private fun save(grade: ColorStudioGrade): List<Float> =
        requireNotNull(with(ColorStudioGradeSaver) {
            with(scope) { save(mutableStateOf(grade)) }
        })

    @Test
    fun `saver round trips HDR style sharpness and existing grade controls`() {
        val grade = ColorStudioGrade(
            brightness = 0.18f,
            contrast = 1.25f,
            saturation = 0.86f,
            shadows = ColorWheelVector(-0.22f, 0.31f),
            hdr = 0.63f,
            sharpness = 0.74f
        )

        val saved = save(grade)
        assertEquals(31, saved.size)
        val restored = ColorStudioGradeSaver.restore(saved)
        assertNotNull(restored)
        assertEquals(grade, restored!!.value)
    }

    @Test
    fun `saver restores legacy grade values with neutral new controls`() {
        val oldGrade = ColorStudioGrade(brightness = -0.12f, contrast = 1.4f, saturation = 0.9f)
        val legacyValues = save(oldGrade).take(29)

        val restored = ColorStudioGradeSaver.restore(legacyValues)
        assertNotNull(restored)
        assertEquals(oldGrade, restored!!.value)
        assertEquals(0f, restored.value.hdr, 0f)
        assertEquals(0f, restored.value.sharpness, 0f)
    }
}
