package com.example.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.example.effects.ChromaKeyProcessor
import com.example.effects.ColorGradingProcessor
import com.example.effects.RealEffectsProcessor
import com.example.model.AdjustmentValues
import com.example.model.ChromaKeyState
import com.example.model.EffectType
import kotlin.math.cos
import kotlin.math.sin

/**
 * Built-in real video clip archetypes for testing Chroma Key, VFX, and Color Grading.
 */
enum class VideoPresetClip(val title: String, val durationMs: Long, val description: String) {
    GREEN_SCREEN_DANCER("Green Screen Studio", 15_000L, "Character over pure #00FF00 chroma screen"),
    CYBERPUNK_METROPOLIS("Cyberpunk Alley", 15_000L, "Futuristic neon city with dark alleys & rain"),
    GOLDEN_SUNSET("Golden Coastline", 15_000L, "Scenic warm sunset over ocean horizon")
}

object VideoFrameRenderer {

    private const val FRAME_WIDTH = 640
    private const val FRAME_HEIGHT = 360

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = 28f
        isFakeBoldText = true
        setShadowLayer(4f, 2f, 2f, AndroidColor.BLACK)
    }

    /**
     * Renders a real procedural video frame at specific millisecond timestamp,
     * then executes the active Chroma Key, VFX shaders, and Color Grading passes.
     */
    fun renderProcessedFrame(
        timeMs: Long,
        activeClip: VideoPresetClip,
        chromaKey: ChromaKeyState,
        activeEffect: EffectType,
        effectIntensity: Float,
        adjustments: AdjustmentValues,
        textOverlay: String = ""
    ): Bitmap {
        // 1. Generate base video frame at exact millisecond
        val baseFrame = renderBaseFrame(timeMs, activeClip, textOverlay)

        // 2. Real Chroma Key processing pass
        val keyedFrame = if (chromaKey.enabled) {
            ChromaKeyProcessor.processFrame(baseFrame, chromaKey)
        } else {
            baseFrame
        }

        // 3. Real VFX processing pass
        val vfxFrame = if (activeEffect != EffectType.NONE && effectIntensity > 0f) {
            val frameIndex = (timeMs / 33L)
            RealEffectsProcessor.applyEffect(keyedFrame, activeEffect, effectIntensity, frameIndex)
        } else {
            keyedFrame
        }

        // 4. Real 12-factor Color Grading pass
        val gradedFrame = if (!adjustments.isDefault) {
            ColorGradingProcessor.applyColorGrading(vfxFrame, adjustments)
        } else {
            vfxFrame
        }

        return gradedFrame
    }

    private fun renderBaseFrame(
        timeMs: Long,
        clip: VideoPresetClip,
        textOverlay: String
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(FRAME_WIDTH, FRAME_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val t = timeMs / 1000.0

        when (clip) {
            VideoPresetClip.GREEN_SCREEN_DANCER -> {
                // Pure #00FF00 Green Screen background for Chroma Key testing
                canvas.drawColor(AndroidColor.rgb(0, 255, 0))

                // Animated dancing human silhouette subject
                val cx = FRAME_WIDTH / 2f + (sin(t * 2.5) * 60f).toFloat()
                val cy = FRAME_HEIGHT * 0.65f + (cos(t * 5.0) * 15f).toFloat()

                // Subject shadow on floor with green tint spill
                paint.color = AndroidColor.argb(120, 0, 180, 0)
                canvas.drawOval(RectF(cx - 80f, cy + 80f, cx + 80f, cy + 110f), paint)

                // Subject Body (Jacket & Jeans)
                paint.color = AndroidColor.rgb(40, 60, 110) // Denim blue
                canvas.drawRoundRect(RectF(cx - 38f, cy - 20f, cx + 38f, cy + 90f), 12f, 12f, paint)

                // Torso / Jacket (Orange & Black)
                paint.color = AndroidColor.rgb(240, 120, 30)
                canvas.drawRoundRect(RectF(cx - 35f, cy - 85f, cx + 35f, cy - 10f), 14f, 14f, paint)

                // Head
                paint.color = AndroidColor.rgb(235, 180, 140) // Skin tone
                canvas.drawCircle(cx, cy - 120f, 26f, paint)

                // Hair / Cap
                paint.color = AndroidColor.rgb(20, 20, 25)
                canvas.drawCircle(cx, cy - 132f, 22f, paint)

                // Animated Arms
                val armAngleL = sin(t * 3.0) * 35.0
                val armAngleR = -sin(t * 3.0) * 35.0
                paint.color = AndroidColor.rgb(240, 120, 30)
                paint.strokeWidth = 16f
                paint.strokeCap = Paint.Cap.ROUND

                // Left Arm
                val armLX = cx - 35f + (sin(Math.toRadians(armAngleL)) * 45.0).toFloat()
                val armLY = cy - 70f + (cos(Math.toRadians(armAngleL)) * 45.0).toFloat()
                canvas.drawLine(cx - 35f, cy - 70f, armLX, armLY, paint)

                // Right Arm
                val armRX = cx + 35f + (sin(Math.toRadians(armAngleR)) * 45.0).toFloat()
                val armRY = cy - 70f + (cos(Math.toRadians(armAngleR)) * 45.0).toFloat()
                canvas.drawLine(cx + 35f, cy - 70f, armRX, armRY, paint)
                paint.style = Paint.Style.FILL

                // Studio light flare
                paint.color = AndroidColor.argb(80, 255, 255, 255)
                canvas.drawCircle(100f, 60f, 45f, paint)
            }

            VideoPresetClip.CYBERPUNK_METROPOLIS -> {
                // Dark rainy cyberpunk alley
                canvas.drawColor(AndroidColor.rgb(12, 14, 22))

                // Skyscrapers silhouette
                paint.color = AndroidColor.rgb(20, 24, 38)
                canvas.drawRect(40f, 80f, 180f, FRAME_HEIGHT.toFloat(), paint)
                canvas.drawRect(220f, 40f, 380f, FRAME_HEIGHT.toFloat(), paint)
                canvas.drawRect(420f, 110f, 590f, FRAME_HEIGHT.toFloat(), paint)

                // Glowing Neon Signs (Cyan & Pink)
                val neonPulse = (sin(t * 4.0) * 0.3 + 0.7).toFloat()
                paint.color = AndroidColor.argb((255 * neonPulse).toInt(), 0, 240, 255)
                canvas.drawRoundRect(RectF(250f, 80f, 350f, 140f), 8f, 8f, paint)

                paint.color = AndroidColor.argb(255, 255, 40, 140)
                canvas.drawRoundRect(RectF(80f, 160f, 140f, 260f), 8f, 8f, paint)

                // Wet asphalt reflection
                val reflectionPaint = Paint().apply {
                    color = AndroidColor.argb(90, 0, 220, 255)
                }
                canvas.drawRect(0f, FRAME_HEIGHT * 0.75f, FRAME_WIDTH.toFloat(), FRAME_HEIGHT.toFloat(), reflectionPaint)
            }

            VideoPresetClip.GOLDEN_SUNSET -> {
                // Sunset sky gradient
                paint.color = AndroidColor.rgb(240, 100, 40)
                canvas.drawRect(0f, 0f, FRAME_WIDTH.toFloat(), FRAME_HEIGHT * 0.6f, paint)

                // Glowing Sun
                paint.color = AndroidColor.rgb(255, 240, 180)
                val sunY = FRAME_HEIGHT * 0.45f + (sin(t * 0.5) * 10f).toFloat()
                canvas.drawCircle(FRAME_WIDTH * 0.5f, sunY, 50f, paint)

                // Ocean water
                paint.color = AndroidColor.rgb(30, 45, 90)
                canvas.drawRect(0f, FRAME_HEIGHT * 0.6f, FRAME_WIDTH.toFloat(), FRAME_HEIGHT.toFloat(), paint)

                // Ocean water golden shimmer waves
                paint.color = AndroidColor.argb(160, 255, 180, 50)
                for (w in 0..8) {
                    val wy = FRAME_HEIGHT * 0.65f + w * 14f
                    val waveOffset = (sin(t * 2.0 + w) * 20.0).toFloat()
                    canvas.drawLine(FRAME_WIDTH * 0.3f + waveOffset, wy, FRAME_WIDTH * 0.7f - waveOffset, wy, paint)
                }
            }
        }

        // Overlay user text if provided
        if (textOverlay.isNotBlank()) {
            val textW = textPaint.measureText(textOverlay)
            canvas.drawText(textOverlay, (FRAME_WIDTH - textW) / 2f, FRAME_HEIGHT * 0.88f, textPaint)
        }

        return bitmap
    }
}
