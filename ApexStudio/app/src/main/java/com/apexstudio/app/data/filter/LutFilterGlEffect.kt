package com.apexstudio.app.data.filter

import android.content.Context
import android.opengl.GLES20
import android.util.Log
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import java.io.BufferedReader
import java.io.InputStreamReader

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
    private val intensity: Float
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return LutShaderProgram(context, preset, intensity.coerceIn(0f, 1f), useHdr)
    }

    @UnstableApi
    private class LutShaderProgram(
        context: Context,
        preset: FilterPreset?,
        private val intensity: Float,
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

            val identity = GlUtil.create4x4IdentityMatrix()
            glProgram.setFloatsUniform("uTransformationMatrix", identity)
            glProgram.setFloatsUniform("uTexTransformationMatrix", identity)
            glProgram.setFloatUniform("uIntensity", intensity)

            if (preset != null) {
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
            val entries = lut.size / 3
            val size = Math.cbrt(entries.toDouble()).toInt()
            if (size * size * size != entries) {
                Log.w(TAG, "LUT ${preset.id} is not a valid cube; using identity")
                uploadIdentityLut()
                return
            }
            // Pack into a 2D strip: width = size*size, height = size.
            // For B slice b, the (g, r) plane lives at row b, columns [g*size .. g*size+size).
            val width = size * size
            val height = size
            val pixels = IntArray(width * height)
            // .cube data is ordered with R changing fastest, then G, then B
            // (the "natural" reading order). We want the GPUImage-style
            // 2D-strip layout where the x axis is "slice_index + g" and
            // y axis is "r" within that slice. Match the original LUT
            // ordering to keep the shader math symmetric.
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
            uploadStrip(pixels, width, height, size)
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
            val buf = java.nio.ByteBuffer.allocateDirect(pixels.size * 4).order(java.nio.ByteOrder.nativeOrder())
            for (p in pixels) buf.putInt(p)
            buf.position(0)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf
            )
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
                glProgram.setFloatUniform("uIntensity", intensity)
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
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform sampler2D uTexSampler;
            uniform sampler2D uLutSampler;
            uniform float uLutSize;
            uniform float uIntensity;
            void main() {
                vec4 color = texture2D(uTexSampler, vTextureCoord);
                float bIdx = color.b * (uLutSize - 1.0);
                float bLow = floor(bIdx);
                float bHigh = min(bLow + 1.0, uLutSize - 1.0);
                float bT = bIdx - bLow;
                float gF = color.g * (uLutSize - 1.0);
                float rF = color.r * (uLutSize - 1.0);
                float widthF = uLutSize * uLutSize;
                float xLow = (bLow * uLutSize + gF) / widthF;
                float xHigh = (bHigh * uLutSize + gF) / widthF;
                float yCoord = rF / uLutSize;
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
