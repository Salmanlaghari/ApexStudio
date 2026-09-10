package com.apexstudio.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.apexstudio.app.ui.theme.ApexPalette

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    cornerRadius: Dp = 20.dp,
    backgroundColor: Color = ApexPalette.BgGlass,
    borderColor: Color = ApexPalette.BorderGlass,
    borderWidth: Dp = 1.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(borderWidth, borderColor, shape)
            .padding(contentPadding)
    ) { content() }
}

@Composable
fun NeonGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(
                        ApexPalette.BgBase,
                        Color(0xFF0E1424),
                        ApexPalette.BgBase
                    )
                )
            )
    ) {
        // ambient orbs
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ApexPalette.NeonPurple.copy(alpha = 0.18f),
                            Color.Transparent
                        ),
                        radius = 900f
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ApexPalette.NeonCyan.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        radius = 1100f
                    )
                )
        )
        content()
    }
}

@Composable
fun GlowingDot(
    color: Color = ApexPalette.NeonCyan,
    size: Dp = 10.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size * 2)
            .blur(8.dp)
            .background(color.copy(alpha = 0.5f), RoundedCornerShape(50))
    )
    Box(
        modifier = Modifier
            .size(size)
            .background(color, RoundedCornerShape(50))
    )
}
