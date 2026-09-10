package com.apexstudio.app.data.gl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLUtils
import android.util.Log
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-performance 3D LUT texture loader for OpenGL ES 3.0 and GLES 2.0.
 *
 * Supports:
 * 1. Adobe / DaVinci `.cube` 3D LUT files -> Native OpenGL ES 3.0 `GL_TEXTURE_3D`
 * 2. Neutral identity fallback 3D texture
 * 3. 2D strip LUT generation for older devices or GLES 2.0 fallback
 * 4. HaldCLUT `.png` image format support (e.g. 512x512 = 64^3 LUT)
 *
 * Memory optimization:
 * - Direct NIO ByteBuffers avoid Java GC churn
 * - Allocations are freed immediately after uploading to GPU memory
 * - Prevents OutOfMemoryError (OOM) on large 64x64x64 3D LUTs
 */
object Lut3DTextureLoader {

    private const val TAG = "Lut3DTextureLoader"

    data class Lut3DData(
        val size: Int,
        val buffer: ByteBuffer,
        val format: Int = GLES30.GL_RGB
    )

    /**
     * Parses a `.cube` file stream into direct memory for GLES30.glTexImage3D.
     */
    fun parseCubeLut(inputStream: InputStream): Lut3DData {
        val reader = BufferedReader(InputStreamReader(inputStream), 32768)
        var lutSize = 0
        val floatValues = mutableListOf<Float>()

        reader.useLines { lines ->
            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#")) continue

                if (line.startsWith("LUT_3D_SIZE")) {
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size >= 2) {
                        lutSize = parts[1].toIntOrNull() ?: 0
                    }
                    continue
                }

                if (line.startsWith("TITLE") || line.startsWith("DOMAIN_MIN") || line.startsWith("DOMAIN_MAX")) {
                    continue
                }

                val tokens = line.split("\\s+".toRegex())
                if (tokens.size >= 3) {
                    val r = tokens[0].toFloatOrNull()
                    val g = tokens[1].toFloatOrNull()
                    val b = tokens[2].toFloatOrNull()
                    if (r != null && g != null && b != null) {
                        floatValues.add(r)
                        floatValues.add(g)
                        floatValues.add(b)
                    }
                }
            }
        }

        if (lutSize <= 0) {
            val totalEntries = floatValues.size / 3
            lutSize = Math.cbrt(totalEntries.toDouble()).toInt()
        }

        val totalPixels = lutSize * lutSize * lutSize
        val byteBuffer = ByteBuffer.allocateDirect(totalPixels * 3)
            .order(ByteOrder.nativeOrder())

        val maxIndex = minOf(floatValues.size, totalPixels * 3)
        for (i in 0 until maxIndex) {
            val clamped = (floatValues[i].coerceIn(0f, 1f) * 255f).toInt().toByte()
            byteBuffer.put(clamped)
        }
        byteBuffer.rewind()

        return Lut3DData(size = lutSize, buffer = byteBuffer)
    }

    /**
     * Parses a neutral identity 3D LUT (R, G, B ramp).
     */
    fun createIdentityLut3D(size: Int = 16): Lut3DData {
        val totalPixels = size * size * size
        val buffer = ByteBuffer.allocateDirect(totalPixels * 3)
            .order(ByteOrder.nativeOrder())

        val step = 255f / (size - 1).coerceAtLeast(1)
        for (b in 0 until size) {
            val bVal = (b * step).toInt().toByte()
            for (g in 0 until size) {
                val gVal = (g * step).toInt().toByte()
                for (r in 0 until size) {
                    val rVal = (r * step).toInt().toByte()
                    buffer.put(rVal)
                    buffer.put(gVal)
                    buffer.put(bVal)
                }
            }
        }
        buffer.rewind()
        return Lut3DData(size = size, buffer = buffer)
    }

    /**
     * Uploads a [Lut3DData] to a native OpenGL ES 3.0 3D texture (`GL_TEXTURE_3D`).
     * Returns the generated texture ID, or 0 on failure.
     */
    fun uploadToGlTexture3D(lutData: Lut3DData): Int {
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        val texId = textures[0]
        if (texId == 0) {
            Log.e(TAG, "Failed to generate OpenGL 3D texture handle")
            return 0
        }

        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, texId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE)

        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)

        GLES30.glTexImage3D(
            GLES30.GL_TEXTURE_3D,
            0,
            GLES30.GL_RGB8,
            lutData.size,
            lutData.size,
            lutData.size,
            0,
            GLES30.GL_RGB,
            GLES30.GL_UNSIGNED_BYTE,
            lutData.buffer
        )

        val error = GLES30.glGetError()
        if (error != GLES30.GL_NO_ERROR) {
            Log.e(TAG, "glTexImage3D failed with GL error: $error")
            GLES30.glDeleteTextures(1, textures, 0)
            return 0
        }

        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, 0)
        return texId
    }

    /**
     * Loads a `.cube` file from app assets and returns an OpenGL ES 3.0 3D texture handle.
     */
    fun loadCubeTextureFromAssets(context: Context, assetPath: String): Int {
        return try {
            context.assets.open(assetPath).use { input ->
                val lutData = parseCubeLut(input)
                uploadToGlTexture3D(lutData)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading LUT from asset: $assetPath, uploading identity", e)
            uploadToGlTexture3D(createIdentityLut3D(16))
        }
    }

    /**
     * Loads a Hald CLUT `.png` image into a 3D Texture or 2D strip.
     */
    fun loadHaldClutFromAssets(context: Context, assetPath: String): Int {
        return try {
            val bitmap = context.assets.open(assetPath).use { input ->
                BitmapFactory.decodeStream(input)
            } ?: return 0

            val width = bitmap.width
            val height = bitmap.height
            // HaldCLUT is typically 512x512 where size = 64 (64^3 = 262,144 pixels)
            val cubeSize = Math.cbrt((width * height).toDouble()).toInt()

            if (cubeSize * cubeSize * cubeSize != width * height) {
                // Treat as 2D strip texture
                val textures = IntArray(1)
                GLES20.glGenTextures(1, textures, 0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
                bitmap.recycle()
                return textures[0]
            }

            // Convert to 3D texture
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            bitmap.recycle()

            val buffer = ByteBuffer.allocateDirect(pixels.size * 3).order(ByteOrder.nativeOrder())
            for (p in pixels) {
                buffer.put(((p shr 16) and 0xFF).toByte())
                buffer.put(((p shr 8) and 0xFF).toByte())
                buffer.put((p and 0xFF).toByte())
            }
            buffer.rewind()

            uploadToGlTexture3D(Lut3DData(cubeSize, buffer))
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading HaldCLUT: $assetPath", e)
            uploadToGlTexture3D(createIdentityLut3D(16))
        }
    }
}
