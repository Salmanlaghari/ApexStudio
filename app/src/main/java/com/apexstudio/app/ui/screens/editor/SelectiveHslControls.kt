package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.domain.model.HslBandAdjustment
import com.apexstudio.app.domain.model.HslColorRange
import com.apexstudio.app.domain.model.SelectiveHslAdjustments
import com.apexstudio.app.ui.theme.EditorPalette as ApexPalette

@Composable
internal fun SelectiveHslControls(
    adjustments: SelectiveHslAdjustments,
    onUpdate: ((SelectiveHslAdjustments) -> SelectiveHslAdjustments) -> Unit
) {
    var selectedRange by remember { mutableStateOf(HslColorRange.RED) }
    val selectedAdjustment = adjustments.forRange(selectedRange)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ApexPalette.BgBase)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Selective HSL", color = ApexPalette.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Adjust one colour range at a time",
                    color = ApexPalette.TextSecondary,
                    fontSize = 10.sp
                )
            }
            Text(
                "Reset ${selectedRange.displayName}",
                color = ApexPalette.NeonCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(ApexPalette.NeonCyan.copy(alpha = 0.12f))
                    .clickable {
                        onUpdate { current -> current.updateRange(selectedRange) { HslBandAdjustment() } }
                    }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 9.dp, vertical = 6.dp)
                    .testTag("hsl_reset_range")
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("hsl_color_range_row")
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HslColorRange.values().forEach { range ->
                val selected = range == selectedRange
                Column(
                    modifier = Modifier
                        .width(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) ApexPalette.NeonCyan.copy(alpha = 0.14f) else ApexPalette.BgElevated)
                        .border(
                            1.dp,
                            if (selected) ApexPalette.NeonCyan else ApexPalette.BorderGlass,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { selectedRange = range }
                        .padding(vertical = 7.dp)
                        .testTag("hsl_range_${range.name.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ColorRangeSwatch(range = range, selected = selected)
                    Text(
                        range.displayName,
                        color = if (selected) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
                        fontSize = 9.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        HslSliderRow(
            id = "hue",
            title = "Hue Shift",
            value = selectedAdjustment.hueShift,
            range = -180f..180f,
            formatted = "%+.0f°".format(selectedAdjustment.hueShift),
            onValueChange = { value ->
                onUpdate { current -> current.updateRange(selectedRange) { it.copy(hueShift = value.coerceIn(-180f, 180f)) } }
            }
        )
        HslSliderRow(
            id = "saturation",
            title = "Saturation",
            value = selectedAdjustment.saturation,
            range = -1f..1f,
            formatted = "%+.0f%%".format(selectedAdjustment.saturation * 100f),
            onValueChange = { value ->
                onUpdate { current -> current.updateRange(selectedRange) { it.copy(saturation = value.coerceIn(-1f, 1f)) } }
            }
        )
        HslSliderRow(
            id = "lightness",
            title = "Lightness",
            value = selectedAdjustment.lightness,
            range = -1f..1f,
            formatted = "%+.0f%%".format(selectedAdjustment.lightness * 100f),
            onValueChange = { value ->
                onUpdate { current -> current.updateRange(selectedRange) { it.copy(lightness = value.coerceIn(-1f, 1f)) } }
            }
        )
    }
}

@Composable
private fun ColorRangeSwatch(range: HslColorRange, selected: Boolean) {
    val color = when (range) {
        HslColorRange.RED -> Color(0xFFFF5267)
        HslColorRange.YELLOW -> Color(0xFFF4D34E)
        HslColorRange.GREEN -> Color(0xFF54D68A)
        HslColorRange.CYAN -> Color(0xFF43CED6)
        HslColorRange.BLUE -> Color(0xFF5C8BFF)
        HslColorRange.MAGENTA -> Color(0xFFE16AD6)
    }
    Spacer(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(color)
            .border(if (selected) 2.dp else 1.dp, if (selected) ApexPalette.TextPrimary else color.copy(alpha = 0.5f), CircleShape)
    )
}

@Composable
private fun HslSliderRow(
    id: String,
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    formatted: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = ApexPalette.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(formatted, color = ApexPalette.NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = ApexPalette.NeonCyan,
                activeTrackColor = ApexPalette.NeonCyan,
                inactiveTrackColor = ApexPalette.BgElevated
            ),
            // Reserve Material3's 48dp interactive target without fixing the slider track height.
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .fillMaxWidth()
                .testTag("hsl_${id}_slider")
        )
    }
}
