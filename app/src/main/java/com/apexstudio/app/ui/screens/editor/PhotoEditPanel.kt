package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.filter.LutBitmapCache
import com.apexstudio.app.data.filter.LutFilterEngine
import com.apexstudio.app.data.photoedit.PhotoEditRenderer
import com.apexstudio.app.domain.model.PhotoEditSettings
import com.apexstudio.app.domain.model.VideoAdjustments
import com.apexstudio.app.presentation.state.PhotoEditTab
import com.apexstudio.app.ui.theme.ApexPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bottom-sheet photo editor for IMAGE clips (PR F): Crop / Adjust /
 * Filters / Rotate. Every control writes into the clip's
 * [PhotoEditSettings], which the live [PhotoClipPreview] renders
 * instantly and the export bakes into the MP4.
 */
@Composable
fun PhotoEditPanel(
    photoUri: String,
    settings: PhotoEditSettings,
    tab: PhotoEditTab,
    onTabChange: (PhotoEditTab) -> Unit,
    onCropAspectPreset: (String) -> Unit,
    onAdjust: ((VideoAdjustments) -> VideoAdjustments) -> Unit,
    onResetAdjust: () -> Unit,
    onFilterSelect: (String?) -> Unit,
    onFilterIntensity: (Float) -> Unit,
    onRotate90: () -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit,
    onResetAll: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(ApexPalette.BgSurface)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Edit Photo",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            if (!settings.isDefault) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onResetAll() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Refresh, null, tint = ApexPalette.NeonCyan, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reset", color = ApexPalette.NeonCyan, fontSize = 12.sp)
                }
            }
            Icon(
                Icons.Default.Close, "Close",
                tint = Color(0xFF9CA3AF),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onClose() }
                    .padding(4.dp)
                    .size(20.dp)
            )
        }

        // Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PhotoEditTab.values().forEach { t ->
                val selected = t == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) ApexPalette.NeonCyan.copy(alpha = 0.18f) else Color(0xFF1E1E2E))
                        .border(
                            1.dp,
                            if (selected) ApexPalette.NeonCyan else Color.Transparent,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onTabChange(t) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        t.label,
                        color = if (selected) ApexPalette.NeonCyan else Color(0xFF9CA3AF),
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        when (tab) {
            PhotoEditTab.CROP -> PhotoCropTab(
                settings = settings,
                onCropAspectPreset = onCropAspectPreset
            )
            PhotoEditTab.ADJUST -> PhotoAdjustTab(
                adjustments = settings.adjustments,
                onAdjust = onAdjust,
                onReset = onResetAdjust
            )
            PhotoEditTab.FILTERS -> PhotoFiltersTab(
                photoUri = photoUri,
                settings = settings,
                onFilterSelect = onFilterSelect,
                onFilterIntensity = onFilterIntensity
            )
            PhotoEditTab.ROTATE -> PhotoRotateTab(
                settings = settings,
                onRotate90 = onRotate90,
                onFlipH = onFlipH,
                onFlipV = onFlipV
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------------------
// Crop tab
// ---------------------------------------------------------------------------

@Composable
private fun PhotoCropTab(
    settings: PhotoEditSettings,
    onCropAspectPreset: (String) -> Unit
) {
    val active = settings.cropAspectPreset ?: "free"
    Column {
        Text(
            "Drag the corner handles on the preview to crop",
            color = Color(0xFF9CA3AF),
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PhotoEditSettings.ASPECT_PRESETS.forEach { (id, _) ->
                val selected = id == active
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) ApexPalette.NeonCyan.copy(alpha = 0.18f) else Color(0xFF1E1E2E))
                        .border(
                            1.dp,
                            if (selected) ApexPalette.NeonCyan else Color(0xFF2A2A3C),
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onCropAspectPreset(id) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (id == "free") "Free" else id,
                        color = if (selected) ApexPalette.NeonCyan else Color.White,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
        val crop = settings.crop
        if (crop != null && !crop.isFullFrame()) {
            Text(
                "Crop: ${(crop.width() * 100).toInt()}% × ${(crop.height() * 100).toInt()}% of photo",
                color = Color(0xFF9CA3AF),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Adjust tab
// ---------------------------------------------------------------------------

private data class PhotoAdjustItem(
    val id: String,
    val label: String,
    val range: ClosedFloatingPointRange<Float>,
    val get: (VideoAdjustments) -> Float,
    val set: (VideoAdjustments, Float) -> VideoAdjustments,
    val format: (Float) -> String
)

private val PHOTO_ADJUST_ITEMS = listOf(
    PhotoAdjustItem("brightness", "Brightness", -1f..1f,
        { it.brightness }, { a, v -> a.copy(brightness = v.coerceIn(-1f, 1f)) },
        { "%+.0f".format(it * 100) }),
    PhotoAdjustItem("contrast", "Contrast", 0f..2f,
        { it.contrast }, { a, v -> a.copy(contrast = v.coerceIn(0f, 2f)) },
        { "%.0f%%".format(it * 100) }),
    PhotoAdjustItem("saturation", "Saturation", 0f..2f,
        { it.saturation }, { a, v -> a.copy(saturation = v.coerceIn(0f, 2f)) },
        { "%.0f%%".format(it * 100) }),
    PhotoAdjustItem("warmth", "Warmth", -1f..1f,
        { it.temperature }, { a, v -> a.copy(temperature = v.coerceIn(-1f, 1f)) },
        { "%+.0f".format(it * 100) }),
    PhotoAdjustItem("sharpness", "Sharpness", 0f..1f,
        { it.sharpness }, { a, v -> a.copy(sharpness = v.coerceIn(0f, 1f)) },
        { "%.0f%%".format(it * 100) })
)

@Composable
private fun PhotoAdjustTab(
    adjustments: VideoAdjustments,
    onAdjust: ((VideoAdjustments) -> VideoAdjustments) -> Unit,
    onReset: () -> Unit
) {
    Column {
        PHOTO_ADJUST_ITEMS.forEach { item ->
            val value = item.get(adjustments)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Text(
                    item.label,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.width(84.dp)
                )
                Slider(
                    value = value,
                    onValueChange = { onAdjust { a -> item.set(a, it) } },
                    valueRange = item.range,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = ApexPalette.NeonCyan,
                        activeTrackColor = ApexPalette.NeonCyan,
                        inactiveTrackColor = Color(0xFF2A2A3C)
                    )
                )
                Text(
                    item.format(value),
                    color = ApexPalette.NeonCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(48.dp)
                )
            }
        }
        if (!adjustments.isDefault) {
            Text(
                "Reset adjustments",
                color = ApexPalette.NeonPink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onReset() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Filters tab — real LUT thumbnails rendered from the photo itself.
// ---------------------------------------------------------------------------

private const val ORIGINAL_FILTER_KEY = "__original__"

@Composable
private fun PhotoFiltersTab(
    photoUri: String,
    settings: PhotoEditSettings,
    onFilterSelect: (String?) -> Unit,
    onFilterIntensity: (Float) -> Unit
) {
    val context = LocalContext.current
    val manifest = remember { LutFilterEngine(context).manifest }
    var thumbs by remember(photoUri) { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    var loading by remember(photoUri) { mutableStateOf(true) }

    LaunchedEffect(photoUri) {
        loading = true
        thumbs = emptyMap()
        try {
            val small = PhotoEditRenderer.loadBitmap(context, photoUri, maxDim = 160)
            if (small == null) {
                loading = false
                return@LaunchedEffect
            }
            val map = mutableMapOf<String, ImageBitmap>()
            map[ORIGINAL_FILTER_KEY] = small.asImageBitmap()
            thumbs = map.toMap()
            for (preset in manifest.filters) {
                val tex = try {
                    LutBitmapCache.getOrLoad(context, preset)
                } catch (_: Exception) {
                    null
                } ?: continue
                val graded = withContext(Dispatchers.Default) {
                    try {
                        LutBitmapCache.applyToBitmap(small, tex, 1f)
                    } catch (_: Exception) {
                        null
                    }
                } ?: continue
                map[preset.id] = graded.asImageBitmap()
                // Progressive: show thumbnails as they finish.
                thumbs = map.toMap()
            }
            try {
                small.recycle()
            } catch (_: Exception) {}
        } finally {
            loading = false
        }
    }

    Column {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item(key = ORIGINAL_FILTER_KEY) {
                PhotoFilterThumb(
                    label = "Original",
                    bitmap = thumbs[ORIGINAL_FILTER_KEY],
                    selected = settings.filterId == null,
                    onClick = { onFilterSelect(null) }
                )
            }
            items(manifest.filters, key = { it.id }) { preset ->
                PhotoFilterThumb(
                    label = preset.name,
                    bitmap = thumbs[preset.id],
                    selected = settings.filterId == preset.id,
                    onClick = { onFilterSelect(preset.id) }
                )
            }
        }
        if (loading) {
            Text(
                "Rendering filter previews…",
                color = Color(0xFF9CA3AF),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (settings.filterId != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Intensity", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(84.dp))
                Slider(
                    value = settings.filterIntensity,
                    onValueChange = onFilterIntensity,
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = ApexPalette.NeonCyan,
                        activeTrackColor = ApexPalette.NeonCyan,
                        inactiveTrackColor = Color(0xFF2A2A3C)
                    )
                )
                Text(
                    "%.0f%%".format(settings.filterIntensity * 100),
                    color = ApexPalette.NeonCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(48.dp)
                )
            }
        }
    }
}

@Composable
private fun PhotoFilterThumb(
    label: String,
    bitmap: ImageBitmap?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(64.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1E1E2E))
                .border(
                    2.dp,
                    if (selected) ApexPalette.NeonCyan else Color.Transparent,
                    RoundedCornerShape(10.dp)
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap,
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (selected) {
                Icon(
                    Icons.Default.Check, null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(16.dp)
                        .background(ApexPalette.NeonCyan, RoundedCornerShape(8.dp))
                        .padding(2.dp)
                )
            }
        }
        Text(
            label,
            color = if (selected) ApexPalette.NeonCyan else Color(0xFF9CA3AF),
            fontSize = 10.sp,
            maxLines = 1,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// Rotate tab
// ---------------------------------------------------------------------------

@Composable
private fun PhotoRotateTab(
    settings: PhotoEditSettings,
    onRotate90: () -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PhotoRotateButton(
            label = "Rotate 90°",
            icon = Icons.Default.RotateRight,
            active = settings.normalizedRotationSteps != 0,
            badge = if (settings.normalizedRotationSteps != 0) "${settings.normalizedRotationSteps * 90}°" else null,
            onClick = onRotate90,
            modifier = Modifier.weight(1f)
        )
        PhotoRotateButton(
            label = "Flip H",
            icon = Icons.Default.SwapHoriz,
            active = settings.flipHorizontal,
            onClick = onFlipH,
            modifier = Modifier.weight(1f)
        )
        PhotoRotateButton(
            label = "Flip V",
            icon = Icons.Default.Flip,
            active = settings.flipVertical,
            onClick = onFlipV,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PhotoRotateButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    badge: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) ApexPalette.NeonCyan.copy(alpha = 0.15f) else Color(0xFF1E1E2E))
            .border(
                1.dp,
                if (active) ApexPalette.NeonCyan else Color(0xFF2A2A3C),
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 12.dp)
    ) {
        Icon(
            icon, label,
            tint = if (active) ApexPalette.NeonCyan else Color.White,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            badge ?: label,
            color = if (active) ApexPalette.NeonCyan else Color(0xFF9CA3AF),
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
        )
    }
}
