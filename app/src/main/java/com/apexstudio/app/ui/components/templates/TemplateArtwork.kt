package com.apexstudio.app.ui.components.templates

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.template.ArtMotionStyle
import com.apexstudio.app.data.template.ArtNamePlacement
import com.apexstudio.app.data.template.TemplateArtArchetype
import com.apexstudio.app.data.template.TemplateArtCatalog
import com.apexstudio.app.data.template.TemplateArtSpec
import com.apexstudio.app.data.template.TransmissionTemplate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val ArtDeep = Color(0xFF0B0E14)

/**
 * Renders the per-template preview artwork: a deterministic geometric
 * composition drawn on [Canvas], unique per [TemplateArtArchetype].
 *
 * Used in two places:
 * - the editor's template picker tiles (static, [phase] = 0)
 * - the Home dashboard card header ([TemplateArtHeader], animated via
 *   [phase] driven by an infinite transition according to the spec's
 *   [ArtMotionStyle])
 *
 * Everything is derived from the template id + accent color, so the
 * picker always shows the new designs truthfully with no image assets
 * to ship or regenerate.
 */
@Composable
fun TemplateArtwork(
    spec: TemplateArtSpec,
    accent: Color,
    modifier: Modifier = Modifier,
    phase: Float = 0f,
) {
    val accentSoft = remember(accent) { accent.copy(alpha = 0.55f) }
    val accentFaint = remember(accent) { accent.copy(alpha = 0.22f) }
    Canvas(modifier = modifier) {
        drawArtBackground(accent)
        drawArchetype(spec.archetype, accent, accentSoft, accentFaint, phase)
        // Sweep highlight band shared by SWEEP-style motion; also gives
        // static art a glossy top edge when phase == 0.
        if (spec.motionStyle == ArtMotionStyle.SWEEP) {
            val x = size.width * phase
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = 0.10f),
                    1f to Color.Transparent,
                    startX = x - size.width * 0.18f,
                    endX = x + size.width * 0.18f,
                ),
                size = size,
            )
        }
    }
}

/**
 * Full-width dashboard header: animated artwork with the template name
 * overlaid at the spec's [ArtNamePlacement].
 */
@Composable
fun TemplateArtHeader(
    template: TransmissionTemplate,
    modifier: Modifier = Modifier,
) {
    val spec = remember(template.id) { TemplateArtCatalog.artFor(template.id) }
    val accent = remember(template.previewAccentArgb) {
        Color(template.previewAccentArgb.toULong().toLong())
    }
    val animated = spec.motionStyle != ArtMotionStyle.NONE
    val transition = rememberInfiniteTransition(label = "template-art-${template.id}")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (spec.motionStyle) {
                    ArtMotionStyle.PULSE -> 2400
                    ArtMotionStyle.SWEEP -> 3400
                    ArtMotionStyle.DRIFT -> 7000
                    ArtMotionStyle.RISE -> 4200
                    ArtMotionStyle.NONE -> 1000
                },
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "art-phase",
    )
    Box(modifier = modifier) {
        TemplateArtwork(
            spec = spec,
            accent = accent,
            modifier = Modifier.matchParentSize(),
            phase = if (animated) phase else 0f,
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = when (spec.namePlacement) {
                ArtNamePlacement.TOP_LEFT -> Alignment.TopStart
                ArtNamePlacement.TOP_CENTER -> Alignment.TopCenter
                ArtNamePlacement.BOTTOM_LEFT -> Alignment.BottomStart
                ArtNamePlacement.BOTTOM_CENTER -> Alignment.BottomCenter
                ArtNamePlacement.CENTER -> Alignment.Center
            },
        ) {
            Text(
                text = template.name,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.85f),
                        offset = Offset(1f, 2f),
                        blurRadius = 6f,
                    )
                ),
            )
        }
    }
}

private fun DrawScope.drawArtBackground(accent: Color) {
    drawRect(
        brush = Brush.verticalGradient(
            0f to ArtDeep,
            0.55f to accent.copy(alpha = 0.16f),
            1f to ArtDeep,
        ),
        size = size,
    )
    // Subtle top sheen so every tile reads as lit glass.
    drawRect(
        color = Color.White.copy(alpha = 0.045f),
        topLeft = Offset.Zero,
        size = Size(size.width, size.height * 0.32f),
    )
}

/** Tiny deterministic PRNG so grain/halftone layouts are stable per archetype. */
private class SeededRandom(seed: Int) {
    private var state = (seed * 2654435761L).toInt() and 0x7fffffff
    fun nextFloat(): Float {
        state = ((state * 1103515245L + 12345L) and 0x7fffffff).toInt()
        return state / 0x7fffffff.toFloat()
    }
}

private fun DrawScope.drawArchetype(
    archetype: TemplateArtArchetype,
    accent: Color,
    accentSoft: Color,
    accentFaint: Color,
    phase: Float,
) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val light = Color.White.copy(alpha = 0.85f)
    val pulse = 1f + 0.07f * sin(phase * 2f * PI.toFloat())
    val rise = (1f - phase) * h * 0.25f
    val drift = (phase * w * 0.3f) % (w * 0.3f + 1f)

    when (archetype) {
        TemplateArtArchetype.DIAGONAL_SPLIT -> {
            val path = Path().apply {
                moveTo(w * 0.55f, 0f); lineTo(w, 0f); lineTo(w * 0.45f, h); lineTo(0f, h); close()
            }
            drawPath(path, accentFaint)
            drawLine(light, Offset(w * 0.55f, 0f), Offset(w * 0.45f, h), strokeWidth = 2.5f)
            drawLine(accent, Offset(w * 0.62f, 0f), Offset(w * 0.52f, h), strokeWidth = 6f, alpha = 0.5f)
        }
        TemplateArtArchetype.CIRCLE_ORBIT -> {
            val r = minOf(w, h) * 0.26f * pulse
            drawCircle(accent, r, Offset(cx, cy), style = Stroke(3f))
            drawCircle(accentFaint, r * 1.45f, Offset(cx, cy), style = Stroke(1.5f))
            repeat(8) { i ->
                val a = (i / 8f) * 2f * PI.toFloat() + phase * 2f * PI.toFloat()
                drawCircle(light, 3f, Offset(cx + cos(a) * r * 1.45f, cy + sin(a) * r * 1.45f))
            }
            drawCircle(accent, 5f, Offset(cx, cy))
        }
        TemplateArtArchetype.ARC_SWEEP -> {
            repeat(4) { i ->
                val r = minOf(w, h) * (0.32f + i * 0.14f)
                drawArc(
                    color = if (i == 3) accent else accentFaint,
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(cx - r, cy - r + rise * 0.4f),
                    size = Size(r * 2f, r * 2f),
                    style = Stroke(width = 4f - i * 0.6f),
                )
            }
            drawCircle(accent, 6f, Offset(cx, cy + minOf(w, h) * 0.32f - rise * 0.4f))
        }
        TemplateArtArchetype.SPLIT_GRID -> {
            val gw = w / 2f; val gh = h / 2f
            drawRect(accent, Offset(0f, 0f), Size(gw - 2f, gh - 2f))
            drawRect(accentFaint, Offset(gw + 2f, 0f), Size(gw - 2f, gh - 2f))
            drawRect(Color.Transparent, Offset(0f, gh + 2f), Size(gw - 2f, gh - 2f), style = Stroke(2f))
            drawRect(accentSoft, Offset(gw + 2f, gh + 2f), Size(gw - 2f, gh - 2f), style = Stroke(2f))
            drawCircle(light, 4f, Offset(cx, cy))
        }
        TemplateArtArchetype.WAVEFORM_BARS -> {
            val rnd = SeededRandom(5)
            val n = 14
            val bw = w / n
            repeat(n) { i ->
                val bh = h * (0.15f + rnd.nextFloat() * 0.6f) * (0.7f + 0.3f * pulse)
                drawRoundRect(
                    color = if (i % 3 == 0) accent else accentSoft,
                    topLeft = Offset(i * bw + bw * 0.2f, cy - bh / 2f),
                    size = Size(bw * 0.6f, bh),
                    cornerRadius = CornerRadius(2f, 2f),
                )
            }
            drawLine(light, Offset(0f, cy), Offset(w, cy), strokeWidth = 1f, alpha = 0.4f)
        }
        TemplateArtArchetype.VIGNETTE_FRAME -> {
            val inset = minOf(w, h) * 0.12f
            drawRoundRect(
                accent, Offset(inset, inset), Size(w - inset * 2f, h - inset * 2f),
                CornerRadius(10f, 10f), style = Stroke(5f),
            )
            drawRoundRect(
                light, Offset(inset + 8f, inset + 8f), Size(w - inset * 2f - 16f, h - inset * 2f - 16f),
                CornerRadius(7f, 7f), style = Stroke(1.5f), alpha = 0.5f,
            )
            drawCircle(accentFaint, minOf(w, h) * 0.2f, Offset(cx, cy))
        }
        TemplateArtArchetype.SCAN_BANDS -> {
            var y = (drift * 0.4f) % 9f
            while (y < h) {
                drawLine(accentSoft, Offset(0f, y), Offset(w, y), strokeWidth = 1.6f, alpha = 0.6f)
                y += 9f
            }
            val trackY = (phase * h * 1.4f) % (h * 1.2f) - h * 0.1f
            drawRect(Color.White.copy(alpha = 0.10f), Offset(0f, trackY), Size(w, 7f))
            drawLine(accent, Offset(0f, h * 0.5f), Offset(w, h * 0.5f), strokeWidth = 3f, alpha = 0.7f)
        }
        TemplateArtArchetype.HALFTONE_DOTS -> {
            val rnd = SeededRandom(8)
            val step = minOf(w, h) / 9f
            var y = step / 2f
            while (y < h) {
                var x = step / 2f
                while (x < w) {
                    val d = Offset(x - cx, y - cy).getDistance() / (minOf(w, h) * 0.7f)
                    val r = (1f - d.coerceIn(0f, 1f)) * step * 0.42f * pulse + 0.6f
                    drawCircle(if (rnd.nextFloat() > 0.85f) light else accent, r.coerceAtLeast(0.8f), Offset(x, y))
                    x += step
                }
                y += step
            }
        }
        TemplateArtArchetype.PRISM_TRIANGLE -> {
            val s = minOf(w, h) * 0.72f
            val path = Path().apply {
                moveTo(cx, cy - s / 2f); lineTo(cx + s / 2f, cy + s / 2.4f); lineTo(cx - s / 2f, cy + s / 2.4f); close()
            }
            drawPath(path, Brush.linearGradient(0f to accent, 1f to accentFaint))
            drawPath(path, light, style = Stroke(2.5f), alpha = 0.7f)
            drawLine(accent, Offset(cx + s * 0.32f, cy - s * 0.1f), Offset(w, cy - s * 0.42f), strokeWidth = 3f, alpha = 0.6f)
            drawLine(light, Offset(cx + s * 0.34f, cy + s * 0.02f), Offset(w, cy + s * 0.12f), strokeWidth = 2f, alpha = 0.5f)
        }
        TemplateArtArchetype.SUN_RAYS -> {
            val sunY = h * 0.78f - rise * 0.5f
            repeat(12) { i ->
                val a = PI.toFloat() + (i / 11f) * PI.toFloat()
                val x2 = cx + cos(a) * w * 0.7f
                val y2 = sunY + sin(a) * w * 0.7f
                drawLine(accentSoft, Offset(cx, sunY), Offset(x2, y2), strokeWidth = 3f, alpha = 0.55f)
            }
            drawCircle(accent, minOf(w, h) * 0.20f * pulse, Offset(cx, sunY))
            drawCircle(light, minOf(w, h) * 0.20f * pulse, Offset(cx, sunY), style = Stroke(2f), alpha = 0.8f)
        }
        TemplateArtArchetype.ZIGZAG_BOLT -> {
            val path = Path().apply {
                val bx = cx + drift * 0.2f
                moveTo(bx + w * 0.08f, h * 0.08f)
                lineTo(bx - w * 0.14f, h * 0.52f); lineTo(bx + w * 0.02f, h * 0.52f)
                lineTo(bx - w * 0.08f, h * 0.92f); lineTo(bx + w * 0.16f, h * 0.44f)
                lineTo(bx - w * 0.02f, h * 0.44f); close()
            }
            drawPath(path, accentFaint, style = Stroke(10f), alpha = 0.5f)
            drawPath(path, accent)
            drawPath(path, light, style = Stroke(1.5f), alpha = 0.8f)
        }
        TemplateArtArchetype.CROSSHAIR -> {
            val r = minOf(w, h) * 0.30f * pulse
            drawCircle(accent, r, Offset(cx, cy), style = Stroke(3f))
            drawLine(accentSoft, Offset(cx - r - 8f, cy), Offset(cx + r + 8f, cy), 2f)
            drawLine(accentSoft, Offset(cx, cy - r - 8f), Offset(cx, cy + r + 8f), 2f)
            drawCircle(light, 3.5f, Offset(cx, cy))
            val c = minOf(w, h) * 0.08f
            listOf(Offset(c, c), Offset(w - c, c), Offset(c, h - c), Offset(w - c, h - c)).forEach {
                drawCircle(accentFaint, 5f, it, style = Stroke(2f))
            }
        }
        TemplateArtArchetype.FILM_STRIP -> {
            val band = h * 0.18f
            drawRect(accentFaint, Offset(0f, 0f), Size(w, band))
            drawRect(accentFaint, Offset(0f, h - band), Size(w, band))
            val n = 8
            repeat(n) { i ->
                val x = (i + 0.5f) * w / n - drift * 0.3f
                drawRoundRect(ArtDeep, Offset(x - 5f, band * 0.28f), Size(10f, band * 0.44f), CornerRadius(2f, 2f))
                drawRoundRect(ArtDeep, Offset(x - 5f, h - band + band * 0.28f), Size(10f, band * 0.44f), CornerRadius(2f, 2f))
            }
            drawLine(accent, Offset(0f, cy), Offset(w, cy), 2.5f, alpha = 0.8f)
            drawCircle(accentSoft, 7f, Offset(cx, cy))
        }
        TemplateArtArchetype.POLAROID -> {
            val m = minOf(w, h) * 0.14f
            drawRoundRect(Color.White.copy(alpha = 0.92f), Offset(m, m * 0.7f), Size(w - m * 2f, h - m * 1.5f), CornerRadius(4f, 4f))
            drawRect(accent, Offset(m * 1.7f, m * 1.25f), Size(w - m * 3.4f, h - m * 3.6f))
            drawCircle(light, 6f, Offset(cx, m * 1.25f + (h - m * 3.6f) * 0.4f), alpha = 0.85f)
            drawLine(ArtDeep.copy(alpha = 0.5f), Offset(m * 1.7f, h - m * 1.9f), Offset(w - m * 1.7f, h - m * 1.9f), 2f)
        }
        TemplateArtArchetype.HORIZON_LINE -> {
            val hy = h * 0.62f
            drawLine(accent, Offset(0f, hy), Offset(w, hy), 3f)
            drawCircle(accent, minOf(w, h) * 0.16f * pulse, Offset(cx, hy - minOf(w, h) * 0.22f + rise * 0.3f))
            drawRect(
                Brush.verticalGradient(0f to accentFaint, 1f to Color.Transparent),
                Offset(0f, hy), Size(w, h - hy),
            )
            drawLine(light, Offset(w * 0.2f, hy + h * 0.12f), Offset(w * 0.8f, hy + h * 0.12f), 1.5f, alpha = 0.5f)
        }
        TemplateArtArchetype.MOSAIC_DIAMONDS -> {
            val s = minOf(w, h) / 5f
            var row = 0
            var y = s / 2f
            while (y < h + s) {
                var x = s / 2f + (if (row % 2 == 0) 0f else s / 2f)
                while (x < w + s) {
                    val path = Path().apply {
                        moveTo(x, y - s * 0.42f); lineTo(x + s * 0.42f, y); lineTo(x, y + s * 0.42f); lineTo(x - s * 0.42f, y); close()
                    }
                    if ((row + (x / s).toInt()) % 3 == 0) drawPath(path, accentFaint) else drawPath(path, accentSoft, style = Stroke(1.5f))
                    x += s
                }
                y += s * 0.86f; row++
            }
        }
        TemplateArtArchetype.SPIRAL_SQUARES -> {
            var s = minOf(w, h) * 0.86f
            var i = 0
            while (s > 8f) {
                val off = (minOf(w, h) * 0.86f - s) / 2f
                if (i % 2 == 0) {
                    drawRect(accentFaint, Offset(off, off), Size(s, s), style = Stroke(2.5f))
                } else {
                    drawRect(accentSoft, Offset(off + 3f, off + 3f), Size(s - 6f, s - 6f), style = Stroke(1.5f), alpha = 0.7f)
                }
                s *= 0.78f; i++
            }
            drawCircle(accent, 5f, Offset(cx, cy))
        }
        TemplateArtArchetype.CHEVRON -> {
            val shift = (phase * w * 0.25f) % (w * 0.25f + 1f)
            repeat(3) { i ->
                val x = w * (0.18f + i * 0.24f) - shift * 0.4f + w * 0.06f
                val path = Path().apply {
                    moveTo(x, h * 0.2f); lineTo(x + w * 0.13f, cy); lineTo(x, h * 0.8f)
                }
                drawPath(path, if (i == 2) accent else accentSoft, style = Stroke(9f - i * 2f))
            }
        }
        TemplateArtArchetype.TOP_BANNER -> {
            val bh = h * 0.34f
            val path = Path().apply {
                moveTo(0f, 0f); lineTo(w, 0f); lineTo(w, bh)
                lineTo(cx + w * 0.08f, bh); lineTo(cx, bh + h * 0.09f); lineTo(cx - w * 0.08f, bh)
                lineTo(0f, bh); close()
            }
            drawPath(path, Brush.verticalGradient(0f to accent, 1f to accentSoft))
            drawLine(light, Offset(w * 0.3f, bh * 0.45f), Offset(w * 0.7f, bh * 0.45f), 3f, alpha = 0.8f)
            drawCircle(light, 3f, Offset(cx, bh * 0.72f))
        }
        TemplateArtArchetype.SIDE_RAIL -> {
            val rw = w * 0.16f
            drawRect(accentFaint, Offset(0f, 0f), Size(rw, h))
            drawRect(accent, Offset(rw, 0f), Size(3f, h))
            repeat(3) { i ->
                val y = h * (0.2f + i * 0.3f) + rise * 0.3f
                drawCircle(if (i == 1) accent else accentSoft, 6f, Offset(rw / 2f, y))
            }
            drawLine(light, Offset(rw + 14f, h * 0.5f), Offset(w - 12f, h * 0.5f), 2f, alpha = 0.6f)
        }
        TemplateArtArchetype.CORNER_FOLD -> {
            val f = minOf(w, h) * 0.42f
            val path = Path().apply {
                moveTo(w - f, 0f); lineTo(w, 0f); lineTo(w, f); close()
            }
            drawPath(path, accentFaint)
            val fold = Path().apply {
                moveTo(w - f, 0f); lineTo(w - f * 0.45f, f * 0.12f); lineTo(w - f * 0.12f, f * 0.45f); lineTo(w, f); close()
            }
            drawPath(fold, light, alpha = 0.28f)
            drawLine(accent, Offset(w - f, 0f), Offset(w, f), 3f)
            drawCircle(accent, 7f * pulse, Offset(w * 0.3f, h * 0.62f))
            drawCircle(accentSoft, 12f * pulse, Offset(w * 0.3f, h * 0.62f), style = Stroke(2f))
        }
        TemplateArtArchetype.STACKED_CARDS -> {
            val cw = w * 0.52f; val ch = h * 0.6f
            val dx = w * 0.09f; val dy = h * 0.07f
            drawRoundRect(accentFaint, Offset(w * 0.14f, h * 0.12f), Size(cw, ch), CornerRadius(8f, 8f))
            drawRoundRect(accentSoft, Offset(w * 0.14f + dx, h * 0.12f + dy), Size(cw, ch), CornerRadius(8f, 8f))
            drawRoundRect(accent, Offset(w * 0.14f + dx * 2f, h * 0.12f + dy * 2f), Size(cw, ch), CornerRadius(8f, 8f))
            drawLine(light, Offset(w * 0.14f + dx * 2f + 10f, h * 0.12f + dy * 2f + ch * 0.4f),
                Offset(w * 0.14f + dx * 2f + cw - 10f, h * 0.12f + dy * 2f + ch * 0.4f), 2.5f, alpha = 0.8f)
        }
        TemplateArtArchetype.GRAIN_FIELD -> {
            val rnd = SeededRandom(23)
            repeat(130) {
                val x = rnd.nextFloat() * w
                val y = rnd.nextFloat() * h
                drawCircle(
                    if (rnd.nextFloat() > 0.9f) light else accent,
                    0.8f + rnd.nextFloat() * 2.2f,
                    Offset(x, y),
                    alpha = 0.25f + rnd.nextFloat() * 0.5f,
                )
            }
            drawCircle(accentFaint, minOf(w, h) * 0.3f, Offset(cx, cy))
        }
        TemplateArtArchetype.RING_PULSE -> {
            repeat(3) { i ->
                val r = minOf(w, h) * (0.16f + i * 0.13f) * (1f + 0.10f * sin(phase * 2f * PI.toFloat() - i * 0.9f))
                drawCircle(if (i == 0) accent else accentSoft, r, Offset(cx, cy), style = Stroke(4f - i))
            }
            drawCircle(light, 4f, Offset(cx, cy))
        }
        TemplateArtArchetype.PIXEL_BLOCKS -> {
            val rnd = SeededRandom(25)
            val n = 8
            val s = minOf(w, h) / n
            val ox = (w - s * n) / 2f; val oy = (h - s * n) / 2f
            repeat(n) { r ->
                repeat(n) { c ->
                    val v = rnd.nextFloat()
                    if (v > 0.45f) {
                        drawRect(
                            if (v > 0.8f) accent else accentFaint,
                            Offset(ox + c * s + 1f, oy + r * s + 1f),
                            Size(s - 2f, s - 2f),
                        )
                    }
                }
            }
            drawRect(light, Offset(ox - 4f, oy - 4f), Size(s * n + 8f, s * n + 8f), style = Stroke(2f), alpha = 0.5f)
        }
        TemplateArtArchetype.DIAGONAL_STRIPES -> {
            val stripeW = w * 0.09f
            var x = -h + drift
            var i = 0
            while (x < w + h) {
                if (i % 3 == 0) {
                    val path = Path().apply {
                        moveTo(x, 0f); lineTo(x + stripeW, 0f); lineTo(x + stripeW - h * 0.5f, h); lineTo(x - h * 0.5f, h); close()
                    }
                    drawPath(path, accentFaint)
                }
                x += stripeW * 2.2f; i++
            }
            drawLine(accent, Offset(0f, h * 0.72f), Offset(w, h * 0.28f), 5f)
        }
        TemplateArtArchetype.CURVE_WAVE -> {
            val path = Path().apply {
                moveTo(0f, h)
                lineTo(0f, cy + rise * 0.3f)
                var x = 0f
                while (x <= w) {
                    lineTo(x, cy + sin(x / w * 2f * PI.toFloat() + phase * 2f * PI.toFloat()) * h * 0.14f + rise * 0.3f)
                    x += w / 40f
                }
                lineTo(w, h); close()
            }
            drawPath(path, Brush.verticalGradient(0f to accentSoft, 1f to accentFaint))
            drawCircle(light, 8f * pulse, Offset(w * 0.72f, h * 0.24f), alpha = 0.9f)
        }
        TemplateArtArchetype.TARGET_RINGS -> {
            val base = minOf(w, h) * 0.42f
            drawCircle(accentFaint, base, Offset(cx, cy))
            drawCircle(ArtDeep, base * 0.72f, Offset(cx, cy))
            drawCircle(accentSoft, base * 0.5f * pulse, Offset(cx, cy))
            drawCircle(ArtDeep, base * 0.28f, Offset(cx, cy))
            drawCircle(accent, base * 0.12f * pulse, Offset(cx, cy))
        }
        TemplateArtArchetype.PLUS_GRID -> {
            val step = minOf(w, h) / 4.5f
            var y = step / 2f + (drift * 0.2f % step)
            while (y < h + step) {
                var x = step / 2f
                while (x < w + step) {
                    val s = step * 0.22f
                    drawLine(accentSoft, Offset(x - s, y), Offset(x + s, y), 2f, alpha = 0.8f)
                    drawLine(accentSoft, Offset(x, y - s), Offset(x, y + s), 2f, alpha = 0.8f)
                    x += step
                }
                y += step
            }
            drawCircle(accent, 6f * pulse, Offset(cx, cy))
        }
        TemplateArtArchetype.BOTTOM_SHEET -> {
            val top = h * (0.52f - phase * 0.06f)
            drawRoundRect(
                Brush.verticalGradient(0f to accentSoft, 1f to accentFaint),
                Offset(0f, top), Size(w, h - top),
                CornerRadius(14f, 14f),
            )
            drawLine(light, Offset(cx - w * 0.12f, top + 8f), Offset(cx + w * 0.12f, top + 8f), 3f, alpha = 0.7f)
            drawCircle(accent, 5f, Offset(w * 0.2f, top + h * 0.2f))
        }
        TemplateArtArchetype.VERTICAL_THIRDS -> {
            val tw = w / 3f
            drawRect(accent, Offset(0f, 0f), Size(tw, h))
            drawRect(light, Offset(tw, 0f), Size(tw, h), alpha = 0.16f)
            drawRect(accentFaint, Offset(tw * 2f, 0f), Size(tw, h))
            drawLine(ArtDeep, Offset(tw, 0f), Offset(tw, h), 3f)
            drawLine(ArtDeep, Offset(tw * 2f, 0f), Offset(tw * 2f, h), 3f)
            drawCircle(ArtDeep, 9f, Offset(cx, cy))
            drawCircle(accent, 9f, Offset(cx, cy), style = Stroke(2.5f))
        }
        TemplateArtArchetype.X_CROSS -> {
            drawLine(accentFaint, Offset(0f, 0f), Offset(w, h), 14f)
            drawLine(accent, Offset(w, 0f), Offset(0f, h), 14f)
            drawLine(light, Offset(w, 0f), Offset(0f, h), 2.5f, alpha = 0.7f)
            drawCircle(ArtDeep, 11f * pulse, Offset(cx, cy))
            drawCircle(accent, 11f * pulse, Offset(cx, cy), style = Stroke(3f))
        }
        TemplateArtArchetype.CONCENTRIC_BURST -> {
            repeat(16) { i ->
                val a = (i / 16f) * 2f * PI.toFloat()
                val r1 = minOf(w, h) * 0.16f
                val r2 = minOf(w, h) * (0.30f + 0.06f * sin(phase * 2f * PI.toFloat() + i))
                drawLine(
                    if (i % 4 == 0) accent else accentSoft,
                    Offset(cx + cos(a) * r1, cy + sin(a) * r1),
                    Offset(cx + cos(a) * r2, cy + sin(a) * r2),
                    3f,
                )
            }
            drawCircle(accent, 8f * pulse, Offset(cx, cy))
            drawCircle(light, 8f * pulse, Offset(cx, cy), style = Stroke(1.5f), alpha = 0.8f)
        }
    }
}
