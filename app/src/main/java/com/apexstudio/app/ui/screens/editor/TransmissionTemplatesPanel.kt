package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.fx.FxPreset
import com.apexstudio.app.data.template.TemplateArtCatalog
import com.apexstudio.app.data.template.TransmissionTemplate
import com.apexstudio.app.ui.components.templates.TemplateArtwork
import com.apexstudio.app.ui.theme.ApexPalette

/**
 * Bottom-sheet transmission template picker. Mirrors the styling of
 * [FxPanel] (rounded top corners, dark surface, neon-cyan accent)
 * so the two sheets feel like siblings.
 *
 * Each tile renders the template's distinct preview artwork
 * ([com.apexstudio.app.ui.components.templates.TemplateArtwork]):
 * unique layout geometry per template, tinted with the template's
 * accent colour (TransmissionTemplate.previewAccentArgb), so a user
 * can scan the row and pick the look they want without reading the
 * label.
 *
 * Selecting a tile calls [onTemplateApplied] with the template id
 * — the EditorViewModel handles the actual LUT + FX + intensity
 * mutation + persistence. The "Original" tile clears any active
 * transmission template (id == null).
 */
@Composable
fun TransmissionTemplatesPanel(
    templates: List<TransmissionTemplate>,
    activeTemplateId: String?,
    onTemplateApplied: (String?) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(ApexPalette.BgSurface)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Transmission",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Default.Close,
                contentDescription = "Close transmission",
                tint = ApexPalette.NeonCyan,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { onClose() }
                    .padding(4.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "One-tap Filter + FX + transition presets.",
            color = ApexPalette.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(12.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // "Original" tile — clears the active transmission
            // template and the editor falls back to whatever LUT / FX
            // the user had before.
            item {
                TransmissionTemplateTile(
                    label = "Original",
                    sublabel = "No preset",
                    accent = ApexPalette.BgElevated,
                    templateId = "original",
                    selected = activeTemplateId == null,
                    onClick = { onTemplateApplied(null) }
                )
            }
            items(templates) { template ->
                TransmissionTemplateTile(
                    label = template.name,
                    sublabel = resolveTransmissionTemplateSublabel(template),
                    accent = Color(template.previewAccentArgb.toULong().toLong()),
                    templateId = template.id,
                    speedBadge = if (template.isSlowMotion) (if (template.speedLabel.isNotBlank()) template.speedLabel else "${template.playbackSpeed}x Slow-Mo") else null,
                    musicTitle = if (template.musicTitle.isNotBlank()) template.musicTitle else null,
                    bpm = if (template.bpm > 0) template.bpm else null,
                    selected = activeTemplateId == template.id,
                    onClick = { onTemplateApplied(template.id) }
                )
            }
        }
    }
}

@Composable
private fun TransmissionTemplateTile(
    label: String,
    sublabel: String,
    accent: Color,
    templateId: String,
    speedBadge: String? = null,
    musicTitle: String? = null,
    bpm: Int? = null,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(112.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) accent.copy(alpha = 0.14f) else Color.Transparent
            )
            .border(
                1.5.dp,
                if (selected) accent else Color.Transparent,
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, ApexPalette.BorderGlass.copy(alpha = 0.7f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Distinct per-template artwork (geometry varies by template,
            // not just the accent colour) so the picker reads truthfully.
            TemplateArtwork(
                spec = TemplateArtCatalog.artFor(templateId),
                accent = accent,
                modifier = Modifier.matchParentSize(),
            )
            if (speedBadge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(ApexPalette.NeonCyan.copy(alpha = 0.9f))
                        .padding(vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        speedBadge,
                        color = Color.Black,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = if (selected) accent else ApexPalette.TextPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Text(
            sublabel,
            color = ApexPalette.TextSecondary,
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
        if (musicTitle != null) {
            Text(
                "🎵 $musicTitle",
                color = ApexPalette.NeonCyan,
                fontSize = 7.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

/**
 * Resolve the [TransmissionTemplate] sublabel shown beneath the
 * template name. Tries to show the human-readable LUT name + FX
 * label so the user sees "Cinematic Travel Vlog" + "Kodak 35mm +
 * VHS" instead of the raw kebab-case ids ("kodak_35mm + vhs") the
 * JSON catalog stores. Falls back to the raw ids only when the
 * lookup fails (e.g. a preset was renamed but the catalog hasn't
 * been updated yet), and surfaces a `?` suffix so the user can
 * tell the lookup didn't resolve cleanly.
 */
private fun resolveTransmissionTemplateSublabel(template: TransmissionTemplate): String {
    val lutName = com.apexstudio.app.data.filter.FilterManifest
        .presetById(template.filterId)
        ?.name
        ?: "${template.filterId} (?)"
    val fxName = FxPreset.byId(template.fxPresetId)?.label
        ?: "${template.fxPresetId} (?)"
    return "$lutName + $fxName"
}
