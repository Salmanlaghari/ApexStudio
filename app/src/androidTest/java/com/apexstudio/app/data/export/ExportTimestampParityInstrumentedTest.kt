package com.apexstudio.app.data.export

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.opengl.GLUtils
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.apexstudio.app.ai.safety.GlobalModerationEnforcement
import com.apexstudio.app.data.audio.PcmAudioExtractor
import com.apexstudio.app.data.background.BackgroundReplaceEngine
import com.apexstudio.app.data.effect.TextOverlayGlShaders
import com.apexstudio.app.data.filter.DownloadedLutFilterRepository
import com.apexstudio.app.data.filter.FilterManifest
import com.apexstudio.app.data.filter.FilterThumbnailGenerator
import com.apexstudio.app.data.filter.LutBitmapCache
import com.apexstudio.app.data.filter.LutFilterEngine
import com.apexstudio.app.data.fx.DownloadedEffectRepository
import com.apexstudio.app.data.fx.DownloadedFxEffect
import com.apexstudio.app.data.fx.FxGlEffect
import com.apexstudio.app.data.fx.FxPreset
import com.apexstudio.app.data.fx.ModernFxShaders
import com.apexstudio.app.data.packs.download.PackManager
import com.apexstudio.app.data.packs.download.PackManifestRepo
import com.apexstudio.app.data.photoedit.PhotoVideoEncoder
import com.apexstudio.app.data.repository.ProjectRepository
import com.apexstudio.app.data.stickers.LottieFrameCache
import com.apexstudio.app.data.stickers.StickerAssetResolver
import com.apexstudio.app.data.stickers.StickerSpriteRenderer
import com.apexstudio.app.data.template.ShortsMusicRepository
import com.apexstudio.app.data.template.ShortsTemplateCatalog
import com.apexstudio.app.data.template.ShortsTemplateProjectFactory
import com.apexstudio.app.domain.model.BackgroundReplaceMode
import com.apexstudio.app.domain.model.BackgroundReplaceSettings
import com.apexstudio.app.domain.model.ChromaKeySettings
import com.apexstudio.app.domain.model.ExportSettings
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.domain.model.ProjectFxSettings
import com.apexstudio.app.domain.model.StickerOverlay
import com.apexstudio.app.domain.model.VideoAdjustments
import com.apexstudio.app.presentation.state.AudioStudioState
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.buildProjectExportConfig
import com.apexstudio.app.presentation.viewmodel.flushProject
import com.apexstudio.app.presentation.viewmodel.selectFx
import com.apexstudio.app.presentation.viewmodel.setFxSpeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** Real Media3/codec regression; CI compiles this test but needs a device to execute it. */
@RunWith(AndroidJUnit4::class)
@UnstableApi
class ExportTimestampParityInstrumentedTest {
    @Test
    fun downloadedFamilySamplesAndPublishedProfilesCompileOnGles2() {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        assertTrue("EGL display must be available", display !== EGL14.EGL_NO_DISPLAY)
        val version = IntArray(2)
        assertTrue("EGL must initialize", EGL14.eglInitialize(display, version, 0, version, 1))

        val configs = arrayOfNulls<EGLConfig>(1)
        val configCount = IntArray(1)
        val configAttrs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_NONE
        )
        assertTrue("EGL must provide an ES2 pbuffer config", EGL14.eglChooseConfig(
            display, configAttrs, 0, configs, 0, configs.size, configCount, 0
        ))
        assertTrue("At least one EGL config is required", configCount[0] > 0)
        val config = requireNotNull(configs[0])
        val context = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
        )
        assertTrue("An ES2 context must be created", context !== EGL14.EGL_NO_CONTEXT)
        val surface = EGL14.eglCreatePbufferSurface(
            display, config,
            intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0
        )
        assertTrue("A pbuffer surface must be created", surface !== EGL14.EGL_NO_SURFACE)
        assertTrue("The ES2 context must become current", EGL14.eglMakeCurrent(display, surface, surface, context))

        val profiles = listOf(
            Triple("PRISM", floatArrayOf(1.7f, 0.13f, 0.42f), false),
            Triple("SPLIT_TONE", floatArrayOf(0.24f, 0.38f, 0.68f), false),
            Triple("RIPPLE", floatArrayOf(2.2f, 0.013f, 2.8f), true),
            Triple("MOSAIC", floatArrayOf(5f, 7f, 0.025f), false),
            Triple("CRT", floatArrayOf(120f, 0.20f, 0.12f), true),
            Triple("HALATION", floatArrayOf(0.30f, 7f, 0.62f), false),
            Triple("INK", floatArrayOf(0.18f, 0.52f, 0.15f), false),
            Triple("MIRROR", floatArrayOf(4f, 0f, 0.72f), false),
            Triple("GLITCH", floatArrayOf(7f, 0.006f, 0.18f), true),
            Triple("STREAK", floatArrayOf(0.10f, 0.36f, 0.66f), true)
        )
        try {
            profiles.forEachIndexed { index, (family, values, animated) ->
                val effect = DownloadedFxEffect(
                    id = "gles_${family.lowercase()}",
                    label = family,
                    category = "Creator Pack",
                    family = family,
                    a = values[0],
                    b = values[1],
                    c = values[2],
                    tintR = 0.22f + index * 0.02f,
                    tintG = 0.7f,
                    tintB = 0.9f,
                    isAnimated = animated,
                    defaultSpeed = 1f
                )
                val fragment = requireNotNull(ModernFxShaders.shaderFor(effect))
                val shader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER)
                assertTrue("$family shader object must be created", shader != 0)
                GLES20.glShaderSource(shader, fragment)
                GLES20.glCompileShader(shader)
                val compiled = IntArray(1)
                GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
                val log = GLES20.glGetShaderInfoLog(shader)
                GLES20.glDeleteShader(shader)
                assertTrue("$family downloadable shader must compile: $log", compiled[0] == GLES20.GL_TRUE)
            }

            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val publishedPayload = instrumentation.context.assets
                .open("fx_profiles.json")
                .bufferedReader()
                .use { it.readText() }
            val items = JSONObject(publishedPayload).getJSONArray("effects")
            assertEquals("The published FX test asset must contain all 30 profiles", 30, items.length())
            val publishedProfiles = (0 until items.length()).map { index ->
                val item = items.getJSONObject(index)
                val tint = item.getJSONArray("tint")
                DownloadedFxEffect(
                    id = item.getString("id"),
                    label = item.getString("name"),
                    category = item.getString("category"),
                    family = item.getString("family"),
                    a = item.getDouble("a").toFloat(),
                    b = item.getDouble("b").toFloat(),
                    c = item.getDouble("c").toFloat(),
                    tintR = tint.getDouble(0).toFloat(),
                    tintG = tint.getDouble(1).toFloat(),
                    tintB = tint.getDouble(2).toFloat(),
                    isAnimated = item.getBoolean("animated"),
                    defaultSpeed = item.getDouble("defaultSpeed").toFloat()
                )
            }
            assertEquals(
                "All published profiles must have unique IDs",
                publishedProfiles.size,
                publishedProfiles.map { it.id }.toSet().size
            )
            publishedProfiles.forEach { effect ->
                val program = FxGlEffect(effect, intensity = 0.73f, speed = effect.defaultSpeed)
                    .toGlShaderProgram(instrumentation.targetContext, useHdr = false) as BaseGlShaderProgram
                try {
                    val outputSize = program.configure(320, 180)
                    assertEquals("${effect.id} program width", 320, outputSize.width)
                    assertEquals("${effect.id} program height", 180, outputSize.height)
                } finally {
                    program.release()
                }
            }
        } finally {
            EGL14.eglMakeCurrent(
                display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
            )
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
    }

    @Test
    fun fxWrapperDrawsUnusedUniformPresetsAndAnimatedSpeedPresetOnRealEglFrame() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val width = 128
        val height = 128
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        assertTrue("EGL display must be available", display !== EGL14.EGL_NO_DISPLAY)
        val version = IntArray(2)
        assertTrue("EGL must initialize", EGL14.eglInitialize(display, version, 0, version, 1))
        val configs = arrayOfNulls<EGLConfig>(1)
        val configCount = IntArray(1)
        assertTrue(
            "EGL must provide an ES2 pbuffer config",
            EGL14.eglChooseConfig(
                display,
                intArrayOf(
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_NONE
                ), 0, configs, 0, configs.size, configCount, 0
            )
        )
        assertTrue("At least one EGL config is required", configCount[0] > 0)
        val config = requireNotNull(configs[0])
        val eglContext = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
        )
        assertTrue("An ES2 context must be created", eglContext !== EGL14.EGL_NO_CONTEXT)
        val surface = EGL14.eglCreatePbufferSurface(
            display, config,
            intArrayOf(EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE), 0
        )
        assertTrue("An ES2 pbuffer surface must be created", surface !== EGL14.EGL_NO_SURFACE)

        val textures = mutableListOf<Int>()
        val framebuffers = mutableListOf<Int>()
        val bitmaps = mutableListOf<Bitmap>()

        fun createTexture(bitmap: Bitmap? = null): Int {
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            assertTrue("GL texture must be created", ids[0] != 0)
            textures += ids[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            if (bitmap == null) {
                GLES20.glTexImage2D(
                    GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
                )
            } else {
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            }
            return ids[0]
        }

        fun assertNoGlError(label: String) {
            assertEquals("$label must leave GLES error state clear", GLES20.GL_NO_ERROR, GLES20.glGetError())
        }

        try {
            assertTrue("The ES2 context must become current", EGL14.eglMakeCurrent(display, surface, surface, eglContext))
            val inputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmaps += inputBitmap
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val checker = ((x / 8) + (y / 8)) and 1
                    inputBitmap.setPixel(
                        x, y,
                        Color.argb(
                            255,
                            (x * 2 + if (checker == 0) 24 else 0).coerceAtMost(255),
                            (y * 2 + if (checker == 1) 24 else 0).coerceAtMost(255),
                            ((x + y) * 255 / (width + height - 2)).coerceIn(0, 255)
                        )
                    )
                }
            }
            val inputTexture = createTexture(inputBitmap)
            val outputTexture = createTexture()
            val framebuffer = IntArray(1)
            GLES20.glGenFramebuffers(1, framebuffer, 0)
            assertTrue("GL framebuffer must be created", framebuffer[0] != 0)
            framebuffers += framebuffer[0]
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer[0])
            GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, outputTexture, 0
            )
            assertEquals(
                "Output framebuffer must be complete",
                GLES20.GL_FRAMEBUFFER_COMPLETE,
                GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
            )
            GLES20.glViewport(0, 0, width, height)

            val inputPixels = IntArray(width * height)
            inputBitmap.getPixels(inputPixels, 0, width, 0, 0, width, height)
            val effects = listOf(
                Triple("lens_flare", FxPreset.LENS_FLARE, 0L),
                Triple("vignette", FxPreset.VIGNETTE, 0L),
                Triple("hue_cycle", FxPreset.HUE_CYCLE, 700_000L)
            )
            effects.forEach { (label, preset, timeUs) ->
                val program = FxGlEffect(preset, intensity = 1f, speed = 1.4f)
                    .toGlShaderProgram(context, useHdr = false) as BaseGlShaderProgram
                try {
                    val configured = program.configure(width, height)
                    assertEquals("$label output width", width, configured.width)
                    assertEquals("$label output height", height, configured.height)
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer[0])
                    GLES20.glViewport(0, 0, width, height)
                    GLES20.glClearColor(0f, 0f, 0f, 0f)
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                    assertNoGlError("$label setup")

                    // This is intentionally the production FxGlEffect wrapper. A raw GLSL draw
                    // would let glUniform(-1) no-op, whereas GlProgram's uniform cache throws on
                    // the same inactive declaration and is the regression being protected.
                    program.drawFrame(inputTexture, timeUs)
                    GLES20.glFinish()
                    assertNoGlError("$label draw")

                    val pixels = ByteBuffer.allocateDirect(width * height * 4)
                        .order(ByteOrder.nativeOrder())
                    GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels)
                    assertNoGlError("$label readback")
                    val output = ByteArray(width * height * 4)
                    pixels.get(output)
                    assertTrue(
                        "$label must produce nonzero output",
                        output.any { (it.toInt() and 0xff) != 0 }
                    )
                    var changedPixels = 0
                    for (index in inputPixels.indices) {
                        val offset = index * 4
                        val distance = abs(Color.red(inputPixels[index]) - (output[offset].toInt() and 0xff)) +
                            abs(Color.green(inputPixels[index]) - (output[offset + 1].toInt() and 0xff)) +
                            abs(Color.blue(inputPixels[index]) - (output[offset + 2].toInt() and 0xff))
                        if (distance >= 80) changedPixels++
                    }
                    assertTrue(
                        "$label must produce meaningful changed output; changed pixels=$changedPixels",
                        changedPixels >= 500
                    )
                } finally {
                    program.release()
                    GLES20.glUseProgram(0)
                }
            }
        } finally {
            if (framebuffers.isNotEmpty()) GLES20.glDeleteFramebuffers(framebuffers.size, framebuffers.toIntArray(), 0)
            if (textures.isNotEmpty()) GLES20.glDeleteTextures(textures.size, textures.toIntArray(), 0)
            bitmaps.forEach { if (!it.isRecycled) it.recycle() }
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, eglContext)
            EGL14.eglTerminate(display)
        }
    }

    @Test
    fun captionBitmapUvIsCorrectedWithoutFlippingMedia3VideoUv() {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        assertTrue("EGL display must be available", display !== EGL14.EGL_NO_DISPLAY)
        val version = IntArray(2)
        assertTrue("EGL must initialize", EGL14.eglInitialize(display, version, 0, version, 1))
        val configs = arrayOfNulls<EGLConfig>(1)
        val configCount = IntArray(1)
        val configAttrs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE
        )
        assertTrue("EGL must provide an ES2 pbuffer config", EGL14.eglChooseConfig(
            display, configAttrs, 0, configs, 0, configs.size, configCount, 0
        ))
        assertTrue("At least one EGL config is required", configCount[0] > 0)
        val config = requireNotNull(configs[0])
        val eglContext = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
        )
        assertTrue("An ES2 context must be created", eglContext !== EGL14.EGL_NO_CONTEXT)
        val surface = EGL14.eglCreatePbufferSurface(
            display, config,
            intArrayOf(EGL14.EGL_WIDTH, 2, EGL14.EGL_HEIGHT, 2, EGL14.EGL_NONE), 0
        )
        assertTrue("An ES2 pbuffer surface must be created", surface !== EGL14.EGL_NO_SURFACE)

        val textureIds = mutableListOf<Int>()
        val bitmaps = mutableListOf<Bitmap>()
        val shaderIds = mutableListOf<Int>()
        var program = 0
        try {
            assertTrue("The ES2 context must become current", EGL14.eglMakeCurrent(display, surface, surface, eglContext))

            fun compileShader(type: Int, source: String): Int {
                val shader = GLES20.glCreateShader(type)
                assertTrue("Shader object must be created", shader != 0)
                shaderIds += shader
                GLES20.glShaderSource(shader, source)
                GLES20.glCompileShader(shader)
                val compiled = IntArray(1)
                GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
                assertTrue("Production text overlay shader must compile: ${GLES20.glGetShaderInfoLog(shader)}",
                    compiled[0] == GLES20.GL_TRUE)
                return shader
            }

            fun uploadBitmap(bitmap: Bitmap): Int {
                val texture = IntArray(1)
                GLES20.glGenTextures(1, texture, 0)
                assertTrue("Bitmap texture must be created", texture[0] != 0)
                textureIds += texture[0]
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0])
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
                return texture[0]
            }

            fun stripedBitmap(topColor: Int, bottomColor: Int): Bitmap =
                Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).also { bitmap ->
                    bitmaps += bitmap
                    for (x in 0..1) {
                        bitmap.setPixel(x, 0, topColor)
                        bitmap.setPixel(x, 1, bottomColor)
                    }
                }

            // Media3's input texture convention is the one already consumed by vTextureCoord:
            // its bottom storage row should appear at the top of the drawn video. The Canvas
            // caption texture uses normal top-first bitmap rows and therefore needs its own V flip.
            val videoTexture = uploadBitmap(stripedBitmap(topColor = Color.BLUE, bottomColor = Color.RED))
            val captionTexture = uploadBitmap(stripedBitmap(topColor = Color.GREEN, bottomColor = Color.MAGENTA))
            val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, TextOverlayGlShaders.vertex)
            val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, TextOverlayGlShaders.fragment)
            program = GLES20.glCreateProgram()
            assertTrue("Production text overlay program must be created", program != 0)
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)
            val linked = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
            assertTrue("Production text overlay program must link: ${GLES20.glGetProgramInfoLog(program)}",
                linked[0] == GLES20.GL_TRUE)

            GLES20.glViewport(0, 0, 2, 2)
            GLES20.glUseProgram(program)
            val framePosition = GLES20.glGetAttribLocation(program, "aFramePosition")
            val vertexBuffer = ByteBuffer.allocateDirect(16 * Float.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(floatArrayOf(
                        -1f, -1f, 0f, 1f,
                        1f, -1f, 0f, 1f,
                        -1f, 1f, 0f, 1f,
                        1f, 1f, 0f, 1f
                    ))
                    position(0)
                }
            GLES20.glEnableVertexAttribArray(framePosition)
            GLES20.glVertexAttribPointer(framePosition, 4, GLES20.GL_FLOAT, false, 0, vertexBuffer)

            val identity = floatArrayOf(
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f
            )
            val videoTextureMatrix = floatArrayOf(
                0.5f, 0f, 0f, 0f,
                0f, 0.5f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0.5f, 0.5f, 0f, 1f
            )
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uTransformationMatrix"), 1, false, identity, 0)
            GLES20.glUniformMatrix4fv(
                GLES20.glGetUniformLocation(program, "uTexTransformationMatrix"), 1, false, videoTextureMatrix, 0
            )
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexSampler"), 0)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uOverlaySampler"), 1)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, videoTexture)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, captionTexture)

            fun render(overlayEnabled: Float): ByteArray {
                GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uOverlayEnabled"), overlayEnabled)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                GLES20.glFinish()
                val pixels = ByteBuffer.allocateDirect(2 * 2 * 4).order(ByteOrder.nativeOrder())
                GLES20.glReadPixels(0, 0, 2, 2, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels)
                return ByteArray(2 * 2 * 4).also { pixels.get(it) }
            }

            fun assertRgb(pixels: ByteArray, x: Int, y: Int, red: Int, green: Int, blue: Int, label: String) {
                val offset = (y * 2 + x) * 4
                assertEquals("$label red", red, pixels[offset].toInt() and 0xff)
                assertEquals("$label green", green, pixels[offset + 1].toInt() and 0xff)
                assertEquals("$label blue", blue, pixels[offset + 2].toInt() and 0xff)
            }

            val videoOnly = render(overlayEnabled = 0f)
            assertRgb(videoOnly, 0, 1, 255, 0, 0, "Media3 video top remains upright")
            assertRgb(videoOnly, 0, 0, 0, 0, 255, "Media3 video bottom remains upright")

            val withCaption = render(overlayEnabled = 1f)
            assertRgb(withCaption, 0, 1, 0, 255, 0, "Android Canvas caption top remains upright")
            assertRgb(withCaption, 0, 0, 255, 0, 255, "Android Canvas caption bottom remains upright")
        } finally {
            if (program != 0) GLES20.glDeleteProgram(program)
            shaderIds.forEach(GLES20::glDeleteShader)
            if (textureIds.isNotEmpty()) GLES20.glDeleteTextures(textureIds.size, textureIds.toIntArray(), 0)
            bitmaps.forEach { if (!it.isRecycled) it.recycle() }
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, eglContext)
            EGL14.eglTerminate(display)
        }
    }

    @Test
    fun selectedAndProjectExportsPreserveVfrTimingWithinRequestedAverageFrameRate() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val engine = ExportEngine(context)
        val id = UUID.randomUUID().toString()
        val source = File(context.cacheDir, "vfr-timestamp-source-$id.mp4")
        val selectedName = "vfr-selected-$id.mp4"
        val projectName = "vfr-project-$id.mp4"
        val outputDir = File(context.getExternalFilesDir(null), "ApexStudio_Exports")
        val trimStartMs = 200L
        val trimEndMs = 1_800L
        val fps = 24
        var selectedOutput: File? = null
        var projectOutput: File? = null

        fun copyFixture() {
            instrumentation.context.assets.open("export/vfr_gap_audio_fixture.mp4").use { input ->
                source.outputStream().use { output -> input.copyTo(output) }
            }
        }

        try {
            copyFixture()
            val sourceTrack = readVideoTrack(source)
            assertTrue("Fixture must contain deliberately sparse VFR timestamps", sourceTrack.maxGapUs > 500_000L)
            assertTrue("Fixture must retain its continuous audio track", hasTrack(source, "audio/"))
            val sourceAnchorsUs = sourceTrack.presentationTimesUs.filter {
                it >= trimStartMs * 1_000L && it < trimEndMs * 1_000L
            }
            assertTrue("Trimmed VFR interval must contain multiple source timestamp anchors", sourceAnchorsUs.size >= 4)
            // FrameDropEffect targets average cadence and may discard frames from short local bursts.
            // Keep the first visible sample and source landmarks separated by at least one target interval.
            val targetIntervalUs = 1_000_000L / fps
            val sparseSourceAnchorUs = sourceAnchorsUs.filterIndexed { index, sourceTimeUs ->
                index == 0 || sourceTimeUs - sourceAnchorsUs[index - 1] > targetIntervalUs
            }
            assertTrue("Trimmed VFR interval must retain landmarks across its long gaps", sparseSourceAnchorUs.size >= 3)

            val selectedConfig = ExportEngine.ExportConfig(
                resolution = "720p",
                fps = fps,
                aspectRatio = "16:9",
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs
            )
            engine.startExport(Uri.fromFile(source).toString(), selectedConfig, selectedName)
            val selectedResult = awaitTerminalState(engine)
            assertNull("Selected export must complete: ${selectedResult.error}", selectedResult.error)
            selectedOutput = File(requireNotNull(Uri.parse(selectedResult.outputUri).path))
            assertOutputTiming(
                label = "selected export",
                output = readVideoTrack(selectedOutput!!),
                expectedDurationUs = (trimEndMs - trimStartMs) * 1_000L,
                expectedSourceAnchorsUs = sparseSourceAnchorUs.mapIndexed { index, sourceTimeUs ->
                    if (index == 0) 0L else sourceTimeUs - trimStartMs * 1_000L
                },
                requestedFps = fps
            )

            val sourceDurationMs = sourceTrack.videoTrackDurationUs / 1_000L
            assertTrue("Fixture duration must extend beyond the trim", sourceDurationMs > trimEndMs)
            val clips = (0..1).map { index ->
                MediaClip(
                    id = "vfr-timestamp-$id-$index",
                    name = "VFR timestamp fixture $index",
                    uri = Uri.fromFile(source).toString(),
                    durationMs = sourceDurationMs,
                    trimStartMs = trimStartMs,
                    trimEndMs = trimEndMs
                )
            }
            val plan = ProjectCompositionTimeline.build(clips)
            assertEquals("Two trimmed clips must form a sequential project plan", 2, plan.entries.size)
            assertEquals("Project duration must equal the sum of retained clip durations", 3_200L, plan.durationMs)

            engine.startProjectExport(
                clips = clips,
                config = ExportEngine.ExportConfig(resolution = "720p", fps = fps, aspectRatio = "16:9"),
                outputFileName = projectName
            )
            val projectResult = awaitTerminalState(engine)
            assertNull("Whole-project export must complete: ${projectResult.error}", projectResult.error)
            projectOutput = File(requireNotNull(Uri.parse(projectResult.outputUri).path))
            val expectedProjectAnchorsUs = plan.entries.flatMapIndexed { entryIndex, entry ->
                sparseSourceAnchorUs.mapIndexed { anchorIndex, sourceTimeUs ->
                    val relativeToClipStartUs = sourceTimeUs - trimStartMs * 1_000L
                    entry.timelineStartMs * 1_000L +
                        if (entryIndex == 0 && anchorIndex == 0) 0L else relativeToClipStartUs
                }
            }
            assertOutputTiming(
                label = "whole-project export",
                output = readVideoTrack(projectOutput!!),
                expectedDurationUs = plan.durationMs * 1_000L,
                expectedSourceAnchorsUs = expectedProjectAnchorsUs,
                requestedFps = fps
            )
        } finally {
            instrumentation.runOnMainSync { engine.release() }
            source.delete()
            selectedOutput?.delete()
            projectOutput?.delete()
            File(outputDir, selectedName).delete()
            File(outputDir, projectName).delete()
            File(outputDir, selectedName.removeSuffix(".mp4") + "_music.mp4").delete()
            File(outputDir, projectName.removeSuffix(".mp4") + "_music.mp4").delete()
        }
    }

    @Test
    fun downloadedLottieStickerAdvancesAndPreservesPixelsOutsideItsBoundsInBothExportPaths() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        // Terminal state can be published before the previous export writer has finished cleanup.
        val baselineEngine = ExportEngine(context)
        val stickerEngine = ExportEngine(context)
        val id = UUID.randomUUID().toString()
        val source = File(context.cacheDir, "sticker-export-source-$id.mp4")
        val packId = "sticker-export-$id"
        val relativePath = "animated/heart.json"
        val packArchive = File(context.cacheDir, "$packId.zip")
        val packDir = PackManager.packDir(context, packId)
        val assetFile = File(packDir, relativePath)
        val registryFile = File(PackManager.packsRoot(context), "installed.json")
        val previousRegistry = registryFile.takeIf { it.isFile }?.readText()
        val baselineName = "sticker-baseline-$id.mp4"
        val selectedName = "sticker-selected-$id.mp4"
        val projectBaselineName = "sticker-project-baseline-$id.mp4"
        val projectName = "sticker-project-$id.mp4"
        val outputDir = File(context.getExternalFilesDir(null), "ApexStudio_Exports")
        val baselineConfig = ExportEngine.ExportConfig(
            resolution = "720p",
            fps = 30,
            aspectRatio = "16:9",
            trimEndMs = 1_800L
        )
        var baselineOutput: File? = null
        var selectedOutput: File? = null
        var projectBaselineOutput: File? = null
        var projectOutput: File? = null
        val assetPath = StickerAssetResolver.packUri(packId, relativePath)
        val sticker = StickerOverlay(
            id = "downloaded-heart-$id",
            symbolOrUri = "",
            category = "Love",
            name = "Heart Pop",
            x = 0.5f,
            y = 0.5f,
            sizeScale = 2f,
            startMs = 0L,
            endMs = 1_800L,
            assetPath = assetPath
        )
        var earlyTimeUs = 0L
        var laterTimeUs = 0L

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

        fun installTestPackAsset() {
            val bundledAsset = context.assets.open("lottie/heart_pop.json").use { it.readBytes() }
            ZipOutputStream(packArchive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry(relativePath))
                zip.write(bundledAsset)
                zip.closeEntry()
            }
            assetFile.parentFile?.mkdirs()
            ZipFile(packArchive).use { zip ->
                val entry = requireNotNull(zip.getEntry(relativePath)) { "Sticker asset must exist in the test pack archive" }
                zip.getInputStream(entry).use { input ->
                    assetFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            assertEquals(
                "Extracted sticker asset must match the bundled fixture SHA-256",
                sha256(bundledAsset),
                sha256(assetFile.readBytes())
            )
            val archiveSha256 = sha256(packArchive.readBytes())
            PackManager.recordInstalledPack(context, packId, 1, archiveSha256)
            assertEquals(
                "PackManager's production serializer must retain the verified archive SHA-256",
                archiveSha256,
                PackManager.installedPackSha256(context, packId)
            )
        }

        fun decodeFrame(file: File, timeUs: Long): Bitmap {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(file.absolutePath)
                requireNotNull(retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)) {
                    "Could not decode a sticker export frame from ${file.name}"
                }
            } finally {
                retriever.release()
            }
        }

        fun stickerPixelBounds(frame: Bitmap): IntArray {
            val rect = StickerSpriteRenderer.dstRect(sticker, frame.width, frame.height)
            return intArrayOf(
                floor(rect.left.toDouble()).toInt().coerceIn(0, frame.width),
                floor(rect.top.toDouble()).toInt().coerceIn(0, frame.height),
                ceil(rect.right.toDouble()).toInt().coerceIn(0, frame.width),
                ceil(rect.bottom.toDouble()).toInt().coerceIn(0, frame.height)
            )
        }

        fun pixelDistance(first: Int, second: Int): Int =
            abs(Color.red(first) - Color.red(second)) +
                abs(Color.green(first) - Color.green(second)) +
                abs(Color.blue(first) - Color.blue(second))

        fun framePixels(frame: Bitmap): IntArray =
            IntArray(frame.width * frame.height).also { pixels ->
                frame.getPixels(pixels, 0, frame.width, 0, 0, frame.width, frame.height)
            }

        fun animationChangedPixels(first: IntArray, second: IntArray): Int {
            var changed = 0
            for (index in first.indices) {
                val a = first[index]
                val b = second[index]
                if (Color.alpha(a) > 32 || Color.alpha(b) > 32) {
                    val delta = pixelDistance(a, b) + abs(Color.alpha(a) - Color.alpha(b))
                    if (delta >= 80) changed++
                }
            }
            return changed
        }

        fun selectVisibleAnimationSamples(commonTimesUs: List<Long>) {
            val frames = requireNotNull(LottieFrameCache.frames(context, assetPath)) {
                "The installed heart-pop pack must render Lottie frames"
            }
            assertTrue("The installed Lottie cache must contain frames", frames.isNotEmpty())
            val pixels = frames.map(::framePixels)
            val visible = commonTimesUs.mapNotNull { timeUs ->
                val timeMs = timeUs / 1_000L
                if (timeMs < sticker.startMs || timeMs >= sticker.endMs) return@mapNotNull null
                val index = LottieFrameCache.frameIndex(timeMs, sticker.startMs, frames.size)
                val frame = pixels[index]
                val opaquePixels = frame.count { Color.alpha(it) >= 32 }
                if (opaquePixels >= 500) Triple(timeUs, index, opaquePixels) else null
            }
            assertTrue(
                "Need at least two visible common retained PTS candidates; common=${commonTimesUs.joinToString()} " +
                    "visible=${visible.map { "${it.first}us->cache${it.second}" }}",
                visible.size >= 2
            )

            var bestEarlyIndex = -1
            var bestLaterIndex = -1
            var bestEarlyTimeUs = 0L
            var bestLaterTimeUs = 0L
            var bestScore = -1
            for (early in 0 until visible.lastIndex) {
                val (earlyTime, earlyIndex, earlyOpaquePixels) = visible[early]
                for (later in early + 1 until visible.size) {
                    val (laterTime, laterIndex, laterOpaquePixels) = visible[later]
                    val changedPixels = animationChangedPixels(pixels[earlyIndex], pixels[laterIndex])
                    if (changedPixels >= 100) {
                        val score = minOf(earlyOpaquePixels, laterOpaquePixels) + changedPixels
                        if (score > bestScore) {
                            bestEarlyIndex = earlyIndex
                            bestLaterIndex = laterIndex
                            bestEarlyTimeUs = earlyTime
                            bestLaterTimeUs = laterTime
                            bestScore = score
                        }
                    }
                }
            }
            assertTrue(
                "The downloaded Lottie pack must have two distinct visible animation phases; " +
                    "common=${commonTimesUs.joinToString()}, visible=${visible.map { "${it.first}us->cache${it.second}" }}",
                bestEarlyIndex >= 0
            )
            earlyTimeUs = bestEarlyTimeUs
            laterTimeUs = bestLaterTimeUs
            assertTrue("Selected cache phases must be distinct", bestEarlyIndex != bestLaterIndex)
            assertTrue("Selected common source PTS must be ordered", earlyTimeUs < laterTimeUs)
            println("sticker-retained-pts chosen: early=${earlyTimeUs}us cache=$bestEarlyIndex; later=${laterTimeUs}us cache=$bestLaterIndex")
        }

        fun assertStickerPixels(
            label: String,
            frame: Bitmap,
            baseline: Bitmap,
            bounds: IntArray,
            framePixels: IntArray,
            baselinePixels: IntArray
        ) {
            assertEquals("$label width", baseline.width, frame.width)
            assertEquals("$label height", baseline.height, frame.height)
            var changedPixels = 0
            for (y in bounds[1] until bounds[3]) {
                for (x in bounds[0] until bounds[2]) {
                    val index = y * frame.width + x
                    if (pixelDistance(baselinePixels[index], framePixels[index]) >= 80) changedPixels++
                }
            }
            assertTrue(
                "$label must visibly composite the downloaded Lottie sticker inside its sprite bounds; changed pixels=$changedPixels",
                changedPixels >= 500
            )
        }

        fun assertBackgroundUnchangedOutsideSticker(
            label: String,
            frame: Bitmap,
            baseline: Bitmap,
            bounds: IntArray,
            framePixels: IntArray,
            baselinePixels: IntArray
        ) {
            assertEquals("$label width", baseline.width, frame.width)
            assertEquals("$label height", baseline.height, frame.height)
            var changedPixels = 0
            var outsidePixels = 0
            for (y in 0 until frame.height) {
                for (x in 0 until frame.width) {
                    val insideSticker =
                        x in (bounds[0] until bounds[2]) && y in (bounds[1] until bounds[3])
                    if (!insideSticker) {
                        outsidePixels++
                        val index = y * frame.width + x
                        if (pixelDistance(baselinePixels[index], framePixels[index]) >= 80) changedPixels++
                    }
                }
            }
            val allowedChangedPixels = (outsidePixels / 100).coerceAtLeast(100)
            assertTrue(
                "$label must preserve the source video outside the sticker sprite bounds; " +
                    "changed=$changedPixels, allowed=$allowedChangedPixels",
                changedPixels <= allowedChangedPixels
            )
        }

        fun assertAnimationAdvances(
            label: String,
            early: Bitmap,
            later: Bitmap,
            earlyPixels: IntArray,
            earlyBaselinePixels: IntArray,
            laterPixels: IntArray,
            laterBaselinePixels: IntArray,
            bounds: IntArray
        ) {
            assertEquals("$label frame width", early.width, later.width)
            assertEquals("$label frame height", early.height, later.height)
            var changedOverlayPixels = 0
            for (y in bounds[1] until bounds[3]) {
                for (x in bounds[0] until bounds[2]) {
                    val index = y * early.width + x
                    val earlyPixel = earlyPixels[index]
                    val earlyBase = earlyBaselinePixels[index]
                    val laterPixel = laterPixels[index]
                    val laterBase = laterBaselinePixels[index]
                    val delta =
                        abs((Color.red(earlyPixel) - Color.red(earlyBase)) - (Color.red(laterPixel) - Color.red(laterBase))) +
                            abs((Color.green(earlyPixel) - Color.green(earlyBase)) - (Color.green(laterPixel) - Color.green(laterBase))) +
                            abs((Color.blue(earlyPixel) - Color.blue(earlyBase)) - (Color.blue(laterPixel) - Color.blue(laterBase)))
                    if (delta >= 80) changedOverlayPixels++
                }
            }
            assertTrue(
                "$label must show distinct Lottie output between $earlyTimeUs us and $laterTimeUs us; " +
                    "changed overlay pixels=$changedOverlayPixels",
                changedOverlayPixels >= 100
            )
        }

        fun verifyExportFrames(label: String, baselineFile: File, outputFile: File) {
            val baselineEarly = decodeFrame(baselineFile, earlyTimeUs)
            val outputEarly = decodeFrame(outputFile, earlyTimeUs)
            val baselineLater = decodeFrame(baselineFile, laterTimeUs)
            val outputLater = decodeFrame(outputFile, laterTimeUs)
            try {
                assertEquals("720p export width", 1_280, baselineEarly.width)
                assertEquals("720p export height", 720, baselineEarly.height)
                val bounds = stickerPixelBounds(baselineEarly)
                val baselineEarlyPixels = framePixels(baselineEarly)
                val outputEarlyPixels = framePixels(outputEarly)
                val baselineLaterPixels = framePixels(baselineLater)
                val outputLaterPixels = framePixels(outputLater)
                assertStickerPixels(
                    "$label early sample frame", outputEarly, baselineEarly, bounds,
                    outputEarlyPixels, baselineEarlyPixels
                )
                assertStickerPixels(
                    "$label later sample frame", outputLater, baselineLater, bounds,
                    outputLaterPixels, baselineLaterPixels
                )
                assertBackgroundUnchangedOutsideSticker(
                    "$label early sample frame", outputEarly, baselineEarly, bounds,
                    outputEarlyPixels, baselineEarlyPixels
                )
                assertBackgroundUnchangedOutsideSticker(
                    "$label later animation frame", outputLater, baselineLater, bounds,
                    outputLaterPixels, baselineLaterPixels
                )
                assertAnimationAdvances(
                    label, outputEarly, outputLater,
                    outputEarlyPixels, baselineEarlyPixels, outputLaterPixels, baselineLaterPixels, bounds
                )
            } finally {
                baselineEarly.recycle()
                outputEarly.recycle()
                baselineLater.recycle()
                outputLater.recycle()
            }
        }

        try {
            instrumentation.context.assets.open("export/cfr_30fps_audio_fixture.mp4").use { input ->
                source.outputStream().use { output -> input.copyTo(output) }
            }
            val sourceTrack = readVideoTrack(source)
            val sourceAnchorsUs = sourceTrack.presentationTimesUs.filter { it < 1_800_000L }
            assertTrue("Source must provide retained anchors before the planned 1.8s end", sourceAnchorsUs.isNotEmpty())
            installTestPackAsset()
            assertTrue("The test pack must be registered as installed", PackManager.isDownloaded(context, packId))
            assertTrue("The downloaded sticker URI must resolve inside its installed pack",
                StickerAssetResolver.downloadedFile(context, assetPath)?.isFile == true)

            baselineEngine.startExport(Uri.fromFile(source).toString(), baselineConfig, baselineName)
            val baselineResult = awaitTerminalState(baselineEngine)
            assertNull("Sticker baseline export must complete: ${baselineResult.error}", baselineResult.error)
            val baselineExportFile = File(
                requireNotNull(Uri.parse(requireNotNull(baselineResult.outputUri)).path)
            )
            assertEquals("Sticker baseline must use its requested output path",
                File(outputDir, baselineName).canonicalPath, baselineExportFile.canonicalPath)
            baselineOutput = baselineExportFile

            stickerEngine.startExport(
                Uri.fromFile(source).toString(),
                baselineConfig.copy(stickers = listOf(sticker)),
                selectedName
            )
            val selectedResult = awaitTerminalState(stickerEngine)
            assertNull("Selected export with downloaded sticker must complete: ${selectedResult.error}", selectedResult.error)
            val stickerExportFile = File(
                requireNotNull(Uri.parse(requireNotNull(selectedResult.outputUri)).path)
            )
            assertEquals("Selected sticker export must use its requested output path",
                File(outputDir, selectedName).canonicalPath, stickerExportFile.canonicalPath)
            selectedOutput = stickerExportFile

            val sourceDurationMs = sourceTrack.videoTrackDurationUs / 1_000L
            val projectClip = MediaClip(
                id = "sticker-project-$id",
                name = "Downloaded sticker project fixture",
                uri = Uri.fromFile(source).toString(),
                durationMs = sourceDurationMs,
                trimEndMs = 1_800L,
                stickers = listOf(sticker)
            )
            val projectConfig = ExportEngine.ExportConfig(resolution = "720p", fps = 30, aspectRatio = "16:9")
            suspend fun exportProjectPass(
                clips: List<MediaClip>,
                outputFileName: String,
                label: String
            ): File {
                // Each pass gets its own engine because terminal state is published before run cleanup finishes.
                val passEngine = ExportEngine(context)
                val destination = File(outputDir, outputFileName)
                var complete = false
                try {
                    passEngine.startProjectExport(clips, projectConfig, outputFileName)
                    val terminal = withTimeout(180_000L) {
                        passEngine.exportState.first { state ->
                            !state.isExporting && (state.outputUri != null || state.error != null)
                        }
                    }
                    assertNull("$label must complete: ${terminal.error}", terminal.error)
                    val output = File(requireNotNull(Uri.parse(requireNotNull(terminal.outputUri)).path))
                    assertEquals("$label output path", destination.canonicalPath, output.canonicalPath)
                    assertTrue("$label output must be non-empty", output.isFile && output.length() > 0L)
                    complete = true
                    return output
                } finally {
                    val safeToDelete = withContext(NonCancellable) { passEngine.cancelAndCheckSafeToDelete() }
                    if (!complete && safeToDelete) destination.delete()
                }
            }

            projectBaselineOutput = exportProjectPass(
                listOf(projectClip.copy(stickers = emptyList())),
                projectBaselineName,
                "Whole-project sticker baseline export"
            )
            projectOutput = exportProjectPass(
                listOf(projectClip),
                projectName,
                "Whole-project export with downloaded sticker"
            )

            val tracksByLabel = linkedMapOf(
                "baseline" to readVideoTrack(requireNotNull(baselineOutput)),
                "selected" to readVideoTrack(requireNotNull(selectedOutput)),
                "project-baseline" to readVideoTrack(requireNotNull(projectBaselineOutput)),
                "project-selected" to readVideoTrack(requireNotNull(projectOutput))
            )
            tracksByLabel.forEach { (label, track) ->
                println(
                    "sticker-retained-pts $label: count=${track.presentationTimesUs.size}, " +
                        "pts=${track.presentationTimesUs.joinToString()}, gaps=${track.presentationTimesUs.zipWithNext { a, b -> b - a }}, " +
                        "videoDurationUs=${track.videoTrackDurationUs}, movieDurationUs=${track.movieDurationUs}, " +
                        "audioDurationUs=${track.audioDurationUs}, trackDurations=${track.trackDurations}"
                )
            }
            val commonTimesUs = commonRetainedFrameTimesUs(
                referenceTimesUs = sourceAnchorsUs,
                encodedTimesByLabel = tracksByLabel.mapValues { it.value.presentationTimesUs }
            )
            println(
                "sticker-retained-pts source: count=${sourceAnchorsUs.size}, pts=${sourceAnchorsUs.joinToString()}; " +
                    "common: count=${commonTimesUs.size}, pts=${commonTimesUs.joinToString()}"
            )
            assertTrue(
                "Need at least two common retained source PTS candidates; source=${sourceAnchorsUs.joinToString()}, " +
                    "outputs=${tracksByLabel.mapValues { it.value.presentationTimesUs }}",
                commonTimesUs.size >= 2
            )
            tracksByLabel.forEach { (label, track) ->
                assertTrue(
                    "sticker $label video duration ${track.videoTrackDurationUs}us must remain within 150000us of 1800000us",
                    track.videoTrackDurationUs > 0L && abs(track.videoTrackDurationUs - 1_800_000L) <= 150_000L
                )
                assertOutputTiming(
                    label = "sticker $label export",
                    output = track,
                    expectedDurationUs = 1_800_000L,
                    expectedSourceAnchorsUs = sourceAnchorsUs,
                    requestedFps = 30
                )
            }
            selectVisibleAnimationSamples(commonTimesUs)

            val baselineFile = requireNotNull(baselineOutput)
            verifyExportFrames("Selected export", baselineFile, requireNotNull(selectedOutput))
            verifyExportFrames("Whole-project export", requireNotNull(projectBaselineOutput), requireNotNull(projectOutput))
        } finally {
            instrumentation.runOnMainSync {
                baselineEngine.release()
                stickerEngine.release()
            }
            source.delete()
            packDir.deleteRecursively()
            packArchive.delete()
            if (previousRegistry == null) registryFile.delete() else registryFile.writeText(previousRegistry)
            baselineOutput?.delete()
            selectedOutput?.delete()
            projectBaselineOutput?.delete()
            projectOutput?.delete()
            File(outputDir, baselineName).delete()
            File(outputDir, selectedName).delete()
            File(outputDir, projectBaselineName).delete()
            File(outputDir, projectName).delete()
        }
    }

    @Test
    fun commonRetainedFrameTimesRequireActualCrossFilePtsAgreement() {
        assertEquals(
            listOf(0L, 33_334L),
            commonRetainedFrameTimesUs(
                referenceTimesUs = listOf(0L, 33_334L, 66_667L),
                encodedTimesByLabel = mapOf(
                    "first" to listOf(0L, 33_333L, 66_666L),
                    "second" to listOf(1L, 33_334L, 100_000L)
                )
            )
        )
        assertEquals(
            emptyList<Long>(),
            commonRetainedFrameTimesUs(
                referenceTimesUs = listOf(0L),
                encodedTimesByLabel = mapOf("first" to listOf(0L), "second" to listOf(2_000L))
            )
        )
        assertEquals(
            emptyList<Long>(),
            commonRetainedFrameTimesUs(
                referenceTimesUs = listOf(100_000L),
                encodedTimesByLabel = mapOf("first" to listOf(99_200L), "second" to listOf(100_800L))
            )
        )

        fun assertInvalid(reference: List<Long>, encoded: Map<String, List<Long>>, toleranceUs: Long = 1L) {
            var rejected = false
            try {
                commonRetainedFrameTimesUs(reference, encoded, toleranceUs)
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertTrue("Invalid timestamp input must fail closed", rejected)
        }
        assertInvalid(emptyList(), mapOf("output" to listOf(0L)))
        assertInvalid(listOf(-1L), mapOf("output" to listOf(0L)))
        assertInvalid(listOf(1L, 1L), mapOf("output" to listOf(1L)))
        assertInvalid(listOf(2L, 1L), mapOf("output" to listOf(1L)))
        assertInvalid(listOf(0L), emptyMap())
        assertInvalid(listOf(0L), mapOf("output" to emptyList()))
        assertInvalid(listOf(0L), mapOf("output" to listOf(-1L)))
        assertInvalid(listOf(0L), mapOf("output" to listOf(0L, 0L)))
        assertInvalid(listOf(0L), mapOf("output" to listOf(1L, 0L)))
        assertInvalid(listOf(0L), mapOf("output" to listOf(0L)), toleranceUs = 0L)
        assertInvalid(listOf(0L), mapOf("output" to listOf(0L)), toleranceUs = 1_001L)
    }

    @Test
    fun bundledOpenH264FallbackGeneratesVerified60FpsAndPreservesAacAudio() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val id = UUID.randomUUID().toString()
        val source = File(context.cacheDir, "cfr-fallback-source-$id.mp4")
        var candidate: File? = null
        try {
            instrumentation.context.assets.open("export/cfr_30fps_audio_fixture.mp4").use { input ->
                source.outputStream().use { output -> input.copyTo(output) }
            }
            val sourceTrack = readVideoTrack(source)
            assertTrue("Fallback fixture must contain AAC audio", hasTrack(source, "audio/"))
            val sourceDurationMs = sourceTrack.videoTrackDurationUs / 1_000L
            val normalized = ExportCfr60FpsNormalizer.transcode(source, targetBitrate = 6_000_000)
            candidate = normalized

            val audit = inspectEncodedVideo(
                context = context,
                file = normalized,
                requestedFps = 60,
                transformerDurationMs = sourceDurationMs
            )
            assertNull("CFR fallback output must pass the strict cadence audit: ${audit.warning}", audit.warning)
            val candidateTrack = readVideoTrack(normalized)
            assertActual60FpsCadence("direct CFR fallback", candidateTrack, sourceDurationMs * 1_000L)
            assertTrue("CFR fallback must retain AAC audio", candidateTrack.audioDurationUs != null)
            assertTrue(
                "CFR fallback audio duration must remain synchronized",
                abs(requireNotNull(candidateTrack.audioDurationUs) - sourceTrack.audioDurationUs!!) <= 150_000L
            )
            val audioTracks = ExportCfr60FpsNormalizer.verifyAudioPreserved(
                sourceFile = source,
                candidateFile = normalized,
                sourceVideoDurationMs = sourceDurationMs,
                candidateVideoDurationMs = requireNotNull(audit.outputDurationMs)
            )
            assertTrue("CFR fallback must preserve at least one audio track", audioTracks > 0)
        } finally {
            candidate?.delete()
            source.delete()
        }
    }

    @Test
    fun selectedAndProjectExportsMeetVerified60FpsCadence() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val engine = ExportEngine(context)
        val id = UUID.randomUUID().toString()
        val source = File(context.cacheDir, "cfr-60fps-source-$id.mp4")
        val selectedName = "cfr-60fps-selected-$id.mp4"
        val projectName = "cfr-60fps-project-$id.mp4"
        val outputDir = File(context.getExternalFilesDir(null), "ApexStudio_Exports")
        val clipDurationMs = 1_800L
        var selectedOutput: File? = null
        var projectOutput: File? = null

        try {
            instrumentation.context.assets.open("export/cfr_30fps_audio_fixture.mp4").use { input ->
                source.outputStream().use { output -> input.copyTo(output) }
            }
            val sourceTrack = readVideoTrack(source)
            assertEquals("Fixture must contain 60 constant-rate source samples", 60, sourceTrack.presentationTimesUs.size)
            assertTrue("Fixture must be 30 fps", sourceTrack.maxGapUs in 33_000L..34_000L)
            assertTrue("Fixture must retain its audio track", hasTrack(source, "audio/"))

            engine.startExport(
                Uri.fromFile(source).toString(),
                ExportEngine.ExportConfig(
                    resolution = "720p",
                    fps = 60,
                    aspectRatio = "16:9",
                    trimEndMs = clipDurationMs
                ),
                selectedName
            )
            val selectedResult = awaitTerminalState(engine)
            assertNull("Selected 60 fps export must complete: ${selectedResult.error}", selectedResult.error)
            selectedOutput = File(requireNotNull(Uri.parse(selectedResult.outputUri).path))
            val selectedTrack = readVideoTrack(selectedOutput!!)
            assertOutputTiming(
                label = "selected 60 fps export",
                output = selectedTrack,
                expectedDurationUs = clipDurationMs * 1_000L,
                expectedSourceAnchorsUs = emptyList(),
                requestedFps = 60
            )
            assertActual60FpsCadence("selected export", selectedTrack, clipDurationMs * 1_000L)
            assertTrue("Selected 60 fps export must retain its audio track", selectedTrack.audioDurationUs != null)
            assertTrue(
                "Selected 60 fps audio must remain duration-synchronized",
                abs(requireNotNull(selectedTrack.audioDurationUs) - clipDurationMs * 1_000L) <= 150_000L
            )

            val sourceDurationMs = sourceTrack.videoTrackDurationUs / 1_000L
            val clips = (0..1).map { index ->
                MediaClip(
                    id = "cfr-60fps-$id-$index",
                    name = "30 fps CFR fixture $index",
                    uri = Uri.fromFile(source).toString(),
                    durationMs = sourceDurationMs,
                    trimEndMs = clipDurationMs
                )
            }
            val plan = ProjectCompositionTimeline.build(clips)
            assertEquals("Two 1.8-second clips must form a 3.6-second project", 3_600L, plan.durationMs)

            engine.startProjectExport(
                clips = clips,
                config = ExportEngine.ExportConfig(resolution = "720p", fps = 60, aspectRatio = "16:9"),
                outputFileName = projectName
            )
            val projectResult = awaitTerminalState(engine)
            assertNull("Whole-project 60 fps export must complete: ${projectResult.error}", projectResult.error)
            projectOutput = File(requireNotNull(Uri.parse(projectResult.outputUri).path))
            val projectTrack = readVideoTrack(projectOutput!!)
            assertOutputTiming(
                label = "whole-project 60 fps export",
                output = projectTrack,
                expectedDurationUs = plan.durationMs * 1_000L,
                expectedSourceAnchorsUs = emptyList(),
                requestedFps = 60
            )
            assertActual60FpsCadence("whole-project export", projectTrack, plan.durationMs * 1_000L)
            assertTrue("Whole-project 60 fps export must retain its audio track", projectTrack.audioDurationUs != null)
            assertTrue(
                "Whole-project 60 fps audio must remain duration-synchronized",
                abs(requireNotNull(projectTrack.audioDurationUs) - plan.durationMs * 1_000L) <= 150_000L
            )
        } finally {
            instrumentation.runOnMainSync { engine.release() }
            source.delete()
            selectedOutput?.delete()
            projectOutput?.delete()
            File(outputDir, selectedName).delete()
            File(outputDir, projectName).delete()
            File(outputDir, selectedName.removeSuffix(".mp4") + "_music.mp4").delete()
            File(outputDir, projectName.removeSuffix(".mp4") + "_music.mp4").delete()
        }
    }

    @Test
    fun downloadedAnimatedFxSurvivesEditorReopenAndChangesBothEncodedExportRoutes() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val id = UUID.randomUUID().toString()
        // Keep the existing encoded-sticker regression's RGB/count floors unchanged.
        // On this fixed 720p fixture, 80 rejects codec noise, 500 rejects a no-op FX,
        // and 100 requires observable motion after subtracting source-frame changes.
        val visibleRgbDistance = 80
        val minChangedPixels = 500
        val minAnimatedPixels = 100
        // First-order identity sanity only; animation proof is the separate baseline-adjusted metric.
        val minDistinctFxFramePixels = 100
        val desiredSampleTimesUs = 400_000L to 1_400_000L
        val saveTimeoutMs = 20_000L
        val savePollIntervalMs = 50L
        val maxSavePolls = (saveTimeoutMs / savePollIntervalMs).toInt()
        val root = File(instrumentation.targetContext.cacheDir, "fx-roundtrip-$id").apply { mkdirs() }
        val context = object : ContextWrapper(instrumentation.targetContext) {
            // VM/engine retain applicationContext; returning the base Application would
            // escape these isolated paths. Assets/resolver/system services still delegate
            // to the real context through ContextWrapper; no Application cast is required.
            override fun getApplicationContext(): android.content.Context = this
            override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
            override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
            override fun getExternalFilesDir(type: String?): File =
                File(root, "external").let { base ->
                    (if (type == null) base else File(base, type)).apply { mkdirs() }
                }
        }
        val source = File(context.cacheDir, "source.mp4")
        val packId = "roundtrip-fx-$id"
        val effectId = "dl_${packId}_tidal_ripple"
        val outputDir = File(context.getExternalFilesDir(null), "ApexStudio_Exports")
        val names = listOf("selected-baseline", "selected-fx", "project-baseline", "project-fx")
            .map { "$it-$id.mp4" }
        val engines = List(4) { ExportEngine(context) }
        val outputs = arrayOfNulls<File>(4)
        val repository = ProjectRepository(context)
        val store = ViewModelStore()
        val editorJobs = mutableListOf<Job>()

        fun openEditor(): EditorViewModel {
            lateinit var editor: EditorViewModel
            instrumentation.runOnMainSync {
                editor = EditorViewModel(context = context, projectId = id)
                store.put("fx-editor", editor)
                editor.viewModelScope.coroutineContext[Job]?.let(editorJobs::add)
            }
            return editor
        }

        suspend fun closeEditor() {
            instrumentation.runOnMainSync { store.clear() }
            withTimeout(20_000L) { editorJobs.forEach { it.join() } }
            editorJobs.clear()
        }

        suspend fun awaitSavedFx(): Project {
            val expected = ProjectFxSettings(effectId, 0.73f, 1.8f)
            var saved: Project? = null
            return withTimeoutOrNull(saveTimeoutMs) {
                repeat(maxSavePolls) { attempt ->
                    saved = repository.findById(id)
                    if (saved?.manualFxOverride == expected) return@withTimeoutOrNull requireNotNull(saved)
                    if (attempt < maxSavePolls - 1) delay(savePollIntervalMs)
                }
                null
            } ?: error("FX save did not reach $expected within ${saveTimeoutMs}ms/$maxSavePolls polls; last=${saved?.manualFxOverride}")
        }

        fun frameAt(file: File, timeUs: Long): Bitmap {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(file.absolutePath)
                requireNotNull(retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST))
            } finally { retriever.release() }
        }

        fun pixels(bitmap: Bitmap): IntArray = IntArray(bitmap.width * bitmap.height).also {
            bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        }

        fun changedPixels(first: IntArray, second: IntArray): Int = first.indices.count { index ->
            val a = first[index]; val b = second[index]
            abs(Color.red(a) - Color.red(b)) + abs(Color.green(a) - Color.green(b)) +
                abs(Color.blue(a) - Color.blue(b)) >= visibleRgbDistance
        }

        fun animationChangedPixels(
            early: IntArray, earlyBaseline: IntArray, later: IntArray, laterBaseline: IntArray
        ): Int = early.indices.count { index ->
            val a = early[index]; val ab = earlyBaseline[index]
            val b = later[index]; val bb = laterBaseline[index]
            abs((Color.red(a) - Color.red(ab)) - (Color.red(b) - Color.red(bb))) +
                abs((Color.green(a) - Color.green(ab)) - (Color.green(b) - Color.green(bb))) +
                abs((Color.blue(a) - Color.blue(ab)) - (Color.blue(b) - Color.blue(bb))) >= visibleRgbDistance
        }

        suspend fun exportPass(index: Int, config: ExportEngine.ExportConfig, clips: List<MediaClip>?): File {
            if (clips == null) engines[index].startExport(Uri.fromFile(source).toString(), config, names[index])
            else engines[index].startProjectExport(clips, config, names[index])
            val terminal = awaitTerminalState(engines[index])
            assertNull("${names[index]} must complete: ${terminal.error}", terminal.error)
            assertFalse("${names[index]} must reach a terminal state", terminal.isExporting)
            return File(requireNotNull(Uri.parse(requireNotNull(terminal.outputUri)).path)).also {
                assertEquals(File(outputDir, names[index]).canonicalPath, it.canonicalPath)
                assertTrue("${names[index]} must contain encoded bytes", it.isFile && it.length() > 0L)
            }
        }

        try {
            instrumentation.context.assets.open("export/cfr_30fps_audio_fixture.mp4").use { input ->
                source.outputStream().use { output -> input.copyTo(output) }
            }
            val sourceTrack = readVideoTrack(source)
            assertTrue("Fixture must contain the retained 1.8-second span", sourceTrack.videoTrackDurationUs >= 1_800_000L)
            val bytes = instrumentation.context.assets.open("fx_profiles.json").use { it.readBytes() }
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            File(PackManager.packDir(context, packId), "fx_profiles.json").apply {
                parentFile?.mkdirs()
                writeBytes(bytes)
            }
            val manifest = PackManifestRepo.PackManifestFile(packs = listOf(
                PackManifestRepo.RemotePack(
                    id = packId, name = "Offline FX round-trip fixture", category = "effects", version = 1,
                    sizeBytes = bytes.size.toLong(), url = "https://example.invalid/$packId/fx_profiles.json",
                    sha256 = hash, entry = "fx_profiles.json", itemCount = 30,
                    payloadType = DownloadedEffectRepository.PAYLOAD_TYPE, entrySha256 = hash
                )
            ))
            PackManager.recordInstalledPack(context, packId, 1, hash)
            assertEquals(30, DownloadedEffectRepository.refreshInstalled(context, manifest))
            val effect = requireNotNull(DownloadedEffectRepository.findVerified(effectId))
            assertEquals(effectId, effect.id)
            assertEquals("RIPPLE", effect.family)
            assertTrue("Published tidal_ripple must be animated", effect.isAnimated)

            val clip = MediaClip(
                id = "fx-clip-$id", name = "FX fixture", uri = Uri.fromFile(source).toString(),
                durationMs = sourceTrack.videoTrackDurationUs / 1_000L, trimEndMs = 1_800L
            )
            repository.saveProject(Project(
                id = id, name = "FX round-trip", durationMs = 1_800L, clips = listOf(clip),
                resolution = "720p", fps = 30, videoAspectRatio = 16f / 9f
            ))
            val first = openEditor()
            withTimeout(20_000L) { first.state.first { it.project?.id == id && it.project?.clips?.singleOrNull()?.id == clip.id } }
            instrumentation.runOnMainSync {
                first.selectFx(effect.id, intensity = 0.73f)
                first.setFxSpeed(1.8f)
                first.flushProject()
            }
            assertEquals(ProjectFxSettings(effectId, 0.73f, 1.8f), awaitSavedFx().manualFxOverride)
            closeEditor()

            val reopened = openEditor()
            val restored = withTimeout(20_000L) {
                reopened.state.first {
                    it.project?.id == id && it.activeFxId == effectId &&
                        it.fxIntensity == 0.73f && it.fxSpeed == 1.8f
                }
            }
            assertEquals(ProjectFxSettings(effectId, 0.73f, 1.8f), restored.project?.manualFxOverride)
            assertEquals(effect, DownloadedEffectRepository.findVerified(restored.activeFxId))
            val clips = requireNotNull(restored.project).clips
            assertEquals(1, clips.size)
            val plan = ProjectCompositionTimeline.build(clips)
            assertEquals(1_800L, plan.durationMs)
            val config = buildProjectExportConfig(
                restored, AudioStudioState(),
                ExportSettings(resolution = "720p", frameRate = 30, aspectRatio = "16:9", bitrateMbps = 0)
            )
            assertNull(config.fxPreset)
            assertEquals(effectId, config.downloadedFxEffect?.id)
            assertEquals(0.73f, config.fxIntensity, 0f)
            assertEquals(1.8f, config.fxSpeed, 0f)
            closeEditor()

            val baseline = config.copy(fxPreset = null, downloadedFxEffect = null, fxIntensity = 0f, fxSpeed = 1f)
            // One engine per pass: terminal publication can precede a previous writer's cleanup.
            outputs[0] = exportPass(0, baseline.copy(trimEndMs = 1_800L), null)
            outputs[1] = exportPass(1, config.copy(trimEndMs = 1_800L), null)
            outputs[2] = exportPass(2, baseline, clips)
            outputs[3] = exportPass(3, config, clips)

            val tracksByName = names.zip(outputs.map { readVideoTrack(requireNotNull(it)) }).toMap()
            val sourceAnchorsUs = sourceTrack.presentationTimesUs.filter { it < plan.durationMs * 1_000L }
            val commonTimesUs = commonRetainedFrameTimesUs(sourceAnchorsUs,
                tracksByName.mapValues { it.value.presentationTimesUs })
            println("fx-retained-pts outputs=${tracksByName.mapValues { it.value.presentationTimesUs }}; common=$commonTimesUs")
            fun retainedAnchorNear(targetUs: Long): Long {
                val anchorUs = commonTimesUs.minByOrNull { abs(it - targetUs) }
                    ?: error("FX exports have no common retained source PTS; outputs=$tracksByName")
                // One source-frame window chooses a phase, not a cross-file matching tolerance.
                // Every chosen moment is already independently retained by all four exports.
                val sourceFrameWindowUs = 1_000_000L / 30L + 1_000L
                assertTrue("No common FX anchor near ${targetUs}us; nearest=${anchorUs}us; common=$commonTimesUs",
                    abs(anchorUs - targetUs) <= sourceFrameWindowUs)
                return anchorUs
            }
            val sampleTimesUs = retainedAnchorNear(desiredSampleTimesUs.first) to retainedAnchorNear(desiredSampleTimesUs.second)
            assertTrue("FX common sample anchors must be ordered", sampleTimesUs.first < sampleTimesUs.second)
            tracksByName.forEach { (name, track) ->
                assertOutputTiming(name, track, plan.durationMs * 1_000L, sourceAnchorsUs, 30)
                assertTrue("$name video duration ${track.videoTrackDurationUs}us differs from the planned ${plan.durationMs}ms",
                    track.videoTrackDurationUs > 0L && abs(track.videoTrackDurationUs - plan.durationMs * 1_000L) <= 150_000L)
            }

            fun verify(label: String, baselineIndex: Int, fxIndex: Int) {
                val baselineFile = requireNotNull(outputs[baselineIndex])
                val fxFile = requireNotNull(outputs[fxIndex])
                assertTrue("$label baseline must retain AAC audio", hasTrack(baselineFile, "audio/mp4a-latm"))
                assertTrue("$label FX must retain AAC audio", hasTrack(fxFile, "audio/mp4a-latm"))
                val frames = mutableListOf<Bitmap>()
                try {
                    sampleTimesUs.toList().forEach { time ->
                        frames += frameAt(baselineFile, time)
                        frames += frameAt(fxFile, time)
                    }
                    frames.forEach {
                        assertEquals("$label frame width", 1_280, it.width)
                        assertEquals("$label frame height", 720, it.height)
                    }
                    val baselineEarly = pixels(frames[0]); val fxEarly = pixels(frames[1])
                    val baselineLater = pixels(frames[2]); val fxLater = pixels(frames[3])
                    val earlyChanged = changedPixels(baselineEarly, fxEarly)
                    val laterChanged = changedPixels(baselineLater, fxLater)
                    val animatedChanged = animationChangedPixels(fxEarly, baselineEarly, fxLater, baselineLater)
                    val fxFramesChanged = changedPixels(fxEarly, fxLater)
                    // A static baseline is valid. This rejects duplicate decoded FX pictures;
                    // only the subsequent baseline-adjusted assertion is animation evidence.
                    assertTrue("$label FX samples must decode distinct pictures; changed=$fxFramesChanged", fxFramesChanged >= minDistinctFxFramePixels)
                    assertTrue("$label early FX changed $earlyChanged pixels; need >=$minChangedPixels", earlyChanged >= minChangedPixels)
                    assertTrue("$label later FX changed $laterChanged pixels; need >=$minChangedPixels", laterChanged >= minChangedPixels)
                    assertTrue("$label animated delta changed $animatedChanged pixels; need >=$minAnimatedPixels", animatedChanged >= minAnimatedPixels)
                } finally { frames.forEach { it.recycle() } }
            }
            verify("selected route", 0, 1)
            verify("whole-project route", 2, 3)
        } finally {
            var safeToDeleteOutputs = true
            withContext(NonCancellable) {
                try {
                    for (engine in engines) {
                        val safeToDelete = engine.cancelAndCheckSafeToDelete()
                        safeToDeleteOutputs = safeToDeleteOutputs && safeToDelete
                    }
                } finally {
                    try { closeEditor() } finally {
                        instrumentation.runOnMainSync { engines.forEach { it.release() } }
                        try { DownloadedEffectRepository.refreshInstalled(context, PackManifestRepo.PackManifestFile()) }
                        finally {
                            if (safeToDeleteOutputs) {
                                names.forEach { name ->
                                    File(outputDir, name).delete()
                                    File(outputDir, name.removeSuffix(".mp4") + "_music.mp4").delete()
                                }
                                root.deleteRecursively()
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun shortsTemplatePersistsAndEncodesTextAndImportedMusicThroughWholeProjectExport() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val id = "shorts-encoded-roundtrip-${UUID.randomUUID()}"
        // Same fixed visible-delta floors as the existing encoded regression; never tune down for codecs.
        val visibleRgbDistance = 80
        val minCaptionChangedPixels = 500
        val root = File(instrumentation.targetContext.cacheDir, id).apply { mkdirs() }
        val context = object : ContextWrapper(instrumentation.targetContext) {
            // VM/engine retain applicationContext; returning the base Application would
            // escape these isolated paths. Assets/resolver/system services still delegate
            // to the real context through ContextWrapper; no Application cast is required.
            override fun getApplicationContext(): android.content.Context = this
            override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
            override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
            override fun getExternalFilesDir(type: String?): File =
                File(root, "external").let { base -> (if (type == null) base else File(base, type)).apply { mkdirs() } }
        }
        val source = File(context.cacheDir, "shorts-source-$id.mp4")
        val music = File(context.filesDir, "shorts_music_fixture-$id.mp4")
        val videoOnly = File(context.cacheDir, "shorts-video-only-$id.mp4")
        val outputDir = File(context.getExternalFilesDir(null), "ApexStudio_Exports")
        val names = listOf("baseline", "caption-a-only", "caption-b-only", "full")
            .map { "shorts-$it-$id.mp4" }
        val engines = List(names.size) { ExportEngine(context) }
        val outputs = arrayOfNulls<File>(names.size)
        val repository = ProjectRepository(context)
        val store = ViewModelStore()
        val editorJobs = mutableListOf<Job>()
        val template = requireNotNull(ShortsTemplateCatalog.byId("shorts_travel_adventure"))

        fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

        fun remuxVideoOnly(input: File, output: File) {
            val extractor = MediaExtractor()
            var muxer: MediaMuxer? = null
            var stopFailure: Throwable? = null
            try {
                extractor.setDataSource(input.absolutePath)
                val videoIndex = (0 until extractor.trackCount).firstOrNull { index ->
                    extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("video/")
                } ?: error("Fixture has no video track")
                val format = extractor.getTrackFormat(videoIndex)
                extractor.selectTrack(videoIndex)
                muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val muxTrack = muxer.addTrack(format)
                muxer.start()
                val buffer = ByteBuffer.allocate(2 * 1024 * 1024)
                val info = MediaCodec.BufferInfo()
                while (extractor.sampleTrackIndex >= 0) {
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    info.set(0, size, extractor.sampleTime, extractor.sampleFlags)
                    muxer.writeSampleData(muxTrack, buffer, info)
                    if (!extractor.advance()) break
                }
            } finally {
                try { muxer?.stop() } catch (failure: Throwable) { stopFailure = failure }
                runCatching { muxer?.release() }
                extractor.release()
            }
            stopFailure?.let { throw AssertionError("Video-only remux muxer stop failed", it) }
            assertTrue("Video-only remux must be non-empty after muxer stop", output.isFile && output.length() > 0L)
        }

        suspend fun closeEditor() {
            instrumentation.runOnMainSync { store.clear() }
            withTimeout(20_000L) { editorJobs.forEach { it.join() } }
            editorJobs.clear()
        }
        fun openEditor(): EditorViewModel {
            lateinit var editor: EditorViewModel
            instrumentation.runOnMainSync {
                editor = EditorViewModel(context = context, projectId = id)
                store.put("shorts-editor", editor)
                editor.viewModelScope.coroutineContext[Job]?.let(editorJobs::add)
            }
            return editor
        }
        fun frame(file: File, timeUs: Long): Bitmap = MediaMetadataRetriever().let { retriever ->
            try {
                retriever.setDataSource(file.absolutePath)
                requireNotNull(retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST))
            } finally { retriever.release() }
        }
        fun pixels(bitmap: Bitmap) = IntArray(bitmap.width * bitmap.height).also {
            bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        }
        fun changed(a: IntArray, b: IntArray): Int = a.indices.count { i ->
            abs(Color.red(a[i]) - Color.red(b[i])) + abs(Color.green(a[i]) - Color.green(b[i])) +
                abs(Color.blue(a[i]) - Color.blue(b[i])) >= visibleRgbDistance
        }
        suspend fun export(index: Int, config: ExportEngine.ExportConfig, clips: List<MediaClip>): File {
            engines[index].startProjectExport(clips, config, names[index])
            val terminal = awaitTerminalState(engines[index])
            assertNull("${names[index]} must complete: ${terminal.error}", terminal.error)
            assertFalse("${names[index]} must be terminal", terminal.isExporting)
            return File(requireNotNull(Uri.parse(requireNotNull(terminal.outputUri)).path)).also {
                assertTrue(it.isFile && it.length() > 0L)
                assertEquals(File(outputDir, names[index]).canonicalPath, it.canonicalPath)
            }
        }

        try {
            instrumentation.context.assets.open("export/cfr_30fps_audio_fixture.mp4").use { input ->
                source.outputStream().use { output -> input.copyTo(output) }
            }
            assertTrue("Fixture must contain AAC", hasTrack(source, "audio/mp4a-latm"))
            remuxVideoOnly(source, videoOnly)
            assertFalse("Video-only source must not retain AAC", hasTrack(videoOnly, "audio/"))
            val sourceTrack = readVideoTrack(videoOnly)
            assertTrue("Fixture must retain at least 1.8 seconds", sourceTrack.videoTrackDurationUs >= 1_800_000L)
            FilterManifest.initialize(context)

            val catalogAsset = ShortsMusicRepository.loadCatalog(context).first { it.id == template.musicAssetId }
            source.copyTo(music, overwrite = true)
            // Expected metadata comes from the original fixture, independently of the owned copy.
            val musicAsset = catalogAsset.copy(
                fileName = music.name,
                bytes = source.length(),
                sha256 = sha256(source),
                durationMs = requireNotNull(readVideoTrack(music).audioDurationUs) / 1_000L,
                attribution = "${catalogAsset.artist} — ${catalogAsset.trackTitle} (offline AAC fixture)",
                changes = "Project mix trims this offline AAC fixture to the retained 1.8 s video duration."
            )
            assertEquals(musicAsset.bytes, music.length())
            assertEquals(musicAsset.sha256, sha256(music))
            assertTrue("Music fixture must contain AAC", hasTrack(music, "audio/mp4a-latm"))

            val applied = ShortsTemplateProjectFactory.apply(
                Project(id, "Shorts encoded round-trip", 1_800L, resolution = "720p", fps = 30,
                    clips = listOf(MediaClip("clip-$id", "video-only.mp4", Uri.fromFile(videoOnly).toString(),
                        sourceTrack.videoTrackDurationUs / 1_000L, trimEndMs = 1_800L))),
                template, musicAsset, music
            ).also { ready ->
                assertEquals(9f / 16f, ready.videoAspectRatio!!, 0f)
                assertEquals(2, ready.clips.single().textOverlays.size)
                assertEquals(1, ready.audioTracks.size)
                assertEquals(template.visualPresetId, ready.lastTransmissionTemplateId)
                withContext(Dispatchers.IO) { repository.saveProject(ready) }
            }

            val first = openEditor()
            val restored = withTimeout(30_000L) {
                first.state.first { state ->
                    val project = state.project
                    project?.id == id && project.clips.size == 1 && project.clips.single().textOverlays.size == 2 &&
                        project.audioTracks.size == 1 && project.lastTransmissionTemplateId == template.visualPresetId &&
                        state.activeFilterId == template.filterId && state.activeFxId == template.fxPresetId
                }
            }
            val restoredProject = requireNotNull(restored.project)
            assertEquals(9f / 16f, requireNotNull(restoredProject.videoAspectRatio), 0f)
            assertEquals(music.canonicalPath, File(URI(restoredProject.audioTracks.single().uri)).canonicalPath)
            assertEquals(musicAsset.sha256, sha256(File(URI(restoredProject.audioTracks.single().uri))))
            closeEditor()
            val reopened = openEditor()
            val exact = withTimeout(30_000L) {
                reopened.state.first { state ->
                    val project = state.project
                    project?.id == id && project.clips.size == 1 && project.clips.single().textOverlays.size == 2 &&
                        project.audioTracks.singleOrNull()?.attributionText == musicAsset.creditText &&
                        project.lastTransmissionTemplateId == template.visualPresetId &&
                        state.activeFilterId == template.filterId && state.activeFxId == template.fxPresetId
                }
            }
            val exactProject = requireNotNull(exact.project)
            assertEquals(template.visualPresetId, exactProject.lastTransmissionTemplateId)
            assertEquals(2, exactProject.clips.single().textOverlays.size)
            assertEquals(template.filterId, exact.activeFilterId)
            assertEquals(template.fxPresetId, exact.activeFxId)

            val lut = requireNotNull(LutFilterEngine(context).manifest.presetById(template.filterId))
            val settings = ExportSettings(resolution = "720p", frameRate = 30, aspectRatio = "9:16", bitrateMbps = 0)
            assertEquals("Every caption field (text/font/animation/timing/layout) must survive reopen",
                applied.clips.single().textOverlays, exactProject.clips.single().textOverlays)
            assertEquals(music.canonicalPath, File(URI(exactProject.audioTracks.single().uri)).canonicalPath)
            assertEquals(musicAsset.sha256, sha256(File(URI(exactProject.audioTracks.single().uri))))
            assertEquals(applied.audioTracks.single().copy(uri = exactProject.audioTracks.single().uri), exactProject.audioTracks.single())
            val fullClips = exactProject.clips
            val fullConfig = buildProjectExportConfig(exact, AudioStudioState(), settings, lut)
            assertEquals("9:16", settings.aspectRatio)
            assertEquals(template.filterId, fullConfig.filterPreset?.id)
            assertEquals(template.fxPresetId, fullConfig.fxPreset?.id)
            assertEquals(1, fullConfig.musicMixTracks.size)
            val baselineClips = fullClips.map { it.copy(textOverlays = emptyList()) }
            val baselineState = exact.copy(project = exactProject.copy(clips = baselineClips, audioTracks = emptyList()))
            val baselineConfig = buildProjectExportConfig(baselineState, AudioStudioState(), settings, lut)
            assertEquals(fullConfig.filterPreset?.id, baselineConfig.filterPreset?.id)
            assertEquals(fullConfig.fxPreset?.id, baselineConfig.fxPreset?.id)
            assertTrue("Baseline must have no music mix tracks", baselineConfig.musicMixTracks.isEmpty())
            val overlays = fullClips.single().textOverlays
            assertEquals("Factory must materialize title and subtitle overlays", 2, overlays.size)
            val partialClips = overlays.map { overlay -> listOf(fullClips.single().copy(textOverlays = listOf(overlay))) }
            val partialConfigs = partialClips.map { clips ->
                buildProjectExportConfig(exact.copy(project = exactProject.copy(clips = clips)), AudioStudioState(), settings, lut)
            }
            partialConfigs.forEach {
                assertEquals(fullConfig.filterPreset?.id, it.filterPreset?.id)
                assertEquals(fullConfig.fxPreset?.id, it.fxPreset?.id)
                assertEquals(fullConfig.musicMixTracks, it.musicMixTracks)
            }
            closeEditor()
            // Each partial keeps the same music/LUT/FX; only one caption is absent.
            // This proves both captions contribute, not merely that either caption rendered.
            outputs[0] = export(0, baselineConfig, baselineClips)
            outputs[1] = export(1, partialConfigs[0], partialClips[0])
            outputs[2] = export(2, partialConfigs[1], partialClips[1])
            outputs[3] = export(3, fullConfig, fullClips)
            val baseline = requireNotNull(outputs[0]); val full = requireNotNull(outputs[3])
            val tracksByName = names.zip(outputs.map { readVideoTrack(requireNotNull(it)) }).toMap()
            val plan = ProjectCompositionTimeline.build(fullClips)
            assertEquals(1_800L, plan.durationMs)
            val sourcePtsUs = sourceTrack.presentationTimesUs.filter { it < plan.durationMs * 1_000L }
            val outputPtsByName = tracksByName.mapValues { it.value.presentationTimesUs }
            val commonPtsUs = commonRetainedFrameTimesUs(sourcePtsUs, outputPtsByName)
            val desiredMidpointsUs = overlays.map { overlay ->
                require(overlay.endMs > overlay.startMs) { "Factory caption has no visible window" }
                overlay.startMs * 1_000L + (overlay.endMs - overlay.startMs) * 1_000L / 2L
            }
            val phaseWindowUs = 1_000_000L / 30L + 1_000L
            val chosenPhasePtsUs = overlays.mapIndexed { index, overlay ->
                val visibleCommonPtsUs = commonPtsUs.filter {
                    it >= overlay.startMs * 1_000L && it < overlay.endMs * 1_000L
                }
                val chosen = visibleCommonPtsUs.minByOrNull { abs(it - desiredMidpointsUs[index]) }
                    ?: error("No common retained source PTS inside ${overlay.id} visible window; " +
                        "window=${overlay.startMs * 1_000L}..${overlay.endMs * 1_000L}us; common=$commonPtsUs")
                assertTrue("${overlay.id} common phase PTS $chosen is too far from midpoint ${desiredMidpointsUs[index]}us; " +
                    "distance=${abs(chosen - desiredMidpointsUs[index])}us; window=${overlay.startMs * 1_000L}..${overlay.endMs * 1_000L}us",
                    abs(chosen - desiredMidpointsUs[index]) <= phaseWindowUs)
                chosen
            }
            assertEquals("Factory captions must provide distinct interior sample times", 2, chosenPhasePtsUs.distinct().size)
            println("shorts-retained-pts outputs=$outputPtsByName; common=$commonPtsUs; " +
                "desiredMidpointsUs=$desiredMidpointsUs; chosenPhasePtsUs=$chosenPhasePtsUs")
            tracksByName.forEach { (name, track) ->
                val normalizedRotation = ((track.rotationDegrees % 360) + 360) % 360
                val displayWidth = if (normalizedRotation == 90 || normalizedRotation == 270) track.videoHeight else track.videoWidth
                val displayHeight = if (normalizedRotation == 90 || normalizedRotation == 270) track.videoWidth else track.videoHeight
                assertEquals("$name encoded display width (coded=${track.videoWidth}x${track.videoHeight}, rotation=${track.rotationDegrees})",
                    720, displayWidth)
                assertEquals("$name encoded display height (coded=${track.videoWidth}x${track.videoHeight}, rotation=${track.rotationDegrees})",
                    1_280, displayHeight)
                assertOutputTiming(name, track, plan.durationMs * 1_000L, sourcePtsUs, 30)
                assertTrue("$name video duration ${track.videoTrackDurationUs}us differs from the planned ${plan.durationMs}ms",
                    track.videoTrackDurationUs > 0L && abs(track.videoTrackDurationUs - plan.durationMs * 1_000L) <= 150_000L)
            }
            val pcmWindowStart = 100 * PcmAudioExtractor.TARGET_RATE / 1_000
            val pcmWindowEnd = 500 * PcmAudioExtractor.TARGET_RATE / 1_000
            fun pcmWindowRms(samples: FloatArray): Double {
                val end = minOf(pcmWindowEnd, samples.size)
                if (end <= pcmWindowStart) return 0.0
                val sumSquares = (pcmWindowStart until end).sumOf { index ->
                    val sample = samples[index].toDouble()
                    sample * sample
                }
                return kotlin.math.sqrt(sumSquares / (end - pcmWindowStart))
            }
            val baselineHasAudioTrack = hasTrack(baseline, "audio/")
            val baselinePcm = PcmAudioExtractor.extractMono16k(context, Uri.fromFile(baseline))
            val baselineRms = if (baselineHasAudioTrack) {
                val samples = requireNotNull(baselinePcm) { "Baseline audio track must decode for silence verification" }
                assertTrue("Baseline audio track must cover the 100-500ms window", samples.size >= pcmWindowEnd)
                pcmWindowRms(samples)
            } else {
                assertNull("Audio-less baseline must not decode PCM", baselinePcm)
                0.0
            }
            assertTrue("Baseline without imported music must be silent; 100-500ms RMS=$baselineRms", baselineRms < 0.005)
            outputs.drop(1).forEachIndexed { index, output ->
                val mixed = requireNotNull(output)
                assertTrue("Imported fixture output $index must retain AAC", hasTrack(mixed, "audio/mp4a-latm"))
                val mixedPcm = requireNotNull(PcmAudioExtractor.extractMono16k(context, Uri.fromFile(mixed))) {
                    "Imported fixture output $index must contain decodable audio"
                }
                assertTrue("Imported fixture output $index must cover the 100-500ms window", mixedPcm.size >= pcmWindowEnd)
                val mixedRms = pcmWindowRms(mixedPcm)
                assertTrue("Imported 440Hz fixture output $index must be audible; 100-500ms RMS=$mixedRms", mixedRms > 0.01)
            }
            // Each caption is sampled only at its own common retained source phase; no output pixels
            // are observed while choosing anchors, and all three comparison files use the exact phase.
            overlays.forEachIndexed { overlayIndex, overlay ->
                val timeUs = chosenPhasePtsUs[overlayIndex]
                val withoutThisCaption = requireNotNull(outputs[if (overlayIndex == 0) 2 else 1])
                val frames = mutableListOf<Bitmap>()
                try {
                    frames += frame(baseline, timeUs)
                    frames += frame(full, timeUs)
                    frames += frame(withoutThisCaption, timeUs)
                    val retrievedFrameSize = frames.first().width to frames.first().height
                    frames.forEach { bitmap ->
                        assertEquals("Retrieved caption-comparison frames must use one consistent size",
                            retrievedFrameSize, bitmap.width to bitmap.height)
                    }
                    val fullPixels = pixels(frames[1])
                    val allCaptionsChanged = changed(pixels(frames[0]), fullPixels)
                    val thisCaptionChanged = changed(pixels(frames[2]), fullPixels)
                    assertTrue("Caption composition at ${timeUs}us changed $allCaptionsChanged pixels; need >=$minCaptionChangedPixels",
                        allCaptionsChanged >= minCaptionChangedPixels)
                    assertTrue("Caption ${overlay.id} at ${timeUs}us contributes $thisCaptionChanged pixels; need >=$minCaptionChangedPixels",
                        thisCaptionChanged >= minCaptionChangedPixels)
                } finally { frames.forEach { it.recycle() } }
            }
        } finally {
            var safeToDeleteOutputs = true
            withContext(NonCancellable) {
                try {
                    for (engine in engines) {
                        val safeToDelete = engine.cancelAndCheckSafeToDelete()
                        safeToDeleteOutputs = safeToDeleteOutputs && safeToDelete
                    }
                } finally {
                    try { closeEditor() } finally {
                        instrumentation.runOnMainSync { engines.forEach { it.release() } }
                        try { repository.deleteProject(id) } finally {
                            if (safeToDeleteOutputs) {
                                names.forEach { File(outputDir, it).delete(); File(outputDir, it.removeSuffix(".mp4") + "_music.mp4").delete() }
                                root.deleteRecursively()
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun downloadedLutPackInstallsAllFiltersShowsRealSamplesAndRoundTripsBothExportRoutes() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val id = UUID.randomUUID().toString()
        val root = File(instrumentation.targetContext.cacheDir, "lut-roundtrip-$id").apply { mkdirs() }
        val context = object : ContextWrapper(instrumentation.targetContext) {
            override fun getApplicationContext(): android.content.Context = this
            override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
            override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
            override fun getExternalFilesDir(type: String?): File {
                val base = File(root, "external")
                return (if (type == null) base else File(base, type)).apply { mkdirs() }
            }
        }
        val packId = "lut-filters-vol-01"
        val source = File(context.cacheDir, "source.mp4")
        val outputDir = File(context.getExternalFilesDir(null), "ApexStudio_Exports")
        val names = listOf("selected-base", "selected-lut", "project-base", "project-lut")
            .map { "$it-$id.mp4" }
        val engines = List(4) { ExportEngine(context) }
        val outputs = arrayOfNulls<File>(4)
        var httpFixture: LocalLutArchiveServer? = null
        var gallerySource: Bitmap? = null

        fun frameAt(file: File, timeUs: Long): Bitmap {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(file.absolutePath)
                requireNotNull(retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST))
            } finally {
                retriever.release()
            }
        }

        FilterManifest.resetForTesting()
        FilterThumbnailGenerator.invalidateDynamicCache()
        try {
            instrumentation.context.assets.open("export/cfr_30fps_audio_fixture.mp4").use { input ->
                source.outputStream().use { output -> input.copyTo(output) }
            }
            val archive = instrumentation.context.assets
                .open("downloadable-packs/lut-filters-vol-01.zip").use { it.readBytes() }
            val archiveSha256 = MessageDigest.getInstance("SHA-256").digest(archive)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            val server = LocalLutArchiveServer(archive)
            httpFixture = server
            val pack = PackManifestRepo.RemotePack(
                id = packId,
                name = "Verified 30 LUT test pack",
                category = "filters",
                version = 1,
                sizeBytes = archive.size.toLong(),
                url = server.url,
                sha256 = archiveSha256,
                entry = "manifest.json",
                itemCount = 30,
                payloadType = DownloadedLutFilterRepository.PAYLOAD_TYPE
            )
            val catalog = PackManifestRepo.PackManifestFile(packs = listOf(pack))

            assertEquals(
                "The production download/install path must validate the ZIP and register its filter pack",
                PackManager.PackState.Downloaded,
                PackManager.downloadAndInstall(context, pack, catalog)
            )
            server.awaitServed()
            server.close()
            httpFixture = null
            assertTrue("The verified pack must be recorded as installed", PackManager.isDownloaded(context, packId))
            assertEquals(archiveSha256, PackManager.installedPackSha256(context, packId))
            assertEquals(30, DownloadedLutFilterRepository.refreshInstalled(context, catalog))

            val presets = FilterManifest.downloadedPresets()
            assertEquals("All 30 manifest entries must be registered", 30, presets.size)
            val presetIds = presets.map { it.id }.toSet()
            assertEquals("Every downloaded LUT must have a unique registered id", 30, presetIds.size)
            presets.forEach { preset ->
                assertTrue(
                    "${preset.id} must load from its verified installed cube",
                    DownloadedLutFilterRepository.loadVerifiedLut(context, preset) != null
                )
            }

            val sourceTrack = readVideoTrack(source)
            assertTrue("The fixture must cover the 1.8-second export window", sourceTrack.videoTrackDurationUs >= 1_800_000L)
            gallerySource = frameAt(source, 600_000L)
            val gallery = FilterThumbnailGenerator.generateDynamicThumbnails(
                context = context,
                source = requireNotNull(gallerySource),
                manifest = FilterManifest(categories = emptyList(), filters = emptyList()),
                customPresets = presets,
                cacheKey = "downloaded-lut-roundtrip-$id"
            )
            assertTrue("The original sample must be present", gallery.containsKey(null))
            assertEquals("Every downloaded preset must produce its own real-LUT sample", presetIds, gallery.keys.filterNotNull().toSet())
            presets.forEach { preset ->
                val image = gallery[preset.id]
                assertTrue("${preset.id} must have a rendered 120px sample", image != null && image.width == 120 && image.height == 120)
            }

            val selectedPreset = requireNotNull(presets.firstOrNull { it.id == "dl_${packId}_amber-drift" })
            val lutTexture = requireNotNull(LutBitmapCache.getOrLoad(context, selectedPreset))
            val clip = MediaClip(
                id = "lut-clip-$id",
                name = "Downloaded LUT fixture",
                uri = Uri.fromFile(source).toString(),
                durationMs = sourceTrack.videoTrackDurationUs / 1_000L,
                trimEndMs = 1_800L
            )
            val clips = listOf(clip)
            val baseConfig = ExportEngine.ExportConfig(
                resolution = "720p",
                fps = 30,
                quality = "high",
                aspectRatio = "16:9",
                bitrateMbps = 8,
                trimEndMs = 1_800L
            )
            val lutConfig = baseConfig.copy(filterPreset = selectedPreset, filterIntensity = 1f)
            // Project exports take trims from MediaClip; ExportConfig trim fields must remain unset.
            val projectBaseConfig = baseConfig.copy(trimStartMs = 0L, trimEndMs = 0L)
            val projectLutConfig = projectBaseConfig.copy(filterPreset = selectedPreset, filterIntensity = 1f)

            suspend fun export(index: Int, config: ExportEngine.ExportConfig, project: Boolean): File {
                if (project) engines[index].startProjectExport(clips, config, names[index])
                else engines[index].startExport(Uri.fromFile(source).toString(), config, names[index])
                val terminal = awaitTerminalState(engines[index])
                assertNull("${names[index]} must finish without error", terminal.error)
                val result = File(requireNotNull(Uri.parse(requireNotNull(terminal.outputUri)).path))
                assertTrue("${names[index]} must contain an encoded MP4", result.isFile && result.length() > 0L)
                return result
            }

            outputs[0] = export(0, baseConfig, project = false)
            outputs[1] = export(1, lutConfig, project = false)
            outputs[2] = export(2, projectBaseConfig, project = true)
            outputs[3] = export(3, projectLutConfig, project = true)

            val sourceAnchors = sourceTrack.presentationTimesUs.filter { it < 1_800_000L }
            val tracks = outputs.map { readVideoTrack(requireNotNull(it)) }
            fun commonSampleTime(baselineIndex: Int, lutIndex: Int): Long {
                val common = commonRetainedFrameTimesUs(
                    referenceTimesUs = sourceAnchors,
                    encodedTimesByLabel = mapOf(
                        "baseline" to tracks[baselineIndex].presentationTimesUs,
                        "downloaded-lut" to tracks[lutIndex].presentationTimesUs
                    )
                )
                return common.minByOrNull { abs(it - 700_000L) }
                    ?: error("No source frame was retained by both LUT round-trip outputs")
            }

            fun changedPixels(first: Bitmap, second: Bitmap): Int {
                assertEquals(first.width, second.width)
                assertEquals(first.height, second.height)
                val firstPixels = IntArray(first.width * first.height)
                val secondPixels = IntArray(second.width * second.height)
                first.getPixels(firstPixels, 0, first.width, 0, 0, first.width, first.height)
                second.getPixels(secondPixels, 0, second.width, 0, 0, second.width, second.height)
                return firstPixels.indices.count { index ->
                    val a = firstPixels[index]
                    val b = secondPixels[index]
                    abs(Color.red(a) - Color.red(b)) + abs(Color.green(a) - Color.green(b)) +
                        abs(Color.blue(a) - Color.blue(b)) >= 80
                }
            }

            fun meanRgbError(expected: Bitmap, actual: Bitmap): Double {
                assertEquals(expected.width, actual.width)
                assertEquals(expected.height, actual.height)
                val expectedPixels = IntArray(expected.width * expected.height)
                val actualPixels = IntArray(actual.width * actual.height)
                expected.getPixels(expectedPixels, 0, expected.width, 0, 0, expected.width, expected.height)
                actual.getPixels(actualPixels, 0, actual.width, 0, 0, actual.width, actual.height)
                val total = expectedPixels.indices.sumOf { index ->
                    val a = expectedPixels[index]
                    val b = actualPixels[index]
                    (abs(Color.red(a) - Color.red(b)) + abs(Color.green(a) - Color.green(b)) +
                        abs(Color.blue(a) - Color.blue(b))).toLong()
                }
                return total.toDouble() / (expectedPixels.size * 3.0)
            }

            fun assertPreviewParity(label: String, baseline: File, filtered: File, timeUs: Long) {
                val baseFrame = frameAt(baseline, timeUs)
                val filteredFrame = frameAt(filtered, timeUs)
                var expectedPreview: Bitmap? = null
                try {
                    assertEquals(1_280, filteredFrame.width)
                    assertEquals(720, filteredFrame.height)
                    val changed = changedPixels(baseFrame, filteredFrame)
                    assertTrue("$label LUT output changed $changed pixels; require >=500 at RGB distance >=80", changed >= 500)
                    expectedPreview = LutBitmapCache.applyToBitmap(baseFrame, lutTexture, 1f)
                    val error = meanRgbError(expectedPreview, filteredFrame)
                    assertTrue("$label decoded export differs from the real-LUT preview (mean RGB error=$error)", error <= 24.0)
                } finally {
                    baseFrame.recycle()
                    filteredFrame.recycle()
                    expectedPreview?.recycle()
                }
            }

            assertPreviewParity("selected-clip export", requireNotNull(outputs[0]), requireNotNull(outputs[1]), commonSampleTime(0, 1))
            assertPreviewParity("whole-project export", requireNotNull(outputs[2]), requireNotNull(outputs[3]), commonSampleTime(2, 3))
        } finally {
            var safeToDeleteOutputs = true
            for (engine in engines) {
                val safeToDelete = engine.cancelAndCheckSafeToDelete()
                safeToDeleteOutputs = safeToDeleteOutputs && safeToDelete
            }
            httpFixture?.close()
            gallerySource?.recycle()
            instrumentation.runOnMainSync { engines.forEach { it.release() } }
            if (safeToDeleteOutputs) {
                names.forEach { name ->
                    File(outputDir, name).delete()
                    File(outputDir, name.removeSuffix(".mp4") + "_music.mp4").delete()
                }
            }
            FilterManifest.resetForTesting()
            FilterThumbnailGenerator.invalidateDynamicCache()
            LutBitmapCache.clear()
            if (safeToDeleteOutputs) root.deleteRecursively()
        }
    }

    @Test
    fun projectAdjustmentsAreBakedIntoSelectedAndWholeProjectExports() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val id = UUID.randomUUID().toString()
        val root = File(instrumentation.targetContext.cacheDir, "adjustments-roundtrip-$id").apply { mkdirs() }
        val context = object : ContextWrapper(instrumentation.targetContext) {
            override fun getApplicationContext(): android.content.Context = this
            override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
            override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
            override fun getExternalFilesDir(type: String?): File {
                val base = File(root, "external")
                return (if (type == null) base else File(base, type)).apply { mkdirs() }
            }
        }
        val source = File(context.cacheDir, "source.mp4")
        val outputDir = File(context.getExternalFilesDir(null), "ApexStudio_Exports")
        val names = listOf("selected-base", "selected-adjusted", "project-base", "project-adjusted")
            .map { "$it-$id.mp4" }
        val engines = List(4) { ExportEngine(context) }
        val outputs = arrayOfNulls<File>(4)

        fun frameAt(file: File, timeUs: Long): Bitmap {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(file.absolutePath)
                requireNotNull(retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST))
            } finally {
                retriever.release()
            }
        }

        try {
            instrumentation.context.assets.open("export/cfr_30fps_audio_fixture.mp4").use { input ->
                source.outputStream().use { output -> input.copyTo(output) }
            }
            val sourceTrack = readVideoTrack(source)
            assertTrue("The fixture must cover the 1.8-second export window", sourceTrack.videoTrackDurationUs >= 1_800_000L)
            val clip = MediaClip(
                id = "adjustment-clip-$id",
                name = "Video adjustment fixture",
                uri = Uri.fromFile(source).toString(),
                durationMs = sourceTrack.videoTrackDurationUs / 1_000L,
                trimEndMs = 1_800L
            )
            val clips = listOf(clip)
            val baseConfig = ExportEngine.ExportConfig(
                resolution = "720p",
                fps = 30,
                quality = "high",
                aspectRatio = "16:9",
                bitrateMbps = 8,
                trimEndMs = 1_800L
            )
            val adjustments = VideoAdjustments(brightness = .28f, sharpness = .65f)
            val adjustedConfig = baseConfig.copy(adjustments = adjustments)
            // Project exports take clip trims from MediaClip; ExportConfig trim fields remain unset.
            val projectBaseConfig = baseConfig.copy(trimStartMs = 0L, trimEndMs = 0L)
            val projectAdjustedConfig = projectBaseConfig.copy(adjustments = adjustments)

            suspend fun export(index: Int, config: ExportEngine.ExportConfig, project: Boolean): File {
                if (project) engines[index].startProjectExport(clips, config, names[index])
                else engines[index].startExport(Uri.fromFile(source).toString(), config, names[index])
                val terminal = awaitTerminalState(engines[index])
                assertNull("${names[index]} must finish without error", terminal.error)
                val result = File(requireNotNull(Uri.parse(requireNotNull(terminal.outputUri)).path))
                assertTrue("${names[index]} must contain an encoded MP4", result.isFile && result.length() > 0L)
                return result
            }

            outputs[0] = export(0, baseConfig, project = false)
            outputs[1] = export(1, adjustedConfig, project = false)
            outputs[2] = export(2, projectBaseConfig, project = true)
            outputs[3] = export(3, projectAdjustedConfig, project = true)

            val sourceAnchors = sourceTrack.presentationTimesUs.filter { it < 1_800_000L }
            val tracks = outputs.map { readVideoTrack(requireNotNull(it)) }
            val exclusiveEndUs = 1_800_000L
            tracks.forEachIndexed { index, track ->
                val label = names[index]
                val details = "videoPtsUs=${track.presentationTimesUs} videoDurationUs=${track.videoTrackDurationUs} " +
                    "movieDurationUs=${track.movieDurationUs} audioDurationUs=${track.audioDurationUs} tracks=${track.trackDurations}"
                assertTrue("$label must contain encoded video samples; $details", track.presentationTimesUs.isNotEmpty())
                assertTrue(
                    "$label must keep every video sample before the exclusive ${exclusiveEndUs}us end; $details",
                    track.presentationTimesUs.all { it < exclusiveEndUs }
                )
                assertTrue(
                    "$label video track duration must remain within 150ms of the planned end; $details",
                    track.videoTrackDurationUs > 0L && abs(track.videoTrackDurationUs - exclusiveEndUs) <= 150_000L
                )
                assertTrue(
                    "$label movie duration must remain within 150ms of the planned end; $details",
                    track.movieDurationUs?.let { abs(it - exclusiveEndUs) <= 150_000L } == true
                )
                assertTrue(
                    "$label must retain its non-empty source audio track; $details",
                    track.audioDurationUs?.let { it > 0L } == true
                )
            }
            fun commonSampleTime(baselineIndex: Int, adjustedIndex: Int): Long {
                val common = commonRetainedFrameTimesUs(
                    referenceTimesUs = sourceAnchors,
                    encodedTimesByLabel = mapOf(
                        "baseline" to tracks[baselineIndex].presentationTimesUs,
                        "adjusted" to tracks[adjustedIndex].presentationTimesUs
                    )
                )
                return common.minByOrNull { abs(it - 700_000L) }
                    ?: error("No source frame was retained by both adjustment round-trip outputs")
            }

            fun changedPixels(first: Bitmap, second: Bitmap): Int {
                assertEquals(first.width, second.width)
                assertEquals(first.height, second.height)
                val firstPixels = IntArray(first.width * first.height)
                val secondPixels = IntArray(second.width * second.height)
                first.getPixels(firstPixels, 0, first.width, 0, 0, first.width, first.height)
                second.getPixels(secondPixels, 0, second.width, 0, 0, second.width, second.height)
                return firstPixels.indices.count { index ->
                    val a = firstPixels[index]
                    val b = secondPixels[index]
                    abs(Color.red(a) - Color.red(b)) + abs(Color.green(a) - Color.green(b)) +
                        abs(Color.blue(a) - Color.blue(b)) >= 80
                }
            }

            fun assertAdjustmentsBaked(label: String, baseline: File, adjusted: File, timeUs: Long) {
                val baselineFrame = frameAt(baseline, timeUs)
                val adjustedFrame = frameAt(adjusted, timeUs)
                try {
                    assertEquals("$label output width", 1_280, adjustedFrame.width)
                    assertEquals("$label output height", 720, adjustedFrame.height)
                    val changed = changedPixels(baselineFrame, adjustedFrame)
                    assertTrue(
                        "$label adjustment output changed $changed pixels; require >=500 at RGB distance >=80",
                        changed >= 500
                    )
                } finally {
                    baselineFrame.recycle()
                    adjustedFrame.recycle()
                }
            }

            assertAdjustmentsBaked(
                "selected-clip export", requireNotNull(outputs[0]), requireNotNull(outputs[1]), commonSampleTime(0, 1)
            )
            assertAdjustmentsBaked(
                "whole-project export", requireNotNull(outputs[2]), requireNotNull(outputs[3]), commonSampleTime(2, 3)
            )
        } finally {
            var safeToDeleteOutputs = true
            for (engine in engines) {
                val safeToDelete = engine.cancelAndCheckSafeToDelete()
                safeToDeleteOutputs = safeToDeleteOutputs && safeToDelete
            }
            instrumentation.runOnMainSync { engines.forEach { it.release() } }
            if (safeToDeleteOutputs) {
                names.forEach { name ->
                    File(outputDir, name).delete()
                    File(outputDir, name.removeSuffix(".mp4") + "_music.mp4").delete()
                }
                root.deleteRecursively()
            }
        }
    }

    @Test
    fun bakedChromaWallpaperReplacementSurvivesSelectedAndProjectExports() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val baseContext = instrumentation.targetContext
        val id = UUID.randomUUID().toString()
        val root = File(baseContext.cacheDir, "chroma-key-encoded-$id").apply { mkdirs() }
        val context = object : ContextWrapper(baseContext) {
            override fun getApplicationContext(): android.content.Context = this
            override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
            override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
            override fun getExternalFilesDir(type: String?): File {
                val base = File(root, "external")
                return (if (type == null) base else File(base, type)).apply { mkdirs() }
            }
        }
        val width = 320
        val height = 180
        val sourceBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    setPixel(x, y, if (x < width * 2 / 3) Color.GREEN else Color.RED)
                }
            }
        }
        val wallpaperBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLUE)
        }
        val wallpaperFile = File(context.cacheDir, "wallpaper-$id.png")
        val outputFile = File(context.cacheDir, "baked-$id.mp4")
        val exportOutputDir = File(context.getExternalFilesDir(null), "ApexStudio_Exports")
        val selectedOutputName = "chroma-selected-$id.mp4"
        val projectOutputName = "chroma-project-$id.mp4"
        val selectedEngine = ExportEngine(context)
        val projectEngine = ExportEngine(context)
        var sourceFile: File? = null
        var bakedFile: File? = null
        var selectedOutput: File? = null
        var projectOutput: File? = null
        var sourceFrame: Bitmap? = null
        var bakedFrame: Bitmap? = null

        try {
            wallpaperFile.outputStream().use { output ->
                assertTrue(
                    "The deterministic wallpaper fixture must encode as PNG",
                    wallpaperBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                )
            }
            val source = withTimeout(120_000L) {
                requireNotNull(PhotoVideoEncoder.encodeStillImage(
                context = context,
                bitmap = sourceBitmap,
                durationMs = 1_000L,
                frameRate = 30
            )) { "The deterministic green-screen MP4 fixture must encode." }
            }
            sourceFile = source
            val sourceUri = Uri.fromFile(source).toString()
            val sourceTrack = readVideoTrack(source)
            assertFalse("The deterministic still-video fixture must not contain audio", hasTrack(source, "audio/"))
            assertTrue(
                "The source fixture should retain approximately one second of video",
                abs(sourceTrack.videoTrackDurationUs - 1_000_000L) <= 200_000L
            )
            assertEquals("The 30fps source fixture must contain all 30 encoded samples", 30, sourceTrack.presentationTimesUs.size)
            assertTrue("The source fixture must contain multiple encoded frames", sourceTrack.presentationTimesUs.size >= 2)

            val wallpaperPath = wallpaperFile.canonicalPath
            assertTrue(
                "The replacement image URI must resolve inside this isolated project fixture",
                wallpaperPath.startsWith(root.canonicalPath + File.separator)
            )
            val validatedWallpaperUri = Uri.fromFile(wallpaperFile.canonicalFile).toString()
            assertEquals(
                "The wallpaper URI passed to the engine must identify the validated project fixture",
                wallpaperPath,
                Uri.parse(validatedWallpaperUri).path
            )
            val decodedWallpaper = requireNotNull(BitmapFactory.decodeFile(wallpaperPath)) {
                "The selected test-project wallpaper URI must resolve to a decodable image."
            }
            try {
                assertEquals("The project wallpaper fixture width must be validated", width, decodedWallpaper.width)
                assertEquals("The project wallpaper fixture height must be validated", height, decodedWallpaper.height)
            } finally {
                decodedWallpaper.recycle()
            }
            val replacement = BackgroundReplaceSettings(
                mode = BackgroundReplaceMode.IMAGE,
                imageUri = validatedWallpaperUri,
                chromaKeySettings = ChromaKeySettings(
                    enabled = true,
                    keyColorArgb = 0xFF00FF00L,
                    similarity = 0.25f,
                    smoothness = 0.10f,
                    spillSuppression = 0f
                ),
                sourceUri = sourceUri
            )
            val baked = withTimeout(180_000L) {
                // This is the same source-URI-bound authorization scope used by EditorBackgroundController.
                GlobalModerationEnforcement.withExistingProjectVideoBackgroundReplacement(sourceUri) {
                    BackgroundReplaceEngine.process(context, sourceUri, replacement, outputFile)
                }
            }
            bakedFile = baked
            assertEquals("The engine must publish to the requested MP4 path", outputFile.canonicalFile, baked.canonicalFile)
            assertTrue("The background-replacement encoder must produce a non-empty MP4", baked.isFile && baked.length() > 0L)
            assertTrue("The baked output must contain a video track", hasTrack(baked, "video/"))

            val bakedTrack = readVideoTrack(baked)
            assertEquals("The baked video width must be retained", width, bakedTrack.videoWidth)
            assertEquals("The baked video height must be retained", height, bakedTrack.videoHeight)
            assertFalse("The baked video must contain encoded samples", bakedTrack.presentationTimesUs.isEmpty())
            assertTrue("The baked video track must report a positive duration", bakedTrack.videoTrackDurationUs > 0L)
            assertEquals(
                "Background replacement must retain every source frame",
                sourceTrack.presentationTimesUs.size,
                bakedTrack.presentationTimesUs.size
            )
            assertTrue(
                "The encoded video track duration must stay within 150 ms of the source",
                abs(bakedTrack.videoTrackDurationUs - sourceTrack.videoTrackDurationUs) <= 150_000L
            )
            assertOutputTiming(
                "Chroma Key background replacement",
                bakedTrack,
                expectedDurationUs = sourceTrack.videoTrackDurationUs,
                expectedSourceAnchorsUs = sourceTrack.presentationTimesUs,
                requestedFps = 30
            )

            val bakedDurationMs = (bakedTrack.videoTrackDurationUs / 1_000L).coerceAtLeast(1L)
            val bakedClip = MediaClip(
                id = "chroma-key-$id",
                name = "Chroma Key encoded fixture",
                uri = Uri.fromFile(baked).toString(),
                durationMs = bakedDurationMs,
                trimEndMs = bakedDurationMs,
                backgroundReplace = replacement
            )
            assertNull(
                "The real baked MP4 must pass project-export preflight",
                projectCompositionUnsupportedReason(listOf(bakedClip), ExportEngine.ExportConfig())
            )

            val exportConfig = ExportEngine.ExportConfig(
                resolution = "720p",
                fps = 30,
                aspectRatio = "16:9",
                bitrateMbps = 8
            )
            // Keep the selected route's endpoint explicit instead of relying only on the final input sample PTS.
            val selectedConfig = exportConfig.copy(trimEndMs = bakedDurationMs)
            selectedEngine.startExport(Uri.fromFile(baked).toString(), selectedConfig, selectedOutputName)
            val selectedResult = awaitTerminalState(selectedEngine)
            assertNull("Selected export of the baked chroma clip must complete: ${selectedResult.error}", selectedResult.error)
            selectedOutput = File(requireNotNull(Uri.parse(requireNotNull(selectedResult.outputUri)).path))
            assertTrue("Selected export must contain an encoded MP4", selectedOutput!!.isFile && selectedOutput!!.length() > 0L)
            assertEquals(
                "Selected export must publish to its requested destination",
                File(exportOutputDir, selectedOutputName).canonicalFile,
                selectedOutput!!.canonicalFile
            )

            projectEngine.startProjectExport(
                clips = listOf(bakedClip),
                config = exportConfig,
                outputFileName = projectOutputName
            )
            val projectResult = awaitTerminalState(projectEngine)
            assertNull("Whole-project export of the baked chroma clip must complete: ${projectResult.error}", projectResult.error)
            projectOutput = File(requireNotNull(Uri.parse(requireNotNull(projectResult.outputUri)).path))
            assertTrue("Whole-project export must contain an encoded MP4", projectOutput!!.isFile && projectOutput!!.length() > 0L)
            assertEquals(
                "Whole-project export must publish to its requested destination",
                File(exportOutputDir, projectOutputName).canonicalFile,
                projectOutput!!.canonicalFile
            )

            fun decodeFirstFrame(file: File): Bitmap {
                val retriever = MediaMetadataRetriever()
                return try {
                    retriever.setDataSource(file.absolutePath)
                    requireNotNull(retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST)) {
                        "${file.name} must contain a decodable video frame."
                    }
                } finally {
                    retriever.release()
                }
            }
            val original = decodeFirstFrame(source)
            sourceFrame = original
            val processed = decodeFirstFrame(baked)
            bakedFrame = processed
            assertEquals(width, original.width)
            assertEquals(height, original.height)
            assertEquals(width, processed.width)
            assertEquals(height, processed.height)

            fun rgbL1Distance(first: Int, second: Int): Int =
                abs(Color.red(first) - Color.red(second)) +
                    abs(Color.green(first) - Color.green(second)) +
                    abs(Color.blue(first) - Color.blue(second))

            assertTrue(
                "The encoded source must retain a recognizably green key region",
                rgbL1Distance(original.getPixel(width / 4, height / 2), Color.GREEN) < 80
            )
            assertTrue(
                "The encoded source must retain a recognizably red foreground region",
                rgbL1Distance(original.getPixel(width * 5 / 6, height / 2), Color.RED) < 80
            )

            fun assertFinalExportPreservesChroma(label: String, file: File) {
                val expectedDurationUs = bakedDurationMs * 1_000L
                val track = readVideoTrack(file)
                assertEquals("$label output width", 1_280, track.videoWidth)
                assertEquals("$label output height", 720, track.videoHeight)
                val sourceAnchorsUs = bakedTrack.presentationTimesUs.sorted()
                val outputAnchorsUs = track.presentationTimesUs.sorted()
                val expectedInteriorAnchorsUs = sourceAnchorsUs.dropLast(2)
                val anchorToleranceUs = 5_000L
                assertOutputTiming(
                    label = label,
                    output = track,
                    expectedDurationUs = expectedDurationUs,
                    // Media3 may omit the last two frame anchors at EOS; duration and exclusive end remain checked separately.
                    expectedSourceAnchorsUs = expectedInteriorAnchorsUs,
                    requestedFps = 30
                )
                var nextOutputIndex = 0
                expectedInteriorAnchorsUs.forEach { expectedUs ->
                    while (nextOutputIndex < outputAnchorsUs.size &&
                        outputAnchorsUs[nextOutputIndex] < expectedUs - anchorToleranceUs
                    ) {
                        nextOutputIndex++
                    }
                    assertTrue(
                        "$label must retain each interior source PTS in order within ${anchorToleranceUs}us; " +
                            "missing/shifted anchor=${expectedUs}us, output PTS=${track.presentationTimesUs}",
                        nextOutputIndex < outputAnchorsUs.size &&
                            abs(outputAnchorsUs[nextOutputIndex] - expectedUs) <= anchorToleranceUs
                    )
                    if (nextOutputIndex < outputAnchorsUs.size) nextOutputIndex++
                }
                assertTrue(
                    "$label must not introduce output PTS unrelated to any source frame anchor within ${anchorToleranceUs}us; " +
                        "output PTS=${track.presentationTimesUs}; source PTS=$sourceAnchorsUs",
                    track.presentationTimesUs.all { outputUs ->
                        sourceAnchorsUs.any { sourceUs -> abs(sourceUs - outputUs) <= anchorToleranceUs }
                    }
                )
                assertTrue(
                    "$label must keep every encoded video PTS before the exclusive ${expectedDurationUs}us end; " +
                        "video PTS=${track.presentationTimesUs}",
                    track.presentationTimesUs.all { it < expectedDurationUs }
                )
                assertFalse("$label must not introduce audio absent from the deterministic video-only fixture", hasTrack(file, "audio/"))

                val rendered = decodeFirstFrame(file)
                try {
                    assertEquals("$label decoded frame width", 1_280, rendered.width)
                    assertEquals("$label decoded frame height", 720, rendered.height)
                    val scaledSource = Bitmap.createScaledBitmap(original, rendered.width, rendered.height, true)
                    try {
                        val rows = rendered.height * 4 / 45 until rendered.height - rendered.height * 4 / 45
                        val keyColumns = rendered.width * 3 / 40 until rendered.width * 2 / 3 - rendered.width / 20
                        var changedKeyPixels = 0
                        var wallpaperMatchedPixels = 0
                        for (y in rows) {
                            for (x in keyColumns) {
                                val before = scaledSource.getPixel(x, y)
                                val after = rendered.getPixel(x, y)
                                if (rgbL1Distance(before, after) >= 80) changedKeyPixels++
                                if (rgbL1Distance(after, Color.BLUE) <= 80) wallpaperMatchedPixels++
                            }
                        }
                        val keyRegionPixels = rows.count() * keyColumns.count()
                        val sourceReferenceKeyPixels = (height - 32).toLong() * (width * 2 / 3 - 40)
                        val scaledMinimumChangedPixels =
                            ((keyRegionPixels.toLong() * 500L + sourceReferenceKeyPixels - 1L) /
                                sourceReferenceKeyPixels).toInt()
                        val minimumChangedPixels = maxOf(500, scaledMinimumChangedPixels)
                        assertTrue(
                            "$label changed $changedKeyPixels key-region pixels; require >=$minimumChangedPixels " +
                                "at RGB L1 distance >=80 (resolution-scaled from the 500-pixel source gate)",
                            changedKeyPixels >= minimumChangedPixels
                        )
                        assertTrue(
                            "$label wallpaper region matched blue in $wallpaperMatchedPixels/$keyRegionPixels pixels",
                            wallpaperMatchedPixels >= keyRegionPixels * 3 / 4
                        )

                        val foregroundColumns = rendered.width * 2 / 3 + rendered.width / 20 until
                            rendered.width - rendered.width * 3 / 40
                        var retainedForegroundPixels = 0
                        for (y in rows) {
                            for (x in foregroundColumns) {
                                if (rgbL1Distance(rendered.getPixel(x, y), scaledSource.getPixel(x, y)) <= 60) {
                                    retainedForegroundPixels++
                                }
                            }
                        }
                        val foregroundRegionPixels = rows.count() * foregroundColumns.count()
                        assertTrue(
                            "$label retained only $retainedForegroundPixels/$foregroundRegionPixels foreground pixels " +
                                "within RGB L1 distance 60; require >=90%",
                            retainedForegroundPixels >= foregroundRegionPixels * 9 / 10
                        )
                    } finally {
                        if (scaledSource !== original) scaledSource.recycle()
                    }
                } finally {
                    rendered.recycle()
                }
            }

            val rows = 16 until height - 16
            val keyColumns = 24 until width * 2 / 3 - 16
            var changedKeyPixels = 0
            var wallpaperMatchedPixels = 0
            for (y in rows) {
                for (x in keyColumns) {
                    val before = original.getPixel(x, y)
                    val after = processed.getPixel(x, y)
                    if (rgbL1Distance(before, after) >= 80) changedKeyPixels++
                    if (rgbL1Distance(after, Color.BLUE) <= 80) wallpaperMatchedPixels++
                }
            }
            val keyRegionPixels = rows.count() * keyColumns.count()
            assertTrue(
                "Encoded Chroma Key must change at least 500 key-region pixels at RGB L1 distance >=80; changed=$changedKeyPixels",
                changedKeyPixels >= 500
            )
            assertTrue(
                "Most of the keyed region must decode near the blue wallpaper; matched=$wallpaperMatchedPixels/$keyRegionPixels",
                wallpaperMatchedPixels >= keyRegionPixels * 3 / 4
            )

            val foregroundColumns = width * 2 / 3 + 16 until width - 24
            var retainedForegroundPixels = 0
            for (y in rows) {
                for (x in foregroundColumns) {
                    if (rgbL1Distance(processed.getPixel(x, y), original.getPixel(x, y)) <= 60) {
                        retainedForegroundPixels++
                    }
                }
            }
            val foregroundRegionPixels = rows.count() * foregroundColumns.count()
            assertTrue(
                "At least 90% of the foreground must remain within RGB L1 distance 60 of the source; retained=$retainedForegroundPixels/$foregroundRegionPixels",
                retainedForegroundPixels >= foregroundRegionPixels * 9 / 10
            )

            assertFinalExportPreservesChroma("Selected-clip export", requireNotNull(selectedOutput))
            assertFinalExportPreservesChroma("Whole-project export", requireNotNull(projectOutput))
        } finally {
            var safeToDeleteOutputs = true
            for (engine in listOf(selectedEngine, projectEngine)) {
                val safeToDelete = engine.cancelAndCheckSafeToDelete()
                safeToDeleteOutputs = safeToDeleteOutputs && safeToDelete
            }
            instrumentation.runOnMainSync {
                selectedEngine.release()
                projectEngine.release()
            }
            sourceFrame?.recycle()
            bakedFrame?.recycle()
            sourceBitmap.recycle()
            wallpaperBitmap.recycle()
            if (safeToDeleteOutputs) {
                sourceFile?.delete()
                bakedFile?.delete()
                selectedOutput?.delete()
                projectOutput?.delete()
                outputFile.delete()
                wallpaperFile.delete()
                File(exportOutputDir, selectedOutputName).delete()
                File(exportOutputDir, projectOutputName).delete()
                File(exportOutputDir, selectedOutputName.removeSuffix(".mp4") + "_music.mp4").delete()
                File(exportOutputDir, projectOutputName.removeSuffix(".mp4") + "_music.mp4").delete()
                root.deleteRecursively()
            }
        }
    }

    private suspend fun awaitTerminalState(engine: ExportEngine): ExportEngine.ExportProgressState =
        withTimeout(180_000L) {
            engine.exportState.first { it.error != null || it.outputUri != null }
        }

    private suspend fun ExportEngine.cancelAndCheckSafeToDelete(): Boolean =
        try {
            cancelExportAndAwait()
        } catch (_: Exception) {
            false
        }

    private fun assertOutputTiming(
        label: String,
        output: VideoTrack,
        expectedDurationUs: Long,
        expectedSourceAnchorsUs: List<Long>,
        requestedFps: Int
    ) {
        val timestamps = output.presentationTimesUs
        assertFalse("$label must contain video samples", timestamps.isEmpty())
        assertFalse("$label must not contain negative PTS", timestamps.any { it < 0L })
        assertEquals("$label must not contain duplicate presentation timestamps", timestamps.size, timestamps.distinct().size)
        val movieDurationUs = output.movieDurationUs
        assertTrue(
            "$label must report a positive movie duration; video track=${output.videoTrackDurationUs}us; " +
                "audio track=${output.audioDurationUs}us; track durations=${output.trackDurations}; " +
                "video PTS=${timestamps.joinToString()}",
            movieDurationUs != null && movieDurationUs > 0L
        )
        val measuredDurationUs = movieDurationUs ?: 0L
        val targetIntervalUs = 1_000_000.0 / requestedFps
        // Media3's default FrameDropEffect targets an average frame rate, not a minimum gap between
        // every pair of PTS. A VFR source can therefore keep a short local burst when the following
        // source gap is long. Bound total cadence over the output duration, allowing one boundary frame.
        assertTrue("$label must report a positive movie duration", measuredDurationUs > 0L)
        val outputIntervals = timestamps.size - 1L
        val maximumIntervalsAtTargetCadence =
            (requestedFps.toLong() * measuredDurationUs + 999_999L) / 1_000_000L
        assertTrue(
            "$label averaged too many video intervals for the requested $requestedFps-fps target: " +
                "$outputIntervals intervals over ${measuredDurationUs}us (maximum $maximumIntervalsAtTargetCadence " +
                "after one-frame boundary tolerance; video track ${output.videoTrackDurationUs}us)",
            outputIntervals <= maximumIntervalsAtTargetCadence
        )

        val durationToleranceUs = 150_000L
        assertTrue(
            "$label movie duration ${measuredDurationUs}us must stay within ${durationToleranceUs}us of planned " +
                "$expectedDurationUs us; video track duration=${output.videoTrackDurationUs}us; " +
                "audio track duration=${output.audioDurationUs}us; track durations=${output.trackDurations}; " +
                "encoded video PTS=${timestamps.joinToString()}",
            abs(measuredDurationUs - expectedDurationUs) <= durationToleranceUs
        )
        // Media3 normalizes only the first muxed video sample to PTS zero when audio is present.
        // Later samples remain relative to the trim start; FrameDropEffect may discard burst frames.
        val anchorToleranceUs = targetIntervalUs.toLong() + 5_000L
        expectedSourceAnchorsUs.forEach { expectedUs ->
            val nearestDistanceUs = timestamps.minOf { abs(it - expectedUs) }
            assertTrue(
                "$label lost or shifted source timestamp anchor $expectedUs us; nearest output PTS is $nearestDistanceUs us away; " +
                    "encoded video PTS=${timestamps.joinToString()}",
                nearestDistanceUs <= anchorToleranceUs
            )
        }
    }

    private fun assertActual60FpsCadence(label: String, output: VideoTrack, expectedDurationUs: Long) {
        val timestamps = output.presentationTimesUs
        assertTrue("$label must expose positive video track duration", output.videoTrackDurationUs > 0L)
        assertTrue(
            "$label video track duration ${output.videoTrackDurationUs}us must stay within 150000us of planned " +
                "$expectedDurationUs us",
            abs(output.videoTrackDurationUs - expectedDurationUs) <= 150_000L
        )
        // Count samples against the actual encoded video track duration. The separately checked planned
        // duration prevents audio/container duration from hiding a truncated video track.
        val expectedSamples = (output.videoTrackDurationUs * 60L + 999_999L) / 1_000_000L
        assertTrue(
            "$label must contain approximately $expectedSamples samples for its encoded track duration " +
                "${output.videoTrackDurationUs}us at 60 fps; got ${timestamps.size}",
            timestamps.size.toLong() in (expectedSamples - 1L)..(expectedSamples + 1L)
        )
        val averageFps = timestamps.size * 1_000_000.0 / output.videoTrackDurationUs
        assertTrue("$label encoded average must be 60 fps, got $averageFps", averageFps in 59.5..60.5)
        val intervals = timestamps.zipWithNext { first, second -> second - first }
        assertTrue("$label must contain timestamp intervals", intervals.isNotEmpty())
        assertTrue("$label has an interval shorter than the 60 fps cadence", intervals.minOrNull()!! >= 12_500L)
        assertTrue("$label has a gap too large for 60 fps: ${intervals.maxOrNull()} us", intervals.maxOrNull()!! <= 25_000L)
    }

    private fun hasTrack(file: File, mimePrefix: String): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            (0 until extractor.trackCount).any { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty().startsWith(mimePrefix)
            }
        } finally {
            extractor.release()
        }
    }

    /**
     * Keep actual source PTS only when every export retained that presentation moment.
     * At most one millisecond allows timestamp quantization, never a missing frame interval.
     * Anchor selection never observes decoded output pixels or chooses a passing comparison.
     */
    private fun commonRetainedFrameTimesUs(
        referenceTimesUs: List<Long>,
        encodedTimesByLabel: Map<String, List<Long>>,
        toleranceUs: Long = 1_000L
    ): List<Long> {
        require(referenceTimesUs.isNotEmpty()) { "referenceTimesUs must be non-empty: $referenceTimesUs" }
        require(encodedTimesByLabel.isNotEmpty()) { "encodedTimesByLabel must be non-empty: $encodedTimesByLabel" }
        require(toleranceUs in 1L..1_000L) { "toleranceUs must stay within 1..1000us: $toleranceUs" }

        fun validate(label: String, timesUs: List<Long>) {
            require(timesUs.isNotEmpty()) { "$label timestamps must be non-empty: $timesUs" }
            require(timesUs.all { it >= 0L }) { "$label timestamps must be non-negative: $timesUs" }
            require(timesUs.zipWithNext().all { (first, second) -> first < second }) {
                "$label timestamps must be strictly increasing and distinct: $timesUs"
            }
        }
        validate("reference", referenceTimesUs)
        encodedTimesByLabel.forEach { (label, timesUs) -> validate(label, timesUs) }

        return referenceTimesUs.filter { sourcePtsUs ->
            val matchedPts = encodedTimesByLabel.values.map { encodedPtsUs ->
                encodedPtsUs.minBy { abs(it - sourcePtsUs) }
            }
            matchedPts.all { abs(it - sourcePtsUs) <= toleranceUs } &&
                (matchedPts.max() - matchedPts.min()) <= toleranceUs
        }
    }

    private fun readVideoTrack(file: File): VideoTrack {
        val extractor = MediaExtractor()
        var audioDurationUs: Long? = null
        var trackDurations = ""
        var videoWidth = 0
        var videoHeight = 0
        var videoRotationDegrees = 0
        var videoTrackDurationUs = 0L
        var presentationTimes: List<Long> = emptyList()
        try {
            extractor.setDataSource(file.absolutePath)
            val durations = mutableListOf<String>()
            for (trackIndex in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(trackIndex)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME).orEmpty()
                val trackDurationUs = if (trackFormat.containsKey(MediaFormat.KEY_DURATION)) {
                    trackFormat.getLong(MediaFormat.KEY_DURATION)
                } else null
                durations += "$mime=${trackDurationUs ?: "unset"}"
                if (mime.startsWith("audio/")) audioDurationUs = trackDurationUs
            }
            trackDurations = durations.joinToString()
            val index = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("video/")
            } ?: error("${file.name} has no video track")
            val format = extractor.getTrackFormat(index)
            videoWidth = format.getInteger(MediaFormat.KEY_WIDTH)
            videoHeight = format.getInteger(MediaFormat.KEY_HEIGHT)
            videoRotationDegrees = if (format.containsKey(MediaFormat.KEY_ROTATION)) {
                format.getInteger(MediaFormat.KEY_ROTATION)
            } else 0
            videoTrackDurationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else 0L
            extractor.selectTrack(index)
            val timestamps = mutableListOf<Long>()
            while (extractor.sampleTrackIndex >= 0) {
                timestamps += extractor.sampleTime
                if (!extractor.advance()) break
            }
            presentationTimes = timestamps.sorted()
        } finally {
            extractor.release()
        }
        val retriever = MediaMetadataRetriever()
        val movieDurationUs = try {
            retriever.setDataSource(file.absolutePath)
            videoRotationDegrees = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: videoRotationDegrees
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.times(1_000L)
        } finally {
            retriever.release()
        }
        val gaps = presentationTimes.zipWithNext { first, second -> second - first }
        return VideoTrack(
            videoTrackDurationUs,
            videoWidth,
            videoHeight,
            videoRotationDegrees,
            presentationTimes,
            gaps.maxOrNull() ?: 0L,
            movieDurationUs,
            audioDurationUs,
            trackDurations
        )
    }

    private data class VideoTrack(
        val videoTrackDurationUs: Long,
        val videoWidth: Int,
        val videoHeight: Int,
        val rotationDegrees: Int,
        val presentationTimesUs: List<Long>,
        val maxGapUs: Long,
        val movieDurationUs: Long?,
        val audioDurationUs: Long?,
        val trackDurations: String
    )
}
