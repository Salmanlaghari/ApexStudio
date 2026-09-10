package com.apexstudio.app.ui.screens.editor

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

/**
 * Live Compose visual FX overlay rendered directly on top of the ExoPlayer
 * preview surface in [VideoPreviewArea].
 *
 * Guarantees that FX (Vignette, VHS, Scanlines, Glitch, Film Grain, Light Leak,
 * Bloom, etc.) are visibly rendered at 60fps in the editor preview regardless
 * of device GL driver peculiarities or codec TextureView limitations.
 */
@Composable
fun FxPreviewOverlay(
    fxId: String?,
    intensity: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    if (fxId == null || intensity <= 0f) return

    val clampedIntensity = intensity.coerceIn(0f, 1f)

    // Running animation time ticker for dynamic FX (VHS roll, grain flicker, glitch jitter)
    val infiniteTransition = rememberInfiniteTransition(label = "fx_time")
    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fx_anim"
    )

    Box(modifier = modifier.fillMaxSize()) {
        when (fxId) {
            "vignette" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.radialGradient(
                                colorStops = arrayOf(
                                    0.35f to Color.Transparent,
                                    0.70f to Color.Black.copy(alpha = 0.45f * clampedIntensity),
                                    1.00f to Color.Black.copy(alpha = 0.88f * clampedIntensity)
                                )
                            )
                        )
                )
            }

            "film_grain", "sepia_grain", "dust_scratches", "film_damage" -> {
                val isSepia = fxId == "sepia_grain"
                if (isSepia) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF704214).copy(alpha = 0.25f * clampedIntensity))
                    )
                }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val seed = (animProgress * 100).toInt()
                    val rng = Random(seed)
                    val dotCount = (120 * clampedIntensity).toInt()
                    val dotAlpha = 0.22f * clampedIntensity
                    for (i in 0 until dotCount) {
                        val x = rng.nextFloat() * w
                        val y = rng.nextFloat() * h
                        val r = rng.nextFloat() * 1.8f + 0.6f
                        drawCircle(
                            color = if (isSepia) Color(0xFFEEDDAA).copy(alpha = dotAlpha)
                            else Color.White.copy(alpha = dotAlpha),
                            radius = r,
                            center = Offset(x, y)
                        )
                    }
                    if (fxId == "dust_scratches" || fxId == "film_damage") {
                        // Projector vertical hair/scratch lines
                        val scratchCount = (4 * clampedIntensity).toInt()
                        for (i in 0 until scratchCount) {
                            val sx = rng.nextFloat() * w
                            drawLine(
                                color = Color.White.copy(alpha = 0.25f * clampedIntensity),
                                start = Offset(sx, 0f),
                                end = Offset(sx + rng.nextFloat() * 10f - 5f, h),
                                strokeWidth = 1.2f
                            )
                        }
                    }
                }
            }

            "scanlines", "crt_phosphor", "interlaced" -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val h = size.height
                    val w = size.width
                    val lineSpacing = if (fxId == "crt_phosphor") 4f else 6f
                    val lineAlpha = 0.25f * clampedIntensity
                    var y = 0f
                    while (y < h) {
                        drawLine(
                            color = Color.Black.copy(alpha = lineAlpha),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.5f
                        )
                        y += lineSpacing
                    }
                }
            }

            "vhs", "bad_tv", "glitch", "rgb_jitter", "datamosh" -> {
                // Tracking line roll
                val trackingY = animProgress
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val seed = (animProgress * 40).toInt()
                    val rng = Random(seed)

                    // Rolling horizontal tracking static band
                    val barY = (trackingY * h) % h
                    val barHeight = 28f * clampedIntensity
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.20f * clampedIntensity),
                                Color.Cyan.copy(alpha = 0.15f * clampedIntensity),
                                Color.Transparent
                            ),
                            startY = barY - barHeight,
                            endY = barY + barHeight
                        ),
                        topLeft = Offset(0f, (barY - barHeight).coerceAtLeast(0f)),
                        size = Size(w, barHeight * 2f)
                    )

                    // Horizontal glitch slices
                    val sliceCount = (6 * clampedIntensity).toInt()
                    for (i in 0 until sliceCount) {
                        val sy = rng.nextFloat() * h
                        val sh = rng.nextFloat() * 12f + 4f
                        val isCyan = rng.nextBoolean()
                        drawRect(
                            color = if (isCyan) Color(0xFF00FFFF).copy(alpha = 0.16f * clampedIntensity)
                            else Color(0xFFFF0055).copy(alpha = 0.16f * clampedIntensity),
                            topLeft = Offset(0f, sy),
                            size = Size(w, sh)
                        )
                    }
                }
            }

            "chromatic", "prism" -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    // Cyan & Magenta fringe edge glow
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color(0xFFFF0055).copy(alpha = 0.22f * clampedIntensity),
                                Color.Transparent,
                                Color(0xFF00FFFF).copy(alpha = 0.22f * clampedIntensity)
                            )
                        ),
                        topLeft = Offset.Zero,
                        size = Size(w, h)
                    )
                }
            }

            "light_leak", "lens_flare", "sun_beam", "halation" -> {
                val shiftX = (kotlin.math.sin(animProgress * 2 * kotlin.math.PI) * 0.15f).toFloat()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.radialGradient(
                                colorStops = arrayOf(
                                    0.0f to Color(0xFFFF9944).copy(alpha = 0.40f * clampedIntensity),
                                    0.35f to Color(0xFFFF4488).copy(alpha = 0.22f * clampedIntensity),
                                    0.75f to Color.Transparent
                                ),
                                center = Offset(
                                    x = 0.25f + shiftX,
                                    y = 0.15f
                                )
                            )
                        )
                )
            }

            "bloom", "glow_diffuse", "sparkle" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.radialGradient(
                                colorStops = arrayOf(
                                    0.0f to Color(0xFFFFFFFF).copy(alpha = 0.22f * clampedIntensity),
                                    0.6f to Color(0xFF88CCFF).copy(alpha = 0.12f * clampedIntensity),
                                    1.0f to Color.Transparent
                                )
                            )
                        )
                )
            }

            "strobe" -> {
                val strobeOn = ((animProgress * 12).toInt() % 2) == 0
                if (strobeOn) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = 0.25f * clampedIntensity))
                    )
                }
            }

            "bleach_bypass" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF8899AA).copy(alpha = 0.15f * clampedIntensity))
                )
            }

            "solarize", "invert_fx" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF003366).copy(alpha = 0.18f * clampedIntensity))
                )
            }
        }
    }
}
