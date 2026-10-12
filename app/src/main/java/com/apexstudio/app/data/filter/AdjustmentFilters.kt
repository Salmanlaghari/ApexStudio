package com.apexstudio.app.data.filter

import android.opengl.GLES20
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter

/**
 * Custom GPUImage adjustment filters for the Video Adjustments v2 set
 * (photo-editor parity: Lightness, Grain, Clarity).
 *
 * All three follow the stock CyberAgent GPUImage pattern:
 * [GPUImageFilter.NO_FILTER_VERTEX_SHADER] + an inline GLSL ES 1.00
 * fragment shader, uniforms resolved in [onInit] and pushed in
 * [onInitialized]/setters. They run fully on the GPU, so slider drags
 * stay at 60fps with no per-frame allocations.
 */

/**
 * Lightness (-1..1): lifts or deepens the midtones while protecting the
 * extremes, so it feels different from plain brightness (which shifts
 * everything uniformly). 0 = no change.
 */
class GPUImageLightnessFilter(private var lightness: Float = 0f) : GPUImageFilter(
    NO_FILTER_VERTEX_SHADER, LIGHTNESS_FRAGMENT_SHADER
) {
    private var lightnessLocation: Int = 0

    override fun onInit() {
        super.onInit()
        lightnessLocation = GLES20.glGetUniformLocation(program, "lightness")
    }

    override fun onInitialized() {
        super.onInitialized()
        setLightness(lightness)
    }

    fun setLightness(lightness: Float) {
        this.lightness = lightness
        setFloat(lightnessLocation, lightness)
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
 * Film grain (0..1): adds hash-based monochrome grain. No texture
 * lookup, no RNG state — fully deterministic per pixel, zero cost
 * beyond one sin/fract.
 */
class GPUImageGrainFilter(private var grain: Float = 0f) : GPUImageFilter(
    NO_FILTER_VERTEX_SHADER, GRAIN_FRAGMENT_SHADER
) {
    private var grainLocation: Int = 0

    override fun onInit() {
        super.onInit()
        grainLocation = GLES20.glGetUniformLocation(program, "grain")
    }

    override fun onInitialized() {
        super.onInitialized()
        setGrain(grain)
    }

    fun setGrain(grain: Float) {
        this.grain = grain
        setFloat(grainLocation, grain)
    }

    companion object {
        private const val GRAIN_FRAGMENT_SHADER = """
            varying highp vec2 textureCoordinate;
            uniform sampler2D inputImageTexture;
            uniform lowp float grain;
            lowp float hash(vec2 p) {
                return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
            }
            void main() {
                lowp vec4 color = texture2D(inputImageTexture, textureCoordinate);
                lowp float g = hash(textureCoordinate * vec2(1920.0, 1080.0)) - 0.5;
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
    private var clarityLocation: Int = 0

    override fun onInit() {
        super.onInit()
        clarityLocation = GLES20.glGetUniformLocation(program, "clarity")
    }

    override fun onInitialized() {
        super.onInitialized()
        setClarity(clarity)
    }

    fun setClarity(clarity: Float) {
        this.clarity = clarity
        setFloat(clarityLocation, clarity)
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
