package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.animation.AnimationPresetType
import com.apexstudio.app.ui.theme.ApexPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Visually-designed animation preset CARDS for the Keyframe Studio.
 *
 * Prince's brief: "manual sliders ki jagah ready-made animation CARDS banao —
 * user card tap kare, video par apply ho jaye. Cards visually designed hon
 * (template cards jaisa), sirf text chips nahi."
 *
 * Tapping a card applies real keyframe data to the selected clip via
 * [onApplyPreset] → same pipeline as before, so the animation plays in the
 * LIVE PREVIEW and in EXPORT (preview+export parity).
 *
 * The "Customized" card opens the existing manual KeyframePanel (sliders)
 * through [onOpenCustomized] — the manual panel is reachable ONLY from there.
 */
@Composable
fun KeyframePresetCards(
    onApplyPreset: (AnimationPresetType) -> Unit,
    onOpenCustomized: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var lastApplied by remember { mutableStateOf<AnimationPresetType?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(ApexPalette.BgElevated)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(14.dp)
    ) {
        // --- Header ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Animation,
                contentDescription = null,
                tint = ApexPalette.NeonCyan,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Animation Cards",
                    color = ApexPalette.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Tap a card — applies to the selected clip instantly",
                    color = ApexPalette.TextTertiary,
                    fontSize = 10.sp
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = ApexPalette.TextSecondary, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.height(10.dp))

        // Adaptive grid: 3 columns in portrait, more in landscape — no frame issues.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp),
            contentPadding = PaddingValues(2.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(PRESET_CARD_DEFS, key = { it.label }) { def ->
                PresetCard(
                    def = def,
                    applied = lastApplied == def.type,
                    onClick = {
                        onApplyPreset(def.type)
                        lastApplied = def.type
                    }
                )
            }
            item(key = "customized") {
                CustomizedCard(onClick = onOpenCustomized)
            }
        }
    }
}

private data class PresetCardDef(
    val type: AnimationPresetType,
    val label: String,
    val start: Color,
    val end: Color
)

private val PRESET_CARD_DEFS = listOf(
    PresetCardDef(AnimationPresetType.ZOOM_IN, "Zoom In", Color(0xFF0EA5E9), Color(0xFF6366F1)),
    PresetCardDef(AnimationPresetType.ZOOM_OUT, "Zoom Out", Color(0xFF6366F1), Color(0xFFA855F7)),
    PresetCardDef(AnimationPresetType.SLIDE_LEFT, "Slide Left", Color(0xFF06B6D4), Color(0xFF0EA5E9)),
    PresetCardDef(AnimationPresetType.SLIDE_RIGHT, "Slide Right", Color(0xFF0EA5E9), Color(0xFF22D3EE)),
    PresetCardDef(AnimationPresetType.SLIDE_UP, "Slide Up", Color(0xFF8B5CF6), Color(0xFFEC4899)),
    PresetCardDef(AnimationPresetType.SLIDE_DOWN, "Slide Down", Color(0xFFEC4899), Color(0xFFF43F5E)),
    PresetCardDef(AnimationPresetType.FADE_IN, "Fade In", Color(0xFF10B981), Color(0xFF14B8A6)),
    PresetCardDef(AnimationPresetType.FADE_OUT, "Fade Out", Color(0xFF14B8A6), Color(0xFF0E7490)),
    PresetCardDef(AnimationPresetType.ROTATE, "Rotate", Color(0xFFF59E0B), Color(0xFFEF4444)),
    PresetCardDef(AnimationPresetType.BOUNCE, "Bounce", Color(0xFFFBBF24), Color(0xFFF97316)),
    PresetCardDef(AnimationPresetType.PULSE, "Pulse", Color(0xFFEF4444), Color(0xFFEC4899)),
    PresetCardDef(AnimationPresetType.SPIN, "Spin", Color(0xFF8B5CF6), Color(0xFF6366F1)),
    PresetCardDef(AnimationPresetType.POP, "Pop", Color(0xFF22D3EE), Color(0xFF34D399)),
    PresetCardDef(AnimationPresetType.SPRING_IN, "Spring In", Color(0xFF34D399), Color(0xFF0EA5E9)),
    PresetCardDef(AnimationPresetType.SHAKE, "Shake", Color(0xFFF97316), Color(0xFFEF4444)),
)

@Composable
private fun PresetCard(
    def: PresetCardDef,
    applied: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(listOf(def.start.copy(alpha = 0.28f), def.end.copy(alpha = 0.14f)))
            )
            .border(
                width = if (applied) 2.dp else 1.dp,
                color = if (applied) def.start else ApexPalette.BorderGlass,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.35f)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(listOf(def.start, def.end))
                ),
            contentAlignment = Alignment.Center
        ) {
            // Soft glow disc behind the art
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color.White.copy(alpha = 0.14f), CircleShape)
            )
            Canvas(modifier = Modifier.size(72.dp)) {
                drawPresetArt(def.type)
            }
            if (applied) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(18.dp)
                        .background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(10.dp)) {
                        val w = size.width
                        val path = Path().apply {
                            moveTo(w * 0.2f, w * 0.52f)
                            lineTo(w * 0.45f, w * 0.75f)
                            lineTo(w * 0.82f, w * 0.28f)
                        }
                        drawPath(path, color = def.start, style = Stroke(width = 3f))
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = def.label,
            color = ApexPalette.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CustomizedCard(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF161A2B))
            .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.35f)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(listOf(Color(0xFF164E63), Color(0xFF0E7490)))
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(72.dp)) {
                // Sliders graphic: three horizontal lines with knobs
                val w = size.width
                val h = size.height
                val rows = listOf(0.28f, 0.5f, 0.72f)
                val knobX = listOf(0.62f, 0.32f, 0.5f)
                rows.forEachIndexed { i, fy ->
                    val y = h * fy
                    drawLine(
                        color = Color.White.copy(alpha = 0.45f),
                        start = Offset(w * 0.16f, y),
                        end = Offset(w * 0.84f, y),
                        strokeWidth = 4f
                    )
                    val kx = w * knobX[i]
                    drawLine(
                        color = Color.White,
                        start = Offset(kx - w * 0.22f, y),
                        end = Offset(kx, y),
                        strokeWidth = 4f
                    )
                    drawCircle(color = Color.White, radius = 9f, center = Offset(kx, y))
                    drawCircle(color = Color(0xFF0E7490), radius = 4.5f, center = Offset(kx, y))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Customized",
            color = ApexPalette.NeonCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Mini motion-graphic art per preset, drawn in white over the card gradient.
 * Each graphic hints at the actual animation (expanding box, motion trail,
 * circular arrow, bounce arc, ...).
 */
private fun DrawScope.drawPresetArt(type: AnimationPresetType) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val ink = Color.White
    val soft = Color.White.copy(alpha = 0.45f)

    fun box(x: Float, y: Float, half: Float, color: Color = ink, stroke: Float = 5f) {
        drawRoundRectPlaceholder(x - half, y - half, half * 2f, color, stroke)
    }

    when (type) {
        AnimationPresetType.ZOOM_IN -> {
            // Small box → big box, expanding outward arrows
            box(cx, cy, 10f, soft)
            box(cx, cy, 20f)
            val corners = listOf(
                Offset(cx - 26f, cy - 26f) to Offset(cx - 34f, cy - 34f),
                Offset(cx + 26f, cy - 26f) to Offset(cx + 34f, cy - 34f),
                Offset(cx - 26f, cy + 26f) to Offset(cx - 34f, cy + 34f),
                Offset(cx + 26f, cy + 26f) to Offset(cx + 34f, cy + 34f),
            )
            corners.forEach { (from, to) ->
                drawLine(ink, from, to, strokeWidth = 4f)
                drawCircle(ink, 3f, to)
            }
        }
        AnimationPresetType.ZOOM_OUT -> {
            // Big box → small box, inward arrows
            box(cx, cy, 22f, soft)
            box(cx, cy, 10f)
            val corners = listOf(
                Offset(cx - 36f, cy - 36f) to Offset(cx - 26f, cy - 26f),
                Offset(cx + 36f, cy - 36f) to Offset(cx + 26f, cy - 26f),
                Offset(cx - 36f, cy + 36f) to Offset(cx - 26f, cy + 26f),
                Offset(cx + 36f, cy + 36f) to Offset(cx + 26f, cy + 26f),
            )
            corners.forEach { (from, to) ->
                drawLine(ink, from, to, strokeWidth = 4f)
                drawCircle(ink, 3f, to)
            }
        }
        AnimationPresetType.SLIDE_LEFT -> {
            drawSlideTrail(dx = -1f)
            box(cx + 6f, cy, 14f)
        }
        AnimationPresetType.SLIDE_RIGHT -> {
            drawSlideTrail(dx = 1f)
            box(cx - 6f, cy, 14f)
        }
        AnimationPresetType.SLIDE_UP -> {
            drawSlideTrail(dy = -1f)
            box(cx, cy + 6f, 14f)
        }
        AnimationPresetType.SLIDE_DOWN -> {
            drawSlideTrail(dy = 1f)
            box(cx, cy - 6f, 14f)
        }
        AnimationPresetType.FADE_IN -> {
            // Faint rings → solid core
            drawCircle(soft, 24f, Offset(cx, cy), style = Stroke(4f))
            drawCircle(Color.White.copy(alpha = 0.7f), 16f, Offset(cx, cy), style = Stroke(4f))
            drawCircle(ink, 8f, Offset(cx, cy))
        }
        AnimationPresetType.FADE_OUT -> {
            // Solid core → faint rings
            drawCircle(ink, 8f, Offset(cx, cy))
            drawCircle(Color.White.copy(alpha = 0.7f), 16f, Offset(cx, cy), style = Stroke(4f))
            drawCircle(soft, 24f, Offset(cx, cy), style = Stroke(4f))
        }
        AnimationPresetType.ROTATE -> {
            // Rotated square + curved arrow
            drawRotatedBox(cx, cy, 16f, 30f)
            val radius = 30f
            val path = Path()
            for (a in 20..300 step 6) {
                val rad = a * PI.toFloat() / 180f
                val p = Offset(cx + radius * cos(rad), cy + radius * sin(rad))
                if (a == 20) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            drawPath(path, soft, style = Stroke(4f))
            val tip = Offset(cx + radius * cos(20f * PI.toFloat() / 180f), cy + radius * sin(20f * PI.toFloat() / 180f))
            drawCircle(ink, 4f, tip)
        }
        AnimationPresetType.BOUNCE -> {
            // Bounce arc with dots
            val path = Path()
            path.moveTo(cx - 26f, cy - 18f)
            path.quadraticBezierTo(cx - 13f, cy + 14f, cx, cy - 6f)
            path.quadraticBezierTo(cx + 13f, cy + 2f, cx + 26f, cy - 10f)
            drawPath(path, soft, style = Stroke(4f))
            drawCircle(ink, 7f, Offset(cx - 26f, cy - 18f))
            drawCircle(ink, 7f, Offset(cx, cy - 6f))
            drawCircle(ink, 7f, Offset(cx + 26f, cy - 10f))
        }
        AnimationPresetType.PULSE -> {
            // Expanding heartbeat rings
            drawCircle(soft, 26f, Offset(cx, cy), style = Stroke(4f))
            drawCircle(Color.White.copy(alpha = 0.7f), 18f, Offset(cx, cy), style = Stroke(5f))
            drawCircle(ink, 10f, Offset(cx, cy))
        }
        AnimationPresetType.SPIN -> {
            // Full circular arrow
            val radius = 24f
            val path = Path()
            for (a in 0..320 step 6) {
                val rad = a * PI.toFloat() / 180f
                val p = Offset(cx + radius * cos(rad), cy + radius * sin(rad))
                if (a == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            drawPath(path, ink, style = Stroke(5f))
            val tip = Offset(cx + radius, cy)
            drawCircle(ink, 5f, tip)
            drawCircle(ink, 6f, Offset(cx, cy), style = Stroke(4f))
        }
        AnimationPresetType.POP -> {
            // Starburst behind box
            for (i in 0 until 8) {
                val rad = i * PI.toFloat() / 4f
                val from = Offset(cx + 16f * cos(rad), cy + 16f * sin(rad))
                val to = Offset(cx + 28f * cos(rad), cy + 28f * sin(rad))
                drawLine(soft, from, to, strokeWidth = 4f)
            }
            box(cx, cy, 13f)
        }
        AnimationPresetType.SPRING_IN -> {
            // Spring coil resolving into box
            val path = Path()
            val coils = 3
            for (i in 0..(coils * 20)) {
                val t = i.toFloat() / (coils * 20)
                val x = cx - 30f + t * 44f
                val y = cy - 14f + (if (i % 20 < 10) -8f else 8f) * (1f - t * 0.5f)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, soft, style = Stroke(4f))
            box(cx + 20f, cy, 11f)
        }
        AnimationPresetType.SHAKE -> {
            // Horizontal zigzag motion
            val path = Path()
            val ys = listOf(0f, -8f, 8f, -8f, 8f, 0f)
            ys.forEachIndexed { i, dy ->
                val x = cx - 28f + i * (56f / (ys.size - 1))
                val y = cy + dy
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, ink, style = Stroke(5f))
        }
        // Combo presets reuse nearest base art if ever surfaced here.
        AnimationPresetType.BLUR_IN -> {
            box(cx, cy, 16f, soft)
            box(cx, cy, 12f)
        }
        AnimationPresetType.BLUR_OUT -> {
            box(cx, cy, 16f)
            box(cx, cy, 12f, soft)
        }
        else -> {
            drawCircle(ink, 12f, Offset(cx, cy))
        }
    }
}

private fun DrawScope.drawRoundRectPlaceholder(left: Float, top: Float, side: Float, color: Color, stroke: Float) {
    val path = Path().apply {
        val r = side * 0.22f
        moveTo(left + r, top)
        lineTo(left + side - r, top)
        quadraticBezierTo(left + side, top, left + side, top + r)
        lineTo(left + side, top + side - r)
        quadraticBezierTo(left + side, top + side, left + side - r, top + side)
        lineTo(left + r, top + side)
        quadraticBezierTo(left, top + side, left, top + side - r)
        lineTo(left, top + r)
        quadraticBezierTo(left, top, left + r, top)
        close()
    }
    drawPath(path, color, style = Stroke(stroke))
}

private fun DrawScope.drawRotatedBox(cx: Float, cy: Float, half: Float, angleDeg: Float) {
    val rad = angleDeg * PI.toFloat() / 180f
    val cosA = cos(rad)
    val sinA = sin(rad)
    fun rot(px: Float, py: Float): Offset {
        val dx = px - cx
        val dy = py - cy
        return Offset(cx + dx * cosA - dy * sinA, cy + dx * sinA + dy * cosA)
    }
    val pts = listOf(
        rot(cx - half, cy - half),
        rot(cx + half, cy - half),
        rot(cx + half, cy + half),
        rot(cx - half, cy + half),
    )
    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        lineTo(pts[1].x, pts[1].y)
        lineTo(pts[2].x, pts[2].y)
        lineTo(pts[3].x, pts[3].y)
        close()
    }
    drawPath(path, Color.White, style = Stroke(5f))
}

private fun DrawScope.drawSlideTrail(dx: Float = 0f, dy: Float = 0f) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    // Three chevrons in the motion direction
    for (i in 0 until 3) {
        val off = (i - 1) * 14f
        val bx = cx + dx * (off - dx * 10f)
        val by = cy + dy * (off - dy * 10f)
        val s = 7f
        val p1: Offset
        val p2: Offset
        val p3: Offset
        if (dx != 0f) {
            val dir = if (dx < 0) -1f else 1f
            p1 = Offset(bx - dir * s, by - s)
            p2 = Offset(bx, by)
            p3 = Offset(bx - dir * s, by + s)
        } else {
            val dir = if (dy < 0) -1f else 1f
            p1 = Offset(bx - s, by - dir * s)
            p2 = Offset(bx, by)
            p3 = Offset(bx + s, by - dir * s)
        }
        val alpha = 0.4f + i * 0.3f
        val path = Path().apply {
            moveTo(p1.x, p1.y)
            lineTo(p2.x, p2.y)
            lineTo(p3.x, p3.y)
        }
        drawPath(path, Color.White.copy(alpha = alpha.coerceAtMost(1f)), style = Stroke(4f))
    }
}
