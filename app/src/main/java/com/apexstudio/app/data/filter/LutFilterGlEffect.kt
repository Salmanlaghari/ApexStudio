package com.apexstudio.app.data.filter

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

/**
 * Real-time 3D LUT colour filter wired into the Media3 GL pipeline.
 *
 * The 3D LUT (a `.cube` file in `assets/luts/`) is reshaped into a
 * 2D `(size*size) x size` strip and uploaded as a single 2D texture
 * — the same trick `GPUImageLookupFilter` uses. This keeps the shader
 * GLSL ES 2.0 (sampler2D) and works on every device the app supports
 * (minSdk 26) without needing an OpenGL ES 3.0 context.
 *
 * The fragment shader maps each frame through the LUT and mixes the
 * result with the original using an `intensity` uniform so the
 * user's slider gives a smooth 0..100% cross-fade.
 *
 * Used both by ExoPlayer preview (`player.setVideoEffects(...)`) and
 * the hardware-accelerated Media3 Transformer export path.
 */
@UnstableApi
class LutFilterGlEffect(
    private val context: Context,
    private val preset: FilterPreset?,
    private val intensity: Float = 1f,
    private val intensityProvider: (() -> Float)? = null,
    private val preloaded: LutTexture? = null
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return LutShaderProgram(context, preset, intensity.coerceIn(0f, 1f), intensityProvider, preloaded, useHdr)
    }

    @UnstableApi
    private class LutShaderProgram(
        context: Context,
        preset: FilterPreset?,
        private val intensity: Float,
        private val intensityProvider: (() -> Float)?,
        preloaded: LutTexture?,
        useHdr: Boolean
    ) : BaseGlShaderProgram(useHdr, TEXTURE_POOL_CAPACITY) {

        private val glProgram: GlProgram
        private val lutTexId: IntArray = intArrayOf(0)
        private var lutSize: Int = 0

        init {
            glProgram = try {
                GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            } catch (e: Exception) {
                throw VideoFrameProcessingException("Failed to compile LUT shader", e)
            }

            glProgram.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )

            // uTransformationMatrix: pass-through NDC positions [-1,1]
            val identity = GlUtil.create4x4IdentityMatrix()
            glProgram.setFloatsUniform("uTransformationMatrix", identity)

            // uTexTransformationMatrix: map NDC [-1,1] → UV [0,1]
            // Without this, vTextureCoord is in [-1,1] which causes
            // CLAMP_TO_EDGE to repeat edge pixels across 3/4 of the
            // frame — the exact corruption pattern (colored stripes +
            // partial video in upper-right) seen in the preview.
            val texMatrix = floatArrayOf(
                0.5f, 0f, 0f, 0f,   // col0: x' = 0.5*x
                0f, 0.5f, 0f, 0f,   // col1: y' = 0.5*y
                0f, 0f, 1f, 0f,     // col2: z' = z
                0.5f, 0.5f, 0f, 1f  // col3: +0.5 bias for x,y
            )
            glProgram.setFloatsUniform("uTexTransformationMatrix", texMatrix)
            glProgram.setFloatUniform("uIntensity", intensity)

            if (preloaded != null) {
                // Hot path: caller already parsed + packed the LUT on
                // Dispatchers.Default (see LutBitmapCache). Only the GL
                // texture upload runs here.
                uploadStrip(preloaded.pixels, preloaded.width, preloaded.height, preloaded.size)
            } else if (preset != null) {
                uploadLut(context, preset)
            } else {
                // No preset selected: upload a 1x1 identity LUT so the
                // shader still has a valid texture bound.
                uploadIdentityLut()
            }
        }

        private fun uploadLut(context: Context, preset: FilterPreset) {
            val lut = loadLut(context, preset) ?: run {
                Log.w(TAG, "No LUT data for ${preset.id}; using identity")
                uploadIdentityLut()
                return
            }
            val packed = LutBitmapCache.packLut(lut) ?: run {
                Log.w(TAG, "LUT ${preset.id} is not a valid cube; using identity")
                uploadIdentityLut()
                return
            }
            uploadStrip(packed.pixels, packed.width, packed.height, packed.size)
        }

        private fun uploadIdentityLut() {
            val size = 2
            val width = size * size
            val height = size
            val pixels = IntArray(width * height)
            var i = 0
            for (b in 0 until size) {
                for (g in 0 until size) {
                    for (r in 0 until size) {
                        val v = ((i / 3) * 255) / (size - 1)
                        pixels[(r) * width + (b * size + g)] =
                            0xFF000000.toInt() or (v shl 16) or (v shl 8) or v
                        i += 3
                    }
                }
            }
            uploadStrip(pixels, width, height, size)
        }

        private fun uploadStrip(pixels: IntArray, width: Int, height: Int, size: Int) {
            val tex = IntArray(1)
            GLES20.glGenTextures(1, tex, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            // Upload through a Bitmap (via GLUtils) rather than packing
            // the IntArray into a ByteBuffer manually: putInt on a
            // little-endian native buffer writes bytes as B,G,R,A, so a
            // raw glTexImage2D would swap every channel (red↔blue) in
            // the LUT and silently distort every filter's colours.
            // Bitmap.createBitmap + GLUtils.texImage2D keeps the ARGB
            // channel order correct on every platform.
            val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
            try {
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            } finally {
                bitmap.recycle()
            }
            GlUtil.checkGlError()
            lutTexId[0] = tex[0]
            lutSize = size
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            return Size(inputWidth, inputHeight)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                glProgram.use()
                glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                glProgram.setSamplerTexIdUniform("uLutSampler", lutTexId[0], 1)
                glProgram.setFloatUniform("uLutSize", lutSize.toFloat())
                val currentIntensity = intensityProvider?.invoke()?.coerceIn(0f, 1f) ?: intensity
                glProgram.setFloatUniform("uIntensity", currentIntensity)
                glProgram.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        override fun release() {
            super.release()
            if (lutTexId[0] != 0) {
                GLES20.glDeleteTextures(1, lutTexId, 0)
                lutTexId[0] = 0
            }
        }
    }

    companion object {
        private const val TAG = "LutFilterGlEffect"
        private const val TEXTURE_POOL_CAPACITY = 1

        private val VERTEX_SHADER = """
            attribute vec4 aFramePosition;
            uniform mat4 uTransformationMatrix;
            uniform mat4 uTexTransformationMatrix;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = uTransformationMatrix * aFramePosition;
                vTextureCoord = (uTexTransformationMatrix * aFramePosition).xy;
            }
        """.trimIndent()

        // 2D-strip 3D LUT lookup. The LUT texture is (size*size) wide
        // and `size` tall; for each B slice, the (g, r) plane lives at
        // y=r, x=b*size+g. We split the blue channel into floor/ceil
        // slice indices and bilinearly blend the two slices — the
        // same scheme the cyberagent GPUImage library uses.
        //
        // Note: x is divided by (size * size) (the texture width), not
        // by `size`. A previous version divided by `size`, which put
        // the sample coordinate at ~2.0 for a 17-size LUT — way past
        // the texture's right edge — and made the filter read
        // clamped-edge garbage, which is why the preview looked
        // untouched even when a filter was "applied".
        private val FRAGMENT_SHADER = """
            precision highp float;
            varying vec2 vTextureCoord;
            uniform sampler2D uTexSampler;
            uniform sampler2D uLutSampler;
            uniform float uLutSize;
            uniform float uIntensity;
            void main() {
                vec4 color = texture2D(uTexSampler, vTextureCoord);
                // 3D LUT lookup via 2D strip texture.
                // Strip layout: width = uLutSize², height = uLutSize.
                // Blue slice b: columns [b*size .. b*size+size), row r.
                float bIdx = color.b * (uLutSize - 1.0);
                float bLow = floor(bIdx);
                float bHigh = min(bLow + 1.0, uLutSize - 1.0);
                float bT = bIdx - bLow;
                float gF = color.g * (uLutSize - 1.0);
                float rF = color.r * (uLutSize - 1.0);
                float widthF = uLutSize * uLutSize;
                // Half-pixel offset (+0.5) so we sample the center
                // of each LUT texel instead of the edge.
                float xLow  = (bLow  * uLutSize + gF + 0.5) / widthF;
                float xHigh = (bHigh * uLutSize + gF + 0.5) / widthF;
                float yCoord = (rF + 0.5) / uLutSize;
                vec3 lo = texture2D(uLutSampler, vec2(xLow, yCoord)).rgb;
                vec3 hi = texture2D(uLutSampler, vec2(xHigh, yCoord)).rgb;
                vec3 graded = mix(lo, hi, bT);
                vec3 outRgb = mix(color.rgb, graded, uIntensity);
                gl_FragColor = vec4(outRgb, color.a);
            }
        """.trimIndent()

        private fun loadLut(context: Context, preset: FilterPreset): FloatArray? {
            return try {
                context.assets.open(preset.asset).use { input ->
                    BufferedReader(InputStreamReader(input)).use { reader ->
                        CubeLutParser.parse(reader)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load LUT ${preset.asset}", e)
                null
            }
        }
    }
}

/**
 * Pre-built LUT pixel buffer ready for `GLUtils.texImage2D`. Pixel
 * layout matches the 2D-strip convention used by
 * [LutFilterGlEffect]'s fragment shader (width = size*size, height =
 * size). Constructed off the main thread by [LutBitmapCache].
 */
data class LutTexture(
    val pixels: IntArray,
    val width: Int,
    val height: Int,
    val size: Int
)

/**
 * Process-scoped cache of parsed + pixel-packed LUTs.
 *
 * Each entry holds the full ARGB pixel buffer ready for upload to a
 * 2D GL texture. Loading happens on [Dispatchers.Default] so the
 * asset read + parsing + bitmap-sized IntArray allocation never runs
 * on the Android Main thread. Repeated taps on the same preset are a
 * map hit, not a re-parse.
 */
object LutBitmapCache {
    private val cache = ConcurrentHashMap<String, LutTexture>()

    /**
     * Return the cached [LutTexture] for [preset], loading + packing
     * it on [Dispatchers.Default] on the first call. Returns null if
     * the .cube asset is missing or malformed.
     */
    suspend fun getOrLoad(context: Context, preset: FilterPreset): LutTexture? {
        cache[preset.id]?.let { return it }
        return withContext(Dispatchers.Default) {
            // Re-check after dispatch: a concurrent caller may have
            // populated the cache while we were waiting for the
            // dispatcher.
            cache[preset.id]?.let { return@withContext it }
            val lut = readLutFromAssets(context, preset) ?: return@withContext null
            val packed = packLut(lut) ?: return@withContext null
            cache[preset.id] = packed
            packed
        }
    }

    /**
     * Drop the cached entry for [preset]. Useful when the editor
     * unloads a project so a subsequent re-load doesn't see stale
     * pixels.
     */
    fun evict(presetId: String) {
        cache.remove(presetId)
    }

    fun clear() {
        cache.clear()
    }

    private fun readLutFromAssets(context: Context, preset: FilterPreset): FloatArray? {
        return try {
            context.assets.open(preset.asset).use { input ->
                BufferedReader(InputStreamReader(input)).use { reader ->
                    CubeLutParser.parse(reader)
                }
            }
        } catch (e: Exception) {
            Log.w("LutBitmapCache", "Failed to load LUT ${preset.asset}", e)
            null
        }
    }

    /**
     * Pack a flat float[size³·3] LUT into the 2D-strip IntArray
     * layout the shader expects (width = size², height = size).
     * Pure CPU — safe to call off the GL thread.
     */
    fun packLut(lut: FloatArray): LutTexture? {
        val entries = lut.size / 3
        val size = Math.cbrt(entries.toDouble()).toInt()
        if (size * size * size != entries) return null
        val width = size * size
        val height = size
        val pixels = IntArray(width * height)
        var idx = 0
        for (b in 0 until size) {
            for (g in 0 until size) {
                for (r in 0 until size) {
                    val rr = (lut[idx].coerceIn(0f, 1f) * 255f).toInt()
                    val gg = (lut[idx + 1].coerceIn(0f, 1f) * 255f).toInt()
                    val bb = (lut[idx + 2].coerceIn(0f, 1f) * 255f).toInt()
                    val x = b * size + g
                    val y = r
                    pixels[y * width + x] = 0xFF000000.toInt() or (rr shl 16) or (gg shl 8) or bb
                    idx += 3
                }
            }
        }
        return LutTexture(pixels, width, height, size)
    }
}