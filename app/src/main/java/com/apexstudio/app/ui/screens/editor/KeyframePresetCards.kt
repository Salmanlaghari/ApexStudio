package com.apexstudio.app.ui.screens.editor

import android.os.SystemClock
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.apexstudio.app.data.animation.AnimationPresetMode
import com.apexstudio.app.data.animation.AnimationPresetOptions
import com.apexstudio.app.data.animation.AnimationPresetType
import com.apexstudio.app.ui.theme.EditorPalette as ApexPalette
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Compact, horizontal animation preset picker. When [onApplyConfiguredPreset]
 * is supplied, card taps only select a preset; the explicit Apply action sends
 * its mode, duration and intensity to the caller. That callback should build
 * [AnimationPresetOptions.createTrack] and attach that exact track to the clip,
 * keeping live preview and export on the same keyframe path.
 *
 * If the configured callback is absent, cards retain the previous instant-apply
 * behavior via [onApplyPreset] and advanced controls are not shown.
 */
@Composable
fun KeyframePresetCards(
    onApplyPreset: (AnimationPresetType) -> Unit,
    onOpenCustomized: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    canApply: Boolean = true,
    onApplyConfiguredPreset: ((AnimationPresetType, AnimationPresetMode, Long, Float) -> Unit)? = null
) {
    val configured = onApplyConfiguredPreset != null
    var mode by remember { mutableStateOf(AnimationPresetMode.IN) }
    var selectedType by remember { mutableStateOf(AnimationPresetType.ZOOM_IN) }
    var durationMs by remember { mutableLongStateOf(AnimationPresetOptions.DEFAULT_DURATION_MS) }
    var intensity by remember { mutableStateOf(AnimationPresetOptions.DEFAULT_INTENSITY) }
    var lastApplied by remember { mutableStateOf<AnimationPresetType?>(null) }
    var lastApplyAtMs by remember { mutableLongStateOf(0L) }
    var lastApplyType by remember { mutableStateOf<AnimationPresetType?>(null) }

    val visiblePresets = remember(mode, configured) {
        if (configured) presetCardsFor(mode) else PRESET_CARD_DEFS
    }
    LaunchedEffect(lastApplied) {
        if (lastApplied != null) {
            delay(1200)
            lastApplied = null
        }
    }
    LaunchedEffect(mode) {
        if (visiblePresets.none { it.type == selectedType }) {
            selectedType = visiblePresets.firstOrNull()?.type ?: AnimationPresetType.ZOOM_IN
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(ApexPalette.BgElevated)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // The close affordance stays pinned above the horizontal card scroller.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Animation, contentDescription = null, tint = ApexPalette.NeonCyan, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(7.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Animation", color = ApexPalette.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                Text(if (canApply) "Replaces transform keys · fits clip · Undo restores" else "Select a video or photo layer first", color = ApexPalette.TextTertiary, fontSize = 10.sp, maxLines = 1)
            }
            IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = ApexPalette.TextSecondary, modifier = Modifier.size(20.dp))
            }
        }

        if (configured) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AnimationPresetMode.entries.forEach { option ->
                    val selected = mode == option
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selected) ApexPalette.NeonCyan.copy(alpha = 0.2f) else Color(0xFF171C2C))
                            .border(1.dp, if (selected) ApexPalette.NeonCyan else ApexPalette.BorderGlass, RoundedCornerShape(9.dp))
                            .clickable { mode = option },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(option.name.lowercase().replaceFirstChar { it.uppercase() }, color = if (selected) ApexPalette.NeonCyan else ApexPalette.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth().height(if (configured) 94.dp else 108.dp),
            contentPadding = PaddingValues(end = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(visiblePresets, key = { it.label }) { def ->
                PresetCard(
                    def = def,
                    selected = if (configured) selectedType == def.type else lastApplied == def.type,
                    onClick = {
                        if (configured) {
                            selectedType = def.type
                        } else {
                            val now = SystemClock.uptimeMillis()
                            // Preserve the legacy same-card tap debounce.
                            if (def.type == lastApplyType && now - lastApplyAtMs < 400L) return@PresetCard
                            lastApplyAtMs = now
                            lastApplyType = def.type
                            onApplyPreset(def.type)
                            lastApplied = def.type
                        }
                    }
                )
            }
            item(key = "customized") { CustomizedCard(onClick = onOpenCustomized) }
        }

        if (configured) {
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Duration", color = ApexPalette.TextSecondary, fontSize = 10.sp, modifier = Modifier.weight(1f))
                        Text("${durationMs} ms", color = ApexPalette.TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Slider(
                        value = durationMs.toFloat(),
                        onValueChange = { durationMs = (it / 50f).toLong() * 50L },
                        valueRange = AnimationPresetOptions.MIN_DURATION_MS.toFloat()..AnimationPresetOptions.MAX_DURATION_MS.toFloat(),
                        modifier = Modifier.fillMaxWidth().height(32.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Intensity", color = ApexPalette.TextSecondary, fontSize = 10.sp, modifier = Modifier.weight(1f))
                        Text("${(intensity * 100).toInt()}%", color = ApexPalette.TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Slider(
                        value = intensity,
                        onValueChange = { intensity = it },
                        valueRange = AnimationPresetOptions.MIN_INTENSITY..AnimationPresetOptions.MAX_INTENSITY,
                        modifier = Modifier.fillMaxWidth().height(32.dp)
                    )
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth().height(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Brush.horizontalGradient(listOf(ApexPalette.NeonCyan, Color(0xFF6366F1))))
                    .clickable(enabled = canApply) {
                        onApplyConfiguredPreset?.invoke(selectedType, mode, durationMs, intensity)
                        lastApplied = selectedType
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("Apply ${presetLabel(selectedType)}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
    PresetCardDef(AnimationPresetType.BLUR_IN, "Blur In", Color(0xFF06B6D4), Color(0xFF6366F1)),
    PresetCardDef(AnimationPresetType.BLUR_OUT, "Blur Out", Color(0xFF6366F1), Color(0xFF0E7490)),
    PresetCardDef(AnimationPresetType.ZOOM_FADE_IN, "Zoom + Fade", Color(0xFF8B5CF6), Color(0xFFEC4899)),
    PresetCardDef(AnimationPresetType.ZOOM_FADE_OUT, "Zoom Fade Out", Color(0xFFEF4444), Color(0xFFA855F7)),
    PresetCardDef(AnimationPresetType.SLIDE_BOUNCE_LEFT, "Bounce Left", Color(0xFF06B6D4), Color(0xFF0EA5E9)),
    PresetCardDef(AnimationPresetType.SLIDE_BOUNCE_RIGHT, "Bounce Right", Color(0xFF0EA5E9), Color(0xFF22D3EE)),
    PresetCardDef(AnimationPresetType.POP_FADE_IN, "Pop + Fade", Color(0xFF22D3EE), Color(0xFF34D399)),
    PresetCardDef(AnimationPresetType.SPIN_ZOOM_IN, "Spin Zoom", Color(0xFF8B5CF6), Color(0xFF6366F1)),
    PresetCardDef(AnimationPresetType.SLIDE_FADE_UP, "Rise + Fade", Color(0xFF8B5CF6), Color(0xFFEC4899))
)

private fun presetCardsFor(mode: AnimationPresetMode): List<PresetCardDef> {
    val types = when (mode) {
        AnimationPresetMode.IN -> setOf(
            AnimationPresetType.ZOOM_IN, AnimationPresetType.SLIDE_LEFT, AnimationPresetType.SLIDE_RIGHT,
            AnimationPresetType.SLIDE_UP, AnimationPresetType.SLIDE_DOWN, AnimationPresetType.FADE_IN,
            AnimationPresetType.ROTATE, AnimationPresetType.BOUNCE, AnimationPresetType.POP,
            AnimationPresetType.SPRING_IN, AnimationPresetType.BLUR_IN, AnimationPresetType.ZOOM_FADE_IN,
            AnimationPresetType.SLIDE_BOUNCE_LEFT, AnimationPresetType.SLIDE_BOUNCE_RIGHT,
            AnimationPresetType.POP_FADE_IN, AnimationPresetType.SPIN_ZOOM_IN, AnimationPresetType.SLIDE_FADE_UP
        )
        AnimationPresetMode.OUT -> setOf(
            AnimationPresetType.ZOOM_OUT, AnimationPresetType.FADE_OUT, AnimationPresetType.BLUR_OUT,
            AnimationPresetType.ZOOM_FADE_OUT
        )
        AnimationPresetMode.LOOP -> setOf(AnimationPresetType.PULSE, AnimationPresetType.SPIN, AnimationPresetType.SHAKE)
    }
    return PRESET_CARD_DEFS.filter { it.type in types }
}

private fun presetLabel(type: AnimationPresetType): String =
    PRESET_CARD_DEFS.firstOrNull { it.type == type }?.label ?: type.displayName

@Composable
private fun PresetCard(def: PresetCardDef, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(104.dp).height(86.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(listOf(def.start.copy(alpha = 0.28f), def.end.copy(alpha = 0.14f))))
            .border(if (selected) 2.dp else 1.dp, if (selected) def.start else ApexPalette.BorderGlass, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(8.dp))
                .background(Brush.linearGradient(listOf(def.start, def.end))),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(48.dp).background(Color.White.copy(alpha = 0.14f), CircleShape))
            Canvas(Modifier.size(58.dp)) { drawPresetArt(def.type) }
            if (selected) {
                Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(9.dp).background(Color.White, CircleShape))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(def.label, color = ApexPalette.TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun CustomizedCard(onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(104.dp).height(86.dp).clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF161A2B)).border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(listOf(Color(0xFF164E63), Color(0xFF0E7490)))), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(58.dp)) {
                val w = size.width
                val h = size.height
                listOf(0.28f, 0.5f, 0.72f).forEachIndexed { i, fy ->
                    val y = h * fy
                    val kx = w * listOf(0.62f, 0.32f, 0.5f)[i]
                    drawLine(Color.White.copy(alpha = 0.5f), Offset(w * 0.16f, y), Offset(w * 0.84f, y), 4f)
                    drawCircle(Color.White, 6f, Offset(kx, y))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Customized", color = ApexPalette.NeonCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

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
