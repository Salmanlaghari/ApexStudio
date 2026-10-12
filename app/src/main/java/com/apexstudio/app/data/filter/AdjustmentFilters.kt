package com.apexstudio.app.data.filter

import android.opengl.GLES20
import android.util.Log
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter

/**
 * Custom GPUImage adjustment filters for the Video Adjustments v2 set
 * (photo-editor parity: Lightness, Grain, Clarity, Whites/Blacks,
 * Denoise, Sharpness).
 *
 * All follow the stock CyberAgent GPUImage pattern:
 * [GPUImageFilter.NO_FILTER_VERTEX_SHADER] + an inline GLSL ES 1.00
 * fragment shader, uniforms resolved in [onInit] and pushed in
 * [onInitialized]/setters. They run fully on the GPU, so slider drags
 * stay at 60fps with no per-frame allocations.
 *
 * WYSIWYG: the Whites/Blacks, Denoise, Sharpness and Grain shaders below
 * mirror the Media3 [com.apexstudio.app.data.adjust.AdjustmentsGlEffect]
 * maths exactly (same scales, same tap weights, same blend factors, same
 * hash, same degenerate-range handling), so the GPUImage preview path and
 * the Media3 export path render identically.
 *
 * Size-dependent uniforms (texelSize / resolution) are pushed via
 * [GPUImageFilter.runOnDraw], which executes after `glUseProgram` on the
 * next draw — so the uniform always lands on THIS filter's program, never
 * on a stale or unbound one. The pending size is retained and re-pushed
 * from [onInit] as well, covering the onOutputSizeChanged-before-onInit
 * ordering.
 */

private const val ADJUST_TAG = "AdjustmentFilters"

/**
 * Resolves a GLSL uniform location, logging when the driver reports -1
 * (uniform optimized out or misspelled). Callers MUST guard every
 * setter with `!= -1` — writing to -1 is silently ignored by GL.
 */
private fun glUniformLocation(program: Int, name: String): Int {
    val loc = GLES20.glGetUniformLocation(program, name)
    if (loc == -1) {
        Log.w(ADJUST_TAG, "uniform '$name' not active (location -1); setter calls will be skipped")
    }
    return loc
}

/**
 * Base for filters with a size-dependent `vec2` uniform (texelSize or
 * resolution). Handles the pending-size + runOnDraw push pattern in one
 * place so subclasses cannot get it wrong.
 */
private abstract class SizeAwareAdjustmentFilter(
    vertexShader: String,
    fragmentShader: String
) : GPUImageFilter(vertexShader, fragmentShader) {

    private var pendingW = 0
    private var pendingH = 0

    override fun onOutputSizeChanged(width: Int, height: Int) {
        super.onOutputSizeChanged(width, height)
        if (width > 0 && height > 0) {
            pendingW = width
            pendingH = height
            pushSizeUniforms()
        }
    }

    /**
     * Re-push the retained size (if any). Called from [onInit] after the
     * uniform locations are resolved, so a size that arrived before init
     * is not lost.
     */
    protected fun repushSizeUniforms() = pushSizeUniforms()

    private fun pushSizeUniforms() {
        val w = pendingW
        val h = pendingH
        if (w <= 0 || h <= 0) return
        // Runs after glUseProgram() on the next draw: the uniform is
        // guaranteed to target this filter's own program.
        runOnDraw { applySizeUniforms(w, h) }
    }

    /** Pushes the size-derived uniform(s); runs on the GL thread with this filter's program bound. */
    protected abstract fun applySizeUniforms(width: Int, height: Int)
}

/**
 * Lightness (-1..1): lifts or deepens the midtones while protecting the
 * extremes, so it feels different from plain brightness (which shifts
 * everything uniformly). 0 = no change.
 */
class GPUImageLightnessFilter(private var lightness: Float = 0f) : GPUImageFilter(
    NO_FILTER_VERTEX_SHADER, LIGHTNESS_FRAGMENT_SHADER
) {
    private var lightnessLocation: Int = -1

    override fun onInit() {
        super.onInit()
        lightnessLocation = glUniformLocation(program, "lightness")
    }

    override fun onInitialized() {
        super.onInitialized()
        setLightness(lightness)
    }

    fun setLightness(lightness: Float) {
        this.lightness = lightness
        if (lightnessLocation != -1) {
            setFloat(lightnessLocation, lightness)
        }
    }

    companion object {
        private const val LIGHTNESS_FRAGMENT_SHADER = """
            varying highp vec2 textureCoordinate;
            uniform sampler2D inputImageTexture;
            uniform lowp float lightness;
            void main() {
                lowp vec4 color = texture2D(inputImageTexture, textureCoordinate);
                lowp float luma = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
                // Midtone weight peaks at 0.5 luma, falls to 0 at extremes.
                lowp float midWeight = clamp(1.0 - abs(luma - 0.5) * 2.0, 0.0, 1.0);
                color.rgb += vec3(lightness * 0.4 * midWeight);
                gl_FragColor = vec4(clamp(color.rgb, 0.0, 1.0), color.w);
            }
        """
    }
}

/**
 * Film grain (0..1): adds hash-based monochrome grain. Uses the same
 * hash12 as AdjustmentsGlEffect (not the old sin/fract hash), hashed
 * at the ACTUAL output resolution — identical grain on both paths.
 * No texture lookup, no RNG state — fully deterministic per pixel.
 */
class GPUImageGrainFilter(private var grain: Float = 0f) : SizeAwareAdjustmentFilter(
    NO_FILTER_VERTEX_SHADER, GRAIN_FRAGMENT_SHADER
) {
    private var grainLocation: Int = -1
    private var resolutionLocation: Int = -1

    override fun onInit() {
        super.onInit()
        grainLocation = glUniformLocation(program, "grain")
        resolutionLocation = glUniformLocation(program, "resolution")
        repushSizeUniforms()
    }

    override fun onInitialized() {
        super.onInitialized()
        setGrain(grain)
    }

    override fun applySizeUniforms(width: Int, height: Int) {
        if (resolutionLocation != -1) {
            GLES20.glUniform2f(resolutionLocation, width.toFloat(), height.toFloat())
        }
    }

    fun setGrain(grain: Float) {
        this.grain = grain
        if (grainLocation != -1) {
            setFloat(grainLocation, grain)
        }
    }

    companion object {
        private const val GRAIN_FRAGMENT_SHADER = """
            varying highp vec2 textureCoordinate;
            uniform sampler2D inputImageTexture;
            uniform lowp float grain;
            uniform highp vec2 resolution;
            // Identical to AdjustmentsGlEffect.hash12 — same pattern,
            // so the Media3 and GPUImage paths match pixel-for-pixel.
            lowp float hash12(vec2 p) {
                vec3 p3 = fract(vec3(p.xyx) * 0.1031);
                p3 += dot(p3, p3.yzx + 33.33);
                return fract((p3.x + p3.y) * p3.z);
            }
            void main() {
                lowp vec4 color = texture2D(inputImageTexture, textureCoordinate);
                lowp float g = hash12(textureCoordinate * resolution) - 0.5;
                color.rgb += vec3(g * grain * 0.28);
                gl_FragColor = vec4(clamp(color.rgb, 0.0, 1.0), color.w);
            }
        """
    }
}

/**
 * Clarity (-1..1): midtone-localised contrast. Positive values add
 * punch to the midtones (the classic "clarity" look); negative values
 * soften them. Unlike global contrast, shadows and highlights are
 * left alone.
 */
class GPUImageClarityFilter(private var clarity: Float = 0f) : GPUImageFilter(
    NO_FILTER_VERTEX_SHADER, CLARITY_FRAGMENT_SHADER
) {
    private var clarityLocation: Int = -1

    override fun onInit() {
        super.onInit()
        clarityLocation = glUniformLocation(program, "clarity")
    }

    override fun onInitialized() {
        super.onInitialized()
        setClarity(clarity)
    }

    fun setClarity(clarity: Float) {
        this.clarity = clarity
        if (clarityLocation != -1) {
            setFloat(clarityLocation, clarity)
        }
    }

    companion object {
        private const val CLARITY_FRAGMENT_SHADER = """
            varying highp vec2 textureCoordinate;
            uniform sampler2D inputImageTexture;
            uniform lowp float clarity;
            void main() {
                lowp vec4 color = texture2D(inputImageTexture, textureCoordinate);
                lowp float luma = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
                lowp float midWeight = clamp(1.0 - abs(luma - 0.5) * 2.0, 0.0, 1.0);
                lowp vec3 contrasted = (color.rgb - 0.5) * (1.0 + clarity * 0.7) + 0.5;
                color.rgb = mix(color.rgb, contrasted, midWeight * 0.9);
                gl_FragColor = vec4(clamp(color.rgb, 0.0, 1.0), color.w);
            }
        """
    }
}

/**
 * Whites / Blacks (-1..1 each): levels remap with the EXACT maths of
 * the Media3 shader (AdjustmentsGlEffect step 5) — same 0.3 scale, same
 * degenerate-range bypass, and NO [0,1] clamp of the normalized ratio
 * (the stock GPUImageLevelsFilter clamps, which diverges from the shader
 * for negative whites/blacks and out-of-range graded values).
 * Positive whites expand highlights; negative compress. Positive blacks
 * crush shadows; negative lift.
 */
class GPUImageWhitesBlacksFilter(
    private var blacks: Float = 0f,
    private var whites: Float = 0f
) : GPUImageFilter(
    NO_FILTER_VERTEX_SHADER, LEVELS_FRAGMENT_SHADER
) {
    private var blacksLocation: Int = -1
    private var whitesLocation: Int = -1

    override fun onInit() {
        super.onInit()
        blacksLocation = glUniformLocation(program, "blacks")
        whitesLocation = glUniformLocation(program, "whites")
    }

    override fun onInitialized() {
        super.onInitialized()
        setBlacks(blacks)
        setWhites(whites)
    }

    fun setBlacks(blacks: Float) {
        this.blacks = blacks
        if (blacksLocation != -1) {
            setFloat(blacksLocation, blacks)
        }
    }

    fun setWhites(whites: Float) {
        this.whites = whites
        if (whitesLocation != -1) {
            setFloat(whitesLocation, whites)
        }
    }

    companion object {
        private const val LEVELS_FRAGMENT_SHADER = """
            varying highp vec2 textureCoordinate;
            uniform sampler2D inputImageTexture;
            uniform lowp float blacks;
            uniform lowp float whites;
            void main() {
                lowp vec4 color = texture2D(inputImageTexture, textureCoordinate);
                // Bit-for-bit the AdjustmentsGlEffect step-5 formula.
                lowp float bIn = max(blacks, 0.0) * 0.3;
                lowp float bOut = max(-blacks, 0.0) * 0.3;
                lowp float wIn = 1.0 - max(whites, 0.0) * 0.3;
                lowp float wOut = 1.0 + min(whites, 0.0) * 0.3;
                if (wIn - bIn > 0.0001) {
                    color.rgb = (color.rgb - vec3(bIn)) / (wIn - bIn);
                    color.rgb = vec3(bOut) + color.rgb * (wOut - bOut);
                }
                gl_FragColor = vec4(color.rgb, color.w);
            }
        """
    }
}

/**
 * Denoise (0..1): soft 4-tap blend mirroring the Media3 shader
 * (AdjustmentsGlEffect) exactly — same 0.4/0.15 tap weights, same
 * `mix(c, soft, denoise * 0.85)` blend factor. Replaces the old
 * arbitrary `GPUImageBilateralBlurFilter(1 + d * 4)` mapping so both
 * paths agree (WYSIWYG).
 */
class GPUImageDenoiseFilter(private var denoise: Float = 0f) : SizeAwareAdjustmentFilter(
    NO_FILTER_VERTEX_SHADER, DENOISE_FRAGMENT_SHADER
) {
    private var denoiseLocation: Int = -1
    private var texelLocation: Int = -1

    override fun onInit() {
        super.onInit()
        denoiseLocation = glUniformLocation(program, "denoise")
        texelLocation = glUniformLocation(program, "texelSize")
        repushSizeUniforms()
    }

    override fun onInitialized() {
        super.onInitialized()
        setDenoise(denoise)
    }

    override fun applySizeUniforms(width: Int, height: Int) {
        if (texelLocation != -1) {
            GLES20.glUniform2f(texelLocation, 1f / width, 1f / height)
        }
    }

    fun setDenoise(denoise: Float) {
        this.denoise = denoise
        if (denoiseLocation != -1) {
            setFloat(denoiseLocation, denoise)
        }
    }

    companion object {
        private const val DENOISE_FRAGMENT_SHADER = """
            varying highp vec2 textureCoordinate;
            uniform sampler2D inputImageTexture;
            uniform lowp float denoise;
            uniform highp vec2 texelSize;
            void main() {
                lowp vec4 color = texture2D(inputImageTexture, textureCoordinate);
                lowp vec3 soft = color.rgb * 0.4;
                soft += texture2D(inputImageTexture, textureCoordinate + vec2(texelSize.x, 0.0)).rgb * 0.15;
                soft += texture2D(inputImageTexture, textureCoordinate - vec2(texelSize.x, 0.0)).rgb * 0.15;
                soft += texture2D(inputImageTexture, textureCoordinate + vec2(0.0, texelSize.y)).rgb * 0.15;
                soft += texture2D(inputImageTexture, textureCoordinate - vec2(0.0, texelSize.y)).rgb * 0.15;
                color.rgb = mix(color.rgb, soft, denoise * 0.85);
                gl_FragColor = vec4(clamp(color.rgb, 0.0, 1.0), color.w);
            }
        """
    }
}

/**
 * Sharpness (0..1): 4-tap unsharp mask mirroring the Media3 shader
 * exactly — same blur kernel, same x1.6 scaling (the old code used
 * `GPUImageSharpenFilter(sharpness * 3.0)`, which neither matched the
 * shader's scaling nor its kernel).
 */
class GPUImageSharpnessFilter(private var sharpness: Float = 0f) : SizeAwareAdjustmentFilter(
    NO_FILTER_VERTEX_SHADER, SHARPNESS_FRAGMENT_SHADER
) {
    private var sharpnessLocation: Int = -1
    private var texelLocation: Int = -1

    override fun onInit() {
        super.onInit()
        sharpnessLocation = glUniformLocation(program, "sharpness")
        texelLocation = glUniformLocation(program, "texelSize")
        repushSizeUniforms()
    }

    override fun onInitialized() {
        super.onInitialized()
        setSharpness(sharpness)
    }

    override fun applySizeUniforms(width: Int, height: Int) {
        if (texelLocation != -1) {
            GLES20.glUniform2f(texelLocation, 1f / width, 1f / height)
        }
    }

    fun setSharpness(sharpness: Float) {
        this.sharpness = sharpness
        if (sharpnessLocation != -1) {
            setFloat(sharpnessLocation, sharpness)
        }
    }

    companion object {
        private const val SHARPNESS_FRAGMENT_SHADER = """
            varying highp vec2 textureCoordinate;
            uniform sampler2D inputImageTexture;
            uniform lowp float sharpness;
            uniform highp vec2 texelSize;
            void main() {
                lowp vec4 color = texture2D(inputImageTexture, textureCoordinate);
                lowp vec3 soft = color.rgb * 0.4;
                soft += texture2D(inputImageTexture, textureCoordinate + vec2(texelSize.x, 0.0)).rgb * 0.15;
                soft += texture2D(inputImageTexture, textureCoordinate - vec2(texelSize.x, 0.0)).rgb * 0.15;
                soft += texture2D(inputImageTexture, textureCoordinate + vec2(0.0, texelSize.y)).rgb * 0.15;
                soft += texture2D(inputImageTexture, textureCoordinate - vec2(0.0, texelSize.y)).rgb * 0.15;
                color.rgb = color.rgb + (color.rgb - soft) * (sharpness * 1.6);
                gl_FragColor = vec4(clamp(color.rgb, 0.0, 1.0), color.w);
            }
        """
    }
}

/**
 * Combined Denoise + Sharpness (0..1 each): used when BOTH sliders are
 * active. Mirrors the Media3 shader's exact staging — the sharpness
 * unsharp mask uses the SAME pre-denoise blur (`soft`) and operates on
 * the denoised color:
 *
 *   soft = blur(c); d = mix(c, soft, denoise * 0.85);
 *   out  = d + (d - soft) * (sharpness * 1.6);
 *
 * Two separate stages would (incorrectly) blur the already-denoised
 * image for the sharpness pass, diverging from the shader.
 */
class GPUImageDenoiseSharpnessFilter(
    private var denoise: Float = 0f,
    private var sharpness: Float = 0f
) : SizeAwareAdjustmentFilter(
    NO_FILTER_VERTEX_SHADER, DENOISE_SHARPNESS_FRAGMENT_SHADER
) {
    private var denoiseLocation: Int = -1
    private var sharpnessLocation: Int = -1
    private var texelLocation: Int = -1

    override fun onInit() {
        super.onInit()
        denoiseLocation = glUniformLocation(program, "denoise")
        sharpnessLocation = glUniformLocation(program, "sharpness")
        texelLocation = glUniformLocation(program, "texelSize")
        repushSizeUniforms()
    }

    override fun onInitialized() {
        super.onInitialized()
        setDenoise(denoise)
        setSharpness(sharpness)
    }

    override fun applySizeUniforms(width: Int, height: Int) {
        if (texelLocation != -1) {
            GLES20.glUniform2f(texelLocation, 1f / width, 1f / height)
        }
    }

    fun setDenoise(denoise: Float) {
        this.denoise = denoise
        if (denoiseLocation != -1) {
            setFloat(denoiseLocation, denoise)
        }
    }

    fun setSharpness(sharpness: Float) {
        this.sharpness = sharpness
        if (sharpnessLocation != -1) {
            setFloat(sharpnessLocation, sharpness)
        }
    }

    companion object {
        private const val DENOISE_SHARPNESS_FRAGMENT_SHADER = """
            varying highp vec2 textureCoordinate;
            uniform sampler2D inputImageTexture;
            uniform lowp float denoise;
            uniform lowp float sharpness;
            uniform highp vec2 texelSize;
            void main() {
                lowp vec4 color = texture2D(inputImageTexture, textureCoordinate);
                lowp vec3 soft = color.rgb * 0.4;
                soft += texture2D(inputImageTexture, textureCoordinate + vec2(texelSize.x, 0.0)).rgb * 0.15;
                soft += texture2D(inputImageTexture, textureCoordinate - vec2(texelSize.x, 0.0)).rgb * 0.15;
                soft += texture2D(inputImageTexture, textureCoordinate + vec2(0.0, texelSize.y)).rgb * 0.15;
                soft += texture2D(inputImageTexture, textureCoordinate - vec2(0.0, texelSize.y)).rgb * 0.15;
                // Same staging as the Media3 shader: denoise first, then
                // sharpen the denoised color against the SAME blur.
                lowp vec3 d = mix(color.rgb, soft, denoise * 0.85);
                d = d + (d - soft) * (sharpness * 1.6);
                gl_FragColor = vec4(clamp(d, 0.0, 1.0), color.w);
            }
        """
    }
}
