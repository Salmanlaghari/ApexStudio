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

/** Only these previews read the speed-scaled wall clock; other FX use their own animation progress. */
private val WALL_CLOCK_FX_IDS = setOf(
    "hue_cycle", "pulse_beat", "gradient_sweep", "light_leak_sweep"
)
internal fun fxPreviewUsesWallClock(fxId: String): Boolean = fxId in WALL_CLOCK_FX_IDS

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
    modifier: Modifier = Modifier,
    speed: Float = 1f
) {
    if (fxId == null || intensity <= 0f) return

    val clampedIntensity = intensity.coerceIn(0f, 1f)
    val clampedSpeed = speed.coerceIn(0.1f, 4f)

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

    // Wall-clock ticker for animated colour filters (0 → 3600s over an
    // hour, so long previews never show a visible loop jump). Driven by
    // real time so it animates whether the player is playing or paused.
    var tSec by remember { mutableFloatStateOf(0f) }
    // Read the latest speed inside the running ticker so slider drags take
    // effect without restarting the coroutine.
    val currentSpeed by rememberUpdatedState(clampedSpeed)
    if (fxPreviewUsesWallClock(fxId)) {
        LaunchedEffect(fxId, isPlaying) {
            val start = System.nanoTime()
            var last = 0f
            while (true) {
                val elapsed = (System.nanoTime() - start) / 1_000_000_000f
                // Advance by wall-clock delta scaled by speed; wrap at 1h.
                val delta = (elapsed - last).coerceAtLeast(0f)
                last = elapsed
                tSec = (tSec + delta * currentSpeed) % 3600f
                kotlinx.coroutines.delay(50)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (fxId.startsWith("creator_")) {
            CreatorPackFxPreview(fxId, clampedIntensity, animProgress)
        } else when (fxId) {
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

            "pixel_sort" -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val rng = Random((animProgress * 30).toInt())
                    val streakCount = (16 * clampedIntensity).toInt()
                    for (i in 0 until streakCount) {
                        val sx = rng.nextFloat() * w
                        val sy = rng.nextFloat() * (h * 0.7f)
                        val length = rng.nextFloat() * (h * 0.35f) + 20f
                        drawLine(
                            brush = Brush.verticalGradient(
                                listOf(Color.Cyan.copy(alpha = 0.45f * clampedIntensity), Color.Transparent),
                                startY = sy,
                                endY = sy + length
                            ),
                            start = Offset(sx, sy),
                            end = Offset(sx, sy + length),
                            strokeWidth = rng.nextFloat() * 4f + 2f
                        )
                    }
                }
            }

            "halftone" -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val step = 14f
                    var y = 0f
                    while (y < h) {
                        var x = 0f
                        while (x < w) {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.18f * clampedIntensity),
                                radius = 2.5f * clampedIntensity,
                                center = Offset(x, y)
                            )
                            x += step
                        }
                        y += step
                    }
                }
            }

            "neon_edge", "edge_neon" -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color(0xFF00F0FF).copy(alpha = 0.28f * clampedIntensity),
                                Color.Transparent,
                                Color(0xFFFF007F).copy(alpha = 0.28f * clampedIntensity)
                            )
                        ),
                        topLeft = Offset.Zero,
                        size = Size(w, h)
                    )
                }
            }

            "thermal", "thermal_vision" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color(0xFFFF3300).copy(alpha = 0.25f * clampedIntensity),
                                    Color(0xFFFFEE00).copy(alpha = 0.18f * clampedIntensity),
                                    Color(0xFF0033CC).copy(alpha = 0.28f * clampedIntensity)
                                )
                            )
                        )
                )
            }

            "radial_blur" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.radialGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.15f * clampedIntensity),
                                    Color.Black.copy(alpha = 0.35f * clampedIntensity)
                                )
                            )
                        )
                )
            }

            "3d_chromakey" -> {
                // 3D ChromaKey VFX live visual preview
                // 1. Holographic 3D grid and cyber portal background
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    
                    // Cyber portal concentric rings
                    val centerX = w / 2f
                    val centerY = h / 2f
                    val maxR = kotlin.math.max(w, h) * 0.7f
                    val ringCount = 6
                    for (i in 1..ringCount) {
                        val r = (maxR * i / ringCount + (animProgress * (maxR / ringCount))) % maxR
                        drawCircle(
                            color = Color(0xFF00E5FF).copy(alpha = (0.25f * (1f - r / maxR) * clampedIntensity)),
                            radius = r,
                            center = Offset(centerX, centerY),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f)
                        )
                    }

                    // 3D perspective floor grid lines
                    val lineCount = 8
                    val horizonY = h * 0.55f
                    for (i in 0..lineCount) {
                        val bottomX = w * i / lineCount
                        drawLine(
                            color = Color(0xFFE040FB).copy(alpha = 0.28f * clampedIntensity),
                            start = Offset(centerX, horizonY),
                            end = Offset(bottomX, h),
                            strokeWidth = 1.5f
                        )
                    }

                    // Top/Bottom 3D Neon Edge bars
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFF00F0FF).copy(alpha = 0.35f * clampedIntensity), Color.Transparent)
                        ),
                        size = Size(w, 24f)
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xFFFF007F).copy(alpha = 0.35f * clampedIntensity))
                        ),
                        topLeft = Offset(0f, h - 24f),
                        size = Size(w, 24f)
                    )
                }

                // Chroma Key Spill Suppression Overlay (tints green edges to neutral)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1A0A2A).copy(alpha = 0.12f * clampedIntensity))
                )
            }

            // --- Animated colour filters (Snapchat-style) ---
            "hue_cycle" -> {
                // Approximate the shader's hue rotation with a BlendMode.Hue
                // overlay whose colour cycles over time.
                val hue = (tSec * 90f) % 360f // 90°/s at 1x, matches shader
                val cycleColor = Color.hsv(hue, 0.85f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(cycleColor.copy(alpha = 0.55f * clampedIntensity))
                )
            }

            "pulse_beat" -> {
                // Warm grade throbbing on a 100 BPM beat envelope.
                val beatPhase = (tSec * 100f / 60f * 2f * Math.PI.toFloat()) % (2f * Math.PI.toFloat())
                val beat = Math.pow(Math.max(0f, kotlin.math.sin(beatPhase)).toDouble(), 4.0).toFloat()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Color(0xFFFF6B35).copy(alpha = beat * 0.28f * clampedIntensity)
                        )
                )
            }

            "gradient_sweep" -> {
                // Cyan→magenta wash band sweeping diagonally (seamless loop).
                val t = (tSec * 0.22f) % 1f
                val pos = t * 1.8f - 0.4f
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    // Draw the diagonal band as a rotated rect approximation:
                    // use a linear gradient whose stops slide with `pos`.
                    val bandCenter = pos * (w + h) / 2f
                    val bandWidth = 0.28f * (w + h) / 2f
                    drawRect(
                        brush = Brush.linearGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                ((bandCenter - bandWidth) / (w + h)).coerceIn(0f, 1f) to Color.Transparent,
                                (bandCenter / (w + h)).coerceIn(0f, 1f) to Color(
                                    0xFF00E5FF
                                ).copy(alpha = 0.65f * clampedIntensity),
                                ((bandCenter + bandWidth) / (w + h)).coerceIn(0f, 1f) to Color.Transparent,
                                1f to Color.Transparent
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(w, h)
                        ),
                        size = size
                    )
                }
            }

            "light_leak_sweep" -> {
                // Warm amber leak sweeping left→right with a breathe.
                val t = (tSec * 0.18f) % 1f
                val pos = t * 1.9f - 0.45f
                val breathe = 0.72f + 0.28f * kotlin.math.sin(tSec * 2.6f)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val cx = pos * w
                    val radius = 0.45f * w
                    drawRect(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0f to Color(0xFFFF9E40).copy(alpha = 0.55f * clampedIntensity * breathe),
                                1f to Color.Transparent
                            ),
                            center = Offset(cx, h * 0.35f),
                            radius = radius
                        ),
                        size = size
                    )
                }
            }
        }
    }
}


/**
 * Compose preview approximation for the Creator Pack shader families. The
 * pixel-sampling GLSL variant is also used by FxGlEffect for rendering/export;
 * this overlay keeps new choices visible in the editor path that bypasses it.
 */
@Composable
private fun CreatorPackFxPreview(fxId: String, intensity: Float, progress: Float) {
    val tint = when {
        fxId.contains("aurora") || fxId.contains("lagoon") || fxId.contains("quad") || fxId.contains("cobalt") || fxId.contains("ice") || fxId.contains("orbit") -> Color(0xFF28D9D0)
        fxId.contains("candy") || fxId.contains("rose") || fxId.contains("berry") || fxId.contains("plum") || fxId.contains("hex") -> Color(0xFFFF4F91)
        fxId.contains("copper") || fxId.contains("heat") || fxId.contains("honey") || fxId.contains("tangerine") || fxId.contains("solar") -> Color(0xFFFF9B35)
        fxId.contains("arctic") || fxId.contains("cyan") || fxId.contains("silver") || fxId.contains("micro") || fxId.contains("twilight") -> Color(0xFF65BFFF)
        fxId.contains("violet") || fxId.contains("lilac") || fxId.contains("kaleido") || fxId.contains("dream") -> Color(0xFFA46BFF)
        fxId.contains("mint") || fxId.contains("rain") || fxId.contains("green") || fxId.contains("soft") || fxId.contains("lime") -> Color(0xFF53E68D)
        fxId.contains("sunset") || fxId.contains("coral") || fxId.contains("pop") || fxId.contains("poster") || fxId.contains("rush") -> Color(0xFFFF5C56)
        fxId.contains("newsprint") || fxId.contains("mono") -> Color(0xFFDDE5EE)
        fxId.contains("riso") -> Color(0xFFFF762E)
        fxId.contains("bold") || fxId.contains("diamond") || fxId.contains("tiles") || fxId.contains("fan") || fxId.contains("radial") -> Color(0xFFFFCF55)
        else -> Color(0xFF66CFFF)
    }
    val family = fxId.removePrefix("creator_").substringBefore('_')
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        val cy = h * 0.5f
        val alpha = intensity
        when (family) {
            "prism" -> {
                val band = (0.08f + progress * 0.05f) * w
                drawRect(Brush.horizontalGradient(listOf(tint.copy(alpha = 0.22f * alpha), Color.Transparent, Color.White.copy(alpha = 0.08f * alpha))), size = size)
                drawRect(tint.copy(alpha = 0.14f * alpha), Offset.Zero, Size(band, h))
                drawRect(tint.copy(alpha = 0.14f * alpha), Offset(w - band, 0f), Size(band, h))
            }
            "tone" -> drawRect(Brush.verticalGradient(listOf(tint.copy(alpha = 0.22f * alpha), Color.Transparent, tint.copy(alpha = 0.07f * alpha))), size = size)
            "ripple", "halation" -> {
                val maxRadius = kotlin.math.max(w, h) * 0.68f
                for (i in 1..7) {
                    val radius = maxRadius * i / 7f * (if (family == "ripple") 0.82f + progress * 0.18f else 1f)
                    drawCircle(tint.copy(alpha = 0.12f * alpha * (1f - i / 8f)), radius, Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = if (family == "ripple") 2.2f else 1.2f))
                }
                if (family == "halation") drawRect(Brush.radialGradient(listOf(tint.copy(alpha = 0.20f * alpha), Color.Transparent), center = Offset(w * 0.36f, h * 0.42f), radius = maxRadius * 0.72f), size = size)
            }
            "mosaic" -> {
                val step = when {
                    fxId.contains("micro") -> 13f
                    fxId.contains("bold") -> 38f
                    fxId.contains("diamond") -> 29f
                    else -> 23f
                }
                var y = 0f
                var row = 0
                while (y < h) {
                    var x = if (row % 2 == 0) 0f else step * 0.5f
                    while (x < w) {
                        drawRect(tint.copy(alpha = 0.12f * alpha), Offset(x, y), Size(step * 0.45f, step * 0.45f))
                        x += step
                    }
                    y += step
                    row++
                }
            }
            "crt" -> {
                drawRect(tint.copy(alpha = 0.08f * alpha), size = size)
                var y = progress * 6f % 7f
                while (y < h) {
                    drawLine(Color.Black.copy(alpha = 0.22f * alpha), Offset(0f, y), Offset(w, y), 1.5f)
                    y += 7f
                }
                drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.25f * alpha)), center = Offset(cx, cy), radius = kotlin.math.max(w, h) * 0.72f), size = size)
            }
            "ink" -> {
                val spacing = if (fxId.contains("newsprint")) 12f else 17f
                var y = 0f
                while (y < h) {
                    var x = 0f
                    while (x < w) {
                        val radius = (1.7f + (kotlin.math.sin(x * 0.04f + y * 0.03f) + 1f) * 2.1f) * alpha
                        drawCircle(tint.copy(alpha = 0.30f * alpha), radius, Offset(x, y))
                        x += spacing
                    }
                    y += spacing
                }
            }
            "mirror" -> {
                for (i in 1..10) drawCircle(tint.copy(alpha = 0.07f * alpha), kotlin.math.max(w, h) * i / 12f, Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
                val spokes = if (fxId.contains("hex")) 6 else if (fxId.contains("quad")) 4 else 12
                for (i in 0 until spokes) {
                    val angle = i * (2.0 * Math.PI / spokes) + progress.toDouble() * 0.04
                    drawLine(tint.copy(alpha = 0.18f * alpha), Offset(cx, cy), Offset(cx + kotlin.math.cos(angle).toFloat() * w, cy + kotlin.math.sin(angle).toFloat() * h), 1.5f)
                }
            }
            "glitch" -> {
                val rng = Random((progress * 80f).toInt() + fxId.hashCode())
                repeat(7) {
                    val y = rng.nextFloat() * h
                    val barHeight = 2f + rng.nextFloat() * 15f
                    val x = rng.nextFloat() * w * 0.25f
                    drawRect(tint.copy(alpha = 0.25f * alpha), Offset(x, y), Size(w - x, barHeight))
                }
            }
            "streak" -> {
                for (i in 0 until 20) {
                    val angle = i * (2.0 * Math.PI / 20.0) + progress.toDouble() * 0.06
                    drawLine(tint.copy(alpha = 0.14f * alpha), Offset(cx, cy), Offset(cx + kotlin.math.cos(angle).toFloat() * w, cy + kotlin.math.sin(angle).toFloat() * h), 2f)
                }
                drawRect(Brush.radialGradient(listOf(Color.Transparent, tint.copy(alpha = 0.12f * alpha)), center = Offset(cx, cy), radius = kotlin.math.max(w, h) * 0.82f), size = size)
            }
        }
    }
}
