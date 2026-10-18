package com.apexstudio.app.data.effect

import android.opengl.GLES20
import androidx.media3.common.util.GlUtil
import androidx.media3.effect.BaseGlShaderProgram
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Requires a real EGL implementation. Compilation alone is not device/GPU acceptance. */
@RunWith(AndroidJUnit4::class)
class VideoCropGlLifecycleTest {
    @Test fun repeatedCropChangesReleaseTheirLinkedShaderPrograms() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val display = GlUtil.getDefaultEglDisplay()
        val eglContext = GlUtil.createEglContext(display)
        try {
            val surface = GlUtil.createFocusedPlaceholderEglSurface(eglContext, display)
            try {
                repeat(16) {
                    val shader = VideoCropGlEffect.fromRect(.25f, .25f, .75f, .75f)!!
                        .toGlShaderProgram(context, false) as BaseGlShaderProgram
                    val current = IntArray(1)
                    GLES20.glGetIntegerv(GLES20.GL_CURRENT_PROGRAM, current, 0)
                    val programId = current[0]
                    try {
                        assertTrue("A linked crop program must exist", programId != 0 && GLES20.glIsProgram(programId))
                        val output = shader.configure(1000, 1000)
                        assertEquals(500, output.width)
                        assertEquals(500, output.height)
                    } finally {
                        shader.release()
                    }
                    // A deleted current program remains alive until it is unbound by GLES.
                    GLES20.glUseProgram(0)
                    GlUtil.checkGlError()
                    assertFalse("Crop program leaked after release, iteration $it", GLES20.glIsProgram(programId))
                }
            } finally {
                GlUtil.destroyEglSurface(display, surface)
            }
        } finally {
            GlUtil.destroyEglContext(display, eglContext)
        }
    }
}
