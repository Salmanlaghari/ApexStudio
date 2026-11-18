package com.apexstudio.app.data.adjust

import android.opengl.GLES20
import androidx.media3.common.util.GlUtil
import androidx.media3.effect.BaseGlShaderProgram
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.apexstudio.app.domain.model.HslBandAdjustment
import com.apexstudio.app.domain.model.HslColorRange
import com.apexstudio.app.domain.model.SelectiveHslAdjustments
import com.apexstudio.app.domain.model.VideoAdjustments
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdjustmentsGlEffectShaderTest {
    @Test
    fun selectiveHslShaderLinksAndRetainsAllSixColorBandUniforms() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val display = GlUtil.getDefaultEglDisplay()
        val eglContext = GlUtil.createEglContext(display)
        try {
            val surface = GlUtil.createFocusedPlaceholderEglSurface(eglContext, display)
            try {
                val settings = VideoAdjustments(
                    selectiveHsl = SelectiveHslAdjustments(
                        red = HslBandAdjustment(hueShift = 18f, saturation = 0.2f),
                        blue = HslBandAdjustment(lightness = -0.15f)
                    )
                )
                val shader = AdjustmentsGlEffect(adjustments = settings)
                    .toGlShaderProgram(context, false) as BaseGlShaderProgram
                try {
                    val currentProgram = IntArray(1)
                    GLES20.glGetIntegerv(GLES20.GL_CURRENT_PROGRAM, currentProgram, 0)
                    val programId = currentProgram[0]
                    assertTrue("The adjustment shader must link", programId != 0 && GLES20.glIsProgram(programId))
                    HslColorRange.values().forEach { range ->
                        assertTrue(
                            "${range.displayName} HSL uniform must be active",
                            GLES20.glGetUniformLocation(programId, range.shaderUniformName) >= 0
                        )
                    }
                    shader.configure(1280, 720)
                } finally {
                    shader.release()
                }
                GLES20.glUseProgram(0)
                GlUtil.checkGlError()
            } finally {
                GlUtil.destroyEglSurface(display, surface)
            }
        } finally {
            GlUtil.destroyEglContext(display, eglContext)
        }
    }
}
