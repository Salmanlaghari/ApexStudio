package com.apexstudio.app.data.fx

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/**
 * Real-time video FX wired into the Media3 GL pipeline — the FX-tool
 * counterpart of [com.apexstudio.app.data.filter.LutFilterGlEffect].
 *
 * Each [FxPreset] is a GLSL ES 2.0 fragment shader that takes the
 * frame plus three uniforms:
 *
 *  - `uIntensity` — 0..1 opacity of the effect (the FX panel slider)
 *  - `uTime` — seconds since playback start, drives animated FX
 *    (grain flicker, VHS roll, glitch slices)
 *  - `uTexel` — 1/width, 1/height of the frame, used by effects that
 *    need pixel-space maths (scanlines, blur, grain)
 *
 * The same shader program runs in the ExoPlayer preview effect chain
 * and in the Media3 Transformer export pipeline, so the exported MP4
 * matches exactly what the user saw on screen.
 */
@UnstableApi
class FxGlEffect(
    private val preset: FxPreset,
    private val intensity: Float
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return FxShaderProgram(preset, intensity.coerceIn(0f, 1f), useHdr)
    }

    @UnstableApi
    private class FxShaderProgram(
        private val preset: FxPreset,
        private val intensity: Float,
        useHdr: Boolean
    ) : BaseGlShaderProgram(useHdr, TEXTURE_POOL_CAPACITY) {

        private val glProgram: GlProgram
        private var inputWidth: Int = 0
        private var inputHeight: Int = 0

        init {
            glProgram = try {
                GlProgram(VERTEX_SHADER, fragmentFor(preset))
            } catch (e: Exception) {
                throw VideoFrameProcessingException("Failed to compile FX shader for ${preset.id}", e)
            }
            glProgram.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )
            val identity = GlUtil.create4x4IdentityMatrix()
            glProgram.setFloatsUniform("uTransformationMatrix", identity)
            // NDC [-1,1] → UV [0,1]; same mapping the LUT effect uses.
            val texMatrix = floatArrayOf(
                0.5f, 0f, 0f, 0f,
                0f, 0.5f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0.5f, 0.5f, 0f, 1f
            )
            glProgram.setFloatsUniform("uTexTransformationMatrix", texMatrix)
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            this.inputWidth = inputWidth
            this.inputHeight = inputHeight
            return Size(inputWidth, inputHeight)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                glProgram.use()
                glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                glProgram.setFloatUniform("uIntensity", intensity)
                glProgram.setFloatUniform("uTime", presentationTimeUs / 1_000_000f)
                glProgram.setFloatsUniform(
                    "uTexel",
                    floatArrayOf(
                        1f / inputWidth.coerceAtLeast(1),
                        1f / inputHeight.coerceAtLeast(1)
                    )
                )
                glProgram.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
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

        private fun fragmentFor(preset: FxPreset): String = when (preset) {
            FxPreset.VIGNETTE -> vignetteShader()
            FxPreset.FILM_GRAIN -> grainShader()
            FxPreset.VHS -> vhsShader()
            FxPreset.GLITCH -> glitchShader()
            FxPreset.PIXELATE -> pixelateShader()
            FxPreset.CHROMATIC -> chromaticShader()
            FxPreset.SCANLINES -> scanlinesShader()
            FxPreset.SOFT_BLUR -> blurShader()
            FxPreset.BLOOM -> bloomShader()
            FxPreset.SHAKE -> shakeShader()
            FxPreset.STROBE -> strobeShader()
            FxPreset.PRISM -> prismShader()
            FxPreset.ZOOM_BLUR -> zoomBlurShader()
            FxPreset.HALATION -> halationShader()

            // 1. Light & Optics
            FxPreset.LENS_FLARE -> lensFlareShader()
            FxPreset.LIGHT_LEAK -> lightLeakShader()
            FxPreset.ANAMORPHIC -> anamorphicShader()
            FxPreset.BOKEH_OVERLAY -> bokehOverlayShader()
            FxPreset.SPARKLE -> sparkleShader()
            FxPreset.SUN_BEAM -> sunBeamShader()
            FxPreset.GLOW_DIFFUSE -> glowDiffuseShader()

            // 2. Glitch & Digital
            FxPreset.BAD_TV -> badTvShader()
            FxPreset.PIXEL_SORT -> pixelSortShader()
            FxPreset.DATAMOSH -> datamoshShader()
            FxPreset.RGB_JITTER -> rgbJitterShader()
            FxPreset.CRT_PHOSPHOR -> crtPhosphorShader()
            FxPreset.INTERLACED -> interlacedShader()
            FxPreset.DIGITAL_DROP -> digitalDropShader()

            // 3. Retro & Film
            FxPreset.SEPIA_GRAIN -> sepiaGrainShader()
            FxPreset.DUST_SCRATCHES -> dustScratchesShader()
            FxPreset.FILM_DAMAGE -> filmDamageShader()
            FxPreset.BLEACH_BYPASS -> bleachBypassShader()
            FxPreset.TECHNICOLOR_STRIP -> technicolorStripShader()
            FxPreset.CROSS_PROCESS -> crossProcessShader()
            FxPreset.SOLARIZE -> solarizeShader()

            // 4. Blur & Motion
            FxPreset.RADIAL_BLUR -> radialBlurShader()
            FxPreset.TILT_SHIFT -> tiltShiftShader()
            FxPreset.MOTION_STREAK -> motionStreakShader()
            FxPreset.GHOSTING -> ghostingShader()
            FxPreset.SPIN_BLUR -> spinBlurShader()
            FxPreset.CAMERA_WOBBLE -> cameraWobbleShader()
            FxPreset.WHIP_PAN_FX -> whipPanFxShader()

            // 5. Stylize & Art
            FxPreset.HALFTONE -> halftoneShader()
            FxPreset.SKETCH_LINES -> sketchLinesShader()
            FxPreset.POSTERIZE -> posterizeShader()
            FxPreset.EDGE_NEON -> edgeNeonShader()
            FxPreset.THERMAL_VISION -> thermalVisionShader()
            FxPreset.NIGHT_VISION -> nightVisionShader()
            FxPreset.OIL_PAINT -> oilPaintShader()
            FxPreset.EMBOSS_RELIEF -> embossReliefShader()
            FxPreset.INVERT_FX -> invertFxShader()
            FxPreset.KALEIDOSCOPE -> kaleidoscopeShader()

            // 6. CapCut Viral & Special FX (20+ New Presets)
            FxPreset.HEART_BEAT -> heartBeatShader()
            FxPreset.EDGE_GLOW -> edgeGlowShader()
            FxPreset.SHIMMER_STAR -> shimmerStarShader()
            FxPreset.VERTIGO_DOLLY -> vertigoDollyShader()
            FxPreset.LIGHTNING_AURA -> lightningAuraShader()
            FxPreset.FIRE_EMBER -> fireEmberShader()
            FxPreset.CRYSTAL_FACET -> crystalFacetShader()
            FxPreset.DOUBLE_EXPOSURE -> doubleExposureShader()
            FxPreset.MATRIX_RAIN -> matrixRainShader()
            FxPreset.GLITCH_RGB_DISPLACE -> glitchRgbDisplaceShader()
            FxPreset.GLITCH_BLOCK -> glitchBlockShader()
            FxPreset.HOLOGRAM_GLITCH -> hologramGlitchShader()
            FxPreset.FILM_BURN -> filmBurnShader()
            FxPreset.SUPER_8_WARM -> super8WarmShader()
            FxPreset.SEPIA_FLICKER -> sepiaFlickerShader()
            FxPreset.WATER_RIPPLE -> waterRippleShader()
            FxPreset.GHOST_TRAIL -> ghostTrailShader()
            FxPreset.HEATWAVE_WARP -> heatwaveWarpShader()
            FxPreset.SPEED_LINES -> speedLinesShader()
            FxPreset.BLACK_HOLE_WARP -> blackHoleWarpShader()
            FxPreset.MIRROR_SPLIT -> mirrorSplitShader()
            FxPreset.NEON_WIREFRAME -> neonWireframeShader()
            FxPreset.COMIC_DOTS -> comicDotsShader()
            FxPreset.COLOR_ISOLATION -> colorIsolationShader()
        }

        /** Shared precision / varyings / uniforms for the FX shaders. */
        private val HEADER = """
            precision highp float;
            varying vec2 vTextureCoord;
            uniform sampler2D uTexSampler;
            uniform float uIntensity;
            uniform float uTime;
            uniform vec2 uTexel;
        """.trimIndent()

        /**
         * GLSL code fragment for a cheap hash of a 2D vector. [x] and
         * [y] are raw GLSL expressions (e.g. pixel coords derived from
         * varyings), so they are interpolated into the shader text.
         */
        private fun hash2(x: String, y: String): String =
            "fract(sin(dot(vec2($x, $y), vec2(12.9898, 78.233))) * 43758.5453)"

        private fun vignetteShader(): String = """
            $HEADER
            void main() {
                vec4 color = texture2D(uTexSampler, vTextureCoord);
                float d = distance(vTextureCoord, vec2(0.5, 0.5));
                float vig = smoothstep(0.32, 0.82, d);
                color.rgb *= 1.0 - vig * 0.8 * uIntensity;
                gl_FragColor = color;
            }
        """.trimIndent()

        private fun grainShader(): String = """
            $HEADER
            void main() {
                vec4 color = texture2D(uTexSampler, vTextureCoord);
                vec2 px = vTextureCoord / uTexel;
                float n = ${hash2("px.x + fract(uTime * 12.0) * 173.0", "px.y")};
                color.rgb += (n - 0.5) * 0.16 * uIntensity;
                gl_FragColor = vec4(color.rgb, color.a);
            }
        """.trimIndent()

        private fun vhsShader(): String = """
            $HEADER
            void main() {
                vec2 px = vTextureCoord / uTexel;
                vec2 uv = vTextureCoord;
                // Horizontal sync wobble.
                uv.x += sin(px.y * 0.08 + uTime * 3.0) * 0.0035 * uIntensity;
                vec4 color = texture2D(uTexSampler, uv);
                // Chromatic offset on the R / B channels.
                float shift = 0.0015 + 0.0025 * sin(uTime * 2.0);
                float rf = texture2D(uTexSampler, uv + vec2(shift, 0.0)).r;
                float bf = texture2D(uTexSampler, uv - vec2(shift, 0.0)).b;
                color.r = mix(color.r, rf, uIntensity);
                color.b = mix(color.b, bf, uIntensity);
                // Fine scanlines.
                float scan = 0.5 + 0.5 * sin(px.y * 1.4);
                color.rgb *= 0.88 + 0.12 * scan;
                // Rolling tracking bar.
                float bar = smoothstep(0.0, 0.06, abs(fract(uv.y * 3.0 - uTime * 0.6) - 0.5));
                color.rgb *= mix(0.55, 1.0, mix(1.0, bar, uIntensity));
                gl_FragColor = color;
            }
        """.trimIndent()

        private fun glitchShader(): String = """
            $HEADER
            void main() {
                vec2 px = vTextureCoord / uTexel;
                float slice = floor(px.y / 16.0);
                float t = floor(uTime * 7.0);
                float h1 = ${hash2("slice + t", "t * 0.31")};
                float h2 = ${hash2("slice + 7.0", "t * 0.73")};
                float h3 = ${hash2("slice + 3.0", "t * 0.17")};
                vec2 offset = vec2((h1 - 0.5) * 0.09, 0.0) * uIntensity;
                // Occasionally jump a whole slice sideways (VHS tracking tear).
                offset.x += step(0.9, h3) * (h2 - 0.5) * 0.18 * uIntensity;
                vec4 color = texture2D(uTexSampler, vTextureCoord + offset);
                // Per-slice RGB channel split, strongest on "hit" slices.
                vec4 rf = texture2D(uTexSampler, vTextureCoord + vec2(0.012 * uIntensity, 0.0));
                vec4 bf = texture2D(uTexSampler, vTextureCoord - vec2(0.012 * uIntensity, 0.0));
                color.r = mix(color.r, rf.r, step(0.85, h2));
                color.b = mix(color.b, bf.b, step(0.85, h1));
                gl_FragColor = vec4(color.rgb, color.a);
            }
        """.trimIndent()

        private fun pixelateShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                vec2 px = vTextureCoord / uTexel;
                // Block size shrinks as intensity rises (subtle → full pixelate).
                float blockPx = mix(48.0, 14.0, uIntensity);
                vec2 uv = (floor(px / blockPx) * blockPx + blockPx * 0.5) * uTexel;
                vec4 color = texture2D(uTexSampler, uv);
                gl_FragColor = mix(orig, color, uIntensity);
            }
        """.trimIndent()

        private fun chromaticShader(): String = """
            $HEADER
            void main() {
                vec4 color = texture2D(uTexSampler, vTextureCoord);
                vec2 dir = vTextureCoord - vec2(0.5, 0.5);
                float amt = mix(0.0010, 0.0060, uIntensity);
                float r = texture2D(uTexSampler, vTextureCoord + dir * amt).r;
                float b = texture2D(uTexSampler, vTextureCoord - dir * amt).b;
                gl_FragColor = vec4(mix(color.rgb, vec3(r, color.g, b), uIntensity), color.a);
            }
        """.trimIndent()

        private fun scanlinesShader(): String = """
            $HEADER
            void main() {
                vec4 color = texture2D(uTexSampler, vTextureCoord);
                vec2 px = vTextureCoord / uTexel;
                float stripe = 1.0 - step(0.55, fract(px.y / 3.0)) * 0.28 * uIntensity;
                // Gentle moving brightness band so the look is alive.
                float band = 1.0 + 0.06 * uIntensity * sin(px.y * 0.02 + uTime * 2.0);
                color.rgb *= stripe * band;
                gl_FragColor = color;
            }
        """.trimIndent()

        private fun blurShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                vec2 o = uTexel * 1.25;
                vec4 c = texture2D(uTexSampler, vTextureCoord) * 4.0;
                c += texture2D(uTexSampler, vTextureCoord + vec2( o.x, 0.0)) * 2.0;
                c += texture2D(uTexSampler, vTextureCoord + vec2(-o.x, 0.0)) * 2.0;
                c += texture2D(uTexSampler, vTextureCoord + vec2(0.0,  o.y)) * 2.0;
                c += texture2D(uTexSampler, vTextureCoord + vec2(0.0, -o.y)) * 2.0;
                c += texture2D(uTexSampler, vTextureCoord + o);
                c += texture2D(uTexSampler, vTextureCoord - o);
                c += texture2D(uTexSampler, vTextureCoord + vec2( o.x, -o.y));
                c += texture2D(uTexSampler, vTextureCoord + vec2(-o.x,  o.y));
                c /= 16.0;
                gl_FragColor = mix(orig, c, uIntensity);
            }
        """.trimIndent()

        private fun bloomShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                vec2 o = uTexel * 2.5;
                vec4 glow = vec4(0.0);
                glow += texture2D(uTexSampler, vTextureCoord + vec2( o.x, 0.0));
                glow += texture2D(uTexSampler, vTextureCoord + vec2(-o.x, 0.0));
                glow += texture2D(uTexSampler, vTextureCoord + vec2(0.0,  o.y));
                glow += texture2D(uTexSampler, vTextureCoord + vec2(0.0, -o.y));
                glow += texture2D(uTexSampler, vTextureCoord + vec2( o.x,  o.y));
                glow += texture2D(uTexSampler, vTextureCoord + vec2(-o.x, -o.y));
                glow /= 6.0;
                // High-pass threshold
                float lum = dot(glow.rgb, vec3(0.299, 0.587, 0.114));
                vec3 brightGlow = max(glow.rgb - vec3(0.45), vec3(0.0)) * 1.8;
                vec3 finalRgb = orig.rgb + brightGlow * uIntensity;
                gl_FragColor = vec4(finalRgb, orig.a);
            }
        """.trimIndent()

        private fun shakeShader(): String = """
            $HEADER
            void main() {
                float freq = uTime * 18.0;
                vec2 shakeOffset = vec2(
                    sin(freq * 1.3) * 0.008 + cos(freq * 2.1) * 0.004,
                    cos(freq * 1.1) * 0.006 + sin(freq * 2.7) * 0.003
                ) * uIntensity;
                vec4 color = texture2D(uTexSampler, clamp(vTextureCoord + shakeOffset, 0.0, 1.0));
                gl_FragColor = color;
            }
        """.trimIndent()

        private fun strobeShader(): String = """
            $HEADER
            void main() {
                vec4 color = texture2D(uTexSampler, vTextureCoord);
                float flash = sin(uTime * 16.0);
                float pulse = step(0.65, flash) * 0.45 * uIntensity;
                color.rgb = min(color.rgb + vec3(pulse), vec3(1.0));
                gl_FragColor = color;
            }
        """.trimIndent()

        private fun prismShader(): String = """
            $HEADER
            void main() {
                vec2 center = vec2(0.5, 0.5);
                vec2 toCenter = vTextureCoord - center;
                float dist = length(toCenter);
                float spread = 0.015 * uIntensity * dist;
                float r = texture2D(uTexSampler, vTextureCoord + toCenter * spread * 1.5).r;
                float g = texture2D(uTexSampler, vTextureCoord).g;
                float b = texture2D(uTexSampler, vTextureCoord - toCenter * spread * 1.5).b;
                gl_FragColor = vec4(r, g, b, 1.0);
            }
        """.trimIndent()

        private fun zoomBlurShader(): String = """
            $HEADER
            void main() {
                vec2 center = vec2(0.5, 0.5);
                vec2 toCenter = center - vTextureCoord;
                vec4 color = vec4(0.0);
                float total = 0.0;
                float factor = 0.05 * uIntensity;
                for (int i = 0; i < 8; i++) {
                    float t = float(i) / 7.0;
                    vec2 uv = vTextureCoord + toCenter * t * factor;
                    color += texture2D(uTexSampler, clamp(uv, 0.0, 1.0));
                    total += 1.0;
                }
                gl_FragColor = color / total;
            }
        """.trimIndent()

        private fun halationShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                vec2 o = uTexel * 3.0;
                vec4 glow = vec4(0.0);
                glow += texture2D(uTexSampler, vTextureCoord + vec2( o.x, 0.0));
                glow += texture2D(uTexSampler, vTextureCoord + vec2(-o.x, 0.0));
                glow += texture2D(uTexSampler, vTextureCoord + vec2(0.0,  o.y));
                glow += texture2D(uTexSampler, vTextureCoord + vec2(0.0, -o.y));
                glow /= 4.0;
                // Warm reddish/orange halation around highlights
                float bright = max(dot(glow.rgb, vec3(0.299, 0.587, 0.114)) - 0.6, 0.0);
                vec3 halationColor = vec3(1.0, 0.35, 0.1) * bright * 1.6 * uIntensity;
                gl_FragColor = vec4(orig.rgb + halationColor, orig.a);
            }
        """.trimIndent()

        // --- 1. Light & Optics ---
        private fun lensFlareShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                vec2 center = vec2(0.5, 0.5);
                vec2 sun = vec2(0.25 + 0.15 * sin(uTime * 0.4), 0.25);
                vec2 delta = vTextureCoord - sun;
                float d = length(delta);
                float flare = max(0.0, 1.0 - d * 2.5);
                flare = pow(flare, 3.0);
                float ring = max(0.0, 1.0 - abs(length(vTextureCoord - center) - 0.35) * 8.0);
                vec3 flareCol = vec3(1.0, 0.7, 0.4) * flare * 1.5 + vec3(0.3, 0.6, 1.0) * ring * 0.4;
                gl_FragColor = vec4(orig.rgb + flareCol * uIntensity, orig.a);
            }
        """.trimIndent()

        private fun lightLeakShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                vec2 uv = vTextureCoord;
                float t = uTime * 0.6;
                float leak1 = sin(uv.x * 2.0 + t) * cos(uv.y * 3.0 - t * 0.8) * 0.5 + 0.5;
                float leak2 = smoothstep(0.0, 0.8, uv.x + sin(t) * 0.2);
                vec3 warmLeak = vec3(1.0, 0.45, 0.15) * leak1 * leak2 * 1.4 * uIntensity;
                gl_FragColor = vec4(orig.rgb + warmLeak, orig.a);
            }
        """.trimIndent()

        private fun anamorphicShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                vec3 streak = vec3(0.0);
                for (int i = -5; i <= 5; i++) {
                    float offset = float(i) * 0.007 * uIntensity;
                    vec3 s = texture2D(uTexSampler, vTextureCoord + vec2(offset, 0.0)).rgb;
                    float luma = dot(s, vec3(0.299, 0.587, 0.114));
                    if (luma > 0.65) {
                        streak += vec3(0.2, 0.6, 1.0) * (luma - 0.65) * 0.35;
                    }
                }
                gl_FragColor = vec4(orig.rgb + streak, orig.a);
            }
        """.trimIndent()

        private fun bokehOverlayShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                vec2 uv = vTextureCoord;
                vec3 bokeh = vec3(0.0);
                for (int i = 0; i < 5; i++) {
                    float fi = float(i);
                    vec2 pos = vec2(fract(fi * 0.23 + uTime * 0.05), fract(fi * 0.67 + sin(fi + uTime * 0.03) * 0.2 + 0.5));
                    float d = length(uv - pos);
                    float orb = smoothstep(0.08, 0.04, d);
                    bokeh += vec3(0.9, 0.7, 1.0) * orb * 0.25;
                }
                gl_FragColor = vec4(orig.rgb + bokeh * uIntensity, orig.a);
            }
        """.trimIndent()

        private fun sparkleShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                vec2 px = vTextureCoord / uTexel;
                float luma = dot(orig.rgb, vec3(0.299, 0.587, 0.114));
                vec3 sparkle = vec3(0.0);
                if (luma > 0.75) {
                    float cross1 = max(0.0, 1.0 - abs(sin(px.x * 0.05 + uTime * 4.0)) * 4.0);
                    float cross2 = max(0.0, 1.0 - abs(cos(px.y * 0.05 + uTime * 4.0)) * 4.0);
                    sparkle = vec3(1.0, 0.95, 0.8) * (cross1 + cross2) * (luma - 0.75) * 2.0;
                }
                gl_FragColor = vec4(orig.rgb + sparkle * uIntensity, orig.a);
            }
        """.trimIndent()

        private fun sunBeamShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                vec2 sunPos = vec2(0.1, 0.05);
                vec2 delta = vTextureCoord - sunPos;
                float angle = atan(delta.y, delta.x);
                float rays = sin(angle * 16.0 + uTime * 0.5) * 0.5 + 0.5;
                rays = pow(rays, 3.0) * max(0.0, 1.0 - length(delta) * 0.8);
                vec3 beamCol = vec3(1.0, 0.9, 0.6) * rays * 0.8 * uIntensity;
                gl_FragColor = vec4(orig.rgb + beamCol, orig.a);
            }
        """.trimIndent()

        private fun glowDiffuseShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                vec4 blur = vec4(0.0);
                vec2 o = uTexel * 4.0 * uIntensity;
                blur += texture2D(uTexSampler, vTextureCoord + vec2(-o.x, -o.y));
                blur += texture2D(uTexSampler, vTextureCoord + vec2( o.x, -o.y));
                blur += texture2D(uTexSampler, vTextureCoord + vec2(-o.x,  o.y));
                blur += texture2D(uTexSampler, vTextureCoord + vec2( o.x,  o.y));
                blur /= 4.0;
                vec3 diffuse = orig.rgb + blur.rgb * 0.4 * uIntensity;
                gl_FragColor = vec4(diffuse, orig.a);
            }
        """.trimIndent()

        // --- 2. Glitch & Digital ---
        private fun badTvShader(): String = """
            $HEADER
            void main() {
                vec2 uv = vTextureCoord;
                float wobble = sin(uv.y * 40.0 + uTime * 15.0) * 0.004 * uIntensity;
                uv.x += wobble;
                vec4 color = texture2D(uTexSampler, uv);
                float noise = ${hash2("uv.x * 50.0 + uTime * 10.0", "uv.y * 50.0")};
                color.rgb += (noise - 0.5) * 0.15 * uIntensity;
                gl_FragColor = color;
            }
        """.trimIndent()

        private fun pixelSortShader(): String = """
            $HEADER
            void main() {
                vec2 uv = vTextureCoord;
                vec4 orig = texture2D(uTexSampler, uv);
                float luma = dot(orig.rgb, vec3(0.299, 0.587, 0.114));
                if (luma > 0.45) {
                    float sortOffset = (luma - 0.45) * 0.08 * uIntensity;
                    uv.y = clamp(uv.y - sortOffset, 0.0, 1.0);
                }
                vec4 sorted = texture2D(uTexSampler, uv);
                gl_FragColor = mix(orig, sorted, uIntensity);
            }
        """.trimIndent()

        private fun datamoshShader(): String = """
            $HEADER
            void main() {
                vec2 uv = vTextureCoord;
                vec2 block = floor(uv * vec2(32.0, 18.0));
                float rnd = ${hash2("block.x + floor(uTime * 3.0)", "block.y")};
                vec2 displace = vec2(rnd - 0.5, 0.0) * 0.08 * uIntensity;
                vec4 col = texture2D(uTexSampler, clamp(uv + displace, 0.0, 1.0));
                gl_FragColor = col;
            }
        """.trimIndent()

        private fun rgbJitterShader(): String = """
            $HEADER
            void main() {
                vec2 uv = vTextureCoord;
                float jit = sin(uTime * 35.0) * 0.008 * uIntensity;
                float r = texture2D(uTexSampler, uv + vec2(jit, 0.0)).r;
                float g = texture2D(uTexSampler, uv).g;
                float b = texture2D(uTexSampler, uv - vec2(jit, 0.0)).b;
                gl_FragColor = vec4(r, g, b, 1.0);
            }
        """.trimIndent()

        private fun crtPhosphorShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                vec2 px = vTextureCoord / uTexel;
                float mask = mod(floor(px.x), 3.0);
                vec3 phosphor = orig.rgb;
                if (mask < 0.5) phosphor *= vec3(1.3, 0.7, 0.7);
                else if (mask < 1.5) phosphor *= vec3(0.7, 1.3, 0.7);
                else phosphor *= vec3(0.7, 0.7, 1.3);
                gl_FragColor = vec4(mix(orig.rgb, phosphor, uIntensity * 0.8), orig.a);
            }
        """.trimIndent()

        private fun interlacedShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                vec2 px = vTextureCoord / uTexel;
                float line = mod(floor(px.y), 2.0);
                vec2 uv = vTextureCoord;
                if (line < 0.5) {
                    uv.x += 0.004 * uIntensity;
                }
                vec4 shifted = texture2D(uTexSampler, uv);
                gl_FragColor = mix(orig, shifted, uIntensity);
            }
        """.trimIndent()

        private fun digitalDropShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                float levels = mix(256.0, 6.0, uIntensity);
                vec3 crushed = floor(orig.rgb * levels + 0.5) / levels;
                gl_FragColor = vec4(crushed, orig.a);
            }
        """.trimIndent()

        // --- 3. Retro & Film ---
        private fun sepiaGrainShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                float luma = dot(orig.rgb, vec3(0.299, 0.587, 0.114));
                vec3 sepia = vec3(luma * 1.2, luma * 0.95, luma * 0.75);
                vec2 px = vTextureCoord / uTexel;
                float grain = (${hash2("px.x + uTime * 10.0", "px.y")} - 0.5) * 0.18;
                vec3 res = mix(orig.rgb, sepia + grain, uIntensity);
                gl_FragColor = vec4(res, orig.a);
            }
        """.trimIndent()

        private fun dustScratchesShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                vec2 uv = vTextureCoord;
                float t = floor(uTime * 14.0);
                float scratchX = ${hash2("t * 17.1", "4.0")};
                float scratch = smoothstep(0.002, 0.0, abs(uv.x - scratchX));
                float dust = step(0.998, ${hash2("uv.x * 200.0 + t", "uv.y * 200.0")});
                vec3 damaged = orig.rgb * (1.0 - scratch * 0.5 * uIntensity) - dust * 0.4 * uIntensity;
                gl_FragColor = vec4(max(damaged, 0.0), orig.a);
            }
        """.trimIndent()

        private fun filmDamageShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                vec2 uv = vTextureCoord;
                float burn = smoothstep(0.4, 0.0, uv.y + sin(uv.x * 10.0 + uTime * 2.0) * 0.05);
                vec3 burnColor = vec3(1.0, 0.4, 0.05) * burn * 1.8 * uIntensity;
                gl_FragColor = vec4(orig.rgb + burnColor, orig.a);
            }
        """.trimIndent()

        private fun bleachBypassShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                float luma = dot(orig.rgb, vec3(0.299, 0.587, 0.114));
                vec3 desat = vec3(luma);
                vec3 blended = mix(2.0 * orig.rgb * desat, 1.0 - 2.0 * (1.0 - orig.rgb) * (1.0 - desat), step(0.5, luma));
                gl_FragColor = vec4(mix(orig.rgb, blended, uIntensity), orig.a);
            }
        """.trimIndent()

        private fun technicolorStripShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                vec3 col = orig.rgb;
                col.r = pow(col.r, 0.9) * 1.25;
                col.g = pow(col.g, 0.95) * 1.15;
                col.b = pow(col.b, 1.1) * 0.9;
                gl_FragColor = vec4(mix(orig.rgb, col, uIntensity), orig.a);
            }
        """.trimIndent()

        private fun crossProcessShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                vec3 c = orig.rgb;
                c.r = c.r * 1.3 - 0.1;
                c.g = c.g * 1.1;
                c.b = c.b * 0.8 + 0.15;
                gl_FragColor = vec4(mix(orig.rgb, clamp(c, 0.0, 1.0), uIntensity), orig.a);
            }
        """.trimIndent()

        private fun solarizeShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                vec3 inv = abs(orig.rgb - 0.5) * 2.0;
                gl_FragColor = vec4(mix(orig.rgb, inv, uIntensity), orig.a);
            }
        """.trimIndent()

        // --- 4. Blur & Motion ---
        private fun radialBlurShader(): String = """
            $HEADER
            void main() {
                vec2 center = vec2(0.5, 0.5);
                vec2 d = vTextureCoord - center;
                vec2 perp = vec2(-d.y, d.x);
                vec4 col = vec4(0.0);
                float samples = 6.0;
                for (float i = -3.0; i <= 3.0; i += 1.0) {
                    vec2 uv = vTextureCoord + perp * (i / samples) * 0.04 * uIntensity;
                    col += texture2D(uTexSampler, clamp(uv, 0.0, 1.0));
                }
                gl_FragColor = col / 7.0;
            }
        """.trimIndent()

        private fun tiltShiftShader(): String = """
            $HEADER
            void main() {
                vec2 uv = vTextureCoord;
                float dist = abs(uv.y - 0.5);
                float blurAmt = smoothstep(0.12, 0.45, dist) * 0.015 * uIntensity;
                vec4 col = vec4(0.0);
                col += texture2D(uTexSampler, uv + vec2(0.0, -blurAmt));
                col += texture2D(uTexSampler, uv + vec2(0.0,  blurAmt));
                col += texture2D(uTexSampler, uv + vec2(-blurAmt, 0.0));
                col += texture2D(uTexSampler, uv + vec2( blurAmt, 0.0));
                gl_FragColor = col / 4.0;
            }
        """.trimIndent()

        private fun motionStreakShader(): String = """
            $HEADER
            void main() {
                vec2 dir = vec2(0.018, 0.006) * uIntensity;
                vec4 col = vec4(0.0);
                for (int i = 0; i < 6; i++) {
                    float f = float(i) / 5.0;
                    col += texture2D(uTexSampler, clamp(vTextureCoord + dir * f, 0.0, 1.0));
                }
                gl_FragColor = col / 6.0;
            }
        """.trimIndent()

        private fun ghostingShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                vec2 ghostUv = vTextureCoord - vec2(0.02 * sin(uTime * 2.0), 0.01) * uIntensity;
                vec4 ghost = texture2D(uTexSampler, clamp(ghostUv, 0.0, 1.0));
                gl_FragColor = mix(orig, ghost, 0.4 * uIntensity);
            }
        """.trimIndent()

        private fun spinBlurShader(): String = """
            $HEADER
            void main() {
                vec2 center = vec2(0.5, 0.5);
                vec2 uv = vTextureCoord - center;
                vec4 col = vec4(0.0);
                for (int i = -3; i <= 3; i++) {
                    float a = float(i) * 0.03 * uIntensity;
                    mat2 rot = mat2(cos(a), -sin(a), sin(a), cos(a));
                    col += texture2D(uTexSampler, clamp(rot * uv + center, 0.0, 1.0));
                }
                gl_FragColor = col / 7.0;
            }
        """.trimIndent()

        private fun cameraWobbleShader(): String = """
            $HEADER
            void main() {
                vec2 uv = vTextureCoord;
                uv.x += (sin(uTime * 2.1) * 0.006 + sin(uTime * 4.7) * 0.003) * uIntensity;
                uv.y += (cos(uTime * 1.8) * 0.006 + cos(uTime * 3.9) * 0.003) * uIntensity;
                gl_FragColor = texture2D(uTexSampler, clamp(uv, 0.0, 1.0));
            }
        """.trimIndent()

        private fun whipPanFxShader(): String = """
            $HEADER
            void main() {
                vec4 col = vec4(0.0);
                for (int i = -4; i <= 4; i++) {
                    float offset = float(i) * 0.012 * uIntensity;
                    col += texture2D(uTexSampler, clamp(vTextureCoord + vec2(offset, 0.0), 0.0, 1.0));
                }
                gl_FragColor = col / 9.0;
            }
        """.trimIndent()

        // --- 5. Stylize & Art ---
        private fun halftoneShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                vec2 px = vTextureCoord / uTexel;
                vec2 cell = mod(px, 12.0) - vec2(6.0);
                float radius = dot(orig.rgb, vec3(0.299, 0.587, 0.114)) * 6.0;
                float dotVal = smoothstep(radius, radius - 1.0, length(cell));
                vec3 c = mix(vec3(0.1), vec3(0.95), dotVal);
                gl_FragColor = vec4(mix(orig.rgb, c, uIntensity), orig.a);
            }
        """.trimIndent()

        private fun sketchLinesShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                vec2 o = uTexel * 2.0;
                float left = dot(texture2D(uTexSampler, vTextureCoord - vec2(o.x, 0.0)).rgb, vec3(0.333));
                float right = dot(texture2D(uTexSampler, vTextureCoord + vec2(o.x, 0.0)).rgb, vec3(0.333));
                float up = dot(texture2D(uTexSampler, vTextureCoord - vec2(0.0, o.y)).rgb, vec3(0.333));
                float down = dot(texture2D(uTexSampler, vTextureCoord + vec2(0.0, o.y)).rgb, vec3(0.333));
                float edge = abs(right - left) + abs(down - up);
                vec3 sketch = vec3(1.0 - edge * 4.0);
                gl_FragColor = vec4(mix(orig.rgb, sketch, uIntensity), orig.a);
            }
        """.trimIndent()

        private fun posterizeShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                float n = mix(32.0, 4.0, uIntensity);
                vec3 post = floor(orig.rgb * n + 0.5) / n;
                gl_FragColor = vec4(mix(orig.rgb, post, uIntensity), orig.a);
            }
        """.trimIndent()

        private fun edgeNeonShader(): String = """
            $HEADER
            void main() {
                vec2 o = uTexel * 2.0;
                float left = dot(texture2D(uTexSampler, vTextureCoord - vec2(o.x, 0.0)).rgb, vec3(0.333));
                float right = dot(texture2D(uTexSampler, vTextureCoord + vec2(o.x, 0.0)).rgb, vec3(0.333));
                float up = dot(texture2D(uTexSampler, vTextureCoord - vec2(0.0, o.y)).rgb, vec3(0.333));
                float down = dot(texture2D(uTexSampler, vTextureCoord + vec2(0.0, o.y)).rgb, vec3(0.333));
                float edge = (abs(right - left) + abs(down - up)) * 4.0;
                vec3 neon = vec3(edge * 0.2, edge * 0.9, edge * 1.0);
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                gl_FragColor = vec4(mix(orig.rgb, neon, uIntensity), orig.a);
            }
        """.trimIndent()

        private fun thermalVisionShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                float val = dot(orig.rgb, vec3(0.299, 0.587, 0.114));
                vec3 heat;
                if (val < 0.25) heat = mix(vec3(0.0, 0.0, 0.4), vec3(0.5, 0.0, 0.5), val * 4.0);
                else if (val < 0.5) heat = mix(vec3(0.5, 0.0, 0.5), vec3(1.0, 0.1, 0.0), (val - 0.25) * 4.0);
                else if (val < 0.75) heat = mix(vec3(1.0, 0.1, 0.0), vec3(1.0, 0.9, 0.0), (val - 0.5) * 4.0);
                else heat = mix(vec3(1.0, 0.9, 0.0), vec3(1.0, 1.0, 1.0), (val - 0.75) * 4.0);
                gl_FragColor = vec4(mix(orig.rgb, heat, uIntensity), orig.a);
            }
        """.trimIndent()

        private fun nightVisionShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                float luma = dot(orig.rgb, vec3(0.299, 0.587, 0.114));
                vec2 px = vTextureCoord / uTexel;
                float noise = (${hash2("px.x + uTime * 20.0", "px.y")} - 0.5) * 0.2;
                float d = length(vTextureCoord - vec2(0.5, 0.5));
                float vig = smoothstep(0.48, 0.2, d);
                vec3 nv = vec3(0.1, luma * 1.5 + noise, 0.2) * vig;
                gl_FragColor = vec4(mix(orig.rgb, nv, uIntensity), orig.a);
            }
        """.trimIndent()

        private fun oilPaintShader(): String = """
            $HEADER
            void main() {
                vec2 uv = vTextureCoord;
                vec2 px = floor(uv * 120.0) / 120.0;
                vec4 col = texture2D(uTexSampler, px);
                vec4 orig = texture2D(uTexSampler, uv);
                gl_FragColor = mix(orig, col, uIntensity);
            }
        """.trimIndent()

        private fun embossReliefShader(): String = """
            $HEADER
            void main() {
                vec2 o = uTexel * 2.0;
                vec4 c1 = texture2D(uTexSampler, vTextureCoord - o);
                vec4 c2 = texture2D(uTexSampler, vTextureCoord + o);
                vec3 diff = (c1.rgb - c2.rgb) * 2.0 + vec3(0.5);
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                gl_FragColor = vec4(mix(orig.rgb, diff, uIntensity), orig.a);
            }
        """.trimIndent()

        private fun invertFxShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                vec3 inv = 1.0 - orig.rgb;
                gl_FragColor = vec4(mix(orig.rgb, inv, uIntensity), orig.a);
            }
        """.trimIndent()

        private fun kaleidoscopeShader(): String = """
            $HEADER
            void main() {
                vec2 uv = vTextureCoord - vec2(0.5, 0.5);
                float a = atan(uv.y, uv.x);
                float r = length(uv);
                a = mod(a, 3.14159 / 3.0);
                a = abs(a - 3.14159 / 6.0);
                vec2 kUv = vec2(cos(a), sin(a)) * r + vec2(0.5, 0.5);
                vec4 col = texture2D(uTexSampler, clamp(kUv, 0.0, 1.0));
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                gl_FragColor = mix(orig, col, uIntensity);
            }
        """.trimIndent()

        // --- 24 CAPCUT-STYLE VIRAL VIDEO EFFECTS ---

        private fun heartBeatShader(): String = """
            $HEADER
            void main() {
                float pulse = pow(max(0.0, sin(uTime * 4.5)), 6.0) * 0.12 * uIntensity;
                vec2 center = vec2(0.5, 0.5);
                vec2 uv = (vTextureCoord - center) * (1.0 - pulse) + center;
                vec2 redOffset = vec2(pulse * 0.06, 0.0);
                vec4 colR = texture2D(uTexSampler, clamp(uv + redOffset, 0.0, 1.0));
                vec4 colG = texture2D(uTexSampler, clamp(uv, 0.0, 1.0));
                vec4 colB = texture2D(uTexSampler, clamp(uv - redOffset, 0.0, 1.0));
                vec4 finalCol = vec4(colR.r, colG.g, colB.b, colG.a);
                finalCol.rgb += vec3(0.08, 0.01, 0.03) * pulse * 8.0;
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                gl_FragColor = mix(orig, finalCol, uIntensity);
            }
        """.trimIndent()

        private fun edgeGlowShader(): String = """
            $HEADER
            void main() {
                vec2 step = uTexel * 2.0;
                vec3 c = texture2D(uTexSampler, vTextureCoord).rgb;
                vec3 l = texture2D(uTexSampler, vTextureCoord - vec2(step.x, 0.0)).rgb;
                vec3 r = texture2D(uTexSampler, vTextureCoord + vec2(step.x, 0.0)).rgb;
                vec3 u = texture2D(uTexSampler, vTextureCoord - vec2(0.0, step.y)).rgb;
                vec3 d = texture2D(uTexSampler, vTextureCoord + vec2(0.0, step.y)).rgb;
                vec3 diff = abs(r - l) + abs(d - u);
                float edge = length(diff);
                vec3 neon = vec3(0.0, 0.9, 1.0) * edge * 2.5 * uIntensity;
                gl_FragColor = vec4(c + neon, 1.0);
            }
        """.trimIndent()

        private fun shimmerStarShader(): String = """
            $HEADER
            void main() {
                vec4 color = texture2D(uTexSampler, vTextureCoord);
                float lum = dot(color.rgb, vec3(0.299, 0.587, 0.114));
                float sparkle = 0.0;
                vec2 grid = fract(vTextureCoord * 12.0) - 0.5;
                float d = length(grid);
                float crossRay = smoothstep(0.03, 0.0, abs(grid.x)) * smoothstep(0.3, 0.0, abs(grid.y))
                               + smoothstep(0.03, 0.0, abs(grid.y)) * smoothstep(0.3, 0.0, abs(grid.x));
                float rotPhase = sin(uTime * 3.0 + vTextureCoord.x * 20.0);
                if (lum > 0.65 && rotPhase > 0.3) {
                    sparkle = crossRay * (lum - 0.65) * 4.0;
                }
                color.rgb += vec3(1.0, 0.95, 0.8) * sparkle * uIntensity;
                gl_FragColor = color;
            }
        """.trimIndent()

        private fun vertigoDollyShader(): String = """
            $HEADER
            void main() {
                vec2 uv = vTextureCoord - vec2(0.5, 0.5);
                float cycle = sin(uTime * 2.2);
                float zoom = 1.0 + cycle * 0.28 * uIntensity;
                float dist = length(uv);
                float warp = 1.0 + dist * dist * cycle * 0.5 * uIntensity;
                vec2 nuv = uv * zoom * warp + vec2(0.5, 0.5);
                vec4 warped = texture2D(uTexSampler, clamp(nuv, 0.0, 1.0));
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                gl_FragColor = mix(orig, warped, uIntensity);
            }
        """.trimIndent()

        private fun lightningAuraShader(): String = """
            $HEADER
            void main() {
                vec4 color = texture2D(uTexSampler, vTextureCoord);
                float noiseVal = fract(sin(dot(vTextureCoord * 40.0 + vec2(uTime * 15.0), vec2(12.9898, 78.233))) * 43758.5453);
                float bolt = smoothstep(0.96, 1.0, noiseVal) * step(0.65, fract(uTime * 3.0));
                vec3 electric = vec3(0.5, 0.8, 1.0) * bolt * 2.5 * uIntensity;
                color.rgb += electric;
                gl_FragColor = color;
            }
        """.trimIndent()

        private fun fireEmberShader(): String = """
            $HEADER
            void main() {
                vec4 color = texture2D(uTexSampler, vTextureCoord);
                // Warm flame gradient near bottom
                float flameGrad = (1.0 - vTextureCoord.y) * 0.35 * uIntensity;
                color.r += flameGrad * 1.2;
                color.g += flameGrad * 0.45;
                // Rising spark particles
                vec2 pCoord = vec2(vTextureCoord.x * 25.0, vTextureCoord.y * 20.0 + uTime * 6.0);
                float sparkNoise = fract(sin(dot(floor(pCoord), vec2(12.9898, 78.233))) * 43758.5453);
                float spark = smoothstep(0.92, 1.0, sparkNoise) * (1.0 - fract(pCoord.y));
                color.rgb += vec3(1.0, 0.6, 0.1) * spark * 2.0 * uIntensity;
                gl_FragColor = color;
            }
        """.trimIndent()

        private fun crystalFacetShader(): String = """
            $HEADER
            void main() {
                vec2 center = vec2(0.5, 0.5);
                vec2 delta = vTextureCoord - center;
                float angle = atan(delta.y, delta.x);
                float radius = length(delta);
                float sides = 8.0;
                float slice = 6.28318 / sides;
                float facetAngle = floor(angle / slice + 0.5) * slice;
                vec2 facetUv = vec2(cos(facetAngle), sin(facetAngle)) * radius * 0.85 + center;
                vec4 facetCol = texture2D(uTexSampler, clamp(facetUv, 0.0, 1.0));
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                facetCol.rgb += vec3(0.1, 0.2, 0.3) * sin(facetAngle * 3.0);
                gl_FragColor = mix(orig, facetCol, uIntensity);
            }
        """.trimIndent()

        private fun doubleExposureShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                vec2 ghostUv = vTextureCoord + vec2(sin(uTime * 0.8) * 0.05, cos(uTime * 0.6) * 0.04);
                vec4 ghost = texture2D(uTexSampler, clamp(ghostUv, 0.0, 1.0));
                vec3 screenBlended = 1.0 - (1.0 - orig.rgb) * (1.0 - ghost.rgb * 0.6);
                gl_FragColor = vec4(mix(orig.rgb, screenBlended, uIntensity), orig.a);
            }
        """.trimIndent()

        private fun matrixRainShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                vec2 grid = vec2(floor(vTextureCoord.x * 60.0), floor((vTextureCoord.y + uTime * 0.8) * 40.0));
                float randVal = fract(sin(dot(grid, vec2(12.9898, 78.233))) * 43758.5453);
                float stream = step(0.75, randVal) * 0.45;
                vec3 matrixTint = vec3(orig.r * 0.3, orig.g * 1.3 + stream, orig.b * 0.3);
                gl_FragColor = vec4(mix(orig.rgb, matrixTint, uIntensity), orig.a);
            }
        """.trimIndent()

        private fun glitchRgbDisplaceShader(): String = """
            $HEADER
            void main() {
                float sliceY = floor(vTextureCoord.y * 30.0);
                float rnd = fract(sin(sliceY + floor(uTime * 14.0)) * 43758.5453);
                float shift = step(0.85, rnd) * (rnd - 0.5) * 0.08 * uIntensity;
                float r = texture2D(uTexSampler, clamp(vTextureCoord + vec2(shift + 0.015 * uIntensity, 0.0), 0.0, 1.0)).r;
                float g = texture2D(uTexSampler, clamp(vTextureCoord + vec2(shift, 0.0), 0.0, 1.0)).g;
                float b = texture2D(uTexSampler, clamp(vTextureCoord + vec2(shift - 0.015 * uIntensity, 0.0), 0.0, 1.0)).b;
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                gl_FragColor = mix(orig, vec4(r, g, b, orig.a), uIntensity);
            }
        """.trimIndent()

        private fun glitchBlockShader(): String = """
            $HEADER
            void main() {
                vec2 blockSize = vec2(0.04, 0.04);
                vec2 blockId = floor(vTextureCoord / blockSize);
                float blockRand = fract(sin(dot(blockId, vec2(12.9898, 78.233)) + floor(uTime * 8.0)) * 43758.5453);
                vec2 displacedUv = vTextureCoord;
                if (blockRand > 0.82) {
                    displacedUv += vec2((blockRand - 0.5) * 0.12 * uIntensity, 0.0);
                }
                vec4 col = texture2D(uTexSampler, clamp(displacedUv, 0.0, 1.0));
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                gl_FragColor = mix(orig, col, uIntensity);
            }
        """.trimIndent()

        private fun hologramGlitchShader(): String = """
            $HEADER
            void main() {
                vec4 col = texture2D(uTexSampler, vTextureCoord);
                float scanline = sin(vTextureCoord.y * 500.0 + uTime * 20.0) * 0.15;
                float flicker = 0.9 + 0.1 * sin(uTime * 40.0);
                vec3 holo = vec3(col.r * 0.2, col.g * 0.85 + scanline, col.b * 1.2 + scanline) * flicker;
                gl_FragColor = vec4(mix(col.rgb, holo, uIntensity), col.a);
            }
        """.trimIndent()

        private fun filmBurnShader(): String = """
            $HEADER
            void main() {
                vec4 col = texture2D(uTexSampler, vTextureCoord);
                float burnShape = sin(vTextureCoord.x * 6.0 + uTime * 2.0) * cos(vTextureCoord.y * 4.0 - uTime);
                float burnEdge = smoothstep(0.2, 0.8, burnShape * (1.0 - vTextureCoord.x));
                vec3 burnColor = mix(vec3(1.0, 0.2, 0.0), vec3(1.0, 0.8, 0.1), burnEdge);
                col.rgb += burnColor * burnEdge * 1.5 * uIntensity;
                gl_FragColor = col;
            }
        """.trimIndent()

        private fun super8WarmShader(): String = """
            $HEADER
            void main() {
                vec4 col = texture2D(uTexSampler, vTextureCoord);
                // Warm golden tone
                vec3 warm = vec3(col.r * 1.2 + 0.08, col.g * 1.05 + 0.04, col.b * 0.75);
                // Circular 8mm vignette
                float d = distance(vTextureCoord, vec2(0.5, 0.5));
                float vig = smoothstep(0.4, 0.75, d);
                warm *= (1.0 - vig * 0.6);
                gl_FragColor = vec4(mix(col.rgb, warm, uIntensity), col.a);
            }
        """.trimIndent()

        private fun sepiaFlickerShader(): String = """
            $HEADER
            void main() {
                vec4 col = texture2D(uTexSampler, vTextureCoord);
                float gray = dot(col.rgb, vec3(0.299, 0.587, 0.114));
                vec3 sepia = vec3(gray * 1.2, gray * 0.95, gray * 0.75);
                float flicker = 0.88 + 0.12 * sin(uTime * 30.0 + fract(sin(uTime * 123.4) * 43758.5));
                sepia *= flicker;
                gl_FragColor = vec4(mix(col.rgb, sepia, uIntensity), col.a);
            }
        """.trimIndent()

        private fun waterRippleShader(): String = """
            $HEADER
            void main() {
                vec2 c = vec2(0.5, 0.5);
                vec2 delta = vTextureCoord - c;
                float dist = length(delta);
                float wave = sin(dist * 40.0 - uTime * 6.0) * 0.02 * uIntensity;
                vec2 rippleUv = vTextureCoord + normalize(delta) * wave;
                vec4 col = texture2D(uTexSampler, clamp(rippleUv, 0.0, 1.0));
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                gl_FragColor = mix(orig, col, uIntensity);
            }
        """.trimIndent()

        private fun ghostTrailShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                vec2 center = vec2(0.5, 0.5);
                vec2 uv1 = (vTextureCoord - center) * 1.05 + center;
                vec2 uv2 = (vTextureCoord - center) * 1.10 + center;
                vec4 g1 = texture2D(uTexSampler, clamp(uv1, 0.0, 1.0));
                vec4 g2 = texture2D(uTexSampler, clamp(uv2, 0.0, 1.0));
                vec3 blended = orig.rgb * 0.6 + g1.rgb * 0.25 + g2.rgb * 0.15;
                gl_FragColor = vec4(mix(orig.rgb, blended, uIntensity), orig.a);
            }
        """.trimIndent()

        private fun heatwaveWarpShader(): String = """
            $HEADER
            void main() {
                float warpX = sin(vTextureCoord.y * 35.0 + uTime * 5.0) * 0.015 * uIntensity;
                float warpY = cos(vTextureCoord.x * 25.0 + uTime * 4.0) * 0.01 * uIntensity;
                vec2 warpedUv = vTextureCoord + vec2(warpX, warpY);
                vec4 col = texture2D(uTexSampler, clamp(warpedUv, 0.0, 1.0));
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                gl_FragColor = mix(orig, col, uIntensity);
            }
        """.trimIndent()

        private fun speedLinesShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                vec2 delta = vTextureCoord - vec2(0.5, 0.5);
                float angle = atan(delta.y, delta.x);
                float dist = length(delta);
                float lines = step(0.65, fract(sin(angle * 35.0 + uTime * 8.0) * 43758.5453));
                float mask = smoothstep(0.2, 0.65, dist);
                vec3 finalCol = mix(orig.rgb, vec3(1.0, 1.0, 1.0), lines * mask * 0.8 * uIntensity);
                gl_FragColor = vec4(finalCol, orig.a);
            }
        """.trimIndent()

        private fun blackHoleWarpShader(): String = """
            $HEADER
            void main() {
                vec2 delta = vTextureCoord - vec2(0.5, 0.5);
                float dist = length(delta);
                float angle = atan(delta.y, delta.x);
                float twist = (1.0 - smoothstep(0.0, 0.6, dist)) * 3.14159 * uIntensity;
                float newAngle = angle + twist;
                vec2 warpedUv = vec2(cos(newAngle), sin(newAngle)) * dist + vec2(0.5, 0.5);
                vec4 col = texture2D(uTexSampler, clamp(warpedUv, 0.0, 1.0));
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                gl_FragColor = mix(orig, col, uIntensity);
            }
        """.trimIndent()

        private fun mirrorSplitShader(): String = """
            $HEADER
            void main() {
                vec2 uv = abs(vTextureCoord - vec2(0.5, 0.5));
                vec4 mirrored = texture2D(uTexSampler, uv + vec2(0.25, 0.25));
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                gl_FragColor = mix(orig, mirrored, uIntensity);
            }
        """.trimIndent()

        private fun neonWireframeShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                if (vTextureCoord.y > 0.5) {
                    float depth = (vTextureCoord.y - 0.5) * 2.0;
                    float z = 1.0 / max(0.01, depth);
                    float gridX = abs(fract((vTextureCoord.x - 0.5) * z * 8.0) - 0.5);
                    float gridY = abs(fract(z * 4.0 - uTime * 3.0) - 0.5);
                    float wire = smoothstep(0.4, 0.48, max(gridX, gridY));
                    vec3 cyberNeon = vec3(0.9, 0.0, 1.0) * wire * uIntensity;
                    orig.rgb += cyberNeon;
                }
                gl_FragColor = orig;
            }
        """.trimIndent()

        private fun comicDotsShader(): String = """
            $HEADER
            void main() {
                vec4 col = texture2D(uTexSampler, vTextureCoord);
                vec2 dotCoord = fract(vTextureCoord * 75.0) - 0.5;
                float lum = dot(col.rgb, vec3(0.299, 0.587, 0.114));
                float radius = (1.0 - lum) * 0.55;
                float ink = step(radius, length(dotCoord));
                vec3 popColor = floor(col.rgb * 4.0) / 4.0 * ink;
                gl_FragColor = vec4(mix(col.rgb, popColor, uIntensity), col.a);
            }
        """.trimIndent()

        private fun colorIsolationShader(): String = """
            $HEADER
            void main() {
                vec4 col = texture2D(uTexSampler, vTextureCoord);
                float gray = dot(col.rgb, vec3(0.299, 0.587, 0.114));
                // Isolate vibrant warm reds & pinks
                float isRed = max(0.0, col.r - max(col.g, col.b));
                float mask = smoothstep(0.12, 0.35, isRed);
                vec3 isolated = mix(vec3(gray), col.rgb, mask);
                gl_FragColor = vec4(mix(col.rgb, isolated, uIntensity), col.a);
            }
        """.trimIndent()
    }
}
