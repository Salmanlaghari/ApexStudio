package com.apexstudio.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    cornerRadius: Dp = 20.dp,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    borderWidth: Dp = 1.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val cardColor = backgroundColor ?: colors.surface.copy(alpha = 0.92f)
    val strokeColor = borderColor ?: colors.outline.copy(alpha = 0.72f)
    Box(
        modifier = modifier
            .clip(shape)
            .background(cardColor)
            .border(borderWidth, strokeColor, shape)
            .padding(contentPadding)
    ) { content() }
}

@Composable
fun NeonGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val themeColors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(
                        themeColors.background,
                        themeColors.surface,
                        themeColors.background
                    )
                )
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            themeColors.secondary.copy(alpha = 0.18f),
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
                            themeColors.primary.copy(alpha = 0.10f),
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
    color: Color? = null,
    size: Dp = 10.dp,
    modifier: Modifier = Modifier
) {
    val dotColor = color ?: MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(size * 2)
            .blur(8.dp)
            .background(dotColor.copy(alpha = 0.5f), RoundedCornerShape(50))
    )
    Box(
        modifier = Modifier
            .size(size)
            .background(dotColor, RoundedCornerShape(50))
    )
}
