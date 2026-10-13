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
    private val brillianceProvider: (() -> Float)? = null,
    // Video Adjustments v2 (photo-editor parity) — live per-frame providers
    private val lightnessProvider: (() -> Float)? = null,
    private val whitesProvider: (() -> Float)? = null,
    private val blacksProvider: (() -> Float)? = null,
    private val hueProvider: (() -> Float)? = null,
    private val vignetteProvider: (() -> Float)? = null,
    private val grainProvider: (() -> Float)? = null,
    private val clarityProvider: (() -> Float)? = null,
    private val denoiseProvider: (() -> Float)? = null,
    private val sharpnessProvider: (() -> Float)? = null,
    private val fadeProvider: (() -> Float)? = null
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
            lightnessProvider = lightnessProvider,
            whitesProvider = whitesProvider,
            blacksProvider = blacksProvider,
            hueProvider = hueProvider,
            vignetteProvider = vignetteProvider,
            grainProvider = grainProvider,
            clarityProvider = clarityProvider,
            denoiseProvider = denoiseProvider,
            sharpnessProvider = sharpnessProvider,
            fadeProvider = fadeProvider,
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
        private val lightnessProvider: (() -> Float)?,
        private val whitesProvider: (() -> Float)?,
        private val blacksProvider: (() -> Float)?,
        private val hueProvider: (() -> Float)?,
        private val vignetteProvider: (() -> Float)?,
        private val grainProvider: (() -> Float)?,
        private val clarityProvider: (() -> Float)?,
        private val denoiseProvider: (() -> Float)?,
        private val sharpnessProvider: (() -> Float)?,
        private val fadeProvider: (() -> Float)?,
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
        // Video Adjustments v2
        private var lightness: Float = adjustments.lightness
        private var whites: Float = adjustments.whites
        private var blacks: Float = adjustments.blacks
        private var hue: Float = adjustments.hue
        private var vignette: Float = adjustments.vignette
        private var grain: Float = adjustments.grain
        private var clarity: Float = adjustments.clarity
        private var denoise: Float = adjustments.denoise
        private var sharpness: Float = adjustments.sharpness
        private var fade: Float = adjustments.fade
        private var texelWidth: Float = 0f
        private var texelHeight: Float = 0f
        // Actual input resolution for the grain pattern (>= 1, guarded
        // in configure() — never hardcoded to 1920x1080).
        private var inputResW: Float = 1f
        private var inputResH: Float = 1f

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

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            // Guard against zero dimensions: a zero texel would collapse
            // every denoise/sharpness tap onto the center pixel. The
            // spatial strengths are zeroed in drawFrame() when the texel
            // is invalid, so the failure mode is explicit, not silent.
            texelWidth = if (inputWidth > 0) 1f / inputWidth else 0f
            texelHeight = if (inputHeight > 0) 1f / inputHeight else 0f
            inputResW = inputWidth.coerceAtLeast(1).toFloat()
            inputResH = inputHeight.coerceAtLeast(1).toFloat()
            return Size(inputWidth, inputHeight)
        }

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
                lightnessProvider?.let { lightness = it() }
                whitesProvider?.let { whites = it() }
                blacksProvider?.let { blacks = it() }
                hueProvider?.let { hue = it() }
                vignetteProvider?.let { vignette = it() }
                grainProvider?.let { grain = it() }
                clarityProvider?.let { clarity = it() }
                denoiseProvider?.let { denoise = it() }
                sharpnessProvider?.let { sharpness = it() }
                fadeProvider?.let { fade = it() }

                // A zero input dimension yields a zero texel (see
                // configure()); disable the spatial ops explicitly rather
                // than letting every tap collapse onto the center pixel.
                val spatialValid = texelWidth > 0f && texelHeight > 0f

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
                glProgram.setFloatUniform("uLightness", lightness)
                glProgram.setFloatUniform("uWhites", whites)
                glProgram.setFloatUniform("uBlacks", blacks)
                glProgram.setFloatUniform("uHue", hue)
                glProgram.setFloatUniform("uVignette", vignette)
                glProgram.setFloatUniform("uGrain", grain)
                glProgram.setFloatUniform("uClarity", clarity)
                glProgram.setFloatUniform("uDenoise", if (spatialValid) denoise else 0f)
                glProgram.setFloatUniform("uSharpness", if (spatialValid) sharpness else 0f)
                glProgram.setFloatUniform("uFade", fade)
                glProgram.setFloatsUniform("uTexelSize", floatArrayOf(texelWidth, texelHeight))
                glProgram.setFloatsUniform("uResolution", floatArrayOf(inputResW, inputResH))
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
         *  - uLightness: -1..1 (midtone lift, distinct from brightness)
         *  - uWhites: -1..1 (white-point expand/compress)
         *  - uBlacks: -1..1 (black-point crush/lift)
         *  - uHue: -180..180 degrees rotation
         *  - uVignette: 0..1 border shading
         *  - uGrain: 0..1 film grain
         *  - uClarity: -1..1 midtone-localised contrast
         *  - uDenoise: 0..1 (4-tap soft blend; every tap runs through the
         *    adjusted color chain — see applyColorChain)
         *  - uSharpness: 0..1 (4-tap unsharp mask, same adjusted taps)
         *  - uGrain: 0..1 film grain (hash scaled by uResolution)
         *  - uFade: 0..1 (washed film look)
         *  - uTexelSize: 1/inputWidth, 1/inputHeight (0,0 when invalid —
         *    spatial ops are then disabled from Kotlin, see drawFrame)
         *  - uResolution: actual input resolution driving the grain hash
         *    (never hardcoded)
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
            uniform float uLightness;
            uniform float uWhites;
            uniform float uBlacks;
            uniform float uHue;
            uniform float uVignette;
            uniform float uGrain;
            uniform float uClarity;
            uniform float uDenoise;
            uniform float uSharpness;
            uniform float uFade;
            uniform vec2 uTexelSize;
            uniform vec2 uResolution;

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

            float hash12(vec2 p) {
                vec3 p3 = fract(vec3(p.xyx) * 0.1031);
                p3 += dot(p3, p3.yzx + 33.33);
                return fract((p3.x + p3.y) * p3.z);
            }

            vec3 applyHue(vec3 c, float degrees) {
                // Standard YIQ hue-rotation, written as explicit dot
                // products (avoids GLSL column-major confusion).
                float a = radians(degrees);
                float cosA = cos(a);
                float sinA = sin(a);
                vec3 r = vec3(0.299 + 0.701 * cosA + 0.168 * sinA,
                              0.587 - 0.587 * cosA + 0.330 * sinA,
                              0.114 - 0.114 * cosA - 0.497 * sinA);
                vec3 g = vec3(0.299 - 0.299 * cosA - 0.328 * sinA,
                              0.587 + 0.413 * cosA + 0.035 * sinA,
                              0.114 - 0.114 * cosA + 0.292 * sinA);
                vec3 b = vec3(0.299 - 0.299 * cosA + 1.250 * sinA,
                              0.587 - 0.587 * cosA - 1.050 * sinA,
                              0.114 + 0.886 * cosA - 0.203 * sinA);
                return vec3(dot(c, r), dot(c, g), dot(c, b));
            }

            // Full per-pixel color chain (steps 1-11, then 15-17 below).
            // Denoise and sharpness run every neighbour tap through this
            // same function, so the spatial ops sample the GRADED image
            // rather than the raw texture (single-pass correct — no extra
            // FBO needed).
            //
            // Performance note: this is 5x the color chain per pixel, but
            // ONLY while denoise/sharpness are active (both sliders are
            // opt-in, default 0, and the whole block is skipped otherwise).
            // Each stage also early-outs on default uniform values, so the
            // common case (a few sliders moved) stays far below the worst
            // case. The alternative — a two-pass FBO — would halve the ALU
            // but adds framebuffer management inside Media3's drawFrame;
            // revisit if 4K60 profiling shows this stage is hot.
            vec3 applyColorChain(vec3 c) {

                // 1. Exposure (multiplicative, in stops)
                float expScale = pow(2.0, uExposure);
                c *= expScale;

                // 2. Brightness (additive, normalised -1..1)
                c += vec3(uBrightness);

                // 3. Lightness (midtone lift — distinct from brightness)
                float lumaL = dot(c, vec3(0.2126, 0.7152, 0.0722));
                float midWL = clamp(1.0 - abs(lumaL - 0.5) * 2.0, 0.0, 1.0);
                c += vec3(uLightness * 0.4 * midWL);

                // 4. Contrast (multiplicative around 0.5)
                c = applyContrast(c, uContrast);

                // 5. Whites / Blacks (levels remap). When whites+blacks
                // collapse the input range the mapping is degenerate:
                // bypass the remap instead of dividing by a tiny epsilon
                // (which would amplify noise ~1000x before the clamp).
                float bIn = max(uBlacks, 0.0) * 0.3;
                float bOut = max(-uBlacks, 0.0) * 0.3;
                float wIn = 1.0 - max(uWhites, 0.0) * 0.3;
                float wOut = 1.0 + min(uWhites, 0.0) * 0.3;
                if (wIn - bIn > 0.0001) {
                    c = (c - vec3(bIn)) / (wIn - bIn);
                    c = vec3(bOut) + c * (wOut - bOut);
                }

                // 6. Highlights / Shadows
                c = applyHighlightsShadows(c, uHighlights, uShadows);

                // 7. Clarity (midtone-localised contrast)
                float lumaC = dot(c, vec3(0.2126, 0.7152, 0.0722));
                float midWC = clamp(1.0 - abs(lumaC - 0.5) * 2.0, 0.0, 1.0);
                vec3 clarityC = (c - 0.5) * (1.0 + uClarity * 0.7) + 0.5;
                c = mix(c, clarityC, midWC * 0.9);

                // 8. Saturation
                c = applySaturation(c, uSaturation);

                // 9. Hue rotation
                if (uHue != 0.0) {
                    c = applyHue(c, uHue);
                }

                // 10. Temperature & Tint
                float rGain = 1.0 + uTemperature * 0.2;
                float bGain = 1.0 - uTemperature * 0.2;
                float gGain = 1.0 - uTint * 0.2;
                c.r *= rGain;
                c.g *= gGain;
                c.b *= bGain;

                // 11. Vignette (border shading toward black)
                if (uVignette > 0.0) {
                    float d = distance(vTextureCoord, vec2(0.5));
                    c *= 1.0 - uVignette * smoothstep(0.25, 0.85, d);
                }

                // 15. Fade (washed film look: lift blacks, slight desat)
                if (uFade > 0.0) {
                    float lumaF = dot(c, vec3(0.2126, 0.7152, 0.0722));
                    c = mix(c, vec3(lumaF), uFade * 0.25);
                    c = c * (1.0 - uFade * 0.22) + vec3(uFade * 0.16);
                }

                // 16. HDR+ dynamic tone-mapping boost
                if (uHdr > 0.0) {
                    vec3 toneMapped = c / (c + vec3(1.0));
                    vec3 boosted = mix(c, toneMapped * 1.8, uHdr * 0.7);
                    c = mix(c, boosted, uHdr);
                }

                // 17. Brilliance midtone expansion
                if (uBrilliance != 0.0) {
                    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
                    float midtoneWeight = 1.0 - abs(luma - 0.5) * 2.0;
                    c += vec3(uBrilliance * 0.25 * midtoneWeight);
                }

                return c;
            }

            void main() {
                vec4 src = texture2D(uTexSampler, vTextureCoord);

                // 12/13. Denoise (soft 4-tap blend) + sharpness (4-tap
                // unsharp mask). Every tap runs through applyColorChain,
                // so both ops work on the ADJUSTED color — never on the
                // raw texture. This mirrors the GPUImage engine, where
                // each filter stage samples the previous stage's output.
                vec3 c;
                if (uDenoise > 0.0 || uSharpness > 0.0) {
                    vec3 t0 = applyColorChain(src.rgb);
                    vec3 t1 = applyColorChain(texture2D(uTexSampler, vTextureCoord + vec2(uTexelSize.x, 0.0)).rgb);
                    vec3 t2 = applyColorChain(texture2D(uTexSampler, vTextureCoord - vec2(uTexelSize.x, 0.0)).rgb);
                    vec3 t3 = applyColorChain(texture2D(uTexSampler, vTextureCoord + vec2(0.0, uTexelSize.y)).rgb);
                    vec3 t4 = applyColorChain(texture2D(uTexSampler, vTextureCoord - vec2(0.0, uTexelSize.y)).rgb);
                    vec3 soft = t0 * 0.4 + (t1 + t2 + t3 + t4) * 0.15;
                    c = t0;
                    if (uDenoise > 0.0) {
                        c = mix(c, soft, uDenoise * 0.85);
                    }
                    if (uSharpness > 0.0) {
                        c = c + (c - soft) * (uSharpness * 1.6);
                    }
                } else {
                    c = applyColorChain(src.rgb);
                }

                // 14. Film grain, hashed at the actual input resolution.
                if (uGrain > 0.0) {
                    float g = hash12(vTextureCoord * uResolution) - 0.5;
                    c += vec3(g * uGrain * 0.28);
                }

                gl_FragColor = vec4(clamp(c, 0.0, 1.0), src.a);
            }
        """.trimIndent()
    }
}
