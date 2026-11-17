package com.apexstudio.app.data.effect

import android.opengl.GLES20
import androidx.media3.common.util.GlUtil
import androidx.media3.effect.BaseGlShaderProgram
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.apexstudio.app.domain.model.StickerOverlay
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Requires EGL/GLES; a compile-only test does not verify shader-program cleanup. */
@RunWith(AndroidJUnit4::class)
class StickerGlLifecycleTest {
    @Test
    fun repeatedStickerReleaseDeletesLinkedShaderPrograms() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val display = GlUtil.getDefaultEglDisplay()
        val eglContext = GlUtil.createEglContext(display)
        try {
            val surface = GlUtil.createFocusedPlaceholderEglSurface(eglContext, display)
            try {
                repeat(16) { iteration ->
                    val shader = StickerGlEffect(context, StickerOverlay(), 1f)
                        .toGlShaderProgram(context, false) as BaseGlShaderProgram
                    val current = IntArray(1)
                    GLES20.glGetIntegerv(GLES20.GL_CURRENT_PROGRAM, current, 0)
                    val programId = current[0]
                    try {
                        assertTrue(
                            "A linked sticker program must exist in iteration $iteration",
                            programId != 0 && GLES20.glIsProgram(programId)
                        )
                    } finally {
                        try {
                            shader.release()
                        } finally {
                            // GLES retains a deleted current program until it is unbound.
                            GLES20.glUseProgram(0)
                        }
                    }
                    GlUtil.checkGlError()
                    assertFalse(
                        "Sticker program leaked after release, iteration $iteration",
                        GLES20.glIsProgram(programId)
                    )
                }
            } finally {
                GlUtil.destroyEglSurface(display, surface)
            }
        } finally {
            GlUtil.destroyEglContext(display, eglContext)
        }
    }
}
