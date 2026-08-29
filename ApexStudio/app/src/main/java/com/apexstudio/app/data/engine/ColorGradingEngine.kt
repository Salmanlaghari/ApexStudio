package com.apexstudio.app.data.engine

import android.content.Context
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ColorGradingState(
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val shadows: Float = 0f,
    val midtones: Float = 0f,
    val highlights: Float = 0f,
    val selectedLut: String = "none"
)

class ColorGradingEngine {

    private val _colorState = MutableStateFlow(ColorGradingState())
    val colorState: StateFlow<ColorGradingState> = _colorState

    private var glProgram: Int = 0
    private var vbo: Int = 0
    private var vao: Int = 0
    private var initialized = false

    private val vertexShaderCode = """
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTexCoord;
        void main() {
            vTexCoord = aTexCoord;
            gl_Position = aPosition;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D uTexture;
        uniform float uBrightness;
        uniform float uContrast;
        uniform float uSaturation;
        uniform float uShadows;
        uniform float uMidtones;
        uniform float uHighlights;

        vec3 applyCurve(vec3 color, float shadows, float midtones, float highlights) {
            float s = clamp(shadows, -1.0, 1.0);
            float m = clamp(midtones, -1.0, 1.0);
            float h = clamp(highlights, -1.0, 1.0);
            float shadowLift = 1.0 + s * 0.3;
            float midContrast = 1.0 + m * 0.5;
            float highlightGain = 1.0 + h * 0.3;
            return color * shadowLift * midContrast * highlightGain;
        }

        void main() {
            vec4 texColor = texture2D(uTexture, vTexCoord);
            vec3 color = texColor.rgb;

            color = (color - 0.5) * uContrast + 0.5 + uBrightness;
            color = applyCurve(color, uShadows, uMidtones, uHighlights);

            float luminance = dot(color, vec3(0.2126, 0.7152, 0.0722));
            color = mix(vec3(luminance), color, uSaturation);

            gl_FragColor = vec4(clamp(color, 0.0, 1.0), texColor.a);
        }
    """.trimIndent()

    fun initGL() {
        try {
            val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
            val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

            glProgram = GLES20.glCreateProgram()
            GLES20.glAttachShader(glProgram, vertexShader)
            GLES20.glAttachShader(glProgram, fragmentShader)
            GLES20.glLinkProgram(glProgram)

            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(glProgram, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] != GLES20.GL_TRUE) {
                Log.e("ColorGradingEngine", "Failed to link GL program")
                return
            }

            GLES20.glGenBuffers(1, intArrayOf(vbo), 0)
            GLES20.glGenVertexArrays(1, intArrayOf(vao), 0)
            initialized = true
        } catch (e: Exception) {
            Log.e("ColorGradingEngine", "GL init error", e)
        }
    }

    fun applyBrightness(value: Float) {
        _colorState.update { it.copy(brightness = value) }
    }

    fun applyContrast(value: Float) {
        _colorState.update { it.copy(contrast = value.coerceIn(0f, 3f)) }
    }

    fun applySaturation(value: Float) {
        _colorState.update { it.copy(saturation = value.coerceIn(0f, 3f)) }
    }

    fun applyShadows(value: Float) {
        _colorState.update { it.copy(shadows = value) }
    }

    fun applyMidtones(value: Float) {
        _colorState.update { it.copy(midtones = value) }
    }

    fun applyHighlights(value: Float) {
        _colorState.update { it.copy(highlights = value) }
    }

    fun applyLut(lutId: String) {
        _colorState.update { it.copy(selectedLut = lutId) }
    }

    fun getColorState(): ColorGradingState = _colorState.value

    fun renderFrame(textureId: Int, width: Int, height: Int): Int {
        if (!initialized || glProgram == 0) return textureId
        return textureId
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        return shader
    }

    fun release() {
        if (glProgram != 0) {
            GLES20.glDeleteProgram(glProgram)
            glProgram = 0
        }
        if (vbo != 0) {
            GLES20.glDeleteBuffers(1, intArrayOf(vbo), 0)
            vbo = 0
        }
        if (vao != 0) {
            GLES20.glDeleteVertexArrays(1, intArrayOf(vao), 0)
            vao = 0
        }
        initialized = false
    }
}
