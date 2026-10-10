package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.autoclip.AUTO_CLIP_STYLES
import com.apexstudio.app.data.autoclip.AutoClipEngine
import com.apexstudio.app.data.autoclip.AutoClipPlan
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.ui.theme.ApexPalette

/**
 * Phase 3: Auto Clip panel — TikTok-style 1-tap edits.
 *
 * Pick a style → preview the plan summary → "Apply" executes it.
 * "Apply + Music" also opens the music picker filtered to the
 * style's mood tag.
 */
@Composable
fun AutoClipPanel(
    clips: List<MediaClip>,
    onApplyPlan: (AutoClipPlan) -> Unit,
    onApplyPlanWithMusic: (AutoClipPlan) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedStyleId by remember { mutableStateOf(AUTO_CLIP_STYLES.first().id) }
    val style = remember(selectedStyleId) {
        AUTO_CLIP_STYLES.first { it.id == selectedStyleId }
    }
    val plan = remember(clips, style) { AutoClipEngine.plan(clips, style) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        ApexPalette.BgSurface.copy(alpha = 0.98f),
                        ApexPalette.BgBase.copy(alpha = 0.98f)
                    )
                )
            )
            .border(
                1.dp,
                Brush.horizontalGradient(
                    listOf(ApexPalette.NeonCyan.copy(alpha = 0.4f), ApexPalette.NeonPink.copy(alpha = 0.4f))
                ),
                RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(ApexPalette.NeonPink.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = ApexPalette.NeonPink, modifier = Modifier.size(16.dp))
                }
                Column {
                    Text(
                        "Auto Clip",
                        color = ApexPalette.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "TikTok-style 1-tap edit • ${clips.size} clips",
                        color = ApexPalette.TextTertiary,
                        fontSize = 9.sp
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(ApexPalette.BgElevated)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = ApexPalette.TextSecondary, modifier = Modifier.size(15.dp))
            }
        }

        Spacer(Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("PICK A STYLE", color = ApexPalette.NeonCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            AUTO_CLIP_STYLES.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { s ->
                        val selected = s.id == selectedStyleId
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (selected) ApexPalette.NeonPink.copy(alpha = 0.15f)
                                    else ApexPalette.BgElevated
                                )
                                .border(
                                    1.5.dp,
                                    if (selected) ApexPalette.NeonPink else Color.Transparent,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { selectedStyleId = s.id }
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(s.emoji, fontSize = 26.sp)
                                Text(s.name, color = ApexPalette.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(s.description, color = ApexPalette.TextTertiary, fontSize = 9.sp, maxLines = 2)
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }

            // Plan summary
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F1A2A))
                    .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("WHAT WILL HAPPEN", color = ApexPalette.NeonCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(plan.summary, color = ApexPalette.TextPrimary, fontSize = 11.sp)
                    Text(
                        "${plan.ops.size} edits • cuts every ${style.cutEverySec}s • ${style.transitionId} transitions",
                        color = ApexPalette.TextTertiary,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Apply buttons
            val canApply = clips.isNotEmpty()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (canApply) {
                                Brush.horizontalGradient(
                                    listOf(ApexPalette.NeonPink, ApexPalette.NeonAmber)
                                )
                            } else {
                                Brush.verticalGradient(
                                    listOf(ApexPalette.BgElevated, ApexPalette.BgElevated)
                                )
                            }
                        )
                        .clickable(enabled = canApply) { onApplyPlan(plan) }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "✨ Apply Auto Clip",
                        color = if (canApply) Color.Black else ApexPalette.TextTertiary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ApexPalette.BgElevated)
                    .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .clickable(enabled = canApply) { onApplyPlanWithMusic(plan) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "🎵 Apply + Add ${style.musicMood.replaceFirstChar { it.uppercase() }} Music",
                    color = if (canApply) ApexPalette.NeonCyan else ApexPalette.TextTertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (!canApply) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Add video clips to the timeline first.",
                    color = ApexPalette.NeonAmber,
                    fontSize = 11.sp
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
