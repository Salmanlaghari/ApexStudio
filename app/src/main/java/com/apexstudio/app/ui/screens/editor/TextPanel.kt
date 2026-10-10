package com.apexstudio.app.ui.screens.editor

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt
import com.apexstudio.app.data.text.TextFontRegistry
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.text.TextAnimEngine
import com.apexstudio.app.data.text.TextPreset
import com.apexstudio.app.data.text.TextPresetEngine
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.ui.theme.ApexPalette
import com.apexstudio.app.presentation.viewmodel.CaptionPhase
import com.apexstudio.app.presentation.viewmodel.CaptionUiState

enum class TextPanelTab {
    EDIT,
    ANIMATION,
    PRESETS,
    CAPTIONS
}

data class TextAnimationOption(
    val id: String,
    val label: String,
    val desc: String
)

val TEXT_ANIMATIONS = listOf(
    TextAnimationOption("NONE", "None", "Static subtitle"),
    TextAnimationOption("FADE_IN", "Fade In", "Smooth opacity dissolve"),
    TextAnimationOption("SLIDE_UP", "Slide Up", "Rises from below with fade"),
    TextAnimationOption("TYPEWRITER", "Typewriter", "Letter by letter reveal"),
    TextAnimationOption("POP_SPRING", "Pop Spring", "Springy overshoot zoom-in"),
    TextAnimationOption("BLUR_IN", "Blur In", "Sharpens out of a blur"),
    TextAnimationOption("SLIDE_BOUNCE", "Slide Bounce", "Bouncy rise with overshoot"),
    TextAnimationOption("PULSE", "Pulse Loop", "Rhythmic breathing scale"),
    TextAnimationOption("BOUNCE", "Bounce Loop", "Playful vertical hop"),
    TextAnimationOption("3D_FLIP_X", "3D Flip X", "3D perspective tumble on horizontal axis"),
    TextAnimationOption("3D_ROTATE_Y", "3D Spin Y", "3D door swing on vertical axis"),
    TextAnimationOption("3D_DEPTH_WARP", "3D Depth Warp", "Extrudes from deep 3D horizon"),
    TextAnimationOption("3D_SWING", "3D Swing", "Dynamic pendulum in 3D perspective"),
    TextAnimationOption("3D_TUMBLE", "3D Cube Tumble", "Dual-axis tumbling 3D effect"),
    TextAnimationOption("3D_ISOMETRIC", "3D Isometric", "Angled 3D isometric depth")
)

/** Exit animations, timed at the END of the text layer's window. */
val TEXT_OUTRO_ANIMATIONS = listOf(
    TextAnimationOption("NONE", "None", "Cuts out instantly"),
    TextAnimationOption("FADE_OUT", "Fade Out", "Smooth opacity dissolve"),
    TextAnimationOption("SLIDE_DOWN", "Slide Down", "Sinks down while fading"),
    TextAnimationOption("SHRINK", "Shrink", "Scales down to a point")
)

/** Gradient fill presets (start → end ARGB), shown in the Text panel. */
val TEXT_GRADIENTS: List<Triple<String, Long, Long>> = listOf(
    Triple("Gold", 0xFFFFD700L, 0xFFFF8C00L),
    Triple("Sunset", 0xFFFF512FL, 0xFFDD2476L),
    Triple("Ocean", 0xFF00E5FFL, 0xFF2979FFL),
    Triple("Mint", 0xFF1DE9B6L, 0xFF00B0FFL),
    Triple("Violet", 0xFFAA00FFL, 0xFFFF00E5L),
    Triple("Ember", 0xFFFFEA00L, 0xFFFF3D00L)
)

@Composable
fun TextPanel(
    overlays: List<TextOverlay>,
    selectedId: String?,
    onAdd: () -> Unit,
    onSelect: (String) -> Unit,
    onTextChange: (String) -> Unit,
    onColorChange: (Long) -> Unit,
    onBgChange: (Long?) -> Unit,
    onSizeChange: (Float) -> Unit,
    onFontFamilyChange: (String) -> Unit = {},
    onStyleChange: (isBold: Boolean, isItalic: Boolean) -> Unit = { _, _ -> },
    onShadowChange: (Long?) -> Unit = {},
    onAlignChange: (String) -> Unit = {},
    onLetterSpacingChange: (Float) -> Unit = {},
    onGradientChange: (Pair<Long, Long>?) -> Unit = {},
    onAnimDurationChange: (Long) -> Unit = {},
    onOutroChange: (String) -> Unit = {},
    onOutroDurationChange: (Long) -> Unit = {},
    onDuplicate: (String) -> Unit = {},
    onDelete: (String) -> Unit,
    onApplyPreset: (TextPreset) -> Unit = {},
    onAnimationChange: (String) -> Unit = {},
    onClose: () -> Unit,
    captionUiState: CaptionUiState = CaptionUiState(),
    onAutoCaptions: () -> Unit = {},
    onClearCaptions: () -> Unit = {},
    hasAutoCaptions: Boolean = false
) {
    val selected = overlays.firstOrNull { it.id == selectedId }
    var activeTab by remember { mutableStateOf(TextPanelTab.EDIT) }
    var showCustomColor by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(TextPresetEngine.categories.first()) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .heightIn(max = 420.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(ApexPalette.BgSurface)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .verticalScroll(scrollState)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Text & Titles",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Default.Close,
                contentDescription = "Close text",
                tint = ApexPalette.NeonCyan,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable { onClose() }
                    .padding(3.dp)
            )
        }

        Spacer(Modifier.height(6.dp))

        // Caption chips: one pill per caption + add button
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ApexPalette.NeonCyan.copy(alpha = 0.15f))
                        .border(1.dp, ApexPalette.NeonCyan, RoundedCornerShape(8.dp))
                        .clickable { onAdd() }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Add, null,
                            tint = ApexPalette.NeonCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Add",
                            color = ApexPalette.NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            items(overlays) { o ->
                val sel = o.id == selectedId
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (sel) ApexPalette.NeonCyan.copy(alpha = 0.2f)
                            else ApexPalette.BgElevated
                        )
                        .border(
                            1.dp,
                            if (sel) ApexPalette.NeonCyan else ApexPalette.BorderGlass,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelect(o.id) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        o.text.ifBlank { "Text" },
                        color = if (sel) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (selected == null) {
            Text(
                "Tap + Add to place a caption on the video. Drag or tap on video to edit.",
                color = ApexPalette.TextTertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            return@Column
        }

        // Section Tabs: Edit & Font, Style & Color, Animation, Presets
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(ApexPalette.BgElevated)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                TextPanelTab.EDIT to "Edit & Font",
                TextPanelTab.ANIMATION to "Animation",
                TextPanelTab.PRESETS to "Presets",
                TextPanelTab.CAPTIONS to "Captions"
            ).forEach { (tab, label) ->
                val isSel = activeTab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSel) ApexPalette.NeonCyan else Color.Transparent)
                        .clickable { activeTab = tab }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (isSel) Color(0xFF0A0E1A) else ApexPalette.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        when (activeTab) {
            TextPanelTab.EDIT -> {
                OutlinedTextField(
                    value = selected.text,
                    onValueChange = onTextChange,
                    singleLine = false,
                    minLines = 1,
                    maxLines = 2,
                    placeholder = { Text("Enter text...", color = ApexPalette.TextTertiary) },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = 14.sp
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ApexPalette.NeonCyan,
                        unfocusedBorderColor = ApexPalette.BorderGlass,
                        focusedContainerColor = ApexPalette.BgElevated,
                        unfocusedContainerColor = ApexPalette.BgElevated,
                        cursorColor = ApexPalette.NeonCyan
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))


                // Numerical Font Size Control with - and + buttons and slider (CapCut style)
                val currentSizeSp = (20f * selected.sizeScale).toInt()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Size",
                        color = ApexPalette.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(44.dp)
                    )

                    // Decrement button
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(ApexPalette.BgElevated)
                            .border(1.dp, ApexPalette.BorderGlass, CircleShape)
                            .clickable {
                                onSizeChange((selected.sizeScale - 0.1f).coerceIn(0.4f, 4f))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("-", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    // Slider
                    Slider(
                        value = selected.sizeScale.coerceIn(0.4f, 4f),
                        onValueChange = onSizeChange,
                        valueRange = 0.4f..4f,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = ApexPalette.NeonCyan,
                            activeTrackColor = ApexPalette.NeonCyan,
                            inactiveTrackColor = ApexPalette.BgElevated
                        )
                    )

                    // Increment button
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(ApexPalette.BgElevated)
                            .border(1.dp, ApexPalette.BorderGlass, CircleShape)
                            .clickable {
                                onSizeChange((selected.sizeScale + 0.1f).coerceIn(0.4f, 4f))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.width(8.dp))

                    // Exact SP badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ApexPalette.NeonCyan.copy(alpha = 0.18f))
                            .border(1.dp, ApexPalette.NeonCyan, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "${currentSizeSp}sp",
                            color = ApexPalette.NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Bundled OFL font picker — chips render in the actual
                // typeface so WYSIWYG matches the exported video.
                // Phase 4: ensure the 36 Google Fonts are registered before
                // the grouped picker reads them (runs before first read).
                val fontCtx = LocalContext.current
                val fontGroups = remember {
                    TextFontRegistry.init(fontCtx)
                    TEXT_FONT_GROUPS
                }
                Text(
                    "Font (${fontGroups.sumOf { it.second.size }} styles)",
                    color = ApexPalette.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                // Phase 4: sectioned font grid (45 fonts across categories)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    fontGroups.forEach { (category, options) ->
                        Text(
                            category.uppercase(),
                            color = ApexPalette.TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            options.chunked(3).forEach { row ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    row.forEach { (fontKey, fontLabel) ->
                                        val isSel = selected.fontFamily.equals(fontKey, ignoreCase = true)
                                        val chipFont = rememberTextFontFamily(fontKey, selected.isBold, selected.isItalic)
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (isSel) ApexPalette.NeonCyan.copy(alpha = 0.2f)
                                                    else ApexPalette.BgElevated
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isSel) ApexPalette.NeonCyan else ApexPalette.BorderGlass,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable { onFontFamilyChange(fontKey) }
                                                .padding(horizontal = 6.dp, vertical = 7.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                fontLabel,
                                                color = if (isSel) ApexPalette.NeonCyan else Color.White,
                                                fontSize = 13.sp,
                                                fontFamily = chipFont,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                    // Pad short rows so chips keep their width
                                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Bold / Italic toggles
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Style",
                        color = ApexPalette.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(44.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Bold toggle
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected.isBold) ApexPalette.NeonCyan.copy(alpha = 0.25f) else ApexPalette.BgElevated)
                                .border(1.dp, if (selected.isBold) ApexPalette.NeonCyan else ApexPalette.BorderGlass, RoundedCornerShape(8.dp))
                                .clickable { onStyleChange(!selected.isBold, selected.isItalic) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("B", color = if (selected.isBold) ApexPalette.NeonCyan else Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        // Italic toggle
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected.isItalic) ApexPalette.NeonCyan.copy(alpha = 0.25f) else ApexPalette.BgElevated)
                                .border(1.dp, if (selected.isItalic) ApexPalette.NeonCyan else ApexPalette.BorderGlass, RoundedCornerShape(8.dp))
                                .clickable { onStyleChange(selected.isBold, !selected.isItalic) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("I", color = if (selected.isItalic) ApexPalette.NeonCyan else Color.White, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Text colour swatches (CapCut popular colors)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Color",
                        color = ApexPalette.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(44.dp)
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(textColors()) { c ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(c.toInt()))
                                    .border(
                                        2.dp,
                                        if (selected.colorArgb == c) ApexPalette.NeonCyan
                                        else ApexPalette.BorderGlass,
                                        CircleShape
                                    )
                                    .clickable { onColorChange(c); showCustomColor = false },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected.colorArgb == c) {
                                    Icon(
                                        Icons.Default.Check, null,
                                        tint = if (c == 0xFFFFFFFFL || c == 0xFFFFC400L || c == 0xFF00E5FFL) Color.Black else Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                        // Phase 4: custom HSV picker entry.
                        item {
                            val isCustom = textColors().none { it == selected.colorArgb }
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.sweepGradient(
                                            listOf(
                                                Color.Red, Color.Yellow, Color.Green,
                                                Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                                            )
                                        )
                                    )
                                    .border(
                                        2.dp,
                                        if (showCustomColor || isCustom) ApexPalette.NeonCyan
                                        else ApexPalette.BorderGlass,
                                        CircleShape
                                    )
                                    .clickable { showCustomColor = !showCustomColor },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Add, null,
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }

                // Phase 4: expandable HSV color picker.
                if (showCustomColor && selected != null) {
                    Spacer(Modifier.height(8.dp))
                    com.apexstudio.app.ui.components.HsvColorPicker(
                        initialColor = Color(selected.colorArgb.toInt()),
                        onColorChange = { c ->
                            // ARGB Long without toArgb() (kept explicit).
                            val a = 0xFF
                            val r = (c.red * 255).roundToInt().coerceIn(0, 255)
                            val g = (c.green * 255).roundToInt().coerceIn(0, 255)
                            val b = (c.blue * 255).roundToInt().coerceIn(0, 255)
                            onColorChange(
                                ((a shl 24) or (r shl 16) or (g shl 8) or b).toLong()
                                    and 0xFFFFFFFFL
                            )
                        }
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Background Pill Options
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Pill",
                        color = ApexPalette.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(44.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pillOptions().forEach { (label, argb) ->
                            val sel = selected.bgArgb == argb
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (sel) ApexPalette.NeonCyan.copy(alpha = 0.2f)
                                        else ApexPalette.BgElevated
                                    )
                                    .border(
                                        1.dp,
                                        if (sel) ApexPalette.NeonCyan else ApexPalette.BorderGlass,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onBgChange(argb) }
                                    .padding(horizontal = 9.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    label,
                                    color = if (sel) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Text Shadow / Glow toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Glow",
                        color = ApexPalette.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(44.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "None" to null,
                            "Black Shadow" to 0xCC000000L,
                            "Cyan Glow" to 0xFF00E5FFL,
                            "Yellow Glow" to 0xFFFFC400L,
                            "Neon Pink" to 0xFFFF2D55L
                        ).forEach { (label, argb) ->
                            val sel = selected.shadowColorArgb == argb
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (sel) ApexPalette.NeonCyan.copy(alpha = 0.2f) else ApexPalette.BgElevated)
                                    .border(1.dp, if (sel) ApexPalette.NeonCyan else ApexPalette.BorderGlass, RoundedCornerShape(8.dp))
                                    .clickable { onShadowChange(argb) }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(label, color = if (sel) ApexPalette.NeonCyan else Color.White, fontSize = 10.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Text alignment relative to the anchor point
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Align",
                        color = ApexPalette.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(44.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("LEFT" to "Left", "CENTER" to "Center", "RIGHT" to "Right").forEach { (key, label) ->
                            val sel = selected.textAlign.equals(key, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (sel) ApexPalette.NeonCyan.copy(alpha = 0.2f) else ApexPalette.BgElevated)
                                    .border(1.dp, if (sel) ApexPalette.NeonCyan else ApexPalette.BorderGlass, RoundedCornerShape(8.dp))
                                    .clickable { onAlignChange(key) }
                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    label,
                                    color = if (sel) ApexPalette.NeonCyan else Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Letter spacing slider
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Spacing",
                        color = ApexPalette.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(44.dp)
                    )
                    Slider(
                        value = selected.letterSpacingEm.coerceIn(-0.1f, 0.5f),
                        onValueChange = onLetterSpacingChange,
                        valueRange = -0.1f..0.5f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = ApexPalette.NeonCyan,
                            activeTrackColor = ApexPalette.NeonCyan,
                            inactiveTrackColor = ApexPalette.BgElevated
                        )
                    )
                    Text(
                        String.format("%.2f", selected.letterSpacingEm),
                        color = ApexPalette.NeonCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 6.dp).width(34.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Gradient fill presets
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Gradient",
                        color = ApexPalette.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(44.dp)
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            val sel = selected.gradientStartArgb == null
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (sel) ApexPalette.NeonCyan.copy(alpha = 0.2f) else ApexPalette.BgElevated)
                                    .border(1.dp, if (sel) ApexPalette.NeonCyan else ApexPalette.BorderGlass, RoundedCornerShape(8.dp))
                                    .clickable { onGradientChange(null) }
                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                Text("None", color = if (sel) ApexPalette.NeonCyan else Color.White, fontSize = 10.sp)
                            }
                        }
                        items(TEXT_GRADIENTS) { (label, start, end) ->
                            val sel = selected.gradientStartArgb == start && selected.gradientEndArgb == end
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                                            listOf(Color(start.toInt()), Color(end.toInt()))
                                        )
                                    )
                                    .border(1.dp, if (sel) ApexPalette.NeonCyan else ApexPalette.BorderGlass, RoundedCornerShape(8.dp))
                                    .clickable { onGradientChange(start to end) }
                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    label,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                    style = androidx.compose.ui.text.TextStyle(
                                        shadow = Shadow(color = Color.Black, offset = Offset(1f, 1f), blurRadius = 3f)
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Quick Duplicate and Delete Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ApexPalette.BgElevated)
                            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(8.dp))
                            .clickable { onDuplicate(selected.id) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ContentCopy, null, tint = ApexPalette.NeonCyan, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Duplicate", color = ApexPalette.NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ApexPalette.Danger.copy(alpha = 0.15f))
                            .border(1.dp, ApexPalette.Danger, RoundedCornerShape(8.dp))
                            .clickable { onDelete(selected.id) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Delete, null, tint = ApexPalette.Danger, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delete", color = ApexPalette.Danger, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            TextPanelTab.ANIMATION -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // --- Live intro → hold → outro demo, driven by the
                    // same TextAnimEngine that powers the preview layer
                    // and the export bake. ---
                    val demoDensity = LocalDensity.current
                    val demoOverlay = remember(selected) {
                        selected.copy(
                            startMs = 0L,
                            endMs = DEMO_CYCLE_MS,
                            animationDurationMs = selected.animationDurationMs.coerceIn(200L, 1200L),
                            outroAnimationDurationMs = selected.outroAnimationDurationMs.coerceIn(150L, 1200L)
                        )
                    }
                    val demoTransition = rememberInfiniteTransition(label = "text_anim_demo")
                    val demoTimeMs by demoTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = DEMO_CYCLE_MS.toFloat(),
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                durationMillis = DEMO_CYCLE_MS.toInt(),
                                easing = LinearEasing
                            ),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "demo_time"
                    )
                    val demoState = TextAnimEngine.compute(demoOverlay, demoTimeMs.toLong())
                    val demoText = demoState.charsToShow
                        ?.let { demoOverlay.text.take(it.coerceIn(0, demoOverlay.text.length)) }
                        ?: demoOverlay.text
                    val demoFont = rememberTextFontFamily(selected.fontFamily, selected.isBold, selected.isItalic)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(92.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0D0D18))
                            .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "LIVE PREVIEW",
                                    color = ApexPalette.NeonCyan,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "${selected.animationType} → ${selected.outroAnimationType}",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Box(
                                modifier = Modifier
                                    .graphicsLayer {
                                        alpha = demoState.alpha
                                        rotationX = demoState.rotXDeg
                                        rotationY = demoState.rotYDeg
                                        rotationZ = demoState.rotZDeg
                                        scaleX = demoState.scaleX
                                        scaleY = demoState.scaleY
                                        translationY = demoState.transYFrac * with(demoDensity) { 92.dp.toPx() }
                                        cameraDistance = 16f * demoDensity.density
                                        shadowElevation = 8f
                                    }
                                    .then(
                                        if (demoState.blurFrac > 0f && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                            Modifier.blur(with(demoDensity) { (demoState.blurFrac * 92.dp.toPx()).toDp() })
                                        } else Modifier
                                    )
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (selected.bgArgb != null) Color(selected.bgArgb.toInt())
                                        else Color(0xFF1E1E2C)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = demoText.ifBlank { "TITLE DEMO" },
                                    color = Color(selected.colorArgb.toInt()),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = demoFont
                                )
                            }
                            Spacer(Modifier.weight(1f))
                        }
                    }

                    // Intro duration slider
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Intro speed",
                            color = ApexPalette.TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(64.dp)
                        )
                        Slider(
                            value = (selected.animationDurationMs / 1000f).coerceIn(0.2f, 2.5f),
                            onValueChange = { onAnimDurationChange((it * 1000L).toLong()) },
                            valueRange = 0.2f..2.5f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = ApexPalette.NeonCyan,
                                activeTrackColor = ApexPalette.NeonCyan,
                                inactiveTrackColor = ApexPalette.BgElevated
                            )
                        )
                        Text(
                            String.format("%.1fs", selected.animationDurationMs / 1000f),
                            color = ApexPalette.NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }

                    Text(
                        "Intro (entrance)",
                        color = ApexPalette.NeonCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    TEXT_ANIMATIONS.forEach { opt ->
                        val isSel = TextAnimEngine.normalizeIntro(selected.animationType) == opt.id
                        AnimationOptionRow(
                            opt = opt,
                            isSelected = isSel,
                            onClick = { onAnimationChange(opt.id) }
                        )
                    }

                    // Outro duration slider
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Outro speed",
                            color = ApexPalette.TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(64.dp)
                        )
                        Slider(
                            value = (selected.outroAnimationDurationMs / 1000f).coerceIn(0.15f, 2.5f),
                            onValueChange = { onOutroDurationChange((it * 1000L).toLong()) },
                            valueRange = 0.15f..2.5f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = ApexPalette.NeonCyan,
                                activeTrackColor = ApexPalette.NeonCyan,
                                inactiveTrackColor = ApexPalette.BgElevated
                            )
                        )
                        Text(
                            String.format("%.1fs", selected.outroAnimationDurationMs / 1000f),
                            color = ApexPalette.NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }

                    Text(
                        "Outro (exit)",
                        color = ApexPalette.NeonCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    TEXT_OUTRO_ANIMATIONS.forEach { opt ->
                        val isSel = selected.outroAnimationType.equals(opt.id, ignoreCase = true)
                        AnimationOptionRow(
                            opt = opt,
                            isSelected = isSel,
                            onClick = { onOutroChange(opt.id) }
                        )
                    }
                }
            }

            TextPanelTab.PRESETS -> {
                // Preset Categories
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(TextPresetEngine.categories) { cat ->
                        val isSel = cat == selectedCategory
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) ApexPalette.NeonCyan else ApexPalette.BgElevated)
                                .clickable { selectedCategory = cat }
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Text(
                                cat,
                                color = if (isSel) Color(0xFF0A0E1A) else ApexPalette.TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Presets in category
                val categoryPresets = TextPresetEngine.getPresetsForCategory(selectedCategory)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categoryPresets) { preset ->
                        val isCurrent = selected.presetId == preset.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (preset.bgArgb != null) Color(preset.bgArgb.toInt())
                                    else ApexPalette.BgElevated
                                )
                                .border(
                                    1.5.dp,
                                    if (isCurrent) ApexPalette.NeonCyan else ApexPalette.BorderGlass,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onApplyPreset(preset) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                preset.name,
                                color = Color(preset.colorArgb.toInt()),
                                fontSize = 11.sp,
                                fontWeight = if (preset.isBold) FontWeight.Bold else FontWeight.Normal,
                                fontStyle = if (preset.isItalic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
                            )
                        }
                    }
                }
            }
                    TextPanelTab.CAPTIONS -> {
                AutoCaptionsTab(
                    captionUiState = captionUiState,
                    onAutoCaptions = onAutoCaptions,
                    onClearCaptions = onClearCaptions,
                    hasAutoCaptions = hasAutoCaptions
                )
            }
}
    }
}

private const val DEMO_CYCLE_MS = 2600L

@Composable
private fun AnimationOptionRow(
    opt: TextAnimationOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) ApexPalette.NeonCyan.copy(alpha = 0.18f)
                else ApexPalette.BgElevated
            )
            .border(
                1.dp,
                if (isSelected) ApexPalette.NeonCyan else ApexPalette.BorderGlass,
                RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    opt.label,
                    color = if (isSelected) ApexPalette.NeonCyan else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    opt.desc,
                    color = ApexPalette.TextTertiary,
                    fontSize = 10.sp
                )
            }
            if (isSelected) {
                Icon(
                    Icons.Default.Check, null,
                    tint = ApexPalette.NeonCyan,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun textColors(): List<Long> = listOf(
    0xFFFFFFFFL, 0xFFFFC400L, 0xFF00E5FFL, 0xFFFF2D55L,
    0xFF00C853L, 0xFF7C4DFFL, 0xFFFF6D00L, 0xFF00B0FFL,
    0xFFFF4081L, 0xFF18FFFFL, 0xFFEEEEEEL, 0xFF0A0E1AL
)

private fun pillOptions(): List<Pair<String, Long?>> = listOf(
    "None" to null,
    "Black Glass" to 0xB3000000L,
    "Solid Black" to 0xFF000000L,
    "Yellow" to 0xCCFFC400L,
    "Cyan" to 0xB300E5FFL,
    "Red" to 0xCCE11D48L,
    "White" to 0xE6FFFFFFL
)

/**
 * Phase 4: Auto-captions tab (Vosk offline STT, Apache-2.0).
 * Every state is surfaced — no dead buttons.
 */
@Composable
private fun AutoCaptionsTab(
    captionUiState: CaptionUiState,
    onAutoCaptions: () -> Unit,
    onClearCaptions: () -> Unit,
    hasAutoCaptions: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Explainer card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ApexPalette.BgElevated)
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Auto Captions (Offline AI)",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Listens to this clip's audio on-device and creates timed lower-third captions. " +
                        "First use downloads a 40MB speech model once. English speech works best.",
                    color = ApexPalette.TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        val phase = captionUiState.phase
        val busy = phase == CaptionPhase.DOWNLOADING_MODEL || phase == CaptionPhase.TRANSCRIBING

        if (busy) {
            // Progress state — never a dead button.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ApexPalette.BgElevated)
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    captionUiState.message.ifBlank { "Working…" },
                    color = ApexPalette.NeonCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                LinearProgressIndicator(
                    progress = { captionUiState.progress01.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = ApexPalette.NeonCyan,
                    trackColor = Color(0xFF26334D)
                )
                Text(
                    "${(captionUiState.progress01 * 100).toInt()}%",
                    color = ApexPalette.TextSecondary,
                    fontSize = 11.sp
                )
            }
        } else {
            Button(
                onClick = onAutoCaptions,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ApexPalette.NeonCyan,
                    contentColor = Color.Black
                ),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Generate Auto Captions", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (phase == CaptionPhase.DONE && captionUiState.segmentsAdded > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F2E1F))
                    .border(1.dp, Color(0xFF34D399), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    "✓ ${captionUiState.segmentsAdded} captions added — they appear as timed text overlays.",
                    color = Color(0xFF6EE7B7),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (phase == CaptionPhase.FAILED) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2E1A1A))
                    .border(1.dp, Color(0xFFF87171), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    captionUiState.message.ifBlank { "Something went wrong." },
                    color = Color(0xFFFCA5A5),
                    fontSize = 12.sp
                )
            }
        }

        if (hasAutoCaptions && !busy) {
            OutlinedButton(
                onClick = onClearCaptions,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, Color(0xFFF87171).copy(alpha = 0.7f)
                ),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF87171)),
                modifier = Modifier.fillMaxWidth().height(42.dp)
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Clear Auto Captions", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
