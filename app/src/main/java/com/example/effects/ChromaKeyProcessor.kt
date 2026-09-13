package com.example.effects

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import com.example.model.BackgroundPlate
import com.example.model.ChromaKeyState
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Real-time 3D Chroma Key pixel processor.
 * Applies true Euclidean color-space distance keying, smoothstep edge falloff,
 * spill suppression to neutralize green/blue spill fringes on edges, and composite rendering.
 */
object ChromaKeyProcessor {

    fun processFrame(
        sourceBitmap: Bitmap,
        chromaKey: ChromaKeyState
    ): Bitmap {
        if (!chromaKey.enabled) return sourceBitmap

        val width = sourceBitmap.width
        val height = sourceBitmap.height
        val outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        sourceBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val keyColor = chromaKey.keyColor
        val keyR = AndroidColor.red(keyColor) / 255f
        val keyG = AndroidColor.green(keyColor) / 255f
        val keyB = AndroidColor.blue(keyColor) / 255f

        val similarity = chromaKey.similarity.coerceIn(0.01f, 1.0f)
        val smoothness = max(0.001f, chromaKey.smoothness)
        val spill = chromaKey.spillSuppression.coerceIn(0f, 1f)
        val isMatteOnly = chromaKey.showMatteOnly
        val bgPlate = chromaKey.backgroundPlate

        // Check dominant channel of key color for spill suppression
        val isGreenKey = keyG > keyR && keyG > keyB
        val isBlueKey = keyB > keyR && keyB > keyG

        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val index = rowOffset + x
                val pixel = pixels[index]

                var pr = AndroidColor.red(pixel) / 255f
                var pg = AndroidColor.green(pixel) / 255f
                var pb = AndroidColor.blue(pixel) / 255f
                val origA = AndroidColor.alpha(pixel) / 255f

                // Euclidean distance in RGB color cube (normalized to 0..1)
                val dr = pr - keyR
                val dg = pg - keyG
                val db = pb - keyB
                val dist = sqrt((dr * dr + dg * dg + db * db).toDouble()).toFloat() / 1.732f

                // Smoothstep alpha falloff
                val alphaMultiplier: Float = when {
                    dist <= similarity -> 0f
                    dist >= similarity + smoothness -> 1f
                    else -> {
                        val t = (dist - similarity) / smoothness
                        t * t * (3f - 2f * t) // Hermite smoothstep
                    }
                }

                val finalAlpha = origA * alphaMultiplier

                if (isMatteOnly) {
                    // Output black & white matte
                    val m = (finalAlpha * 255).toInt().coerceIn(0, 255)
                    pixels[index] = AndroidColor.argb(255, m, m, m)
                    continue
                }

                // Spill suppression: damp the spill channel on semi-keyed and fringe pixels
                if (spill > 0f) {
                    if (isGreenKey) {
                        val maxNeighbor = max(pr, pb)
                        if (pg > maxNeighbor) {
                            val correctedG = pg * (1f - spill) + maxNeighbor * spill
                            pg = min(pg, correctedG)
                        }
                    } else if (isBlueKey) {
                        val maxNeighbor = max(pr, pg)
                        if (pb > maxNeighbor) {
                            val correctedB = pb * (1f - spill) + maxNeighbor * spill
                            pb = min(pb, correctedB)
                        }
                    }
                }

                // Composite over selected background plate
                if (finalAlpha < 0.999f) {
                    val bgPixel = getBackgroundPixel(bgPlate, x, y, width, height)
                    val bgR = AndroidColor.red(bgPixel) / 255f
                    val bgG = AndroidColor.green(bgPixel) / 255f
                    val bgB = AndroidColor.blue(bgPixel) / 255f

                    // Standard Porter-Duff source over
                    val outR = (pr * finalAlpha + bgR * (1f - finalAlpha)).coerceIn(0f, 1f)
                    val outG = (pg * finalAlpha + bgG * (1f - finalAlpha)).coerceIn(0f, 1f)
                    val outB = (pb * finalAlpha + bgB * (1f - finalAlpha)).coerceIn(0f, 1f)

                    pixels[index] = AndroidColor.argb(
                        255,
                        (outR * 255).toInt(),
                        (outG * 255).toInt(),
                        (outB * 255).toInt()
                    )
                } else {
                    pixels[index] = AndroidColor.argb(
                        255,
                        (pr * 255).toInt().coerceIn(0, 255),
                        (pg * 255).toInt().coerceIn(0, 255),
                        (pb * 255).toInt().coerceIn(0, 255)
                    )
                }
            }
        }

        outputBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return outputBitmap
    }

    private fun getBackgroundPixel(
        plate: BackgroundPlate,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ): Int {
        return when (plate) {
            BackgroundPlate.CHECKERBOARD -> {
                val checkSize = 16
                val isCheck = ((x / checkSize) + (y / checkSize)) % 2 == 0
                if (isCheck) 0xFF4A4E5A.toInt() else 0xFF2A2D35.toInt()
            }
            BackgroundPlate.BLACK -> 0xFF000000.toInt()
            BackgroundPlate.STUDIO_DARK -> {
                // Radial spotlight gradient on dark stage
                val cx = width / 2f
                val cy = height * 0.7f
                val dx = (x - cx) / width
                val dy = (y - cy) / height
                val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                val glow = (1f - dist * 1.4f).coerceIn(0f, 1f)
                val r = (25 + glow * 40).toInt().coerceIn(0, 255)
                val g = (20 + glow * 30).toInt().coerceIn(0, 255)
                val b = (45 + glow * 70).toInt().coerceIn(0, 255)
                AndroidColor.rgb(r, g, b)
            }
            BackgroundPlate.CYBER_CITY -> {
                // Neon synthwave sunset gradient
                val yNorm = y.toFloat() / height
                val r = (20 + yNorm * 90).toInt().coerceIn(0, 255)
                val g = (10 + yNorm * 20).toInt().coerceIn(0, 255)
                val b = (60 + (1f - yNorm) * 120).toInt().coerceIn(0, 255)
                AndroidColor.rgb(r, g, b)
            }
            BackgroundPlate.SUNSET_BEACH -> {
                val yNorm = y.toFloat() / height
                val r = (255 * (1f - yNorm * 0.4f)).toInt().coerceIn(0, 255)
                val g = (110 * (1f - yNorm * 0.6f) + yNorm * 40).toInt().coerceIn(0, 255)
                val b = (50 + yNorm * 110).toInt().coerceIn(0, 255)
                AndroidColor.rgb(r, g, b)
            }
        }
    }
}
