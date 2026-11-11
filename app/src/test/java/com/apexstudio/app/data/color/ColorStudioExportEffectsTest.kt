package com.apexstudio.app.data.color

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.apexstudio.app.data.adjust.AdjustmentsGlEffect
import com.apexstudio.app.data.filter.LutFilterGlEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ColorStudioExportEffectsTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `project export uses shared LUT then spatial sharpness when enabled`() {
        val effects = createColorStudioVideoEffects(
            context,
            ColorStudioGrade(hdr = 0.4f, sharpness = 0.7f)
        )

        assertEquals(2, effects.size)
        assertTrue(effects[0] is LutFilterGlEffect)
        assertTrue(effects[1] is AdjustmentsGlEffect)
    }

    @Test
    fun `project export omits only spatial stage at zero sharpness`() {
        val effects = createColorStudioVideoEffects(context, ColorStudioGrade(hdr = 0.4f))
        assertEquals(1, effects.size)
        assertTrue(effects.single() is LutFilterGlEffect)
    }

    @Test
    fun `project export effect builder refuses malformed grade`() {
        val failure = runCatching {
            createColorStudioVideoEffects(context, ColorStudioGrade(sharpness = Float.NaN))
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }
}
