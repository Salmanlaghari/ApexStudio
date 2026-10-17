package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.domain.model.MaskShape
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.OverlayBlend
import com.apexstudio.app.domain.model.VideoMask
import com.apexstudio.app.ui.theme.ApexPalette
import java.util.Locale

/**
 * Overlay (PiP) properties panel — the overlay clip's main editing
 * surface, opened from the clip bar's "Properties" action while an
 * overlay is selected.
 *
 * - Opacity, blend mode (Normal / Screen / Multiply / Overlay),
 *   mask shape (None / Rect / Rounded / Circle), border (width +
 *   colour), shadow, flip H/V, volume (video overlays only).
 * - Track picker (OVL1..OVL9) moves the overlay between overlay tracks.
 * - Keyframes: add one at the playhead (captures the current
 *   position/scale/rotation/opacity), tap one to seek to it, delete one.
 *
 * Every control is live. Mask shape reuses the existing [VideoMask]
 * system (same numbers the export shader consumes): Rect = full-size
 * rectangle, Circle = full-size ellipse, Rounded = rectangle with a
 * soft feathered edge (VideoMask has no corner-radius field — the
 * feather is the closest approximation and is documented as such).
 * None clears the mask.
 */
@Composable
fun OverlayPropertiesPanel(
    clip: MediaClip,
    isVideo: Boolean,
    playheadMs: Long,
    onOpacity: (Float) -> Unit,
    // Bug 3: overlay layout (position/scale/rotation) and source trim,
    // both rendered by OverlayEditor - see its KDoc for why three
    // floats go back together and why trim binds to the source window.
    onLayout: (x: Float, y: Float, scale: Float, rotationDeg: Float) -> Unit,
    onTrim: (startMs: Long, endMs: Long) -> Unit,
    onBlend: (OverlayBlend) -> Unit,
    onMaskShape: (OverlayMaskShape) -> Unit,
    onBorder: (widthFraction: Float, colorArgb: Int) -> Unit,
    onShadow: (Float) -> Unit,
    onFlip: (flipH: Boolean, flipV: Boolean) -> Unit,
    onVolume: (Float) -> Unit,
    onTrack: (layer: Int) -> Unit,
    onAddKeyframe: () -> Unit,
    onSeekKeyframe: (Long) -> Unit,
    onRemoveKeyframe: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mask = clip.mask
    val activeShape = when {
        mask == null || !mask.enabled -> OverlayMaskShape.NONE
        mask.shape == MaskShape.ELLIPSE -> OverlayMaskShape.CIRCLE
        // The detection threshold must stay BELOW the feather ROUNDED
        // writes (ROUNDED_MASK_FEATHER), or a Rounded mask would not
        // round-trip back to the Rounded chip.
        mask.shape == MaskShape.RECTANGLE && mask.feather > ROUNDED_DETECT_THRESHOLD ->
            OverlayMaskShape.ROUNDED
        mask.shape == MaskShape.RECTANGLE -> OverlayMaskShape.RECT
        else -> OverlayMaskShape.NONE
    }
    val borderColors = listOf(
        0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFE53935.toInt(),
        0xFF00E5FF.toInt(), 0xFFFFD600.toInt(), 0xFFEC4899.toInt(),
        0xFF00E676.toInt(), 0xFF7C4DFF.toInt()
    )

    // Half-height sheet (same 0.55 cap as the FX sheets): the panel must
    // NEVER cover the full screen — preview + timeline stay visible.
    // The controls live in HORIZONTAL cards (swipe left/right between
    // control groups) instead of one tall vertical column.
    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * 0.55f).dp
    val cardsHeight = (maxSheetHeight - 110.dp).coerceAtLeast(160.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(Color(0xFF141420))
            .border(1.dp, Color(0xFF2A2A3E), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(16.dp)
            .heightIn(max = maxSheetHeight)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PictureInPicture,
                    contentDescription = null,
                    tint = ApexPalette.NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Overlay Properties",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }
        }
        Text(
            text = clip.name,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp)
        )

        // Bug 3: layout + trim. Above the scrolling card row on purpose -
        // the cards are a horizontally-swiped strip, and burying POSITION
        // or CUT inside one of them would make placement a second-class
        // control. Full width = this is the overlay's primary edit surface.
        OverlayEditor(
            clip = clip,
            onLayout = onLayout,
            onTrim = onTrim,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        // Horizontal control cards: swipe left/right between groups.
        // Each card scrolls vertically on its own if its content
        // overflows (e.g. a long keyframe list).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(cardsHeight)
                .horizontalScroll(rememberScrollState())
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card 1: Opacity + Blend.
            PropsCard {
                // Opacity — shows the EFFECTIVE opacity at the
                // playhead when the overlay has keyframes (the slider
                // auto-keyframes there), else the base pipOpacity.
                val effectiveOpacity = if (clip.keyframes.keyframes.isNotEmpty()) {
                    clip.keyframes.interpolateAt(playheadMs).opacity
                } else {
                    clip.pipOpacity
                }
                PanelSliderRow(
                    label = "Opacity",
                    valueLabel = "${(effectiveOpacity * 100).toInt()}%",
                    value = effectiveOpacity,
                    onValueChange = onOpacity
                )

                // Blend mode
                PanelSectionLabel("Blend mode", topPadding = 8.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OverlayBlend.entries.forEach { blend ->
                        PanelChip(
                            label = blend.label,
                            selected = clip.overlayBlend == blend,
                            onClick = { onBlend(blend) }
                        )
                    }
                }
            }

            // Card 2: Mask + Border + Shadow.
            PropsCard {
        // Mask shape
        PanelSectionLabel("Mask shape")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OverlayMaskShape.entries.forEach { shape ->
                PanelChip(
                    label = shape.label,
                    selected = activeShape == shape,
                    onClick = { onMaskShape(shape) }
                )
            }
        }
        if (activeShape == OverlayMaskShape.ROUNDED) {
            Text(
                text = "Rounded = rectangle with a soft edge (no corner-radius field yet).",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Border: the slider is 0..1; the stored width is a fraction
        // of the box width, 0..OVERLAY_MAX_BORDER_WIDTH.
        PanelSliderRow(
            label = "Border",
            valueLabel = "${(clip.overlayBorderWidth * 100).toInt()}%",
            value = clip.overlayBorderWidth / OVERLAY_MAX_BORDER_WIDTH,
            onValueChange = { onBorder(it * OVERLAY_MAX_BORDER_WIDTH, clip.overlayBorderColor) }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            borderColors.forEach { argb ->
                val selected = clip.overlayBorderColor == argb
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) ApexPalette.NeonCyan
                            else Color.White.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                        .clickable {
                            onBorder(clip.overlayBorderWidth, argb)
                        }
                )
            }
        }

        // Shadow
        PanelSliderRow(
            label = "Shadow",
            valueLabel = "${(clip.overlayShadow * 100).toInt()}%",
            value = clip.overlayShadow,
            onValueChange = onShadow
        )
            } // Card 2

            // Card 3: Flip + Volume + Track.
            PropsCard {
        // Flip
        PanelSectionLabel("Flip")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PanelChip(
                label = "Flip H",
                selected = clip.overlayFlipH,
                onClick = { onFlip(!clip.overlayFlipH, clip.overlayFlipV) }
            )
            PanelChip(
                label = "Flip V",
                selected = clip.overlayFlipV,
                onClick = { onFlip(clip.overlayFlipH, !clip.overlayFlipV) }
            )
        }

        // Volume (video overlays only — images have no audio).
        if (isVideo) {
            PanelSliderRow(
                label = "Volume",
                valueLabel = "${(clip.overlayVolume * 100).toInt()}%",
                value = clip.overlayVolume / 2f,
                onValueChange = { onVolume(it * 2f) }
            )
        }

        // Track picker (OVL1..OVL9).
        PanelSectionLabel("Track")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            (1..9).forEach { layer ->
                PanelChip(
                    label = "OVL$layer",
                    selected = clip.trackIndex == layer,
                    onClick = { onTrack(layer) }
                )
            }
        }
            } // Card 3

            // Card 4: Keyframes (position / scale / rotation / opacity).
            PropsCard {
        // Keyframes: position / scale / rotation / opacity.
        PanelSectionLabel("Keyframes")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ApexPalette.NeonCyan)
                    .clickable { onAddKeyframe() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Add at ${formatOverlayKfTime(playheadMs)}",
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "${clip.keyframes.keyframes.size} keyframe(s)",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }
        if (clip.keyframes.keyframes.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                clip.keyframes.keyframes.forEach { kf ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .clickable { onSeekKeyframe(kf.timeMs) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "◆ ${formatOverlayKfTime(kf.timeMs)}",
                            color = ApexPalette.NeonCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "x ${String.format(Locale.US, "%.2f", kf.translateX)} · " +
                                    "y ${String.format(Locale.US, "%.2f", kf.translateY)} · " +
                                    "${(kf.scale * 100).toInt()}% · ${kf.rotationDeg.toInt()}°",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                        IconButton(
                            onClick = { onRemoveKeyframe(kf.id) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Delete keyframe",
                                tint = Color(0xFFE53935),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
            } // Card 4
        } // Horizontal cards row
    }
}

/** Maximum overlay border width, as a fraction of the box width. */
private const val OVERLAY_MAX_BORDER_WIDTH = 0.05f

/** Feather a Rounded mask writes (see OverlayMaskShape.ROUNDED). */
private const val ROUNDED_MASK_FEATHER = 0.08f

/**
 * Feather threshold for detecting a Rounded mask. Must stay below
 * [ROUNDED_MASK_FEATHER] so a Rounded mask round-trips, and above 0
 * so a sharp Rect (feather 0) is not misread as Rounded.
 */
private const val ROUNDED_DETECT_THRESHOLD = 0.02f

/** Mask shapes offered by the overlay properties panel. */
enum class OverlayMaskShape(val label: String) {
    NONE("None"),
    RECT("Rect"),
    ROUNDED("Rounded"),
    CIRCLE("Circle");

    /** The [VideoMask] this shape maps to (null = clear the mask). */
    fun toVideoMask(): VideoMask? = when (this) {
        NONE -> null
        RECT -> VideoMask(
            shape = MaskShape.RECTANGLE, centerX = 0.5f, centerY = 0.5f,
            sizeX = 1f, sizeY = 1f, feather = 0f, invert = false,
            opacity = 1f, enabled = true
        )
        ROUNDED -> VideoMask(
            shape = MaskShape.RECTANGLE, centerX = 0.5f, centerY = 0.5f,
            sizeX = 1f, sizeY = 1f, feather = ROUNDED_MASK_FEATHER, invert = false,
            opacity = 1f, enabled = true
        )
        CIRCLE -> VideoMask(
            shape = MaskShape.ELLIPSE, centerX = 0.5f, centerY = 0.5f,
            sizeX = 1f, sizeY = 1f, feather = 0f, invert = false,
            opacity = 1f, enabled = true
        )
    }
}

@Composable
private fun PanelSectionLabel(text: String, topPadding: Dp = 16.dp) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = topPadding, bottom = 8.dp)
    )
}

/**
 * One horizontal card of the properties panel: a fixed-width,
 * full-cards-height column that scrolls vertically on its own when
 * its content overflows (e.g. a long keyframe list).
 */
@Composable
private fun PropsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .width(264.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        content = content
    )
}

@Composable
private fun PanelSliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(text = valueLabel, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
    }
    Slider(
        value = value.coerceIn(0f, 1f),
        onValueChange = onValueChange,
        colors = SliderDefaults.colors(
            thumbColor = ApexPalette.NeonCyan,
            activeTrackColor = ApexPalette.NeonCyan,
            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PanelChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) ApexPalette.NeonCyan
                else Color.White.copy(alpha = 0.08f)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else Color.White,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

private fun formatOverlayKfTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0L)
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.US, "%d:%02d", min, sec)
}
