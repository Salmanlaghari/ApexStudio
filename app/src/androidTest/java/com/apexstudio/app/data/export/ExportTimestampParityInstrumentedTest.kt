package com.apexstudio.app.data.export

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.opengl.GLUtils
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.apexstudio.app.data.effect.TextOverlayGlShaders
import com.apexstudio.app.data.fx.DownloadedFxEffect
import com.apexstudio.app.data.fx.FxGlEffect
import com.apexstudio.app.data.fx.ModernFxShaders
import com.apexstudio.app.data.packs.download.PackManager
import com.apexstudio.app.data.stickers.StickerAssetResolver
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.StickerOverlay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.File
import java.util.UUID
import kotlin.math.abs

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
    fun downloadedLottieStickerProducesVisiblePixelsInSelectedAndProjectExports() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val engine = ExportEngine(context)
        val id = UUID.randomUUID().toString()
        val source = File(context.cacheDir, "sticker-export-source-$id.mp4")
        val packId = "sticker-export-$id"
        val relativePath = "animated/heart.json"
        val packDir = PackManager.packDir(context, packId)
        val assetFile = File(packDir, relativePath)
        val registryFile = File(PackManager.packsRoot(context), "installed.json")
        val previousRegistry = registryFile.takeIf { it.isFile }?.readText()
        val baselineName = "sticker-baseline-$id.mp4"
        val selectedName = "sticker-selected-$id.mp4"
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
        var projectOutput: File? = null
        var baselineFrame: Bitmap? = null
        var selectedFrame: Bitmap? = null
        var projectFrame: Bitmap? = null

        fun installTestPackAsset() {
            assetFile.parentFile?.mkdirs()
            context.assets.open("lottie/heart_pop.json").use { input ->
                assetFile.outputStream().use { output -> input.copyTo(output) }
            }
            val registry = previousRegistry?.let { JSONObject(it) } ?: JSONObject()
            val packs = registry.optJSONObject("packs") ?: JSONObject()
            val sha256 = java.security.MessageDigest.getInstance("SHA-256")
                .digest(assetFile.readBytes())
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            packs.put(packId, JSONObject().put("version", 1).put("sha256", sha256))
            registry.put("packs", packs)
            registryFile.writeText(registry.toString())
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

        fun assertStickerPixels(label: String, frame: Bitmap, baseline: Bitmap) {
            assertEquals("$label width", baseline.width, frame.width)
            assertEquals("$label height", baseline.height, frame.height)
            val left = frame.width * 35 / 100
            val right = frame.width * 65 / 100
            val top = frame.height * 25 / 100
            val bottom = frame.height * 75 / 100
            var changedPixels = 0
            for (y in top until bottom) {
                for (x in left until right) {
                    val before = baseline.getPixel(x, y)
                    val after = frame.getPixel(x, y)
                    val difference = abs(Color.red(before) - Color.red(after)) +
                        abs(Color.green(before) - Color.green(after)) +
                        abs(Color.blue(before) - Color.blue(after))
                    if (difference >= 80) changedPixels++
                }
            }
            assertTrue(
                "$label must visibly composite the downloaded Lottie sticker in the center; changed pixels=$changedPixels",
                changedPixels >= 500
            )
        }

        try {
            instrumentation.context.assets.open("export/cfr_30fps_audio_fixture.mp4").use { input ->
                source.outputStream().use { output -> input.copyTo(output) }
            }
            installTestPackAsset()
            val assetPath = StickerAssetResolver.packUri(packId, relativePath)
            assertTrue("The test pack must be registered as installed", PackManager.isDownloaded(context, packId))
            assertTrue("The downloaded sticker URI must resolve inside its installed pack",
                StickerAssetResolver.downloadedFile(context, assetPath)?.isFile == true)
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

            engine.startExport(Uri.fromFile(source).toString(), baselineConfig, baselineName)
            val baselineResult = awaitTerminalState(engine)
            assertNull("Sticker baseline export must complete: ${baselineResult.error}", baselineResult.error)
            baselineOutput = File(requireNotNull(Uri.parse(baselineResult.outputUri).path))

            engine.startExport(
                Uri.fromFile(source).toString(),
                baselineConfig.copy(stickers = listOf(sticker)),
                selectedName
            )
            val selectedResult = awaitTerminalState(engine)
            assertNull("Selected export with downloaded sticker must complete: ${selectedResult.error}", selectedResult.error)
            selectedOutput = File(requireNotNull(Uri.parse(selectedResult.outputUri).path))

            val sourceDurationMs = readVideoTrack(source).videoTrackDurationUs / 1_000L
            val projectClip = MediaClip(
                id = "sticker-project-$id",
                name = "Downloaded sticker project fixture",
                uri = Uri.fromFile(source).toString(),
                durationMs = sourceDurationMs,
                trimEndMs = 1_800L,
                stickers = listOf(sticker)
            )
            engine.startProjectExport(
                clips = listOf(projectClip),
                config = ExportEngine.ExportConfig(resolution = "720p", fps = 30, aspectRatio = "16:9"),
                outputFileName = projectName
            )
            val projectResult = awaitTerminalState(engine)
            assertNull("Whole-project export with downloaded sticker must complete: ${projectResult.error}", projectResult.error)
            projectOutput = File(requireNotNull(Uri.parse(projectResult.outputUri).path))

            baselineFrame = decodeFrame(requireNotNull(baselineOutput), 600_000L)
            selectedFrame = decodeFrame(requireNotNull(selectedOutput), 600_000L)
            projectFrame = decodeFrame(requireNotNull(projectOutput), 600_000L)
            assertEquals("720p export width", 1_280, baselineFrame!!.width)
            assertEquals("720p export height", 720, baselineFrame!!.height)
            assertStickerPixels("Selected export", selectedFrame!!, baselineFrame!!)
            assertStickerPixels("Whole-project export", projectFrame!!, baselineFrame!!)
        } finally {
            instrumentation.runOnMainSync { engine.release() }
            baselineFrame?.recycle()
            selectedFrame?.recycle()
            projectFrame?.recycle()
            source.delete()
            packDir.deleteRecursively()
            if (previousRegistry == null) registryFile.delete() else registryFile.writeText(previousRegistry)
            baselineOutput?.delete()
            selectedOutput?.delete()
            projectOutput?.delete()
            File(outputDir, baselineName).delete()
            File(outputDir, selectedName).delete()
            File(outputDir, projectName).delete()
        }
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

    private suspend fun awaitTerminalState(engine: ExportEngine): ExportEngine.ExportProgressState =
        withTimeout(180_000L) {
            engine.exportState.first { it.error != null || it.outputUri != null }
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

    private fun readVideoTrack(file: File): VideoTrack {
        val extractor = MediaExtractor()
        var audioDurationUs: Long? = null
        var trackDurations = ""
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
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.times(1_000L)
        } finally {
            retriever.release()
        }
        val gaps = presentationTimes.zipWithNext { first, second -> second - first }
        return VideoTrack(
            videoTrackDurationUs,
            presentationTimes,
            gaps.maxOrNull() ?: 0L,
            movieDurationUs,
            audioDurationUs,
            trackDurations
        )
    }

    private data class VideoTrack(
        val videoTrackDurationUs: Long,
        val presentationTimesUs: List<Long>,
        val maxGapUs: Long,
        val movieDurationUs: Long?,
        val audioDurationUs: Long?,
        val trackDurations: String
    )
}
