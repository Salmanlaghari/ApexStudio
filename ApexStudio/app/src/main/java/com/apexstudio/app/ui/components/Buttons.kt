package com.apexstudio.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.apexstudio.app.ui.theme.ApexPalette

@Composable
fun NeonIconButton(
    icon: ImageVector,
    label: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = ApexPalette.TextPrimary,
    background: Color = ApexPalette.BgElevated,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    selected: Boolean = false
) {
    val bg by animateColorAsState(
        if (selected) ApexPalette.NeonCyan.copy(alpha = 0.18f) else background,
        label = "neon-icon-bg"
    )
    val border by animateColorAsState(
        if (selected) ApexPalette.NeonCyan else Color.Transparent,
        label = "neon-icon-border"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(bg)
                .border(1.dp, border, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(iconSize))
        }
        if (label != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                color = if (selected) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun NeonPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "shimmer-offset"
    )
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        ApexPalette.NeonPurple,
                        ApexPalette.NeonCyan,
                        ApexPalette.NeonPurple
                    ),
                    start = androidx.compose.ui.geometry.Offset(offset * 400 - 100, 0f),
                    end = androidx.compose.ui.geometry.Offset(offset * 400, 0f)
                )
            )
            .shadow(if (enabled) 16.dp else 0.dp, RoundedCornerShape(16.dp),
                ambientColor = ApexPalette.NeonCyan,
                spotColor = ApexPalette.NeonPurple)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = ApexPalette.BgDeep, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text, color = ApexPalette.BgDeep,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
fun GlassChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    val border = if (selected) ApexPalette.NeonCyan else ApexPalette.BorderGlass
    val textColor = if (selected) ApexPalette.NeonCyan else ApexPalette.TextPrimary
    val bg = if (selected) ApexPalette.NeonCyan.copy(alpha = 0.14f) else ApexPalette.BgElevated
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = textColor, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun PulsingPlayButton(
    isPlaying: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        if (isPlaying) 0.92f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "play-scale"
    )
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        1f, 1.15f, infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "pulse"
    )
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .scale(pulse)
                .clip(CircleShape)
                .background(ApexPalette.NeonCyan.copy(alpha = 0.18f))
        )
        Box(
            modifier = Modifier
                .size(64.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(ApexPalette.NeonCyan, ApexPalette.NeonPurple))
                )
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isPlaying) Icons.Default.PlayArrow else Icons.Default.PlayArrow,
                null, tint = ApexPalette.BgDeep,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
