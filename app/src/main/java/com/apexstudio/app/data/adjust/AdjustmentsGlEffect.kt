package com.apexstudio.app.data.adjust

import android.content.Context
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.apexstudio.app.domain.model.VideoAdjustments

/**
 * Real-time video adjustments wired into the Media3 GL pipeline.
 *
 * Applies the same Brightness / Contrast / Saturation / Exposure /
 * Temperature / Tint / Highlights / Shadows maths that
 * [com.apexstudio.app.data.filter.FilterColorMatrix] applies to the
 * CPU side for thumbnails, but as a fragment shader on the GPU so it
 * works on the live preview (`ExoPlayer.setVideoEffects`) AND on the
 * Media3 Transformer export path.
 *
 * The math mirrors the Android ColorMatrix used by FilterColorMatrix
 * (lines 426..471) so what the user sees in the preview matches what
 * is baked into the exported MP4.
 *
 * Slider drag is smooth: each value is read on every frame via
 * [intensityProvider]-style lambdas — no need to rebuild the effect
 * when the user drags a slider.
 *
 * Used by both the preview ExoPlayer and the export pipeline.
 */
@UnstableApi
class AdjustmentsGlEffect(
    private val adjustments: VideoAdjustments = VideoAdjustments(),
    private val brightnessProvider: (() -> Float)? = null,
    private val contrastProvider: (() -> Float)? = null,
    private val saturationProvider: (() -> Float)? = null,
    private val exposureProvider: (() -> Float)? = null,
    private val temperatureProvider: (() -> Float)? = null,
    private val tintProvider: (() -> Float)? = null,
    private val highlightsProvider: (() -> Float)? = null,
    private val shadowsProvider: (() -> Float)? = null,
    private val hdrProvider: (() -> Float)? = null,
    private val brillianceProvider: (() -> Float)? = null
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return AdjustmentsShaderProgram(
            adjustments = adjustments,
            brightnessProvider = brightnessProvider,
            contrastProvider = contrastProvider,
            saturationProvider = saturationProvider,
            exposureProvider = exposureProvider,
            temperatureProvider = temperatureProvider,
            tintProvider = tintProvider,
            highlightsProvider = highlightsProvider,
            shadowsProvider = shadowsProvider,
            hdrProvider = hdrProvider,
            brillianceProvider = brillianceProvider,
            useHdr = useHdr
        )
    }

    @UnstableApi
    private class AdjustmentsShaderProgram(
        adjustments: VideoAdjustments,
        private val brightnessProvider: (() -> Float)?,
        private val contrastProvider: (() -> Float)?,
        private val saturationProvider: (() -> Float)?,
        private val exposureProvider: (() -> Float)?,
        private val temperatureProvider: (() -> Float)?,
        private val tintProvider: (() -> Float)?,
        private val highlightsProvider: (() -> Float)?,
        private val shadowsProvider: (() -> Float)?,
        private val hdrProvider: (() -> Float)?,
        private val brillianceProvider: (() -> Float)?,
        useHdr: Boolean
    ) : BaseGlShaderProgram(useHdr, TEXTURE_POOL_CAPACITY) {

        private val glProgram: GlProgram

        // Snapshot of the slider values; refreshed on every frame
        // by reading the *Provider lambdas (if set), so live slider
        // drag is smooth without rebuilding this effect.
        private var hdr: Float = adjustments.hdr
        private var brilliance: Float = adjustments.brilliance
        private var brightness: Float = adjustments.brightness
        private var contrast: Float = adjustments.contrast
        private var saturation: Float = adjustments.saturation
        private var exposure: Float = adjustments.exposure
        private var temperature: Float = adjustments.temperature
        private var tint: Float = adjustments.tint
        private var highlights: Float = adjustments.highlights
        private var shadows: Float = adjustments.shadows

        init {
            glProgram = try {
                GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            } catch (e: Exception) {
                throw VideoFrameProcessingException("Failed to compile Adjustments shader", e)
            }

            glProgram.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )
            val identity = GlUtil.create4x4IdentityMatrix()
            glProgram.setFloatsUniform("uTransformationMatrix", identity)
            val texMatrix = floatArrayOf(
                0.5f, 0f, 0f, 0f,
                0f, 0.5f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0.5f, 0.5f, 0f, 1f
            )
            glProgram.setFloatsUniform("uTexTransformationMatrix", texMatrix)
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size =
            Size(inputWidth, inputHeight)

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                // Refresh from live providers on every frame.
                brightnessProvider?.let { brightness = it() }
                contrastProvider?.let { contrast = it() }
                saturationProvider?.let { saturation = it() }
                exposureProvider?.let { exposure = it() }
                temperatureProvider?.let { temperature = it() }
                tintProvider?.let { tint = it() }
                highlightsProvider?.let { highlights = it() }
                shadowsProvider?.let { shadows = it() }
                hdrProvider?.let { hdr = it() }
                brillianceProvider?.let { brilliance = it() }

                glProgram.use()
                glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                glProgram.setFloatUniform("uBrightness", brightness)
                glProgram.setFloatUniform("uContrast", contrast)
                glProgram.setFloatUniform("uSaturation", saturation)
                glProgram.setFloatUniform("uExposure", exposure)
                glProgram.setFloatUniform("uTemperature", temperature)
                glProgram.setFloatUniform("uTint", tint)
                glProgram.setFloatUniform("uHighlights", highlights)
                glProgram.setFloatUniform("uShadows", shadows)
                glProgram.setFloatUniform("uHdr", hdr)
                glProgram.setFloatUniform("uBrilliance", brilliance)
                glProgram.bindAttributesAndUniforms()
                android.opengl.GLES20.glDrawArrays(
                    android.opengl.GLES20.GL_TRIANGLE_STRIP, 0, 4
                )
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        override fun release() {
            super.release()
        }
    }

    companion object {
        private const val TEXTURE_POOL_CAPACITY = 4

        // GLSL ES 1.00 — matches what LutFilterGlEffect and
        // FxGlEffect use (compatible with minSdk 26, no GLES3 needed).
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

        /**
         * Adjustments fragment shader. Each uniform is a normalised
         * slider value:
         *  - uBrightness: -1..1 additive (multiplied by 255 inside)
         *  - uContrast: 0..3 multiplicative
         *  - uSaturation: 0..3 (1 = no change)
         *  - uExposure: -2..2 in stops (2^stops multiplier)
         *  - uTemperature: -1..1 (positive = warmer, scales R↑ B↓)
         *  - uTint: -1..1 (positive = green↑)
         *  - uHighlights: -1..1 (lifts/compresses bright pixels)
         *  - uShadows: -1..1 (lifts/compresses dark pixels)
         */
        private val FRAGMENT_SHADER = """
            precision highp float;
            varying vec2 vTextureCoord;
            uniform sampler2D uTexSampler;
            uniform float uBrightness;
            uniform float uContrast;
            uniform float uSaturation;
            uniform float uExposure;
            uniform float uTemperature;
            uniform float uTint;
            uniform float uHighlights;
            uniform float uShadows;
            uniform float uHdr;
            uniform float uBrilliance;

            vec3 applyContrast(vec3 c, float k) {
                // Same formula as Android ColorMatrix contrast: scale
                // around 0.5 so neutral grey stays neutral.
                return (c - 0.5) * k + 0.5;
            }

            vec3 applySaturation(vec3 c, float s) {
                float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
                return mix(vec3(luma), c, s);
            }

            vec3 applyHighlightsShadows(vec3 c, float hi, float sh) {
                // Lift / crush: hi > 0 lifts highlights, hi < 0 crushes
                // them. The curve is a soft lerp toward white/black.
                float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
                float hiFactor = smoothstep(0.5, 1.0, luma);
                float shFactor = 1.0 - smoothstep(0.0, 0.5, luma);
                vec3 lifted = c + vec3(hi) * hiFactor + vec3(sh) * shFactor;
                return clamp(lifted, 0.0, 1.0);
            }

            void main() {
                vec4 src = texture2D(uTexSampler, vTextureCoord);
                vec3 c = src.rgb;

                // 1. Exposure (multiplicative, in stops)
                float expScale = pow(2.0, uExposure);
                c *= expScale;

                // 2. Brightness (additive, normalised -1..1)
                c += vec3(uBrightness);

                // 3. Contrast (multiplicative around 0.5)
                c = applyContrast(c, uContrast);

                // 4. Highlights / Shadows
                c = applyHighlightsShadows(c, uHighlights, uShadows);

                // 5. Saturation
                c = applySaturation(c, uSaturation);

                // 6. Temperature & Tint
                float rGain = 1.0 + uTemperature * 0.2;
                float bGain = 1.0 - uTemperature * 0.2;
                float gGain = 1.0 - uTint * 0.2;
                c.r *= rGain;
                c.g *= gGain;
                c.b *= bGain;

                // 7. HDR+ dynamic tone-mapping boost
                if (uHdr > 0.0) {
                    vec3 toneMapped = c / (c + vec3(1.0));
                    vec3 boosted = mix(c, toneMapped * 1.8, uHdr * 0.7);
                    c = mix(c, boosted, uHdr);
                }

                // 8. Brilliance midtone expansion
                if (uBrilliance != 0.0) {
                    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
                    float midtoneWeight = 1.0 - abs(luma - 0.5) * 2.0;
                    c += vec3(uBrilliance * 0.25 * midtoneWeight);
                }

                gl_FragColor = vec4(clamp(c, 0.0, 1.0), src.a);
            }
        """.trimIndent()
    }
}
