package com.apexstudio.app.data.effect

import android.opengl.GLES20
import androidx.media3.common.util.GlUtil
import androidx.media3.effect.BaseGlShaderProgram
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.apexstudio.app.data.filter.FilterManifest
import com.apexstudio.app.data.filter.LutFilterGlEffect
import com.apexstudio.app.data.fx.FxGlEffect
import com.apexstudio.app.data.fx.FxPreset
import com.apexstudio.app.domain.model.MaskShape
import com.apexstudio.app.domain.model.VideoMask
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Requires a real EGL implementation. Compilation alone is not device/GPU acceptance. */
@RunWith(AndroidJUnit4::class)
class EditorEffectsGlLifecycleTest {
    @Test
    fun repeatedLutFxAndMaskChangesReleaseTheirLinkedShaderPrograms() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        FilterManifest.initialize(context)
        val goldenHour = requireNotNull(FilterManifest.presetById("golden_hour")) {
            "Bundled golden_hour LUT must be available to the GL lifecycle test"
        }
        val mask = requireNotNull(
            MaskGlEffect.fromMask(
                VideoMask(shape = MaskShape.RECTANGLE, centerX = 0.5f, centerY = 0.5f, sizeX = 0.4f, sizeY = 0.4f)
            )
        )

        val display = GlUtil.getDefaultEglDisplay()
        val eglContext = GlUtil.createEglContext(display)
        try {
            val surface = GlUtil.createFocusedPlaceholderEglSurface(eglContext, display)
            try {
                repeatSixTimes("LUT") {
                    LutFilterGlEffect(context, goldenHour)
                        .toGlShaderProgram(context, false) as BaseGlShaderProgram
                }
                repeatSixTimes("FX vignette") {
                    FxGlEffect(FxPreset.VIGNETTE, 0.75f)
                        .toGlShaderProgram(context, false) as BaseGlShaderProgram
                }
                repeatSixTimes("rectangle mask") {
                    mask.toGlShaderProgram(context, false) as BaseGlShaderProgram
                }
            } finally {
                GlUtil.destroyEglSurface(display, surface)
            }
        } finally {
            GlUtil.destroyEglContext(display, eglContext)
        }
    }

    private fun repeatSixTimes(label: String, createShader: () -> BaseGlShaderProgram) {
        val texture = IntArray(1)
        GLES20.glGenTextures(1, texture, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        val pixels = java.nio.ByteBuffer.allocateDirect(4 * 4 * 4).apply {
            put(ByteArray(4 * 4 * 4) { 127.toByte() })
            position(0)
        }
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 4, 4, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels)
        try {
            repeat(6) { iteration ->
                val shader = createShader()
                var programId = 0
                try {
                    shader.configure(4, 4)
                    shader.drawFrame(texture[0], 500_000L)
                    GlUtil.checkGlError()
                    val current = IntArray(1)
                    GLES20.glGetIntegerv(GLES20.GL_CURRENT_PROGRAM, current, 0)
                    programId = current[0]
                    assertTrue("A linked $label program must exist", programId != 0 && GLES20.glIsProgram(programId))
                } finally {
                    shader.release()
                }
                // A deleted current program remains alive until GLES unbinds it.
                GLES20.glUseProgram(0)
                GlUtil.checkGlError()
                assertFalse("$label program leaked after release, iteration $iteration", GLES20.glIsProgram(programId))
            }
        } finally {
            GLES20.glDeleteTextures(1, texture, 0)
        }
    }
}
