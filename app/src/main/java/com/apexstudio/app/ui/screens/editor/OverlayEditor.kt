package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.OverlayClip
import com.apexstudio.app.ui.theme.ApexPalette
import com.apexstudio.app.util.TimeFormat

/**
 * OverlayClip edit section — Bug 3's "OverlayEditor".
 *
 * Two jobs the overlay sheet did not have before this:
 *
 * **Layout.** Position / scale / rotation were reachable only by
 * dragging and pinching the preview, which cannot express a value —
 * you cannot drag an overlay to exactly 35% width or to -45.0°. The
 * three sliders are pure mirrors of the gesture fields, clamped by
 * [OverlayClip]'s own bounds so a slider can never produce something the
 * pinch could not. Getting three numbers drift-free means one write per
 * tick: the four floats are submitted together as a unit
 * (`onLayout(x, y, scale, rotationDeg)`), so a fast drag of two sliders
 * cannot leave the clip on a half-applied combination of both.
 *
 * **Trim.** An overlay plays its own media (louding in as a source
 * window) and had no start/end editing before this - the video trim
 * sheet edits timeline placement of V1 clips, which is the wrong object.
 * Both handles are expressed as fractions of that media's own duration
 * so a slider drag stays stable across rotation changes: the value
 * round-trips through `MediaClip.trimStartMs/trimEndMs`, never through
 * a stored slider position.
 *
 * Everything is read through [OverlayClip], i.e. clamped rather than
 * assumed valid, so a project with a stale/out-of-range value still
 * renders meaningful slider positions instead of throwing.
 */
@Composable
fun OverlayEditor(
    clip: MediaClip,
    onLayout: (x: Float, y: Float, scale: Float, rotationDeg: Float) -> Unit,
    onTrim: (startMs: Long, endMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    // Derive the view once per clip/property change instead of per frame:
    // a slider tick must not re-clamp its own outgoing value.
    val view = remember(clip) {
        OverlayClip.from(clip)
    }
    val source = clip.durationMs.coerceAtLeast(1L)

    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel("POSITION & SCALE")
        SliderRow(
            label = "Position X",
            valueLabel = "${(view.x * 100f).toInt()}%",
            value = view.x.coerceIn(0f, 1f),
            onValueChange = { onLayout(it, view.y, view.scale, view.rotationDeg) }
        )
        SliderRow(
            label = "Position Y",
            valueLabel = "${(view.y * 100f).toInt()}%",
            value = view.y.coerceIn(0f, 1f),
            onValueChange = { onLayout(view.x, it, view.scale, view.rotationDeg) }
        )
        Spacer(Modifier.height(8.dp))
        SliderRow(
            label = "Scale",
            valueLabel = "${(view.scale * 100f).toInt()}%",
            value = view.scale.coerceIn(OverlayClip.MIN_SCALE, OverlayClip.MAX_SCALE),
            valueRange = OverlayClip.MIN_SCALE..OverlayClip.MAX_SCALE,
            onValueChange = { onLayout(view.x, view.y, it, view.rotationDeg) }
        )
        SliderRow(
            label = "Rotation",
            valueLabel = "${view.rotationDeg.toInt()}°",
            // Map -180..180 onto 0..1 for the control only; the model
            // keeps degrees because that is what the preview gesture
            // reports and what the export shader converts to radians.
            value = ((view.rotationDeg + 180f) / 360f).coerceIn(0f, 1f),
            onValueChange = {
                val degrees = it.coerceIn(0f, 1f) * 360f - 180f
                onLayout(view.x, view.y, view.scale, degrees)
            }
        )

        Spacer(Modifier.height(14.dp))
        SectionLabel("TRIM (SOURCE WINDOW)")
        SliderRow(
            label = "Cut start",
            valueLabel = TimeFormat.formatMs(view.startMs),
            value = view.startMs.toFloat().coerceIn(0f, source.toFloat()),
            valueRange = 0f..source.toFloat(),
            onValueChange = { ms ->
                val start = ms.toLong().coerceIn(0L, (view.endMs - 1L).coerceAtLeast(0L))
                onTrim(start, view.endMs)
            }
        )
        SliderRow(
            label = "Cut end",
            valueLabel = TimeFormat.formatMs(view.endMs),
            value = view.endMs.toFloat().coerceIn(0f, source.toFloat()),
            valueRange = 0f..source.toFloat(),
            onValueChange = { ms ->
                val end = ms.toLong()
                    .coerceIn((view.startMs + 1L).coerceAtLeast(1L), source.coerceAtLeast(1L))
                onTrim(view.startMs, end)
            }
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "${TimeFormat.formatMs(view.durationMs)} of ${TimeFormat.formatMs(clip.durationMs)} kept",
            color = ApexPalette.TextSecondary,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/** Small caps-ish section heading shared with the panel chrome. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

/**
 * Label + live value + track slider. Deliberately one round-trip per
 * tick (value comes from the model, so it never feels "springy").
 */
@Composable
private fun SliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                valueLabel,
                color = ApexPalette.NeonCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.semantics { contentDescription = label },
            colors = SliderDefaults.colors(
                thumbColor = ApexPalette.NeonCyan,
                activeTrackColor = ApexPalette.NeonCyan
            )
        )
    }
}
