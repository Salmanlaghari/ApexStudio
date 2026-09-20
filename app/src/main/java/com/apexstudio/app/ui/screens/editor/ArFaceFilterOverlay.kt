package com.apexstudio.app.ui.screens.editor

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.ar.ArFilterCatalog
import com.apexstudio.app.data.ar.DetectedFaceData
import kotlin.math.sin
import kotlin.random.Random

/**
 * Live AR/AI Face Filter & Festival Overlay renderer.
 * Accurately tracks detected facial landmarks (or centered anchor) in real-time,
 * applying beauty smoothing, anime features, retro optics, and handcrafted festival arches.
 */
@Composable
fun ArFaceFilterOverlay(
    filterId: String?,
    intensity: Float,
    customText: String,
    detectedFace: DetectedFaceData? = null,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (filterId == null || intensity <= 0f) return

    val filter = ArFilterCatalog.getFilterById(filterId) ?: return
    val clampedIntensity = intensity.coerceIn(0f, 1f)
    val face = detectedFace ?: DetectedFaceData.DEFAULT

    // Infinite animation ticker for natural organic movement (flame flicker, bell swing, sparkle pulse)
    val infiniteTransition = rememberInfiniteTransition(label = "ar_anim")
    val ticker by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ar_ticker"
    )

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Face coordinates translated to Canvas pixels
            val leftEyeX = face.normLeftEye.x * w
            val leftEyeY = face.normLeftEye.y * h
            val rightEyeX = face.normRightEye.x * w
            val rightEyeY = face.normRightEye.y * h
            val eyeDist = kotlin.math.abs(rightEyeX - leftEyeX).coerceAtLeast(40f)
            val eyeRadius = eyeDist * 0.28f

            val faceCenterX = (face.normBoundsLeft + face.normBoundsRight) / 2f * w
            val faceCenterY = (face.normBoundsTop + face.normBoundsBottom) / 2f * h
            val faceRadiusX = (face.normBoundsRight - face.normBoundsLeft) / 2f * w
            val faceRadiusY = (face.normBoundsBottom - face.normBoundsTop) / 2f * h

            when (filter.id) {
                // 1. Beauty Smoothing
                "ar_beauty_smooth" -> {
                    // Soft porcelain skin glow centered on face
                    drawOval(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFECEE).copy(alpha = 0.35f * clampedIntensity),
                                Color(0xFFFFDDE2).copy(alpha = 0.20f * clampedIntensity),
                                Color.Transparent
                            ),
                            center = Offset(faceCenterX, faceCenterY),
                            radius = faceRadiusY * 1.1f
                        ),
                        topLeft = Offset(faceCenterX - faceRadiusX * 1.1f, faceCenterY - faceRadiusY * 1.1f),
                        size = Size(faceRadiusX * 2.2f, faceRadiusY * 2.2f),
                        blendMode = BlendMode.Screen
                    )
                }

                // 2. Skin Retouch
                "ar_skin_retouch" -> {
                    // Warm radiant peach skin tone
                    drawOval(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFB899).copy(alpha = 0.32f * clampedIntensity),
                                Color(0xFFFF9E7A).copy(alpha = 0.15f * clampedIntensity),
                                Color.Transparent
                            ),
                            center = Offset(faceCenterX, faceCenterY),
                            radius = faceRadiusY * 1.15f
                        ),
                        topLeft = Offset(faceCenterX - faceRadiusX * 1.1f, faceCenterY - faceRadiusY * 1.1f),
                        size = Size(faceRadiusX * 2.2f, faceRadiusY * 2.2f),
                        blendMode = BlendMode.ColorDodge
                    )
                }

                // 3. Face Slimming
                "ar_face_slimming" -> {
                    // Subtle contour shading around the lower jawline
                    val jawY = faceCenterY + faceRadiusY * 0.3f
                    val jawW = faceRadiusX * 1.4f
                    val jawH = faceRadiusY * 0.9f
                    drawOval(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF382015).copy(alpha = 0.22f * clampedIntensity),
                                Color(0xFF1E100A).copy(alpha = 0.40f * clampedIntensity)
                            ),
                            center = Offset(faceCenterX, jawY),
                            radius = jawW
                        ),
                        topLeft = Offset(faceCenterX - jawW, jawY - jawH * 0.5f),
                        size = Size(jawW * 2f, jawH),
                        blendMode = BlendMode.Multiply
                    )
                    // Chin highlight
                    val chinY = faceCenterY + faceRadiusY * 0.82f
                    drawCircle(
                        color = Color(0xFFFFFAEE).copy(alpha = 0.35f * clampedIntensity),
                        radius = 16f,
                        center = Offset(faceCenterX, chinY),
                        blendMode = BlendMode.Screen
                    )
                }

                // 4. Anime Big Eyes
                "ar_big_eyes" -> {
                    val pulse = 1f + sin(ticker * Math.PI.toFloat() * 2f) * 0.08f
                    val r = eyeRadius * pulse

                    // Left Eye Sparkle & Iris Glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF00E5FF).copy(alpha = 0.45f * clampedIntensity),
                                Color(0xFF38BDF8).copy(alpha = 0.25f * clampedIntensity),
                                Color.Transparent
                            ),
                            center = Offset(leftEyeX, leftEyeY),
                            radius = r * 1.6f
                        ),
                        center = Offset(leftEyeX, leftEyeY),
                        radius = r * 1.6f,
                        blendMode = BlendMode.Screen
                    )
                    // Anime Star Twinkle
                    drawSparkleStar(Offset(leftEyeX - r * 0.35f, leftEyeY - r * 0.35f), r * 0.55f, clampedIntensity)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.90f * clampedIntensity),
                        radius = r * 0.22f,
                        center = Offset(leftEyeX + r * 0.30f, leftEyeY + r * 0.25f)
                    )

                    // Right Eye Sparkle & Iris Glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF00E5FF).copy(alpha = 0.45f * clampedIntensity),
                                Color(0xFF38BDF8).copy(alpha = 0.25f * clampedIntensity),
                                Color.Transparent
                            ),
                            center = Offset(rightEyeX, rightEyeY),
                            radius = r * 1.6f
                        ),
                        center = Offset(rightEyeX, rightEyeY),
                        radius = r * 1.6f,
                        blendMode = BlendMode.Screen
                    )
                    drawSparkleStar(Offset(rightEyeX - r * 0.35f, rightEyeY - r * 0.35f), r * 0.55f, clampedIntensity)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.90f * clampedIntensity),
                        radius = r * 0.22f,
                        center = Offset(rightEyeX + r * 0.30f, rightEyeY + r * 0.25f)
                    )
                }

                // 5. Cartoon / Anime Style
                "ar_cartoon_anime" -> {
                    // Anime rosy cheek blush
                    val cheekRadius = eyeDist * 0.38f
                    val leftCheekX = leftEyeX - eyeDist * 0.15f
                    val rightCheekX = rightEyeX + eyeDist * 0.15f
                    val cheekY = (leftEyeY + rightEyeY) / 2f + eyeDist * 0.50f

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFF5277).copy(alpha = 0.45f * clampedIntensity),
                                Color.Transparent
                            ),
                            center = Offset(leftCheekX, cheekY),
                            radius = cheekRadius
                        ),
                        center = Offset(leftCheekX, cheekY),
                        radius = cheekRadius,
                        blendMode = BlendMode.Screen
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFF5277).copy(alpha = 0.45f * clampedIntensity),
                                Color.Transparent
                            ),
                            center = Offset(rightCheekX, cheekY),
                            radius = cheekRadius
                        ),
                        center = Offset(rightCheekX, cheekY),
                        radius = cheekRadius,
                        blendMode = BlendMode.Screen
                    )

                    // Three cute blush line marks on cheeks
                    for (i in -1..1) {
                        drawLine(
                            color = Color(0xFFFF1744).copy(alpha = 0.55f * clampedIntensity),
                            start = Offset(leftCheekX + i * 8f - 6f, cheekY - 4f),
                            end = Offset(leftCheekX + i * 8f + 6f, cheekY + 4f),
                            strokeWidth = 2.5f,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = Color(0xFFFF1744).copy(alpha = 0.55f * clampedIntensity),
                            start = Offset(rightCheekX + i * 8f - 6f, cheekY - 4f),
                            end = Offset(rightCheekX + i * 8f + 6f, cheekY + 4f),
                            strokeWidth = 2.5f,
                            cap = StrokeCap.Round
                        )
                    }

                    // Anime forehead sparkle
                    drawSparkleStar(Offset(faceCenterX, leftEyeY - eyeDist * 0.7f), eyeDist * 0.35f, clampedIntensity)
                }

                // 6. Cyber Glitch Visor
                "ar_cyber_visor" -> {
                    val visorW = eyeDist * 2.5f
                    val visorH = eyeDist * 0.75f
                    val visorTop = (leftEyeY + rightEyeY) / 2f - visorH * 0.45f
                    val visorLeft = faceCenterX - visorW / 2f

                    // Glowing Neon Cyan Visor Strip
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color(0xFF00E5FF).copy(alpha = 0.25f * clampedIntensity),
                                Color(0xFF00E5FF).copy(alpha = 0.65f * clampedIntensity),
                                Color(0xFF00E5FF).copy(alpha = 0.25f * clampedIntensity)
                            )
                        ),
                        topLeft = Offset(visorLeft, visorTop),
                        size = Size(visorW, visorH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                        blendMode = BlendMode.Screen
                    )
                    // Visor Border
                    drawRoundRect(
                        color = Color(0xFF00E5FF).copy(alpha = 0.85f * clampedIntensity),
                        topLeft = Offset(visorLeft, visorTop),
                        size = Size(visorW, visorH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                        style = Stroke(width = 2f)
                    )
                    // Grid scanlines inside visor
                    val scanY = visorTop + (ticker * visorH)
                    drawLine(
                        color = Color.White.copy(alpha = 0.90f * clampedIntensity),
                        start = Offset(visorLeft + 4f, scanY),
                        end = Offset(visorLeft + visorW - 4f, scanY),
                        strokeWidth = 2f
                    )
                    // Target crosshairs
                    drawCircle(
                        color = Color(0xFF00E5FF).copy(alpha = 0.95f * clampedIntensity),
                        radius = 8f,
                        center = Offset(leftEyeX, leftEyeY),
                        style = Stroke(width = 1.5f)
                    )
                    drawCircle(
                        color = Color(0xFF00E5FF).copy(alpha = 0.95f * clampedIntensity),
                        radius = 8f,
                        center = Offset(rightEyeX, rightEyeY),
                        style = Stroke(width = 1.5f)
                    )
                }

                // 7. Portrait Bokeh Blur
                "ar_bokeh_blur" -> {
                    // Simulates optical lens background blur with clear focal face center
                    drawRect(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0.25f to Color.Transparent,
                                0.55f to Color(0xFF101018).copy(alpha = 0.35f * clampedIntensity),
                                0.85f to Color(0xFF0A0A10).copy(alpha = 0.70f * clampedIntensity)
                            ),
                            center = Offset(faceCenterX, faceCenterY),
                            radius = (w * 0.65f)
                        ),
                        size = Size(w, h),
                        blendMode = BlendMode.Multiply
                    )
                    // Bokeh light orbs in background
                    val rng = Random(42)
                    for (i in 0 until 14) {
                        val bx = rng.nextFloat() * w
                        val by = rng.nextFloat() * h
                        val br = rng.nextFloat() * 26f + 14f
                        val bAlpha = (rng.nextFloat() * 0.25f + 0.10f) * clampedIntensity
                        drawCircle(
                            color = Color(0xFFFFEECC).copy(alpha = bAlpha),
                            radius = br,
                            center = Offset(bx, by),
                            blendMode = BlendMode.Screen
                        )
                    }
                }

                // 8. Retro Camera Grain & Viewfinder
                "ar_retro_grain" -> {
                    // Vintage 35mm Analog Grain
                    val rng = Random((ticker * 100).toInt())
                    val dotCount = (140 * clampedIntensity).toInt()
                    for (i in 0 until dotCount) {
                        val gx = rng.nextFloat() * w
                        val gy = rng.nextFloat() * h
                        drawCircle(
                            color = Color.White.copy(alpha = 0.18f * clampedIntensity),
                            radius = rng.nextFloat() * 1.5f + 0.5f,
                            center = Offset(gx, gy)
                        )
                    }
                    // Viewfinder corner marks
                    val cornerLen = 28f
                    val pad = 24f
                    val strokeCol = Color.White.copy(alpha = 0.60f * clampedIntensity)
                    // Top-Left
                    drawLine(strokeCol, Offset(pad, pad), Offset(pad + cornerLen, pad), strokeWidth = 2f)
                    drawLine(strokeCol, Offset(pad, pad), Offset(pad, pad + cornerLen), strokeWidth = 2f)
                    // Top-Right
                    drawLine(strokeCol, Offset(w - pad, pad), Offset(w - pad - cornerLen, pad), strokeWidth = 2f)
                    drawLine(strokeCol, Offset(w - pad, pad), Offset(w - pad, pad + cornerLen), strokeWidth = 2f)
                    // Bottom-Left
                    drawLine(strokeCol, Offset(pad, h - pad), Offset(pad + cornerLen, h - pad), strokeWidth = 2f)
                    drawLine(strokeCol, Offset(pad, h - pad), Offset(pad, h - pad - cornerLen), strokeWidth = 2f)
                    // Bottom-Right
                    drawLine(strokeCol, Offset(w - pad, h - pad), Offset(w - pad - cornerLen, h - pad), strokeWidth = 2f)
                    drawLine(strokeCol, Offset(w - pad, h - pad), Offset(w - pad, h - pad - cornerLen), strokeWidth = 2f)

                    // Center Focus Reticle
                    drawCircle(
                        color = strokeCol,
                        radius = 18f,
                        center = Offset(w / 2f, h / 2f),
                        style = Stroke(width = 1.5f)
                    )
                }

                // 9. Ganesh Chaturthi Festival Frame
                "ar_ganesh_chaturthi" -> {
                    drawFestivalTempleFrame(w, h, clampedIntensity, ticker)
                }

                // 10. Diya & Floral Garland Frame
                "ar_diya_flower" -> {
                    drawDiyaFloralFrame(w, h, clampedIntensity, ticker)
                }

                // 11. Sacred Lotus Temple Aura
                "ar_temple_lotus" -> {
                    drawLotusTempleAura(w, h, faceCenterX, faceCenterY, clampedIntensity, ticker)
                }
            }
        }

        // --- FESTIVAL GREETING EDITABLE BANNER OVERLAY ---
        if (filter.hasEditableText) {
            val greeting = if (customText.isNotBlank()) customText else filter.defaultGreetingText
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp, start = 20.dp, end = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color(0xFF781E08).copy(alpha = 0.90f * clampedIntensity),
                                Color(0xFFC2410C).copy(alpha = 0.95f * clampedIntensity),
                                Color(0xFF781E08).copy(alpha = 0.90f * clampedIntensity)
                            )
                        )
                    )
                    .border(
                        width = 1.5.dp,
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFFFFD700), Color(0xFFFFA000), Color(0xFFFFD700))
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "✨ $greeting ✨",
                        color = Color(0xFFFFFDFA),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }

        // Retro Date Stamp for Retro 35mm
        if (filter.id == "ar_retro_grain") {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 16.dp, end = 20.dp)
            ) {
                Text(
                    text = "'98 09 18",
                    color = Color(0xFFFF8C00).copy(alpha = 0.85f * clampedIntensity),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// Sparkle Star 4-Point shape for Anime & Big Eyes
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSparkleStar(
    center: Offset,
    size: Float,
    intensity: Float
) {
    val path = Path().apply {
        moveTo(center.x, center.y - size)
        quadraticTo(center.x, center.y, center.x + size, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + size)
        quadraticTo(center.x, center.y, center.x - size, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - size)
        close()
    }
    drawPath(
        path = path,
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.95f * intensity),
                Color(0xFFFFE082).copy(alpha = 0.65f * intensity),
                Color.Transparent
            ),
            center = center,
            radius = size * 1.2f
        )
    )
}

// Sacred Ganesh Chaturthi Arch, Swaying Temple Bells, & Earthen Diyas
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFestivalTempleFrame(
    w: Float,
    h: Float,
    intensity: Float,
    ticker: Float
) {
    // 1. Top Sacred Temple Dome & Arch
    val archHeight = 44f
    drawRect(
        brush = Brush.verticalGradient(
            listOf(
                Color(0xFFFFD700).copy(alpha = 0.45f * intensity),
                Color(0xFFEA580C).copy(alpha = 0.25f * intensity),
                Color.Transparent
            )
        ),
        size = Size(w, archHeight * 2f),
        blendMode = BlendMode.Screen
    )

    // Golden Temple Arch filigree line
    drawLine(
        brush = Brush.horizontalGradient(
            listOf(Color.Transparent, Color(0xFFFFD700).copy(alpha = 0.95f * intensity), Color.Transparent)
        ),
        start = Offset(0f, 6f),
        end = Offset(w, 6f),
        strokeWidth = 3f
    )

    // 2. Hanging Golden Temple Bells (Swaying with ticker)
    val swingAngle = sin(ticker * Math.PI.toFloat() * 2f) * 8f
    drawTempleBell(Offset(w * 0.16f, 10f), 26f, swingAngle, intensity)
    drawTempleBell(Offset(w * 0.84f, 10f), 26f, -swingAngle, intensity)

    // 3. Earthen Clay Diyas with Flickering Warm Flame in Bottom Corners
    val flameFlicker = sin(ticker * Math.PI.toFloat() * 6f) * 4f
    drawDiyaLamp(Offset(36f, h - 34f), flameFlicker, intensity)
    drawDiyaLamp(Offset(w - 36f, h - 34f), -flameFlicker, intensity)
}

// Diya & Floral Garland Frame
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDiyaFloralFrame(
    w: Float,
    h: Float,
    intensity: Float,
    ticker: Float
) {
    // Top Marigold Garland (Alternating Orange & Yellow floral beads)
    val beadCount = 18
    val step = w / beadCount
    for (i in 0..beadCount) {
        val bx = i * step
        val by = 16f + sin((i.toFloat() / beadCount) * Math.PI.toFloat()) * 12f
        val isYellow = i % 2 == 0
        drawCircle(
            color = if (isYellow) Color(0xFFFFD700).copy(alpha = 0.90f * intensity)
            else Color(0xFFF97316).copy(alpha = 0.90f * intensity),
            radius = 7.5f,
            center = Offset(bx, by)
        )
        // Garland bead highlight
        drawCircle(
            color = Color.White.copy(alpha = 0.70f * intensity),
            radius = 2.5f,
            center = Offset(bx - 2f, by - 2f)
        )
    }

    // Corner Diyas
    val flameFlicker = sin(ticker * Math.PI.toFloat() * 5f) * 3f
    drawDiyaLamp(Offset(32f, h - 32f), flameFlicker, intensity)
    drawDiyaLamp(Offset(w - 32f, h - 32f), flameFlicker, intensity)

    // Sparkle dust rising from lamps
    val rng = Random(88)
    for (i in 0 until 12) {
        val sx = rng.nextFloat() * w
        val sy = h - (rng.nextFloat() * 120f + 30f)
        drawCircle(
            color = Color(0xFFFFE082).copy(alpha = (rng.nextFloat() * 0.5f + 0.3f) * intensity),
            radius = rng.nextFloat() * 2f + 1f,
            center = Offset(sx, sy)
        )
    }
}

// Lotus Temple Sacred Aura
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLotusTempleAura(
    w: Float,
    h: Float,
    faceCenterX: Float,
    faceCenterY: Float,
    intensity: Float,
    ticker: Float
) {
    // Lotus bloom crown above head
    val crownY = (faceCenterY - 140f).coerceAtLeast(36f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFD700).copy(alpha = 0.50f * intensity),
                Color(0xFFF97316).copy(alpha = 0.20f * intensity),
                Color.Transparent
            ),
            center = Offset(faceCenterX, crownY),
            radius = 55f
        ),
        center = Offset(faceCenterX, crownY),
        radius = 55f,
        blendMode = BlendMode.Screen
    )

    // Lotus petals
    for (deg in listOf(-35f, -18f, 0f, 18f, 35f)) {
        val rad = Math.toRadians(deg.toDouble())
        val px = faceCenterX + (sin(rad) * 28f).toFloat()
        val py = crownY - (kotlin.math.cos(rad) * 28f).toFloat()
        drawCircle(
            color = Color(0xFFFF80AB).copy(alpha = 0.85f * intensity),
            radius = 6f,
            center = Offset(px, py)
        )
    }

    // Sacred Tilak / Bindi on forehead
    val tilakY = faceCenterY - 45f
    drawCircle(
        color = Color(0xFFDC2626).copy(alpha = 0.90f * intensity),
        radius = 4f,
        center = Offset(faceCenterX, tilakY)
    )
}

// Draw realistic earthen clay Diya lamp with glowing warm flame
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDiyaLamp(
    center: Offset,
    flameFlicker: Float,
    intensity: Float
) {
    // Clay base bowl
    val bowlW = 32f
    val bowlH = 14f
    drawArc(
        brush = Brush.verticalGradient(
            listOf(Color(0xFFB45309), Color(0xFF78350F))
        ),
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(center.x - bowlW / 2f, center.y),
        size = Size(bowlW, bowlH)
    )
    // Diya golden rim
    drawLine(
        color = Color(0xFFFFD700).copy(alpha = 0.90f * intensity),
        start = Offset(center.x - bowlW / 2f, center.y),
        end = Offset(center.x + bowlW / 2f, center.y),
        strokeWidth = 2.5f
    )

    // Warm animated flame
    val flameTipY = center.y - 20f + flameFlicker
    val flamePath = Path().apply {
        moveTo(center.x, flameTipY)
        quadraticTo(center.x + 8f, center.y - 8f, center.x, center.y)
        quadraticTo(center.x - 8f, center.y - 8f, center.x, flameTipY)
        close()
    }
    // Flame glow halo
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFEA00).copy(alpha = 0.65f * intensity),
                Color(0xFFFF9100).copy(alpha = 0.35f * intensity),
                Color.Transparent
            ),
            center = Offset(center.x, center.y - 10f),
            radius = 28f
        ),
        center = Offset(center.x, center.y - 10f),
        radius = 28f,
        blendMode = BlendMode.Screen
    )
    // Core flame body
    drawPath(
        path = flamePath,
        brush = Brush.verticalGradient(
            listOf(Color(0xFFFFFFFF), Color(0xFFFFD600), Color(0xFFFF6D00))
        )
    )
}

// Draw sacred golden temple bell with hanging chain
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTempleBell(
    topAnchor: Offset,
    bellSize: Float,
    swingDeg: Float,
    intensity: Float
) {
    // Hanging golden chain
    drawLine(
        color = Color(0xFFFFD700).copy(alpha = 0.90f * intensity),
        start = topAnchor,
        end = Offset(topAnchor.x + swingDeg * 0.4f, topAnchor.y + 24f),
        strokeWidth = 2f
    )
    val bellCenter = Offset(topAnchor.x + swingDeg * 0.4f, topAnchor.y + 32f)

    // Bell body
    drawArc(
        brush = Brush.verticalGradient(
            listOf(Color(0xFFFFD700), Color(0xFFD97706), Color(0xFF78350F))
        ),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(bellCenter.x - bellSize / 2f, bellCenter.y - bellSize * 0.4f),
        size = Size(bellSize, bellSize * 0.8f)
    )
    // Bell bottom lip
    drawOval(
        color = Color(0xFFFFD700).copy(alpha = 0.95f * intensity),
        topLeft = Offset(bellCenter.x - bellSize / 2f, bellCenter.y + bellSize * 0.35f),
        size = Size(bellSize, 4f)
    )
    // Clapper bead
    drawCircle(
        color = Color(0xFF78350F),
        radius = 3.5f,
        center = Offset(bellCenter.x - swingDeg * 0.3f, bellCenter.y + bellSize * 0.5f)
    )
}
